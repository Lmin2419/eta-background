#!/usr/bin/env python3
"""Check that an unsigned artifact contains both executable ArkTS and native code."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

path = Path(sys.argv[1])
with zipfile.ZipFile(path) as z:
    assert z.testzip() is None, 'Corrupted HAP zip'
    names = z.namelist()
    for name in ['ets/modules.abc', 'libs/arm64-v8a/libagent_hdc.so', 'libs/arm64-v8a/libc++_shared.so', 'module.json']:
        assert name in names, f'Missing {name}'
        assert len(z.read(name)) > 100, f'Empty {name}'
    so = z.read('libs/arm64-v8a/libagent_hdc.so')
    assert so[:4] == b'\x7fELF' and int.from_bytes(so[18:20], 'little') == 183, 'Not AArch64 ELF'
    module = json.loads(z.read('module.json'))
    assert module['app']['bundleName'] == 'com.example.harmonyagent'
    print(f'Validated HAP structure: {path}, {path.stat().st_size:,} bytes')
    print('SHA-256:', hashlib.sha256(path.read_bytes()).hexdigest())
    print('This checks packaging only; device authorization and execution require a real phone.')
