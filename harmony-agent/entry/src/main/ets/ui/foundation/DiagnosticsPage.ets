import { pasteboard } from '@kit.BasicServicesKit';
import { FoundationController, ShellState, emptyShellState } from '../../core/foundation/controller';
import { DiagnosticFact } from '../../core/diagnostics';

/** Device facts and recent events. The report contains no keys, URL paths or message text. */
@Component
export struct DiagnosticsPage {
  @Prop state: ShellState = emptyShellState();
  controller: FoundationController | undefined = undefined;
  @State private notice: string = '';
  private copy(): void {
    if (!this.controller) return;
    const report = this.controller.diagnosticReport();
    try {
      pasteboard.getSystemPasteboard().setData(pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, report))
        .then((): void => { this.notice = '报告已复制，可以直接粘贴给开发者'; })
        .catch((): void => { this.notice = '复制失败，请长按下方事件文字手动复制'; });
    } catch (_) { this.notice = '复制失败，请长按下方事件文字手动复制'; }
  }
  private clear(): void {
    this.getUIContext().showAlertDialog({ title: '清空诊断记录', message: '只清空本页的事件记录，不影响对话和模型服务。',
      primaryButton: { value: '取消', action: (): void => {} },
      secondaryButton: { value: '清空', action: (): void => { this.controller?.clearDiagnostics(); this.notice = '已清空'; } } });
  }
  build() {
    Scroll() {
      Column({ space: 16 }) {
        Column({ space: 10 }) {
          ForEach(this.state.diagnostics.facts, (fact: DiagnosticFact) => {
            Row({ space: 12 }) {
              Text(fact.label).fontSize(13).fontColor('#8993A5').width(76)
              Text(fact.value).fontSize(13).fontColor('#273247').layoutWeight(1).textAlign(TextAlign.End).copyOption(CopyOptions.LocalDevice)
            }.width('100%')
          }, (fact: DiagnosticFact): string => fact.label + fact.value)
        }.padding(18).width('100%').backgroundColor('#FFFFFF').borderRadius(18)
        Row({ space: 10 }) {
          Button('复制报告').height(44).fontSize(15).layoutWeight(1).backgroundColor('#397AF6').onClick(() => this.copy())
          Button('清空记录').height(44).fontSize(15).layoutWeight(1).fontColor('#58657D').backgroundColor('#E7ECFA')
            .enabled(this.state.diagnostics.total > 0).onClick(() => this.clear())
        }.width('100%')
        if (this.notice) { Text(this.notice).fontSize(13).fontColor('#397AF6').width('100%') }
        Text('报告会自动隐去 API Key、授权信息、网址路径和消息内容。出现问题后复制报告，连同你的操作步骤一起发给开发者。')
          .fontSize(12).lineHeight(19).fontColor('#8993A5').width('100%')
        Column({ space: 8 }) {
          Text('最近事件 · ' + this.state.diagnostics.total + ' 条（最新在上）').fontSize(14).fontColor('#273247').width('100%')
          if (!this.state.diagnostics.lines.length) { Text('暂无记录').fontSize(13).fontColor('#8993A5').padding({ top: 8, bottom: 8 }) }
          ForEach(this.state.diagnostics.lines, (line: string) => {
            Text(line).fontSize(12).lineHeight(18).fontFamily('monospace').fontColor('#344460').width('100%').copyOption(CopyOptions.LocalDevice)
          }, (line: string, index: number): string => index + '-' + line)
        }.padding(18).width('100%').backgroundColor('#FFFFFF').borderRadius(18).alignItems(HorizontalAlign.Start)
      }.width('100%').constraintSize({ maxWidth: 840 }).padding(20)
    }.width('100%').height('100%').align(Alignment.Top)
  }
}
