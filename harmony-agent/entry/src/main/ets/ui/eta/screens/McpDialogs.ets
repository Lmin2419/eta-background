// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/screens/mcp/McpServersScreen.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). Native dialog lifecycle; state is a projection of McpUiStore, never a second draft.
import {FoundationController} from '../../../core/foundation/controller';
import {McpUiState,McpModal,McpNotice,emptyMcpState} from '../../../core/eta/mcp/mcpUiStore';
import {EtaComponentSizes as S,MiuixFontSizes as F,EtaControlDefaults as C} from '../../../core/eta/tokens';
import {EtaDialogContent,MiuixDialogActions} from '../components/EtaDialogs';
export function mcpNotice(notice:McpNotice,count:number):Resource{
 switch(notice){
  case 'added':return $r('app.string.mcp_server_added',count);
  case 'refreshed':return $r('app.string.mcp_refreshed',count);
  case 'refresh_failed':return $r('app.string.mcp_refresh_failed');
  case 'connection_failed':return $r('app.string.mcp_connection_failed');
  case 'name_required':return $r('app.string.mcp_name_required');
  case 'invalid_url':return $r('app.string.eta_mcp_invalid_url');
  case 'token_invalid':return $r('app.string.eta_mcp_token_invalid');
  default:return $r('app.string.eta_mcp_save_failed');
 }
}
@CustomDialog
export struct McpFormDialog {
 controller?:CustomDialogController;
 owner:FoundationController|undefined=undefined;
 target:McpModal='';
 @State private view:McpUiState=emptyMcpState();
 private timer:number=-1;
 aboutToAppear():void{this.sync();this.timer=setInterval(()=>this.sync(),32);}
 aboutToDisappear():void{clearInterval(this.timer);}
 private sync():void{this.view=this.owner?.mcp?.snapshot()||emptyMcpState();if(this.view.modal!==this.target)this.controller?.close();}
 private title():Resource{switch(this.target){case 'add':return $r('app.string.mcp_add_server');case 'token':return $r('app.string.mcp_update_token');case 'risk':return $r('app.string.mcp_enable_risky_tool');default:return $r('app.string.mcp_delete_server');}}
 private summary():ResourceStr{const server=this.view.servers.find(s=>s.id===this.view.selectedId);switch(this.target){case 'token':return $r('app.string.mcp_update_token_summary');case 'risk':return $r('app.string.mcp_enable_risky_tool_summary',this.view.riskyToolName,server?.name||'');case 'delete':return $r('app.string.mcp_delete_confirm',server?.name||'');default:return '';}}
 private confirmText():Resource{switch(this.target){case 'add':return this.view.working?$r('app.string.mcp_connecting'):$r('app.string.mcp_add_server');case 'token':return $r('app.string.action_save');case 'risk':return $r('app.string.mcp_enable_tool');default:return $r('app.string.ui_delete_3755f5');}}
 private confirm():void{switch(this.target){case 'add':void this.owner?.mcp?.add();break;case 'token':void this.owner?.mcp?.saveToken();break;case 'risk':void this.owner?.mcp?.confirmRisk();break;case 'delete':void this.owner?.deleteMcp();break;}this.sync();}
 @Builder private field(label:Resource,kind:'name'|'url'|'token'){
  Column({space:S.Dp4}){
   Text(label).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary')).width('100%')
   TextInput({text:kind==='name'?this.view.draft.name:kind==='url'?this.view.draft.url:this.view.draft.token}).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface')).backgroundColor($r('app.color.miuix_surface_container_high'))
    .type(kind==='token'?InputType.Password:kind==='url'?InputType.URL:InputType.Normal).enabled(!this.view.working).showPasswordIcon(false)
    .onChange((text:string)=>{this.owner?.mcp?.draft(kind,text);this.sync();})
  }.width('100%')
 }
 @Builder private fields(){Column({space:S.Dp12}){
  if(this.target==='add'){
   this.field($r('app.string.mcp_server_name'),'name')
   this.field($r('app.string.mcp_server_url'),'url')
   this.field($r('app.string.mcp_bearer_optional'),'token')
  }else if(this.target==='token'){this.field($r('app.string.mcp_bearer_token'),'token')}
  if(this.view.notice){Text(mcpNotice(this.view.notice,this.view.noticeCount)).fontSize(F.Body2).fontColor($r('app.color.miuix_error')).width('100%')}
 }.width('100%')}
 @Builder private body(){Column(){
  Scroll(){this.fields()}.constraintSize({maxHeight:S.Dp520-C.ButtonMinHeight-S.Dp16}).scrollBar(BarState.Auto).width('100%')
  MiuixDialogActions({confirmText:this.confirmText(),confirmEnabled:!this.view.working,destructive:this.target==='risk'||this.target==='delete',onCancel:()=>{this.owner?.mcp?.dismiss();this.controller?.close();},onConfirm:()=>this.confirm()}).margin({top:this.target==='add'?S.Dp16:this.target==='token'?S.Dp12:S.Dp0})
 }.width('100%').constraintSize({maxHeight:S.Dp520})}
 build(){EtaDialogContent({title:this.title(),summary:this.summary(),content:() => { this.body(); }}).margin({left:C.DialogOutsideMargin.width,right:C.DialogOutsideMargin.width,bottom:C.DialogOutsideMargin.height})}
}
