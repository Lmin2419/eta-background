// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/device/DeviceToolContract.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
/** 结构化设备工具只操作 Eta 所属用户；Android UID 的用户段宽度由系统定义，调用方把 uid 传进来。 */
export interface DeviceToolFailure {
  ok: false;
  code: string;
  message: string;
  [key: string]: unknown;
}
export function appUserId(uid: number): number {
  return Math.floor(uid / 100000);
}
export function userScopeError(
  argumentsValue: Record<string, unknown>,
  userId: number,
): DeviceToolFailure | null {
  if (!('user_id' in argumentsValue)) return null;
  const requested = argumentsValue.user_id;
  if (typeof requested !== 'number' || !Number.isInteger(requested) || requested < 0) {
    return failure('INVALID_ARGUMENT', 'user_id 必须是非负整数');
  }
  if (requested === userId) return null;
  return {
    ...failure('USER_SCOPE_UNSUPPORTED', '此工具只支持 Eta 所属的 Android 用户，不自动跨用户或工作资料执行'),
    user_id: userId,
    requested_user_id: requested,
  };
}
const PACKAGE_NAME = /^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$/;
const SETTING_KEY = /^[A-Za-z0-9_.-]{1,200}$/;
export function validPackageName(value: string): boolean {
  return value.length <= 255 && (value === 'android' || PACKAGE_NAME.test(value));
}
export function validSetting(namespace: string, key: string): boolean {
  return (namespace === 'system' || namespace === 'secure' || namespace === 'global') && SETTING_KEY.test(key);
}
export function quote(value: string): string {
  return "'" + value.replace(/'/g, "'\\''") + "'";
}
export function failure(code: string, message: string): DeviceToolFailure {
  return { ok: false, code, message };
}
