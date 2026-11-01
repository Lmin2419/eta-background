// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildPhoneOpsSnapshot,
  nativeMissingSnapshot,
  phoneOpsStatusKey,
  uidOnly,
  uitestAvailable,
} from '../../../entry/src/main/ets/core/eta/device/phoneOps';

const connection = {
  state: 'unconfigured' as const,
  reason: null,
  attempt: 0,
  target: '',
};

test('each connection state maps to its own status resource key', () => {
  const states = ['unconfigured', 'connecting', 'authorizing', 'connected', 'disconnected'] as const;
  const keys = states.map((s) => phoneOpsStatusKey(s));
  assert.equal(new Set(keys).size, states.length);
  for (const key of keys) assert.ok(key.startsWith('phone_ops_status_'));
});

test('shell identity only keeps the uid segment', () => {
  assert.equal(uidOnly('uid=20010043(shell) gid=20010043 groups=20010043'), 'uid=20010043');
  assert.equal(uidOnly('uid=0(root)'), 'uid=0');
  assert.equal(uidOnly('no identity here'), '');
});

test('uitest is ready only when both dumpLayout and uiInput are advertised', () => {
  assert.equal(uitestAvailable('dumpLayout\nuiInput'), true);
  assert.equal(uitestAvailable('dumpLayout'), false);
  assert.equal(uitestAvailable(''), false);
});

test('snapshot carries status, reason key and probe fields', () => {
  const snapshot = buildPhoneOpsSnapshot(
    { state: 'disconnected', reason: 'timeout', attempt: 3, target: '127.0.0.1:5555' },
    { identity: 'uid=20010043(shell)', uitestReady: true, nodeCount: 103 },
    true,
  );
  assert.equal(snapshot.statusKey, 'phone_ops_status_disconnected');
  assert.equal(snapshot.reasonKey, 'hdc_disconnected_timeout');
  assert.equal(snapshot.identity, 'uid=20010043');
  assert.equal(snapshot.uitestReady, true);
  assert.equal(snapshot.nodeCount, 103);
  assert.equal(snapshot.nativeAvailable, true);
});

test('missing native library reports its own status and no probe data', () => {
  const snapshot = nativeMissingSnapshot('127.0.0.1:5555');
  assert.equal(snapshot.statusKey, 'phone_ops_status_native_missing');
  assert.equal(snapshot.nativeAvailable, false);
  assert.equal(snapshot.nodeCount, 0);
  assert.equal(snapshot.identity, '');
});

test('connected snapshot without reason has no reason key', () => {
  const snapshot = buildPhoneOpsSnapshot(
    { state: 'connected', reason: null, attempt: 1, target: '10.0.0.2:8710' },
    { identity: 'uid=0(root)', uitestReady: false, nodeCount: 0 },
    true,
  );
  assert.equal(snapshot.reasonKey, null);
  assert.equal(snapshot.statusKey, 'phone_ops_status_connected');
});
