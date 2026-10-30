# Porting inputs: app/src/main/ @ 3b1794d; see docs/ETA_PORT.md §8.
"""Shared deterministic output and provenance helpers (no network access)."""
import argparse
import hashlib
import json
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
ETA_COMMIT = '3b1794d70881c3c849b53d7778dd2bfdf0a46cc5'
MIUIX_COMMIT = '39c40f99844227b853f0049a0933b1f3ae6c00ba'


def arguments(description):
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument('--eta-root', type=Path, default=PROJECT.parent)
    parser.add_argument('--output-root', type=Path, default=PROJECT)
    parser.add_argument('--check', action='store_true', help='Fail on generated drift; do not write.')
    return parser.parse_args()


def emit(path, value, check=False):
    data = value if isinstance(value, bytes) else value.encode('utf-8')
    if check:
        if not path.is_file() or path.read_bytes() != data:
            raise ValueError('Generated output differs: ' + str(path))
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)


def emit_json(path, value, check=False):
    emit(path, json.dumps(value, ensure_ascii=False, indent=2) + '\n', check)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def origin(path, root):
    return {'path': path.relative_to(root).as_posix(), 'sha256': digest(path)}
