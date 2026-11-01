// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/SettingsScreen.kt (diagnostic entry),
// ui/components/EtaCard.kt @ 3b1794d; Harmony's M0 diagnostic data/controls are retained.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { pasteboard } from '@kit.BasicServicesKit';
import { common } from '@kit.AbilityKit';
import { FoundationController, ShellState, emptyShellState } from '../../../core/foundation/controller';
import { DiagnosticFact } from '../../../core/diagnostics';
import { AccessibilityProbe, A11Y_TEST_BUTTON_TEXT, emptyAccessibilityProbe } from '../../../core/eta/device/accessibilityProbe';
import { readAccessibilityProbe, requestAccessibilityGesture } from '../../../platform/AccessibilityProbeFile';
import { EtaComponentSizes as S, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { MiuixScaffoldPage } from '../components/MiuixScaffoldPage';
import { EtaCard } from '../components/EtaCard';
import { EtaTextButton } from '../components/EtaControls';
// One object-literal parameter: ArkUI re-evaluates it when the probe state changes (positional @Builder
// parameters keep their first values, so the rows would not update after "刷新").
interface ProbeRowParams { label: ResourceStr; value: ResourceStr; }
@Component
export struct DiagnosticsPage {
  @Prop state:ShellState=emptyShellState();
  controller:FoundationController|undefined=undefined;
  @State private notice:ResourceStr='';
  @State private probe:AccessibilityProbe=emptyAccessibilityProbe();
  @State private probeClicks:number=0;
  @State private probeNotice:ResourceStr='';
  aboutToAppear():void{this.refreshProbe(false);}
  private filesDir():string{const context=this.getUIContext().getHostContext();return context!==undefined&&context!==null?(context as common.UIAbilityContext).filesDir:'';}
  private refreshProbe(notify:boolean):void{const dir=this.filesDir();if(dir===''){this.probe=emptyAccessibilityProbe();if(notify)this.probeNotice=$r('app.string.a11y_probe_refreshed');return;}
    this.probe=readAccessibilityProbe(dir);
    if(notify)this.probeNotice=$r('app.string.a11y_probe_refreshed');}
  private requestInjection():void{const dir=this.filesDir();if(dir===''||!requestAccessibilityGesture(dir)){this.probeNotice=$r('app.string.a11y_probe_disconnected');return;}
    this.probeNotice=$r('app.string.a11y_probe_requested');}
  private probeTime(value:number):string{return value<=0?'-':new Date(value).toISOString().slice(11,19);}
  private copy():void{
    if(!this.controller)return;
    try{void pasteboard.getSystemPasteboard().setData(pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN,this.controller.diagnosticReport()))
      .then(()=>{this.notice=$r('app.string.eta_diagnostics_copied');}).catch(()=>{this.notice=$r('app.string.eta_diagnostics_copy_failed');});}
    catch(_){this.notice=$r('app.string.eta_diagnostics_copy_failed');}
  }
  private clear():void{this.getUIContext().showAlertDialog({title:$r('app.string.eta_diagnostics_clear_title'),message:$r('app.string.eta_diagnostics_clear_message'),
    primaryButton:{value:$r('app.string.action_cancel'),action:()=>{}},secondaryButton:{value:$r('app.string.eta_diagnostics_clear'),action:()=>{this.controller?.clearDiagnostics();this.notice='';}}});}
  @Builder private facts(){Column({space:S.Dp12}){
    ForEach(this.state.diagnostics.facts,(fact:DiagnosticFact)=>{Row({space:S.Dp16}){
      Text(fact.label).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).layoutWeight(1)
      Text(fact.value).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface')).textAlign(TextAlign.End).layoutWeight(2).copyOption(CopyOptions.LocalDevice)
    }.width('100%')},(fact:DiagnosticFact)=>fact.label+fact.value)
  }}
  @Builder private events(){Column({space:S.Dp8}){
    Text($r('app.string.eta_diagnostics_events',this.state.diagnostics.total)).fontSize(F.Body1).fontColor($r('app.color.miuix_on_surface')).width('100%')
    if(!this.state.diagnostics.lines.length){Text($r('app.string.eta_diagnostics_empty')).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary'))}
    ForEach(this.state.diagnostics.lines,(line:string)=>{Text(line).fontSize(F.Footnote1).fontFamily('monospace').fontColor($r('app.color.miuix_on_surface')).width('100%').copyOption(CopyOptions.LocalDevice)},(line:string,index:number)=>String(index)+line)
  }}
  @Builder private probeRow(row:ProbeRowParams){Row({space:S.Dp16}){
    Text(row.label).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).layoutWeight(1)
    Text(row.value).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface')).textAlign(TextAlign.End).layoutWeight(2)
  }.width('100%')}
  @Builder private a11yProbe(){Column({space:S.Dp12}){
    Text($r('app.string.a11y_probe_title')).fontSize(F.Body1).fontWeight(FontWeight.Medium).fontColor($r('app.color.miuix_on_surface')).width('100%')
    this.probeRow({label:$r('app.string.a11y_probe_state'),value:this.probe.connected?$r('app.string.a11y_probe_connected'):$r('app.string.a11y_probe_disconnected')})
    this.probeRow({label:$r('app.string.a11y_probe_bundle'),value:this.probe.foregroundBundle!==''?this.probe.foregroundBundle:'-'})
    this.probeRow({label:$r('app.string.a11y_probe_nodes'),value:String(this.probe.nodeCount)})
    this.probeRow({label:$r('app.string.a11y_probe_last_event'),value:this.probeTime(this.probe.lastEventAt)})
    this.probeRow({label:$r('app.string.a11y_probe_gesture'),value:this.probe.gesture.result})
    if(this.probe.readError!==''){this.probeRow({label:$r('app.string.a11y_probe_read_error'),value:this.probe.readError})}
    Row({space:S.Dp8}){
      EtaTextButton({text:$r('app.string.a11y_probe_refresh'),onAction:()=>this.refreshProbe(true)}).layoutWeight(1)
      EtaTextButton({text:$r('app.string.a11y_probe_request'),primary:true,onAction:()=>this.requestInjection()}).layoutWeight(1)
    }.width('100%')
    Button(A11Y_TEST_BUTTON_TEXT).id('eta-a11y-test').fontSize(F.Body2).height(S.Dp40)
      .backgroundColor($r('app.color.miuix_surface_container_high')).fontColor($r('app.color.miuix_on_surface'))
      .onClick(()=>{this.probeClicks+=1;})
    Text($r('app.string.a11y_probe_clicks',this.probeClicks)).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface'))
    if(this.probeNotice!==''){Text(this.probeNotice).fontSize(F.Footnote1).fontColor($r('app.color.eta_settings_primary'))}
  }.width('100%').id('eta-a11y-probe')}
  @Builder private content(){Column({space:S.Dp16}){
    EtaCard({insidePadding:S.Dp16,content:() => { this.facts(); }})
    EtaCard({insidePadding:S.Dp16,content:() => { this.a11yProbe(); }})
    Row({space:S.Dp8}){
      EtaTextButton({text:$r('app.string.eta_diagnostics_copy'),primary:true,onAction:()=>this.copy()}).layoutWeight(1)
      EtaTextButton({text:$r('app.string.eta_diagnostics_clear'),isEnabled:this.state.diagnostics.total>0,onAction:()=>this.clear()}).layoutWeight(1)
    }.width('100%')
    if(this.notice!==''){Text(this.notice).fontSize(F.Body2).fontColor($r('app.color.eta_settings_primary'))}
    Text($r('app.string.eta_diagnostics_hint')).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary')).width('100%')
    EtaCard({insidePadding:S.Dp16,content:() => { this.events(); }})
  }.width('100%').padding({left:S.Dp16,right:S.Dp16})}
  build(){MiuixScaffoldPage({title:$r('app.string.background_diagnostics_title'),blurEnabled:this.state.appearance.blurEnabled,progressiveBlur:this.state.appearance.topBarBlurStyle==='progressive',content:() => { this.content(); },onBack:()=>this.controller?.back()})}
}
