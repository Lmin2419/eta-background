// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
// D0a 无障碍实验：扩展进程与界面进程通过 filesDir 下的 a11y-probe.json 通信。
export interface GestureProbe {
  requestedAt: number;
  result: string;
  at: number;
}
export interface AccessibilityProbe {
  connected: boolean;
  connectedAt: number;
  lastEventAt: number;
  foregroundBundle: string;
  nodeCount: number;
  gesture: GestureProbe;
}
export const MAX_PROBE_NODES = 500;
export const A11Y_TEST_BUTTON_TEXT = '无障碍注入测试按钮';
export const GESTURE_NOT_REQUESTED = 'not-requested';
export function emptyAccessibilityProbe(): AccessibilityProbe {
  return {
    connected: false,
    connectedAt: 0,
    lastEventAt: 0,
    foregroundBundle: '',
    nodeCount: 0,
    gesture: { requestedAt: 0, result: GESTURE_NOT_REQUESTED, at: 0 },
  };
}
function numberOr(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}
function textOr(value: unknown, fallback: string): string {
  return typeof value === 'string' ? value : fallback;
}
/** 容错解析：文件缺失、半写入或字段类型不对时退回“未连接”，不让诊断页崩掉。 */
export function parseAccessibilityProbe(text: string): AccessibilityProbe {
  if (text.trim() === '') return emptyAccessibilityProbe();
  let raw: Record<string, unknown>;
  try {
    const parsed: unknown = JSON.parse(text);
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) return emptyAccessibilityProbe();
    raw = parsed as Record<string, unknown>;
  } catch (_) {
    return emptyAccessibilityProbe();
  }
  const gestureRaw = raw.gesture;
  const gesture: Record<string, unknown> =
    gestureRaw !== null && typeof gestureRaw === 'object' && !Array.isArray(gestureRaw)
      ? (gestureRaw as Record<string, unknown>)
      : {};
  return {
    connected: raw.connected === true,
    connectedAt: numberOr(raw.connectedAt, 0),
    lastEventAt: numberOr(raw.lastEventAt, 0),
    foregroundBundle: textOr(raw.foregroundBundle, '').trim(),
    nodeCount: Math.max(0, Math.min(MAX_PROBE_NODES, Math.floor(numberOr(raw.nodeCount, 0)))),
    gesture: {
      requestedAt: numberOr(gesture.requestedAt, 0),
      result: textOr(gesture.result, GESTURE_NOT_REQUESTED),
      at: numberOr(gesture.at, 0),
    },
  };
}
export function serializeAccessibilityProbe(probe: AccessibilityProbe): string {
  return JSON.stringify({
    connected: probe.connected,
    connectedAt: probe.connectedAt,
    lastEventAt: probe.lastEventAt,
    foregroundBundle: probe.foregroundBundle,
    nodeCount: probe.nodeCount,
    gesture: {
      requestedAt: probe.gesture.requestedAt,
      result: probe.gesture.result,
      at: probe.gesture.at,
    },
  });
}
/** 注入结果只允许 ok / not-found / error:<错误码>；其余按 error:unknown 处理。 */
export function normalizeGestureResult(result: string): string {
  if (result === 'ok' || result === 'not-found') return result;
  if (result.startsWith('error:') && result.length > 'error:'.length) return result;
  return 'error:unknown';
}
