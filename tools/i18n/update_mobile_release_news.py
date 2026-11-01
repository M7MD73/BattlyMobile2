#!/usr/bin/env python3
"""Translate one English mobile-news entry and synchronize release news files."""

from __future__ import annotations

import argparse
import importlib.util
import json
import shutil
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
NEWS_DIR = ROOT / "app_pojavlauncher" / "src" / "main" / "assets" / "news"
TARGETS = {
    "de": "de",
    "fr": "fr",
    "hi": "hi",
    "id": "id",
    "ja": "ja",
    "pt": "pt",
    "ru": "ru",
    "zh": "zh-CN",
}


def load_translator():
    path = Path(__file__).with_name("translate_battly_strings.py")
    spec = importlib.util.spec_from_file_location("battly_translate", path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def read_news(path: Path) -> list[dict[str, str]]:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_news(path: Path, entries: list[dict[str, str]]) -> None:
    path.write_text(
        json.dumps(entries, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def upsert(entries: list[dict[str, str]], entry: dict[str, str]) -> list[dict[str, str]]:
    return [entry] + [item for item in entries if str(item.get("id")) != str(entry["id"])]


def translate_content(content: str, target: str, translator) -> str:
    translated: list[str] = []
    for line in content.splitlines():
        if not line or line == "@here & @everyone":
            translated.append(line)
            continue
        prefix = "- " if line.startswith("- ") else ""
        body = line[len(prefix):]
        translated.append(prefix + translator.translate_preserving_tokens(body, target))
    return "\n".join(translated)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("entry_id")
    parser.add_argument("--sync-dir", action="append", default=[])
    args = parser.parse_args()

    english_entries = read_news(NEWS_DIR / "mobile.en.json")
    source = next((item for item in english_entries if str(item.get("id")) == args.entry_id), None)
    if source is None:
        raise RuntimeError(f"English news entry {args.entry_id} was not found")

    translator = load_translator()
    for locale, target in TARGETS.items():
        path = NEWS_DIR / f"mobile.{locale}.json"
        entries = read_news(path)
        translated = dict(source)
        translated["title"] = translator.translate_preserving_tokens(source["title"], target)
        translated["content"] = translate_content(source["content"], target, translator)
        write_news(path, upsert(entries, translated))

    # The unqualified endpoint is the English fallback.
    shutil.copyfile(NEWS_DIR / "mobile.en.json", NEWS_DIR / "mobile.json")

    for destination_value in args.sync_dir:
        destination = Path(destination_value)
        destination.mkdir(parents=True, exist_ok=True)
        for path in NEWS_DIR.glob("mobile*.json"):
            shutil.copyfile(path, destination / path.name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
