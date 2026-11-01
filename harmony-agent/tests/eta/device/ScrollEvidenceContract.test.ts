// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/device/ScrollEvidenceContractTest.kt @ 3b1794d
import test from 'node:test';
import assert from 'node:assert/strict';
import { ScrollDirection } from '../../../entry/src/main/ets/core/eta/device/ScrollGestureContract';
import {
  ScrollEvidence,
  ScrollMovementSource,
  classifyScrollEvidence,
  normalizeAccessibilityDelta,
} from '../../../entry/src/main/ets/core/eta/device/ScrollEvidenceContract';

test('undefined accessibility delta is not movement or mismatch', () => {
  const delta = normalizeAccessibilityDelta(-1);
  assert.equal(delta, null);
  assert.equal(classifyScrollEvidence(ScrollDirection.DOWN, delta, ScrollMovementSource.EVENT, false), ScrollEvidence.UNVERIFIED);
});

test('classifies event tree boundary and mismatch evidence', () => {
  assert.equal(classify(20), ScrollEvidence.MOVED_BY_EVENT);
  assert.equal(classify(20, ScrollMovementSource.ANCHOR_MOTION), ScrollEvidence.MOVED_BY_ANCHOR_MOTION);
  assert.equal(classify(null, ScrollMovementSource.EVENT, true), ScrollEvidence.AT_BOUNDARY);
  assert.equal(classify(-20), ScrollEvidence.DIRECTION_MISMATCH);
});

function classify(
  delta: number | null,
  source: ScrollMovementSource = ScrollMovementSource.EVENT,
  boundary = false,
): ScrollEvidence {
  return classifyScrollEvidence(ScrollDirection.DOWN, delta, source, boundary);
}
