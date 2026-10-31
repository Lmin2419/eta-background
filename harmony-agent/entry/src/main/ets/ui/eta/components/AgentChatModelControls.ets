// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatModelControls.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { intl, i18n } from '@kit.LocalizationKit';
import { AgentContextUsageUi, NumberFormatter, contextUsageProgress, formatContextUsage } from '../../../core/eta/modelPicker';
import { EtaContextDefaults as U, EtaChatDefaults as C, EtaComponentSizes as S, MiuixFontSizes as F } from '../../../core/eta/tokens';
class LocaleNumberFormatter implements NumberFormatter {
  format(value: number, minimum: number, maximum: number, grouping: boolean): string {
    return new intl.NumberFormat(i18n.System.getAppPreferredLanguage(),{minimumFractionDigits:minimum,maximumFractionDigits:maximum,useGrouping:grouping}).format(value);
  }
}
@Component
export struct AgentContextUsageButton {
  @Prop usage: AgentContextUsageUi = {contextTokens:null,contextWindow:null};
  @State private visible: boolean = false;
  private summary(): string {
    const resource=this.getUIContext().getHostContext()?.resourceManager;
    return formatContextUsage(this.usage,new LocaleNumberFormatter(),
      resource?.getStringSync($r('app.string.context_no_previous_usage').id)||'',resource?.getStringSync($r('app.string.context_no_model_limit').id)||'');
  }
  private progress(): number { return contextUsageProgress(this.usage.contextTokens,this.usage.contextWindow)??S.Dp0; }
  @Builder private detail() {
    Column({space:S.Dp8}) {
      Text($r('app.string.ui_contextual_usage_d12810')).fontSize(F.Subtitle).fontColor($r('app.color.miuix_on_surface'))
      Text(this.usage.contextWindow===null?this.summary():this.usage.contextTokens===null?$r('app.string.context_usage_after_response'):$r('app.string.context_usage_previous_response',this.summary()))
        .fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary'))
    }.padding(S.Dp16).constraintSize({maxWidth:S.Dp280})
  }
  build() {
    Button({type:ButtonType.Normal}) {
      Progress({value:this.progress()*100,total:100,type:ProgressType.Ring}).width(C.ActionIconSize).height(C.ActionIconSize)
        .style({strokeWidth:S.Dp2_5}).color(contextUsageProgress(this.usage.contextTokens,this.usage.contextWindow)===null?$r('app.color.miuix_on_surface_variant_actions'):this.progress()>=U.ErrorThreshold?$r('app.color.eta_status_error'):this.progress()>=U.WarningThreshold?$r('app.color.eta_status_warning'):$r('app.color.miuix_primary'))
        .backgroundColor($r('app.color.miuix_secondary_container'))
    }.width(C.ActionSize).height(C.ActionSize).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent'))
      .accessibilityText($r('app.string.context_usage_description',this.summary())).onClick(()=>{this.visible=true;})
      .bindPopup($$this.visible,{builder:this.detail,placement:Placement.Top,enableArrow:false,popupColor:$r('app.color.miuix_surface')})
  }
}
