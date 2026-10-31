// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  HdcConnectOutcome,
  HdcConnection,
  HdcDisconnectReason,
  HdcTransport,
  HdcTransportEvent,
  disconnectMessageKey,
  validateHdcTarget,
} from '../../../entry/src/main/ets/core/eta/device/hdcConnection';

class FakeTransport implements HdcTransport {
  calls: { target: string; attempt: number }[] = [];
  closed = 0;
  outcomes: HdcConnectOutcome[] = [];
  pending = false;
  private listener: ((event: HdcTransportEvent) => void) | null = null;
  private resolvers: ((outcome: HdcConnectOutcome) => void)[] = [];

  connect(target: string, attempt: number): Promise<HdcConnectOutcome> {
    this.calls.push({ target, attempt });
    if (this.pending) {
      return new Promise<HdcConnectOutcome>((resolve) => {
        this.resolvers.push(resolve);
      });
    }
    return Promise.resolve(this.outcomes.shift() ?? 'connected');
  }

  close(): void {
    this.closed += 1;
  }

  onEvent(listener: (event: HdcTransportEvent) => void): () => void {
    this.listener = listener;
    return () => {
      this.listener = null;
    };
  }

  emit(event: HdcTransportEvent): void {
    this.listener?.(event);
  }

  resolve(outcome: HdcConnectOutcome): void {
    const resolver = this.resolvers.shift();
    resolver?.(outcome);
  }
}

test('invalid target is rejected and state stays unconfigured', () => {
  const connection = new HdcConnection(new FakeTransport());
  assert.equal(connection.configure('192.168.1.300:5555'), false);
  assert.equal(connection.configure('not-an-address'), false);
  assert.equal(connection.snapshot().state, 'unconfigured');
  assert.equal(validateHdcTarget(' 192.168.1.5:5555 '), '192.168.1.5:5555');
});

test('configure then connect moves through connecting to connected', async () => {
  const transport = new FakeTransport();
  transport.outcomes = ['connected'];
  const connection = new HdcConnection(transport);
  assert.equal(connection.configure('192.168.1.5:5555'), true);
  assert.equal(connection.snapshot().state, 'disconnected');
  const pending = connection.connect();
  assert.equal(connection.snapshot().state, 'connecting');
  await pending;
  assert.deepEqual(connection.snapshot(), {
    state: 'connected',
    reason: null,
    attempt: 1,
    target: '192.168.1.5:5555',
  });
});

test('authorizing event then connected event is a legal transition', async () => {
  const transport = new FakeTransport();
  transport.pending = true;
  const connection = new HdcConnection(transport);
  connection.configure('10.0.0.2:8710');
  const pending = connection.connect();
  transport.emit({ attempt: 1, kind: 'authorizing' });
  assert.equal(connection.snapshot().state, 'authorizing');
  transport.emit({ attempt: 1, kind: 'connected' });
  assert.equal(connection.snapshot().state, 'connected');
  transport.resolve('authorizing');
  await pending;
  assert.equal(connection.snapshot().state, 'connected');
});

test('illegal events are ignored instead of reviving a state', async () => {
  const transport = new FakeTransport();
  const connection = new HdcConnection(transport);
  transport.emit({ attempt: 0, kind: 'connected' });
  assert.equal(connection.snapshot().state, 'unconfigured');
  connection.configure('10.0.0.2:8710');
  await connection.connect();
  assert.equal(connection.snapshot().state, 'connected');
  transport.emit({ attempt: 1, kind: 'authorizing' });
  assert.equal(connection.snapshot().state, 'connected');
});

test('late events from an older attempt are ignored', async () => {
  const transport = new FakeTransport();
  transport.pending = true;
  const connection = new HdcConnection(transport);
  connection.configure('10.0.0.2:8710');
  const first = connection.connect();
  const second = connection.connect();
  assert.equal(connection.snapshot().attempt, 2);
  transport.emit({ attempt: 1, kind: 'connected' });
  assert.equal(connection.snapshot().state, 'connecting');
  transport.emit({ attempt: 2, kind: 'connected' });
  assert.equal(connection.snapshot().state, 'connected');
  transport.resolve('connected');
  await first;
  transport.resolve('connected');
  await second;
  assert.equal(transport.calls.length, 2);
});

test('failed outcomes map to disconnect reasons', async () => {
  const transport = new FakeTransport();
  const connection = new HdcConnection(transport);
  connection.configure('10.0.0.2:8710');
  transport.outcomes = ['refused'];
  await connection.connect();
  assert.deepEqual(connection.snapshot().state, 'disconnected');
  assert.equal(connection.snapshot().reason, 'refused');
  transport.outcomes = ['timeout'];
  await connection.connect();
  assert.equal(connection.snapshot().reason, 'timeout');
});

test('transport disconnect event keeps its reason', async () => {
  const transport = new FakeTransport();
  const connection = new HdcConnection(transport);
  connection.configure('10.0.0.2:8710');
  await connection.connect();
  transport.emit({ attempt: 1, kind: 'disconnected', reason: 'lost' });
  assert.equal(connection.snapshot().state, 'disconnected');
  assert.equal(connection.snapshot().reason, 'lost');
});

test('user disconnect closes the transport and records the reason', async () => {
  const transport = new FakeTransport();
  const connection = new HdcConnection(transport);
  connection.configure('10.0.0.2:8710');
  await connection.connect();
  connection.disconnect();
  assert.equal(transport.closed, 1);
  assert.equal(connection.snapshot().reason, 'user');
});

test('disconnect reasons map to distinct message keys', () => {
  const reasons: HdcDisconnectReason[] = ['user', 'timeout', 'refused', 'unauthorized', 'lost', 'protocol', 'unknown'];
  const keys = reasons.map((reason) => disconnectMessageKey(reason));
  assert.equal(new Set(keys).size, reasons.length);
  for (const key of keys) assert.ok(key.startsWith('hdc_disconnected_'));
});
