// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/MarkdownContent.kt,
// MarkdownStyle.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { MarkdownBlock, MarkdownText, MarkdownCode, MarkdownQuote, MarkdownAlert, MarkdownList, MarkdownListItem, MarkdownTable, MarkdownTableCell, plainText } from '../../../core/eta/markdown/markdownModel';
import { MarkdownTone, markdownBlockGap, markdownCompactGap, listMarkerVisible } from '../../../core/eta/markdown/markdownStylePolicy';
import { SmoothTextRevealCoordinator } from '../../../core/eta/markdown/smoothTextReveal';
import { tableColumnWidths } from '../../../core/eta/markdown/markdownRendering';
import { EtaComponentSizes as S, EtaMarkdownFonts as F, EtaMarkdownDefaults as M, MiuixFontSizes as MF } from '../../../core/eta/tokens';
import { copyEtaText } from '../../../platform/EtaTextActions';
import { MarkdownTextView } from './MarkdownText';
@Component
export struct MarkdownCopyButton {
  @Prop text:string='';
  @State private copied:boolean=false;
  private timer:number=-1;
  aboutToDisappear():void {clearTimeout(this.timer);}
  private copy():void {void copyEtaText(this.text).then(()=>{this.copied=true;clearTimeout(this.timer);this.timer=setTimeout(()=>{this.copied=false;},M.CopyResetMs);}).catch(()=>{});}
  build(){Button({type:ButtonType.Normal}){Image(this.copied?$r('app.media.ic_rounded_check'):$r('app.media.ic_rounded_content_copy')).width(S.Dp14).height(S.Dp14).fillColor(this.copied?$r('app.color.miuix_primary'):$r('app.color.miuix_on_surface_variant_summary'))}.width(S.Dp32).height(S.Dp32).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent')).accessibilityText(this.copied?$r('app.string.copy_copied'):$r('app.string.copy_code')).onClick(()=>this.copy())}
}
@Component
export struct MarkdownBlocks {
  @Prop blocks:MarkdownBlock[]=[];
  @Prop tone:MarkdownTone='Answer';
  @Prop muted:boolean=false;
  @Prop compact:boolean=false;
  @Prop depth:number=0;
  @Prop startedKeys:number[]=[];
  reveal:SmoothTextRevealCoordinator|undefined=undefined;
  private gap(index:number):number {const previous=index>0?this.blocks[index-1]:null;const value=this.compact?markdownCompactGap(previous,this.tone):markdownBlockGap(previous,this.blocks[index],this.tone);return this.getUIContext().px2vp(this.getUIContext().fp2px(value));}
  build(){Column(){ForEach(this.blocks,(block:MarkdownBlock,index:number)=>{
    MarkdownBlockView({block,tone:this.tone,muted:this.muted,depth:this.depth,startedKeys:this.startedKeys,reveal:this.reveal}).margin({top:this.gap(index)})
  },(block:MarkdownBlock)=>String(block.offset))}.width('100%').alignItems(HorizontalAlign.Start)}
}
@Component
struct MarkdownTableView {
  @Prop @Watch('measureColumns') table:MarkdownTable={kind:'table',offset:0,alignments:[],header:[],rows:[]};
  @Prop tone:MarkdownTone='Answer';
  reveal:SmoothTextRevealCoordinator|undefined=undefined;
  @State private widths:number[]=[];
  private available:number=0;
  aboutToAppear():void {this.measureColumns();}
  private measureColumns():void {
    const intrinsic:number[]=this.table.header.map((_cell:MarkdownTableCell,column:number)=>Math.max(...[this.table.header,...this.table.rows].map((row:MarkdownTableCell[])=>{
      const size=this.getUIContext().getMeasureUtils().measureTextSize({textContent:row[column]?.text.text||'',fontSize:(this.tone==='Answer'?F.Fp14:F.Fp13)+'fp'});
      return this.getUIContext().px2vp(Number(size.width))+S.Dp24;
    })));
    this.widths=tableColumnWidths(intrinsic,this.available);
  }
  @Builder private row(cells:MarkdownTableCell[],header:boolean) {
    Row(){ForEach(cells,(cell:MarkdownTableCell,index:number)=>{
      Column(){MarkdownTextView({value:cell.text,fontSizeValue:this.tone==='Answer'?F.Fp14:F.Fp13,lineHeightValue:this.tone==='Answer'?F.Fp21:F.Fp19,bold:header,textAlignment:this.table.alignments[index]==='Center'?TextAlign.Center:this.table.alignments[index]==='End'?TextAlign.End:TextAlign.Start,blockKey:cell.offset,reveal:this.reveal})}
        .width(this.widths[index]||M.TableMinColumnWidth).alignSelf(ItemAlign.Stretch).padding({left:S.Dp12,right:S.Dp12,top:S.Dp8,bottom:S.Dp8})
        .border({width:S.Dp0_5,color:$r('app.color.eta_markdown_divider')})
    },(cell:MarkdownTableCell)=>String(cell.offset))}.alignItems(VerticalAlign.Top).backgroundColor(header?$r('app.color.miuix_surface_container'):$r('app.color.eta_transparent'))
  }
  build(){Scroll(){Column(){this.row(this.table.header,true);ForEach(this.table.rows,(row:MarkdownTableCell[])=>{this.row(row,false)},(row:MarkdownTableCell[])=>String(row[0]?.offset))}.borderRadius(S.Dp12).clip(true)}.width('100%').scrollable(ScrollDirection.Horizontal).scrollBar(BarState.Auto).onAreaChange((_old,area)=>{const width=Number(area.width);if(width!==this.available){this.available=width;this.measureColumns();}})}
}
@Component
struct MarkdownBlockView {
  @Prop block:MarkdownBlock={kind:'rule',offset:0};
  @Prop tone:MarkdownTone='Answer';
  @Prop muted:boolean=false;
  @Prop depth:number=0;
  @Prop startedKeys:number[]=[];
  reveal:SmoothTextRevealCoordinator|undefined=undefined;
  private text():MarkdownText {return this.block.kind==='paragraph'||this.block.kind==='heading'||this.block.kind==='code'?this.block.text:plainText('');}
  private headingSize():number {if(this.tone==='Thinking')return F.Fp15;const level=this.block.kind==='heading'?this.block.level:0;return level===1?F.Fp20:level===2?F.Fp18:level===3?F.Fp17:F.Fp16;}
  private lineHeight():number {if(this.tone==='Thinking')return M.THINKING_LINE_HEIGHT_SP;const level=this.block.kind==='heading'?this.block.level:0;return level===1?F.Fp28:level===2?F.Fp26:level===3?F.Fp25:M.ANSWER_LINE_HEIGHT_SP;}
  private nested():MarkdownBlock[]{return this.block.kind==='quote'||this.block.kind==='alert'?this.block.blocks:[];}
  private list():MarkdownList {return this.block as MarkdownList;}
  private marker(item:MarkdownListItem):string {return item.number!==null?item.number+'.':['•','◦','▪'][this.depth%3];}
  private markerWidth():number {const list=this.list();return list.ordered?String(list.items[list.items.length-1]?.number??1).length*M.ListMarkerDigitWidth+S.Dp8:S.Dp14;}
  private listGap():number {return this.list().loose?(this.tone==='Answer'?S.Dp10:S.Dp8):(this.tone==='Answer'?S.Dp4:S.Dp2);}
  private alertIcon():Resource {const kind=(this.block as MarkdownAlert).alertKind;return kind==='Tip'?$r('app.media.ic_rounded_lightbulb'):kind==='Important'?$r('app.media.ic_rounded_feedback'):kind==='Warning'?$r('app.media.ic_rounded_warning'):kind==='Caution'?$r('app.media.ic_rounded_report'):$r('app.media.ic_rounded_info');}
  private alertLabel():Resource {const kind=(this.block as MarkdownAlert).alertKind;return kind==='Tip'?$r('app.string.markdown_alert_tip'):kind==='Important'?$r('app.string.markdown_alert_important'):kind==='Warning'?$r('app.string.markdown_alert_warning'):kind==='Caution'?$r('app.string.markdown_alert_caution'):$r('app.string.markdown_alert_note');}
  private alertColor():Resource {const kind=(this.block as MarkdownAlert).alertKind;return kind==='Tip'?$r('app.color.eta_status_success'):kind==='Important'?$r('app.color.eta_markdown_important'):kind==='Warning'?$r('app.color.eta_status_warning'):kind==='Caution'?$r('app.color.miuix_error'):$r('app.color.miuix_primary');}
  build(){Column(){
    if(this.block.kind==='paragraph'||this.block.kind==='heading'){
      MarkdownTextView({value:this.text(),fontSizeValue:this.block.kind==='heading'?this.headingSize():this.tone==='Answer'?F.Fp16:F.Fp14,lineHeightValue:this.lineHeight(),bold:this.block.kind==='heading',muted:this.muted||this.tone==='Thinking',blockKey:this.block.offset,reveal:this.reveal})
    }else if(this.block.kind==='code'){
      Column(){Row(){Text((this.block as MarkdownCode).language||$r('app.string.markdown_code_block')).fontSize(MF.Footnote2).fontColor($r('app.color.miuix_on_surface_variant_summary')).maxLines(1).layoutWeight(1);MarkdownCopyButton({text:this.text().text})}.width('100%').padding({left:S.Dp14,right:S.Dp4,top:S.Dp2})
        Scroll(){MarkdownTextView({value:this.text(),fontSizeValue:this.tone==='Answer'?F.Fp13:F.Fp12,lineHeightValue:this.tone==='Answer'?F.Fp20:F.Fp18,code:true,muted:this.tone==='Thinking',blockKey:this.block.offset,reveal:this.reveal})}.width('100%').scrollable(ScrollDirection.Horizontal).scrollBar(BarState.Auto).padding({left:S.Dp14,right:S.Dp14,bottom:S.Dp12})
      }.width('100%').backgroundColor($r('app.color.miuix_surface_container')).borderRadius(S.Dp12)
    }else if(this.block.kind==='quote'){
      Row(){MarkdownBlocks({blocks:this.nested(),tone:this.tone,muted:true,depth:this.depth,startedKeys:this.startedKeys,reveal:this.reveal})}.width('100%').padding({left:S.Dp12}).border({width:{left:S.Dp3},color:$r('app.color.eta_markdown_divider')})
    }else if(this.block.kind==='alert'){
      Column({space:S.Dp6}){Row({space:S.Dp8}){Image(this.alertIcon()).width(S.Dp18).height(S.Dp18).fillColor(this.alertColor());Text(this.alertLabel()).fontSize(MF.Body2).fontWeight(FontWeight.Medium).fontColor(this.alertColor())}.width('100%');MarkdownBlocks({blocks:this.nested(),tone:this.tone,depth:this.depth,startedKeys:this.startedKeys,reveal:this.reveal})}.width('100%').alignItems(HorizontalAlign.Start).padding({left:S.Dp12}).border({width:{left:S.Dp3},color:this.alertColor()})
    }else if(this.block.kind==='list'){
      Column({space:this.listGap()}){ForEach(this.list().items,(item:MarkdownListItem)=>{
        Row(){
          if(item.checked!==null){Image(item.checked?$r('app.media.ic_rounded_check_box'):$r('app.media.ic_rounded_check_box_outline_blank')).width(S.Dp18).height(S.Dp18).fillColor(item.checked?$r('app.color.miuix_primary'):$r('app.color.eta_markdown_marker')).margin({right:S.Dp8}).opacity(listMarkerVisible(!!this.reveal,item.markerRevealKey,new Set(this.startedKeys))?1:0)}
          else {Text(this.marker(item)).fontSize(this.tone==='Answer'?F.Fp16:F.Fp14).lineHeight(this.tone==='Answer'?M.ANSWER_LINE_HEIGHT_SP:M.THINKING_LINE_HEIGHT_SP).fontColor($r('app.color.eta_markdown_marker')).textAlign(TextAlign.End).constraintSize({minWidth:this.markerWidth()}).margin({right:S.Dp8}).opacity(listMarkerVisible(!!this.reveal,item.markerRevealKey,new Set(this.startedKeys))?1:0)}
          MarkdownBlocks({blocks:item.blocks,tone:this.tone,muted:this.muted,compact:!this.list().loose,depth:this.depth+1,startedKeys:this.startedKeys,reveal:this.reveal}).layoutWeight(1)
        }.width('100%').alignItems(VerticalAlign.Top)
      },(item:MarkdownListItem)=>String(item.offset))}.width('100%')
    }else if(this.block.kind==='table'){MarkdownTableView({table:this.block as MarkdownTable,tone:this.tone,reveal:this.reveal})}
    else {Divider().strokeWidth(S.Dp0_5).color($r('app.color.eta_markdown_divider')).margin({top:S.Dp4,bottom:S.Dp4})}
  }.width('100%').alignItems(HorizontalAlign.Start)}
}
