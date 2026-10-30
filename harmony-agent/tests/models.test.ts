import test from 'node:test';
import assert from 'node:assert/strict';
import { HttpTransport, ModelSettings, OpenAIClient, modelEndpoint, fetchModels, testModel } from '../entry/src/main/ets/core/model';
import { ProfileRepository, ModelProfile, ProfileStorage, SecretStorage } from '../entry/src/main/ets/core/profiles';
import { RunControl } from '../entry/src/main/ets/core/types';
const settings: ModelSettings={baseUrl:'https://provider.example/v1',model:'vision-model',apiKey:'fixture-only',screenshots:true};
const message=(calls?:unknown[])=>JSON.stringify({choices:[{message:{role:'assistant',content:calls?null:'OK',...(calls?{tool_calls:calls}:{})},finish_reason:calls?'tool_calls':'stop'}]});
const call={id:'check-1',type:'function',function:{name:'connection_check',arguments:'{"ok":true}'}};
class Transport implements HttpTransport {
  requests:{url:string;headers:Record<string,string>;body:string}[]=[];
  response=message();
  async post(url:string,headers:Record<string,string>,body:string){this.requests.push({url,headers,body});return this.response;}
  async get(url:string,headers:Record<string,string>){this.requests.push({url,headers,body:''});return this.response;}
  cancel(){}
}
test('full chat endpoints and provider path prefixes normalize without duplication',()=>{
  assert.equal(modelEndpoint({...settings,baseUrl:' https://provider.example/api/v1/chat/completions/ '}),'https://provider.example/api/v1/chat/completions');
  assert.equal(modelEndpoint({...settings,baseUrl:'https://provider.example/api/v1/chat/completions'},'models'),'https://provider.example/api/v1/models');
});
test('addresses reject embedded credentials, query parameters, fragments and invalid ports',()=>{
  for(const baseUrl of ['https://user:key@host/v1','https://host/v1?key=abc','https://host/v1#f','https://host:0/v1','https://host:65536/v1','https://host\\evil/v1','https://host/\nfoo','file:///v1'])assert.throws(()=>modelEndpoint({...settings,baseUrl}));
});
test('HTTP is explicit opt-in and no-auth servers receive no empty bearer token',async()=>{
  const transport=new Transport();const local={...settings,baseUrl:'http://192.168.1.2:8080/v1',apiKey:''};
  await assert.rejects(new OpenAIClient(local,transport).complete([],new RunControl()),/HTTPS/);
  assert.equal(transport.requests.length,0);
  await new OpenAIClient({...local,allowInsecureHttp:true},transport).complete([],new RunControl());
  assert.equal(transport.requests[0].headers.Authorization,undefined);
});
test('model calls reject header injection before transport',async()=>{
  const transport=new Transport();
  await assert.rejects(new OpenAIClient({...settings,apiKey:'abc\r\nx-evil: 1'},transport).complete([],new RunControl()),/无效字符/);
  assert.equal(transport.requests.length,0);
});
test('model discovery deduplicates IDs without truncating normal provider catalogs',async()=>{
  const transport=new Transport();transport.response=JSON.stringify({data:[{id:'same'},{id:'same'},{},...Array.from({length:250},(_,i)=>({id:'m'+i}))]});
  const result=await fetchModels(settings,transport);
  assert.equal(result.ids.length,251);assert.equal(result.partial,false);assert.equal(new Set(result.ids).size,251);
  assert.equal(transport.requests[0].url,'https://provider.example/v1/models');
});
test('unsupported model catalog gives a manual-entry fallback',async()=>{
  const transport=new Transport();transport.response='{"models":["a"]}';await assert.rejects(fetchModels(settings,transport),/手动/);
});
test('connection test sends synthetic text with only a harmless tool',async()=>{
  const transport=new Transport();transport.response=message([call]);
  assert.match(await testModel(settings,transport),/工具调用测试通过/);
  const body=JSON.parse(transport.requests[0].body);
  assert.equal(body.tools.length,1);assert.equal(body.tools[0].function.name,'connection_check');assert.equal(body.messages.length,1);
  assert.equal(body.messages[0].role,'user');assert.equal(typeof body.messages[0].content,'string');
  assert.ok(!transport.requests[0].body.includes('observe_screen'));
});
test('a text-only reply is not presented as verified tool support',async()=>{
  const transport=new Transport();assert.match(await testModel(settings,transport),/未验证 Tool Calling/);
});
test('probe refuses malformed or truncated tool results',async()=>{
  const transport=new Transport();transport.response=message([{...call,function:{name:'connection_check',arguments:'{"ok":false}'}}]);
  await assert.rejects(testModel(settings,transport),/参数无效/);
  transport.response=message([call]).replace('"finish_reason":"tool_calls"','"finish_reason":"length"');
  await assert.rejects(testModel(settings,transport),/截断/);
});
test('parse errors never expose server response or credentials',async()=>{
  const transport=new Transport();transport.response='<html>fixture-only credentials</html>';
  await assert.rejects(new OpenAIClient(settings,transport).complete([],new RunControl()),e=>e instanceof Error && !e.message.includes('fixture-only') && e.message.includes('JSON'));
});
test('cancelled calls cannot publish a late response',async()=>{
  const control=new RunControl();const transport=new Transport();transport.post=async()=>{control.stop();return message([call]);};
  await assert.rejects(new OpenAIClient(settings,transport).complete([],control),/CANCELLED/);
});
function fixture(){
  let raw='';let failWrite=false;let failSecret=false;let failRemove=false;let seq=0;
  const secrets=new Map<string,string>();
  const metadata:ProfileStorage={read:async()=>raw,write:async(value)=>{if(failWrite)throw new Error('disk full');raw=value;}};
  const secure:SecretStorage={get:async alias=>{if(!secrets.has(alias))throw new Error('missing secret');return secrets.get(alias)!;},put:async(alias,value)=>{if(failSecret)throw new Error('asset unavailable');secrets.set(alias,value);},remove:async alias=>{if(failRemove)throw new Error('asset unavailable');secrets.delete(alias);}};
  const repo=new ProfileRepository(metadata,secure,()=>String(++seq));
  const profile:ModelProfile={id:'',name:'A',baseUrl:settings.baseUrl,model:settings.model,screenshots:true,allowInsecureHttp:false};
  return {repo,profile,secrets,metadata,secure,raw:()=>raw,writeFail:()=>failWrite=true,secretFail:()=>failSecret=true,removeFail:()=>failRemove=true};
}
test('multiple profiles keep keys separate and no plaintext keys enter metadata',async()=>{
  const f=fixture();const a=await f.repo.save({...f.profile,apiKey:'do-not-persist'} as ModelProfile,'alpha-secret',true);
  const b=await f.repo.save({...f.profile,name:'B',baseUrl:'https://second.example/v1'},'beta-secret',true);
  assert.equal(await f.repo.key(a),'alpha-secret');assert.equal(await f.repo.key(b),'beta-secret');
  assert.ok(!f.raw().includes('alpha-secret'));assert.ok(!f.raw().includes('beta-secret'));assert.ok(!f.raw().includes('apiKey'));
  const loaded=new ProfileRepository(f.metadata,f.secure,()=> 'next');await loaded.load();
  assert.equal(loaded.document.selectedId,b.id);assert.equal(await loaded.key(loaded.document.profiles[0]),'alpha-secret');
});
test('metadata failure preserves old provider/key and removes the staged secret',async()=>{
  const f=fixture();const old=await f.repo.save(f.profile,'old-key',true);const before=f.raw();f.writeFail();
  await assert.rejects(f.repo.save({...old,baseUrl:'https://new.example/v1'},'new-key',true),/disk full/);
  assert.equal(f.raw(),before);assert.equal(await f.repo.key(old),'old-key');assert.equal(f.secrets.size,1);
});
test('secure storage failure never falls back to plaintext',async()=>{
  const f=fixture();f.secretFail();await assert.rejects(f.repo.save(f.profile,'key-value',true),/asset unavailable/);
  assert.equal(f.raw(),'');assert.equal(f.repo.document.profiles.length,0);
});
test('turning off remember removes the old secret after committing metadata',async()=>{
  const f=fixture();const old=await f.repo.save(f.profile,'old-key',true);
  const saved=await f.repo.save(old,'session-key',false);
  assert.equal(saved.secretRef,undefined);assert.equal(f.secrets.size,0);assert.ok(!f.raw().includes('session-key'));
});
test('deletion metadata failure leaves the old key available',async()=>{
  const f=fixture();const old=await f.repo.save(f.profile,'old-key',true);f.writeFail();
  await assert.rejects(f.repo.remove(old.id),/disk full/);assert.equal(await f.repo.key(old),'old-key');
});
test('deleting one profile leaves the other usable',async()=>{
  const f=fixture();const a=await f.repo.save(f.profile,'a-key',true);const b=await f.repo.save({...f.profile,name:'B'},'b-key',true);
  await f.repo.remove(b.id);assert.equal(f.repo.document.selectedId,a.id);assert.equal(await f.repo.key(a),'a-key');assert.equal(f.secrets.size,1);
});
test('cleanup failure is reported without undoing a committed profile update',async()=>{
  const f=fixture();const old=await f.repo.save(f.profile,'old-key',true);f.removeFail();
  const saved=await f.repo.save(old,'new-key',true);assert.equal(await f.repo.key(saved),'new-key');assert.match(f.repo.warning,/清理失败/);
});
test('corrupt profile metadata is not silently reset on load',async()=>{
  const f=fixture();await f.metadata.write('{broken');await assert.rejects(f.repo.load());assert.equal(f.raw(),'{broken');
});

test('imported catalog and stream preference survive reload, older profiles gain a one-model catalog',async()=>{
  const f=fixture();await f.repo.save({...f.profile,models:['a','b','a'],model:'b',streaming:false},'',false);
  const reloaded=new ProfileRepository(f.metadata,f.secure,()=> 'n');await reloaded.load();
  assert.deepEqual(reloaded.document.profiles[0].models,['a','b']);assert.equal(reloaded.document.profiles[0].streaming,false);
  const old=JSON.parse(f.raw());delete old.profiles[0].models;delete old.profiles[0].streaming;await f.metadata.write(JSON.stringify(old));
  await reloaded.load();assert.deepEqual(reloaded.document.profiles[0].models,['b']);assert.equal(reloaded.document.profiles[0].streaming,true);
});
