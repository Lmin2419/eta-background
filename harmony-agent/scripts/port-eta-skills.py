#!/usr/bin/env python3
"""Generate built-in Skills from the pinned Android assets (Required Notice: Copyright © 2026 蛮吉)."""
import argparse, hashlib, json
from pathlib import Path
root=Path(__file__).resolve().parent.parent
p=argparse.ArgumentParser();p.add_argument('--assets',type=Path,default=root.parent/'app/src/main/assets/builtin_skills');a=p.parse_args()
pins={'manifest.json':'24c478c230475980181038ea4ff40d5649fe7265','self-improving-agent/SKILL.md':'9d81b2656de759154c1760c452b3dd26e15dce51','skill-creator/SKILL.md':'18ddcac2547305e30a79ddbafc712ec1bd3f7527','skill-installer/SKILL.md':'0b5a6ac756a15df4ebf9988a56c1ecb11f4c288e'}
for rel,expected in pins.items():
    b=(a.assets/rel).read_bytes();actual=hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
    if actual!=expected:raise SystemExit('Source differs from pinned Eta: '+rel)
manifest=json.loads((a.assets/'manifest.json').read_text())
sources=[];skills=[]
for item in manifest['skills']:
    files=[]
    for file in sorted((a.assets/item['id']).rglob('*')):
        if file.is_file():
            raw=file.read_bytes();sources.append({'path':'app/src/main/assets/builtin_skills/'+str(file.relative_to(a.assets)),'sha256':hashlib.sha256(raw).hexdigest()})
            files.append({'path':str(file.relative_to(a.assets/item['id'])),'content':raw.decode().replace('Eta','随身助手')})
    skills.append({'id':item['id'],'name':item['name'],'description':item['description'].replace('Eta','随身助手'),'files':files})
target=root/'entry/src/main/ets/core/eta/skills/builtinSkills.ts'
target.write_text('// Generated from app/src/main/assets/builtin_skills @ 3b1794d. Do not edit.\n// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.\nimport {BuiltinSkill} from \'./skillTypes\';\nexport const BUILTIN_SKILLS:BuiltinSkill[]='+json.dumps(skills,ensure_ascii=False,indent=2)+';\n')
(root/'scripts/eta-port/skills-provenance.json').write_text(json.dumps({'commit':'3b1794d70881c3c849b53d7778dd2bfdf0a46cc5','sources':sources},ensure_ascii=False,indent=2)+'\n')
print('Generated',len(skills),'built-in Skills; no model registration')
