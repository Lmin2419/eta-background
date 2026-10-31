// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ModelProviderListScreen.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController, ShellState, emptyShellState } from '../../../../core/foundation/controller';
import { resolveProviderSource } from '../../../../core/eta/providerSourceRegistry';
import { implementedProtocol } from '../../../../core/eta/protocol/foundationProviderBridge';
import { ModelProfile } from '../../../../core/profiles';
import { EtaComponentSizes as S, EtaProviderDefaults as P, MiuixFontSizes as F } from '../../../../core/eta/tokens';
import { ProviderSection, ProviderBrandIcon } from './ProviderComponents';
import { MiuixScaffoldPage } from '../../components/MiuixScaffoldPage';
import { EtaPreferenceRow, EtaArrowPreference } from '../../components/EtaPreferenceRow';
import { EtaPreferenceDivider, EtaPreferenceIcon } from '../../components/EtaPreferenceStyle';
import { EtaHaptics } from '../../../../platform/EtaHaptics';
import { EtaHaptic } from '../../../../core/eta/hapticFeedback';
@Component
export struct ModelProviderListScreen {
  @Prop state:ShellState=emptyShellState();controller:FoundationController|undefined=undefined;
  private count(resource:Resource,value:number):string{return this.getUIContext().getHostContext()!.resourceManager.getIntPluralStringValueSync(resource.id,value,value);}
  private type(p:ModelProfile):string{return this.getUIContext().getHostContext()!.resourceManager.getStringSync((p.protocol==='anthropic'?$r('app.string.eta_provider_type_anthropic'):p.protocol==='gemini'?$r('app.string.eta_provider_type_gemini'):$r('app.string.eta_provider_type_openai')).id);}
  private remove(p:ModelProfile):void{if(p.isBuiltIn)return;EtaHaptics.perform(EtaHaptic.LongPress);this.getUIContext().showAlertDialog({title:$r('app.string.ui_remove_provider_9f848f'),message:$r('app.string.provider_delete_summary',p.name),primaryButton:{value:$r('app.string.action_cancel'),action:()=>{}},secondaryButton:{value:$r('app.string.ui_delete_3755f5'),action:()=>{void this.controller?.removeProviderFromList(p.id);}}});}
  @Builder private logo(source:string){ProviderBrandIcon({source})}
  @Builder private catalogIcon(){EtaPreferenceIcon({icon:$r('app.media.ic_rounded_storefront'),color:$r('app.color.eta_preference_blue')})}
  @Builder private create(){Column(){
    EtaArrowPreference({title:$r('app.string.eta_catalog_add'),summary:$r('app.string.eta_catalog_add_summary'),hasLeading:true,startAction:() => { this.catalogIcon(); },onAction:()=>this.controller?.openCommunity()});EtaPreferenceDivider()
    EtaArrowPreference({title:$r('app.string.ui_added_openai_compatible_6bd471'),summary:$r('app.string.ui_support_chatgpt_deepseek_kimi_glm_qwen_etc_b31d02'),hasLeading:true,startAction:()=>{this.logo('openai');},onAction:()=>{void this.controller?.openProvider();}});EtaPreferenceDivider()
    EtaArrowPreference({title:$r('app.string.ui_new_anthropic_db6098'),summary:$r('app.string.ui_support_anthropic_claude_official_or_compatible_api_de3f80'),hasLeading:true,startAction:()=>{this.logo('anthropic');},onAction:()=>{void this.controller?.openProvider('','anthropic');}});EtaPreferenceDivider()
    EtaArrowPreference({title:$r('app.string.eta_provider_gemini'),summary:$r('app.string.eta_port_unavailable'),isEnabled:false,hasLeading:true,startAction:()=>{this.logo('gemini');}})
  }}
  @Builder private title(p:ModelProfile){Column(){
    Text(p.name).fontSize(F.Headline1).fontColor($r('app.color.miuix_on_surface')).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis})
    Text(p.baseUrl).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis}).margin({top:S.Dp2})
    Text(this.type(p)+' · '+this.count($r('app.plural.provider_models_count'),p.models?.length||0)).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary')).margin({top:S.Dp6})
    if(!implementedProtocol(p.protocol)){Text($r('app.string.eta_port_unavailable')).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary'))}
    if(p.isEnabled===false){Text($r('app.string.ui_disabled_0fe5a9')).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary'))}
  }.alignItems(HorizontalAlign.Start)}
  @Builder private selected(p:ModelProfile){Button({type:ButtonType.Normal}){
    Image(p.id===this.state.selectedProviderId?$r('app.media.ic_rounded_check'):$r('app.media.ic_rounded_radio_button_unchecked')).width(S.Dp24).height(S.Dp24).fillColor(p.id===this.state.selectedProviderId?$r('app.color.miuix_primary'):$r('app.color.miuix_on_surface_variant_actions'))
  }.width(S.Dp44).height(S.Dp44).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent')).enabled(p.isEnabled!==false&&implementedProtocol(p.protocol)).accessibilityText(p.id===this.state.selectedProviderId?$r('app.string.provider_selected'):$r('app.string.provider_set_current')).onClick(()=>{void this.controller?.selectDefaultProvider(p.id);})}
  @Builder private list(){Column(){
    if(!this.state.providers.length){Text(this.state.providerSearch?$r('app.string.provider_no_matches'):$r('app.string.provider_empty')).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).padding({top:S.Dp24,bottom:S.Dp24})}
    ForEach(this.state.providers,(p:ModelProfile,index:number)=>{if(index>0){EtaPreferenceDivider()}
      EtaPreferenceRow({hasLeading:true,hasCustomTitle:true,hasActions:true,startAction:()=>{ProviderBrandIcon({source:resolveProviderSource(p.id,p.baseUrl,p.protocol)});},titleAction:()=>{this.title(p);},endActions:()=>{this.selected(p);},onAction:()=>{void this.controller?.openProvider(p.id);},onLongClick:()=>this.remove(p)}).opacity(p.isEnabled===false?P.DisabledAlpha:1)
    },(p:ModelProfile)=>p.id)
  }}
  @Builder private content(){Column(){
    TextInput({text:this.state.providerSearch,placeholder:$r('app.string.ui_search_provider_74e049')}).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface')).backgroundColor($r('app.color.miuix_secondary')).margin({left:S.Dp16,right:S.Dp16,top:S.Dp12,bottom:S.Dp8}).onChange((value:string)=>this.controller?.searchProviders(value))
    ProviderSection({title:$r('app.string.ui_add_new_provider_74df54'),content:() => { this.create(); }})
    ProviderSection({title:this.count($r('app.plural.provider_configured_count'),this.state.providers.length),content:() => { this.list(); }})
  }}
  build(){MiuixScaffoldPage({title:$r('app.string.ui_model_provider_e8c7f5'),blurEnabled:this.state.appearance.blurEnabled,progressiveBlur:this.state.appearance.topBarBlurStyle==='progressive',content:() => { this.content(); },onBack:()=>this.controller?.back()})}
}
