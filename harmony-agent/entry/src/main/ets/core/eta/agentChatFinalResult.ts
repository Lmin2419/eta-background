// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatBody.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatMessageUi } from './model/conversation';
export function resolveFinalResultMessageIds(messages: AgentChatMessageUi[], isStreaming: boolean = false): Set<string> {
  const ids = new Set<string>(); let last: string | null = null;
  for (const message of messages) {
    if (message.kind === 'user') { if (last !== null) ids.add(last); last = null; }
    else if (message.kind === 'assistant') last = message.id;
  }
  if (!isStreaming && last !== null) ids.add(last);
  return ids;
}
