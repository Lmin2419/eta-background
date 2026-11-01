// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/device/ShellActionOutcomePolicyTest.kt @ 3b1794d
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  PROCESS_TIMEOUT_EXIT_CODE,
  ShellActionOutcome,
  classifyShellAction,
} from '../../../entry/src/main/ets/core/eta/device/ShellActionOutcomePolicy';

test('process timeout is not treated as a definite command failure', () => {
  assert.equal(classifyShellAction(PROCESS_TIMEOUT_EXIT_CODE), ShellActionOutcome.TIMED_OUT);
});

test('zero and ordinary nonzero exit codes stay distinct', () => {
  assert.equal(classifyShellAction(0), ShellActionOutcome.SUCCEEDED);
  assert.equal(classifyShellAction(1), ShellActionOutcome.FAILED);
});
