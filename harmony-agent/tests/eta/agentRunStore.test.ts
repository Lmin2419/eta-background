// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/AgentRunStoreTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { AgentRunStore } from '../../entry/src/main/ets/core/eta/agentRunStore';
import { AgentProjectionEvent, BlockDelta } from '../../entry/src/main/ets/core/eta/model/runtimeProjection';
function fixture(){const delivered:{id:string;event:AgentProjectionEvent}[]=[];const store=new AgentRunStore((id,event)=>delivered.push({id,event}),{schedule:()=>()=>{}});return {store,delivered};}
function delta(text:string):BlockDelta{return {type:'AssistantBlockDelta',round:1,kind:'TEXT',index:0,delta:text};}
test('differentConversationsRemainIndependentWhenCompletingInReverseOrder',()=>{
 const {store}=fixture();assert.equal(store.register('a','conversation-a'),true);assert.equal(store.register('b','conversation-b'),true);assert.equal(store.register('second-a','conversation-a'),false);assert.equal(store.register('a','conversation-b'),false);
 store.requestStop('b',true);assert.equal(store.isStopRequested('a'),false);assert.equal(store.wasStoppedByUser('b'),true);store.remove('b');assert.deepEqual(store.observedRunIds,new Set(['a']));assert.equal(store.activeRunId('conversation-a'),'a');assert.equal(store.activeRunId('conversation-b'),null);assert.equal(store.register('next-b','conversation-b'),true);
});
test('interleavedDeltasAndTerminalBoundariesFlushOnlyTheirOwnRun',()=>{
 const {store,delivered}=fixture();store.register('a','conversation-a');store.register('b','conversation-b');store.enqueue('a',delta('甲'));store.enqueue('b',delta('乙'));store.enqueue('a',delta('丙'));store.enqueue('b',{type:'RunFinished'});
 assert.deepEqual(delivered.map(x=>x.id),['b','b']);assert.equal((delivered[0].event as BlockDelta).delta,'乙');store.flush('a');assert.equal((delivered.at(-1)!.event as BlockDelta).delta,'甲丙');
});
test('lateEventsDoNotRecreateRemovedRunOrChangeAnotherRunStatus',()=>{
 const {store,delivered}=fixture();store.register('a','conversation-a');store.register('b','conversation-b');store.updateGuiStatus('b','Waiting');store.enqueue('a',delta('未提交'));store.remove('a');store.enqueue('a',{type:'RunFinished'});store.updateGuiStatus('a','Acquired');assert.deepEqual(delivered,[]);assert.equal(store.guiStatus('conversation-b'),'Waiting');assert.equal(store.conversationId('a'),null);
});
test('replayOnlyRecordsDoNotClaimAnActiveSubscription',()=>{
 const {store}=fixture();store.register('archived','conversation-a',false);assert.equal(store.observedRunIds.size,0);assert.equal(store.hasActiveRuns,false);assert.equal(store.conversationId('archived'),'conversation-a');store.remove('archived');
});
test('settlementRejectsLateEventsAndKeepsConversationUntilSaved',()=>{
 const {store,delivered}=fixture();store.register('a','conversation-a');assert.equal(store.beginSettlement('a'),true);assert.equal(store.beginSettlement('a'),false);store.enqueue('a',delta('迟到的增量'));assert.deepEqual(delivered,[]);assert.equal(store.register('next-a','conversation-a'),false);store.remove('a');assert.equal(store.register('next-a','conversation-a'),true);
});
