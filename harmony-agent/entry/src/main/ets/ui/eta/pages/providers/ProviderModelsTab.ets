// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/ProviderModelsTab.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { implementedProtocol } from '../../../../core/eta/protocol/foundationProviderBridge';
import { window } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { FoundationController, ShellState, emptyShellState } from '../../../../core/foundation/controller';
import { ProviderModelUi, contextWindowInputError, contextWindowPresets } from '../../../../core/eta/providerConfigDraft';
import { EtaComponentSizes as S, EtaProviderDefaults as P, MiuixFontSizes as F } from '../../../../core/eta/tokens';
import { ProviderSection, ProviderTextField } from './ProviderComponents';
import { EtaListDataSource } from '../../components/EtaListDataSource';
import { EtaArrowPreference } from '../../components/EtaPreferenceRow';
import { EtaPreferenceDivider } from '../../components/EtaPreferenceStyle';
import { EtaCheckboxPreference, EtaSwitchPreference } from '../../components/EtaSelectionPreferences';
import { EtaTextButton } from '../../components/EtaControls';
import { EtaDialogContent, MiuixDialogActions } from '../../components/EtaDialogs';
import { EtaChoiceChip } from '../../components/EtaSmallComponents';
import { EtaHaptics } from '../../../../platform/EtaHaptics';
import { EtaHaptic } from '../../../../core/eta/hapticFeedback';
@CustomDialog
struct ModelEditDialog {
  controller?:CustomDialogController;
  model:ProviderModelUi={id:'',modelId:'',displayName:'',sortOrder:0,enabled:true};
  @State private name:string='';@State private modelIdText:string='';@State private contextWindow:string='';@State private bodyHeight:number=P.ModelDialogHeight;@State private error:string='';
  onSubmit:(previous:string,id:string,name:string,context:string,vision:boolean)=>boolean=()=>false;
  aboutToAppear():void{this.name=this.model.displayName;this.modelIdText=this.model.id;this.contextWindow=this.model.contextWindow?String(this.model.contextWindow):'';
    const context=this.getUIContext().getHostContext() as common.UIAbilityContext;
    void window.getLastWindow(context).then(w=>{this.bodyHeight=Math.max(S.Dp48,Math.min(P.ModelDialogHeight,this.getUIContext().px2vp(w.getWindowProperties().windowRect.height)-P.ModelDialogChrome-S.Dp48));}).catch(()=>{});
  }
  @Builder private body(){Column(){
    Scroll(){Column({space:S.Dp12}){
      ProviderTextField({value:this.name,label:$r('app.string.ui_display_name_ed16be'),onChange:(v:string)=>{this.name=v;}})
      ProviderTextField({value:this.modelIdText,label:$r('app.string.eta_model_identifier'),onChange:(v:string)=>{this.modelIdText=v;}})
      ProviderTextField({value:this.contextWindow,label:$r('app.string.ui_context_length_tokens_227860'),onChange:(v:string)=>{this.contextWindow=v.replace(/\D/g,'');}})
      Flex({wrap:FlexWrap.Wrap}){ForEach(contextWindowPresets,(preset:number)=>{EtaChoiceChip({text:preset>=1000000?String(preset/1000000)+'M':String(preset/1000)+'K',selected:Number(this.contextWindow)===preset,onAction:()=>{this.contextWindow=String(preset);}}).margin({right:S.Dp8,bottom:S.Dp8})},(preset:number)=>String(preset))}.width('100%')
      Text(contextWindowInputError(this.contextWindow)?$r('app.string.provider_context_window_hint'):$r('app.string.provider_context_window_usage')).fontSize(F.Footnote2).fontColor($r('app.color.miuix_on_surface_variant_summary'))
      EtaSwitchPreference({title:$r('app.string.ui_support_thinking_5b9e4c'),summary:$r('app.string.eta_port_unavailable'),isEnabled:false})
      if(this.error){Text(this.error).fontColor($r('app.color.miuix_error')).fontSize(F.Body2)}
    }.width('100%')}.constraintSize({maxHeight:this.bodyHeight}).width('100%').scrollBar(BarState.Auto)
    MiuixDialogActions({confirmEnabled:!!this.modelIdText.trim()&&!contextWindowInputError(this.contextWindow),onCancel:():void=>{this.controller?.close();},onConfirm:()=>{if(this.onSubmit(this.model.id,this.modelIdText,this.name,this.contextWindow,!!this.model.vision))this.controller?.close();else this.error=this.getUIContext().getHostContext()!.resourceManager.getStringSync($r('app.string.provider_save_failed').id);}}).margin({top:S.Dp16})
  }}
  build(){EtaDialogContent({title:this.model.id?$r('app.string.page_edit_model_29e31e'):$r('app.string.page_add_model_532a64'),content:this.body}).margin(S.Dp24)}
}
@Component
export struct ProviderModelsTab {
  @Prop @Watch('refresh') state:ShellState=emptyShellState();controller:FoundationController|undefined=undefined;
  private source:EtaListDataSource<ProviderModelUi>=new EtaListDataSource<ProviderModelUi>();private entriesKey:string='';private dialog?:CustomDialogController;
  aboutToAppear():void{this.refresh();}aboutToDisappear():void{this.dialog?.close();}
  private refresh():void{const models=this.controller?.editor.models()||[];const key=JSON.stringify(models);if(key!==this.entriesKey){this.entriesKey=key;this.source.replace(models);}}
  private plural(resource:Resource,value:number):string{return this.getUIContext().getHostContext()!.resourceManager.getIntPluralStringValueSync(resource.id,value,value);}
  private edit(model:ProviderModelUi={id:'',modelId:'',displayName:'',sortOrder:0,enabled:true}):void{
    const providerId=this.state.editor.draft.id;this.dialog=new CustomDialogController({builder:ModelEditDialog({model,onSubmit:(old:string,id:string,name:string,context:string,vision:boolean):boolean=>providerId===this.state.editor.draft.id&&Boolean(this.controller?.editor.editModel(old,id,name,context,vision))}),customStyle:true});this.dialog.open();
  }
  private allModelsSelected():boolean{const ids=this.controller?.editor.models().map(m=>m.id)||[];return !!ids.length&&ids.every(id=>this.state.editor.modelSelection.includes(id));}
  private remove():void{const id=this.state.editor.draft.id;this.getUIContext().showAlertDialog({title:$r('app.string.ui_delete_3755f5'),message:this.plural($r('app.plural.provider_delete_models_action'),this.state.editor.modelSelection.length),primaryButton:{value:$r('app.string.action_cancel'),action:()=>{}},secondaryButton:{value:$r('app.string.ui_delete_3755f5'),action:()=>{if(id===this.state.editor.draft.id)this.controller?.editor.deleteSelectedModels();}}});}
  @Builder private actions(){Column(){
    EtaArrowPreference({title:this.state.editor.busy?$r('app.string.page_retrieving_a880c9'):$r('app.string.page_automatically_pull_from_remote_f883d0'),summary:$r('app.string.provider_models_endpoint_summary',this.state.editor.draft.baseUrl),isEnabled:!this.state.editor.busy&&implementedProtocol(this.state.editor.draft.protocol),onAction:()=>{void this.controller?.probeProvider(false);}})
    EtaArrowPreference({title:$r('app.string.ui_add_custom_model_a5ddc0'),summary:$r('app.string.ui_manually_fill_in_the_display_name_and_model_id_077a7b'),isEnabled:!this.state.editor.busy,onAction:()=>this.edit()})
  }}
  @Builder private row(model:ProviderModelUi){Column(){Row({space:S.Dp8}){
    Column(){Text(model.displayName).fontSize(F.Headline1).fontColor($r('app.color.miuix_on_surface')).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis})
      Text(model.modelId).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis})
      if(this.state.editor.draft.model===model.id){Text($r('app.string.ui_current_25e74d')).fontSize(F.Footnote2).fontColor($r('app.color.miuix_primary')).margin({top:S.Dp6})}
    }.layoutWeight(1).alignItems(HorizontalAlign.Start)
    if(this.state.editor.selectionMode){Checkbox().select(this.state.editor.modelSelection.includes(model.id)).onChange(()=>this.controller?.editor.toggleModelSelection(model.id))}
    else{
      Button({type:ButtonType.Normal}){Image($r('app.media.ic_rounded_tune')).width(S.Dp24).height(S.Dp24).fillColor($r('app.color.miuix_on_surface_variant_actions'))}.width(S.Dp44).height(S.Dp44).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent')).accessibilityText($r('app.string.ui_edit_model_parameters_ba4864')).onClick(()=>this.edit(model))
      Button({type:ButtonType.Normal}){Image(this.state.editor.draft.model===model.id?$r('app.media.ic_rounded_check'):$r('app.media.ic_rounded_radio_button_unchecked')).width(S.Dp24).height(S.Dp24).fillColor($r('app.color.miuix_primary'))}.width(S.Dp44).height(S.Dp44).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent')).accessibilityText($r('app.string.page_set_as_current_model_183d7d')).onClick(()=>this.controller?.editor.text('model',model.id))
    }
  }.padding({left:S.Dp16,right:S.Dp16,top:S.Dp12,bottom:S.Dp12}).width('100%').enabled(!this.state.editor.busy).onClick(()=>{if(this.state.editor.selectionMode)this.controller?.editor.toggleModelSelection(model.id);else this.controller?.editor.text('model',model.id);}).gesture(LongPressGesture().onAction(()=>{EtaHaptics.perform(EtaHaptic.LongPress);this.controller?.editor.beginModelSelection(model.id);}));EtaPreferenceDivider({hasLeading:false})
  }.width('100%').backgroundColor($r('app.color.miuix_surface_container'))}
  build(){Column(){
    if(this.state.editor.selectionMode){Row(){
      EtaTextButton({text:$r('app.string.action_cancel'),onAction:()=>this.controller?.editor.endModelSelection()})
      Text(this.plural($r('app.plural.provider_models_selected'),this.state.editor.modelSelection.length)).layoutWeight(1).fontSize(F.Headline1).fontColor($r('app.color.miuix_on_surface')).textAlign(TextAlign.Center)
      EtaTextButton({text:this.allModelsSelected()?$r('app.string.page_select_none_ba20eb'):$r('app.string.page_select_all_3e44b2'),onAction:()=>this.controller?.editor.toggleAllModels()})
    }.width('100%').padding({left:S.Dp24,right:S.Dp24})}
    List(){
      if(!this.state.editor.selectionMode){ListItem(){ProviderSection({title:$r('app.string.ui_model_management_183414'),content:this.actions})}}
      ListItem(){TextInput({text:this.state.editor.modelQuery,placeholder:$r('app.string.ui_search_model_df5586')}).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface')).backgroundColor($r('app.color.miuix_secondary')).margin({left:S.Dp16,right:S.Dp16,top:S.Dp4,bottom:S.Dp8}).onChange((v:string)=>this.controller?.editor.modelSearch(v))}
      if(this.state.editor.message||this.state.editor.error){ListItem(){Text(this.state.editor.error||this.state.editor.message).fontSize(F.Body2).fontColor(this.state.editor.error?$r('app.color.miuix_error'):$r('app.color.miuix_on_surface_variant_summary')).padding(S.Dp16)}}
      LazyForEach(this.source,(model:ProviderModelUi)=>{ListItem(){this.row(model)}.padding({left:S.Dp16,right:S.Dp16})},(model:ProviderModelUi)=>model.id)
    }.width('100%').layoutWeight(1).scrollBar(BarState.Auto).edgeEffect(EdgeEffect.Spring)
    Text($r('app.string.eta_provider_draft_models')).fontSize(F.Footnote2).fontColor($r('app.color.miuix_on_surface_variant_summary')).padding(S.Dp8)
    if(this.state.editor.selectionMode){EtaTextButton({text:this.plural($r('app.plural.provider_delete_models_action'),this.state.editor.modelSelection.length),destructive:true,isEnabled:this.state.editor.modelSelection.length>0&&!this.state.editor.busy,onAction:()=>this.remove()}).width('100%')}
    else{EtaTextButton({text:$r('app.string.page_save_configuration_817af1'),primary:true,isEnabled:!this.state.editor.busy,onAction:()=>{void this.controller?.saveProvider();}}).width('100%')}
  }.width('100%').height('100%')}
}
