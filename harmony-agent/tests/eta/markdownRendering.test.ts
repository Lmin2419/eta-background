// Integration of MarkdownContent.kt/StreamingMarkdown.kt @ 3b1794d with ArkUI paint boundaries.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { renderSpans, tableColumnWidths, MarkdownRenderSession, MarkdownRenderStore } from '../../entry/src/main/ets/core/eta/markdown/markdownRendering';
import { plainText } from '../../entry/src/main/ets/core/eta/markdown/markdownModel';
import { graphemeBoundaries } from '../../entry/src/main/ets/core/eta/markdown/smoothTextReveal';
import { foundationChatMessages } from '../../entry/src/main/ets/core/eta/foundationChatMessages';
import { MessageRow } from '../../entry/src/main/ets/core/foundation/controller';
import { toTimelineEntries } from '../../entry/src/main/ets/core/eta/agentChatTimelineEntries';
const turn=(patch:Partial<MessageRow>={}):MessageRow=>({id:'run',request:'问',reply:'答',reasoning:'思考',model:'m',active:true,status:'进行中',notice:'',...patch});
const settle=async()=>{for(let i=0;i<20;i++)await Promise.resolve();};
test('reveal paints whole graphemes while retaining identical full text for layout',()=>{
 const value=plainText('你好👨‍👩‍👧‍👦e\u0301!');
 for(const boundary of graphemeBoundaries(value.text)){
  const spans=renderSpans(value,boundary);
  assert.equal(spans.map(s=>s.text).join(''),value.text);
  assert.equal(spans.filter(s=>s.visible).map(s=>s.text).join(''),value.text.slice(0,boundary));
 }
});
test('a span key never stays the same while what the span paints changes (ForEach reuses equal keys)',()=>{
 // Streaming growth plus every reveal boundary: any two spans sharing a key must be identical.
 const seen=new Map<string,string>();
 const full='你好，世界。code 和链接继续。';
 for(let length=1;length<=full.length;length++){
  const text=full.slice(0,length);
  const value={text,spans:text.length>5?[{start:3,end:5,kind:'bold' as const},{start:6,end:Math.min(10,text.length),kind:'code' as const}]:[]};
  for(let boundary=0;boundary<=text.length;boundary++){
   for(const span of renderSpans(value,boundary)){
    const {key,...painted}=span,before=seen.get(key),now=JSON.stringify(painted);
    if(before!==undefined)assert.equal(now,before,'key '+key+' was reused for different content');
    seen.set(key,now);
   }
  }
 }
});
test('span keys are stable for identical input and unique within one render',()=>{
 const value=plainText('stable text');
 assert.deepEqual(renderSpans(value,4).map(s=>s.key),renderSpans(value,4).map(s=>s.key));
 assert.notEqual(renderSpans(value,4)[0].key,renderSpans(value,6)[0].key);
 const spans=renderSpans({text:'aaaa',spans:[{start:1,end:2,kind:'bold'}]},3);
 assert.equal(new Set(spans.map(s=>s.key)).size,spans.length);
});
test('overlapping emphasis, inline code and links survive a reveal boundary',()=>{
 const spans=renderSpans({text:'abcdef',spans:[{start:0,end:6,kind:'bold'},{start:1,end:4,kind:'italic'},{start:2,end:5,kind:'code'},{start:2,end:5,kind:'link',url:'https://example.com'}]},3);
 assert.equal(spans.map(s=>s.text).join(''),'abcdef');assert.ok(spans.every(s=>s.bold));
 const {key:_key,...c}=spans.find(s=>s.text==='c')!;
 assert.deepEqual(c,{text:'c',bold:true,italic:true,strike:false,code:true,url:'https://example.com',visible:true});
 assert.equal(spans.find(s=>s.text==='d')?.visible,false);
});
test('table clamps intrinsic columns and apportions free width without losing rounding remainder',()=>{
 assert.deepEqual(tableColumnWidths([10,500],100),[72,260]);
 assert.deepEqual(tableColumnWidths([100,200],500),[166,334]);
 assert.deepEqual(tableColumnWidths([],500),[]);
});
test('completed history bypasses live reveal and retains parsed blocks across virtualization',async()=>{
 const store=new MarkdownRenderStore(),session=store.get('reply');session.attach();session.update('**历史**\n\n第二段',false);await settle();
 assert.equal(session.document.blocks.length,2);assert.equal(session.state.revealCoordinator.isAnimationPaused,true);
 session.detach();assert.equal(store.get('reply'),session);session.attach();assert.equal(session.state.isAttachedToContent,true);session.detach();store.clear();
});
test('streaming session publishes final document, stops callbacks after disposal and never exposes a replaced old target',async()=>{
 const s=new MarkdownRenderSession();let calls=0;s.subscribe(()=>calls++);s.attach();
 s.update('旧内容',true);s.update('最终 **内容**',false);await settle();
 assert.equal(s.state.snapshot?.originalSource,'最终 **内容**');assert.equal(s.state.snapshot?.isComplete,true);
 const before=calls;s.dispose();s.update('迟到',true);await settle();assert.equal(calls,before);
});
test('automatic thinking tail opens fully on first tap while work header closes immediately',()=>{
 const s=new MarkdownRenderSession();assert.equal(s.thinkingExpanded(true),true);s.toggleThinking(true);assert.equal(s.thinkingExpanded(false),true);assert.equal(s.thinkingAutomatic(),false);s.dispose();
 const work=new MarkdownRenderSession();work.toggleWork(true);assert.equal(work.thinkingExpanded(true),false);work.dispose();
});
test('a 3000-word streamed document preserves previous paragraphs and completed code/table structure',async()=>{
 const session=new MarkdownRenderSession();session.attach();
 const prefix='# 标题\n\n稳定段落\n\n';session.update(prefix+'开始',true);await settle();const stable=session.document.blocks[1];
 const large=prefix+('中文 emoji 👩🏽‍💻 '.repeat(3000))+'\n\n```ts\nconst n = 1;\n```\n\n| a | b |\n|---|---|\n| 1 | 2 |';
 session.update(large,true);await settle();assert.equal(session.document.blocks[1],stable);session.update(large,false);await settle();assert.equal(session.document.blocks[1].offset,stable.offset);assert.ok(session.document.blocks.some(b=>b.kind==='code'));assert.ok(session.document.blocks.some(b=>b.kind==='table'));session.detach();session.dispose();
});
test('turn projection uses stable identities and keeps stopped content without inventing tool execution',()=>{
 const first=foundationChatMessages([turn({reply:''})]),entries=toTimelineEntries(first);
 const second=foundationChatMessages([turn()],first),next=toTimelineEntries(second,entries);
 assert.equal(second[0],first[0]);assert.equal(next.find(e=>e.kind==='work')?.key,entries.find(e=>e.kind==='work')?.key);
 const stopped=foundationChatMessages([turn({active:false,resultStatus:'stopped',notice:'停止'})],second);
 assert.equal(stopped.find(m=>m.kind==='assistant')?.id,'run-assistant');assert.equal(stopped.find(m=>m.kind==='notice')?.code,'Stopped');assert.ok(stopped.every(m=>m.kind!=='tool'));
});
test('rejected platform parser displays received text and continues processing later targets',async()=>{
 let fail=true;const parser=new (await import('../../entry/src/main/ets/core/eta/markdown/streamingGfmParser')).StreamingGfmParserSession();
 const session=new MarkdownRenderSession(async target=>{if(fail)throw new Error('worker unavailable');return parser.parse(target.content,!target.isStreaming);});
 session.update('收到的 **原文**',true);await settle();assert.equal(session.document.blocks[0].kind,'paragraph');
 assert.equal(session.state.snapshot?.originalSource,'收到的 **原文**');
 fail=false;session.update('## 已恢复',false);await settle();assert.equal(session.document.blocks[0].kind,'heading');session.dispose();
});
