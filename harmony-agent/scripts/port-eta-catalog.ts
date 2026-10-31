// Ported from app/src/main/assets/catalog/models-dev-api.json.gzip @ 3b1794d.
// Snapshot data: Copyright (c) 2025 models.dev, MIT; see licenses/models-dev-MIT.txt.
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';import {gunzipSync} from 'node:zlib';import {createHash} from 'node:crypto';
import {parseCommunityCatalog} from '../entry/src/main/ets/core/eta/communityCatalog';
const path=process.argv[2];if(!path)throw Error('Usage: npx tsx scripts/port-eta-catalog.ts /path/to/pinned.gzip');const bytes=readFileSync(path);const hash=createHash('sha1').update(Buffer.from(`blob ${bytes.length}\0`)).update(bytes).digest('hex');if(hash!=='9d9c322cf19c1c129e70da1947586521e8aa2160')throw Error('Pinned snapshot hash mismatch');
const raw=JSON.parse(gunzipSync(bytes).toString('utf8'));const catalog=parseCommunityCatalog(JSON.stringify(raw),'snapshot');const filtered:Record<string,unknown>={};
for(const p of catalog.providers){const old=raw[p.id];filtered[p.id]={name:old.name,npm:old.npm,api:old.api,models:Object.fromEntries(p.models.map(m=>[m.modelId,Object.fromEntries(['name','tool_call','modalities','limit','status','reasoning','structured_output','temperature','release_date'].filter(k=>k in old.models[m.modelId]).map(k=>[k,old.models[m.modelId][k]]))]))};}
// Same parser inputs, only ignored fields/non-candidates removed; source endpoints remain untouched.
const output=JSON.stringify(filtered);const reparsed=parseCommunityCatalog(output,'snapshot');if(JSON.stringify(catalog)!==JSON.stringify(reparsed))throw Error('Snapshot compaction changed candidates');
mkdirSync('entry/src/main/resources/rawfile/catalog',{recursive:true});writeFileSync('entry/src/main/resources/rawfile/catalog/models-dev-api.json',output+'\n');console.log({providers:catalog.providers.length,models:catalog.providers.reduce((n,p)=>n+p.models.length,0),bytes:Buffer.byteLength(output)});
