// 随身助手原创代码（无障碍扩展实验，D0a）。
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  GESTURE_NOT_REQUESTED,
  MAX_PROBE_NODES,
  emptyAccessibilityProbe,
  normalizeGestureResult,
  parseAccessibilityProbe,
  serializeAccessibilityProbe,
} from '../../../entry/src/main/ets/core/eta/device/accessibilityProbe';

test('missing probe file reads as disconnected', () => {
  const probe = parseAccessibilityProbe('');
  assert.deepEqual(probe, emptyAccessibilityProbe());
  assert.equal(probe.connected, false);
});

test('malformed probe json falls back to disconnected', () => {
  assert.deepEqual(parseAccessibilityProbe('{"connected": tru'), emptyAccessibilityProbe());
  assert.deepEqual(parseAccessibilityProbe('[]'), emptyAccessibilityProbe());
});

test('serialize and parse keep every probe field', () => {
  const probe = {
    connected: true,
    connectedAt: 1000,
    lastEventAt: 2000,
    foregroundBundle: 'com.example.app',
    nodeCount: 42,
    readError: '',
    gesture: { requestedAt: 3000, result: 'ok', at: 3001 },
  };
  assert.deepEqual(parseAccessibilityProbe(serializeAccessibilityProbe(probe)), probe);
});

test('node count is clamped to the probe limit', () => {
  assert.equal(parseAccessibilityProbe('{"nodeCount": 9999}').nodeCount, MAX_PROBE_NODES);
  assert.equal(parseAccessibilityProbe('{"nodeCount": -3}').nodeCount, 0);
  assert.equal(parseAccessibilityProbe('{"nodeCount": "many"}').nodeCount, 0);
});

test('gesture result only accepts ok, not-found or error codes', () => {
  assert.equal(normalizeGestureResult('ok'), 'ok');
  assert.equal(normalizeGestureResult('not-found'), 'not-found');
  assert.equal(normalizeGestureResult('error:1600001'), 'error:1600001');
  assert.equal(normalizeGestureResult('error:'), 'error:unknown');
  assert.equal(normalizeGestureResult('boom'), 'error:unknown');
});

test('readError round-trips and stays separate from the gesture result', () => {
  const probe = emptyAccessibilityProbe();
  assert.equal(probe.readError, '');
  probe.readError = 'error:9300003';
  const parsed = parseAccessibilityProbe(serializeAccessibilityProbe(probe));
  assert.equal(parsed.readError, 'error:9300003');
  assert.equal(parsed.gesture.result, GESTURE_NOT_REQUESTED);
  assert.equal(parseAccessibilityProbe('{"connected":true}').readError, '');
});
