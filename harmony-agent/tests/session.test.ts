import test from 'node:test';
import assert from 'node:assert/strict';
import { AgentTask, BackgroundDriver, TaskSession } from '../entry/src/main/ets/core/session';

function deferred<T>() { let resolve!:(v:T)=>void; const promise=new Promise<T>(r=>resolve=r); return {resolve,promise}; }
class FakeBackground implements BackgroundDriver {
  starts=0;stops=0;revoke:(reason:string)=>void=()=>{};
  onStart:()=>Promise<void>=async()=>{};
  onStop:()=>Promise<void>=async()=>{};
  async start(revoke:(reason:string)=>void){this.starts++;this.revoke=revoke;await this.onStart();}
  async stop(){this.stops++;await this.onStop();}
}
class FakeTask implements AgentTask {
  runs=0;stops=0;
  onRun:()=>Promise<string>=async()=>'完成';
  async run(){this.runs++;return this.onRun();}
  stop(){this.stops++;}
}
function setup(){
  const background=new FakeBackground(),task=new FakeTask();const notices:string[]=[];
  let hides=0,aborts=0;let onHide=async()=>{};
  const session=new TaskSession(task,background,async()=>{hides++;await onHide();},()=>aborts++,m=>notices.push(m));
  return {background,task,session,notices,counts:()=>({hides,aborts}),hide:(fn:()=>Promise<void>)=>onHide=fn};
}
test('session acquires background before hiding and releases after task completion',async()=>{
  const s=setup();s.hide(async()=>{assert.equal(s.background.starts,1);assert.equal(s.task.runs,0);});
  assert.equal(await s.session.run('查看'),'完成');assert.equal(s.background.stops,1);
  await assert.rejects(s.session.run('重复'),/重复启动/);
});
test('stop during background authorization rejects a late grant before any agent action',async()=>{
  const s=setup(),gate=deferred<void>(),entered=deferred<void>();
  s.background.onStart=async()=>{entered.resolve();await gate.promise;};
  const run=s.session.run('查看');await entered.promise;s.session.stop();
  assert.equal(s.background.stops,0,'must wait for the in-flight grant before releasing');
  gate.resolve();await assert.rejects(run,/CANCELLED/);
  assert.equal(s.task.runs,0);assert.equal(s.counts().hides,0);assert.equal(s.background.stops,1);
});
test('notification removal before the background promise resolves prevents startup',async()=>{
  const s=setup();s.background.onStart=async()=>s.background.revoke('通知被删除');
  await assert.rejects(s.session.run('查看'),/CANCELLED/);
  assert.equal(s.session.stopReason,'通知被删除');assert.equal(s.task.runs,0);assert.equal(s.background.stops,1);
});
test('stop while moving to background does not start the agent when hide resolves',async()=>{
  const s=setup(),gate=deferred<void>(),entered=deferred<void>();
  s.hide(async()=>{entered.resolve();await gate.promise;});
  const run=s.session.run('查看');await entered.promise;s.session.stop();gate.resolve();
  await assert.rejects(run,/CANCELLED/);assert.equal(s.task.runs,0);assert.equal(s.background.stops,1);
});
test('system revocation stops the agent, aborts device I/O, and immediately releases resources',async()=>{
  const s=setup(),gate=deferred<string>(),entered=deferred<void>(),released=deferred<void>();
  s.task.onRun=async()=>{entered.resolve();return gate.promise;};
  s.background.onStop=async()=>released.resolve();
  const run=s.session.run('查看');await entered.promise;s.background.revoke('系统收回');await released.promise;
  assert.equal(s.task.stops,1);assert.equal(s.counts().aborts,1);assert.equal(s.background.stops,1);
  s.background.revoke('再次回调');gate.resolve('晚到的结果');await assert.rejects(run,/CANCELLED/);
  assert.equal(s.task.stops,1);assert.equal(s.background.stops,1);assert.equal(s.session.stopReason,'系统收回');
});
test('a failed background request cleans partially acquired resources and does not run tools',async()=>{
  const s=setup();s.background.onStart=async()=>{throw new Error('permission denied');};
  await assert.rejects(s.session.run('查看'),/permission denied/);assert.equal(s.background.stops,1);assert.equal(s.task.runs,0);
});
test('stopping before run cannot be undone by later start',async()=>{
  const s=setup();s.session.stop();await assert.rejects(s.session.run('查看'),/CANCELLED/);
  assert.equal(s.background.starts,0);assert.equal(s.task.runs,0);
});
test('agent failure releases location and notification resources',async()=>{
  const s=setup();s.task.onRun=async()=>{throw new Error('model failed');};
  await assert.rejects(s.session.run('查看'),/model failed/);assert.equal(s.background.stops,1);
});
test('cleanup failure is reported without an unhandled rejection or silent success message',async()=>{
  const s=setup();s.background.onStop=async()=>{throw new Error('system error');};
  assert.equal(await s.session.run('查看'),'完成');assert.match(s.notices.at(-1)!,/释放失败/);
});
