import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { HarmonyDevice, HdcTransport, parseLayout, quote, validateTarget } from '../entry/src/main/ets/core/device';
import { AgentLoop } from '../entry/src/main/ets/core/loop';
import { OpenAIClient, HttpTransport } from '../entry/src/main/ets/core/model';
import { DeviceTools, Message, ModelClient, ModelReply, Observation, RunControl, ToolCall, ToolResult } from '../entry/src/main/ets/core/types';

const layout=(text='搜索',extra={})=>JSON.stringify({attributes:{bounds:'[0,0][100,200]',type:'Root'},children:[{attributes:{bounds:'[10,20][90,60]',type:'TextInput',text,clickable:'true',...extra}}]});
const call=(id:string,name='observe_screen',args='{}'):ToolCall=>({id,type:'function',function:{name,arguments:args}});
const toolReply=(calls:ToolCall[],finish='tool_calls'):ModelReply=>({message:{role:'assistant',content:null,tool_calls:calls},finishReason:finish});
const done:ModelReply={message:{role:'assistant',content:'已完成'},finishReason:'stop'};
function deferred<T>() { let resolve!:(v:T)=>void; const promise=new Promise<T>(r=>resolve=r); return {resolve,promise}; }
function copy<T>(v:T):T{return JSON.parse(JSON.stringify(v));}

class FakeHdc implements HdcTransport {
  calls:string[]=[];
  tree=layout();
  failIdentity=false;
  failMutation=false;
  launchStatus=0;
  commandGate:((cmd:string)=>Promise<void>)|undefined;
  async command(cmd:string):Promise<string>{
    this.calls.push(cmd);await this.commandGate?.(cmd);
    if(cmd.startsWith('tconn '))return 'Connect OK';
    const marker=cmd.match(/__HARMONY_AGENT_EXIT_\d+__/)?.[0];
    assert.ok(marker,'shell status marker required');
    let result='';
    if(cmd.includes('\nid\n'))result=this.failIdentity?'unauthorized':'uid=2000(shell)';
    else if(cmd.includes('uitest help')||cmd.includes('uitest uiInput -h'))result='dumpLayout uiInput screenCap';
    else if(cmd.includes('dumpLayout'))result=this.tree;
    else if(cmd.includes('screenCap'))result='iVBORw0KGgoAAA';
    else if(cmd.includes('aa start') && this.launchStatus)return 'launch failed\n'+marker+this.launchStatus+'\n';
    else if(this.failMutation)throw new Error('HDC_TIMEOUT');
    return result+'\n'+marker+'0\n';
  }
  mutations():string[]{return this.calls.filter(c=>c.includes('uiInput')&&!c.includes('help')&&!c.includes('uiInput -h'));}
}
async function setup(){
  const hdc=new FakeHdc();let size={width:100,height:200};
  const device=new HarmonyDevice(hdc,()=>size,()=>false);await device.connect('127.0.0.1:5555');
  const control=new RunControl();
  const obs=(await device.execute('observe_screen',{},control)).observation!;
  return {hdc,device,control,obs,rotate:()=>{size={width:200,height:100};}};
}
class FakeModel implements ModelClient {
  snapshots:Message[][]=[];cancelled=false;
  constructor(private replies:ModelReply[]){}
  async complete(messages:Message[]):Promise<ModelReply>{this.snapshots.push(copy(messages));return this.replies.shift()||done;}
  cancel(){this.cancelled=true;}
}
class FakeDevice implements DeviceTools {
  calls:string[]=[];invalidations=0;
  observation:Observation={id:'v1',capturedAt:0,width:100,height:200,nodes:[],truncated:false,fingerprint:'a',screenshot:'iVBORw0KGgoAAA'};
  async execute(name:string):Promise<ToolResult>{this.calls.push(name);return {ok:true,observation:copy(this.observation)};}
  invalidate(){this.invalidations++;}
}

test('shell quoting preserves metacharacters, Chinese, apostrophes and newlines',()=>{
  const input="你好 ' $(echo INJECTED) `echo ALSO` ;\n$HOME \\\"";
  assert.equal(execFileSync('/bin/sh',['-c','printf %s '+quote(input)],{encoding:'utf8'}),input);
  assert.throws(()=>quote('bad\0value'));
});
test('target validation rejects command injection and impossible ports',()=>{
  assert.equal(validateTarget(' 127.0.0.1:5555 '),'127.0.0.1:5555');
  for(const x of ['256.1.1.1:5','1.2.3.4:65536','1.2.3.4:0','1.2.3.4:5;id','host:123'])assert.throws(()=>validateTarget(x));
});
test('parser clips nodes, detects editable controls, and redacts password text',()=>{
  const parsed=parseLayout(layout(),100,200);assert.equal(parsed.nodes[1].editable,true);
  const secret=parseLayout(layout('secret',{password:'true'}),100,200);
  assert.equal(secret.nodes[1].text,'[密码控件]');assert.equal(secret.nodes[1].editable,false);
  assert.throws(()=>parseLayout('{"nothing":true}',100,200));
});
test('fingerprint includes rotation, full text, and state changes',()=>{
  const a=parseLayout(layout('a'.repeat(350)),100,200);
  assert.notEqual(a.fingerprint,parseLayout(layout('a'.repeat(350)+'b'),100,200).fingerprint);
  assert.notEqual(a.fingerprint,parseLayout(layout('a'.repeat(350)),200,100).fingerprint);
  assert.notEqual(a.fingerprint,parseLayout(layout('a'.repeat(350),{checked:true}),100,200).fingerprint);
});
test('connection success text without authorized shell is rejected',async()=>{
  const hdc=new FakeHdc();hdc.failIdentity=true;
  const device=new HarmonyDevice(hdc,()=>({width:100,height:200}),()=>false);
  await assert.rejects(device.connect('127.0.0.1:5555'),/未授权/);
  await assert.rejects(device.execute('observe_screen',{},new RunControl()),/先连接/);
});
test('a successful tap is followed by a fresh observation',async()=>{
  const {device,hdc,control,obs}=await setup();
  const result=await device.execute('tap_element',{observation_id:obs.id,node_id:1},control);
  assert.equal(result.ok,true);assert.notEqual(result.observation?.id,obs.id);
  assert.match(hdc.mutations()[0],/click 50 40/);
  await assert.rejects(device.execute('tap',{observation_id:obs.id,x:50,y:40},control),/STALE/);
  assert.equal(hdc.mutations().length,1);
});
test('page changes prevent a tap even with a matching observation ID',async()=>{
  const {device,hdc,control,obs}=await setup();hdc.tree=layout('删除');
  await assert.rejects(device.execute('tap_element',{observation_id:obs.id,node_id:1},control),/PAGE_CHANGED/);
  assert.equal(hdc.mutations().length,0);
});
test('folding or rotating prevents using old coordinates',async()=>{
  const {device,hdc,control,obs,rotate}=await setup();rotate();
  await assert.rejects(device.execute('tap',{observation_id:obs.id,x:50,y:40},control),/PAGE_CHANGED/);
  assert.equal(hdc.mutations().length,0);
});
test('expired observations and out-of-bounds coordinates cannot mutate',async()=>{
  const {device,hdc,control,obs}=await setup();
  await assert.rejects(device.execute('tap',{observation_id:obs.id,x:100,y:40},control),/越界/);
  const now=Date.now;Date.now=()=>obs.capturedAt+60001;
  try{await assert.rejects(device.execute('back',{observation_id:obs.id},control),/过期/);}finally{Date.now=now;}
  assert.equal(hdc.mutations().length,0);
});
test('an unknown mutation outcome is never replayed by the device adapter',async()=>{
  const {device,hdc,control,obs}=await setup();hdc.failMutation=true;
  await assert.rejects(device.execute('tap',{observation_id:obs.id,x:50,y:40},control),/连接中断/);
  assert.equal(device.isConnected,false);
  await assert.rejects(device.execute('tap',{observation_id:obs.id,x:50,y:40},control),/STALE/);
  assert.equal(hdc.mutations().length,1);
});
test('pause during page validation prevents the pending click',async()=>{
  const {device,hdc,control,obs}=await setup();
  hdc.commandGate=async cmd=>{if(cmd.includes('dumpLayout')){control.pause();device.invalidate();}};
  await assert.rejects(device.execute('tap',{observation_id:obs.id,x:50,y:40},control),/PAGE_CHANGED/);
  assert.equal(hdc.mutations().length,0);
});
test('cancellation during observation does not retain an executable observation',async()=>{
  const {device,hdc,control,obs}=await setup();
  hdc.commandGate=async cmd=>{if(cmd.includes('dumpLayout'))control.stop();};
  await assert.rejects(device.execute('tap',{observation_id:obs.id,x:50,y:40},control),/CANCELLED/);
  assert.equal(hdc.mutations().length,0);
});
test('input is shell quoted and cannot execute command substitutions',async()=>{
  const {device,hdc,control,obs}=await setup();
  await device.execute('input_text',{observation_id:obs.id,node_id:1,text:'你好 $(id)'},control);
  assert.ok(hdc.mutations()[0].includes('inputText 50 40'));
  // Exercise both shell layers without invoking UITest.
  const command=hdc.mutations()[0].replace('shell ','').replace('uitest uiInput inputText 50 40','printf %s');
  assert.match(execFileSync('/bin/sh',['-c',command],{encoding:'utf8'}),/^你好 \$\(id\)/);
});
test('device rejects arbitrary tools and package-name injection',async()=>{
  const {device,control,hdc}=await setup();
  await assert.rejects(device.execute('shell',{command:'id'},control),/不可用/);
  await assert.rejects(device.execute('launch_app',{bundle:'com.test;id',ability:'EntryAbility'},control),/无效/);
  assert.equal(hdc.mutations().length,0);
});
test('an ambiguous application launch stops the model loop without retrying',async()=>{
  const {device,hdc}=await setup();hdc.launchStatus=1;
  const model=new FakeModel([toolReply([call('launch','launch_app',JSON.stringify({bundle:'com.test.app',ability:'EntryAbility'}))]),done]);
  await assert.rejects(new AgentLoop(model,device,()=>{}).run('打开应用'),/启动命令没有返回/);
  assert.equal(model.snapshots.length,1);
  assert.equal(hdc.calls.filter(c=>c.includes('aa start')).length,1);
});
test('tool results remain adjacent to assistant calls before image messages',async()=>{
  const model=new FakeModel([toolReply([call('a'),call('b')]),done]);const device=new FakeDevice();
  await new AgentLoop(model,device,()=>{}).run('查看页面');
  assert.deepEqual(device.calls,['observe_screen','observe_screen']);
  const history=model.snapshots[1];
  assert.deepEqual(history.map(m=>m.role),['system','user','assistant','tool','tool','user','user']);
  assert.equal(history[3].tool_call_id,'a');assert.equal(history[4].tool_call_id,'b');
  assert.ok(!String(history[3].content).includes('iVBOR'));assert.ok(device.invalidations>=2);
});
test('truncated model tool calls never execute',async()=>{
  const device=new FakeDevice();const model=new FakeModel([toolReply([call('a','tap','{"x":1}')],'length')]);
  await assert.rejects(new AgentLoop(model,device,()=>{}).run('点击'),/截断/);
  assert.deepEqual(device.calls,[]);
});
test('malformed tool arguments return an error without calling the device',async()=>{
  const device=new FakeDevice();const model=new FakeModel([toolReply([call('a','tap','{')]),done]);
  await new AgentLoop(model,device,()=>{}).run('点击');
  assert.deepEqual(device.calls,[]);assert.match(String(model.snapshots[1][3].content),/TOOL_ERROR/);
});
test('duplicate call IDs are rejected before any execution',async()=>{
  const device=new FakeDevice();const model=new FakeModel([toolReply([call('a'),call('a')])]);
  await assert.rejects(new AgentLoop(model,device,()=>{}).run('查看'),/ID/);assert.deepEqual(device.calls,[]);
});
test('stop while awaiting model prevents late tool execution and rejects concurrent runs',async()=>{
  const gate=deferred<ModelReply>();const entered=deferred<void>();
  const model:ModelClient={complete:async()=>{entered.resolve();return gate.promise;},cancel:()=>{}};
  const device=new FakeDevice();const loop=new AgentLoop(model,device,()=>{});
  const run=loop.run('查看');await entered.promise;
  await assert.rejects(loop.run('另一个任务'),/已有任务/);loop.stop();gate.resolve(toolReply([call('a')]));
  await assert.rejects(run,/CANCELLED/);assert.deepEqual(device.calls,[]);
});
test('paused run wakes on stop without hanging',async()=>{
  const control=new RunControl();control.pause();const wait=control.checkpoint();control.stop();
  await assert.rejects(wait,/CANCELLED/);
});
test('only the two newest screenshot messages are retained',async()=>{
  const model=new FakeModel([toolReply([call('a')]),toolReply([call('b')]),toolReply([call('c')]),done]);
  await new AgentLoop(model,new FakeDevice(),()=>{}).run('查看');
  assert.equal(model.snapshots[3].filter(m=>Array.isArray(m.content)).length,2);
  assert.equal(model.snapshots[3].filter(m=>m.role==='tool').length,3);
});
test('step limit stops a looping model and clears observations',async()=>{
  const device=new FakeDevice();const model=new FakeModel([toolReply([call('a')]),toolReply([call('b')])]);
  await assert.rejects(new AgentLoop(model,device,()=>{}).run('查看',2),/上限/);assert.ok(device.invalidations>=2);
});
test('model adapter sends tools and rejects malformed responses',async()=>{
  let payload='';let url='';
  const transport:HttpTransport={post:async(u,_h,b)=>{url=u;payload=b;return JSON.stringify({choices:[{message:done.message,finish_reason:'stop'}]});},cancel:()=>{}};
  const settings={baseUrl:'https://example.com/v1/',model:'test',apiKey:'test-only',screenshots:false};
  const client=new OpenAIClient(settings,transport);
  await client.complete([{role:'user',content:'查看'}],new RunControl());
  assert.equal(url,'https://example.com/v1/chat/completions');assert.equal(JSON.parse(payload).tools.length,13);
  transport.post=async()=>'{"choices":[{"message":{"role":"assistant","tool_calls":[{}]},"finish_reason":"tool_calls"}]}';
  await assert.rejects(client.complete([],new RunControl()),/无效工具/);
  settings.baseUrl='http://example.com/v1';await assert.rejects(client.complete([],new RunControl()),/HTTPS/);
});
