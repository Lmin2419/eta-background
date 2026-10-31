// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/StartupSplash.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
/** Platform-independent one-shot pre-draw gate, used by the Harmony loadContent boundary. */
export class StartupSplashGate {
  private installed = true;
  constructor(private readonly isContentReady: () => boolean) {}
  onPreDraw(): boolean {
    if (!this.installed) return true;
    if (!this.isContentReady()) return false;
    this.installed = false;
    return true;
  }
  onDestroy(): void {
    this.installed = false;
  }
}

// Harmony retains its system starting window until loadContent. Prepare appearance
// before that call instead of drawing an intermediate imitation splash page.
export class StartupContentHost {
  private alive = true;
  private ready = false;
  private started = false;
  private gate = new StartupSplashGate(() => this.ready);
  async load(prepareAppearance: () => Promise<void>, showContent: () => Promise<void>): Promise<void> {
    if(this.started) return;
    this.started = true;
    await prepareAppearance();
    this.ready = true;
    if(this.alive && this.gate.onPreDraw()) await showContent();
  }
  destroy(): void { this.alive = false; this.gate.onDestroy(); }
}
