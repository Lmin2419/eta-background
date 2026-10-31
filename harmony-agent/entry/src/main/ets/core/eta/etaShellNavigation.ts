// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentAppRoot.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Compatibility intents for unported 0.9 pages; EtaNavigation owns the only page stack.
import { EtaNavigation } from './etaNavigation';
import { AppRoute, AppRouteName, NewProviderType, decodeRoutes, encodeRoutes } from './routes';
export type Screen = 'chat' | 'providers' | 'provider' | 'settings' | 'diagnostics';
export type Sheet = 'none' | 'models' | 'catalog';
export class EtaShellNavigation {
  readonly eta = new EtaNavigation();
  sheet: Sheet = 'none';
  // The Harmony diagnostic view is a platform destination, not a fabricated Eta route.
  private diagnostics = false;
  get drawer(): boolean { return this.eta.snapshot().conversationPaneOpen; }
  set drawer(value: boolean) { this.eta.setConversationPaneOpen(value); }
  get routes(): AppRoute[] { return this.eta.snapshot().routes; }
  get screen(): Screen {
    if (this.diagnostics) return 'diagnostics';
    switch (this.eta.snapshot().current?.name) {
      case AppRouteName.ModelProviders: return 'providers';
      case AppRouteName.ModelProviderNew:
      case AppRouteName.ModelProviderDetail: return 'provider';
      case AppRouteName.Settings: return 'settings';
      default: return 'chat';
    }
  }
  push(screen: Screen): void {
    this.sheet = 'none'; this.diagnostics = screen === 'diagnostics';
    if (screen === 'chat') this.eta.home();
    else if (screen === 'providers') this.eta.push(new AppRoute(AppRouteName.ModelProviders));
    else if (screen === 'settings') this.eta.push(new AppRoute(AppRouteName.Settings));
    else if (screen === 'provider') this.provider('');
  }
  provider(id: string): void {
    this.sheet = 'none'; this.diagnostics = false;
    this.eta.push(id ? new AppRoute(AppRouteName.ModelProviderDetail,id) : new AppRoute(AppRouteName.ModelProviderNew,null,NewProviderType.OpenAiCompatible));
  }
  back(): boolean {
    if(this.sheet !== 'none') { this.sheet = 'none'; return true; }
    if(this.diagnostics) { this.diagnostics = false; return true; }
    return this.eta.back();
  }
  providersAfterDelete(): void {
    this.sheet='none'; this.diagnostics=false;
    this.eta.replace(new AppRoute(AppRouteName.ModelProviders));
  }
  restore(encoded: string): boolean {
    // Do not resurrect unported pages or an unsaved credential editor after process death.
    const supported=[AppRouteName.Home,AppRouteName.Settings,AppRouteName.ModelProviders];
    try {
      if(!this.eta.restoreStack(encodeRoutes(decodeRoutes(encoded).filter(route=>supported.includes(route.name))))) return false;
    } catch { return false; }
    this.sheet='none'; this.diagnostics=false; return true;
  }
}
