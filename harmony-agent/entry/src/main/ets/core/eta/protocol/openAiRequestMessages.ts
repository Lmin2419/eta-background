// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/OpenAiRequestMessages.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { JsonObject, object, copy } from './providerTypes';
import { providerMessageText } from './providerMessageContent';
import { repairCompleteBatches } from './chatToolCallHistory';
const INTERNAL = ['_eta_context_summary', '_eta_compacted_users', '_eta_summary_through_user', '_eta_observation', '_eta_message_id', '_eta_character_profile'];
export function forChatCompletions(messages: unknown[]): JsonObject[] {
  const copied = copy(messages); repairCompleteBatches(copied);
  const instructions = copied.map(object).filter(m => m?.role === 'system').map(m => providerMessageText(m!.content).trim()).filter(Boolean).join('\n\n');
  const result: JsonObject[] = instructions ? [{ role: 'system', content: instructions }] : [];
  for (const value of copied) { const m = object(value); if (!m || m.role === 'system') continue; for (const key of INTERNAL) delete m[key]; result.push(m); }
  return result;
}
