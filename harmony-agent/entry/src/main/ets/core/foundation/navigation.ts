/** Screen identity, transient overlays and back behavior are independent of chat/runtime state. */
export type Screen = 'chat' | 'providers' | 'provider' | 'settings' | 'diagnostics';
export type Sheet = 'none' | 'models' | 'catalog';
export class ShellNavigation {
  private stack: Screen[] = ['chat'];
  drawer = false;
  sheet: Sheet = 'none';
  get screen(): Screen { return this.stack[this.stack.length - 1]; }
  push(screen: Screen): void {
    this.drawer = false; this.sheet = 'none';
    if (screen === 'chat') this.stack = ['chat'];
    else if (screen !== this.screen) this.stack.push(screen);
  }
  back(): boolean {
    if (this.sheet !== 'none') { this.sheet = 'none'; return true; }
    if (this.drawer) { this.drawer = false; return true; }
    if (this.stack.length > 1) { this.stack.pop(); return true; }
    return false;
  }
}
