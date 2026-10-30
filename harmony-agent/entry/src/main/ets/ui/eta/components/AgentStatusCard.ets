// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentStatusCard.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaComponentSizes as S, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { EtaCard } from './EtaCard';
@Component
export struct AgentStatusCard {
  @Prop title:ResourceStr='';
  @Prop currentStep:ResourceStr='';
  @Prop elapsedLabel:ResourceStr='';
  @Prop status:string='Running';
  onOpen:()=>void=()=>{};
  onStop:()=>void=()=>{};
  @Builder private body(){Column(){
    Row({space:S.Dp10}){if(this.status==='Running'){LoadingProgress().width(S.Dp18).height(S.Dp18).color($r('app.color.miuix_primary'))}else{Image(this.status==='Success'?$r('app.media.ic_rounded_check'):this.status==='Failed'?$r('app.media.ic_rounded_close'):$r('app.media.ic_rounded_more_horiz')).width(S.Dp18).height(S.Dp18).fillColor(this.status==='Cancelled'?$r('app.color.miuix_on_surface_variant_actions'):$r('app.color.miuix_primary'))}Text(this.title).fontSize(F.Headline1).fontColor($r('app.color.miuix_on_surface_container')).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis}).layoutWeight(1)}.width('100%')
    Text(this.currentStep).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).margin({top:S.Dp8}).width('100%')
    Row(){Text(this.elapsedLabel).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_actions')).layoutWeight(1);if(this.status==='Running'){Button($r('app.string.ui_stop_a17f70')).backgroundColor($r('app.color.eta_transparent')).fontColor($r('app.color.miuix_primary')).onClick(this.onStop)}}.width('100%').margin({top:S.Dp12})
  }.width('100%').padding(S.Dp16)}
  build(){Column(){EtaCard({content:this.body,clickable:true,onAction:this.onOpen})}.width('100%').padding({left:S.Dp12,right:S.Dp12,top:S.Dp6,bottom:S.Dp6})}
}
