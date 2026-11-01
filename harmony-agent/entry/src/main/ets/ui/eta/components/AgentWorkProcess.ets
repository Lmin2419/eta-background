// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentWorkProcess.kt,
// AgentWorkSteps.kt and AgentToolActivity.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatMessageUi, ThinkingMessageUi, ToolActivityMessageUi } from '../../../core/eta/model/conversation';
import { AgentWorkStep, WorkToolGroup, toWorkSteps, workSummary, outcomeLine } from '../../../core/eta/agentWorkSteps';
import { MarkdownRenderStore } from '../../../core/eta/markdown/markdownRendering';
import { EtaComponentSizes as S, MiuixFontSizes as F, EtaMessageDefaults as C } from '../../../core/eta/tokens';
import { toolLabel,toolIcon } from './ToolResources';
import { MarkdownCopyButton } from '../markdown/MarkdownContent';
import { EtaFontFamilies } from '../../../platform/EtaFonts';
import { ThinkingRow } from './ChatMessageItem';
import { syncSlots } from '../../../core/eta/renderKey';
// Steps and grouped calls keep their views (and each row's expanded state) while a run updates them:
// ForEach does not rebuild a child whose key is unchanged, so content is replaced inside observed slots.
@Observed
class WorkStepSlot {
  value:AgentWorkStep;
  last:boolean=false;
  constructor(value:AgentWorkStep){this.value=value;}
}
@Observed
class ToolCallSlot {
  value:ToolActivityMessageUi;
  constructor(value:ToolActivityMessageUi){this.value=value;}
}
@Component
struct ToolActivityRow {
  @Prop tool:ToolActivityMessageUi={kind:'tool',id:'',toolName:'',status:'Unknown',argumentsSummary:'',command:null,resultSummary:null,imageCount:0};
  @State private expanded:boolean=false;
  private details():boolean {return !!this.tool.command||!!this.tool.resultSummary&&this.tool.resultSummary.trim()!==outcomeLine(this.tool);}
  build(){Column(){Row(){Column(){Text(this.tool.argumentsSummary||toolLabel(this.tool.toolName)).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface'));if(outcomeLine(this.tool)){Text(outcomeLine(this.tool)||'').fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary'))}}.layoutWeight(1).alignItems(HorizontalAlign.Start)
    if(this.details()){Image($r('app.media.ic_rounded_chevron_right')).width(S.Dp14).height(S.Dp14).rotate({angle:this.expanded?C.ChevronExpandedDegrees:S.Dp0}).fillColor($r('app.color.eta_message_chevron'))}
  }.width('100%').constraintSize({minHeight:C.WorkRowHeight}).padding({top:S.Dp6,bottom:S.Dp6,right:S.Dp8}).onClick(()=>{if(this.details())this.expanded=!this.expanded;})
    if(this.expanded){Column({space:S.Dp8}){if(this.tool.command){Column(){Row(){Text(this.tool.argumentsSummary||$r('app.string.shell_command')).fontSize(F.Footnote2).layoutWeight(1);MarkdownCopyButton({text:this.tool.command})};Scroll(){Text(this.tool.command).fontFamily(EtaFontFamilies.Normal).fontSize(F.Footnote2).copyOption(CopyOptions.LocalDevice)}.scrollable(ScrollDirection.Horizontal)}.padding(S.Dp12).backgroundColor($r('app.color.miuix_surface_container')).borderRadius(S.Dp10)}if(this.tool.resultSummary){Text(this.tool.resultSummary).fontColor($r('app.color.miuix_on_surface_variant_summary')).fontSize(F.Footnote2).maxLines(S.Dp12).textOverflow({overflow:TextOverflow.Ellipsis}).copyOption(CopyOptions.LocalDevice).width('100%').padding({left:S.Dp12,right:S.Dp12,top:S.Dp10,bottom:S.Dp10}).backgroundColor($r('app.color.miuix_surface_container')).borderRadius(S.Dp10)}}.width('100%').alignItems(HorizontalAlign.Start).padding({top:S.Dp2,bottom:S.Dp8,right:S.Dp4})}
  }.width('100%')}
}
@Component
struct ToolGroupRow {
  @Prop @Watch('syncCalls') group:WorkToolGroup={kind:'group',key:'',toolName:'',calls:[],running:false,failedCount:0};
  @State private expanded:boolean=false;
  @State private calls:ToolCallSlot[]=[];
  aboutToAppear():void {this.syncCalls();}
  private syncCalls():void {this.calls=syncSlots<ToolActivityMessageUi,ToolCallSlot>(this.calls,this.group.calls,(tool:ToolActivityMessageUi)=>tool.id,(tool:ToolActivityMessageUi)=>new ToolCallSlot(tool));}
  private count():string{return this.getUIContext().getHostContext()?.resourceManager.getIntPluralStringValueSync($r('app.plural.tool_group_calls').id,this.group.calls.length,this.group.calls.length)||'';}
  build(){Column(){Row(){Column(){Row(){Text(toolLabel(this.group.toolName)).fontSize(F.Body2);Text(' · '+this.count()).fontSize(F.Body2)}
    if(this.group.running){Text(this.group.calls[this.group.calls.length-1]?.argumentsSummary||'').fontSize(F.Footnote2).maxLines(1)}
    else if(this.group.failedCount>0){Text($r('app.plural.work_summary_failed',this.group.failedCount,this.group.failedCount)).fontSize(F.Footnote2).fontColor($r('app.color.miuix_error'))}
  }.layoutWeight(1).alignItems(HorizontalAlign.Start)
    Image($r('app.media.ic_rounded_chevron_right')).width(S.Dp14).height(S.Dp14).rotate({angle:this.expanded?C.ChevronExpandedDegrees:S.Dp0}).fillColor($r('app.color.eta_message_chevron'))
  }.width('100%').constraintSize({minHeight:C.WorkRowHeight}).padding({top:S.Dp6,bottom:S.Dp6}).onClick(()=>{this.expanded=!this.expanded;})
    if(this.expanded){Column(){ForEach(this.calls,(slot:ToolCallSlot)=>{ToolCallView({slot})},(slot:ToolCallSlot)=>slot.value.id)}.padding({bottom:S.Dp4})}
  }.width('100%')}
}
@Component
struct ToolCallView {
  @ObjectLink slot:ToolCallSlot;
  build(){Row(){Circle().width(S.Dp5).height(S.Dp5).fill(this.slot.value.status==='Failed'?$r('app.color.miuix_error'):$r('app.color.miuix_primary')).margin({top:S.Dp13,right:S.Dp8});ToolActivityRow({tool:this.slot.value}).layoutWeight(1)}.alignItems(VerticalAlign.Top)}
}
@Component
struct WorkStepView {
  @ObjectLink slot:WorkStepSlot;
  store:MarkdownRenderStore|undefined=undefined;
  private failed():boolean {const step=this.slot.value;return step.kind==='group'?step.failedCount>0:step.message.kind==='tool'&&step.message.status==='Failed';}
  @Builder private content(){
    if(this.slot.value.kind==='group'){ToolGroupRow({group:this.slot.value})}
    else if(this.slot.value.message.kind==='thinking'){ThinkingRow({message:this.slot.value.message as ThinkingMessageUi,store:this.store})}
    else if(this.slot.value.message.kind==='tool'){ToolActivityRow({tool:this.slot.value.message as ToolActivityMessageUi})}
    else if(this.slot.value.message.kind==='toolSummary'){Text(this.slot.value.message.tools.join(' · ')).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary'))}
  }
  build(){
      Row(){Stack({alignContent:Alignment.Top}){
        if(!this.slot.last){Divider().vertical(true).strokeWidth(S.Dp1).color($r('app.color.eta_markdown_divider')).height('100%')}
        Stack(){Image(this.slot.value.kind==='single'&&this.slot.value.message.kind==='thinking'?$r('app.media.ic_rounded_lightbulb'):this.slot.value.kind==='group'?toolIcon(this.slot.value.toolName):this.slot.value.message.kind==='tool'?toolIcon(this.slot.value.message.toolName):$r('app.media.ic_rounded_build')).width(S.Dp12).height(S.Dp12).fillColor(this.failed()?$r('app.color.miuix_error'):$r('app.color.miuix_primary'))}.width(C.WorkNodeSize).height(C.WorkNodeSize).borderRadius(C.WorkNodeSize).backgroundColor($r('app.color.miuix_secondary_container')).margin({top:(C.WorkRowHeight-C.WorkNodeSize)/2})
      }.width(C.WorkGutterWidth).alignSelf(ItemAlign.Stretch).margin({right:C.WorkGutterGap})
      Column(){this.content()}.layoutWeight(1)
      }.width('100%').alignItems(VerticalAlign.Top)
  }
}
@Component
export struct AgentWorkProcess {
  @Prop @Watch('syncSteps') messages:AgentChatMessageUi[]=[];
  @State private steps:WorkStepSlot[]=[];
  @Prop active:boolean=false;
  @Prop identity:string='';
  store:MarkdownRenderStore|undefined=undefined;
  @State private expansionRevision:number=0;
  aboutToAppear():void {this.syncSteps();}
  private syncSteps():void {
    const steps=syncSlots<AgentWorkStep,WorkStepSlot>(this.steps,toWorkSteps(this.messages),(step:AgentWorkStep)=>step.key,(step:AgentWorkStep)=>new WorkStepSlot(step));
    steps.forEach((slot:WorkStepSlot,index:number)=>{const last=index===steps.length-1;if(slot.last!==last)slot.last=last;});
    this.steps=steps;
  }
  private expanded():boolean {return this.expansionRevision>=0&&(this.store?.get(this.identity).thinkingExpanded(this.active)??this.active);}
  private summary():string {
    const summary=workSummary(this.messages),r=this.getUIContext().getHostContext()?.resourceManager,parts:string[]=[];
    if(summary.thinkingCount)parts.push(r?.getIntPluralStringValueSync($r('app.plural.work_summary_thinking').id,summary.thinkingCount,summary.thinkingCount)||'');
    if(summary.toolCount)parts.push(r?.getIntPluralStringValueSync($r('app.plural.work_summary_tools').id,summary.toolCount,summary.toolCount)||'');
    if(summary.failedCount)parts.push(r?.getIntPluralStringValueSync($r('app.plural.work_summary_failed').id,summary.failedCount,summary.failedCount)||'');
    return parts.join(' · ')||r?.getStringSync($r('app.string.work_completed').id)||'';
  }
  build(){Column(){
    Row(){Stack(){Image(this.active?$r('app.media.ic_rounded_lightbulb'):$r('app.media.ic_rounded_checklist')).width(S.Dp15).height(S.Dp15).fillColor(this.active?$r('app.color.miuix_primary'):$r('app.color.miuix_on_surface_variant_summary')).margin({right:S.Dp0})}.width(C.WorkGutterWidth).margin({right:C.WorkGutterGap})
      Text(this.active?$r('app.string.work_analyzing'):this.summary()).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).layoutWeight(1)
      Image($r('app.media.ic_rounded_chevron_right')).width(S.Dp14).height(S.Dp14).rotate({angle:this.expanded()?C.ChevronExpandedDegrees:S.Dp0}).fillColor($r('app.color.miuix_on_surface_variant_summary'))
    }.width('100%').constraintSize({minHeight:C.WorkRowHeight}).padding({right:S.Dp8}).onClick(()=>{this.store?.get(this.identity).toggleWork(this.active);this.expansionRevision++;}).accessibilityText(this.expanded()?$r('app.string.work_collapse'):$r('app.string.work_expand'))
    if(this.expanded()){Column(){ForEach(this.steps,(slot:WorkStepSlot)=>{WorkStepView({slot,store:this.store})},(slot:WorkStepSlot)=>slot.value.key)}.padding({bottom:S.Dp6})}
  }.width('100%').padding({left:S.Dp16,right:S.Dp16,top:S.Dp2,bottom:S.Dp2})}
}
