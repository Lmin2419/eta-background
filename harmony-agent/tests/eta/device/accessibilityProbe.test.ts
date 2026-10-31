// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from 'node:test';
import assert from 'node:assert/strict';
import {
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
