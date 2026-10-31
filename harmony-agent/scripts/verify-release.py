#!/usr/bin/env python3
"""Verify package integrity and source parity. Does not certify the UI or the device."""
import hashlib
import json
from pathlib import Path
import struct
import zipfile

root = Path(__file__).resolve().parents[1]
dist = root / 'dist'
version = json.loads((root / 'package.json').read_text())['version']
app = json.loads((root / 'AppScope/app.json5').read_text())['app']
prefix = 'harmony-agent-' + version
checks = []
def check(ok, label):
    if not ok:
        raise SystemExit('FAIL: ' + label)
    checks.append(label)

with zipfile.ZipFile(dist / f'{prefix}-unsigned.hap') as hap:
    check(hap.testzip() is None, 'HAP integrity')
    module = json.loads(hap.read('module.json'))
    check(module['app']['versionName'] == version and module['app']['versionCode'] == app['versionCode'], 'HAP version matches source')
    check(module['app']['bundleName'] == 'com.example.harmonyagent', 'expected bundle')
    check(len(hap.read('ets/modules.abc')) > 100, 'compiled ArkTS included')
    check([p['name'] for p in module['module']['requestPermissions']] == ['ohos.permission.INTERNET', 'ohos.permission.VIBRATE'], 'framework requests only network and approved touch feedback permissions')
    check(all(not a.get('backgroundModes') for a in module['module']['abilities']), 'no legacy background mode')
    libraries = {n: hap.read(n) for n in hap.namelist() if n.startswith('libs/arm64-v8a/') and n.endswith('.so')}
    for name, binary in libraries.items():
        check(binary[:4] == b'\x7fELF' and struct.unpack('<H', binary[18:20])[0] == 183, 'ARM64: ' + name)
check(json.loads((root / 'entry/src/main/resources/base/profile/main_pages.json').read_text())['src'] == ['pages/Index'], 'preview fixture is not a production route')

for suffix in ['source', 'deveco', 'upgrade']:
    with zipfile.ZipFile(dist / f'{prefix}-{suffix}.zip') as package:
        names = package.namelist()
        check(package.testzip() is None and len(names) == len(set(names)), suffix + ' ZIP integrity and unique paths')
        for name in names:
            check(not name.startswith('/') and '..' not in Path(name).parts, suffix + ' safe path: ' + name)
            check(Path(name).suffix.lower() not in {'.p12', '.p7b', '.pem', '.cer', '.key'}, suffix + ' no certificate: ' + name)
        if suffix in ['source', 'deveco']:
            profile = json.loads(package.read('harmony-agent/build-profile.json5'))
            check(not profile['app']['signingConfigs'], suffix + ' no personal signing data')
            if suffix == 'deveco':
                check(all(p.get('signingConfig') == 'default' for p in profile['app']['products']), 'DevEco default signature linked')
                build = json.loads(package.read('harmony-agent/entry/build-profile.json5'))
                check('externalNativeOptions' not in build.get('buildOption', {}), 'DevEco uses prebuilt C++')
                for name, binary in libraries.items():
                    check(package.read('harmony-agent/entry/' + name) == binary, 'prebuilt byte parity: ' + name)
            for name in names:
                rel = name.removeprefix('harmony-agent/')
                source = root / rel
                if source.is_file() and rel not in {'build-profile.json5', 'entry/build-profile.json5'}:
                    check(package.read(name) == source.read_bytes(), suffix + ' source parity: ' + rel)
        else:
            check('build-profile.json5' not in names and 'entry/build-profile.json5' not in names, 'upgrade preserves signing and native settings')
            for source in (root / 'entry/src/main/ets').rglob('*'):
                if source.is_file():
                    rel = source.relative_to(root).as_posix()
                    check(package.read(rel) == source.read_bytes(), 'upgrade source parity: ' + rel)

report = {'version': version, 'result': 'passed', 'checks': len(checks),
          'scope': 'Archive integrity, source parity, manifest and native packaging only. UI rendering and device acceptance remain pending.',
          'artifacts': [{'file': p.name, 'bytes': p.stat().st_size, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
                        for p in sorted(dist.glob(prefix + '-*')) if p.suffix in {'.zip', '.hap'} and not p.name.endswith('-acceptance.zip')]}
(dist / f'PACKAGING_VALIDATION_{version}.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(report, ensure_ascii=False, indent=2))
