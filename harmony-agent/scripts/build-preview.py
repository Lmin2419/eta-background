#!/usr/bin/env python3
"""Compile the native ArkUI preview fixture; this does not certify rendered pixels."""
import json
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
sdk = Path(os.environ['HARMONY_CLT_HOME'])
env = dict(os.environ)
env['DEVECO_SDK_HOME'] = env.get('DEVECO_SDK_HOME', str(sdk / 'sdk'))
env['PATH'] = os.pathsep.join([str(sdk / 'tool/node/bin'), str(sdk / 'bin'), env['PATH']])
config = root / 'entry/.preview/config/buildConfig.json'
config.parent.mkdir(parents=True, exist_ok=True)
data = json.loads(config.read_text()) if config.exists() else {}
data['previewPagePath'] = str(root / 'entry/src/main/ets/pages/FoundationPreview.ets')
config.write_text(json.dumps(data, indent=2) + '\n')
subprocess.run(['hvigorw', '--mode', 'module', '-p', 'product=default', '-p', 'module=entry@default',
                '-p', 'previewer.replace.page=pages/FoundationPreview', '-p', 'buildRoot=.preview',
                'PreviewBuild', '--no-daemon'], cwd=root, env=env, check=True)
