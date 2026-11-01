#!/usr/bin/env python3
"""Prepare localized release content without deploying feeds or publishing builds."""
import argparse
import json
import re
import shutil
import time
from pathlib import Path
import translate_battly_strings as i18n

ROOT = Path(__file__).resolve().parents[2]
NEWS = ROOT / "app_pojavlauncher/src/main/assets/news"
FEEDS = {"de": "de", "fr": "fr", "hi": "hi", "id": "id", "ja": "ja",
         "pt": "pt", "ru": "ru", "zh": "zh-CN"}


def content(copy):
    return (copy["intro"] + "\n\n**" + copy["heading"] + ":**\n\n"
            + "\n".join("- " + item for item in copy["bullets"])
            + "\n\n" + copy["thanks"] + "\n\n@here & @everyone")


def replace_strings(path, values, previous, version):
    text = path.read_text(encoding="utf-8-sig")
    text = text.replace(previous, version)
    for key, value in values.items():
        pattern = re.compile(i18n.STRING_PATTERN_TEMPLATE.format(name=re.escape(key)), re.DOTALL)
        encoded = i18n.android_xml_text(value)
        if pattern.search(text):
            text = pattern.sub(lambda match: match.group(1) + encoded + match.group(3), text, count=1)
        else:
            text = text.replace("</resources>", f'    <string name="{key}">{encoded}</string>\n</resources>')
    for attempt in range(5):
        try:
            path.write_text(text, encoding="utf-8", newline="\n")
            break
        except OSError:
            if attempt == 4:
                raise
            time.sleep(0.25 * (attempt + 1))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("release", type=Path)
    args = parser.parse_args()
    release = json.loads(args.release.read_text(encoding="utf-8"))
    english = dict(release["en"], heading="What's new")
    spanish = dict(release["es"], heading="Novedades")
    cache = i18n.load_cache()
    version = release["version"]
    def translate(values, target):
        # Scope cache entries to this release's protected terminology.
        prefix = f"release-{version}-protected-v1:"
        result = i18n.translate_values({prefix + key: value for key, value in values.items()}, target, cache)
        return {key.removeprefix(prefix): value for key, value in result.items()}
    output = ROOT / "build" / f"release-{version}"
    output.mkdir(parents=True, exist_ok=True)

    for path in sorted(i18n.RES_DIR.glob("values*/strings.xml")):
        qualifier = path.parent.name.removeprefix("values-")
        if qualifier == "es":
            panel = spanish["panel"]
        elif qualifier in i18n.LOCALES:
            panel = translate(english["panel"], i18n.LOCALES[qualifier])
        else:
            panel = english["panel"]
        replace_strings(path, panel, release["previous_version"], version)
        print(f"Panel: {path.parent.name}", flush=True)

    # Translate individual paragraphs and bullets so Markdown and mentions remain intact.
    fields = {"title": english["title"], "intro": english["intro"],
              "heading": english["heading"], "thanks": english["thanks"]}
    fields.update({f"bullet_{index}": value for index, value in enumerate(english["bullets"])})
    for locale in ["en", "es", *FEEDS]:
        if locale in ("en", "es"):
            copy = english if locale == "en" else spanish
        else:
            translated = translate(fields, FEEDS[locale])
            copy = dict(translated, bullets=[translated[f"bullet_{index}"] for index in range(len(english["bullets"]))])
        path = NEWS / f"mobile.{locale}.json"
        entries = json.loads(path.read_text(encoding="utf-8-sig"))
        # Refuse to overwrite a different announcement with the selected numeric ID.
        for entry in entries:
            if str(entry.get("id")) == release["id"] and version not in entry.get("title", ""):
                raise ValueError(f"News ID collision in {path}")
        entry = dict(id=release["id"], title=copy["title"], content=content(copy),
                     author="Battly Staff - 1ly4s0", publish_date=release["publish_date"])
        entries = [entry] + [item for item in entries if str(item.get("id")) != release["id"]]
        path.write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Feed: {locale}", flush=True)
    shutil.copyfile(NEWS / "mobile.en.json", NEWS / "mobile.json")
    for path in NEWS.glob("mobile*.json"):
        destination = output / "news" / path.name
        destination.parent.mkdir(exist_ok=True)
        shutil.copyfile(path, destination)
    announcement = ("# " + spanish["title"] + "\n\n" + spanish["intro"]
                    + "\n\n## Novedades\n\n" + "\n".join("- " + item for item in spanish["bullets"])
                    + "\n\n" + spanish["thanks"] + "\n\n@here & @everyone\n")
    (output / f"anuncio-{version}.md").write_text(announcement, encoding="utf-8")
    i18n.save_cache(cache)


if __name__ == "__main__":
    main()
