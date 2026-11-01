// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/tool/ObservationReferencePolicyTest.kt @ 3b1794d
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  ObservationStatus,
  validateObservationReference,
} from '../../../entry/src/main/ets/core/eta/tool/ObservationReferencePolicy';

test('requires an observation and its id', () => {
  assert.equal(validateObservationReference(null, 'o1'), ObservationStatus.NO_OBSERVATION);
  assert.equal(validateObservationReference('o1', ''), ObservationStatus.ID_REQUIRED);
});

test('publishing a newer observation rejects the old id', () => {
  assert.equal(validateObservationReference('o2', 'o1'), ObservationStatus.STALE);
  assert.equal(validateObservationReference('o2', 'o2'), ObservationStatus.MATCH);
});
