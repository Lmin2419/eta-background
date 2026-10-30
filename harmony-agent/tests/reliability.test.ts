import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { DataGate } from '../entry/src/main/ets/core/dataGate';
import { PolicyWatch, policyKey, runtimeOptions } from '../entry/src/main/ets/core/runPolicy';
import { ExtensionDocument, ExtensionRepository, KnownApp, parseKnownApps } from '../entry/src/main/ets/core/extensions';
import { ProfileRepository } from '../entry/src/main/ets/core/profiles';
import { ConversationRepository } from '../entry/src/main/ets/core/conversations';
import { BrowserTools, BrowserPort } from '../entry/src/main/ets/core/browser';
import { RunControl, UnknownToolOutcomeError, DeviceTools, ModelClient, ModelReply } from '../entry/src/main/ets/core/types';
import { AgentLoop } from '../entry/src/main/ets/core/loop';
import { KnownAppTools, workflowDraft, importAppCatalog, exportAppCatalog } from '../entry/src/main/ets/core/workflows';
const empty=():ExtensionDocument=>({version:1,skills:[],roles:[],mcp:[],apps:[],roleSelections:{},secondaryProfileId:'',subtasks:false});
const app:KnownApp={name:'Clock',bundle:'com.test.clock',ability:'EntryAbility',verifiedAt:0};
function deferred<T>(){let resolve!:(x:T)=>void;const promise=new Promise<T>(r=>resolve=r);return {promise,resolve};}
function storage(){let raw='';return {read:async()=>raw,write:async(v:string)=>{raw=v;}};}
const profile={id:'',name:'Provider',baseUrl:'https://service.example/v1',model:'m',screenshots:false,allowInsecureHttp:false};

test('restore excludes an in-flight secure-key save and failed saves release the maintenance lease',async()=>{
 const gate=new DataGate(),key=deferred<void>(),entered=deferred<void>();
 const repo=new ProfileRepository({read:async()=>'',write:async()=>{throw new Error('disk full');}},{get:async()=>'',put:async()=>{entered.resolve();await key.promise;},remove:async()=>{}},()=>String(Math.random()),gate);
 const save=repo.save(profile,'secret',true);await entered.promise;assert.throws(()=>gate.maintain(),/进行中/);
 key.resolve();await assert.rejects(save,/disk full/);const release=gate.maintain();await assert.rejects(()=>repo.save(profile,'secret',true),/数据维护/);release();
});
test('queued conversation edits hold maintenance until durable writes and callback publication complete',async()=>{
 const gate=new DataGate(),pending=deferred<void>(),entered=deferred<void>();let held=false,i=0;
 const store={read:async()=>'',write:async()=>{if(held){entered.resolve();await pending.promise;}}};
 const repo=new ConversationRepository(store,()=>String(++i),Date.now,()=>{},1,gate);await repo.load();const id=await repo.create();held=true;
 const first=repo.rename(id,'first'),second=repo.rename(id,'second');await entered.promise;
 assert.throws(()=>gate.maintain(),/进行中/);pending.resolve();await Promise.all([first,second]);assert.equal(repo.get(id)?.title,'second');const release=gate.maintain();await assert.rejects(()=>repo.rename(id,'stale'),/数据维护/);release();
});
test('extension permissions only publish after durable commit; a failed change does not cancel a valid run',async()=>{
 const gate=new DataGate();let fail=false,changed=0;const store={read:async()=>'',write:async()=>{if(fail)throw new Error('disk');}};
 const repo=new ExtensionRepository(store,()=>changed++,gate);await repo.load();await repo.change(d=>{d.apps=[app];});assert.equal(changed,1);
 fail=true;await assert.rejects(()=>repo.change(d=>{d.apps=[];}),/disk/);assert.equal(repo.document.apps.length,1);assert.equal(changed,1);const release=gate.maintain();await assert.rejects(()=>repo.change(d=>{d.apps=[];}),/数据维护/);release();
});
test('cold-start runtime preferences preserve disabled tools and notification choices before a page exists',()=>{
 const options=runtimeOptions({useWorkspace:false,useWeb:true,useSystem:true,memoryEnabled:false,completionAlerts:false,inlineReply:true,searchUrl:'https://search.example',contextTokens:16000});
 assert.equal(options.useWorkspace,false);assert.equal(options.completionAlerts,false);assert.equal(options.inlineReply,true);assert.equal(options.searchUrl,'https://search.example');assert.equal(runtimeOptions({}).useSystem,false);
});
test('changing an active extension stops a late model tool call before execution',async()=>{
 const document=empty();document.mcp=[{id:'m',name:'remote',url:'https://example.com/mcp',enabled:true,enabledTools:['write']}];const options=runtimeOptions({}),watch=new PolicyWatch();
 const response=deferred<ModelReply>(),started=deferred<void>();let executions=0,cancelled=0;
 const model:ModelClient={complete:async()=>{started.resolve();return response.promise;},cancel:()=>{cancelled++;}};
 const device:DeviceTools={invalidate(){},execute:async()=>{executions++;return {ok:true};}};
 const loop=new AgentLoop(model,device,()=>{}, {allowedTools:['custom_write']});watch.watch('run','c',policyKey(document,'c',options),()=>loop.stop());
 const run=loop.run('write');await started.promise;document.mcp[0].enabled=false;watch.refresh(id=>policyKey(document,id,options));
 response.resolve({message:{role:'assistant',content:null,tool_calls:[{id:'x',type:'function',function:{name:'custom_write',arguments:'{}'}}]},finishReason:'tool_calls'});
 await assert.rejects(run,/CANCELLED/);assert.equal(executions,0);assert.equal(cancelled,1);
});
test('policy changes only revoke affected conversations; notification and directory edits do not cancel runs',()=>{
 const document=empty();document.roles=[{id:'role',name:'role',description:'a',greeting:'',world:[]}];document.roleSelections.a='role';const options=runtimeOptions({}),watch=new PolicyWatch(),revoked:string[]=[];
 for(const id of ['a','b'])watch.watch(id,id,policyKey(document,id,options),()=>revoked.push(id));
 options.completionAlerts=false;document.apps=[app];watch.refresh(id=>policyKey(document,id,options));assert.deepEqual(revoked,[]);
 document.roles[0].description='b';watch.refresh(id=>policyKey(document,id,options));assert.deepEqual(revoked,['a']);options.useWorkspace=false;watch.refresh(id=>policyKey(document,id,options));assert.deepEqual(revoked,['a','b']);watch.refresh(id=>policyKey(document,id,options));assert.equal(revoked.length,2);
});
function browserFixture(){
 let clicks=0,covered=false;const style={visibility:'visible',display:'block',opacity:'1'},rect={x:10,y:20,width:80,height:30,left:10,right:90,top:20,bottom:50};
 class Input {stored='';get value(){return this.stored;}set value(v:string){this.stored=v;}}
 const e=Object.assign(new Input(),{tagName:'A',type:'',innerText:'go',href:'/old',disabled:false,readOnly:false,isConnected:true,
  getBoundingClientRect:()=>rect,getAttribute:(k:string)=>k==='href'?e.href:'',contains:(node:unknown)=>node===e,click:()=>clicks++,focus:()=>{},dispatchEvent:()=>{}});
 const env={window:{},location:{href:'https://example.com/'},innerWidth:200,innerHeight:300,getComputedStyle:()=>style,HTMLInputElement:Input,HTMLTextAreaElement:Input,Event,
  document:{querySelectorAll:()=>[e],title:'page',body:{innerText:'page text'},elementFromPoint:()=>covered?{}:e}};
 const port:BrowserPort={ready:()=>true,invalidate(){},navigate:async()=>{},script:async(code)=>vm.runInNewContext(code,env)};
 const tools=new BrowserTools(port);return {e,style,rect,env,tools,port,cover:()=>{covered=true;},clicks:()=>clicks};
}
async function observe(tools:BrowserTools){const r=await tools.execute('browser_read',{},new RunControl());return r.data!.observation_id;}
test('browser refuses a link whose destination changes without changing its label',async()=>{
 const f=browserFixture(),id=await observe(f.tools);f.e.href='/other';await assert.rejects(()=>f.tools.execute('browser_click',{observation_id:id,node_id:0},new RunControl()),/变化/);assert.equal(f.clicks(),0);
});
test('browser refuses covered, disabled, hidden or moved controls before dispatching click',async()=>{
 for(const mutation of [(f:ReturnType<typeof browserFixture>)=>f.cover(),(f:ReturnType<typeof browserFixture>)=>{f.e.disabled=true;},(f:ReturnType<typeof browserFixture>)=>{f.style.visibility='hidden';},(f:ReturnType<typeof browserFixture>)=>{f.rect.x=50;}]){
  const f=browserFixture(),id=await observe(f.tools);mutation(f);await assert.rejects(()=>f.tools.execute('browser_click',{observation_id:id,node_id:0},new RunControl()));assert.equal(f.clicks(),0);
 }
});
test('browser input uses native setter, refuses passwords and checks exact action acknowledgement',async()=>{
 const f=browserFixture();f.e.tagName='INPUT';f.e.type='text';let id=await observe(f.tools);await f.tools.execute('browser_input',{observation_id:id,node_id:0,text:'hello'},new RunControl());assert.equal(f.e.stored,'hello');
 f.e.type='password';id=await observe(f.tools);await assert.rejects(()=>f.tools.execute('browser_input',{observation_id:id,node_id:0,text:'secret'},new RunControl()));assert.equal(f.e.stored,'hello');
 f.e.type='text';id=await observe(f.tools);f.port.script=async()=>'NOT_ACCEPTED';await assert.rejects(()=>f.tools.execute('browser_click',{observation_id:id,node_id:0},new RunControl()),UnknownToolOutcomeError);
});
test('browser screenshot is discarded when the page is taken over during capture',async()=>{
 const f=browserFixture(),capture=deferred<string>();f.port.snapshot=()=>capture.promise;const pending=f.tools.execute('browser_snapshot',{},new RunControl());await Promise.resolve();await Promise.resolve();f.tools.invalidate();capture.resolve('data:image/png;base64,x');await assert.rejects(pending,/变化/);
});
test('imported application catalog never grants historical verification or silently accepts conflicts',()=>{
 const exported=exportAppCatalog([{...app,verifiedAt:Date.now()}]);assert.equal(importAppCatalog(exported)[0].verifiedAt,0);
 assert.throws(()=>parseKnownApps([app,app]),/重复/);assert.throws(()=>importAppCatalog(JSON.stringify({format:'harmony-agent-apps',version:2,apps:[]})),/版本/);
 assert.throws(()=>parseKnownApps([{...app,bundle:'com.test;id'}]),/入口/);
});
test('known app launch resolves only the configured ability and records verified screen evidence',async()=>{
 let launches=0,seen=0;const device:DeviceTools={invalidate(){},async execute(name,args){assert.equal(name,'launch_app');assert.equal(args.ability,'EntryAbility');launches++;return {ok:true,observation:{id:'o',capturedAt:0,width:1,height:1,fingerprint:'x',nodes:[],truncated:false,bundleName:app.bundle}};}};
 const tools=new KnownAppTools([app],device,async a=>{seen++;assert.ok(a.verifiedAt>0);});await assert.rejects(()=>tools.execute('open_known_app',{bundle:'com.other.app'},new RunControl()),/不在/);assert.equal(launches,0);
 assert.equal((await tools.execute('open_known_app',{bundle:app.bundle},new RunControl())).ok,true);assert.equal(seen,1);
});
test('a launch without target-page evidence is not marked verified; failed evidence persistence stops retries',async()=>{
 let seen=0;const device:DeviceTools={invalidate(){},async execute(){return {ok:true};}};const tools=new KnownAppTools([app],device,async()=>{seen++;});assert.equal((await tools.execute('open_known_app',{bundle:app.bundle},new RunControl())).ok,false);assert.equal(seen,0);
 const known:DeviceTools={invalidate(){},async execute(){return {ok:true,observation:{id:'o',capturedAt:0,width:1,height:1,fingerprint:'x',nodes:[],truncated:false,bundleName:app.bundle}};}};
 const failing=new KnownAppTools([app],known,async()=>{throw new Error('disk');});await assert.rejects(()=>failing.execute('open_known_app',{bundle:app.bundle},new RunControl()),UnknownToolOutcomeError);
});
test('workflow drafts reject ambiguous clock values and never execute during preparation',()=>{
 for(const time of ['25:00','7:30','07:70','tomorrow'])assert.throws(()=>workflowDraft({kind:'alarm',app,time,title:'',body:''}),/24 小时/);
 const note=workflowDraft({kind:'note',app,time:'',title:'List',body:'a\nb'});assert.ok(note.goal.includes('不能覆盖'));assert.ok(note.goal.includes('"a\\nb"'));
 assert.throws(()=>workflowDraft({kind:'note',app,time:'',title:'',body:'body'}),/请填写/);assert.throws(()=>workflowDraft({kind:'shell',app,time:'',title:'',body:''}),/未知/);
});

import { ToolRouter } from '../entry/src/main/ets/core/workspace';
test('remote and secondary credentials are redacted from nested tool results before another model sees them',async()=>{
 const secrets=['remote-key-123'],router=new ToolRouter(secrets);router.register(['remote'],{invalidate(){},async execute(){return {ok:true,data:{nested:['remote-key-123',{'value':'secondary-key-456'}]}};}});secrets.push('secondary-key-456');
 const result=JSON.stringify(await router.execute('remote',{},new RunControl()));assert.ok(!result.includes('remote-key-123'));assert.ok(!result.includes('secondary-key-456'));assert.ok(result.includes('已隐藏密钥'));
});
test('redacting an unknown tool outcome preserves its fatal type and does not turn it into a retryable error',async()=>{
 const router=new ToolRouter(['private-key']);router.register(['remote'],{invalidate(){},async execute(){throw new UnknownToolOutcomeError('transport private-key');}});
 await assert.rejects(()=>router.execute('remote',{},new RunControl()),e=>e instanceof UnknownToolOutcomeError&&!e.message.includes('private-key'));
});
test('name-only workflow drafts fall back to visible desktop lookup without inventing package or ability names',()=>{
 const draft=workflowDraft({kind:'inspect',appName:'时钟',time:'',title:'',body:''});assert.ok(draft.goal.includes('时钟'));assert.ok(draft.goal.includes('不要编造 bundle 或 ability'));assert.ok(!draft.goal.includes('EntryAbility'));
 assert.throws(()=>workflowDraft({kind:'inspect',appName:' ',time:'',title:'',body:''}),/应用名称/);
});
