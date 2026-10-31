// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/ChatToolCallHistory.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { JsonObject, array, object } from './providerTypes';
function id(value: unknown): string | null { return typeof value === 'string' && value.trim() ? value : null; }
export function referencedIds(messages: unknown[]): Set<string> {
  const ids = new Set<string>();
  for (const item of messages) { const m = object(item); if (m?.role === 'assistant') for (const call of array(m.tool_calls)) { const value = id(object(call)?.id); if (value) ids.add(value); }
    if (m?.role === 'tool') { const value = id(m.tool_call_id); if (value) ids.add(value); } }
  return ids;
}
function completeBatch(messages: unknown[], index: number, calls: unknown[]): Map<string, JsonObject> | null {
  if (!calls.length) return null; const ids = new Set<string>(), results = new Map<string, JsonObject>();
  for (const call of calls) { const value = id(object(call)?.id); if (!value || ids.has(value)) return null; ids.add(value); }
  for (let i = index + 1; i < messages.length; i++) { const result = object(messages[i]); if (result?.role !== 'tool') break;
    const value = id(result.tool_call_id); if (!value || !ids.has(value) || results.has(value)) return null; results.set(value, result); }
  return results.size === ids.size ? results : null;
}
/** Only paired complete batches can be renamed. Non-object messages retain their original boundaries. */
export function repairCompleteBatches(messages: unknown[]): void {
  const reserved = referencedIds(messages), seen = new Set<string>();
  messages.forEach((item, i) => { const message = object(item); if (message?.role !== 'assistant') return;
    const calls = array(message.tool_calls), results = completeBatch(messages, i, calls);
    if (results) calls.forEach((raw, j) => { const call = object(raw)!, original = id(call.id)!; if (!seen.has(original)) return;
      const base = `call_eta_history_${i}_${j}`; let replacement = base, suffix = 0;
      while (reserved.has(replacement)) replacement = base + '_' + ++suffix;
      reserved.add(replacement); call.id = replacement; results.get(original)!.tool_call_id = replacement; });
    for (const call of calls) { const value = id(object(call)?.id); if (value) seen.add(value); }
  });
}
