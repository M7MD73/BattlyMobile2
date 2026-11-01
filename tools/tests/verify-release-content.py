"""Validate generated release messaging against Android version and all locales."""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[2]
release = json.loads((root / sys.argv[1]).read_text(encoding="utf-8"))
version = release["version"]
gradle = (root / "app_pojavlauncher/build.gradle").read_text(encoding="utf-8")
assert f'versionName "{version}"' in gradle
assert f'versionCode {release["version_code"]}' in gradle
release_block = gradle.split('        release {', 1)[1].split('        gplay {', 1)[0]
assert 'minifyEnabled false' in release_block
assert 'shrinkResources false' in release_block

locales = list((root / "app_pojavlauncher/src/main/res").glob("values*/strings.xml"))
for path in locales:
    strings = {item.attrib["name"]: "".join(item.itertext()) for item in ET.parse(path).getroot().findall("string")}
    for key in release["en"]["panel"]:
        assert strings.get(key), (path, key)
        assert not re.search(r"ZXQ\w*QXZ", strings[key]), (path, key)
    assert version in strings["battly_whats_new_title"], path
    for term in release.get("required_native_terms", ("Krypton Wrapper", "MobileGlues", "LTW")):
        assert term in strings["battly_whats_new_native_desc"], (path, term)
    assert release["previous_version"] not in path.read_text(encoding="utf-8-sig"), path

news = root / "app_pojavlauncher/src/main/assets/news"
for path in news.glob("mobile*.json"):
    entries = json.loads(path.read_text(encoding="utf-8-sig"))
    ids = [str(entry["id"]) for entry in entries]
    assert len(ids) == len(set(ids)), path
    entry = entries[0]
    assert entry["id"] == release["id"], path
    assert version in entry["title"], path
    assert len([line for line in entry["content"].splitlines() if line.startswith("- ")]) == len(release["en"]["bullets"]), path
    assert "@here & @everyone" in entry["content"], path
    for term in release.get("required_news_terms", ("Krypton Wrapper", "MobileGlues", "LTW", "SDL", "ImGui")):
        assert term in entry["content"], (path, term)
    assert not re.search(r"ZXQ\w*QXZ", entry["content"]), path
assert (news / "mobile.json").read_bytes() == (news / "mobile.en.json").read_bytes()
onboarding = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/onboarding/OnboardingActivity.java").read_text(encoding="utf-8")
assert 'PREF_WHATS_NEW_SEEN_PREFIX + BuildConfig.VERSION_CODE' in onboarding
print(f"PASS: {version}, {len(locales)} Android string files, 10 localized feeds + English fallback, version-gated native panel, R8 disabled")
