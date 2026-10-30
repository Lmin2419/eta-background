#!/usr/bin/env python3
"""Download pinned archives, verify SHA-256, then extract into vendor/."""
import hashlib
import json
from pathlib import Path
import subprocess
import tarfile

root = Path(__file__).resolve().parents[1]
vendor = root / 'vendor'
vendor.mkdir(exist_ok=True)
for source in json.loads((root / 'scripts/native-sources.json').read_text()):
    name = source['name']
    archive = vendor / f"{name}-locked.tar.gz"
    if not archive.exists():
        partial = archive.with_suffix('.part')
        subprocess.run(['curl', '-fLsS', '--retry', '2', source['url'], '-o', str(partial)], check=True)
        partial.rename(archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != source['sha256']:
        raise SystemExit(f'Checksum mismatch: {archive}; remove this archive and retry.')
    target = vendor / source['directory']
    stamp = target / '.source-sha256'
    if target.exists():
        if not stamp.exists() or stamp.read_text().strip() != source['sha256']:
            raise SystemExit(f'Unverified existing sources: {target}; move this folder aside and retry.')
        print(f'{name}: source verified')
        continue
    target.mkdir()
    with tarfile.open(archive, 'r:gz') as tf:
        for member in tf.getmembers():
            parts = Path(member.name).parts[1:]
            if not parts:
                continue
            member.name = str(Path(*parts))
            # data filter rejects traversal and links escaping the extraction root.
            tf.extract(member, target, filter='data')
    stamp.write_text(source['sha256'] + '\n')
    print(f'{name}: downloaded and verified')
