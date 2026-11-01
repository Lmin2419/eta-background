// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/device/ScrollAxisContractTest.kt @ 3b1794d
import test from 'node:test';
import assert from 'node:assert/strict';
import { ScrollAxis } from '../../../entry/src/main/ets/core/eta/device/ScrollGestureContract';
import { exposesOnlyOppositeAxis, mayTreatLegacyActionsAsVertical } from '../../../entry/src/main/ets/core/eta/device/ScrollAxisContract';

test('horizontal only target rejects vertical request', () => {
  assert.equal(exposesOnlyOppositeAxis(ScrollAxis.VERTICAL, false, true), true);
});

test('vertical only target rejects horizontal request', () => {
  assert.equal(exposesOnlyOppositeAxis(ScrollAxis.HORIZONTAL, true, false), true);
});

test('same axis both axes and unknown axis do not reject prematurely', () => {
  assert.equal(exposesOnlyOppositeAxis(ScrollAxis.VERTICAL, true, false), false);
  assert.equal(exposesOnlyOppositeAxis(ScrollAxis.VERTICAL, true, true), false);
  assert.equal(exposesOnlyOppositeAxis(ScrollAxis.VERTICAL, false, false), false);
});

test('legacy forward backward alone is never assumed vertical', () => {
  assert.equal(mayTreatLegacyActionsAsVertical(ScrollAxis.VERTICAL, false, false), false);
  assert.equal(mayTreatLegacyActionsAsVertical(ScrollAxis.HORIZONTAL, false, true), false);
  assert.equal(mayTreatLegacyActionsAsVertical(ScrollAxis.VERTICAL, true, false), true);
});
