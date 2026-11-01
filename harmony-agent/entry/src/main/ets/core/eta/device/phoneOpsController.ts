// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
// D0b3：页面控制器——连接状态、探针和读屏计数都在这里，页面只渲染快照。
import {
  HdcConnectOutcome, HdcConnection, HdcTransport, HdcTransportEvent,
} from './hdcConnection';
import { PhoneOpsSnapshot, buildPhoneOpsSnapshot, nativeMissingSnapshot, uidOnly, uitestAvailable } from './phoneOps';

export interface PhoneOpsTransport extends HdcTransport {
  readonly nativeAvailable: boolean;
  readonly identity: string;
  readonly uitestReady: boolean;
  dumpLayout(): Promise<string>;
}

export class PhoneOpsController {
  private connection: HdcConnection;
  private nodeCount = 0;
  private notice = '';

  constructor(private readonly transport: PhoneOpsTransport, private readonly countNodes: (raw: string) => number,
    private readonly changed: () => void = () => {}) {
    this.connection = new HdcConnection(transport, () => this.changed());
  }

  snapshot(): PhoneOpsSnapshot {
    if (!this.transport.nativeAvailable) return nativeMissingSnapshot(this.connection.snapshot().target);
    const base = buildPhoneOpsSnapshot(
      this.connection.snapshot(),
      { identity: uidOnly(this.transport.identity), uitestReady: this.transport.uitestReady, nodeCount: this.nodeCount },
      true,
    );
    return base;
  }

  noticeText(): string { return this.notice; }

  configure(address: string): boolean {
    const ok = this.connection.configure(address);
    this.notice = ok ? '' : 'invalid';
    this.changed();
    return ok;
  }

  async connect(): Promise<void> {
    this.notice = '';
    await this.connection.connect();
    this.changed();
  }

  disconnect(): void {
    this.connection.disconnect();
    this.nodeCount = 0;
    this.changed();
  }

  async testDump(): Promise<number> {
    try {
      const raw = await this.transport.dumpLayout();
      this.nodeCount = this.countNodes(raw);
      this.notice = this.nodeCount > 0 ? '' : 'empty';
    } catch (error) {
      this.nodeCount = 0;
      this.notice = 'error';
      console.error('[phoneOps] dump failed: ' + String(error));
    }
    this.changed();
    return this.nodeCount;
  }
}

/** 预览器用的假传输层：按端口模拟成功 / 待授权 / 拒绝。 */
export class FakePhoneOpsTransport implements PhoneOpsTransport {
  readonly nativeAvailable = true;
  identity = '';
  uitestReady = false;
  private listener: ((event: HdcTransportEvent) => void) | null = null;
  private lastTarget = '';
  private lastAttempt = 0;

  async connect(target: string, attempt: number): Promise<HdcConnectOutcome> {
    this.lastTarget = target;
    this.lastAttempt = attempt;
    const port = Number(target.split(':')[1]);
    if (port === 5556) {
      this.emit({ attempt: attempt, kind: 'authorizing' });
      return 'authorizing';
    }
    if (port === 5557) {
      this.emit({ attempt: attempt, kind: 'failed', reason: 'refused' });
      return 'refused';
    }
    this.identity = 'uid=20010043(shell) gid=20010043 groups=20010043';
    this.uitestReady = true;
    this.emit({ attempt: attempt, kind: 'connected' });
    return 'connected';
  }

  async close(): Promise<void> {}

  onEvent(listener: (event: HdcTransportEvent) => void): () => void {
    this.listener = listener;
    return () => { this.listener = null; };
  }

  async dumpLayout(): Promise<string> {
    return JSON.stringify([{ attributes: { text: '', type: 'root', bounds: '[0,0][1080,1920]' },
      children: [{ attributes: { text: 'a', type: 'Text', bounds: '[0,0][100,50]' } },
        { attributes: { text: 'b', type: 'Text', bounds: '[0,60][100,110]' } }] }]);
  }

  private emit(event: HdcTransportEvent): void { this.listener?.(event); }
}
