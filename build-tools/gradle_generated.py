#!/usr/bin/env python3
"""Files the Gradle build takes from this repository: the server bundle, translations, the intro video.

Usage: gradle_generated.py <output dir>  (the app module adds <out>/assets and <out>/res).
"""
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_apk import ROOT, server_bundle, visible_files  # noqa: E402


def main(out):
    if out.exists():
        shutil.rmtree(out)
    assets = out / "assets"
    (assets / "i18n").mkdir(parents=True)
    (assets / "replyai-server.bundle").write_bytes(server_bundle())
    for table in visible_files(ROOT / "i18n", "*.json"):
        shutil.copy2(table, assets / "i18n" / table.name)
    (out / "res").mkdir()
    intro = ROOT / "assets/intro.mp4"   # optional, not in the repository: your own video
    if intro.is_file():
        (out / "res/raw").mkdir()
        shutil.copy2(intro, out / "res/raw/replyai_intro.mp4")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
