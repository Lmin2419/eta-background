import test from 'node:test';
import assert from 'node:assert/strict';
import { ModelDraft, emptyModelDraft, editModelAddress, modelSnapshot, probeModel, sameModelRequest } from '../entry/src/main/ets/core/modelForm';
import { HttpTransport } from '../entry/src/main/ets/core/model';
import { BackgroundService } from '../entry/src/main/ets/core/backgroundService';
import { BackgroundDriver } from '../entry/src/main/ets/core/session';
import { HarmonyDevice, HdcTransport } from '../entry/src/main/ets/core/device';
import { RunControl } from '../entry/src/main/ets/core/types';
function gate<T>() {let resolve!:(value:T)=>void;return {promise:new Promise<T>(r=>resolve=r),resolve:(value:T)=>resolve(value)};}
function draft():ModelDraft{return {...emptyModelDraft(),name:'My service',baseUrl:'https://models.example/v1',apiKey:'test-only-key',model:'model-a'};}
class Http implements HttpTransport {
  response='{"data":[{"id":"model-a"},{"id":"model-b"}]}';
  before:()=>Promise<void>=async()=>{};
  calls:{url:string;key:string}[]=[];
  async get(url:string,headers:Record<string,string>){this.calls.push({url,key:headers.Authorization});await this.before();return this.response;}
  async post(url:string,headers:Record<string,string>,_body:string){return this.get(url,headers);}
  cancel(){}
}
test('address callbacks with unchanged text retain entered key, name, model and flags',()=>{
  const form=draft();assert.equal(editModelAddress(form,form.baseUrl),form);
  assert.equal(editModelAddress(form,' https://models.example/v1/ ').apiKey,form.apiKey);
  assert.equal(editModelAddress(form,'https://other.example/v1').apiKey,'');
  assert.equal(editModelAddress(form,'https://other.example/v1').model,form.model);
});
test('model list success and connection failure leave every input untouched',async()=>{
  const form=draft(),before={...form},io=new Http();
  assert.deepEqual((await probeModel(true,modelSnapshot(form),io)).ids,['model-a','model-b']);
  assert.deepEqual(form,before);
  io.before=async()=>{throw new Error('HTTP 401');};
  await assert.rejects(probeModel(false,modelSnapshot(form),io));assert.deepEqual(form,before);
});
test('HTTP validation failure preserves the form for correcting the explicit HTTP preference',async()=>{
  const form={...draft(),baseUrl:'http://192.168.1.2:8080/v1'},before={...form},io=new Http();
  await assert.rejects(probeModel(true,modelSnapshot(form),io),/HTTPS/);
  assert.deepEqual(form,before);assert.equal(io.calls.length,0);
});
test('in-flight model lookup keeps its snapshot and cannot silently select models for a changed form',async()=>{
  const form=draft(),snapshot=modelSnapshot(form),io=new Http(),wait=gate<void>();io.before=()=>wait.promise;
  const pending=probeModel(true,snapshot,io);
  form.baseUrl='https://new.example/v1';form.apiKey='new-key';form.name='Renamed';
  assert.equal(sameModelRequest(form,snapshot),false);wait.resolve();await pending;
  assert.equal(io.calls[0].url,'https://models.example/v1/models');assert.equal(io.calls[0].key,'Bearer test-only-key');
  assert.equal(form.baseUrl,'https://new.example/v1');assert.equal(form.name,'Renamed');assert.equal(form.apiKey,'new-key');
});
class Background implements BackgroundDriver {
  starts=0;stops=0;revoke:(reason:string)=>void=()=>{};
  onStart:()=>Promise<void>=async()=>{};onStop:()=>Promise<void>=async()=>{};
  async start(revoke:(reason:string)=>void){this.starts++;this.revoke=revoke;await this.onStart();}
  async stop(){this.stops++;await this.onStop();}
}
function service(){const driver=new Background(),states:string[]=[];const session=new BackgroundService(()=>driver,()=>states.push(session.state));return {driver,session,states};}
test('manually started service survives individual task lease completion without allocating another OS task',async()=>{
  const s=service();await s.session.start();const lease=s.session.lease();await lease.start(()=>{},()=>false);await lease.stop();
  assert.equal(s.session.active,true);assert.equal(s.driver.starts,1);assert.equal(s.driver.stops,0);
  await s.session.stop();assert.equal(s.driver.stops,1);assert.equal(s.session.state,'off');
});
test('stop during authorization waits for the late grant and never transitions to active',async()=>{
  const s=service(),wait=gate<void>(),entered=gate<void>();s.driver.onStart=async()=>{entered.resolve();await wait.promise;};
  const start=s.session.start();await entered.promise;const stop=s.session.stop();assert.equal(s.driver.stops,0);
  wait.resolve();await Promise.all([start,stop]);assert.ok(!s.states.includes('active'));assert.equal(s.driver.stops,1);
});
test('a synchronous cancellation callback before the grant settles still waits for complete cleanup',async()=>{
  const s=service(),wait=gate<void>(),entered=gate<void>();s.driver.onStart=async()=>{s.driver.revoke('system revoked');entered.resolve();await wait.promise;};
  const start=s.session.start();await entered.promise;await Promise.resolve();assert.equal(s.driver.stops,0);
  wait.resolve();await start;assert.equal(s.driver.stops,1);assert.equal(s.session.state,'error');assert.ok(!s.states.includes('active'));
});
test('stopping before the deferred driver start never requests OS resources',async()=>{
  const s=service();const start=s.session.start();await s.session.stop();await start;assert.equal(s.driver.starts,0);assert.equal(s.session.state,'off');
});
test('notification cancellation revokes the task and clears the service without automatic restart',async()=>{
  const s=service();await s.session.start();let stopped='';const lease=s.session.lease();await lease.start(reason=>stopped=reason,()=>false);
  s.driver.revoke('notification removed');await s.session.stop();assert.equal(stopped,'notification removed');
  assert.equal(s.session.state,'error');assert.equal(s.driver.starts,1);assert.equal(s.driver.stops,1);
  await assert.rejects(s.session.lease().start(()=>{},()=>false),/已停止/);
});
test('two simultaneous leases and duplicate service starts cannot allocate concurrent background ownership',async()=>{
  const s=service();await s.session.start();await assert.rejects(s.session.start());
  const first=s.session.lease();await first.start(()=>{},()=>false);
  await assert.rejects(s.session.lease().start(()=>{},()=>false),/已有任务/);
  await first.stop();await s.session.stop();assert.equal(s.driver.starts,1);
});
test('denied start releases partial resources and keeps the exact failure visible',async()=>{
  const s=service();s.driver.onStart=async()=>{throw new Error('通知验证失败（9800006）');};await s.session.start();
  assert.equal(s.session.state,'error');assert.match(s.session.message,/9800006/);assert.equal(s.driver.stops,1);
});
test('failed resource release blocks another start until the process is restarted',async()=>{
  const s=service();await s.session.start();s.driver.onStop=async()=>{throw new Error('OS release failed');};await s.session.stop();
  assert.equal(s.session.state,'error');await assert.rejects(s.session.start(),/未能释放/);assert.equal(s.driver.starts,1);
});
class Hdc implements HdcTransport {
  commands:string[]=[];after:()=>void=()=>{};
  async command(command:string){this.commands.push(command);this.after();if(command.startsWith('tconn'))return 'OK';
    const marker=command.match(/__HARMONY_AGENT_EXIT_\d+__/)?.[0];
    const body=command.includes('\nid\n')?'uid=2000(shell)':command.includes('uitest help')?'dumpLayout uiInput screenCap':command.includes('hidumper -ls')?'DisplayManagerService\nOtherService':'aa help';
    return body+'\n'+marker+'0\n';}
}
test('virtual-screen probe collects help and permissions without issuing screen creation or GUI actions',async()=>{
  const hdc=new Hdc(),device=new HarmonyDevice(hdc,()=>({width:100,height:100}),()=>false);await device.connect('127.0.0.1:5555');hdc.commands=[];
  const report=await device.virtualScreenDiagnostics(new RunControl());assert.equal(hdc.commands.length,4);assert.match(report,/未验证虚拟屏创建/);
  assert.ok(hdc.commands.every(c=>!c.includes('aa start')&&!c.includes('uiInput click')&&!c.includes('screenCap -p')));
  assert.ok(!report.includes('OtherService'));
});
test('cancelled virtual-screen probe stops before issuing more diagnostic commands',async()=>{
  const hdc=new Hdc(),device=new HarmonyDevice(hdc,()=>({width:100,height:100}),()=>false);await device.connect('127.0.0.1:5555');hdc.commands=[];
  const control=new RunControl();hdc.after=()=>control.stop();await assert.rejects(device.virtualScreenDiagnostics(control),/CANCELLED/);assert.equal(hdc.commands.length,1);
});

import { TaskNotice, TaskNoticeQueue } from '../entry/src/main/ets/core/taskNotice';
test('notification completion is serialized behind a pending publish and late progress cannot replace it',async()=>{
  const wait=gate<void>(),entered=gate<void>(),notices:TaskNotice[]=[];
  const queue=new TaskNoticeQueue(async notice=>{notices.push(notice);if(notices.length===1){entered.resolve();await wait.promise;}});
  const starting=queue.start();await entered.promise;queue.update('clicking');const ending=queue.finish(true,false);queue.update('late tool');
  wait.resolve();await Promise.all([starting,ending]);assert.equal(notices.length,2);assert.equal(notices[1].running,false);assert.match(notices[1].title,/任务完成/);
});
test('notification publish failure is reported and does not block the final task state',async()=>{
  let calls=0;const warnings:string[]=[];const notices:TaskNotice[]=[];
  const queue=new TaskNoticeQueue(async notice=>{calls++;if(calls===1)throw new Error('OS error');notices.push(notice);},warning=>warnings.push(warning));
  await queue.start();await queue.finish(false,true);assert.equal(warnings.length,1);assert.match(notices[0].title,/已停止/);
});
