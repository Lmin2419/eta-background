// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/device/ScrollEvidenceContract.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import { ScrollDirection } from './ScrollGestureContract';
export enum ScrollEvidence {
  MOVED_BY_EVENT = 'MOVED_BY_EVENT',
  MOVED_BY_ANCHOR_MOTION = 'MOVED_BY_ANCHOR_MOTION',
  DIRECTION_MISMATCH = 'DIRECTION_MISMATCH',
  AT_BOUNDARY = 'AT_BOUNDARY',
  UNVERIFIED = 'UNVERIFIED',
}
export enum ScrollMovementSource {
  EVENT = 'EVENT',
  ANCHOR_MOTION = 'ANCHOR_MOTION',
}
export const ACCESSIBILITY_UNDEFINED = -1;
export function normalizeAccessibilityDelta(value: number): number | null {
  return value === ACCESSIBILITY_UNDEFINED ? null : value;
}
function sign(value: number): number {
  return value > 0 ? 1 : value < 0 ? -1 : 0;
}
export function classifyScrollEvidence(
  direction: ScrollDirection,
  delta: number | null,
  movementSource: ScrollMovementSource | null,
  atBoundary: boolean,
): ScrollEvidence {
  if (delta !== null && delta !== 0 && sign(delta) !== direction.scrollDeltaSign) {
    return ScrollEvidence.DIRECTION_MISMATCH;
  }
  if (delta !== null && delta !== 0) {
    if (movementSource === ScrollMovementSource.EVENT) return ScrollEvidence.MOVED_BY_EVENT;
    if (movementSource === ScrollMovementSource.ANCHOR_MOTION) return ScrollEvidence.MOVED_BY_ANCHOR_MOTION;
    return ScrollEvidence.UNVERIFIED;
  }
  if (atBoundary) return ScrollEvidence.AT_BOUNDARY;
  return ScrollEvidence.UNVERIFIED;
}
