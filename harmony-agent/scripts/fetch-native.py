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
        if subprocess.run(['curl', '-fLsS', '--retry', '2', source['url'], '-o', str(partial)]).returncode != 0:
            # Some networks block codeload.github.com but allow git. GitHub builds these tarballs with
            # `git archive | gzip -n`, so the rebuilt archive must match the same pinned SHA-256 below.
            print(f'{name}: tarball download failed, fetching {source["ref"]} with git')
            clone = vendor / f'.{name}-git'
            subprocess.run(['rm', '-rf', str(clone)], check=True)
            subprocess.run(['git', 'init', '-q', str(clone)], check=True)
            subprocess.run(['git', '-C', str(clone), 'fetch', '-q', '--depth', '1', source['git'], source['ref']], check=True)
            with open(partial, 'wb') as out:
                git = subprocess.Popen(['git', '-C', str(clone), 'archive', '--format=tar', '--prefix=' + source['archivePrefix'] + '/', 'FETCH_HEAD'], stdout=subprocess.PIPE)
                subprocess.run(['gzip', '-cn'], stdin=git.stdout, stdout=out, check=True)
                if git.wait() != 0: raise SystemExit(f'git archive failed for {name}')
            subprocess.run(['rm', '-rf', str(clone)], check=True)
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
