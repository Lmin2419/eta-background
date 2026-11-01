// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/device/GestureFallbackPolicy.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
/** 只有确认手势从未提交给系统时，才允许改用 Root 重放。 */
export function mayFallbackToRoot(errorCode: string): boolean {
  return errorCode === 'GESTURE_NOT_DISPATCHED';
}
