#!/usr/bin/env python3
"""Build a Workshop upload folder using only the mod and publication templates."""
import pathlib
import shutil
import zipfile

repo = pathlib.Path(__file__).resolve().parent.parent
output = repo / 'build' / 'workshop' / 'KnoxNet-Workshop'
if output.exists():
    shutil.rmtree(output)
output.mkdir(parents=True)
shutil.copytree(repo / 'mod' / 'PZCloudManagement', output / 'Contents' / 'mods' / 'PZCloudManagement')
for name in ('preview.png', 'workshop.txt', 'README_PUBLICAR.txt'):
    shutil.copyfile(repo / 'workshop' / name, output / name)
archive = output.parent / 'KnoxNet-Workshop-2.3.5.zip'
# Reject a mistagged release or unexpected executable in the Workshop payload.
info = (output / 'Contents/mods/PZCloudManagement/42/mod.info').read_text(encoding='utf-8')
assert 'id=KNManagement' in info.splitlines()
assert 'modversion=2.3.5' in info.splitlines()
assert (output / 'Contents/mods/PZCloudManagement/42/media/lua/client/PZCloud/CloudSaves.lua').is_file()
for candidate in (output / 'Contents').rglob('*'):
    assert candidate.suffix.lower() not in {'.exe','.dll','.bat','.ps1','.zip','.jar','.sh','.so'}, candidate
assert (output / 'workshop.txt').read_text(encoding='utf-8').startswith('version=1\nid=')

with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as target:
    for path in sorted(output.rglob('*')):
        target.write(path, pathlib.Path(output.name) / path.relative_to(output))
with zipfile.ZipFile(archive) as check:
    assert check.testzip() is None
    assert 'KnoxNet-Workshop/Contents/mods/PZCloudManagement/42/mod.info' in check.namelist()
print(f'Workshop package: {archive} ({archive.stat().st_size} bytes)')
