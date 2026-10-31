// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/MarkdownContent.kt,
// StreamingMarkdown.kt and StaticMarkdown.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { InlineSpan, MarkdownText, MarkdownDocument, defaultInlineStyle, plainText } from './markdownModel';
import { StreamingMarkdownState, MarkdownParseCancellation, markdownTarget, StreamingMarkdownTarget } from './streamingMarkdownState';
import { StreamingGfmSnapshot } from './streamingGfmParser';
import { EtaMarkdownDefaults } from '../tokens';
export interface RenderSpan { key:string; text:string; bold:boolean; italic:boolean; strike:boolean; code:boolean; url:string; visible:boolean; }
/** Keep the complete text in layout, changing only paint at grapheme boundaries. */
export function renderSpans(value:MarkdownText,visible=value.text.length):RenderSpan[] {
  const edges=new Set([0,value.text.length,Math.max(0,Math.min(visible,value.text.length))]);
  for(const span of value.spans){edges.add(span.start);edges.add(span.end);}
  const points=[...edges].filter(n=>n>=0&&n<=value.text.length).sort((a,b)=>a-b), result:RenderSpan[]=[];
  for(let i=1;i<points.length;i++){
    const start=points[i-1],end=points[i];if(start===end)continue;
    const active=value.spans.filter(s=>s.start<=start&&s.end>=end);
    result.push({key:String(start),text:value.text.slice(start,end),bold:active.some(s=>s.kind==='bold'),italic:active.some(s=>s.kind==='italic'),strike:active.some(s=>s.kind==='strike'),code:active.some(s=>s.kind==='code'),url:active.find(s=>s.kind==='link')?.url||'',visible:start<visible});
  }
  return result;
}
/** Same min/max intrinsic columns and proportional expansion as MarkdownTableBlock. */
export function tableColumnWidths(intrinsic:number[],available:number):number[] {
  const widths=intrinsic.map(n=>Math.max(EtaMarkdownDefaults.TableMinColumnWidth,Math.min(EtaMarkdownDefaults.TableMaxColumnWidth,n)));
  const natural=widths.reduce((a,b)=>a+b,0);
  if(natural>0&&natural<available){const extra=available-natural;let assigned=0;return widths.map((n,i)=>{const share=i===widths.length-1?extra-assigned:Math.floor(extra*n/natural);assigned+=share;return n+share;});}
  return widths;
}
export class MarkdownRenderSession {
  readonly state=new StreamingMarkdownState();
  private cancellation=new MarkdownParseCancellation();
  private listeners=new Set<()=>void>();
  private target='';
  private finished=false;
  private disposed=false;
  private everStreaming=false;
  private manualThinking:boolean|null=null;
  constructor(parse?: (target:StreamingMarkdownTarget)=>Promise<StreamingGfmSnapshot>){
    const safeParse=parse?async (target:StreamingMarkdownTarget):Promise<StreamingGfmSnapshot>=>{
      try{return await parse(target);}catch(_){
        // A rejected platform task must not erase a received reply or kill the consumer.
        return new StreamingGfmSnapshot(target.content,target.content,!target.isStreaming,{blocks:[{kind:'paragraph',offset:0,text:plainText(target.content)}],revealKeys:new Set([0]),inlineStyle:target.style});
      }
    }:undefined;
    void this.state.parseUpdates(this.cancellation,safeParse,()=>this.listeners.forEach(l=>l()));}
  get document():MarkdownDocument {return this.state.snapshot?.document??{blocks:[],revealKeys:new Set(),inlineStyle:defaultInlineStyle};}
  update(content:string,streaming:boolean):void {
    if(this.disposed||(content===this.target&&!streaming===this.finished))return;
    if(streaming&&!this.everStreaming){this.everStreaming=true;if(this.state.isAttachedToContent)this.state.revealCoordinator.resumeAnimationsAfterCatchUp();}
    this.target=content;this.finished=!streaming;
    this.state.parseTargets.send(markdownTarget(content,streaming));
  }
  subscribe(listener:()=>void):()=>void {this.listeners.add(listener);return()=>this.listeners.delete(listener);}
  attach():void {this.state.attachContent();if(this.everStreaming)this.state.revealCoordinator.resumeAnimationsAfterCatchUp();}
  detach():void {this.state.detachContent();if(!this.state.isAttachedToContent)this.state.revealCoordinator.pauseAnimationsAndCatchUp();}
  thinkingExpanded(streaming:boolean):boolean {return this.manualThinking??streaming;}
  thinkingAutomatic():boolean {return this.manualThinking===null;}
  toggleThinking(streaming:boolean):void {const expanded=this.thinkingExpanded(streaming);this.manualThinking=(this.manualThinking===null&&expanded)||!expanded;}
  toggleWork(active:boolean):void {this.manualThinking=!this.thinkingExpanded(active);}
  dispose():void {this.disposed=true;this.cancellation.cancel();this.state.parseTargets.close();this.listeners.clear();}
}
/** Retained by the list owner, so virtualization does not replay a completed reveal. */
export class MarkdownRenderStore {
  private sessions=new Map<string,MarkdownRenderSession>();
  constructor(private parse?: (target:StreamingMarkdownTarget)=>Promise<StreamingGfmSnapshot>){}
  get(id:string):MarkdownRenderSession {let s=this.sessions.get(id);if(!s){s=new MarkdownRenderSession(this.parse);this.sessions.set(id,s);}return s;}
  retain(ids:Set<string>):void {for(const [id,s]of this.sessions)if(!ids.has(id)){s.dispose();this.sessions.delete(id);}}
  clear():void {this.retain(new Set());}
}
