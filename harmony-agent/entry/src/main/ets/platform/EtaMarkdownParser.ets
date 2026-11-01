// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/StreamingMarkdown.kt
// Dispatchers.Default parse work @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { taskpool } from '@kit.ArkTS';
import { StreamingGfmParserSession, StreamingGfmSnapshot } from '../core/eta/markdown/streamingGfmParser';
import { StreamingMarkdownTarget } from '../core/eta/markdown/streamingMarkdownState';
import { MarkdownBlock, MarkdownInlineStyle, revealKeys } from '../core/eta/markdown/markdownModel';
interface MarkdownWireDocument { renderedSource:string; blocks:MarkdownBlock[]; }
@Concurrent
function parseMarkdownOnWorker(content:string,complete:boolean,styleJson:string):string {
  const style:MarkdownInlineStyle=JSON.parse(styleJson) as MarkdownInlineStyle;
  const parsed=new StreamingGfmParserSession().parse(content,complete,style);
  const wire:MarkdownWireDocument={renderedSource:parsed.renderedSource,blocks:parsed.document.blocks};
  return JSON.stringify(wire);
}
// Same work on the calling thread. A @Concurrent function may only use imports and locals, so it cannot
// share this helper; keep the two bodies identical.
function parseMarkdownHere(content:string,complete:boolean,styleJson:string):string {
  const style:MarkdownInlineStyle=JSON.parse(styleJson) as MarkdownInlineStyle;
  const parsed=new StreamingGfmParserSession().parse(content,complete,style);
  const wire:MarkdownWireDocument={renderedSource:parsed.renderedSource,blocks:parsed.document.blocks};
  return JSON.stringify(wire);
}
export async function parseEtaMarkdown(target:StreamingMarkdownTarget):Promise<StreamingGfmSnapshot> {
  const styleJson=JSON.stringify(target.style);
  const result=await taskpool.execute(parseMarkdownOnWorker,target.content,!target.isStreaming,styleJson);
  // The Linux previewer has no taskpool module and resolves with an empty object. Parse here instead of
  // letting the caller degrade the reply to one plain paragraph; a worker failure still rejects as before.
  const json=typeof result==='string'?result:parseMarkdownHere(target.content,!target.isStreaming,styleJson);
  const wire=JSON.parse(json) as MarkdownWireDocument;
  return new StreamingGfmSnapshot(target.content,wire.renderedSource,!target.isStreaming,{blocks:wire.blocks,revealKeys:revealKeys(wire.blocks),inlineStyle:target.style});
}
