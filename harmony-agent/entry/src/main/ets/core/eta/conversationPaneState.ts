// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/ConversationPaneState.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export type ConversationPaneAnchor = 'Closed' | 'Open';
export interface PaneSnapshot { settledValue: ConversationPaneAnchor; settledPosition: number; offset: number; isAnimationRunning: boolean; isDragging: boolean; }
/** Equivalent to snapshotFlow's distinct-until-changed BEFORE filterNotNull. */
export class ConversationPaneSettledValues {
  private previous: ConversationPaneAnchor | null | undefined = undefined;
  update(snapshot: PaneSnapshot): ConversationPaneAnchor | null {
    const anchor = !snapshot.isDragging && !snapshot.isAnimationRunning && Math.abs(snapshot.offset - snapshot.settledPosition) < 0.5 ? snapshot.settledValue : null;
    if (anchor === this.previous) return null;
    this.previous = anchor;
    return anchor;
  }
}
