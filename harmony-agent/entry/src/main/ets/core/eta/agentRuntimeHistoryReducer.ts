// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentRuntimeHistoryReducer.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatUiState, AgentContextSnapshot, ConversationMessage, conversationMessage } from './model/conversation';
export interface HistoryOutcome { state: AgentChatUiState; alreadyApplied: boolean; }
function suffixOrdinal(id: string): number { const s = id.slice(id.lastIndexOf('-') + 1); const value = Number(s); return /^[+-]?\d+$/.test(s) && Number.isInteger(value) && value >= -2147483648 && value <= 2147483647 ? value : 0; }
export class AgentRuntimeHistoryReducer {
  static apply(state: AgentChatUiState, runId: string, additions: ConversationMessage[], snapshot: AgentContextSnapshot | null = null, retainPendingSupplements: boolean = snapshot !== null): HistoryOutcome {
    if (this.wasApplied(state, runId)) return { state, alreadyApplied: true };
    const valid = snapshot?.operationId === runId ? snapshot : null;
    const consumed = Math.max(additions.filter(x => x.role === 'user').length, valid?.consumedSupplementCount ?? 0);
    const supplements = !retainPendingSupplements ? [] : state.messages.filter(x => x.kind === 'user' && x.id.startsWith(`user-${runId}-supplement-`))
      .sort((a, b) => suffixOrdinal(a.id) - suffixOrdinal(b.id)).slice(consumed)
      .map(x => ({ ...conversationMessage('user', x.kind === 'user' ? x.content : ''), messageId: x.id }));
    let history: ConversationMessage[];
    if (valid) {
      const turns = state.history.reduce((sum, x) => sum + x.compactedUserTurns + (x.role === 'user' ? 1 : 0), 0);
      const extra = Math.max(0, turns - valid.consumedUserTurns);
      const users = state.history.flatMap((x, i) => x.role === 'user' ? [i] : []);
      const firstLater = extra > 0 ? users.slice(-extra)[0] : undefined;
      // Kotlin first() also rejects an inconsistent snapshot with no retained user.
      if (extra > 0 && firstLater === undefined) throw new Error('Invalid retained user boundary');
      const later = firstLater === undefined ? [] : state.history.slice(firstLater);
      const covered = valid.consumedTranscriptMessages ?? additions.length;
      if (additions.length > 0 && (covered < 0 || covered > additions.length)) throw new Error('Invalid context transcript boundary');
      if (covered < 0) throw new Error('Invalid context transcript boundary');
      history = [...valid.messages, ...additions.slice(covered), ...later, ...supplements];
    } else history = [...state.history, ...additions, ...supplements];
    return { state: { ...state, journal: [...(state.journal.length > 0 ? state.journal : state.history), ...additions, ...supplements], history, appliedRuntimeRunIds: [...state.appliedRuntimeRunIds, runId] }, alreadyApplied: false };
  }
  static wasApplied(state: AgentChatUiState, runId: string): boolean { return state.appliedRuntimeRunIds.includes(runId); }
}
