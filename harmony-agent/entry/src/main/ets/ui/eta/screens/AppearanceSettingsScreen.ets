// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/AppearanceSettingsScreen.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController, ShellState, emptyShellState } from '../../../core/foundation/controller';
import { AppearanceThemeMode, AppearanceTopBarBlurStyle } from '../../../core/eta/appearanceSettings';
import { EtaAppearanceDefaults as A, EtaComponentSizes as S, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { MiuixScaffoldPage } from '../components/MiuixScaffoldPage';
import { EtaPreferenceGroup, EtaPreferenceGroupTitle, EtaPreferenceDivider } from '../components/EtaPreferenceStyle';
import { EtaArrowPreference } from '../components/EtaPreferenceRow';
import { EtaSwitchPreference } from '../components/EtaSelectionPreferences';
import { EtaOverlayDropdownPreference, EtaDropdownItem } from '../components/EtaDropdownPreference';
import { EtaDialogContent, MiuixDialogActions } from '../components/EtaDialogs';
import { EtaHaptics } from '../../../platform/EtaHaptics';
import { EtaHaptic } from '../../../core/eta/hapticFeedback';

@CustomDialog
struct InterfaceScaleDialog {
  controller?:CustomDialogController;
  @Prop initial:number=A.DEFAULT_INTERFACE_SCALE*A.Percent;
  @State private input:string='';
  onConfirm:(value:number)=>void=()=>{};
  aboutToAppear():void{this.input=String(Math.round(this.initial));}
  private valid():boolean{return /^\d+$/.test(this.input)&&Number(this.input)>=A.MIN_INTERFACE_SCALE*A.Percent&&Number(this.input)<=A.MAX_INTERFACE_SCALE*A.Percent;}
  @Builder private body(){Column({space:S.Dp16}){
    TextInput({text:this.input,placeholder:$r('app.string.appearance_interface_scale_input_label')})
      .type(InputType.Number).fontColor($r('app.color.miuix_on_background')).backgroundColor($r('app.color.miuix_secondary'))
      .onChange((value:string)=>{this.input=value.replace(/\D/g,'').slice(0,3);})
    MiuixDialogActions({confirmEnabled:this.valid(),onCancel:():void=>{this.controller?.close();},onConfirm:()=>{if(this.valid()){this.onConfirm(Number(this.input));this.controller?.close();}}})
  }}
  build(){EtaDialogContent({title:$r('app.string.appearance_interface_scale_dialog_title'),summary:$r('app.string.appearance_interface_scale_dialog_summary'),content:() => { this.body(); }}).margin(S.Dp24)}
}
@Component
export struct AppearanceSettingsScreen {
  @Prop @Watch('refreshScale') state:ShellState=emptyShellState();
  controller:FoundationController|undefined=undefined;
  // Ephemeral drag preview only; the selected value belongs to the repository.
  @State private scaleDraft:number=A.DEFAULT_INTERFACE_SCALE*A.Percent;
  private dragging:boolean=false;
  private scaleDialog?:CustomDialogController;
  aboutToAppear():void{this.refreshScale();}
  aboutToDisappear():void{this.scaleDialog?.close();}
  private refreshScale():void{if(!this.dragging)this.scaleDraft=this.state.appearance.interfaceScale*A.Percent;}
  private commitScale(percent:number):void{this.dragging=false;void this.controller?.updateAppearance({interfaceScale:Math.round(percent)/A.Percent});}
  private showScale():void{
    this.scaleDialog=new CustomDialogController({builder:InterfaceScaleDialog({initial:this.scaleDraft,onConfirm:(percent:number)=>this.commitScale(percent)}),customStyle:true});this.scaleDialog.open();
  }
  private themes():EtaDropdownItem[]{return [new EtaDropdownItem('system',$r('app.string.appearance_theme_system')),new EtaDropdownItem('light',$r('app.string.appearance_theme_light')),new EtaDropdownItem('dark',$r('app.string.appearance_theme_dark'))];}
  @Builder private colorGroup(){
    EtaOverlayDropdownPreference({title:$r('app.string.appearance_theme_mode'),items:this.themes(),selectedIndex:['system','light','dark'].indexOf(this.state.appearance.themeMode),onSelectedIndexChange:(index:number)=>{void this.controller?.updateAppearance({themeMode:['system','light','dark'][index] as AppearanceThemeMode});}})
    // Monet and its dependent palette/accent/pure-black controls are C-class.
  }
  @Builder private pending(title:Resource){EtaSwitchPreference({title,summary:$r('app.string.eta_port_unavailable'),checked:false,isEnabled:false})}
  @Builder private scaleLabel(){Text(Math.round(this.scaleDraft)+'%').fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_actions'))}
  @Builder private slider(){
    Slider({value:this.scaleDraft,min:A.MIN_INTERFACE_SCALE*A.Percent,max:A.MAX_INTERFACE_SCALE*A.Percent,step:1,style:SliderStyle.OutSet})
      .width('100%').selectedColor($r('app.color.eta_settings_primary')).showTips(true)
      .onChange((value:number,mode:SliderChangeMode)=>{
        if(mode===SliderChangeMode.Begin||mode===SliderChangeMode.Moving){this.dragging=true;this.scaleDraft=value;}
        if(mode===SliderChangeMode.End||mode===SliderChangeMode.Click){this.scaleDraft=value;EtaHaptics.perform(EtaHaptic.TextHandleMove);this.commitScale(value);}
      })
  }
  @Builder private interfaceGroup(){Column(){
    this.pending($r('app.string.appearance_runtime_glow'));EtaPreferenceDivider({hasLeading:false})
    this.pending($r('app.string.appearance_task_completion_alerts'));EtaPreferenceDivider({hasLeading:false})
    EtaSwitchPreference({title:$r('app.string.appearance_blur'),summary:$r('app.string.appearance_blur_summary'),checked:this.state.appearance.blurEnabled,onCheckedChange:(enabled:boolean)=>{void this.controller?.updateAppearance({blurEnabled:enabled});}})
    if(this.state.appearance.blurEnabled){
      EtaPreferenceDivider({hasLeading:false})
      EtaOverlayDropdownPreference({title:$r('app.string.appearance_blur_style'),summary:this.state.appearance.topBarBlurStyle==='progressive'?$r('app.string.appearance_blur_style_progressive_summary'):$r('app.string.appearance_blur_style_gaussian_summary'),items:[new EtaDropdownItem('gaussian',$r('app.string.appearance_blur_style_gaussian')),new EtaDropdownItem('progressive',$r('app.string.appearance_blur_style_progressive'))],selectedIndex:this.state.appearance.topBarBlurStyle==='progressive'?1:0,onSelectedIndexChange:(index:number)=>{void this.controller?.updateAppearance({topBarBlurStyle:['gaussian','progressive'][index] as AppearanceTopBarBlurStyle});}})
    }
    EtaPreferenceDivider({hasLeading:false});this.pending($r('app.string.appearance_swipe_dismiss'))
    EtaPreferenceDivider({hasLeading:false});this.pending($r('app.string.appearance_predictive_back'))
    EtaPreferenceDivider({hasLeading:false})
    EtaArrowPreference({title:$r('app.string.appearance_interface_scale'),summary:$r('app.string.appearance_interface_scale_summary'),hasBottom:true,endActions:() => { this.scaleLabel(); },bottomAction:() => { this.slider(); },onAction:()=>this.showScale()})
  }}
  @Builder private content(){Column(){
    EtaPreferenceGroupTitle({title:$r('app.string.appearance_group_color')});EtaPreferenceGroup({content:() => { this.colorGroup(); }})
    EtaPreferenceGroupTitle({title:$r('app.string.appearance_group_interface')});EtaPreferenceGroup({content:() => { this.interfaceGroup(); }})
  }}
  build(){MiuixScaffoldPage({title:$r('app.string.appearance_title'),blurEnabled:this.state.appearance.blurEnabled,progressiveBlur:this.state.appearance.topBarBlurStyle==='progressive',content:() => { this.content(); },onBack:()=>this.controller?.back()})}
}
