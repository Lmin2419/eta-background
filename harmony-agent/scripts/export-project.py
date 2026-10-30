#!/usr/bin/env python3
"""Export the current framework, preserving the user's signing on overlay upgrades."""
import hashlib
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
dist = root / 'dist'
dist.mkdir(exist_ok=True)
version = json.loads((root / 'package.json').read_text())['version']
prefix = 'harmony-agent-' + version
hap = dist / (prefix + '-unsigned.hap')
if not hap.exists():
    raise SystemExit('Build the current unsigned HAP first: npm run build:harmony')
profile = json.loads((root / 'build-profile.json5').read_text())
if profile.get('app', {}).get('signingConfigs'):
    raise SystemExit('Export from a clean unsigned checkout, not an installation with personal signing data.')
files = [root / name for name in ['README.md', 'THIRD_PARTY_NOTICES.md', '.gitignore', 'build-profile.json5',
         'hvigorfile.ts', 'oh-package.json5', 'package.json', 'package-lock.json', 'tsconfig.json']]
ignored = {'build', 'oh_modules', 'node_modules', '.cxx', '.hvigor', '.preview', '__pycache__', 'libs', 'vendor', 'native-build'}
for folder in ['AppScope', 'entry', 'hvigor', 'scripts', 'tests', 'docs']:
    for p in (root / folder).rglob('*'):
        if not p.is_file() or set(p.relative_to(root).parts) & ignored:
            continue
        if p.suffix.lower() in {'.p12', '.p7b', '.pem', '.cer', '.key', '.log', '.pyc'}:
            raise SystemExit('Refusing credential/generated file: ' + str(p))
        files.append(p)
files = sorted(set(files))
with zipfile.ZipFile(hap) as z:
    module = json.loads(z.read('module.json'))
    assert module['app']['versionName'] == version, 'HAP version does not match source'
    libraries = {n: z.read(n) for n in z.namelist() if n.startswith('libs/arm64-v8a/') and n.endswith('.so')}
    assert 'libs/arm64-v8a/libagent_hdc.so' in libraries

for suffix in ['source', 'deveco']:
    destination = dist / f'{prefix}-{suffix}.zip'
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as out:
        for p in files:
            rel = p.relative_to(root).as_posix()
            target = 'harmony-agent/' + rel
            if suffix == 'deveco' and rel == 'build-profile.json5':
                data = json.loads(p.read_text())
                for product in data['app']['products']:
                    product['signingConfig'] = 'default'
                out.writestr(target, json.dumps(data, indent=2) + '\n')
            elif suffix == 'deveco' and rel == 'entry/build-profile.json5':
                out.writestr(target, json.dumps({'apiType': 'stageMode', 'buildOption': {}, 'targets': [{'name': 'default'}]}, indent=2))
            else:
                out.write(p, target)
        if suffix == 'deveco':
            for name, binary in libraries.items():
                out.writestr('harmony-agent/entry/' + name, binary)
            out.writestr('harmony-agent/PREBUILT_PROJECT.md', '# 0.9 交互框架预览\n\n使用预编译 ARM64 库；无需在 DevEco 编译 C++。请生成自己的 default 调试签名后 Run。已有旧工程优先使用 upgrade 包以保留签名。详见 README.md 和 docs/UPGRADE_0.9.md。\n')
    print(destination)
destination = dist / f'{prefix}-upgrade.zip'
with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as out:
    for p in files:
        rel = p.relative_to(root).as_posix()
        if rel in {'AppScope/app.json5', 'entry/src/main/module.json5'} or rel.startswith(('entry/src/main/ets/', 'entry/src/main/resources/')):
            out.write(p, rel)
    out.writestr('升级说明.txt', (root / 'docs/UPGRADE_0.9.md').read_text())
    out.writestr('本版验收清单.txt', (root / 'docs/ACCEPTANCE_0.9.md').read_text())
print(destination)
with (dist / f'SHA256SUMS-{version}').open('w') as output:
    for p in sorted(dist.glob(prefix + '-*')):
        if p.suffix in {'.hap', '.zip'} and not p.name.endswith('-acceptance.zip'):
            output.write(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n')
