// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentConversationLoading.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaComponentSizes as S, MiuixFontSizes as F } from '../../../core/eta/tokens';
@Component
export struct AgentConversationLoading {
  @Prop failed:boolean=false;
  onRetry:()=>void=()=>{};
  build(){Column({space:S.Dp16}){if(this.failed){Text($r('app.string.conversation_load_failed')).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface'));Button($r('app.string.action_retry')).onClick(this.onRetry)}else{LoadingProgress().width(S.Dp24).height(S.Dp24).color($r('app.color.miuix_primary'));Text($r('app.string.conversation_loading')).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface'))}}.width('100%').height('100%').justifyContent(FlexAlign.Center).padding(S.Dp24)}
}
