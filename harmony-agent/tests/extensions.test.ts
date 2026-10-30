import test from 'node:test';
import assert from 'node:assert/strict';
import { ProtocolStream, protocolRequest, completeProtocol } from '../entry/src/main/ets/core/protocols';
import { RunControl, Observation, Message } from '../entry/src/main/ets/core/types';
import { WorkspaceTools, WorkspaceBackend, safeWorkspaceName, ToolRouter } from '../entry/src/main/ets/core/workspace';
import { evidence, NoProgressTracker, scrollTarget } from '../entry/src/main/ets/core/actionEvidence';
import { DraftRepository } from '../entry/src/main/ets/core/drafts';
const frame=(v:object)=>'data: '+JSON.stringify(v)+'\n\n';
const settings={baseUrl:'https://models.example/v1',model:'fixture',apiKey:'secret',screenshots:true};
const functionCall={type:'function_call',call_id:'c1',name:'file_list',arguments:'{}'};
test('Responses only executes a completed response and preserves encrypted reasoning across tool turns',()=>{
  let text='';const p=new ProtocolStream('responses',t=>text=t,()=>{});
  p.feed(frame({type:'response.output_text.delta',delta:'查找'}));assert.equal(text,'查找');
  assert.throws(()=>p.finish(),/结束事件/);
  p.feed(frame({type:'response.completed',response:{status:'completed',output:[{type:'reasoning',encrypted_content:'opaque'},functionCall]}}));
  const reply=p.finish();assert.equal(reply.finishReason,'tool_calls');
  const request=protocolRequest('responses',settings,[reply.message,{role:'tool',tool_call_id:'c1',content:'ok'}],[],true);
  assert.equal((request.input as any[])[0].encrypted_content,'opaque');
  assert.equal((request.input as any[])[2].call_id,'c1');
});
test('Anthropic fragmented tool arguments require all block and message boundaries',()=>{
  const p=new ProtocolStream('anthropic',()=>{},()=>{});
  const events=[{type:'content_block_start',index:0,content_block:{type:'tool_use',id:'t1',name:'file_list',input:{}}},{type:'content_block_delta',index:0,delta:{type:'input_json_delta',partial_json:'{}'}},{type:'content_block_stop',index:0},{type:'message_delta',delta:{stop_reason:'tool_use'}},{type:'message_stop'}];
  const stream=events.map(frame).join('');for(const c of stream)p.feed(c);
  const r=p.finish();assert.equal(r.message.tool_calls?.[0].function.arguments,'{}');assert.equal(r.finishReason,'tool_calls');
  const incomplete=new ProtocolStream('anthropic',()=>{},()=>{});incomplete.feed(frame(events[0]));assert.throws(()=>incomplete.feed(frame(events[4])),/完整结束/);
});
test('Anthropic retains thinking signatures without leaking internal assembly fields to provider',()=>{
  const p=new ProtocolStream('anthropic',()=>{},()=>{});
  for(const e of [{type:'content_block_start',index:0,content_block:{type:'thinking',thinking:''}},{type:'content_block_delta',index:0,delta:{type:'thinking_delta',thinking:'summary'}},{type:'content_block_delta',index:0,delta:{type:'signature_delta',signature:'sig'}},{type:'content_block_stop',index:0},{type:'content_block_start',index:1,content_block:{type:'text',text:'done'}},{type:'content_block_stop',index:1},{type:'message_delta',delta:{stop_reason:'end_turn'}},{type:'message_stop'}])p.feed(frame(e));
  const r=p.finish(),req=protocolRequest('anthropic',settings,[r.message],[],true);
  const b=(req.messages as any[])[0].content[0];assert.equal(b.signature,'sig');assert.equal(b.partial,undefined);assert.equal(b.closed,undefined);
});
test('Gemini preserves function thought signatures and correlates function response names',()=>{
  const p=new ProtocolStream('gemini',()=>{},()=>{});
  p.feed(frame({candidates:[{content:{parts:[{functionCall:{name:'file_list',args:{}},thoughtSignature:'opaque'}]},finishReason:'STOP'}]}));
  const r=p.finish();const req=protocolRequest('gemini',settings,[r.message,{role:'tool',tool_call_id:r.message.tool_calls![0].id,content:'ok'}],[],true);
  const entries=req.contents as any[];assert.equal(entries[0].parts.find((p:any)=>p.functionCall).thoughtSignature,'opaque');assert.equal(entries[1].parts[0].functionResponse.name,'file_list');
});
test('provider protocol authentication is isolated and cancellation rejects late stream tools',async()=>{
  const control=new RunControl();let seen:any;
  await assert.rejects(()=>completeProtocol('anthropic','https://model.example/v1',settings,{post:async()=>'',cancel(){},stream:async(url,headers,body,chunk)=>{seen={url,headers,body};control.stop();chunk(frame({type:'message_stop'}));}},[],[{role:'user',content:'hi'}],control,()=>{},()=>{}),/CANCELLED/);
  assert.equal(seen.headers.Authorization,undefined);assert.equal(seen.headers['x-api-key'],'secret');assert.ok(seen.url.endsWith('/messages'));
});
class MemoryWorkspace implements WorkspaceBackend { files=new Map<string,string>();async list(){return [...this.files].map(([name,text])=>({name,size:text.length,modifiedAt:0}));}async read(n:string){if(!this.files.has(n))throw new Error('missing');return this.files.get(n)!;}async write(n:string,t:string){this.files.set(n,t);}async remove(n:string){this.files.delete(n);}}
test('workspace rejects traversal, ambiguous edits and disabled memory access',async()=>{
  for(const n of ['../x','/x','a/b','.secret','a\\b'])assert.throws(()=>safeWorkspaceName(n));
  const backend=new MemoryWorkspace();backend.files.set('a.txt','x x');backend.files.set('MEMORY.md','private');const tools=new WorkspaceTools(backend),c=new RunControl();
  await assert.rejects(()=>tools.execute('file_edit',{name:'a.txt',old_text:'x',new_text:'y'},c),/唯一/);assert.equal(await backend.read('a.txt'),'x x');
  await assert.rejects(()=>tools.execute('file_read',{name:'MEMORY.md'},c),/关闭/);
  assert.deepEqual((await tools.execute('file_list',{},c)).data,{files:[{name:'a.txt',size:3,modifiedAt:0}]});
  tools.memoryEnabled=true;assert.equal(await tools.memory(),'private');
  c.stop();await assert.rejects(()=>tools.execute('file_write',{name:'a.txt',text:'changed'},c),/CANCELLED/);assert.equal(await backend.read('a.txt'),'x x');
});
test('tool registry cannot execute disabled tools',async()=>{const r=new ToolRouter();r.register(['file_read'],new WorkspaceTools(new MemoryWorkspace()));await assert.rejects(()=>r.execute('file_write',{},new RunControl()),/未注册/);});
const obs=(fingerprint:string,rotation=0):Observation=>({id:'a',width:100,height:100,rotation,fingerprint,capturedAt:0,nodes:[],truncated:false});
test('action evidence separates effects from goals and bounds no-progress retries with TTL',()=>{
  const a=obs('same');assert.equal(evidence(a,obs('changed'),true).outcome,'CHANGED');assert.equal(evidence(a,obs('changed',1),true).outcome,'UNKNOWN');assert.equal(evidence(a,a,true).goalVerified,false);
  let now=0;const guard=new NoProgressTracker(()=>now);for(let i=0;i<3;i++){assert.equal(guard.blocked('scroll',a),false);guard.record('scroll',a,a);}assert.equal(guard.blocked('scroll',a),true);now=120001;assert.equal(guard.blocked('scroll',a),false);
  assert.throws(()=>scrollTarget([],undefined),/未识别/);
});
test('damaged drafts refuse later writes and preserve original bytes',async()=>{let value='broken';const draft=new DraftRepository({read:async()=>value,write:async(v)=>{value=v;}});await assert.rejects(()=>draft.load());draft.setMessage('c','new');await assert.rejects(()=>draft.flush(),/原草稿/);assert.equal(value,'broken');});
