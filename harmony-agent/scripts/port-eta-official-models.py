#!/usr/bin/env python3
# Ported from app/src/main/kotlin/io/github/mangi/eta/data/provider/OfficialModelCatalog.kt @ 3b1794d.
# Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
"""Generate the pinned model metadata, retaining every source model and stable ID."""
import json
import re
from eta_port_common import arguments, emit

args = arguments(__doc__)
source = 'app/src/main/kotlin/io/github/mangi/eta/data/provider/OfficialModelCatalog.kt'
text = (args.eta_root / source).read_text()
catalog = {}
for group in re.finditer(r'ProviderSourceTypes\.(\w+) to listOf\((.*?)(?=\n        ProviderSourceTypes\.|\n    \)\n\n    fun)', text, re.S):
    name, body = group.group(1).lower(), group.group(2)
    rows = []
    for call in re.finditer(r'officialModel\((.*?)\n            \)', body, re.S):
        fields = dict(re.findall(r'(\w+) = (listOf\([^)]*\)|"[^"]*"|true|false|[\d_]+)', call.group(1)))
        row = {}
        for key, value in fields.items():
            if value.startswith('listOf'):
                value = [x.strip().strip('"').replace('Model.TEXT_MODALITY','text').replace('Model.IMAGE_MODALITY','image') for x in value[7:-1].split(',') if x.strip()]
            else:
                value = json.loads(value if value.startswith('"') else value.replace('_',''))
            row[{'id':'catalogId','modelId':'id','displayName':'name','contextWindow':'catalogContextWindow'}.get(key,key)] = value
        row.update(source='catalog', inputModalities=row.get('inputModalities',['text']), outputModalities=row.get('outputModalities',['text']))
        if name in ('openai','gemini'):
            row['contextWindow'] = row['catalogContextWindow']
        rows.append(row)
    catalog[name] = rows
assert len(catalog) == 9 and sum(map(len,catalog.values())) == 31, 'Pinned catalog shape changed; review upstream before regenerating'
out = '// Generated from ' + source + ' @ 3b1794d; run scripts/port-eta-official-models.py.\n// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).\nimport type { ModelMetadata } from "../modelMetadata";\nexport const OFFICIAL_MODELS: Record<string, ModelMetadata[]> = ' + json.dumps(catalog,ensure_ascii=False,indent=2) + ';\n'
emit(args.output_root / 'entry/src/main/ets/core/eta/officialModels.generated.ts', out, args.check)
