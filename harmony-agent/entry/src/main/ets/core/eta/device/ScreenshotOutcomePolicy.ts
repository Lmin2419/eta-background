// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/device/ScreenshotOutcomePolicy.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
export enum ScreenshotQuality {
  NOT_REQUESTED = 'not_requested',
  COMPLETE = 'complete',
  PARTIAL = 'partial',
  FAILED = 'failed',
}
export function classifyScreenshot(requested: boolean, hasImage: boolean, complete: boolean): ScreenshotQuality {
  if (!requested) return ScreenshotQuality.NOT_REQUESTED;
  if (hasImage && complete) return ScreenshotQuality.COMPLETE;
  if (hasImage) return ScreenshotQuality.PARTIAL;
  return ScreenshotQuality.FAILED;
}
export function mayFallbackToRoot(excludedPackagesPresent: boolean, criticalWindowMissing: boolean): boolean {
  return !excludedPackagesPresent && !criticalWindowMissing;
}
