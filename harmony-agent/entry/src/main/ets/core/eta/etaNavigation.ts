// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentAppRoot.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Harmony boundary adds the ETA_PORT A/B/C gate and invalidates late external navigation requests.
import { AgentNavigator } from "./agentNavigator";
import {
  AppRoute,
  AppRouteName,
  decodeRoutes,
  encodeRoutes,
  routeCapability,
} from "./routes";
export class EtaNavigationSnapshot {
  routes: AppRoute[] = [];
  conversationPaneOpen = false;
  current: AppRoute | null = null;
}
export class EtaNavigation {
  private navigator = new AgentNavigator();
  private paneOpen = false;
  private externalGeneration = 0;
  snapshot(): EtaNavigationSnapshot {
    const state = new EtaNavigationSnapshot();
    state.routes = this.navigator.snapshot();
    state.current = this.navigator.current();
    state.conversationPaneOpen = this.paneOpen;
    return state;
  }
  push(route: AppRoute): boolean {
    if (routeCapability(route.name) !== "A") return false;
    this.externalGeneration++;
    this.navigator.push(route);
    // Eta preserves the requested pane state while visiting a child page, restoring it on back.
    return true;
  }
  replace(route: AppRoute): boolean {
    if (routeCapability(route.name) !== "A") return false;
    this.externalGeneration++;
    this.navigator.replace(route);
    return true;
  }
  setConversationPaneOpen(value: boolean): void {
    this.externalGeneration++;
    this.paneOpen = value;
  }
  back(): boolean {
    this.externalGeneration++;
    if (this.navigator.current()?.name === AppRouteName.Home && this.paneOpen) {
      this.paneOpen = false;
      return true;
    }
    return this.navigator.pop();
  }
  home(): void {
    this.externalGeneration++;
    this.navigator.popToHome();
    this.paneOpen = false;
  }
  saveStack(): string {
    return encodeRoutes(this.navigator.snapshot());
  }
  restoreStack(encoded: string): boolean {
    try {
      const routes = decodeRoutes(encoded).filter(
        (route) => routeCapability(route.name) === "A",
      );
      if (!routes.length || routes[0].name !== AppRouteName.Home)
        routes.unshift(new AppRoute(AppRouteName.Home));
      const replacement = new AgentNavigator();
      for (const route of routes) replacement.push(route);
      this.externalGeneration++;
      this.navigator = replacement;
      this.paneOpen = false;
      return true;
    } catch {
      return false;
    }
  }
  /** Called only AFTER the platform validates notification tokens. No models, tools or execution are started here. */
  async notificationConversation(
    conversationId: string,
    openConversation: (id: string) => Promise<boolean>,
  ): Promise<boolean> {
    if (!conversationId) return false;
    const generation = ++this.externalGeneration;
    const opened = await openConversation(conversationId);
    if (!opened || generation !== this.externalGeneration) return false;
    this.navigator.popToHome();
    this.paneOpen = false;
    return true;
  }
}
