// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatBody.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export interface BottomFollowDecision { scrollByPx: number; requestIndex: number | null; }
export function resolveBottomFollowDecision(enabled: boolean, bottomItemIndex: number, sentinelBottom: number | null, viewportEnd: number, lastVisibleIndex: number | null): BottomFollowDecision {
  if (!enabled) return { scrollByPx: 0, requestIndex: null };
  const overflow = sentinelBottom === null ? null : sentinelBottom - viewportEnd;
  if (overflow !== null && overflow > 0) return { scrollByPx: overflow, requestIndex: null };
  return { scrollByPx: 0, requestIndex: sentinelBottom === null && lastVisibleIndex !== null && lastVisibleIndex < bottomItemIndex ? bottomItemIndex : null };
}
export function smoothBottomFollowStep(distancePx: number, elapsedSeconds: number, density: number): number {
  if (distancePx <= 0 || elapsedSeconds <= 0) return 0;
  if (distancePx <= 0.75) return distancePx;
  const seconds = Math.min(elapsedSeconds, 0.05);
  const eased = distancePx * (1 - Math.exp(-seconds / 0.085));
  return Math.min(distancePx, Math.min(Math.max(eased, 0.5), 720 * density * seconds));
}
export function resolveKeepBottomAnchored(current: boolean, isUserDragging: boolean, isAtBottom: boolean): boolean { return isUserDragging ? isAtBottom : isAtBottom ? true : current; }
export function resolveBottomFollowEnabled(isStreaming: boolean, keepBottomAnchored: boolean, isUserDragging: boolean, isBottomSettling: boolean = false): boolean { return (isStreaming || isBottomSettling) && keepBottomAnchored && !isUserDragging; }
export function shouldRequestInitialBottom(isStreaming: boolean, keepBottomAnchored: boolean, isUserDragging: boolean): boolean { return isStreaming && keepBottomAnchored && !isUserDragging; }
