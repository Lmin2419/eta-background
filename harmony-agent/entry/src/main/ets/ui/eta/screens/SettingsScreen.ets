// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/SettingsScreen.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { common } from '@kit.AbilityKit';
import { FoundationController, ShellState, emptyShellState } from '../../../core/foundation/controller';
import { EtaComponentSizes as S } from '../../../core/eta/tokens';
import { MiuixScaffoldPage } from '../components/MiuixScaffoldPage';
import { EtaArrowPreference } from '../components/EtaPreferenceRow';
import { EtaSwitchPreference } from '../components/EtaSelectionPreferences';
import { EtaPreferenceGroup, EtaPreferenceGroupTitle, EtaPreferenceIcon, EtaPreferenceDivider } from '../components/EtaPreferenceStyle';
import { EtaDropdownPreference, EtaDropdownItem } from '../components/EtaDropdownPreference';
import { ItemDescriptionDialog } from '../components/EtaDialogs';
import { openEtaLink } from '../../../platform/EtaTextActions';
@Component
export struct SettingsScreen {
  @Prop state:ShellState=emptyShellState();
  controller:FoundationController|undefined=undefined;
  private about:CustomDialogController=new CustomDialogController({builder:ItemDescriptionDialog({title:$r('app.string.eta_port_about_title'),description:$r('app.string.eta_about_details')}),customStyle:true});
  private link(url:string):void{const context=this.getUIContext().getHostContext() as common.UIAbilityContext;void openEtaLink(context,url).catch(()=>{});}
  @Builder private icon(source:Resource,color:ResourceColor,enabled:boolean=true){EtaPreferenceIcon({icon:source,color,isEnabled:enabled})}
  @Builder private row(title:Resource,source:Resource,color:ResourceColor,enabled:boolean,action:()=>void,summary:ResourceStr=''){
    EtaArrowPreference({title,summary:enabled?summary:$r('app.string.eta_port_unavailable'),isEnabled:enabled,hasLeading:true,startAction:()=>{this.icon(source,color,enabled);},onAction:action})
  }
  @Builder private modelProviderRow(){
    EtaArrowPreference({title:$r('app.string.ui_model_provider_e8c7f5'),summary:this.state.chat.provider?this.state.chat.provider+' / '+(this.state.chat.model||''):$r('app.string.settings_not_configured'),isEnabled:true,hasLeading:true,startAction:()=>{this.icon($r('app.media.ic_rounded_cloud'),$r('app.color.eta_preference_blue'),true);},onAction:()=>this.controller?.navigate('providers')})
  }
  @Builder private pendingSwitch(title:Resource,source:Resource,color:ResourceColor){EtaSwitchPreference({title,summary:$r('app.string.eta_port_unavailable'),isEnabled:false,hasLeading:true,startAction:()=>{this.icon(source,color,false);}})}
  @Builder private agent(){Column(){
    this.modelProviderRow()
    EtaPreferenceDivider()
    EtaSwitchPreference({title:$r('app.string.ui_deep_thinking_enabled_by_default_c032d6'),checked:this.state.runtimePreferences.thinkingEnabled,isEnabled:!!this.controller?.ports.runtimePreferences,hasLeading:true,startAction:()=>{this.icon($r('app.media.ic_rounded_psychology'),$r('app.color.eta_preference_blue'));},onCheckedChange:(value:boolean)=>{void this.controller?.updateRuntimePreferences({thinkingEnabled:value});}})
  }}
  @Builder private extensions(){Column(){
    EtaSwitchPreference({title:$r('app.string.settings_auto_compaction'),checked:this.state.runtimePreferences.autoCompactionEnabled,isEnabled:!!this.controller?.ports.runtimePreferences,hasLeading:true,startAction:()=>{this.icon($r('app.media.ic_rounded_layers'),$r('app.color.eta_preference_blue'));},onCheckedChange:(value:boolean)=>{void this.controller?.updateRuntimePreferences({autoCompactionEnabled:value});}});EtaPreferenceDivider()
    this.row($r('app.string.ui_memory_b55ff5'),$r('app.media.ic_rounded_menu_book'),$r('app.color.eta_preference_orange'),!!this.controller?.memory,()=>this.controller?.openMemory());EtaPreferenceDivider()
    this.row($r('app.string.eta_route_subagents'),$r('app.media.ic_rounded_hub'),$r('app.color.eta_preference_blue'),false,()=>{});EtaPreferenceDivider()
    this.row($r('app.string.route_skills'),$r('app.media.ic_rounded_import_contacts'),$r('app.color.eta_preference_green'),false,()=>{});EtaPreferenceDivider()
    this.row($r('app.string.route_mcp_servers'),$r('app.media.ic_rounded_account_tree'),$r('app.color.eta_preference_blue'),!!this.controller?.mcp,()=>this.controller?.openMcp());EtaPreferenceDivider()
    this.row($r('app.string.eta_route_characters'),$r('app.media.ic_rounded_sports_bar'),$r('app.color.eta_preference_orange'),false,()=>{})
  }}
  @Builder private tools(){Column(){
    this.row($r('app.string.settings_tools_list'),$r('app.media.ic_rounded_dashboard'),$r('app.color.eta_preference_green'),false,()=>{});EtaPreferenceDivider()
    this.pendingSwitch($r('app.string.ui_enable_web_browsing_tools_8b6b03'),$r('app.media.ic_rounded_language'),$r('app.color.eta_preference_blue'));EtaPreferenceDivider()
    this.row($r('app.string.web_tools_settings_title'),$r('app.media.ic_rounded_travel_explore'),$r('app.color.eta_preference_blue'),false,()=>{});EtaPreferenceDivider()
    this.row($r('app.string.phone_ops_title'),$r('app.media.ic_rounded_smartphone'),$r('app.color.eta_preference_green'),true,()=>this.controller?.openPhoneOps(),$r('app.string.phone_ops_summary'));EtaPreferenceDivider()
    this.pendingSwitch($r('app.string.ui_enable_device_direct_tools_e2d595'),$r('app.media.ic_rounded_smartphone'),$r('app.color.eta_preference_green'));EtaPreferenceDivider()
    this.pendingSwitch($r('app.string.ui_allow_reading_of_sensitive_device_information_feaec0'),$r('app.media.ic_rounded_visibility'),$r('app.color.eta_preference_blue'));EtaPreferenceDivider()
    this.pendingSwitch($r('app.string.ui_allow_sensitive_device_operation_3d42ea'),$r('app.media.ic_rounded_gpp_maybe'),$r('app.color.eta_preference_orange'));EtaPreferenceDivider()
    this.pendingSwitch($r('app.string.ui_enable_terminal_file_tools_18bb43'),$r('app.media.ic_rounded_terminal'),$r('app.color.eta_preference_green'))
  }}
  @Builder private general(){Column(){
    this.row($r('app.string.appearance_title'),$r('app.media.ic_rounded_palette'),$r('app.color.eta_preference_orange'),true,()=>this.controller?.openAppearance());EtaPreferenceDivider()
    this.row($r('app.string.speech_settings_title'),$r('app.media.ic_rounded_mic'),$r('app.color.eta_preference_blue'),false,()=>{});EtaPreferenceDivider()
    EtaDropdownPreference({title:$r('app.string.settings_language'),summary:$r('app.string.eta_language_restart'),hasLeading:true,startAction:()=>{this.icon($r('app.media.ic_rounded_language'),$r('app.color.eta_preference_blue'));},
      items:[new EtaDropdownItem('default',$r('app.string.settings_language_system')),new EtaDropdownItem('en',$r('app.string.eta_language_english')),new EtaDropdownItem('zh-Hans',$r('app.string.eta_language_hans')),new EtaDropdownItem('zh-Hant',$r('app.string.eta_language_hant'))],selectedIndex:['default','en','zh-Hans','zh-Hant'].indexOf(this.state.language),onSelectedIndexChange:(index:number)=>{void this.controller?.setLanguage(['default','en','zh-Hans','zh-Hant'][index]);}})
    EtaPreferenceDivider()
    this.row($r('app.string.data_backup_title'),$r('app.media.ic_rounded_description'),$r('app.color.eta_preference_blue'),false,()=>{})
  }}
  @Builder private permissions(){this.row($r('app.string.eta_wireless_debugging'),$r('app.media.ic_rounded_smartphone'),$r('app.color.eta_preference_blue'),false,()=>{})}
  @Builder private aboutGroup(){Column(){
    this.row($r('app.string.background_notifications_title'),$r('app.media.ic_rounded_info'),$r('app.color.eta_preference_blue'),false,()=>{});EtaPreferenceDivider()
    this.row($r('app.string.background_diagnostics_title'),$r('app.media.ic_rounded_bug_report'),$r('app.color.eta_preference_orange'),true,()=>this.controller?.openDiagnostics(),$r('app.string.background_diagnostics_summary'));EtaPreferenceDivider()
    this.row($r('app.string.ui_about_version_title'),$r('app.media.ic_rounded_info'),$r('app.color.eta_preference_blue'),true,():void=>{this.about.open();},this.state.appVersion);EtaPreferenceDivider()
    this.row($r('app.string.ui_about_feedback_title'),$r('app.media.ic_rounded_bug_report'),$r('app.color.eta_preference_orange'),true,()=>this.link('https://github.com/Lmin2419/eta-background/issues'));EtaPreferenceDivider()
    this.row($r('app.string.ui_about_github_star_title'),$r('app.media.ic_rounded_code'),$r('app.color.eta_preference_blue'),true,()=>this.link('https://github.com/Lmin2419/eta-background'),$r('app.string.ui_about_github_star_hint'))
  }}
  @Builder private content(){Column(){
    EtaPreferenceGroupTitle({title:$r('app.string.settings_llm_providers')});EtaPreferenceGroup({content:() => { this.agent(); }})
    EtaPreferenceGroupTitle({title:$r('app.string.settings_context_extensions')});EtaPreferenceGroup({content:() => { this.extensions(); }})
    EtaPreferenceGroupTitle({title:$r('app.string.ui_tool_a72ef1')});EtaPreferenceGroup({content:() => { this.tools(); }})
    EtaPreferenceGroupTitle({title:$r('app.string.settings_general')});EtaPreferenceGroup({content:() => { this.general(); }})
    EtaPreferenceGroupTitle({title:$r('app.string.ui_permissions_560165')});EtaPreferenceGroup({content:() => { this.permissions(); }})
    EtaPreferenceGroupTitle({title:$r('app.string.ui_about_bed172')});EtaPreferenceGroup({content:() => { this.aboutGroup(); }})
  }}
  build(){MiuixScaffoldPage({title:$r('app.string.ui_set_up_7debf9'),blurEnabled:this.state.appearance.blurEnabled,progressiveBlur:this.state.appearance.topBarBlurStyle==='progressive',content:() => { this.content(); },onBack:()=>this.controller?.back()})}
}
