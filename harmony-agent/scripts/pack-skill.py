#!/usr/bin/env python3
"""Pack a local Skill directory into the inert JSON resource format accepted by the app."""
import argparse,json
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('directory',type=Path);p.add_argument('--id',required=True);p.add_argument('--name',required=True);p.add_argument('--version',default='1.0.0');p.add_argument('--output',type=Path,required=True);a=p.parse_args()
root=a.directory.resolve();main=root/'SKILL.md'
if not main.is_file() or main.is_symlink():raise SystemExit('A regular SKILL.md is required')
resources=[]
for file in sorted(root.rglob('*')):
 if file.is_symlink():raise SystemExit('Symbolic links are not allowed')
 if not file.is_file() or file==main or file.resolve()==a.output.resolve():continue
 rel=file.relative_to(root).as_posix()
 if any(part.startswith('.') for part in file.relative_to(root).parts):continue
 if file.stat().st_size>100000:raise SystemExit('Resource too large: '+rel)
 resources.append({'path':rel,'text':file.read_text(encoding='utf-8')})
 if len(resources)>40:raise SystemExit('At most 40 text resources')
value={'id':a.id,'version':a.version,'name':a.name,'instructions':main.read_text(encoding='utf-8'),'resources':resources,'enabled':False}
raw=json.dumps(value,ensure_ascii=False,indent=2)
if len(value['instructions'])>16000 or len(raw)>500000:raise SystemExit('Skill exceeds app limits')
a.output.write_text(raw,encoding='utf-8');print(a.output)
