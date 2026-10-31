// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/ChatMessageItem.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController } from '../../../core/foundation/controller';
import { AgentChatMessageUi, UserMessageUi, AgentMessageUi, ThinkingMessageUi, SystemNoticeMessageUi, userMessage } from '../../../core/eta/model/conversation';
import { MarkdownRenderStore } from '../../../core/eta/markdown/markdownRendering';
import { EtaComponentSizes as S, MiuixFontSizes as F, EtaMarkdownDefaults as M, EtaMessageDefaults as C } from '../../../core/eta/tokens';
import { EtaHaptic } from '../../../core/eta/hapticFeedback';
import { EtaHaptics } from '../../../platform/EtaHaptics';
import { copyEtaText } from '../../../platform/EtaTextActions';
import { StreamingMarkdown } from '../markdown/StreamingMarkdown';
@Component
export struct ThinkingRow {
  @Prop message:ThinkingMessageUi={kind:'thinking',id:'',content:'',isStreaming:false,elapsedSeconds:null,collapsed:false};
  store:MarkdownRenderStore|undefined=undefined;
  @State private expandedRevision:number=0;
  private scroller:Scroller=new Scroller();
  private expanded():boolean {return this.expandedRevision>=0&&(this.store?.get(this.message.id).thinkingExpanded(this.message.isStreaming)??this.message.isStreaming);}
  private automatic():boolean {return this.store?.get(this.message.id).thinkingAutomatic()??true;}
  private toggle():void {this.store?.get(this.message.id).toggleThinking(this.message.isStreaming);this.expandedRevision++;}
  private completedLabel():ResourceStr{const seconds=this.message.elapsedSeconds;return seconds&&seconds>0?this.getUIContext().getHostContext()!.resourceManager.getIntPluralStringValueSync($r('app.plural.reasoning_completed_seconds').id,seconds,seconds):$r('app.string.reasoning_completed');}
  private cap():number {return this.getUIContext().px2vp(this.getUIContext().fp2px(M.THINKING_LINE_HEIGHT_SP*C.ThinkingTailLines));}
  build(){Column(){
    Row(){Text(this.message.isStreaming?$r('app.string.reasoning_in_progress'):this.completedLabel()).fontSize(F.Body2).fontColor(this.message.isStreaming?$r('app.color.miuix_on_surface'):$r('app.color.miuix_on_surface_variant_summary')).layoutWeight(1)
      Image($r('app.media.ic_rounded_chevron_right')).width(S.Dp14).height(S.Dp14).rotate({angle:this.expanded()?C.ChevronExpandedDegrees:S.Dp0}).fillColor($r('app.color.miuix_on_surface_variant_summary'))
    }.width('100%').constraintSize({minHeight:C.WorkRowHeight}).padding({right:S.Dp8}).onClick(()=>this.toggle()).accessibilityText(this.expanded()?$r('app.string.reasoning_collapse'):$r('app.string.reasoning_expand'))
    if(this.expanded()&&this.message.content){
      Scroll(this.scroller){StreamingMarkdown({content:this.message.content,streaming:this.message.isStreaming,tone:'Thinking',session:this.store?.get(this.message.id)}).onAreaChange(()=>{if(this.automatic())this.scroller.scrollEdge(Edge.Bottom);})}
        .width('100%').constraintSize({maxHeight:this.automatic()?this.cap():undefined}).scrollBar(BarState.Off).enableScrollInteraction(!this.automatic()).padding({right:S.Dp8,bottom:S.Dp8})
    }
  }.width('100%').alignItems(HorizontalAlign.Start)}
}
@Component
export struct ChatMessageItem {
  controller:FoundationController|undefined=undefined;
  @Prop message:AgentChatMessageUi=userMessage('','');
  @Prop actionsEnabled:boolean=true;
  store:MarkdownRenderStore|undefined=undefined;
  @State private copied:boolean=false;
  @State private revealed:boolean=false;
  private timer:number=-1;
  aboutToDisappear():void {clearTimeout(this.timer);}
  private content():string {if(this.message.kind==='user'||this.message.kind==='assistant'||this.message.kind==='thinking')return this.message.content;return this.message.kind==='notice'?this.message.detail||'':'';}
  private copy():void {void copyEtaText(this.content()).then(()=>{this.copied=true;EtaHaptics.perform(EtaHaptic.Confirm);clearTimeout(this.timer);this.timer=setTimeout(()=>{this.copied=false;},M.CopyResetMs);}).catch(()=>{});}
  private noticeLabel():Resource {const code=(this.message as SystemNoticeMessageUi).code;return code==='Stopped'?$r('app.string.system_notice_stopped'):code==='Interrupted'?$r('app.string.system_notice_interrupted'):code==='EmptyResult'?$r('app.string.system_notice_empty_result'):code==='ContextCompaction'?$r('app.string.context_compaction'):code==='ModelRetry'?$r('app.string.system_notice_model_retry'):$r('app.string.system_notice_runtime_failed');}
  @Builder private userMenu(){Menu(){MenuItem({content:$r('app.string.ui_copy_4edd1d'),startIcon:$r('app.media.ic_rounded_content_copy')}).onClick(()=>this.copy())
    MenuItem({content:$r('app.string.ui_edit_a7f814')}).enabled(this.actionsEnabled).onClick(()=>this.controller?.editMessage(this.message.id))
    MenuItem({content:$r('app.string.ui_delete_3755f5')}).enabled(this.actionsEnabled).onClick(()=>{void this.controller?.requestMessageMutation(this.message.id,'delete');})
  }}
  @Builder private actions(){Row({space:S.Dp2}){
    Button({type:ButtonType.Normal}){Image(this.copied?$r('app.media.ic_rounded_check'):$r('app.media.ic_rounded_content_copy')).width(S.Dp16).height(S.Dp16).fillColor(this.copied?$r('app.color.miuix_primary'):$r('app.color.eta_message_action'))}.width(S.Dp36).height(S.Dp36).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent')).onClick(()=>this.copy()).accessibilityText($r('app.string.ui_copy_4edd1d'))
    Button({type:ButtonType.Normal}){Image($r('app.media.ic_rounded_refresh')).width(S.Dp16).height(S.Dp16).fillColor(this.actionsEnabled?$r('app.color.eta_message_action'):$r('app.color.miuix_disabled_on_surface'))}.width(S.Dp36).height(S.Dp36).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent')).enabled(this.actionsEnabled).accessibilityText($r('app.string.action_regenerate')).onClick(()=>{void this.controller?.requestMessageMutation(this.message.id,'regenerate');})
    Button({type:ButtonType.Normal}){Image($r('app.media.ic_rounded_delete')).width(S.Dp16).height(S.Dp16).fillColor(this.actionsEnabled?$r('app.color.eta_message_action'):$r('app.color.miuix_disabled_on_surface'))}.width(S.Dp36).height(S.Dp36).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent')).enabled(this.actionsEnabled).accessibilityText($r('app.string.action_delete')).onClick(()=>{void this.controller?.requestMessageMutation(this.message.id,'delete');})
  }.margin({top:S.Dp4})}
  build(){Column(){
    if(this.message.kind==='user'){
      Row(){Column(){Text(this.content()).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface')).copyOption(CopyOptions.LocalDevice)
        if((this.message as UserMessageUi).isEdited){Text($r('app.string.ui_edited_c36776')).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).margin({top:S.Dp4})}
      }.constraintSize({maxWidth:S.Dp320}).padding({left:S.Dp16,right:S.Dp16,top:S.Dp12,bottom:S.Dp12}).alignItems(HorizontalAlign.Start).backgroundColor($r('app.color.miuix_surface_container_high')).borderRadius({topLeft:S.Dp20,topRight:S.Dp20,bottomLeft:S.Dp20,bottomRight:S.Dp6})
        .bindContextMenu(this.userMenu,ResponseType.LongPress,{onAppear:()=>EtaHaptics.perform(EtaHaptic.LongPress)})
      }.width('100%').justifyContent(FlexAlign.End)
    }else if(this.message.kind==='assistant'){
      StreamingMarkdown({content:this.content(),streaming:(this.message as AgentMessageUi).isStreaming,session:this.store?.get(this.message.id),onRevealComplete:(complete:boolean)=>{this.revealed=complete;}})
      if(this.revealed&&!(this.message as AgentMessageUi).isStreaming){this.actions()}
    }else if(this.message.kind==='notice'){
      Text(this.noticeLabel()).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface')).copyOption(CopyOptions.LocalDevice)
      if(this.content()){Text(this.content()).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface')).copyOption(CopyOptions.LocalDevice).margin({top:S.Dp12})}
    }
  }.width('100%').alignItems(HorizontalAlign.Start).padding({left:S.Dp20,right:S.Dp20,top:S.Dp8,bottom:S.Dp8})}
}
