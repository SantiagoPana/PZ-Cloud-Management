#!/usr/bin/env python3
"""Stage the author's 'workshop' branch without editing that branch.

Usage: python3 scripts/prepare-workshop-source.py workshop-source
Called after checking out 'workshop' into ./workshop-source in CI.
The stage is used by Lua tests, the Workshop ZIP and the Java app's bundled mod.
"""
from pathlib import Path
import shutil
import sys

repo = Path(__file__).resolve().parent.parent
source_checkout = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else repo / "workshop-source"
source = source_checkout / "Knox Net"
source_mod = source / "Contents" / "mods" / "PZCloudManagement"
target_mod = repo / "mod" / "PZCloudManagement"
target_pub = repo / "workshop"

if not source_mod.is_dir() or not (source / "workshop.txt").is_file():
    raise SystemExit("The workshop branch must contain 'Knox Net/Contents/mods/PZCloudManagement' and 'Knox Net/workshop.txt'.")

# Fail closed instead of silently shipping stale edits from the application's branch.
info = (source_mod / "42" / "mod.info").read_text(encoding="utf-8")
for expected in ("id=KNManagement", "author=47", "modversion=2.3.5"):
    if expected not in info.splitlines():
        raise SystemExit(f"Unexpected Workshop metadata: {expected} is missing.")
for file in source.rglob("*"):
    if file.is_symlink():
        raise SystemExit(f"Symlink not permitted inside Workshop source: {file}")
    if file.is_file() and file.suffix.lower() in {".exe",".dll",".ps1",".bat",".sh",".so",".jar",".zip"}:
        raise SystemExit(f"Unexpected executable in Workshop source: {file}")

if target_mod.exists():
    shutil.rmtree(target_mod)
shutil.copytree(source_mod, target_mod)
target_pub.mkdir(parents=True, exist_ok=True)
for name in ("workshop.txt", "README_PUBLICAR.txt", "preview.png"):
    shutil.copyfile(source / name, target_pub / name)

# The branch can keep its original draft copy; the release output must not recommend an old app.
for name in ("workshop.txt", "README_PUBLICAR.txt"):
    file = target_pub / name
    text = file.read_text(encoding="utf-8")
    text = text.replace("Knox Net 2.3.2", "Knox Net 2.3.5")
    text = text.replace("Integración 2.3.2", "Integración 2.3.5")
    text = text.replace("mod 2.3.2", "mod 2.3.5")
    text = text.replace("El mod 2.3.2", "El mod 2.3.5")
    text = text.replace("app 2.3.2", "app 2.3.5")
    text = text.replace("del mod 2.3.2", "del mod 2.3.5")
    text = text.replace("KnoxNet-Workshop", "Knox Net")
    file.write_text(text, encoding="utf-8")

print("Staged Workshop author's source for the 2.3.5 build:")
print(" ", source_mod)
print(" ", target_mod)
print("Workshop branch and its files remain unchanged.")
