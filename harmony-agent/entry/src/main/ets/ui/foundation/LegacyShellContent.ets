// Temporary 0.9 content extracted unchanged for E03; E07/E05 replace these builders.
import { FoundationController, ShellState, emptyShellState } from '../../core/foundation/controller';
import { ConversationSummary } from '../../core/conversations';
@Component
export struct LegacyConversationPane {
  @Prop state: ShellState = emptyShellState();
  controller: FoundationController | undefined = undefined;
  private viewportWidth: number = 0;
  @State private renameId: string = '';
  @State private renameTitle: string = '';
  private removeConversation(item: ConversationSummary): void {
    this.getUIContext().showAlertDialog({ title: '删除对话', message: '“' + item.title + '”的聊天记录将被删除。',
      primaryButton: { value: '取消', action: (): void => {} },
      secondaryButton: { value: '删除', action: (): void => { void this.controller?.removeConversation(item.id); } } });
  }
  @Builder private sidebar() {
    Column({ space: 16 }) {
      Row() {
        Text('随身助手').fontSize(22).fontWeight(FontWeight.Medium).fontColor('#273247').layoutWeight(1)
        if (this.viewportWidth < 900) { Button({ type: ButtonType.Circle }) { Image($r('app.media.ic_close')).width(20).height(20) }.padding(0).width(36).height(36).backgroundColor(Color.Transparent).accessibilityText('关闭侧栏').onClick(() => this.controller?.drawer(false)) }
      }.width('100%')
      Button('＋  新建对话').fontSize(15).height(46).width('100%').fontColor('#397AF6').backgroundColor('#E7ECFA').borderRadius(14)
        .enabled(!this.state.busy).onClick(() => { void this.controller?.newConversation(); })
      TextInput({ text: this.state.search, placeholder: '搜索对话' }).height(42).fontSize(14).backgroundColor('#EDEFF5').borderRadius(12)
        .onChange((value: string) => this.controller?.searchConversations(value))
      Text('最近对话').fontSize(11).fontColor('#929BAD').width('100%')
      Scroll() {
        Column({ space: 5 }) {
          if (!this.state.conversations.length) { Text('没有匹配的对话').fontSize(13).fontColor('#8993A5').padding(20) }
          ForEach(this.state.conversations, (item: ConversationSummary) => {
            Column({ space: 5 }) {
              Row() {
                Text(item.title).fontSize(14).fontColor('#344059').maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis }).layoutWeight(1)
                Button({ type: ButtonType.Normal }) { Image($r('app.media.ic_more')).width(18).height(18) }.height(26).width(32).padding(0).backgroundColor(Color.Transparent).accessibilityText('对话菜单')
                  .bindMenu([{ value: '重命名', action: (): void => { this.renameId = item.id; this.renameTitle = item.title; } }, { value: '删除', action: (): void => this.removeConversation(item) }])
              }.width('100%')
              Text(item.preview || '开始新的对话').fontSize(11).fontColor('#929BAD').maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis }).width('100%')
            }.padding(12).width('100%').borderRadius(12).backgroundColor(item.id === this.state.chat.id ? '#E5EAF7' : Color.Transparent)
              .enabled(!this.state.busy).onClick(() => { void this.controller?.selectConversation(item.id); })
          }, (item: ConversationSummary): string => item.id + item.title + item.updatedAt)
        }.width('100%')
      }.layoutWeight(1).width('100%').align(Alignment.Top).scrollBar(BarState.Auto)
      if (this.renameId) {
        Column({ space: 8 }) {
          TextInput({ text: this.renameTitle, placeholder: '对话名称' }).maxLength(80).height(40).onChange((v: string) => this.renameTitle = v)
          Row({ space: 8 }) {
            Button('取消').height(32).fontSize(12).onClick(() => this.renameId = '')
            Button('保存').height(32).fontSize(12).enabled(Boolean(this.renameTitle.trim())).onClick(() => { void this.controller?.rename(this.renameId, this.renameTitle); this.renameId = ''; })
          }
        }.width('100%')
      }
      Divider().color('#E3E7F0')
      Button({ type: ButtonType.Normal }) {
        Row({ space: 12 }) { Image($r('app.media.ic_models')).width(20).height(20); Text('模型服务').fontSize(14).fontColor('#58657D'); Blank(); Text('›').fontColor('#8993A5') }.width('100%').padding(10)
      }.width('100%').height(42).backgroundColor(Color.Transparent).borderRadius(12).onClick(() => this.controller?.navigate('providers'))
      Button({ type: ButtonType.Normal }) {
        Row({ space: 12 }) { Image($r('app.media.ic_device')).width(20).height(20); Text('设置').fontSize(14).fontColor('#58657D'); Blank(); Text('›').fontColor('#8993A5') }.width('100%').padding(10)
      }.width('100%').height(42).backgroundColor(Color.Transparent).borderRadius(12).onClick(() => this.controller?.navigate('settings'))
    }.padding(18).width('100%').height('100%').backgroundColor('#F1F3F8').alignItems(HorizontalAlign.Start)
  }
  build() { this.sidebar() }
}
@Component
export struct LegacySettingsContent {
  @Prop state: ShellState = emptyShellState();
  controller: FoundationController | undefined = undefined;
  @Builder private settingsPage() {
    Scroll() {
      Column({ space: 20 }) {
        Column({ space: 12 }) {
          Image($r('app.media.ic_spark')).width(40).height(40)
          Text('随身助手').fontSize(22).fontWeight(FontWeight.Medium).fontColor('#273247')
          Text(this.state.appVersion ? this.state.appVersion + ' · 交互框架预览' : '交互框架预览').fontSize(12).fontColor('#8993A5')
        }.width('100%').padding(26).backgroundColor('#FFFFFF').borderRadius(18)
        Button({ type: ButtonType.Normal }) {
          Row() { Column({ space: 5 }) { Text('模型服务').fontSize(16).fontColor('#273247'); Text('管理 API、密钥和聊天模型').fontSize(12).fontColor('#8993A5') }.alignItems(HorizontalAlign.Start); Blank(); Text('›').fontSize(22).fontColor('#8993A5') }.width('100%').padding(18)
        }.width('100%').backgroundColor('#FFFFFF').borderRadius(18).onClick(() => this.controller?.navigate('providers'))
        Button({ type: ButtonType.Normal }) {
          Row() { Column({ space: 5 }) { Text('诊断').fontSize(16).fontColor('#273247'); Text('设备信息和最近事件，可复制报告').fontSize(12).fontColor('#8993A5') }.alignItems(HorizontalAlign.Start); Blank(); Text('›').fontSize(22).fontColor('#8993A5') }.width('100%').padding(18)
        }.width('100%').backgroundColor('#FFFFFF').borderRadius(18).onClick(() => this.controller?.openDiagnostics())
        Column({ space: 12 }) {
          Text('这版先把对话做好').fontSize(16).fontColor('#273247').width('100%')
          Text('聊天、会话切换和模型服务已接入。手机自动操作、后台服务和其他扩展暂不提供入口，后续逐项接回。').fontSize(14).lineHeight(23).fontColor('#8993A5').width('100%')
          Text('请保持助手在前台。离开应用会停止当前回复，已经收到的内容会保留。').fontSize(13).lineHeight(21).fontColor('#8993A5').width('100%')
        }.padding(18).width('100%').backgroundColor('#FFFFFF').borderRadius(18)
      }.width('100%').constraintSize({ maxWidth: 840 }).padding(20)
    }.width('100%').height('100%').align(Alignment.Top)
  }
  build() { this.settingsPage() }
}
