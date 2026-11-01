"""Check the delivered APK/AAB actually contain the current localized feeds."""
import json
import sys
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[2]
release = json.loads((root / sys.argv[1]).read_text(encoding="utf-8"))
news = root / "app_pojavlauncher/src/main/assets/news"
for value in sys.argv[2:]:
    path = Path(value)
    prefix = "base/" if path.suffix == ".aab" else ""
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        assert not any(".codex" in name or "release-secrets/" in name for name in names)
        for source in news.glob("mobile*.json"):
            packed = archive.read(prefix + "assets/news/" + source.name)
            assert packed == source.read_bytes(), (path, source.name)
            assert json.loads(packed)[0]["id"] == release["id"]
        abis = {name[len(prefix + "lib/"):].split("/")[0] for name in names
                if name.startswith(prefix + "lib/") and name.endswith(".so")}
        assert abis == {"arm64-v8a", "armeabi-v7a"}, (path, abis)
        for abi in abis:
            for library in ("libng_gl4es.so", "libmobileglues.so"):
                assert prefix + f"lib/{abi}/{library}" in names, (path, abi, library)
    print(f"PASS: {path.name}: current {release['version']} feeds, ARM production libraries, no Codex attachments")
