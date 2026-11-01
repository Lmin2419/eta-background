// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentAppShell.kt, AgentAppRoot.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ConversationPanePanel } from '../components/ConversationPanePanel';
import { FoundationController, ShellState, emptyShellState } from '../../../core/foundation/controller';
import { AppRouteName, encodeRoute } from '../../../core/eta/routes';
import { EtaPageDefaults as P, EtaProviderDefaults as ProviderMetrics, EtaComponentSizes as S, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { AgentHomeScreen } from '../screens/AgentHomeScreen';
import { ModelProviderListScreen } from '../pages/providers/ModelProviderListScreen';
import { ModelProviderDetailScreen } from '../pages/providers/ModelProviderDetailScreen';
import { RemoteModelImportSheet } from '../pages/providers/RemoteModelImportSheet';
import { CommunityCatalogScreen, CommunityCatalogProviderScreen } from '../pages/providers/CommunityCatalogScreens';
import { DiagnosticsPage } from '../screens/DiagnosticsPage';
import {McpServersScreen} from '../screens/McpServersScreen';
import {AgentMemoryScreen} from '../screens/AgentMemoryScreen';
import { SettingsScreen } from '../screens/SettingsScreen';
import { AppearanceSettingsScreen } from '../screens/AppearanceSettingsScreen';
import { PhoneOpsScreen } from '../../harmony/PhoneOpsScreen';
import { ConversationSidePaneScaffold } from '../components/ConversationSidePaneScaffold';
import { AgentTopBar } from './AgentTopBar';

@Component
export struct AgentAppShell {
  @Prop @Watch('sync') state: ShellState = emptyShellState();
  controller: FoundationController | undefined = undefined;
  @Prop keyboardVisible: boolean = false;
  private pathStack: NavPathStack = new NavPathStack();
  private pathKey: string = '';
  private syncing: boolean = false;
  @State private widthVp: number = S.Dp0;
  @State private sheetVisible: boolean = false;
  aboutToAppear(): void { this.sync(); }
  private sync(): void {
    this.sheetVisible = this.state.sheet === 'catalog';
    const routes = this.state.routes.slice(1);
    const key = routes.map(encodeRoute).join('\n') + (this.state.screen === 'diagnostics' ? '\nHarmonyDiagnostics' : '') + (this.state.screen === 'phoneOps' ? '\nHarmonyPhoneOps' : '');
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
    if (this.state.screen === 'phoneOps') paths.push(new NavPathInfo('HarmonyPhoneOps', '', () => { this.nativePop('HarmonyPhoneOps'); }));
    this.pathStack.setPathStack(paths);
    this.syncing = false;
  }
  private nativePop(identity: string): void {
    if (this.syncing) return;
    this.pathKey = '';
    const route = this.state.routes[this.state.routes.length - 1];
    if (identity === 'HarmonyPhoneOps' ? this.state.screen === 'phoneOps' : identity === 'HarmonyDiagnostics' ? this.state.screen === 'diagnostics' : route !== undefined && encodeRoute(route) === identity) this.controller?.back();
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
        AgentHomeScreen({state:this.state,keyboardVisible:this.keyboardVisible,controller:this.controller})
      }.width('100%').layoutWeight(1)
    }.width('100%').height('100%').backgroundColor($r('app.color.miuix_background'))
  }
  @Builder private pane() { ConversationPanePanel({ state: this.state, controller: this.controller }) }
  @Builder private destination(name: string) {
    NavDestination() {
      Column() {
        this.error()
        Column() {
          if (name === AppRouteName.ModelProviders) { ModelProviderListScreen({ state: this.state, controller: this.controller }) }
          else if (name === AppRouteName.ModelProviderDetail || name === AppRouteName.ModelProviderNew) { ModelProviderDetailScreen({ state: this.state, controller: this.controller }) }
          else if (name === AppRouteName.CommunityCatalog) { CommunityCatalogScreen({state:this.state,controller:this.controller}) }
          else if (name === AppRouteName.CommunityCatalogProvider) { CommunityCatalogProviderScreen({state:this.state,controller:this.controller}) }
          else if (name === 'HarmonyDiagnostics') { DiagnosticsPage({ state: this.state, controller: this.controller }) }
          else if (name === 'HarmonyPhoneOps') { PhoneOpsScreen() }
          else if (name === AppRouteName.Settings) { SettingsScreen({ state: this.state, controller: this.controller }) }
          else if (name === AppRouteName.McpServers || name === AppRouteName.McpServerDetail) { McpServersScreen({state:this.state,controller:this.controller,isDetail:name===AppRouteName.McpServerDetail}) }
          else if (name === AppRouteName.Memory) { AgentMemoryScreen({state:this.state,controller:this.controller,keyboardVisible:this.keyboardVisible}) }
          else if (name === AppRouteName.AppearanceSettings) { AppearanceSettingsScreen({state:this.state,controller:this.controller}) }
        }.layoutWeight(1).width('100%')
      }.width('100%').height('100%').backgroundColor($r('app.color.miuix_background'))
    }.hideTitleBar(true).onBackPressed(() => { this.controller?.back(); return true; })
  }
  @Builder private modelSheet() { RemoteModelImportSheet({ state: this.state, controller: this.controller }) }
  build() {
    Navigation(this.pathStack) {
      ConversationSidePaneScaffold({ isOpen: this.state.drawer, isCurrent: this.state.screen === 'chat',
        pane: () => { this.pane(); }, content: () => { this.homeContent(); }, onOpenChange: (open: boolean) => this.controller?.drawer(open) })
    }.mode(NavigationMode.Stack).hideTitleBar(true).navDestination(this.destination)
      .width('100%').height('100%').backgroundColor($r('app.color.miuix_background'))
      .onAreaChange((_old, area) => { this.widthVp = Number(area.width); })
      .bindSheet($$this.sheetVisible, this.modelSheet, { height: (ProviderMetrics.SheetHeightFraction*100)+'%', showClose: false,
        backgroundColor: $r('app.color.miuix_surface'), onDisappear: () => { if(this.state.sheet === 'catalog') this.controller?.back(); } })
  }
}
