#!/usr/bin/env python3
"""Validate B42 JSON, assets, and the real Lua mod against a fake game API."""
import json
import pathlib
import re
import shutil
import struct
import subprocess
import sys

repo = pathlib.Path(__file__).resolve().parent.parent
mod = repo / 'mod/PZCloudManagement/42'
lua = (mod / 'media/lua/client/PZCloud/CloudSaves.lua').read_text(encoding='utf-8')
fallback = dict(re.findall(r'(\w+) = "([^"]*)"', lua.split('local fallback = {', 1)[1].split('\n}', 1)[0]))
# Keep the public workshop metadata and game mod synchronized.
info = (mod / 'mod.info').read_text(encoding='utf-8').splitlines()
assert 'id=KNManagement' in info
assert 'modversion=2.3.5' in info
icon_files = [line.split('=', 1)[1] for line in info if line.startswith('icon=')]
for filename in icon_files:
    icon = (mod / filename).read_bytes()
    assert icon.startswith(bytes.fromhex('89504e470d0a1a0a')), f'Invalid Workshop icon {filename}'
workshop_metadata = (repo / 'workshop/workshop.txt').read_text(encoding='utf-8')
assert workshop_metadata.startswith('version=1\nid=')
assert 'https://github.com/SantiagoPana/PZ-Cloud-Management/releases/' in workshop_metadata
assert 'math.floor((screenWidth - PZCloud.button.width) / 2)' in lua
translations = {}
for language in ('EN', 'ES', 'AR'):
    path = mod / 'media/lua/shared/Translate' / language / 'UI.json'
    raw = path.read_bytes()
    assert not raw.startswith(b'\xef\xbb\xbf'), f'BOM in {path}'
    values = json.loads(raw)
    assert values and all(isinstance(value, str) and value for value in values.values())
    assert set(values) == {'UI_PZCloud_' + key for key in fallback}
    translations[language] = values
assert not list(mod.rglob('UI_*.txt')), 'Obsolete translations in B42 mod'
for path in (mod / 'poster.png', repo / 'workshop/preview.png'):
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>II', data[16:24]) == (512, 512)
    assert len(data) < 1024 * 1024
work = repo / 'build/mod-tests'
work.mkdir(parents=True, exist_ok=True)
generated = work / 'translations.lua'
quoted = lambda value: json.dumps(value, ensure_ascii=False)
generated.write_text('return {\n' + ',\n'.join('[' + quoted(language) + '] = {' + ','.join('[' + quoted(key) + ']=' + quoted(value) for key, value in values.items()) + '}' for language, values in translations.items()) + '\n}\n', encoding='utf-8')
executable = sys.argv[1] if len(sys.argv) > 1 else (shutil.which('lua5.1') or shutil.which('lua'))
if not executable:
    raise SystemExit('Install Lua 5.1 or pass the Lua executable as the first argument.')
subprocess.run([executable, str(repo / 'src/test/lua/workshop.lua'), str(repo), str(generated)], check=True)
print('B42 EN/ES/AR JSON and 512px Workshop images validated.')
