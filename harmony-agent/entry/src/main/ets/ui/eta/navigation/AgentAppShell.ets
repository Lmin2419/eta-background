// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentAppShell.kt, AgentAppRoot.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { FoundationController, ShellState, emptyShellState } from '../../../core/foundation/controller';
import { AppRouteName, encodeRoute } from '../../../core/eta/routes';
import { EtaPageDefaults as P, EtaComponentSizes as S, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { ChatPage } from '../../foundation/ChatPage';
import { ProviderListPage, ProviderDetailPage, ModelSelectionSheet } from '../../foundation/ProviderPages';
import { DiagnosticsPage } from '../../foundation/DiagnosticsPage';
import { LegacyConversationPane, LegacySettingsContent } from '../../foundation/LegacyShellContent';
import { ConversationSidePaneScaffold } from '../components/ConversationSidePaneScaffold';
import { AgentTopBar } from './AgentTopBar';

@Component
export struct AgentAppShell {
  @Prop @Watch('sync') state: ShellState = emptyShellState();
  controller: FoundationController | undefined = undefined;
  private pathStack: NavPathStack = new NavPathStack();
  private pathKey: string = '';
  private syncing: boolean = false;
  @State private widthVp: number = S.Dp0;
  @State private sheetVisible: boolean = false;
  aboutToAppear(): void { this.sync(); }
  private sync(): void {
    this.sheetVisible = this.state.sheet !== 'none';
    const routes = this.state.routes.slice(1);
    const key = routes.map(encodeRoute).join('\n') + (this.state.screen === 'diagnostics' ? '\nHarmonyDiagnostics' : '');
    if (key === this.pathKey) return;
    this.pathKey = key;
    this.syncing = true;
    const existing = this.pathStack.getPathStack();
    const paths: NavPathInfo[] = routes.map(route => {
      const identity = encodeRoute(route);
      const retained = existing.find(path => path.name === route.name && path.param === identity);
      return retained || new NavPathInfo(route.name, identity, () => { this.nativePop(identity); });
    });
    if (this.state.screen === 'diagnostics') paths.push(new NavPathInfo('HarmonyDiagnostics', '', () => { this.nativePop('HarmonyDiagnostics'); }));
    this.pathStack.setPathStack(paths);
    this.syncing = false;
  }
  private nativePop(identity: string): void {
    if (this.syncing) return;
    this.pathKey = '';
    const route = this.state.routes[this.state.routes.length - 1];
    if (identity === 'HarmonyDiagnostics' ? this.state.screen === 'diagnostics' : route !== undefined && encodeRoute(route) === identity) this.controller?.back();
  }
  @Builder private error() {
    if (this.state.error) {
      Row({ space: S.Dp8 }) {
        Text(this.state.error).fontSize(F.Body2).fontColor($r('app.color.miuix_error')).layoutWeight(1).copyOption(CopyOptions.LocalDevice)
        Button($r('app.string.action_close')).backgroundColor($r('app.color.eta_transparent')).fontColor($r('app.color.miuix_error')).onClick(() => this.controller?.dismissError())
      }.width('100%').padding(S.Dp12)
    }
  }
  @Builder private homeContent() {
    Column() {
      AgentTopBar({ route: AppRouteName.Home, isWide: this.widthVp >= P.WideScreenMinWidth, isEnabled: !this.state.busy,
        onOpenPane: () => this.controller?.drawer(true), onNewConversation: () => { void this.controller?.newConversation(); } })
      this.error()
      Column() {
        if (this.state.ready) { ChatPage({ chat: this.state.chat, busy: this.state.busy, controller: this.controller }) }
        else {
          Column({ space: S.Dp16 }) {
            if (this.state.busy) { LoadingProgress().width(S.Dp32).height(S.Dp32).color($r('app.color.miuix_primary')); Text($r('app.string.conversation_loading')).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')) }
            else { Button($r('app.string.action_retry')).onClick(() => { void this.controller?.initialize(); }) }
          }.width('100%').height('100%').justifyContent(FlexAlign.Center)
        }
      }.width('100%').layoutWeight(1)
    }.width('100%').height('100%').backgroundColor($r('app.color.miuix_background'))
  }
  @Builder private pane() { LegacyConversationPane({ state: this.state, controller: this.controller }) }
  @Builder private destination(name: string) {
    NavDestination() {
      Column() {
        AgentTopBar({ route: name === 'HarmonyDiagnostics' ? AppRouteName.Settings : name as AppRouteName,
          diagnostics: name === 'HarmonyDiagnostics', isWide: this.widthVp >= P.WideScreenMinWidth, onBack: () => this.controller?.back() })
        this.error()
        Column() {
          if (name === AppRouteName.ModelProviders) { ProviderListPage({ state: this.state, controller: this.controller }) }
          else if (name === AppRouteName.ModelProviderDetail || name === AppRouteName.ModelProviderNew) { ProviderDetailPage({ state: this.state, controller: this.controller }) }
          else if (name === 'HarmonyDiagnostics') { DiagnosticsPage({ state: this.state, controller: this.controller }) }
          else if (name === AppRouteName.Settings) { LegacySettingsContent({ state: this.state, controller: this.controller }) }
        }.layoutWeight(1).width('100%')
      }.width('100%').height('100%').backgroundColor($r('app.color.miuix_background'))
    }.hideTitleBar(true).onBackPressed(() => { this.controller?.back(); return true; })
  }
  @Builder private modelSheet() { ModelSelectionSheet({ state: this.state, controller: this.controller }) }
  build() {
    Navigation(this.pathStack) {
      ConversationSidePaneScaffold({ isOpen: this.state.drawer, isCurrent: this.state.screen === 'chat',
        pane: () => { this.pane(); }, content: () => { this.homeContent(); }, onOpenChange: (open: boolean) => this.controller?.drawer(open) })
    }.mode(NavigationMode.Stack).hideTitleBar(true).navDestination(this.destination)
      .width('100%').height('100%').backgroundColor($r('app.color.miuix_background'))
      .onAreaChange((_old, area) => { this.widthVp = Number(area.width); })
      .bindSheet($$this.sheetVisible, this.modelSheet, { height: SheetSize.LARGE, showClose: false,
        backgroundColor: $r('app.color.miuix_surface'), onDisappear: () => { if(this.state.sheet !== 'none') this.controller?.back(); } })
  }
}
