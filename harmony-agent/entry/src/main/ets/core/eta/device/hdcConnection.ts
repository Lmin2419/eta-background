// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
// D0b1：HDC 连接状态机。传输层只通过注入的接口访问，不直接 import 原生模块。
// 接口形状参考 core/device.ts 的 HdcTransport 与 entry/src/main/cpp/bridge.cpp 的导出（tconn / list targets / shell）。

export type HdcConnectionState = 'unconfigured' | 'connecting' | 'authorizing' | 'connected' | 'disconnected';

export type HdcDisconnectReason =
  | 'user'
  | 'timeout'
  | 'refused'
  | 'unauthorized'
  | 'lost'
  | 'protocol'
  | 'unknown';

export type HdcConnectOutcome =
  | 'connected'
  | 'authorizing'
  | 'refused'
  | 'timeout'
  | 'unauthorized'
  | 'protocol'
  | 'unknown';

export interface HdcTransportEvent {
  attempt: number;
  kind: 'authorizing' | 'connected' | 'disconnected' | 'failed';
  reason?: string;
}

export interface HdcTransport {
  connect(target: string, attempt: number): Promise<HdcConnectOutcome>;
  close(): Promise<void> | void;
  onEvent(listener: (event: HdcTransportEvent) => void): () => void;
}

export interface HdcConnectionSnapshot {
  state: HdcConnectionState;
  reason: HdcDisconnectReason | null;
  attempt: number;
  target: string;
}

const MESSAGE_KEYS: Record<HdcDisconnectReason, string> = {
  user: 'hdc_disconnected_user',
  timeout: 'hdc_disconnected_timeout',
  refused: 'hdc_disconnected_refused',
  unauthorized: 'hdc_disconnected_unauthorized',
  lost: 'hdc_disconnected_lost',
  protocol: 'hdc_disconnected_protocol',
  unknown: 'hdc_disconnected_unknown',
};

/** 每种断开原因对应一个文案键；界面只按 key 取字符串。 */
export function disconnectMessageKey(reason: HdcDisconnectReason): string {
  return MESSAGE_KEYS[reason];
}

/** 只接受无线调试页面显示的 IPv4:端口；其余地址在配置阶段就拒绝。 */
export function validateHdcTarget(value: string): string | null {
  const match = /^(\d{1,3}(?:\.\d{1,3}){3}):(\d{1,5})$/.exec(value.trim());
  if (!match) return null;
  if (match[1].split('.').some((part) => Number(part) > 255)) return null;
  const port = Number(match[2]);
  if (port < 1 || port > 65535) return null;
  return value.trim();
}

function reasonFromOutcome(outcome: HdcConnectOutcome): HdcDisconnectReason {
  switch (outcome) {
    case 'refused':
      return 'refused';
    case 'timeout':
      return 'timeout';
    case 'unauthorized':
      return 'unauthorized';
    case 'protocol':
      return 'protocol';
    default:
      return 'unknown';
  }
}

function reasonFromText(value: string | undefined): HdcDisconnectReason {
  switch (value) {
    case 'user':
    case 'timeout':
    case 'refused':
    case 'unauthorized':
    case 'lost':
    case 'protocol':
      return value;
    default:
      return 'unknown';
  }
}

export class HdcConnection {
  private stateValue: HdcConnectionState = 'unconfigured';
  private reasonValue: HdcDisconnectReason | null = null;
  private attemptValue = 0;
  private targetValue = '';
  private unsubscribe: (() => void) | null = null;

  constructor(
    private readonly transport: HdcTransport,
    private readonly changed: () => void = () => {},
  ) {}

  snapshot(): HdcConnectionSnapshot {
    return {
      state: this.stateValue,
      reason: this.reasonValue,
      attempt: this.attemptValue,
      target: this.targetValue,
    };
  }

  /** 保存用户填写的地址；空串表示清除配置。非法地址返回 false，状态不变。 */
  configure(address: string): boolean {
    const trimmed = address.trim();
    if (trimmed === '') {
      this.detach();
      this.stateValue = 'unconfigured';
      this.reasonValue = null;
      this.targetValue = '';
      this.changed();
      return true;
    }
    const target = validateHdcTarget(trimmed);
    if (target === null) return false;
    this.targetValue = target;
    if (this.stateValue === 'unconfigured' || this.stateValue === 'disconnected') {
      this.stateValue = 'disconnected';
      this.reasonValue = null;
    }
    this.changed();
    return true;
  }

  /** 每次连接都带递增的尝试编号；旧尝试的迟到事件一律忽略。 */
  async connect(): Promise<HdcConnectOutcome> {
    if (this.targetValue === '') {
      this.stateValue = 'unconfigured';
      this.reasonValue = null;
      this.changed();
      return 'refused';
    }
    this.attemptValue += 1;
    const attempt = this.attemptValue;
    this.stateValue = 'connecting';
    this.reasonValue = null;
    this.attach();
    this.changed();
    let outcome: HdcConnectOutcome;
    try {
      outcome = await this.transport.connect(this.targetValue, attempt);
    } catch (_) {
      outcome = 'unknown';
    }
    if (attempt !== this.attemptValue) return outcome;
    if (outcome === 'connected') this.applyConnected(attempt);
    else if (outcome === 'authorizing') this.applyAuthorizing(attempt);
    else this.applyFailed(attempt, reasonFromOutcome(outcome));
    return outcome;
  }

  /** 用户主动断开：先关传输层，再进入“已断开（user）”。 */
  disconnect(): void {
    if (this.stateValue === 'unconfigured') return;
    try {
      void this.transport.close();
    } catch (_) {
      // 关闭失败不影响状态：界面已经不再使用这条连接。
    }
    this.detach();
    this.stateValue = 'disconnected';
    this.reasonValue = 'user';
    this.changed();
  }

  dispose(): void {
    this.detach();
  }

  private attach(): void {
    this.detach();
    this.unsubscribe = this.transport.onEvent((event) => this.applyEvent(event));
  }

  private detach(): void {
    if (this.unsubscribe !== null) {
      this.unsubscribe();
      this.unsubscribe = null;
    }
  }

  private applyEvent(event: HdcTransportEvent): void {
    if (event.attempt !== this.attemptValue) return;
    if (event.kind === 'authorizing') this.applyAuthorizing(event.attempt);
    else if (event.kind === 'connected') this.applyConnected(event.attempt);
    else if (event.kind === 'disconnected') this.applyFailed(event.attempt, reasonFromText(event.reason));
    else this.applyFailed(event.attempt, reasonFromText(event.reason));
  }

  private applyAuthorizing(attempt: number): void {
    if (attempt !== this.attemptValue) return;
    if (this.stateValue !== 'connecting' && this.stateValue !== 'authorizing') return;
    this.stateValue = 'authorizing';
    this.reasonValue = null;
    this.changed();
  }

  private applyConnected(attempt: number): void {
    if (attempt !== this.attemptValue) return;
    if (this.stateValue !== 'connecting' && this.stateValue !== 'authorizing') return;
    this.stateValue = 'connected';
    this.reasonValue = null;
    this.changed();
  }

  private applyFailed(attempt: number, reason: HdcDisconnectReason): void {
    if (attempt !== this.attemptValue) return;
    if (this.stateValue !== 'connecting' && this.stateValue !== 'authorizing' && this.stateValue !== 'connected') return;
    this.detach();
    this.stateValue = 'disconnected';
    this.reasonValue = reason;
    this.changed();
  }
}
