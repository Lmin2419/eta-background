// Integration of AgentAppState / AgentAppRoot / AgentRunStore @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). New adapter tests; not counted as translated Eta tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import { FoundationController } from '../../entry/src/main/ets/core/foundation/controller';
import { ConversationRepository } from '../../entry/src/main/ets/core/conversations';
import { ProfileRepository } from '../../entry/src/main/ets/core/profiles';
import { DraftRepository } from '../../entry/src/main/ets/core/drafts';
import { HttpTransport } from '../../entry/src/main/ets/core/model';
import { FoundationRunProjection } from '../../entry/src/main/ets/core/eta/foundationRunProjection';
class Storage {
  raw=''; writes=0; failAt=0; gate:(()=>Promise<void>)|undefined;
  async read(){return this.raw;}
  async write(value:string){this.writes++;await this.gate?.();if(this.failAt===this.writes)throw new Error('disk full');this.raw=value;}
}
function deferred<T>(){let resolve!:(v:T)=>void;const promise=new Promise<T>(r=>resolve=r);return {promise,resolve};}
const answer=(text:string)=>JSON.stringify({choices:[{message:{role:'assistant',content:text},finish_reason:'stop'}]});
async function fixture(){
  const chatStorage=new Storage(),profileStorage=new Storage(),draftStorage=new Storage();let id=0;
  const requests:{body:{messages:{role:string;content:string}[]};finish:(text:string)=>void;chunk?: (text:string)=>void}[]=[];
  const profiles=new ProfileRepository(profileStorage,{get:async()=>'',put:async()=>{},remove:async()=>{}},()=>String(++id));await profiles.load();
  await profiles.save({id:'',name:'test',baseUrl:'https://example.test/v1',model:'test',models:['test'],streaming:false,screenshots:false,allowInsecureHttp:false},'',false);
  let c:FoundationController;
  const chats=new ConversationRepository(chatStorage,()=>String(++id),Date.now,()=>c?.changed(),2),drafts=new DraftRepository(draftStorage);
  c=new FoundationController({chats,profiles,drafts,uuid:()=>String(++id),elapsed:()=>performance.now(),load:async()=>{await chats.load();await drafts.load();},transport:():HttpTransport=>{
    let cancel:()=>void=()=>{};return {post:async(_url,_headers,body)=>{const d=deferred<string>();requests.push({body:JSON.parse(body),finish:t=>d.resolve(answer(t))});cancel=()=>d.resolve(answer('cancelled'));return d.promise;},cancel:()=>cancel()};
  }});await c.initialize();return {c,chats,drafts,chatStorage,draftStorage,requests};
}
async function until(check:()=>boolean){for(let i=0;i<200;i++){if(check())return;await new Promise(r=>setImmediate(r));}assert.fail('transition not reached');}
async function send(f:Awaited<ReturnType<typeof fixture>>,text:string,reply='answer'){const count=f.requests.length;f.c.draft(text);await f.c.send();await until(()=>f.requests.length>count);f.requests[count].finish(reply);await f.c.runtime.idle();}

test('generating messages enqueue FIFO; only durable admission starts the next HTTP request',async()=>{
 const f=await fixture();f.c.draft('one');await f.c.send();await until(()=>f.requests.length===1);
 f.c.draft('two');await f.c.send();f.c.draft('three');await f.c.send();assert.deepEqual(f.c.snapshot().chat.queuedMessages.map(q=>q.content),['two','three']);assert.equal(f.requests.length,1);
 f.requests[0].finish('first');await until(()=>f.requests.length===2);assert.equal(f.chats.get(f.c.snapshot().chat.id)?.turns.at(-1)?.request,'two');
 f.requests[1].finish('second');await until(()=>f.requests.length===3);f.requests[2].finish('third');await f.c.runtime.idle();assert.equal(f.c.snapshot().chat.queuedMessages.length,0);assert.equal(f.c.snapshot().chat.rows.length,3);await f.c.flush();
});
test('manual stop keeps queued messages; recalling combines the current draft without losing it',async()=>{
 const f=await fixture();f.c.draft('one');await f.c.send();await until(()=>f.requests.length===1);f.c.draft('queued');await f.c.send();f.c.stop();await f.c.runtime.idle();assert.equal(f.requests.length,1);assert.equal(f.c.snapshot().chat.rows[0].resultStatus,'stopped');
 const id=f.c.snapshot().chat.queuedMessages[0].id;f.c.draft('new draft');f.c.recallQueued(id,'new draft');assert.equal(f.c.snapshot().chat.input,'new draft\nqueued');assert.equal(f.c.snapshot().chat.queuedMessages.length,0);await f.c.flush();
});
test('steering interrupts once, runs promoted item first, then resumes remaining queue',async()=>{
 const f=await fixture();f.c.draft('one');await f.c.send();await until(()=>f.requests.length===1);f.c.draft('two');await f.c.send();f.c.draft('three');await f.c.send();
 const third=f.c.snapshot().chat.queuedMessages[1].id;await f.c.steerQueued(third);await until(()=>f.requests.length===2);assert.equal(f.chats.get(f.c.snapshot().chat.id)?.turns.at(-1)?.request,'three');f.requests[1].finish('third');await until(()=>f.requests.length===3);f.requests[2].finish('second');await f.c.runtime.idle();assert.deepEqual(f.c.snapshot().chat.rows.map(r=>r.request),['one','three','two']);await f.c.flush();
});
test('queue is isolated by conversation and process restart does not replay pending inputs',async()=>{
 const f=await fixture();const first=f.c.snapshot().chat.id;f.c.draft('one');await f.c.send();await until(()=>f.requests.length===1);f.c.draft('pending');await f.c.send();await f.c.newConversation();assert.equal(f.c.snapshot().chat.queuedMessages.length,0);f.c.foreground(false);await f.c.runtime.idle();await f.c.selectConversation(first);assert.equal(f.c.snapshot().chat.queuedMessages.length,1);
 const restored=new ConversationRepository(f.chatStorage,()=> 'unused',Date.now,()=>{},2);await restored.load();assert.equal(restored.get(first)?.turns.length,1);assert.equal(f.requests.length,1);assert.ok(!f.chatStorage.raw.includes('pending'));await f.c.flush();
});
test('editing previews through user message; cancellation restores previous draft and full history',async()=>{
 const f=await fixture();await send(f,'one');await send(f,'two');f.c.draft('unsent');const messages=f.c.snapshot().chat.messages;
 f.c.editMessage(messages[0].id);assert.equal(f.c.snapshot().chat.messages.length,1);assert.equal(f.c.snapshot().chat.input,'one');assert.equal(f.c.snapshot().chat.messageEdit?.hasLaterTurns,true);
 f.c.cancelMessageEdit();assert.equal(f.c.snapshot().chat.input,'unsent');assert.deepEqual(f.c.snapshot().chat.messages,messages);await f.c.flush();
});
test('editing resubmits at the original boundary and cannot leak later turns into model context',async()=>{
 const f=await fixture();await send(f,'one','reply-one');await send(f,'two','reply-two');await send(f,'three','reply-three');const old=f.c.snapshot().chat.rows[1].id;
 f.c.editMessage(old+'-user');f.c.draft('edited two');await f.c.send();await until(()=>f.requests.length===4);
 const context=f.requests[3].body.messages;assert.ok(context.some(m=>m.content==='reply-one'));assert.ok(!context.some(m=>m.content.includes('reply-two')||m.content.includes('three')));
 f.requests[3].finish('edited reply');await f.c.runtime.idle();assert.deepEqual(f.c.snapshot().chat.rows.map(r=>r.request),['one','edited two']);assert.equal(f.chats.get(f.c.snapshot().chat.id)?.turns[1].revisionOf,old);await f.c.flush();
});
test('failed atomic revision admission preserves full history, edit state and draft; no HTTP call',async()=>{
 const f=await fixture();await send(f,'one');await send(f,'two');const before=f.c.snapshot().chat.rows;f.c.editMessage(before[0].id+'-user');f.c.draft('edited');f.chatStorage.failAt=f.chatStorage.writes+2;
 await f.c.send();await f.c.runtime.idle();assert.equal(f.requests.length,2);assert.deepEqual(f.chats.get(f.c.snapshot().chat.id)?.turns.map(t=>t.request),['one','two']);assert.equal(f.c.snapshot().chat.input,'edited');assert.ok(f.c.snapshot().chat.messageEdit);assert.match(f.c.snapshot().error,/disk full/);await f.c.flush();
});
test('regeneration of a prior turn requires confirmation; latest runs immediately and preserves input',async()=>{
 const f=await fixture();await send(f,'one');await send(f,'two');f.c.draft('unsent');await f.c.requestMessageMutation(f.c.snapshot().chat.rows[0].id+'-assistant','regenerate');assert.equal(f.c.snapshot().mutation?.laterTurns,1);assert.equal(f.requests.length,2);f.c.dismissMessageMutation();
 await f.c.requestMessageMutation(f.c.snapshot().chat.rows[1].id+'-assistant','regenerate');await until(()=>f.requests.length===3);assert.equal(f.c.snapshot().chat.input,'unsent');f.requests[2].finish('new reply');await f.c.runtime.idle();assert.equal(f.c.snapshot().chat.rows.length,2);await f.c.flush();
});
test('confirmed delete truncates at the user boundary and first-turn delete removes conversation',async()=>{
 const f=await fixture();await send(f,'one');await send(f,'two');const id=f.c.snapshot().chat.id;
 await f.c.requestMessageMutation(f.c.snapshot().chat.rows[1].id+'-assistant','delete');await f.c.confirmMessageMutation();assert.equal(f.c.snapshot().chat.rows.length,1);
 await f.c.requestMessageMutation(f.c.snapshot().chat.rows[0].id+'-user','delete');await f.c.confirmMessageMutation();assert.equal(f.chats.get(id),undefined);assert.equal(f.c.snapshot().chat.rows.length,0);await f.c.flush();
});
test('a stale mutation dialog cannot delete the old conversation after switching',async()=>{
 const f=await fixture();await send(f,'one');const first=f.c.snapshot().chat.id;await f.c.requestMessageMutation(f.c.snapshot().chat.rows[0].id+'-user','delete');await f.c.newConversation();await f.c.confirmMessageMutation();assert.equal(f.chats.get(first)?.turns.length,1);assert.match(f.c.snapshot().error,/会话状态/);await f.c.flush();
});
test('L4 bridge coalesces cumulative deltas, retains thinking timing, applies corrections and ignores late chunks',()=>{
 let now=0;const p=new FoundationRunProjection('run',()=>now,()=>{});p.update('THINKING','abc');p.update('THINKING','abcdef');p.flush();assert.equal(p.messages().length,1);assert.equal((p.messages()[0] as {content:string}).content,'abcdef');now=2300;p.update('TEXT','hello');p.update('TEXT','hello world');p.flush();
 assert.equal(p.messages().find(m=>m.kind==='thinking')?.elapsedSeconds,2);assert.equal(p.messages().find(m=>m.kind==='assistant')?.content,'hello world');p.update('TEXT','corrected');p.finish();p.update('TEXT','late');assert.equal(p.messages().find(m=>m.kind==='assistant')?.content,'corrected');assert.equal(p.messages().find(m=>m.kind==='assistant')?.isStreaming,false);
});
test('queue admission failure preserves the item without retry or falling through to later items',async()=>{
 const f=await fixture();f.c.draft('one');await f.c.send();await until(()=>f.requests.length===1);f.c.draft('two');await f.c.send();f.c.draft('three');await f.c.send();
 // Finish first, then fail queued preferences after durable completed outcome.
 f.chatStorage.gate=async()=>{if(JSON.parse(f.chatStorage.raw||'{}').conversations?.[0]?.turns?.[0]?.status==='completed')throw new Error('disk full');};
 f.requests[0].finish('first');await f.c.runtime.idle();assert.equal(f.requests.length,1);assert.deepEqual(f.c.snapshot().chat.queuedMessages.map(q=>q.content),['two','three']);await f.c.flush();
});
test('manual stop during a promoted run keeps the remaining queue for explicit resumption',async()=>{
 const f=await fixture();f.c.draft('one');await f.c.send();await until(()=>f.requests.length===1);f.c.draft('two');await f.c.send();f.c.draft('priority');await f.c.send();
 const priority=f.c.snapshot().chat.queuedMessages[1].id;const steering=f.c.steerQueued(priority);
 await steering;await until(()=>f.requests.length===2);assert.equal(f.chats.get(f.c.snapshot().chat.id)?.turns.at(-1)?.request,'priority');
 f.c.stop();await f.c.runtime.idle();assert.equal(f.requests.length,2);assert.equal(f.c.snapshot().chat.queuedMessages[0].content,'two');await f.c.flush();
});
