import test from 'node:test';
import assert from 'node:assert/strict';
import { CompletionStream } from '../entry/src/main/ets/core/stream';
import { OpenAIClient, HttpTransport, ModelSettings, fetchModels } from '../entry/src/main/ets/core/model';
import { RunControl } from '../entry/src/main/ets/core/types';
import { AgentLoop } from '../entry/src/main/ets/core/loop';
import { ConversationRepository } from '../entry/src/main/ets/core/conversations';
import { ConversationRun } from '../entry/src/main/ets/core/conversationRun';
import { messageBlocks, inlineParts } from '../entry/src/main/ets/core/markdown';
import { HarmonyDevice } from '../entry/src/main/ets/core/device';
const settings:ModelSettings={baseUrl:'https://provider.example/v1',model:'m',apiKey:'fixture',screenshots:false};
const frame=(delta:unknown,finish:string|null=null)=>'data: '+JSON.stringify({choices:[{index:0,delta,finish_reason:finish}]})+'\r\n\r\n';
const done='data: [DONE]\r\n\r\n';
const noJSON=()=>{throw new Error('unexpected JSON');};
const tool=(args:string,first=false)=>({tool_calls:[{index:0,...(first?{id:'c1',type:'function'}:{}),function:{...(first?{name:'observe_screen'}:{}),arguments:args}}]});
class Transport implements HttpTransport {
  calls:string[]=[];chunks:string[]=[];after:()=>void=()=>{};
  async post():Promise<string>{throw new Error('unexpected fallback request');}
  async stream(_url:string,_headers:Record<string,string>,body:string,onChunk:(chunk:string)=>void){this.calls.push(body);for(const part of this.chunks){onChunk(part);this.after();}}
  cancel(){}
}
test('SSE handles every character boundary, CRLF, comments, usage and Chinese text',()=>{
  const output:string[]=[];const parser=new CompletionStream(t=>output.push(t));
  const wire=': ping\r\n\r\n'+frame({role:'assistant',content:'你'})+frame({content:'好 🌻'})+frame({},'stop')+'data: {"choices":[],"usage":{"total_tokens":4}}\r\n\r\n'+done;
  for(const char of wire)parser.feed(char);
  assert.deepEqual(output,['你','你好 🌻']);assert.equal(parser.finish(noJSON).message.content,'你好 🌻');
});
test('SSE joins multi-line data and accepts a complete finish event without DONE',()=>{
  const parser=new CompletionStream(()=>{});
  parser.feed('data: {"choices": [\ndata: {"delta":{"content":"ok"},"finish_reason":"stop"}]}\n\n');
  assert.equal(parser.finish(noJSON).message.content,'ok');
});
test('tools are assembled without exposing arguments as assistant text',()=>{
  const texts:string[]=[];const parser=new CompletionStream(t=>texts.push(t));
  parser.feed(frame(tool('{',true))+frame(tool('}'))+frame({},'tool_calls')+done);
  const reply=parser.finish(noJSON);assert.deepEqual(texts,[]);assert.equal(reply.message.tool_calls?.[0].function.arguments,'{}');
});
test('incomplete framing and missing finish never become an executable tool reply',()=>{
  for(const wire of [frame(tool('{',true)),frame(tool('{}',true))+done,frame(tool('{}',true))+frame({},'tool_calls').trimEnd()]) {
    const parser=new CompletionStream(()=>{});parser.feed(wire);assert.throws(()=>parser.finish(noJSON),/中断/);
  }
});
test('stream rejects duplicated IDs, changed IDs and data after completion',()=>{
  const parser=new CompletionStream(()=>{});parser.feed(frame(tool('{}',true)));
  assert.throws(()=>parser.feed(frame({tool_calls:[{index:0,id:'c2'}]})),/ID/);
  const duplicate=new CompletionStream(()=>{});
  duplicate.feed(frame({tool_calls:[{index:0,id:'a',function:{name:'home',arguments:'{}'}},{index:1,id:'a',function:{name:'home',arguments:'{}'}}]})+frame({},'tool_calls'));
  assert.throws(()=>duplicate.finish(noJSON),/重复/);
  const late=new CompletionStream(()=>{});late.feed(frame({content:'ok'},'stop'));assert.throws(()=>late.feed(frame({content:'late'})),/完成后/);
});
test('provider errors and oversized response do not expose raw error content',()=>{
  const parser=new CompletionStream(()=>{});
  assert.throws(()=>parser.feed('data: {"error":{"message":"secret fixture"}}\n\n'),e=>e instanceof Error&&!e.message.includes('fixture'));
  assert.throws(()=>new CompletionStream(()=>{}).feed('x'.repeat(2_000_001)),/大小限制/);
});
test('OpenAI stream publishes before completion and accepts JSON-only servers without retrying',async()=>{
  const transport=new Transport();transport.chunks=[frame({content:'hello'}),frame({content:' world'},'stop'),done];
  const texts:string[]=[];const result=await new OpenAIClient(settings,transport,[]).complete([],new RunControl(),t=>texts.push(t));
  assert.deepEqual(texts,['hello','hello world']);assert.equal(result.message.content,'hello world');assert.equal(JSON.parse(transport.calls[0]).stream,true);
  transport.chunks=['{"choices":[{"message":{"role":"assistant","content":"JSON reply"},"finish_reason":"stop"}]}'];
  assert.equal((await new OpenAIClient(settings,transport,[]).complete([],new RunControl())).message.content,'JSON reply');assert.equal(transport.calls.length,2);
});
test('late streamed data is rejected on stop; no second request is made',async()=>{
  const transport=new Transport();transport.chunks=[frame({content:'first'}),frame({content:'late'},'stop')];
  const control=new RunControl();const received:string[]=[];transport.after=()=>control.stop();
  await assert.rejects(new OpenAIClient(settings,transport,[]).complete([],control,t=>received.push(t)),/CANCELLED/);
  assert.deepEqual(received,['first']);assert.equal(transport.calls.length,1);
});
test('explicit nonstream preference sends stream false once',async()=>{
  let body='';const io:HttpTransport={post:async(_u,_h,b)=>{body=b;return '{"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}';},stream:async()=>{throw new Error('must not stream');},cancel(){}};
  await new OpenAIClient({...settings,streaming:false},io).complete([],new RunControl());assert.equal(JSON.parse(body).stream,false);
});
test('AgentLoop never executes a truncated streamed tool call',async()=>{
  const io=new Transport();io.chunks=[frame(tool('{}',true))];let actions=0;
  const loop=new AgentLoop(new OpenAIClient(settings,io),{execute:async()=>{actions++;return {ok:true};},invalidate(){}},()=>{});
  await assert.rejects(loop.run('test'),/中断/);assert.equal(actions,0);
});
test('AgentLoop shows intermediate text, journals before tools, then streams the final answer',async()=>{
  const io=new Transport();let round=0;const events:string[]=[];
  io.stream=async(_u,_h,_b,emit)=>{
    if(round++===0){emit(frame({content:'先看一下。'}));emit(frame(tool('{}',true))+frame({},'tool_calls')+done);}
    else emit(frame({content:'完成。'},'stop')+done);
  };
  const loop=new AgentLoop(new OpenAIClient(settings,io),{execute:async()=>{events.push('execute');return {ok:true};},invalidate(){}},event=>events.push(event.kind),{journal:async event=>{events.push('journal:'+event.kind);}});
  assert.equal(await loop.run('test'),'先看一下。\n\n完成。');assert.ok(events.indexOf('stream')<events.indexOf('execute'));assert.ok(events.indexOf('journal:tool')<events.indexOf('execute'));
});
test('stop preserves streamed partial text, but never replays it as a completed answer',async()=>{
  let saved='';let n=0;
  const repo=new ConversationRepository({read:async()=>saved,write:async value=>{saved=value;}},()=>String(++n));await repo.load();const id=await repo.create('p');
  const io=new Transport();io.chunks=[frame({content:'收到 fixture 的一部分'}),frame({content:'不应收到'},'stop')];
  let run:ConversationRun;
  run=new ConversationRun(repo,{conversationId:id,text:'开始',modelLabel:'m',profileId:'p',mode:'chat',secrets:['fixture']},(context,journal)=>{
    const loop=new AgentLoop(new OpenAIClient(settings,io,[]),{execute:async()=>({ok:true}),invalidate(){}},event=>{if(event.kind==='stream')run.stop();},{history:context.messages,allowDevice:false,journal});return loop;
  });
  await assert.rejects(run.run(),/停止/);
  const turn=repo.get(id)!.turns[0];assert.equal(turn.status,'stopped');assert.match(turn.reply,/一部分/);assert.ok(!saved.includes('fixture'));assert.ok(!saved.includes('不应收到'));
  const next=await repo.begin(id,'继续聊','m','p','chat');assert.ok(!JSON.stringify(next.context).includes('一部分'));
});
test('catalog follows bounded cursor pagination on the same endpoint, removes duplicates and retains all 251 models',async()=>{
  const urls:string[]=[];const io:HttpTransport={post:async()=>'',cancel(){},get:async url=>{
    urls.push(url);return JSON.stringify(urls.length===1?{data:Array.from({length:200},(_,i)=>({id:'m'+i})),has_more:true,last_id:'m199'}:{data:[{id:'m199'},...Array.from({length:51},(_,i)=>({id:'m'+(i+200)}))]});}};
  const result=await fetchModels(settings,io);assert.equal(result.ids.length,251);assert.equal(result.partial,false);assert.equal(urls[1],'https://provider.example/v1/models?after=m199&limit=100');
});
test('catalog never sends credentials to a supplied next URL and detects repeating cursors',async()=>{
  const urls:string[]=[];const io:HttpTransport={post:async()=>'',cancel(){},get:async url=>{urls.push(url);return JSON.stringify({data:[{id:'m'}],next:'https://foreign.example/steal'});}};
  assert.equal((await fetchModels(settings,io)).partial,true);assert.equal(urls.length,1);
  io.get=async url=>{urls.push(url);return JSON.stringify({data:[{id:'m'}],has_more:true});};
  assert.equal((await fetchModels(settings,io)).partial,true);assert.equal(urls.length,3);assert.ok(urls.every(u=>u.startsWith(settings.baseUrl)));
});
test('native message formatting preserves unfinished code fences and renders headings, lists and emphasis',()=>{
  const blocks=messageBlocks('# 标题\n\n段落 **加粗**\n\n- 事项\n> 引用\n```ts\nlet x = 1;');
  assert.deepEqual(blocks.map(b=>b.kind),['heading','text','text','quote','code']);assert.equal(blocks.at(-1)?.text,'let x = 1;');
  assert.deepEqual(inlineParts('a **b** `c`').map(p=>p.kind),['text','bold','text','code']);
});
test('lost HDC session invalidates connection and stops further capability commands',async()=>{
  let broken=false,calls=0;
  const device=new HarmonyDevice({command:async command=>{calls++;if(broken)return '';const marker=command.match(/__HARMONY_AGENT_EXIT_\d+__/)?.[0];return (command.includes('uitest')?'dumpLayout uiInput':'uid=2000')+'\n'+marker+'0\n';}},()=>({width:100,height:100}),()=>false);
  await device.connect('127.0.0.1:12345');calls=0;broken=true;
  const result=await device.virtualScreenDiagnostics(new RunControl());assert.equal(calls,1);assert.equal(device.isConnected,false);assert.match(result,/后续检测已停止/);
});
