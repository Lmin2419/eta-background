// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/RequestBodyMerge.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { CustomBody, JsonObject, object, copy } from './providerTypes';
function put(target: JsonObject, key: string, value: unknown): void { Object.defineProperty(target, key, { value, enumerable: true, configurable: true, writable: true }); }
function merge(target: JsonObject, key: string, value: unknown): void {
  const source = object(value), current = Object.prototype.hasOwnProperty.call(target, key) ? object(target[key]) : null;
  if (source && current) for (const [k, v] of Object.entries(source)) merge(current, k, v); else put(target, key, copy(value));
}
export function mergeCustomBody(target: JsonObject, body: CustomBody[]): void { for (const field of body) merge(target, field.key, field.value); }
export function mergeExtraBody(target: JsonObject, raw = ''): void {
  if (!raw.trim()) return; let value: JsonObject | null;
  try { value = object(JSON.parse(raw)); } catch (_) { throw Error('自定义请求体不是有效 JSON 对象'); }
  if (!value) throw Error('自定义请求体不是有效 JSON 对象'); for (const [key, v] of Object.entries(value)) put(target, key, v);
}
