import test from 'node:test';
import assert from 'node:assert/strict';
import { ConversationRepository, ConversationStorage, Conversation, conversationContext, CHAT_LIMITS } from '../entry/src/main/ets/core/conversations';
import { ConversationRun } from '../entry/src/main/ets/core/conversationRun';
import { AgentLoop } from '../entry/src/main/ets/core/loop';
import { AgentTask } from '../entry/src/main/ets/core/session';
import { DeviceTools, Message, ModelClient, ModelReply, RunControl } from '../entry/src/main/ets/core/types';
import { HttpTransport, OpenAIClient } from '../entry/src/main/ets/core/model';
function deferred<T>() {let resolve!:(v:T)=>void;const promise=new Promise<T>(r=>resolve=r);return {resolve,promise};}
class Storage implements ConversationStorage {
  raw='';writes=0;fail=false;gate:((raw:string)=>Promise<void>)|undefined;
  async read(){return this.raw;}
  async write(raw:string){await this.gate?.(raw);if(this.fail)throw new Error('DISK_FULL');this.raw=raw;this.writes++;}
}
async function setup(){
  const storage=new Storage();let seq=0,tick=0;
  const repo=new ConversationRepository(storage,()=>String(++seq),()=>++tick);
  await repo.load();const id=await repo.create('provider-a');
  return {repo,storage,id};
}
const done:ModelReply={message:{role:'assistant',content:'记住了'},finishReason:'stop'};
const tool:ModelReply={message:{role:'assistant',content:null,tool_calls:[{id:'call',type:'function',function:{name:'observe_screen',arguments:'{}'}}]},finishReason:'tool_calls'};
const start={kind:'tool',text:'ignored',toolName:'observe_screen',stepId:'step_0_0'};
const finish={kind:'result',text:'ignored',toolName:'observe_screen',stepId:'step_0_0'};
function inertDevice(){let calls=0;return {device:{execute:async()=>{calls++;return {ok:true};},invalidate:()=>{}} as DeviceTools,calls:()=>calls};}
function makeRun(repo:ConversationRepository,id:string,factory:(history:Message[],journal:(e:typeof start)=>Promise<void>)=>AgentTask){
  return new ConversationRun(repo,{conversationId:id,text:'你好 fixture-model-secret',modelLabel:'test',profileId:'provider-a',mode:'chat',secrets:['fixture-model-secret']},(context,journal)=>factory(context.messages,journal));
}

test('conversation persists text, mode, model reference and independent history across restart',async()=>{
  const {repo,storage,id}=await setup();const t=await repo.begin(id,'我叫小明','model-a','provider-a','chat');await repo.finish(id,t.turnId,'completed','你好，小明');
  const other=await repo.create('provider-b','device');const second=await repo.begin(other,'另一个任务','model-b','provider-b','device');await repo.finish(other,second.turnId,'stopped','','已停止');
  const loaded=new ConversationRepository(storage,()=> 'new');await loaded.load();assert.equal(loaded.selectedId,other);
  assert.equal(loaded.get(id)?.turns[0].reply,'你好，小明');assert.equal(loaded.get(other)?.mode,'device');assert.equal(loaded.get(id)?.profileId,'provider-a');
});
test('search covers old complete messages beyond the display page and supports rename/delete',async()=>{
  const {repo,id}=await setup();for(let i=0;i<25;i++){const t=await repo.begin(id,'消息 '+i,'model','','chat');await repo.finish(id,t.turnId,'completed',i===0?'earliest needle':'回答');}
  assert.equal(repo.list('needle')[0].id,id);await repo.rename(id,'工作计划');assert.equal(repo.list('工作')[0].title,'工作计划');
  const other=await repo.create();await repo.remove(id);assert.equal(repo.get(id),undefined);assert.equal(repo.get(other)?.turns.length,0);
});
test('restart marks unfinished steps interrupted without exposing runnable tool calls',async()=>{
  const {repo,storage,id}=await setup();const t=await repo.begin(id,'打开设置','model','','device');await repo.step(id,t.turnId,start);
  const writes=storage.writes;const loaded=new ConversationRepository(storage,()=> 'new');assert.equal(await loaded.load(),1);assert.equal(storage.writes,writes+1);
  const c=loaded.get(id)!;assert.equal(c.turns[0].status,'interrupted');assert.equal(c.turns[0].steps[0].status,'interrupted');
  const context=conversationContext(c);assert.match(String(context.messages[1].content),/不能作为继续执行/);assert.ok(!JSON.stringify(context).includes('tool_calls'));
  const again=new ConversationRepository(storage,()=> 'newer');assert.equal(await again.load(),0);
});
test('failed startup recovery preserves disk and refuses writes until loaded',async()=>{
  const {repo,storage,id}=await setup();await repo.begin(id,'任务','m','','device');const raw=storage.raw;storage.fail=true;
  const loaded=new ConversationRepository(storage,()=> 'new');await assert.rejects(loaded.load(),/DISK_FULL/);assert.equal(storage.raw,raw);await assert.rejects(loaded.create(),/载入/);
});
test('corrupted and newer-version history is never silently replaced',async()=>{
  for(const raw of ['broken','{"version":7,"selectedId":"","conversations":[]}',JSON.stringify({version:1,selectedId:'x',conversations:[{id:'x',turns:[]}]})]){
    const storage=new Storage();storage.raw=raw;const repo=new ConversationRepository(storage,()=> 'id');await assert.rejects(repo.load());assert.equal(storage.raw,raw);assert.equal(storage.writes,0);
  }
});
test('serialized mutations do not lose concurrent create operations',async()=>{
  const {repo}=await setup();await Promise.all([repo.create(),repo.create(),repo.create()]);assert.equal(repo.list().length,4);assert.equal(new Set(repo.list().map(c=>c.id)).size,4);
});
test('only one active turn can exist across conversations',async()=>{
  const {repo,id}=await setup();const other=await repo.create();const a=await repo.begin(id,'a','m','','chat');await assert.rejects(repo.begin(other,'b','m','','device'),/已有任务/);
  await assert.rejects(repo.remove(id),/停止/);await assert.rejects(repo.rename(id,'new'),/停止/);await assert.rejects(repo.preferences(id,'other','device'),/停止/);
  await repo.finish(id,a.turnId,'completed','done');await repo.begin(other,'b','m','','chat');
});
test('history write failure publishes no staged turn and does not poison later operations',async()=>{
  const {repo,storage,id}=await setup();storage.fail=true;await assert.rejects(repo.begin(id,'first','m','','chat'),/DISK_FULL/);assert.equal(repo.get(id)?.turns.length,0);
  storage.fail=false;await repo.begin(id,'second','m','','chat');assert.equal(repo.get(id)?.turns.length,1);
});
test('journal stores step metadata only, rejects replay, and ignores raw tool output',async()=>{
  const {repo,storage,id}=await setup();const t=await repo.begin(id,'检查','m','','device');await repo.step(id,t.turnId,{...start,text:'private-screen-data'});
  await assert.rejects(repo.step(id,t.turnId,start),/重复/);await repo.step(id,t.turnId,{...finish,text:'private-result-with-screenshot'});
  await repo.finish(id,t.turnId,'completed','done');assert.ok(!storage.raw.includes('private-'));assert.equal(repo.get(id)?.turns[0].steps[0].status,'completed');
  await assert.rejects(repo.step(id,t.turnId,finish),/迟到/);await assert.rejects(repo.finish(id,t.turnId,'completed','late'),/迟到/);
});
test('unknown and prototype-like tool names are not persisted as executable names',async()=>{
  const {repo,id}=await setup();const t=await repo.begin(id,'检查','m','','device');await repo.step(id,t.turnId,{...start,toolName:'constructor'});
  assert.equal(repo.get(id)?.turns[0].steps[0].tool,'unknown');
});
test('repository snapshots cannot mutate stored history',async()=>{
  const {repo,id}=await setup();const view=repo.get(id)!;view.title='tampered';view.turns.push({} as never);assert.equal(repo.get(id)?.title,'新会话');assert.equal(repo.get(id)?.turns.length,0);
});
test('context keeps whole recent turns under its budget and reports omitted history',()=>{
  const c:Conversation={id:'c',title:'T',createdAt:0,updatedAt:0,profileId:'',mode:'chat',turns:[]};
  for(let i=0;i<30;i++)c.turns.push({id:String(i),request:'q'+i,reply:'a'.repeat(3000),status:'completed',mode:'chat',modelLabel:'m',startedAt:i,steps:[],notice:''});
  const context=conversationContext(c);assert.ok(context.omittedTurns>0);assert.ok(context.messages.reduce((n,m)=>n+String(m.content).length,0)<=CHAT_LIMITS.context);
  assert.equal(context.messages.length%2,0);assert.equal(context.messages[context.messages.length-2].content,'q29');assert.equal(c.turns.length,30);
});
test('markdown export includes all historical text and step status, not raw parameters',async()=>{
  const {repo,id}=await setup();const t=await repo.begin(id,'任务','m','','device');await repo.step(id,t.turnId,start);await repo.finish(id,t.turnId,'stopped','','停止了');
  const markdown=repo.exportMarkdown(id);assert.match(markdown,/任务/);assert.match(markdown,/观察页面：结果未确认/);assert.match(markdown,/停止了/);assert.ok(!markdown.includes('tool_calls'));
});
test('empty and oversized user messages cannot start a persisted run',async()=>{
  const {repo,id}=await setup();await assert.rejects(repo.begin(id,'  ','m','','chat'),/不能为空/);await assert.rejects(repo.begin(id,'a'.repeat(8001),'m','','chat'),/8000/);assert.equal(repo.get(id)?.turns.length,0);
});

test('turn runner waits for durable user history before creating any executor',async()=>{
  const {repo,storage,id}=await setup();const gate=deferred<void>(),entered=deferred<void>();let created=0;
  storage.gate=async()=>{entered.resolve();await gate.promise;};const run=makeRun(repo,id,()=>{created++;return {run:async()=> '回答 fixture-model-secret',stop:()=>{}};});
  const promise=run.run();await entered.promise;assert.equal(created,0);gate.resolve();await promise;
  assert.equal(created,1);assert.ok(!storage.raw.includes('fixture-model-secret'));assert.equal(repo.get(id)?.turns[0].status,'completed');
});
test('stop while saving user history prevents late startup',async()=>{
  const {repo,storage,id}=await setup();const gate=deferred<void>(),entered=deferred<void>();let created=0;
  storage.gate=async()=>{entered.resolve();await gate.promise;};const run=makeRun(repo,id,()=>{created++;return {run:async()=> 'late',stop:()=>{}};});
  const promise=run.run();await entered.promise;run.stop();gate.resolve();await assert.rejects(promise,/停止/);assert.equal(created,0);assert.equal(repo.get(id)?.turns[0].status,'stopped');
});
test('stop before send has no persisted turn and no model call',async()=>{
  const {repo,id}=await setup();let calls=0;const run=makeRun(repo,id,()=>{calls++;throw Error('must not create');});run.stop();await assert.rejects(run.run(),/停止/);assert.equal(calls,0);assert.equal(repo.get(id)?.turns.length,0);
});
test('cancelled model replies never become a completed transcript',async()=>{
  const {repo,id}=await setup();const gate=deferred<string>(),entered=deferred<void>();let stops=0;
  const run=makeRun(repo,id,()=>({run:async()=>{entered.resolve();return gate.promise;},stop:()=>{stops++;}}));
  const promise=run.run();await entered.promise;run.stop();gate.resolve('late answer');await assert.rejects(promise,/停止/);
  const turn=repo.get(id)!.turns[0];assert.equal(turn.status,'stopped');assert.equal(turn.reply,'');assert.ok(stops>0);
});
test('history save failure prevents even a text model call',async()=>{
  const {repo,storage,id}=await setup();storage.fail=true;let calls=0;const run=makeRun(repo,id,()=>{calls++;throw new Error('unexpected');});
  await assert.rejects(run.run(),/DISK_FULL/);assert.equal(calls,0);
});
test('failure while saving a final answer is reported without claiming completion',async()=>{
  const {repo,storage,id}=await setup();const run=makeRun(repo,id,()=>({run:async()=>{storage.fail=true;return 'answer';},stop:()=>{}}));
  await assert.rejects(run.run(),/最终记录未能保存/);assert.equal(repo.get(id)?.turns[0].status,'running');
});
test('task failure stores a generic notice instead of raw error payload',async()=>{
  const {repo,storage,id}=await setup();const run=makeRun(repo,id,()=>({run:async()=>{throw new Error('private raw screen fixture-model-secret');},stop:()=>{}}));
  await assert.rejects(run.run(),/已隐藏密钥/);assert.ok(!storage.raw.includes('private raw screen'));assert.equal(repo.get(id)?.turns[0].status,'failed');
});
test('next turn receives earlier text, while separate conversations do not',async()=>{
  const {repo,id}=await setup();const first=await repo.begin(id,'我叫小明','m','','chat');await repo.finish(id,first.turnId,'completed','你好小明');
  let history:Message[]=[];await makeRun(repo,id,h=>{history=h;return {run:async()=> 'answer',stop:()=>{}};}).run();assert.equal(history[0].content,'我叫小明');
  const other=await repo.create();await makeRun(repo,other,h=>{assert.equal(h.length,0);return {run:async()=> 'answer',stop:()=>{}};}).run();
});
test('chat mode does not execute a model-invented tool call',async()=>{
  const d=inertDevice();const model:ModelClient={complete:async()=>tool,cancel:()=>{}};
  await assert.rejects(new AgentLoop(model,d.device,()=>{},{allowDevice:false}).run('你好'),/聊天模式/);assert.equal(d.calls(),0);
});
test('plain chat request omits the tools field instead of sending an empty array',async()=>{
  let payload='';const transport:HttpTransport={post:async(_u,_h,b)=>{payload=b;return JSON.stringify({choices:[{...done,message:done.message,finish_reason:'stop'}]});},cancel:()=>{}};
  await new OpenAIClient({baseUrl:'https://example.com/v1',apiKey:'fixture',model:'m',screenshots:false},transport,[]).complete([],new RunControl());assert.equal('tools' in JSON.parse(payload),false);
});
test('journal failure before action means the device is never called',async()=>{
  const d=inertDevice();const model:ModelClient={complete:async()=>tool,cancel:()=>{}};
  await assert.rejects(new AgentLoop(model,d.device,()=>{},{journal:async()=>{throw new Error('disk full');}}).run('检查'),/disk full/);assert.equal(d.calls(),0);
});
test('stop during pre-action journal wait blocks the pending device action',async()=>{
  const d=inertDevice(),gate=deferred<void>(),entered=deferred<void>();const model:ModelClient={complete:async()=>tool,cancel:()=>{}};
  const loop=new AgentLoop(model,d.device,()=>{},{journal:async()=>{entered.resolve();await gate.promise;}});
  const promise=loop.run('检查');await entered.promise;loop.stop();gate.resolve();await assert.rejects(promise,/CANCELLED/);assert.equal(d.calls(),0);
});
test('failed result journal never retries the completed device action',async()=>{
  const d=inertDevice();let journalCalls=0,modelCalls=0;const model:ModelClient={complete:async()=>{modelCalls++;return tool;},cancel:()=>{}};
  await assert.rejects(new AgentLoop(model,d.device,()=>{},{journal:async()=>{if(++journalCalls===2)throw new Error('disk full');}}).run('检查'),/disk full/);
  assert.equal(d.calls(),1);assert.equal(modelCalls,1);
});
test('historical tools, system instructions and screenshots are not replayed',async()=>{
  let seen:Message[]=[];const model:ModelClient={complete:async m=>{seen=m;return done;},cancel:()=>{}};
  const history:Message[]=[{role:'system',content:'old override'},{role:'user',content:'old user'},{role:'assistant',content:'old reply',tool_calls:tool.message.tool_calls},{role:'tool',content:'raw result',tool_call_id:'call'},{role:'user',content:[{type:'image_url',image_url:{url:'data:image/png;base64,old'}}]}];
  await new AgentLoop(model,inertDevice().device,()=>{},{allowDevice:false,history}).run('new');
  assert.deepEqual(seen.map(m=>m.role),['system','user','assistant','user']);assert.ok(!JSON.stringify(seen).includes('tool_calls'));assert.ok(!JSON.stringify(seen).includes('raw result'));assert.ok(!JSON.stringify(seen).includes('data:image'));
});
