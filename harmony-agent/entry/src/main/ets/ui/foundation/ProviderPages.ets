import { FoundationController, ShellState, emptyShellState, ModelChoice } from '../../core/foundation/controller';
import { ModelProfile } from '../../core/profiles';
import { ModelProtocol } from '../../core/protocols';

@Component
export struct ProviderListPage {
  @Prop state: ShellState = emptyShellState();
  controller: FoundationController | undefined = undefined;
  build() {
    Scroll() {
      Column({ space: 18 }) {
        TextInput({ text: this.state.providerSearch, placeholder: '搜索服务名称或地址' }).height(46).backgroundColor('#EDEFF5').borderRadius(14)
          .onChange((value: string) => this.controller?.searchProviders(value))
        Button({ type: ButtonType.Normal }) {
          Row({ space: 14 }) {
            Text('＋').fontSize(24).fontColor('#397AF6').width(40).height(40).textAlign(TextAlign.Center).backgroundColor('#EDF0FF').borderRadius(12)
            Column({ space: 4 }) {
              Text('添加模型服务').fontSize(16).fontColor('#273247')
              Text('OpenAI 兼容、Anthropic、Gemini').fontSize(12).fontColor('#8993A5')
            }.alignItems(HorizontalAlign.Start).layoutWeight(1)
            Text('›').fontSize(24).fontColor('#A1A9B6')
          }.width('100%').padding(16)
        }.width('100%').borderRadius(18).backgroundColor('#FFFFFF').onClick(() => { void this.controller?.openProvider(); })
        Text('已配置的服务 · ' + this.state.providers.length).fontSize(12).fontColor('#8993A5').width('100%')
        if (!this.state.providers.length) { Text(this.state.providerSearch ? '没有匹配的服务' : '还没有服务，添加后即可选择聊天模型').fontSize(14).fontColor('#8993A5').padding(28).width('100%').textAlign(TextAlign.Center) }
        ForEach(this.state.providers, (profile: ModelProfile) => {
          Button({ type: ButtonType.Normal }) {
            Row({ space: 14 }) {
              Text(profile.name.slice(0, 1)).fontSize(18).fontColor('#397AF6').width(42).height(42).textAlign(TextAlign.Center).backgroundColor('#EDF0FF').borderRadius(13)
              Column({ space: 5 }) {
                Text(profile.name).fontSize(16).fontColor('#273247').width('100%')
                Text((profile.models?.length || 1) + ' 个模型 · ' + profile.baseUrl).fontSize(12).fontColor('#8993A5').maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis }).width('100%')
              }.layoutWeight(1).alignItems(HorizontalAlign.Start)
              Text('›').fontSize(24).fontColor('#A1A9B6')
            }.padding(16).width('100%')
          }.width('100%').borderRadius(18).backgroundColor('#FFFFFF').onClick(() => { void this.controller?.openProvider(profile.id); })
        }, (profile: ModelProfile): string => profile.id + profile.name + profile.models?.length)
      }.width('100%').constraintSize({ maxWidth: 840 }).padding(20).alignItems(HorizontalAlign.Start)
    }.width('100%').height('100%').align(Alignment.Top).scrollBar(BarState.Auto)
  }
}

@Component
export struct ProviderDetailPage {
  @Prop state: ShellState = emptyShellState();
  controller: FoundationController | undefined = undefined;
  @State private manualModel: string = '';
  @State private modelSearch: string = '';
  @State private modelLimit: number = 100;
  private locked(): boolean { return ['保存', '删除', '载入'].includes(this.state.editor.working); }
  private visibleModels(): string[] { return this.state.editor.draft.models.filter((id: string): boolean => id.toLowerCase().includes(this.modelSearch.toLowerCase())); }
  @Builder private label(text: string) { Text(text).fontSize(12).fontColor('#7A8598').width('100%').margin({ top: 4 }) }
  @Builder private config() {
    Column({ space: 12 }) {
      this.label('服务名称')
      TextInput({ text: this.state.editor.draft.name, placeholder: '我的模型服务' }).id('model-name').height(48).backgroundColor('#F3F5F9').borderRadius(12)
        .onChange((v: string) => this.controller?.editor.text('name', v))
      this.label('接口协议')
      Select([{ value: 'OpenAI Chat Completions' }, { value: 'OpenAI Responses' }, { value: 'Anthropic Messages' }, { value: 'Gemini' }])
        .selected(['chat_completions', 'responses', 'anthropic', 'gemini'].indexOf(this.state.editor.draft.protocol || 'chat_completions'))
        .value(this.protocolName()).width('100%').enabled(!this.state.editor.busy).onSelect((index: number) => {
          const protocols: ModelProtocol[] = ['chat_completions', 'responses', 'anthropic', 'gemini']; this.controller?.editor.protocol(protocols[index]);
        })
      this.label('API 地址')
      TextInput({ text: this.state.editor.draft.baseUrl, placeholder: 'https://api.example.com/v1' }).id('model-address').height(48).fontSize(14).backgroundColor('#F3F5F9').borderRadius(12)
        .onChange((v: string) => this.controller?.editor.text('baseUrl', v))
      if (this.state.editor.draft.baseUrl.startsWith('http://')) {
        Row() {
          Column({ space: 3 }) { Text('允许此 HTTP 服务').fontSize(14); Text('密钥与消息将以明文传输').fontSize(11).fontColor('#A45F55') }.layoutWeight(1).alignItems(HorizontalAlign.Start)
          Toggle({ type: ToggleType.Switch, isOn: this.state.editor.draft.allowInsecureHttp }).selectedColor('#397AF6').enabled(!this.state.editor.busy).onChange((v: boolean) => this.controller?.editor.flag('allowInsecureHttp', v))
        }.width('100%')
      }
      this.label('API Key')
      TextInput({ text: this.state.editor.draft.apiKey, placeholder: '服务密钥（支持无密钥的本地服务）' }).id('model-key').type(InputType.Password).height(48).backgroundColor('#F3F5F9').borderRadius(12)
        .onChange((v: string) => this.controller?.editor.text('apiKey', v))
      this.label('默认模型')
      TextInput({ text: this.state.editor.draft.model, placeholder: '填写模型 ID，或在“模型”页获取' }).id('model-id').height(48).backgroundColor('#F3F5F9').borderRadius(12)
        .onChange((v: string) => this.controller?.editor.text('model', v))
      Button(this.state.editor.busy ? '连接中…' : '测试连接并获取模型').id('model-test').height(44).width('100%').fontSize(14).fontColor('#397AF6').backgroundColor('#EBEEFC')
        .enabled(!this.state.editor.busy).onClick(() => { void this.controller?.probeProvider(true); })
      if (this.state.editor.connection) { Text(this.state.editor.connection).fontSize(12).fontColor('#39806A').width('100%') }
      Divider().color('#EEF0F5')
      Row() { Text('安全保存密钥').fontSize(14).layoutWeight(1); Toggle({ type: ToggleType.Switch, isOn: this.state.editor.draft.rememberKey }).selectedColor('#397AF6').enabled(!this.state.editor.busy).onChange((v: boolean) => this.controller?.editor.flag('rememberKey', v)) }.width('100%')
      Text('开启后在本机安全存储中保存；关闭则重启后需要重新填写。').fontSize(11).lineHeight(18).fontColor('#8993A5').width('100%')
      Row() { Text('流式输出').fontSize(14).layoutWeight(1); Toggle({ type: ToggleType.Switch, isOn: this.state.editor.draft.streaming }).selectedColor('#397AF6').enabled(!this.state.editor.busy).onChange((v: boolean) => this.controller?.editor.flag('streaming', v)) }.width('100%')
    }.padding(18).width('100%').backgroundColor('#FFFFFF').borderRadius(18).enabled(!this.locked())
  }
  private protocolName(): string { const names = ['OpenAI Chat Completions', 'OpenAI Responses', 'Anthropic Messages', 'Gemini']; return names[['chat_completions', 'responses', 'anthropic', 'gemini'].indexOf(this.state.editor.draft.protocol || 'chat_completions')] || names[0]; }
  @Builder private models() {
    Column({ space: 14 }) {
      Button(this.state.editor.busy ? '获取中…' : '从 API 获取模型').id('model-fetch').height(44).fontSize(14).width('100%').fontColor('#397AF6').backgroundColor('#EBEEFC').enabled(!this.state.editor.busy)
        .onClick(() => { void this.controller?.probeProvider(false); })
      Row({ space: 8 }) {
        TextInput({ text: this.manualModel, placeholder: '手动添加模型 ID' }).layoutWeight(1).height(44).fontSize(14).onChange((v: string) => this.manualModel = v)
        Button('添加').height(40).fontSize(13).fontColor('#397AF6').backgroundColor('#EBEEFC').enabled(!this.state.editor.busy && Boolean(this.manualModel.trim())).onClick(() => { this.controller?.editor.addModel(this.manualModel); this.manualModel = ''; })
      }.width('100%')
      TextInput({ text: this.modelSearch, placeholder: '搜索已添加的模型' }).height(44).onChange((v: string) => { this.modelSearch = v; this.modelLimit = 100; })
      Text('已添加 ' + this.state.editor.draft.models.length + ' 个模型 · 点选可设为默认').fontSize(12).fontColor('#8993A5').width('100%')
      ForEach(this.visibleModels().slice(0, this.modelLimit), (id: string) => {
        Row({ space: 10 }) {
          Text(this.state.editor.draft.model === id ? '✓' : '○').fontSize(18).fontColor('#397AF6')
          Text(id).fontSize(14).fontColor('#273247').layoutWeight(1).maxLines(2).onClick(() => this.controller?.editor.text('model', id))
          Button('移除').fontSize(11).height(32).fontColor('#8993A5').backgroundColor(Color.Transparent).onClick(() => this.controller?.editor.removeModel(id))
        }.width('100%').padding(12).backgroundColor('#FFFFFF').borderRadius(12).enabled(!this.state.editor.busy)
      }, (id: string): string => id)
      if (this.visibleModels().length > this.modelLimit) { Button('显示更多模型').height(36).fontSize(13).fontColor('#397AF6').backgroundColor(Color.Transparent).onClick(() => this.modelLimit += 100) }
      if (!this.state.editor.draft.models.length) { Text('获取目录后选择要添加的模型，也可以手动输入。').fontSize(13).fontColor('#8993A5').width('100%').padding(18) }
    }.width('100%')
  }
  build() {
    Column({ space: 12 }) {
      Row({ space: 6 }) {
        Button('配置').layoutWeight(1).height(38).fontSize(14).fontColor(this.state.editor.tab === 'config' ? '#397AF6' : '#8993A5').backgroundColor(this.state.editor.tab === 'config' ? '#FFFFFF' : Color.Transparent).borderRadius(10).onClick(() => this.controller?.editor.tab('config'))
        Button('模型').layoutWeight(1).height(38).fontSize(14).fontColor(this.state.editor.tab === 'models' ? '#397AF6' : '#8993A5').backgroundColor(this.state.editor.tab === 'models' ? '#FFFFFF' : Color.Transparent).borderRadius(10).onClick(() => this.controller?.editor.tab('models'))
      }.padding(4).width('100%').backgroundColor('#E9ECF3').borderRadius(14)
      Scroll() {
        Column({ space: 14 }) {
          if (this.state.editor.tab === 'config') { this.config() } else { this.models() }
          if (this.state.editor.error) { Text(this.state.editor.error).fontSize(13).lineHeight(21).fontColor('#A45F55').width('100%').copyOption(CopyOptions.LocalDevice) }
          if (this.state.editor.message) { Text(this.state.editor.message).fontSize(13).lineHeight(21).fontColor('#687594').width('100%') }
          if (this.state.editor.draft.id) {
            Button('删除此服务').fontSize(13).height(36).fontColor('#A45F55').backgroundColor(Color.Transparent).enabled(!this.state.editor.busy)
              .onClick(() => this.getUIContext().showAlertDialog({ title: '删除模型服务', message: '删除配置与保存的密钥，已有对话仍会保留。', primaryButton: { value: '取消', action: (): void => {} }, secondaryButton: { value: '删除', action: (): void => { void this.controller?.deleteProvider(); } } }))
          }
        }.width('100%').padding({ bottom: 20 })
      }.layoutWeight(1).width('100%').align(Alignment.Top).scrollBar(BarState.Auto)
      Button(this.state.editor.working === '保存' ? '正在保存…' : '保存服务').id('model-save').height(48).fontSize(15).width('100%').borderRadius(14).backgroundColor('#397AF6').fontColor('#FFFFFF')
        .enabled(!this.state.editor.busy).onClick(() => { void this.controller?.editor.save(); })
    }.width('100%').height('100%').constraintSize({ maxWidth: 840 }).padding({ left: 20, right: 20, top: 8, bottom: 16 })
  }
}

@Component
export struct ModelSelectionSheet {
  @Prop state: ShellState = emptyShellState();
  controller: FoundationController | undefined = undefined;
  @State private limit: number = 100;
  private candidates(): string[] { return this.state.editor.catalog.filter((id: string): boolean => id.toLowerCase().includes(this.state.editor.query.toLowerCase())); }
  build() {
    Column({ space: 14 }) {
      Row() {
        Text(this.state.sheet === 'catalog' ? '添加远端模型' : '选择聊天模型').fontSize(22).fontWeight(FontWeight.Medium).fontColor('#273247').layoutWeight(1)
        Button('关闭').fontSize(13).height(34).fontColor('#8993A5').backgroundColor(Color.Transparent).onClick(() => this.controller?.back())
      }.width('100%')
      TextInput({ text: this.state.sheet === 'catalog' ? this.state.editor.query : this.state.modelQuery, placeholder: '搜索模型' }).height(44)
        .onChange((v: string) => { this.limit = 100; if (this.state.sheet === 'catalog') this.controller?.editor.search(v); else this.controller?.searchModels(v); })
      if (this.state.sheet === 'catalog') {
        Row() { Text('可添加 ' + this.state.editor.catalog.length + ' 个模型').fontSize(12).fontColor('#8993A5').layoutWeight(1); Button('全选当前结果 / 取消').height(32).fontSize(12).fontColor('#397AF6').backgroundColor(Color.Transparent).onClick(() => this.controller?.editor.toggleVisible()) }.width('100%')
      }
      Scroll() {
        Column({ space: 6 }) {
          if (this.state.sheet === 'catalog') {
            ForEach(this.candidates().slice(0, this.limit), (id: string) => {
              Row({ space: 10 }) { Text(id).fontSize(15).layoutWeight(1).maxLines(3); Text(this.state.editor.selected.includes(id) ? '✓' : '○').fontSize(20).fontColor('#397AF6') }
                .padding(14).width('100%').borderRadius(12).backgroundColor(this.state.editor.selected.includes(id) ? '#EBEEFC' : '#FFFFFF').onClick(() => this.controller?.editor.toggle(id))
            }, (id: string): string => id)
          } else {
            ForEach(this.state.choices.slice(0, this.limit), (choice: ModelChoice) => {
              Row({ space: 10 }) {
                Column({ space: 5 }) { Text(choice.model).fontSize(15).fontColor('#273247').width('100%'); Text(choice.provider).fontSize(11).fontColor('#8993A5').width('100%') }.layoutWeight(1)
                if (this.state.chat.model === choice.model && this.state.chat.profileId === choice.profileId) { Text('✓').fontColor('#397AF6') }
              }.padding(14).width('100%').backgroundColor('#FFFFFF').borderRadius(12).enabled(!this.state.busy).onClick(() => { void this.controller?.useModel(choice); })
            }, (choice: ModelChoice): string => choice.key)
          }
          if (this.state.sheet === 'catalog' ? this.candidates().length > this.limit : this.state.choices.length > this.limit) { Button('显示更多').height(36).fontSize(13).fontColor('#397AF6').backgroundColor(Color.Transparent).onClick(() => this.limit += 100) }
          if (this.state.sheet === 'models' && !this.state.choices.length) { Text('没有匹配的模型，可先添加模型服务').fontSize(14).fontColor('#8993A5').padding(30) }
        }.width('100%')
      }.layoutWeight(1).width('100%').align(Alignment.Top).scrollBar(BarState.Auto)
      if (this.state.sheet === 'catalog') {
        Button('添加所选 · ' + this.state.editor.selected.length).height(46).fontSize(14).width('100%').backgroundColor('#397AF6').enabled(this.state.editor.selected.length > 0).onClick(() => this.controller?.importModels())
      } else {
        Button('管理模型服务').height(42).fontSize(14).fontColor('#397AF6').backgroundColor('#EBEEFC').width('100%').onClick(() => this.controller?.navigate('providers'))
      }
    }.height('100%').width('100%').padding(20).backgroundColor('#F7F8FC')
  }
}
