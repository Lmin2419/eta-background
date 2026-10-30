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
export async function parseEtaMarkdown(target:StreamingMarkdownTarget):Promise<StreamingGfmSnapshot> {
  const result=await taskpool.execute(parseMarkdownOnWorker,target.content,!target.isStreaming,JSON.stringify(target.style));
  const wire=JSON.parse(result as string) as MarkdownWireDocument;
  return new StreamingGfmSnapshot(target.content,wire.renderedSource,!target.isStreaming,{blocks:wire.blocks,revealKeys:revealKeys(wire.blocks),inlineStyle:target.style});
}
