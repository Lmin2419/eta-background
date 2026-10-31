// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/ProviderMessageContent.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { array, object, string } from './providerTypes';
export function providerMessageText(content: unknown): string {
  if (!Array.isArray(content)) return string(content);
  let text = '';
  for (const v of array(content)) { const item = object(v); if (!item || !['text', 'input_text', 'output_text'].includes(string(item.type))) continue; if (text) text += '\n'; text += string(item.text); }
  return text;
}
