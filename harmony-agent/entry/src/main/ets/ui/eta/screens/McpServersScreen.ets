// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/screens/mcp/McpServersScreen.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). Both list and detail use Eta group order and source-generated resources.
import {FoundationController,ShellState,emptyShellState} from '../../../core/foundation/controller';
import {McpServer,McpTool,activeMcpTools} from '../../../core/eta/mcp/mcpTypes';
import {McpModal} from '../../../core/eta/mcp/mcpUiStore';
import {EtaComponentSizes as S,MiuixFontSizes as F,MiuixIconButtonDefaults as I} from '../../../core/eta/tokens';
import {MiuixScaffoldPage} from '../components/MiuixScaffoldPage';
import {EtaPreferenceGroup,EtaPreferenceGroupTitle,EtaPreferenceDivider} from '../components/EtaPreferenceStyle';
import {EtaArrowPreference,EtaPreferenceRow} from '../components/EtaPreferenceRow';
import {EtaSwitchPreference} from '../components/EtaSelectionPreferences';
import {EtaTextButton} from '../components/EtaControls';
import {ListEmptyState} from '../components/EtaSmallComponents';
import {McpFormDialog,mcpNotice} from './McpDialogs';
@Component
export struct McpServersScreen {
 @Prop @Watch('sync') state:ShellState=emptyShellState();
 @Prop isDetail:boolean=false;
 controller:FoundationController|undefined=undefined;
 private dialog:CustomDialogController|undefined=undefined;
 private shown:McpModal='';private lastNotice:string='';
 aboutToAppear():void{this.sync();}
 aboutToDisappear():void{this.dialog?.close();this.dialog=undefined;this.shown='';}
 private server():McpServer|undefined{return this.state.mcp.servers.find(s=>s.id===this.state.mcp.selectedId);}
 private sync():void{
  // Navigation keeps the list destination mounted below detail; only the current route presents dialogs/toasts.
  if(this.isDetail?this.state.screen!=='mcpDetail':this.state.screen!=='mcp'){this.dialog?.close();this.dialog=undefined;this.shown='';return;}
  const modal=this.state.mcp.modal;
  if(this.shown!==modal){this.dialog?.close();this.dialog=undefined;this.shown=modal;if(modal){this.dialog=new CustomDialogController({builder:McpFormDialog({owner:this.controller,target:modal}),customStyle:true,cancel:()=>this.controller?.mcp?.dismiss()});this.dialog.open();}}
  const notice=this.state.mcp.notice;if(!modal&&notice&&notice!==this.lastNotice){this.getUIContext().getPromptAction().showToast({message:mcpNotice(notice,this.state.mcp.noticeCount)});this.controller?.mcp?.dismissNotice();}this.lastNotice=notice;
 }
 @Builder private icon(resource:Resource){Image(resource).width(S.Dp24).height(S.Dp24).fillColor($r('app.color.miuix_on_surface'))}
 @Builder private actions(){Button({type:ButtonType.Normal}){this.icon(this.isDetail?$r('app.media.ic_rounded_refresh'):$r('app.media.ic_rounded_add'))}
  .width(I.MinWidth).height(I.MinHeight).padding(S.Dp0).borderRadius(I.CornerRadius).backgroundColor($r('app.color.eta_transparent'))
  .enabled(!this.state.mcp.loading&&!this.state.mcp.working&&(!this.isDetail||!!this.server()))
  .accessibilityText(this.isDetail?$r('app.string.mcp_refresh_tools'):$r('app.string.mcp_add_server'))
  .onClick(()=>{if(this.isDetail)void this.controller?.mcp?.refreshTools();else this.controller?.mcp?.show('add');})}
 @Builder private addButton(){EtaTextButton({text:$r('app.string.mcp_add_server'),onAction:()=>this.controller?.mcp?.show('add')})}
 @Builder private emptyIcon(){this.icon($r('app.media.ic_rounded_hub'))}
 @Builder private refreshIcon(){this.icon($r('app.media.ic_rounded_refresh'))}
 @Builder private list(){Column(){
  if(this.state.mcp.servers.length===0){ListEmptyState({title:$r('app.string.mcp_empty_title'),summary:$r('app.string.mcp_empty_summary'),hasIcon:true,icon:() => { this.emptyIcon(); },hasAction:true,action:() => { this.addButton(); }})}
  else{ForEach(this.state.mcp.servers,(server:McpServer,index:number)=>{if(index>0){EtaPreferenceDivider({hasLeading:false})}EtaArrowPreference({title:server.name,summary:$r('app.string.mcp_server_row_summary',activeMcpTools(server).length,server.tools.length),onAction:()=>this.controller?.openMcp(server.id)})},(server:McpServer)=>server.id)}
 }.width('100%')}
 @Builder private serverSettings(server:McpServer){Column(){
  EtaSwitchPreference({title:$r('app.string.mcp_enable_server'),summary:server.url,checked:server.enabled,isEnabled:!this.state.mcp.working,onCheckedChange:(value:boolean)=>{void this.controller?.mcp?.setEnabled(value);}})
  EtaPreferenceDivider({hasLeading:false})
  EtaArrowPreference({title:$r('app.string.mcp_update_token'),summary:server.authorizationType==='bearer'?$r('app.string.mcp_token_configured'):$r('app.string.mcp_no_authentication'),isEnabled:!this.state.mcp.working,onAction:()=>this.controller?.mcp?.show('token')})
 }}
 private toolSummary(tool:McpTool):ResourceStr{return tool.description.trim()?tool.description:tool.readOnlyHint===true?$r('app.string.mcp_read_only_tool'):$r('app.string.mcp_may_modify_data');}
 @Builder private tools(server:McpServer){Column(){
  if(server.tools.length===0){ListEmptyState({title:$r('app.string.mcp_no_tools'),summary:$r('app.string.mcp_refresh_tools_hint'),hasIcon:true,icon:() => { this.refreshIcon(); }})}
  else{ForEach(server.tools,(tool:McpTool,index:number)=>{if(index>0){EtaPreferenceDivider({hasLeading:false})}EtaSwitchPreference({title:tool.title.trim()?tool.title:tool.name,summary:this.toolSummary(tool),checked:server.enabledToolNames.includes(tool.name),isEnabled:!this.state.mcp.working,onCheckedChange:(value:boolean)=>{void this.controller?.mcp?.setTool(tool.name,value);}})},(tool:McpTool,index:number)=>String(index)+':'+tool.name)}
 }}
 @Builder private deleteTitle(){Column(){Text($r('app.string.mcp_delete_server')).fontSize(F.Body1).fontWeight(FontWeight.Medium).fontColor($r('app.color.miuix_error'));Text($r('app.string.mcp_delete_server_summary')).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary'))}.alignItems(HorizontalAlign.Start).width('100%')}
 @Builder private deleteRow(){EtaPreferenceRow({hasCustomTitle:true,titleAction:() => { this.deleteTitle(); },isEnabled:!this.state.mcp.working,onAction:()=>this.controller?.mcp?.show('delete')})}
 @Builder private detail(server:McpServer){Column(){
  EtaPreferenceGroupTitle({title:$r('app.string.mcp_server_settings')});EtaPreferenceGroup({content:()=>{this.serverSettings(server);}})
  EtaPreferenceGroupTitle({title:$r('app.string.mcp_tools_count',server.tools.length)});EtaPreferenceGroup({content:()=>{this.tools(server);}})
  EtaPreferenceGroup({content:() => { this.deleteRow(); }}).margin({top:S.Dp8})
 }.width('100%')}
 @Builder private body(){Column(){
  Text($r('app.string.eta_extension_saved_not_in_use')).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary')).width('100%').padding(S.Dp16)
  if(this.state.mcp.loading){LoadingProgress().width(S.Dp24).height(S.Dp24).color($r('app.color.miuix_primary'))}
  else if(this.isDetail){if(this.server()){this.detail(this.server()!)}else{ListEmptyState({title:$r('app.string.mcp_server_missing')})}}
  else{EtaPreferenceGroupTitle({title:$r('app.string.mcp_configured_servers',this.state.mcp.servers.length)});EtaPreferenceGroup({content:() => { this.list(); }})}
 }.width('100%')}
 build(){MiuixScaffoldPage({title:this.isDetail?(this.server()?.name||$r('app.string.route_mcp_server_detail')):$r('app.string.route_mcp_servers'),blurEnabled:this.state.appearance.blurEnabled,progressiveBlur:this.state.appearance.topBarBlurStyle==='progressive',actions:() => { this.actions(); },content:() => { this.body(); },onBack:()=>this.controller?.back()})}
}
