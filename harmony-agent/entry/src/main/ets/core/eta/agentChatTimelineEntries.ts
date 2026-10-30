// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatBody.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatMessageUi } from './model/conversation';
export interface TimelineMessage { kind: 'message'; key: string; message: AgentChatMessageUi; }
export interface TimelineWorkProcess { kind: 'work'; key: string; messages: AgentChatMessageUi[]; }
export type AgentTimelineEntry = TimelineMessage | TimelineWorkProcess;
export function toTimelineEntries(messages: AgentChatMessageUi[], previous: AgentTimelineEntry[] = []): AgentTimelineEntry[] {
  const previousByMessage = new Map<string, TimelineWorkProcess>();
  for (const entry of previous) if (entry.kind === 'work') for (const message of entry.messages) previousByMessage.set(message.id, entry);
  const result: AgentTimelineEntry[] = [];
  let work: AgentChatMessageUi[] = [];
  const flush = (): void => {
    if (work.length === 0) return;
    const ids = new Set(work.map(x => x.id));
    const firstPrevious = work.map(x => previousByMessage.get(x.id)).find(x => x !== undefined);
    const old = firstPrevious?.messages.every(x => ids.has(x.id)) ? firstPrevious : undefined;
    const key = old?.key ?? 'work-' + work[0].id;
    const reusable = old?.messages.length === work.length && old.messages.every((x, i) => x === work[i]);
    result.push(reusable && old ? old : { kind: 'work', key, messages: work.slice() });
    work = [];
  };
  for (const message of messages) {
    if (message.kind === 'thinking' || message.kind === 'tool' || message.kind === 'toolSummary') work.push(message);
    else { flush(); result.push({ kind: 'message', key: message.id, message }); }
  }
  flush(); return result;
}
