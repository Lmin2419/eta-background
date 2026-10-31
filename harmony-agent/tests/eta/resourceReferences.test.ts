// Harmony resource seam for Eta UI ports. Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';import assert from 'node:assert/strict';import {readdirSync,readFileSync} from 'node:fs';import {join,basename,extname} from 'node:path';
function files(root:string):string[]{return readdirSync(root,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(join(root,e.name)):[join(root,e.name)]);}
test('all statically referenced Eta UI and platform resources resolve in packaged resources',()=>{
 const available=new Set<string>();
 for(const root of ['entry/src/main/resources/base','AppScope/resources/base'])for(const path of files(root)){
  if(path.includes('/element/')&&path.endsWith('.json')){const doc=JSON.parse(readFileSync(path,'utf8'));for(const [kind,rows]of Object.entries(doc))for(const row of rows as {name:string}[])available.add(kind+'.'+row.name);}
  else if(path.includes('/media/'))available.add('media.'+basename(path,extname(path)));
 }
 const sources=[...files('entry/src/main/ets/ui/eta'),...files('entry/src/main/ets/platform').filter(p=>basename(p).startsWith('Eta'))];const missing:string[]=[];
 for(const path of sources)for(const match of readFileSync(path,'utf8').matchAll(/\$r\('app\.([^']+)'/g))if(!available.has(match[1]))missing.push(path+': '+match[1]);
 assert.deepEqual(missing,[]);
});
