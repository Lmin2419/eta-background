// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatInputBar.kt, AgentChatFileAttachments.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController, ShellState, emptyShellState } from '../../../core/foundation/controller';
import { AgentChatQueueStrip } from './AgentChatQueueStrip';
import { EtaHaptics } from '../../../platform/EtaHaptics';
import { EtaHaptic } from '../../../core/eta/hapticFeedback';
import { modelCount } from '../../../core/eta/modelPicker';
import { EtaChatDefaults as C, EtaComponentSizes as S, MiuixFontSizes as F, EtaModelPickerDefaults as M } from '../../../core/eta/tokens';
import { AgentModelPickerContent, ModelBrandMark } from './AgentModelPicker';
import { AgentContextUsageButton } from './AgentChatModelControls';

@Component
export struct AgentChatInputBar {
  @Prop @Watch('syncInput') state: ShellState = emptyShellState();
  controller: FoundationController | undefined = undefined;
  @State private text: string = '';
  @State private quickVisible: boolean = false;
  @State private modelSheetVisible: boolean = false;
  @State private attachmentVisible: boolean = false;
  @State private inputTop: number = S.Dp0;
  private identity: string = '';
  private revision: number = -1;
  aboutToAppear(): void { this.syncInput(); }
  private syncInput(): void {
    if(this.identity!==this.state.chat.id||this.revision!==this.state.chat.inputVersion) {
      this.identity=this.state.chat.id;this.revision=this.state.chat.inputVersion;this.text=this.state.chat.input;
    }
    const open=this.state.sheet==='models'&&!this.state.chat.active;
    this.quickVisible=open&&modelCount(this.state.picker)<=M.ModelPopupLimit;
    this.modelSheetVisible=open&&modelCount(this.state.picker)>M.ModelPopupLimit;
  }
  private dismissPicker(): void { if(this.state.sheet==='models')this.controller?.back(); }
  private canSend(): boolean { return this.text.trim().length>0; }
  private primaryStop(): boolean { return this.state.chat.active&&!this.canSend(); }
  private send(immediate:boolean=false): void { this.controller?.draft(this.text);void this.controller?.send(immediate); }
  private quickHeight(): number {
    const lineVp=this.getUIContext().px2vp(this.getUIContext().fp2px(F.Body2));
    return Math.max(S.Dp40,Math.min(this.inputTop-S.Dp24,
      modelCount(this.state.picker)*(lineVp+S.Dp26)+this.state.pickerGroups.length*S.Dp32+S.Dp12));
  }
  @Builder private attachments() {
    Menu() {
      MenuItem({content:$r('app.string.attachment_image'),labelInfo:$r('app.string.eta_port_unavailable')}).enabled(false)
      MenuItem({content:$r('app.string.attachment_file'),labelInfo:$r('app.string.eta_port_unavailable')}).enabled(false)
      MenuItem({content:$r('app.string.attachment_folder'),labelInfo:$r('app.string.eta_port_unavailable')}).enabled(false)
      MenuItem({content:$r('app.string.attachment_enter_path'),labelInfo:$r('app.string.eta_port_unavailable')}).enabled(false)
    }
  }
  @Builder private quickModels() {
    Column(){AgentModelPickerContent({state:this.state,controller:this.controller})}.width(M.ModelPopupWidth).height(this.quickHeight())
  }
  @Builder private sheetModels() { AgentModelPickerContent({state:this.state,isSheet:true,controller:this.controller}) }
  @Builder private stop() {
    Button({type:ButtonType.Normal}) {
      Stack(){Image($r('app.media.ic_rounded_stop')).width(C.StopIconSize).height(C.StopIconSize).fillColor($r('app.color.miuix_on_surface'))}
        .width(C.ActionIconSize).height(C.ActionIconSize).borderRadius(C.ActionIconSize).backgroundColor($r('app.color.miuix_surface_container_high'))
    }.width(C.ActionSize).height(C.ActionSize).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent'))
      .accessibilityText($r('app.string.chat_stop')).onClick(()=>this.controller?.stop())
  }
  @Builder private composer() {
    Column() {
      TextArea({text:this.text,placeholder:this.state.chat.active?$r('app.string.chat_eta_working'):$r('app.string.chat_input_hint')})
        .id('eta-composer').fontSize(F.Body1).lineHeight(C.InputLineHeight).fontColor($r('app.color.miuix_on_surface'))
        .placeholderColor($r('app.color.miuix_on_surface_variant_summary')).caretColor($r('app.color.miuix_primary'))
        .minLines(1).maxLines(C.InputMaxLines).constraintSize({minHeight:S.Dp40})
        .padding({left:S.Dp8,right:S.Dp8,top:S.Dp6,bottom:S.Dp6}).backgroundColor($r('app.color.eta_transparent'))
        .onChange((value:string)=>{this.text=value;this.controller?.draft(value);})
      Row() {
        Button({type:ButtonType.Normal}) {
          Image(this.state.chat.messageEdit?$r('app.media.ic_rounded_close'):$r('app.media.ic_rounded_add')).width(C.ActionIconSize).height(C.ActionIconSize).fillColor($r('app.color.miuix_on_surface'))
        }.width(C.ActionSize).height(C.ActionSize).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent'))
          .accessibilityText(this.state.chat.messageEdit?$r('app.string.ui_cancel_edit_c698df'):$r('app.string.ui_add_attachment_dba9e8')).onClick(()=>{if(this.state.chat.messageEdit)this.controller?.cancelMessageEdit();else this.attachmentVisible=true;})
        Blank().width(S.Dp2)
        // Source Atom means reasoning effort. Disabled pending the protocol/configuration port.
        Button({type:ButtonType.Normal}) {
          Image($r('app.media.ic_atom')).width(C.ThinkingIconSize).height(C.ThinkingIconSize).fillColor($r('app.color.miuix_disabled_on_surface'))
        }.width(C.ActionSize).height(C.ActionSize).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent')).enabled(false)
          .accessibilityText($r('app.string.eta_port_unavailable')).bindTips($r('app.string.eta_port_unavailable'))
        Blank()
        if(this.state.chat.rows.length>0) { AgentContextUsageButton({usage:this.state.contextUsage});Blank().width(S.Dp2) }
        Button({type:ButtonType.Normal}) {
          ModelBrandMark({modelId:this.state.picker.selectedModel?.modelId||'',sourceType:this.state.picker.selectedModel?.providerSourceType||'custom'})
        }.width(C.ActionSize).height(C.ActionSize).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent'))
          .enabled(!this.state.chat.active&&!this.state.busy&&modelCount(this.state.picker)>0)
          .accessibilityText($r('app.string.model_switch_current',this.state.chat.model)).onClick(()=>this.controller?.chooseModel())
        if(this.state.chat.active&&this.canSend()) { this.stop() }
        Button({type:ButtonType.Normal}) {
          Stack() {
            Image(this.primaryStop()?$r('app.media.ic_rounded_stop'):$r('app.media.ic_rounded_arrow_upward'))
              .width(this.primaryStop()?C.StopIconSize:C.SendIconSize).height(this.primaryStop()?C.StopIconSize:C.SendIconSize)
              .fillColor(this.primaryStop()?$r('app.color.miuix_surface'):this.canSend()?$r('app.color.miuix_on_primary'):$r('app.color.miuix_on_surface_variant_actions'))
          }.width(C.ActionIconSize).height(C.ActionIconSize).borderRadius(C.ActionIconSize)
            .backgroundColor(this.primaryStop()?$r('app.color.miuix_on_surface'):this.canSend()?$r('app.color.miuix_primary'):$r('app.color.miuix_surface_container_high'))
        }.width(C.ActionSize).height(C.ActionSize).padding(S.Dp0).backgroundColor($r('app.color.eta_transparent'))
          .enabled(this.primaryStop()||(!this.state.busy&&this.canSend()))
          .accessibilityText(this.primaryStop()?$r('app.string.chat_stop'):$r('app.string.chat_send'))
          .gesture(GestureGroup(GestureMode.Exclusive,
            LongPressGesture().onAction(()=>{if(this.state.chat.active&&this.canSend()&&!this.state.busy){EtaHaptics.perform(EtaHaptic.LongPress);this.send(true);}}),
            TapGesture().onAction(()=>{if(this.primaryStop())this.controller?.stop();else if(!this.state.busy)this.send();})
          ))
      }.width('100%')
    }.width('100%').padding({left:S.Dp10,right:S.Dp10,top:S.Dp8,bottom:S.Dp8})
      .backgroundColor($r('app.color.miuix_surface_container')).border({width:S.Dp0_5,color:$r('app.color.eta_input_outline')}).borderRadius(C.InputCornerRadius)
      .shadow({radius:S.Dp8,color:$r('app.color.eta_input_shadow')})
      .onAreaChange((_old,area)=>{this.inputTop=Number(area.globalPosition.y);})
      .bindMenu($$this.attachmentVisible,this.attachments,{placement:Placement.TopLeft,onDisappear:()=>{this.attachmentVisible=false;}})
      .bindPopup($$this.quickVisible,{builder:this.quickModels,placement:Placement.TopRight,enableArrow:false,
        popupColor:$r('app.color.miuix_surface'),onStateChange:(event)=>{if(!event.isVisible)this.dismissPicker();}})
      .bindSheet($$this.modelSheetVisible,this.sheetModels,{height:(M.SheetHeightFraction*100)+'%',showClose:false,
        title:{title:$r('app.string.model_picker_title')},backgroundColor:$r('app.color.miuix_surface'),onDisappear:()=>this.dismissPicker()})
  }
  build(){Column(){
    if(this.state.chat.queuedMessages.length){AgentChatQueueStrip({items:this.state.chat.queuedMessages,streaming:this.state.chat.active,
      onSteer:(id:string)=>{void this.controller?.steerQueued(id);},onRecall:(id:string)=>this.controller?.recallQueued(id,this.text),onRemove:(id:string)=>this.controller?.removeQueued(id)}).margin({bottom:S.Dp8})}
    if(this.state.chat.messageEdit){Text(this.state.chat.messageEdit.hasLaterTurns?$r('app.string.chat_edit_replace_later'):$r('app.string.chat_edit_replace_message'))
      .fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary')).padding({left:S.Dp18,right:S.Dp18,bottom:S.Dp8}).width('100%')}
    this.composer()
  }.width('100%')}
}
