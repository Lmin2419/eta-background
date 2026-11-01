// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatQueueStrip.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { QueuedMessageUi } from '../../../core/eta/model/conversation';
import { EtaComponentSizes as S, EtaQueueDefaults as Q, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { renderKey } from '../../../core/eta/renderKey';
@Component
export struct AgentChatQueueStrip {
  @Prop @Watch('reset') items:QueuedMessageUi[]=[];
  @Prop streaming:boolean=false;
  onSteer:(id:string)=>void=()=>{};
  onRecall:(id:string)=>void=()=>{};
  onRemove:(id:string)=>void=()=>{};
  @State private expanded:boolean=false;
  private reset():void{if(this.items.length<=1)this.expanded=false;}
  @Builder private preview(item:QueuedMessageUi){
    Text(item.content.replace(/\s+/g,' ').trim()).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface')).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis})
      .padding(S.Dp4).borderRadius(S.Dp6).layoutWeight(1).onClick(()=>this.onRecall(item.id))
  }
  @Builder private actions(item:QueuedMessageUi){Row(){
    if(!item.immediateInterrupt){Button({type:ButtonType.Normal}){Image(this.streaming?$r('app.media.ic_rounded_bolt'):$r('app.media.ic_rounded_arrow_upward')).width(S.Dp15).height(S.Dp15).fillColor($r('app.color.miuix_primary'))}
      .width(Q.QueueActionSize).height(Q.QueueActionSize).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent')).accessibilityText($r('app.string.chat_queue_steer_now')).onClick(()=>this.onSteer(item.id))}
    Button({type:ButtonType.Normal}){Image($r('app.media.ic_rounded_close')).width(S.Dp14).height(S.Dp14).fillColor($r('app.color.miuix_on_surface_variant_actions'))}
      .width(Q.QueueActionSize).height(Q.QueueActionSize).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent')).accessibilityText($r('app.string.chat_queue_remove')).onClick(()=>this.onRemove(item.id))
  }}
  build(){if(this.items.length){Column(){
    Row(){Row({space:S.Dp6}){
      Circle().width(S.Dp6).height(S.Dp6).fill($r('app.color.miuix_primary'))
      Text(this.items[0].immediateInterrupt?$r('app.string.chat_queue_interrupting'):this.items.length>1?$r('app.string.chat_queue_status_count',this.items.length):$r('app.string.chat_queue_status'))
        .fontSize(F.Footnote1).fontWeight(FontWeight.Medium).fontColor($r('app.color.miuix_primary'))
      if(this.items.length>1){Image($r('app.media.ic_rounded_expand_more')).width(S.Dp14).height(S.Dp14).fillColor($r('app.color.miuix_primary')).rotate({angle:this.expanded?Q.ChevronExpandedDegrees:S.Dp0})}
    }.onClick(()=>{if(this.items.length>1)this.getUIContext().animateTo({duration:Q.ExpandDurationMs},()=>{this.expanded=!this.expanded;});})
      if(!this.expanded){this.preview(this.items[0]);this.actions(this.items[0])}else{Blank()}
    }.padding({left:S.Dp12,right:S.Dp6,top:S.Dp4,bottom:S.Dp4}).width('100%')
    if(this.expanded){Scroll(){Column(){ForEach(this.items,(item:QueuedMessageUi,index:number)=>{Column(){
      Divider().strokeWidth(S.Dp0_5).color($r('app.color.eta_queue_divider')).margin({left:S.Dp12,right:S.Dp12})
      Row({space:S.Dp8}){Text(String(index+1)).fontSize(F.Footnote2).fontWeight(FontWeight.Medium).fontColor($r('app.color.miuix_on_surface_variant_summary'));this.preview(item);this.actions(item)}
        .padding({left:S.Dp14,right:S.Dp6,top:S.Dp3,bottom:S.Dp3}).width('100%')
    }},(item:QueuedMessageUi)=>renderKey(item.id,item)}.width('100%')}.width('100%').constraintSize({maxHeight:Q.QueueExpandedMaxHeight}).scrollBar(BarState.Auto).padding({bottom:S.Dp4})}
  }.width('100%').backgroundColor($r('app.color.miuix_surface_container')).border({width:S.Dp0_5,color:$r('app.color.eta_queue_outline')}).borderRadius(Q.QueueContainerCornerRadius).clip(true)}}
}
