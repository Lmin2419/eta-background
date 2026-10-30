// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/navigation/AgentNavigator.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AppRoute, AppRouteName, routeEquals } from "./routes";

/** Pure stack operations. Platform NavPathStack is a rendering mirror, never the state owner. */
export class AgentNavigator {
  private readonly stack: AppRoute[];
  constructor(
    initial: readonly AppRoute[] = [new AppRoute(AppRouteName.Home)],
  ) {
    this.stack = [...initial];
  }
  snapshot(): AppRoute[] {
    return [...this.stack];
  }
  push(route: AppRoute): void {
    // Eta does not bring an existing entry to the front; duplicate pushes anywhere in the stack are ignored.
    if (!this.stack.some((item) => routeEquals(item, route)))
      this.stack.push(route);
  }
  replace(route: AppRoute): void {
    const index = this.stack.findIndex((item) => routeEquals(item, route));
    if (index >= 0) {
      this.stack.splice(index + 1);
      return;
    }
    if (this.stack.length) this.stack[this.stack.length - 1] = route;
    else this.stack.push(route);
  }
  pop(): boolean {
    if (this.stack.length <= 1) return false;
    this.stack.pop();
    return true;
  }
  popToHome(): void {
    if (this.stack.length > 1) this.stack.splice(1);
    if (this.stack.length && this.stack[0].name !== AppRouteName.Home)
      this.stack[0] = new AppRoute(AppRouteName.Home);
  }
  current(): AppRoute | null {
    return this.stack[this.stack.length - 1] ?? null;
  }
}
