// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/screens/home/AgentHomeScreen.kt,
// ui/components/AgentChatBody.kt (EmptyChatState/SuggestionCard) @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController, ShellState, emptyShellState } from '../../../core/foundation/controller';
import { EtaComponentSizes as S, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { AgentChatInputBar } from '../components/AgentChatInputBar';
import { AgentChatBody } from '../components/AgentChatBody';
import { AgentConversationLoading } from '../components/AgentConversationLoading';
@Component
export struct AgentHomeScreen {
  @Prop @Watch('showMutation') state: ShellState = emptyShellState();
  @Prop keyboardVisible: boolean = false;
  controller: FoundationController | undefined = undefined;
  private shownMutation:string='';
  private showMutation():void{
    const target=this.state.mutation;if(!target){this.shownMutation='';return;}
    const key=target.conversationId+target.messageId+target.kind;if(key===this.shownMutation)return;this.shownMutation=key;
    const deleting=target.kind==='delete';
    const summary:ResourceStr=target.laterTurns>0?this.getUIContext().getHostContext()!.resourceManager.getIntPluralStringValueSync(
      deleting?$r('app.plural.conversation_delete_later_turns').id:$r('app.plural.conversation_regenerate_later_turns').id,target.laterTurns,target.laterTurns):
      deleting?$r('app.string.conversation_delete_message_body'):$r('app.string.conversation_regenerate_current_turn');
    this.getUIContext().showAlertDialog({title:deleting?$r('app.string.conversation_delete_message_title'):$r('app.string.conversation_regenerate_title'),message:summary,
      primaryButton:{value:$r('app.string.action_cancel'),action:()=>this.controller?.dismissMessageMutation()},
      secondaryButton:{value:deleting?$r('app.string.action_delete'):$r('app.string.action_regenerate'),fontColor:deleting?$r('app.color.miuix_error'):$r('app.color.miuix_primary'),action:()=>{void this.controller?.confirmMessageMutation();}},
      cancel:()=>this.controller?.dismissMessageMutation()});
  }
  @Builder private suggestion(title: Resource, icon: Resource) {
    Column() {
      Image(icon).width(S.Dp18).height(S.Dp18).fillColor($r('app.color.miuix_disabled_primary'))
      Blank().height(S.Dp10)
      Text(title).fontSize(F.Body2).fontColor($r('app.color.miuix_disabled_on_surface')).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis})
      Text($r('app.string.eta_port_unavailable')).fontSize(F.Footnote1).fontColor($r('app.color.miuix_disabled_on_surface'))
    }.alignItems(HorizontalAlign.Start).layoutWeight(1).padding(S.Dp14).backgroundColor($r('app.color.miuix_surface'))
      .border({width:S.Dp0_5,color:$r('app.color.eta_suggestion_outline')}).borderRadius(S.Dp16).enabled(false)
  }
  @Builder private empty() {
    Column() {
      Text($r('app.string.ui_how_can_i_help_you_e75391')).fontSize(F.Headline1).fontColor($r('app.color.miuix_on_surface'))
      Blank().height(S.Dp32)
      if(!this.keyboardVisible) {
        Column({space:S.Dp12}) {
          Row({space:S.Dp12}) {
            this.suggestion($r('app.string.ui_analyze_current_screen_ebf08f'),$r('app.media.ic_rounded_document_scanner'))
            this.suggestion($r('app.string.ui_open_wechat_6b2c28'),$r('app.media.ic_rounded_rocket_launch'))
          }.width('100%')
          Row({space:S.Dp12}) {
            this.suggestion($r('app.string.ui_browse_the_web_da7afb'),$r('app.media.ic_rounded_language'))
            this.suggestion($r('app.string.ui_check_memory_pressure_2d9600'),$r('app.media.ic_rounded_terminal'))
          }.width('100%')
        }.width('100%').padding({left:S.Dp20,right:S.Dp20})
      }
    }.width('100%').height('100%').padding({bottom:S.Dp56}).justifyContent(FlexAlign.Center)
  }
  build() {
    Column() {
      Column() {
        if(!this.state.ready){AgentConversationLoading({failed:!!this.state.error,onRetry:()=>{void this.controller?.initialize();}})}
        else if(this.state.chat.rows.length===0){this.empty()}
        else {AgentChatBody({chat:this.state.chat,controller:this.controller})}
      }.width('100%').layoutWeight(1)
      if(this.state.ready){Column(){AgentChatInputBar({state:this.state,controller:this.controller})}.width('100%')
        .padding({left:S.Dp16,right:S.Dp16,bottom:S.Dp12}).backgroundColor($r('app.color.miuix_surface'))}
    }.width('100%').height('100%').backgroundColor($r('app.color.miuix_surface'))
  }
}
