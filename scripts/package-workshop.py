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
archive = output.parent / 'KnoxNet-Workshop-2.3.2.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as target:
    for path in sorted(output.rglob('*')):
        target.write(path, pathlib.Path(output.name) / path.relative_to(output))
with zipfile.ZipFile(archive) as check:
    assert check.testzip() is None
    assert 'KnoxNet-Workshop/Contents/mods/PZCloudManagement/42/mod.info' in check.namelist()
print(f'Workshop package: {archive} ({archive.stat().st_size} bytes)')
