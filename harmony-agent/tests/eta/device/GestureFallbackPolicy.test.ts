// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/device/GestureFallbackPolicyTest.kt @ 3b1794d
import test from 'node:test';
import assert from 'node:assert/strict';
import { mayFallbackToRoot } from '../../../entry/src/main/ets/core/eta/device/GestureFallbackPolicy';

test('only a definitely undispatched gesture may be replayed', () => {
  assert.equal(mayFallbackToRoot('GESTURE_NOT_DISPATCHED'), true);
  assert.equal(mayFallbackToRoot('GESTURE_CANCELLED'), false);
  assert.equal(mayFallbackToRoot('ACTION_OUTCOME_UNKNOWN'), false);
});
