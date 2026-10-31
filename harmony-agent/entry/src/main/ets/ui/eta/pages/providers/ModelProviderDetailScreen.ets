// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ModelProviderDetailScreen.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController, ShellState, emptyShellState } from '../../../../core/foundation/controller';
import { EtaComponentSizes as S, MiuixFontSizes as F } from '../../../../core/eta/tokens';
import { ProviderSection, ProviderPage } from './ProviderComponents';
import { ProviderConnectionFields } from './ProviderConnectionFields';
import { ProviderHeadersEditor } from './ProviderHeadersEditor';
import { ProviderModelsTab } from './ProviderModelsTab';
import { EtaPreferenceRow } from '../../components/EtaPreferenceRow';
import { EtaPreferenceDivider } from '../../components/EtaPreferenceStyle';
import { EtaSwitchPreference } from '../../components/EtaSelectionPreferences';
import { EtaTextButton } from '../../components/EtaControls';
import { implementedProtocol } from '../../../../core/eta/protocol/foundationProviderBridge';
@Component
export struct ModelProviderDetailScreen {
  @Prop state:ShellState=emptyShellState();controller:FoundationController|undefined=undefined;
  private remove():void{const id=this.state.editor.draft.id;this.getUIContext().showAlertDialog({title:$r('app.string.ui_remove_provider_9f848f'),message:$r('app.string.provider_delete_summary',this.state.editor.draft.name),primaryButton:{value:$r('app.string.action_cancel'),action:()=>{}},secondaryButton:{value:$r('app.string.ui_delete_3755f5'),action:()=>{if(id===this.state.editor.draft.id)void this.controller?.deleteProvider();}}});}
  @Builder private connection(){Column(){
    ProviderConnectionFields({state:this.state,controller:this.controller});EtaPreferenceDivider({hasLeading:false})
    EtaPreferenceRow({title:$r('app.string.ui_test_connection_10b7d8'),summary:this.state.editor.connection,isEnabled:!this.state.editor.busy&&implementedProtocol(this.state.editor.draft.protocol),onAction:()=>{void this.controller?.probeProvider(true);}})
  }}
  @Builder private preferences(){Column(){
    EtaSwitchPreference({title:$r('app.string.ui_enable_this_provider_683a76'),checked:this.state.editor.draft.isEnabled!==false,isEnabled:!this.state.editor.busy,onCheckedChange:(v:boolean)=>this.controller?.editor.flag('isEnabled',v)});EtaPreferenceDivider({hasLeading:false})
    Column(){
      TextArea({text:this.state.editor.draft.systemPrompt||'',placeholder:$r('app.string.ui_system_prompt_word_193981')}).height(S.Dp120).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface')).backgroundColor($r('app.color.miuix_secondary')).enabled(!this.state.editor.busy).onChange((v:string)=>this.controller?.editor.text('systemPrompt',v))
      Text($r('app.string.ui_leave_blank_to_use_the_default_mobile_agent_prompt_w_21e7c8')).fontSize(F.Footnote2).fontColor($r('app.color.miuix_on_surface_variant_summary')).margin({top:S.Dp8})
    }.padding(S.Dp16)
    EtaPreferenceDivider({hasLeading:false})
    EtaSwitchPreference({title:$r('app.string.eta_provider_stream'),checked:this.state.editor.draft.protocol==='responses'||this.state.editor.draft.streaming,isEnabled:!this.state.editor.busy&&this.state.editor.draft.protocol!=='responses',onCheckedChange:(v:boolean)=>this.controller?.editor.flag('streaming',v)})
  }}
  @Builder private status(){
    if(this.state.editor.error){Text(this.state.editor.error).fontSize(F.Footnote1).fontColor($r('app.color.miuix_error')).width('100%').padding(S.Dp16)}
    if(this.state.editor.message){Text(this.state.editor.message).fontSize(F.Footnote1).fontColor($r('app.color.eta_status_success')).width('100%').padding(S.Dp16)}
  }
  @Builder private config(){Scroll(){Column(){
    ProviderSection({title:$r('app.string.ui_connection_configuration_7d057b'),content:this.connection})
    ProviderHeadersEditor({state:this.state,controller:this.controller})
    ProviderSection({title:$r('app.string.ui_preferences_and_strategies_2abd3c'),content:this.preferences})
    EtaTextButton({text:this.state.editor.busy?$r('app.string.page_saving_d70d42'):this.state.editor.draft.id?$r('app.string.page_save_configuration_817af1'):$r('app.string.page_create_fcbd09'),primary:true,isEnabled:!this.state.editor.busy,onAction:()=>{void this.controller?.saveProvider();}}).width('100%').padding({left:S.Dp16,right:S.Dp16,top:S.Dp12})
    this.status()
    if(this.state.editor.draft.isBuiltIn){EtaTextButton({text:$r('app.string.page_reset_built_in_configuration_35b6ec'),isEnabled:!this.state.editor.busy,onAction:()=>{void this.controller?.editor.resetBuiltIn();}}).width('100%').padding({left:S.Dp16,right:S.Dp16,top:S.Dp12})}
    else if(this.state.editor.draft.id){EtaTextButton({text:$r('app.string.page_remove_provider_9f848f'),destructive:true,isEnabled:!this.state.editor.busy,onAction:()=>this.remove()}).width('100%').padding({left:S.Dp16,right:S.Dp16,top:S.Dp12})}
    Blank().height(S.Dp24)
  }.width('100%')}.height('100%').width('100%').scrollBar(BarState.Auto).edgeEffect(EdgeEffect.Spring)}
  @Builder private body(){Column(){
    if(this.state.editor.draft.id){Row({space:S.Dp4}){
      EtaTextButton({text:$r('app.string.page_configuration_d7d7ce'),primary:this.state.editor.tab==='config',onAction:()=>this.controller?.editor.tab('config')}).layoutWeight(1)
      EtaTextButton({text:$r('app.string.page_model_98fd0c'),primary:this.state.editor.tab==='models',onAction:()=>this.controller?.editor.tab('models')}).layoutWeight(1)
    }.width('100%').padding({left:S.Dp16,right:S.Dp16,top:S.Dp8,bottom:S.Dp8})}
    Column(){if(this.state.editor.tab==='models'&&this.state.editor.draft.id){ProviderModelsTab({state:this.state,controller:this.controller})}else{this.config()}}.width('100%').layoutWeight(1)
  }.width('100%').height('100%')}
  build(){ProviderPage({title:this.state.editor.draft.id?this.state.editor.draft.name:$r('app.string.page_create_new_provider_36cab9'),blurEnabled:this.state.appearance.blurEnabled,content:this.body,onBack:()=>this.controller?.back()})}
}
