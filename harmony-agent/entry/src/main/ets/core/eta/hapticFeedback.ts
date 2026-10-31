// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaDropdownPreference.kt,
// MiuixScaffoldPage.kt @ 3b1794d; Miuix ScrollEndHaptic.kt / Switch.kt @ 39c40f9 (Apache-2.0).
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export enum EtaHaptic {
  ContextClick, ToggleOn, ToggleOff, TextHandleMove, GestureEnd, LongPress, Confirm
}

// ArkUI reports consumed displacement + Fling state, not Compose's post-fling
// available/consumed velocities. Only an actual fling reaching an edge can fire;
// initial layout, programmatic scrolling and a finger drag cannot.
export class ScrollEndHapticGate {
  private boundary: number = 0;
  private movedInFling: boolean = false;
  update(delta: number, offset: number, atEnd: boolean, fling: boolean): boolean {
    if (this.boundary === -1 && delta > 1 || this.boundary === 1 && delta < -1) this.boundary = 0;
    if (!fling) { this.movedInFling = false; return false; }
    if (Math.abs(delta) > 0) this.movedInFling = true;
    const next = offset <= 0 && delta < 0 ? -1 : atEnd && delta > 0 ? 1 : 0;
    if (next === 0 || next === this.boundary || !this.movedInFling) return false;
    this.boundary = next;
    return true;
  }
  reset(): void { this.boundary = 0; this.movedInFling = false; }
}
