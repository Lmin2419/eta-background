// Logic-only port from app/src/test/kotlin/io/github/mangi/eta/ui/components/EtaPreferenceRenderingTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Robolectric semantics/bounds assertions remain pending in the DevEco gallery, not claimed here.
import test from 'node:test';
import assert from 'node:assert/strict';
import { measurePreferenceRow } from '../../entry/src/main/ets/core/eta/etaPreferenceRowLayout';
import { EtaPreferenceDefaults as P } from '../../entry/src/main/ets/core/eta/tokens';
test('controlsKeepTheirTitlesWhenTrailingSlotsAreProvided (measurement logic only)', () => {
  for (const trailingWidth of [44, 26, 180]) {
    const row = measurePreferenceRow(320 - P.SidePadding * 2, null, true, trailingWidth);
    assert.ok(row.titleMaxWidth > 0);
    assert.ok(row.actionMaxWidth > 0);
    assert.equal(row.actionGap, 12);
  }
  const tiny = measurePreferenceRow(20, 24, true, 44);
  assert.equal(tiny.actionMaxWidth, 0);
  assert.equal(tiny.titleMaxWidth, 0);
});
test('trailingSlotIsPlacedAfterTitleWithOrWithoutAnIcon (measurement logic only)', () => {
  for (const hasIcon of [false, true]) {
    const row = measurePreferenceRow(320 - 32, hasIcon ? 24 : null, true, 44);
    const titleLeft = P.SidePadding + row.leadingWidth;
    const actionRight = 320 - P.SidePadding;
    assert.equal(titleLeft, hasIcon ? 56 : 16);
    assert.ok(actionRight - 44 > titleLeft + 80);
    assert.equal(actionRight, 304);
  }
});
