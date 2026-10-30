import test from 'node:test';
import assert from 'node:assert/strict';
import { TaskLedger, TaskScheduler, TaskRecord, ManagedTask } from '../entry/src/main/ets/core/runtime';
import { TaskNotifications, NoticeView, notificationView } from '../entry/src/main/ets/core/notifications';
import { ConversationRepository, ConversationStorage } from '../entry/src/main/ets/core/conversations';
import { ConversationRun } from '../entry/src/main/ets/core/conversationRun';
import { DraftRepository } from '../entry/src/main/ets/core/drafts';
import { emptyModelDraft } from '../entry/src/main/ets/core/modelForm';
import { CompletionStream } from '../entry/src/main/ets/core/stream';

class Memory implements ConversationStorage {raw='';fail=false; async read(){return this.raw;}async write(s:string){if(this.fail)throw new Error('DISK_FULL');this.raw=s;}}
function defer<T=void>(){let resolve!:(v:T)=>void;const promise=new Promise<T>(r=>resolve=r);return {resolve,promise};}
async function setup(){const storage=new Memory();let seq=0;const ledger=new TaskLedger(storage,()=>`00000000-0000-4000-8000-${String(++seq).padStart(12,'0')}`);await ledger.load();return {storage,ledger};}
const request=(conversationId='a',mode:'chat'|'device'='chat')=>({conversationId,mode,text:'测试任务',modelLabel:'model',profileId:'provider'});
const tick=()=>new Promise<void>(resolve=>setImmediate(resolve));
async function until(fn:()=>boolean){for(let i=0;i<200&&!fn();i++)await tick();assert.ok(fn(),'condition eventually met');}
function workers(){const started:string[]=[];const gates=new Map<string,{resolve:()=>void;promise:Promise<void>}>();const stops:string[]=[];return {started,gates,stops,factory:(r:TaskRecord):ManagedTask=>{const gate=defer();gates.set(r.id,gate);return {run:async()=>{started.push(r.id);await gate.promise;},stop:()=>{stops.push(r.id);gate.resolve();},pause:()=>{},resume:async()=>{},steer:()=>{}};}};}

test('task admission is durable, bounded, and rejects failed writes without exposing a staged task',async()=>{
  const {ledger,storage}=await setup();storage.fail=true;await assert.rejects(ledger.admit(request()),/DISK_FULL/);assert.equal(ledger.list().length,0);storage.fail=false;
  for(let i=0;i<8;i++)await ledger.admit(request(String(i)));
  await assert.rejects(ledger.admit(request('overflow')),/8/);assert.equal(new Set(ledger.list().map(t=>t.notificationId)).size,8);
});
test('restart interrupts queued and active tasks and preserves identities without replay',async()=>{
  const {ledger,storage}=await setup();const a=await ledger.admit(request());await ledger.update(a.id,t=>{t.state='preparing';});
  const b=await ledger.admit(request('b'));const restarted=new TaskLedger(storage,()=> 'unused');assert.equal(await restarted.load(),2);
  assert.equal(restarted.get(a.id)?.state,'interrupted');assert.equal(restarted.get(b.id)?.state,'interrupted');assert.equal(restarted.get(a.id)?.notificationId,a.notificationId);
});
test('terminal tasks reject stale transitions and action tokens are scoped to exact task',async()=>{
  const {ledger}=await setup();const a=await ledger.admit(request()),b=await ledger.admit(request('b'));
  await ledger.update(a.id,t=>{t.state='cancelled';});await assert.rejects(ledger.update(a.id,t=>{t.state='running';}),/过期/);
  assert.equal(ledger.validAction(a.id,b.token),undefined);assert.equal(ledger.validAction(a.id,a.token)?.id,a.id);
});
test('two model slots never allow two entity-screen owners or two writers in one conversation',async()=>{
  const {ledger}=await setup();const a=await ledger.admit(request('a','device')),b=await ledger.admit(request('b','device')),c=await ledger.admit(request('a')),d=await ledger.admit(request('d'));
  const w=workers(),scheduler=new TaskScheduler(ledger,w.factory);scheduler.kick();await until(()=>w.started.length===2);
  assert.deepEqual(new Set(w.started),new Set([a.id,d.id]));
  w.gates.get(a.id)!.resolve();await until(()=>w.started.includes(b.id));assert.ok(!w.started.includes(c.id));
  w.gates.get(d.id)!.resolve();await until(()=>w.started.includes(c.id));w.gates.get(b.id)!.resolve();w.gates.get(c.id)!.resolve();await scheduler.idle();
  assert.ok(ledger.list().every(t=>t.state==='completed'));
});
test('user stop cancels synchronously and leaves later messages held until explicit queue resume',async()=>{
  const {ledger}=await setup();const a=await ledger.admit(request()),b=await ledger.admit(request());const w=workers(),scheduler=new TaskScheduler(ledger,w.factory);
  scheduler.kick();await until(()=>w.started.length===1);const stopping=scheduler.stop(a.id);assert.deepEqual(w.stops,[a.id]);await stopping;await scheduler.idle();
  assert.equal(ledger.get(a.id)?.state,'cancelled');assert.equal(ledger.get(b.id)?.held,true);assert.equal(w.started.length,1);
  await scheduler.resume(b.id);await until(()=>w.started.length===2);w.gates.get(b.id)!.resolve();await scheduler.idle();
});
test('interrupt waits for predecessor cleanup and then starts the promoted message first',async()=>{
  const {ledger}=await setup();const a=await ledger.admit(request()),b=await ledger.admit(request()),c=await ledger.admit(request());
  const w=workers(),scheduler=new TaskScheduler(ledger,w.factory,()=>{},1);scheduler.kick();await until(()=>w.started.length===1);
  await scheduler.interrupt(c.id);await until(()=>w.started.length===2);assert.equal(w.started[1],c.id);w.gates.get(c.id)!.resolve();await until(()=>w.started.length===3);assert.equal(w.started[2],b.id);w.gates.get(b.id)!.resolve();await scheduler.idle();
});
test('notification continuation is committed once and never crosses conversations',async()=>{
  const {ledger}=await setup();const source=await ledger.admit(request());await ledger.update(source.id,t=>{t.state='cancelled';});
  await assert.rejects(ledger.admit({...request('other'),sourceRunId:source.id}),/源任务/);
  const first=await ledger.admit({...request(),sourceRunId:source.id}),second=await ledger.admit({...request(),sourceRunId:source.id});assert.equal(first.id,second.id);assert.equal(ledger.list().length,2);
});
test('concurrent conversation repository permits separate writers, rejects duplicates, does not switch viewed conversation',async()=>{
  const storage=new Memory();let n=0;const repo=new ConversationRepository(storage,()=>String(++n),Date.now,()=>{},2);await repo.load();const a=await repo.create(),b=await repo.create();
  await repo.begin(a,'a','m','p','chat','run-a');assert.equal(repo.selectedId,b);await assert.rejects(repo.begin(a,'again','m','p','chat','run-a'),/停止/);
  await repo.begin(b,'b','m','p','chat','run-b');assert.equal(repo.get(a)?.turns[0].id,'run-a');
});
class Notices {
  ids=new Set<number>();views:NoticeView[]=[];interactive=true;permission=true;
  async enabled(){return this.permission;}async activeIds(){return Array.from(this.ids);}async canAlert(){return this.interactive;}
  async publish(v:NoticeView){if(v.updateOnly&&!this.ids.has(v.id))throw new Error('NO_EXISTING_NOTICE');this.views.push(JSON.parse(JSON.stringify(v)));this.ids.add(v.id);}
  async cancel(id:number){this.ids.delete(id);}
}
async function running(ledger:TaskLedger){const r=await ledger.admit(request());await ledger.update(r.id,t=>{t.state='preparing';});await ledger.update(r.id,t=>{t.state='running';});return r;}
test('phone completion keeps three intents using body and exactly two action buttons',async()=>{
  const {ledger}=await setup();const task=await running(ledger);await ledger.update(task.id,t=>{t.state='completed';t.targetBundle='com.example.app';t.targetAbility='EntryAbility';});
  const view=notificationView(ledger.get(task.id)!);assert.equal(view.conversationId,'a');assert.deepEqual(view.actions.map(a=>a.name),['open-app','continue']);assert.equal(view.id,task.notificationId);
});
test('deleted OS card does not reappear on completion or process recovery',async()=>{
  const {ledger,storage}=await setup();const driver=new Notices(),notices=new TaskNotifications(ledger,driver);notices.initialize();const r=await running(ledger);
  notices.changed();await notices.flush();assert.equal(driver.views.length,1);driver.ids.delete(r.notificationId);await ledger.update(r.id,t=>{t.state='completed';});notices.changed();await notices.flush();
  assert.equal(driver.views.length,1);assert.equal(ledger.get(r.id)?.dismissed,true);
  const restart=new TaskLedger(storage,()=> 'unused');await restart.load();const next=new TaskNotifications(restart,driver);next.initialize();next.changed();await next.flush();assert.equal(driver.views.length,1);
});
test('locked completion consumes alert attempt; unlocking or changing buttons does not replay alert',async()=>{
  const {ledger}=await setup();const driver=new Notices(),notices=new TaskNotifications(ledger,driver);notices.initialize();const r=await running(ledger);notices.changed();await notices.flush();
  driver.interactive=false;await ledger.update(r.id,t=>{t.state='completed';});notices.changed();await notices.flush();assert.equal(driver.views.at(-1)?.alert,false);
  driver.interactive=true;notices.inlineReplyEnabled=true;notices.changed();await notices.flush();assert.equal(driver.views.at(-1)?.alert,false);assert.equal(ledger.get(r.id)?.alerted,true);
});
test('notification completion update uses same ID and one alert; dismissal token cannot target another task',async()=>{
  const {ledger}=await setup();const driver=new Notices(),notices=new TaskNotifications(ledger,driver);notices.initialize();const r=await running(ledger);notices.changed();await notices.flush();
  await notices.dismissed(r.notificationId,'wrong-token');assert.equal(ledger.get(r.id)?.dismissed,false);
  await ledger.update(r.id,t=>{t.state='completed';});notices.changed();await notices.flush();notices.changed();await notices.flush();
  assert.equal(driver.views.length,2);assert.equal(driver.views[1].id,driver.views[0].id);assert.equal(driver.views[1].updateOnly,true);assert.equal(driver.views[1].alert,true);
});
test('notification permission denial leaves durable task status intact',async()=>{
  const {ledger}=await setup();const driver=new Notices();driver.permission=false;const notices=new TaskNotifications(ledger,driver);notices.initialize();const r=await running(ledger);notices.changed();await notices.flush();assert.equal(ledger.get(r.id)?.state,'running');assert.equal(driver.views.length,0);
});
test('draft persistence excludes credentials while preserving per-provider edits and conversation text',async()=>{
  const storage=new Memory(),drafts=new DraftRepository(storage);await drafts.load();const form={...emptyModelDraft(),id:'p',name:'edited',baseUrl:'https://model.example/v1',apiKey:'secret-fixture',models:['one','two']};
  drafts.setProvider(form);drafts.setMessage('chat','unsent');await drafts.flush();assert.ok(!storage.raw.includes('secret-fixture'));assert.equal(drafts.provider('p')?.apiKey,'secret-fixture');
  const restored=new DraftRepository(storage);await restored.load();assert.equal(restored.provider('p')?.apiKey,'');assert.equal(restored.provider('p')?.name,'edited');assert.equal(restored.message('chat').text,'unsent');
});
test('stream checkpoints survive restart before the model round completes and redact configured keys',async()=>{
  const storage=new Memory();let n=0;const repo=new ConversationRepository(storage,()=>String(++n));await repo.load();const id=await repo.create();const gate=defer<string>(),started=defer();let run:ConversationRun;
  run=new ConversationRun(repo,{conversationId:id,text:'test',modelLabel:'m',profileId:'p',mode:'chat',secrets:['fixture-key'],runId:'run-checkpoint'},()=>({run:async()=>{started.resolve();return gate.promise;},stop(){gate.resolve('stopped');}}));
  const pending=run.run();await started.promise;run.capture('partial fixture-key');await new Promise(r=>setTimeout(r,800));
  assert.equal(repo.get(id)?.turns[0].reply,'partial [已隐藏密钥]');const reopened=new ConversationRepository(storage,()=> 'new');await reopened.load();assert.equal(reopened.get(id)?.turns[0].status,'interrupted');assert.ok(reopened.get(id)?.turns[0].reply.includes('partial'));
  run.stop();await assert.rejects(pending,/停止/);
});
test('reasoning stream has a separate channel and cannot become a tool or answer',()=>{
  const answers:string[]=[],reasoning:string[]=[];const s=new CompletionStream(t=>answers.push(t),t=>reasoning.push(t));
  s.feed('data: {"choices":[{"delta":{"reasoning_content":"分析"},"finish_reason":null}]}\n\n');
  s.feed('data: {"choices":[{"delta":{"content":"回答"},"finish_reason":"stop"}]}\n\n');
  const r=s.finish(()=>{throw new Error('unused');});assert.deepEqual(reasoning,['分析']);assert.deepEqual(answers,['回答']);assert.equal(r.reasoning,'分析');
});
