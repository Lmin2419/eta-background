// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
// D0b3：手机操作连接页的纯逻辑——状态文案、shell 身份、uitest 探针结果。
import { HdcConnectionSnapshot, HdcConnectionState, HdcDisconnectReason, disconnectMessageKey } from './hdcConnection';

export interface PhoneOpsProbe {
  identity: string;
  uitestReady: boolean;
  nodeCount: number;
}

export interface PhoneOpsSnapshot {
  state: HdcConnectionState;
  statusKey: string;
  reasonKey: string | null;
  target: string;
  attempt: number;
  identity: string;
  uitestReady: boolean;
  nodeCount: number;
  nativeAvailable: boolean;
}

const STATUS_KEYS: Record<HdcConnectionState, string> = {
  unconfigured: 'phone_ops_status_unconfigured',
  connecting: 'phone_ops_status_connecting',
  authorizing: 'phone_ops_status_authorizing',
  connected: 'phone_ops_status_connected',
  disconnected: 'phone_ops_status_disconnected',
};

export function phoneOpsStatusKey(state: HdcConnectionState): string {
  return STATUS_KEYS[state];
}

/** 只保留 `uid=数字` 这一段；其余输出不显示、不写日志。 */
export function uidOnly(output: string): string {
  const match = /uid=\d+/.exec(output);
  return match === null ? '' : match[0];
}

/** uitest 是否具备读屏和输入命令。 */
export function uitestAvailable(help: string): boolean {
  return help.includes('dumpLayout') && help.includes('uiInput');
}

export function buildPhoneOpsSnapshot(
  connection: HdcConnectionSnapshot,
  probe: PhoneOpsProbe,
  nativeAvailable: boolean,
): PhoneOpsSnapshot {
  return {
    state: connection.state,
    statusKey: phoneOpsStatusKey(connection.state),
    reasonKey: connection.reason === null ? null : disconnectMessageKey(connection.reason),
    target: connection.target,
    attempt: connection.attempt,
    identity: uidOnly(probe.identity),
    uitestReady: probe.uitestReady,
    nodeCount: probe.nodeCount,
    nativeAvailable,
  };
}

/** 原生库缺失时的快照：页面显示“原生库未加载”，不影响其他页面。 */
export function nativeMissingSnapshot(target: string): PhoneOpsSnapshot {
  return {
    state: 'unconfigured',
    statusKey: 'phone_ops_status_native_missing',
    reasonKey: null,
    target,
    attempt: 0,
    identity: '',
    uitestReady: false,
    nodeCount: 0,
    nativeAvailable: false,
  };
}

export function disconnectReasonKey(reason: HdcDisconnectReason): string {
  return disconnectMessageKey(reason);
}
