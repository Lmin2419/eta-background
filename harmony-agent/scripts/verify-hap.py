#!/usr/bin/env python3
"""Check that an unsigned artifact contains executable ArkTS; native libraries are optional."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

path = Path(sys.argv[1])
with zipfile.ZipFile(path) as z:
    assert z.testzip() is None, 'Corrupted HAP zip'
    names = z.namelist()
    for name in ['ets/modules.abc', 'module.json']:
        assert name in names, f'Missing {name}'
        assert len(z.read(name)) > 100, f'Empty {name}'
    # The native HDC library is optional until the phone-operation milestone reconnects it.
    for name in [n for n in names if n.startswith('libs/arm64-v8a/') and n.endswith('.so')]:
        so = z.read(name)
        assert so[:4] == b'\x7fELF' and int.from_bytes(so[18:20], 'little') == 183, f'Not AArch64 ELF: {name}'
    module = json.loads(z.read('module.json'))
    assert module['app']['bundleName'] == 'com.example.harmonyagent'
    print(f'Validated HAP structure: {path}, {path.stat().st_size:,} bytes')
    print('SHA-256:', hashlib.sha256(path.read_bytes()).hexdigest())
    print('This checks packaging only; device authorization and execution require a real phone.')
