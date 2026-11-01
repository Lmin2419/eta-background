// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/device/ScreenshotOutcomePolicyTest.kt @ 3b1794d
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  ScreenshotQuality,
  classifyScreenshot,
  mayFallbackToRoot,
} from '../../../entry/src/main/ets/core/eta/device/ScreenshotOutcomePolicy';

test('classifies complete partial failed and not requested outcomes', () => {
  assert.equal(classifyScreenshot(false, false, false), ScreenshotQuality.NOT_REQUESTED);
  assert.equal(classifyScreenshot(true, true, true), ScreenshotQuality.COMPLETE);
  assert.equal(classifyScreenshot(true, true, false), ScreenshotQuality.PARTIAL);
  assert.equal(classifyScreenshot(true, false, false), ScreenshotQuality.FAILED);
});

test('never root-fallbacks across exclusions or a missing critical window', () => {
  assert.equal(mayFallbackToRoot(false, false), true);
  assert.equal(mayFallbackToRoot(true, false), false);
  assert.equal(mayFallbackToRoot(false, true), false);
});
