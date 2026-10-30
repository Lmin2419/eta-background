#!/usr/bin/env python3
"""Read-only HDC capability report. Does not capture key events or change button/settings mappings."""
import argparse
import datetime
import json
from pathlib import Path
import re
import shlex
import subprocess


CHECKS = {
    'shell_identity': 'id',
    'api_version': 'param get const.ohos.apiversion',
    'system_version': 'param get const.ohos.fullname',
    'device_model': 'param get const.product.model',
    'input_directory_metadata': 'ls -ld /dev/input',
    'input_node_read_access': '''for node in /dev/input/event*; do
  if [ -e "$node" ]; then
    if [ -r "$node" ]; then printf '%s readable\n' "$node";
    else printf '%s not-readable\n' "$node"; fi
  fi
done''',
    'getevent_available': 'command -v getevent',
    'uinput_available': 'command -v uinput',
    'ability_command_available': 'command -v aa',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', required=True, help='Already-authorized HDC target from hdc list targets')
    parser.add_argument('--hdc', default='hdc', help='HDC executable path')
    parser.add_argument('--output', type=Path, help='Optional local JSON report file')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_.:%\[\]-]{1,255}', args.target) or args.target.startswith('-'):
        parser.error('Invalid HDC target')
    results = {}
    marker = '__AGENT_PROBE_STATUS__'
    for name, command in CHECKS.items():
        wrapped = 'sh -c ' + shlex.quote(command) + '; printf "\\n' + marker + '%s\\n" "$?"'
        try:
            completed = subprocess.run([args.hdc, '-t', args.target, 'shell', wrapped], capture_output=True,
                                       text=True, encoding='utf-8', errors='replace', timeout=15, check=False)
            match = re.search(r'\n' + marker + r'(\d+)\s*$', completed.stdout)
            result = {'hdc_exit': completed.returncode, 'shell_exit': int(match[1]) if match else None,
                      'output': completed.stdout[:match.start() if match else None][:16000],
                      'stderr': completed.stderr[:2000], 'verified_shell_status': bool(match)}
        except FileNotFoundError:
            parser.error('HDC executable not found; use --hdc with its installed path')
        except subprocess.TimeoutExpired:
            result = {'error': 'timeout; no retry or device mutation attempted'}
        results[name] = result
    report = {
        'collected_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'checks': results,
        'scope': 'Only identity, version, command availability and access metadata. No input event content was read.',
        'interpretation': 'Readable input nodes or getevent availability do not establish power-key delivery, '
                          'background longevity, permission to launch an app, or permission to replace the system handler.',
        'power_key_rebinding_verified': False,
    }
    text = json.dumps(report, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.write_text(text, encoding='utf-8')
        print(f'Report saved: {args.output}')
    else:
        print(text, end='')


if __name__ == '__main__':
    main()
