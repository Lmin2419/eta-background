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
test('overlapping emphasis, inline code and links survive a reveal boundary',()=>{
 const spans=renderSpans({text:'abcdef',spans:[{start:0,end:6,kind:'bold'},{start:1,end:4,kind:'italic'},{start:2,end:5,kind:'code'},{start:2,end:5,kind:'link',url:'https://example.com'}]},3);
 assert.equal(spans.map(s=>s.text).join(''),'abcdef');assert.ok(spans.every(s=>s.bold));
 assert.deepEqual(spans.find(s=>s.text==='c'),{key:'2',text:'c',bold:true,italic:true,strike:false,code:true,url:'https://example.com',visible:true});
 assert.equal(spans.find(s=>s.key==='3')?.visible,false);
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
