// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/ConversationPanePanel.kt,
// ui/app/AgentAppRoot.kt (rename/delete dialogs) @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController, ShellState, emptyShellState } from '../../../core/foundation/controller';
import { ConversationDrawerGroup, ConversationSummaryUi } from '../../../core/eta/conversationDrawer';
import { ConversationPanelMetrics as M, EtaComponentSizes as S, MiuixFontSizes as F, EtaFontWeights as W } from '../../../core/eta/tokens';
import { EtaHaptics } from '../../../platform/EtaHaptics';
import { EtaHaptic } from '../../../core/eta/hapticFeedback';
import { EtaDialogContent, MiuixDialogActions } from './EtaDialogs';
import { EmptyEtaSlot } from './EtaCard';
import { renderKey } from '../../../core/eta/renderKey';
@CustomDialog
struct RenameConversationDialog {
  controller?:CustomDialogController;
  owner:FoundationController|undefined=undefined;
  initial:string='';
  @State private text:string='';
  @State private error:string='';
  @State private saving:boolean=false;
  aboutToAppear():void{this.text=this.initial;}
  @Builder private body(){Column({space:S.Dp16}){
    TextInput({text:this.text,placeholder:$r('app.string.conversation_rename_hint')}).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface')).backgroundColor($r('app.color.miuix_surface_container_high')).onChange((value:string)=>{this.text=value;this.owner?.paneRenameInput(value);})
    if(this.error){Text(this.error).fontSize(F.Body2).fontColor($r('app.color.miuix_error'))}
    MiuixDialogActions({confirmText:$r('app.string.action_save'),cancelEnabled:!this.saving,confirmEnabled:!this.saving&&this.text.trim().length>0,onCancel:()=>{this.owner?.dismissPaneMutation();this.controller?.close();},onConfirm:()=>{this.saving=true;void this.owner?.confirmPaneMutation().then(()=>{this.saving=false;if(!this.owner?.snapshot().paneMutation)this.controller?.close();else this.error=this.owner.snapshot().error;});}})
  }}
  build(){EtaDialogContent({title:$r('app.string.conversation_rename_title'),content:() => { this.body(); }})}
}
@Component
export struct ConversationPanePanel {
  @Prop @Watch('syncDialog') state:ShellState=emptyShellState();
  controller:FoundationController|undefined=undefined;
  private shown:string='';
  private renameDialog:CustomDialogController|undefined=undefined;
  aboutToDisappear():void{this.renameDialog?.close();}
  private syncDialog():void{
    const target=this.state.paneMutation;if(!target){this.shown='';return;}
    const key=target.id+target.kind;if(this.shown===key)return;this.shown=key;
    if(target.kind==='rename'){
      this.renameDialog=new CustomDialogController({builder:RenameConversationDialog({owner:this.controller,initial:target.input}),customStyle:true,cancel:()=>this.controller?.dismissPaneMutation()});this.renameDialog.open();
    }else this.getUIContext().showAlertDialog({title:$r('app.string.conversation_delete_title'),message:target.active?$r('app.string.conversation_stop_and_delete_message'):$r('app.string.conversation_delete_message'),
      primaryButton:{value:$r('app.string.action_cancel'),action:()=>this.controller?.dismissPaneMutation()},
      secondaryButton:{value:target.active?$r('app.string.conversation_stop_and_delete'):$r('app.string.action_delete'),fontColor:$r('app.color.miuix_error'),action:()=>{void this.controller?.confirmPaneMutation().then(()=>{this.controller?.dismissPaneMutation();});}},cancel:()=>this.controller?.dismissPaneMutation()});
  }
  @Builder private menu(item:ConversationSummaryUi){Menu(){
    MenuItem({content:$r('app.string.action_rename'),startIcon:$r('app.media.ic_rounded_edit')}).onClick(()=>this.controller?.requestPaneMutation(item.id,'rename'))
    MenuItem({content:$r('app.string.action_export'),startIcon:$r('app.media.ic_rounded_download')}).onClick(()=>{void this.controller?.exportConversation(item.id);})
    MenuItem({content:$r('app.string.action_delete'),startIcon:$r('app.media.ic_rounded_delete')}).contentFontColor($r('app.color.miuix_error')).onClick(()=>this.controller?.requestPaneMutation(item.id,'delete'))
  }}
  @Builder private row(item:ConversationSummaryUi){Row(){Column(){
    Text(item.title.trim()||item.preview).fontSize(F.Body1).fontColor(item.id===this.state.chat.id?$r('app.color.miuix_primary'):$r('app.color.miuix_on_surface')).fontWeight(item.id===this.state.chat.id?W.SemiBold:FontWeight.Medium).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis}).width('100%')
    if(item.runStatus){Text(item.runStatus).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary')).maxLines(1).textOverflow({overflow:TextOverflow.Ellipsis}).width('100%')}
  }.layoutWeight(1).alignItems(HorizontalAlign.Start)
    if(item.isActiveRun){Circle().width(M.ActiveDotSize).height(M.ActiveDotSize).fill($r('app.color.miuix_primary')).margin({left:M.ActiveDotGap})}
  }.width('100%').constraintSize({minHeight:M.RowMinHeight}).padding({left:M.RowHorizontalPadding,right:M.RowHorizontalPadding,top:M.RowVerticalPadding,bottom:M.RowVerticalPadding})
    .borderRadius(M.RowCornerRadius).backgroundColor(item.id===this.state.chat.id?$r('app.color.miuix_surface_container_highest'):$r('app.color.eta_transparent'))
    .onClick(()=>{void this.controller?.selectConversation(item.id);}).bindContextMenu(this.menu.bind(this,item),ResponseType.LongPress,{onAppear:()=>EtaHaptics.perform(EtaHaptic.LongPress)})
  }
  @Builder private section(group:ConversationDrawerGroup){Row(){
    Image($r('app.media.ic_rounded_schedule')).width(M.SectionIconSize).height(M.SectionIconSize).fillColor($r('app.color.miuix_on_surface_variant_actions'))
    Text(group.section==='Pinned'?$r('app.string.conversation_section_pinned'):group.section==='Today'?$r('app.string.conversation_section_today'):group.label).fontSize(F.Footnote1).fontWeight(W.SemiBold).fontColor($r('app.color.miuix_on_surface_variant_summary')).margin({left:M.SectionIconGap})
    Text(String(group.items.length)).fontSize(F.Footnote1).fontWeight(FontWeight.Medium).fontColor($r('app.color.miuix_on_surface_variant_actions')).margin({left:M.SectionCountGap})
  }.width('100%').padding({top:M.SectionTopPadding,bottom:M.SectionBottomPadding})}
  @Builder private dock(icon:Resource,label:Resource,enabled:boolean,action:()=>void){Button({type:ButtonType.Normal}){
    Image(icon).width(M.DockEntryIconSize).height(M.DockEntryIconSize).fillColor(enabled?$r('app.color.miuix_on_surface'):$r('app.color.miuix_disabled_on_surface'))
  }.layoutWeight(1).constraintSize({minHeight:M.RowMinHeight}).borderRadius(M.DockEntryCornerRadius).backgroundColor($r('app.color.eta_transparent')).padding(S.Dp0)
    .enabled(enabled).accessibilityText(label).bindTips(enabled?label:$r('app.string.eta_port_unavailable')).onClick(action)}
  build(){Column(){
    Search({value:this.state.search,placeholder:$r('app.string.conversation_search_hint')}).searchButton('').fontColor($r('app.color.miuix_on_surface')).backgroundColor($r('app.color.miuix_surface_container_highest')).onChange((value:string)=>this.controller?.searchConversations(value))
      .margin({top:M.TopInset,bottom:M.AfterActionBar,left:M.PaneHorizontalPadding,right:M.PaneHorizontalPadding})
    List({space:M.RowGap}){
      if(this.state.paneGroups.length===0){ListItem(){Text(this.state.search.trim()?$r('app.string.conversation_no_results'):$r('app.string.conversation_empty')).fontSize(F.Body2).fontWeight(FontWeight.Medium).fontColor($r('app.color.miuix_on_surface_variant_summary')).padding({left:M.RowHorizontalPadding,right:M.RowHorizontalPadding,top:M.EmptyVerticalPadding,bottom:M.EmptyVerticalPadding})}}
      ForEach(this.state.paneGroups,(group:ConversationDrawerGroup)=>{
        ListItem(){this.section(group)}
        ForEach(group.items,(item:ConversationSummaryUi)=>{ListItem(){this.row(item)}},(item:ConversationSummaryUi)=>renderKey(item.id,item))
      },(group:ConversationDrawerGroup)=>renderKey(group.id,group))
    }.layoutWeight(1).width('100%').padding({left:M.PaneHorizontalPadding,right:M.PaneHorizontalPadding,top:S.Dp4,bottom:S.Dp4}).clip(true).scrollBar(BarState.Off)
    Row(){
      this.dock($r('app.media.ic_rounded_settings'),$r('app.string.action_settings'),true,()=>this.controller?.navigate('settings'))
      this.dock($r('app.media.ic_rounded_cloud'),$r('app.string.conversation_dock_models'),true,()=>this.controller?.navigate('providers'))
      this.dock($r('app.media.ic_rounded_dashboard'),$r('app.string.eta_port_unavailable'),false,()=>{})
      this.dock($r('app.media.ic_rounded_import_contacts'),$r('app.string.eta_port_unavailable'),false,()=>{})
      this.dock($r('app.media.ic_rounded_lock'),$r('app.string.eta_port_unavailable'),false,()=>{})
      this.dock($r('app.media.ic_rounded_sports_bar'),$r('app.string.eta_port_unavailable'),false,()=>{})
    }.width('100%').padding({left:M.PaneHorizontalPadding,right:M.PaneHorizontalPadding,top:M.DockTopGap,bottom:M.BottomInset})
  }.width('100%').height('100%').backgroundColor($r('app.color.eta_conversation_pane'))}
}
