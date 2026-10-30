import { FoundationController, ChatView, MessageRow, emptyShellState } from '../../core/foundation/controller';
import { MessageBody } from '../../components/MessageBody';

@Observed
class MessageItem {
  data: MessageRow;
  constructor(data: MessageRow) { this.data = data; }
}

@Component
struct ChatMessage {
  @ObjectLink item: MessageItem;
  controller: FoundationController | undefined = undefined;
  @State private reasoningOpen: boolean = false;
  build() {
    Column({ space: 18 }) {
      Row() {
        Blank().width('12%')
        Text(this.item.data.request).fontSize(16).lineHeight(25).fontColor('#222B3C')
          .padding({ left: 18, right: 18, top: 13, bottom: 13 }).backgroundColor('#E9EDF8').borderRadius(20)
          .copyOption(CopyOptions.LocalDevice).constraintSize({ maxWidth: '88%' })
      }.width('100%').justifyContent(FlexAlign.End)
      Column({ space: 10 }) {
        Row({ space: 8 }) {
          Image($r('app.media.ic_spark')).width(20).height(20)
          Text('随身助手').fontSize(12).fontColor('#758096').layoutWeight(1)
          if (this.item.data.active) { LoadingProgress().width(16).height(16).color('#397AF6') }
        }.width('100%')
        if (this.item.data.reasoning) {
          Column({ space: 8 }) {
            Text(this.reasoningOpen ? '收起思考过程 ∧' : '思考过程 ∨').fontSize(12).fontColor('#758096')
              .onClick(() => this.reasoningOpen = !this.reasoningOpen)
            if (this.reasoningOpen) { Text(this.item.data.reasoning).fontSize(14).lineHeight(23).fontColor('#758096').copyOption(CopyOptions.LocalDevice).width('100%') }
          }.width('100%').alignItems(HorizontalAlign.Start).padding(12).backgroundColor('#F0F2F7').borderRadius(12)
        }
        if (this.item.data.reply) {
          MessageBody({ content: this.item.data.reply })
        } else {
          Text(this.item.data.active ? '正在思考…' : '本轮没有收到回复').fontSize(15).fontColor('#8790A1').width('100%')
        }
        if (this.item.data.notice) { Text(this.item.data.notice).fontSize(12).lineHeight(19).fontColor('#A45F55').width('100%').copyOption(CopyOptions.LocalDevice) }
        if (!this.item.data.active) {
          Row() {
            Text(this.item.data.model).fontSize(11).fontColor('#929AAA').layoutWeight(1).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
            Button('编辑重发').fontSize(12).height(32).fontColor('#6C7790').backgroundColor(Color.Transparent)
              .onClick(() => this.controller?.reuse(this.item.data.request))
          }.width('100%')
        }
      }.width('100%').alignItems(HorizontalAlign.Start)
    }.width('100%').padding({ top: 12, bottom: 24 })
  }
}

@Component
struct ChatComposer {
  @Prop @Watch('syncInput') chat: ChatView = emptyShellState().chat;
  @Prop busy: boolean = false;
  controller: FoundationController | undefined = undefined;
  @State private text: string = '';
  private identity: string = '';
  private version: number = -1;
  aboutToAppear(): void { this.syncInput(); }
  private syncInput(): void {
    if (this.identity !== this.chat.id || this.version !== this.chat.inputVersion) {
      this.identity = this.chat.id; this.version = this.chat.inputVersion; this.text = this.chat.input;
    }
  }
  build() {
    Column({ space: 6 }) {
      TextArea({ text: this.text, placeholder: '发消息，开始一段对话…' }).id('foundation-composer')
        .height(Math.min(116, 52 + Math.floor(this.text.length / 32) * 20)).fontSize(16).maxLength(8000)
        .padding({ top: 10, left: 10, right: 10, bottom: 4 }).backgroundColor(Color.Transparent)
        .onChange((value: string) => { this.text = value; this.controller?.draft(value); })
      Row({ space: 12 }) {
        Button({ type: ButtonType.Normal }) {
          Row({ space: 6 }) {
            Image($r('app.media.ic_models')).width(17).height(17)
            Text(this.chat.model || '选择模型').fontSize(12).fontColor('#58657F').maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis }).layoutWeight(1)
            Image($r('app.media.ic_chevron_down')).width(16).height(16)
          }.padding({ left: 10, right: 10 })
        }.height(36).width('72%').constraintSize({ maxWidth: 280 }).borderRadius(12).backgroundColor('#F0F2F7')
          .enabled(!this.chat.active && !this.busy).onClick(() => this.controller?.chooseModel())
        if (this.chat.active) {
          Button({ type: ButtonType.Circle }) { Image($r('app.media.ic_stop')).width(20).height(20) }.width(42).height(42).padding(0).backgroundColor('#397AF6')
            .accessibilityText('停止生成').onClick(() => this.controller?.stop())
        } else {
          Button({ type: ButtonType.Circle }) { Image($r('app.media.ic_send')).width(24).height(24) }.width(42).height(42).padding(0).backgroundColor('#397AF6')
            .accessibilityText('发送消息').enabled(!this.busy && this.text.trim().length > 0)
            .onClick(() => { this.controller?.draft(this.text); void this.controller?.send(); })
        }
      }.width('100%').justifyContent(FlexAlign.SpaceBetween).padding({ left: 4, right: 4, bottom: 4 })
    }.padding(8).width('100%').backgroundColor('#FFFFFF').borderRadius(24)
      .shadow({ radius: 14, color: '#0A17294B', offsetX: 0, offsetY: 2 })
  }
}

@Component
export struct ChatPage {
  @Prop @Watch('update') chat: ChatView = emptyShellState().chat;
  @Prop busy: boolean = false;
  controller: FoundationController | undefined = undefined;
  @State private rows: MessageItem[] = [];
  @State private following: boolean = true;
  @State private limit: number = 30;
  @State private contentHeight: number = 480;
  private identity: string = '';
  private scroller: Scroller = new Scroller();
  private timer: number = -1;
  aboutToAppear(): void { this.update(); }
  aboutToDisappear(): void { clearTimeout(this.timer); this.storeViewport(); }
  private storeViewport(): void { try { this.controller?.viewport(this.identity, this.scroller.currentOffset().yOffset, this.following); } catch (_) {} }
  private update(): void {
    const switched = this.identity !== this.chat.id;
    if (switched) { this.identity = this.chat.id; this.following = this.chat.followBottom; this.limit = 30; }
    const previous = this.rows;
    this.rows = this.chat.rows.slice(-this.limit).map((data: MessageRow): MessageItem => {
      const item = previous.find((row: MessageItem): boolean => row.data.id === data.id);
      if (item) { if (JSON.stringify(item.data) !== JSON.stringify(data)) item.data = data; return item; }
      return new MessageItem(data);
    });
    clearTimeout(this.timer);
    const id = this.chat.id;
    this.timer = setTimeout((): void => {
      if (this.chat.id !== id) return;
      try { if (this.following) this.scroller.scrollEdge(Edge.Bottom); else if (switched) this.scroller.scrollTo({ xOffset: 0, yOffset: this.chat.scroll }); } catch (_) {}
    }, 35);
  }
  @Builder private suggestion(icon: Resource, title: string, text: string) {
    Button({ type: ButtonType.Normal }) {
      Column({ space: 12 }) {
        Image(icon).width(24).height(24)
        Text(title).fontSize(15).fontColor('#273247').maxLines(2)
      }.width('100%').alignItems(HorizontalAlign.Start).padding(16)
    }.layoutWeight(1).height(94).backgroundColor(Color.Transparent)
      .border({ width: 1, color: '#E8EBF1' }).borderRadius(18).onClick(() => this.controller?.reuse(text))
  }
  build() {
    Column({ space: 8 }) {
      Scroll(this.scroller) {
        Column() {
          if (!this.rows.length) {
            Column({ space: 14 }) {
              Text('有什么可以帮你？').fontSize(25).fontWeight(FontWeight.Medium).fontColor('#273247').margin({ bottom: 20 })
              Row({ space: 12 }) {
                this.suggestion($r('app.media.ic_write'), '写点内容', '帮我写一段内容：')
                this.suggestion($r('app.media.ic_plan'), '整理思路', '帮我整理下面的想法，给出清晰的行动步骤：')
              }.width('100%')
              Row({ space: 12 }) {
                this.suggestion($r('app.media.ic_translate'), '翻译文字', '请翻译下面的文字，并解释其中不容易理解的表达：')
                this.suggestion($r('app.media.ic_explain'), '解释一个概念', '请用通俗的语言和例子解释这个概念：')
              }.width('100%')
              if (!this.chat.model) {
                Button('添加模型服务').height(42).fontSize(14).fontColor('#397AF6').backgroundColor('#E9EDFA')
                  .onClick(() => this.controller?.navigate('providers'))
              }
            }.width('100%').constraintSize({ maxWidth: 600 }).padding({ top: 24, bottom: 44 })
          }
          if (this.chat.rows.length > this.limit) {
            Button('加载更早的消息').height(36).fontSize(12).fontColor('#758096').backgroundColor(Color.Transparent).onClick(() => { this.limit += 30; this.update(); })
          }
          ForEach(this.rows, (item: MessageItem) => { ChatMessage({ item: item, controller: this.controller }) }, (item: MessageItem): string => item.data.id)
        }.width('100%').constraintSize({ maxWidth: 840, minHeight: this.contentHeight }).padding({ left: 18, right: 18 })
          .justifyContent(this.rows.length ? FlexAlign.Start : FlexAlign.Center)
      }.layoutWeight(1).width('100%').scrollBar(BarState.Auto)
        .onAreaChange((_old: Area, area: Area) => this.contentHeight = Number(area.height))
        .onScrollFrameBegin((offset: number, scrollState: ScrollState) => { if (scrollState === ScrollState.Scroll && offset < 0) this.following = false; return { offsetRemain: offset }; })
        .onReachEnd(() => this.following = true).onScrollStop(() => this.storeViewport())
      if (!this.following) {
        Button('↓ 回到最新消息').height(30).fontSize(12).fontColor('#397AF6').backgroundColor('#E9EDFA').onClick(() => { this.following = true; this.scroller.scrollEdge(Edge.Bottom); })
      }
      Column() { ChatComposer({ chat: this.chat, busy: this.busy, controller: this.controller }) }
        .width('100%').constraintSize({ maxWidth: 840 }).padding({ left: 14, right: 14, bottom: 10 })
    }.width('100%').height('100%')
  }
}
