// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/device/ScrollAxisContract.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import { ScrollAxis } from './ScrollGestureContract';
/** 明确暴露另一滚动轴时禁止手势兜底，避免把滚动误变成列表项侧滑。 */
export function exposesOnlyOppositeAxis(
  requestedAxis: ScrollAxis,
  hasVerticalActions: boolean,
  hasHorizontalActions: boolean,
): boolean {
  if (requestedAxis === ScrollAxis.VERTICAL) return hasHorizontalActions && !hasVerticalActions;
  return hasVerticalActions && !hasHorizontalActions;
}
/** FORWARD/BACKWARD 没有轴语义；仅在已有明确纵向且无横向证据时可作纵向兼容。 */
export function mayTreatLegacyActionsAsVertical(
  requestedAxis: ScrollAxis,
  hasVerticalActions: boolean,
  hasHorizontalActions: boolean,
): boolean {
  return requestedAxis === ScrollAxis.VERTICAL && hasVerticalActions && !hasHorizontalActions;
}
