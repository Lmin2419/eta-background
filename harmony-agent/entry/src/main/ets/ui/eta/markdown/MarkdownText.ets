// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/MarkdownContent.kt,
// MarkdownStyle.kt and SmoothTextReveal.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { common } from '@kit.AbilityKit';
import { MarkdownText as MarkdownValue, plainText } from '../../../core/eta/markdown/markdownModel';
import { RenderSpan, renderSpans } from '../../../core/eta/markdown/markdownRendering';
import { SmoothTextRevealCoordinator, SmoothTextRevealState, RevealNode } from '../../../core/eta/markdown/smoothTextReveal';
import { EtaMarkdownFonts as F, EtaMarkdownDefaults as M, EtaComponentSizes as S } from '../../../core/eta/tokens';
import { EtaFontFamilies } from '../../../platform/EtaFonts';
import { openEtaLink } from '../../../platform/EtaTextActions';
class MarkdownRevealNode implements RevealNode {
  private notify:()=>void;
  constructor(notify:()=>void){this.notify=notify;}
  onRevealDataChanged():void {this.notify();}
}
@Component
export struct MarkdownTextView {
  @Prop @Watch('refresh') value:MarkdownValue=plainText('');
  @Prop fontSizeValue:number=F.Fp16;
  @Prop lineHeightValue:number=M.ANSWER_LINE_HEIGHT_SP;
  @Prop muted:boolean=false;
  @Prop code:boolean=false;
  @Prop bold:boolean=false;
  @Prop textAlignment:TextAlign=TextAlign.Start;
  blockKey:number=0;
  reveal:SmoothTextRevealCoordinator|undefined=undefined;
  @State private spans:RenderSpan[]=[];
  private node:MarkdownRevealNode=new MarkdownRevealNode(()=>this.paint());
  private revealState:SmoothTextRevealState|undefined=undefined;
  aboutToAppear():void {if(this.reveal){this.revealState=new SmoothTextRevealState(this.blockKey,this.reveal);this.revealState.attach(this.node);}this.refresh();}
  aboutToDisappear():void {this.revealState?.detach(this.node);}
  private refresh():void {
    this.revealState?.onTextLayout(this.value.text,{identity:String(this.blockKey)});
    this.paint();
  }
  private paint():void {
    const draw=this.revealState?.drawSnapshot();
    const count=draw?draw.boundaries[Math.min(Math.floor(draw.progress),draw.boundaries.length-1)]:this.value.text.length;
    this.spans=renderSpans(this.value,count);
  }
  private link(url:string):void {
    const context=this.getUIContext().getHostContext() as common.UIAbilityContext;
    void openEtaLink(context,url).catch(()=>{});
  }
  build() {
    Text() {
      ForEach(this.spans,(span:RenderSpan)=>{
        Span(span.text).fontSize(span.code?(this.muted?F.Fp12:F.Fp13):this.fontSizeValue)
          .fontWeight(span.bold||this.bold?FontWeight.Bold:FontWeight.Normal).fontStyle(span.italic?FontStyle.Italic:FontStyle.Normal)
          .fontFamily(this.code||span.code?EtaFontFamilies.Normal:'sans-serif')
          .fontColor(!span.visible?$r('app.color.eta_transparent'):span.url?$r('app.color.miuix_primary'):this.muted?$r('app.color.miuix_on_surface_variant_summary'):$r('app.color.miuix_on_surface'))
          .decoration({type:span.strike?TextDecorationType.LineThrough:TextDecorationType.None})
          .textBackgroundStyle({color:span.code&&span.visible?$r('app.color.eta_markdown_inline_code'):$r('app.color.eta_transparent'),radius:S.Dp2})
          .onClick(()=>{if(span.visible&&span.url)this.link(span.url);})
      },(span:RenderSpan)=>span.key)
    }.fontSize(this.fontSizeValue).lineHeight(this.lineHeightValue).textAlign(this.textAlignment)
      .fontColor(this.muted?$r('app.color.miuix_on_surface_variant_summary'):$r('app.color.miuix_on_surface'))
      .copyOption(CopyOptions.LocalDevice).wordBreak(this.code?WordBreak.NORMAL:WordBreak.BREAK_WORD)
  }
}
