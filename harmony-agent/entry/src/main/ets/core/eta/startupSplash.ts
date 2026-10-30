// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/StartupSplash.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
/** Platform-independent one-shot pre-draw gate. A Harmony first-frame adapter has NOT been wired yet. */
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
