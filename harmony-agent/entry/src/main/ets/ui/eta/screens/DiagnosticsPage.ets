// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/SettingsScreen.kt (diagnostic entry),
// ui/components/EtaCard.kt @ 3b1794d; Harmony's M0 diagnostic data/controls are retained.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { pasteboard } from '@kit.BasicServicesKit';
import { FoundationController, ShellState, emptyShellState } from '../../../core/foundation/controller';
import { DiagnosticFact } from '../../../core/diagnostics';
import { EtaComponentSizes as S, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { MiuixScaffoldPage } from '../components/MiuixScaffoldPage';
import { EtaCard } from '../components/EtaCard';
import { EtaTextButton } from '../components/EtaControls';
@Component
export struct DiagnosticsPage {
  @Prop state:ShellState=emptyShellState();
  controller:FoundationController|undefined=undefined;
  @State private notice:ResourceStr='';
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
  @Builder private content(){Column({space:S.Dp16}){
    EtaCard({insidePadding:S.Dp16,content:() => { this.facts(); }})
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
