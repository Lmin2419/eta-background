#!/usr/bin/env python3
"""Bundle reviewed install projects, source, acceptance records, and real build logs."""
import hashlib
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
dist = root / 'dist'
version = json.loads((root / 'package.json').read_text())['version']
prefix = f'harmony-agent-{version}'
report = json.loads((dist / f'PACKAGING_VALIDATION_{version}.json').read_text())
assert report['result'] == 'passed'
for item in report['artifacts']:
    assert hashlib.sha256((dist / item['file']).read_bytes()).hexdigest() == item['sha256'], 'Re-run release validation after export'
entries = {'00_先读_安装与核验.md': (root / 'docs/UPGRADE_0.9.md').read_bytes()}
for suffix, folder in [('upgrade', '安装工程'), ('deveco', '安装工程'), ('source', '源码')]:
    path = dist / f'{prefix}-{suffix}.zip'
    entries[folder + '/' + path.name] = path.read_bytes()
for path in sorted((root / 'docs').glob('*_0.9.md')):
    entries['验收资料/' + path.name] = path.read_bytes()
archive = dist / 'harmony-agent-0.8.0-archived-source.zip'
if archive.exists():
    entries['源码/' + archive.name] = archive.read_bytes()
for filename in ['typecheck-foundation.log', 'test-all-foundation.log', 'build-foundation.log', 'build-preview.log', 'export-build-foundation.log', 'EXPORTED_BUILD_VALIDATION_0.9.json', f'PACKAGING_VALIDATION_{version}.json']:
    entries['验证记录/' + filename] = (dist / filename).read_bytes()
for filename in ['foundation-engine-diagnostic.log', 'minimal-engine-diagnostic.log', 'build-smoke.log']:
    path = dist / 'visual' / filename
    if path.exists():
        entries['验证记录/visual/' + filename] = path.read_bytes()
entries['SHA256SUMS'] = ''.join(hashlib.sha256(data).hexdigest() + '  ' + name + '\n' for name, data in sorted(entries.items())).encode()
destination = dist / f'{prefix}-acceptance.zip'
with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED) as package:
    for name, data in sorted(entries.items()):
        package.writestr(name, data)
with zipfile.ZipFile(destination) as package:
    assert package.testzip() is None
    for name, data in entries.items():
        assert package.read(name) == data
checksum = hashlib.sha256(destination.read_bytes()).hexdigest()
destination.with_suffix('.zip.sha256').write_text(checksum + '  ' + destination.name + '\n')
print(destination)
print('SHA-256:', checksum)
