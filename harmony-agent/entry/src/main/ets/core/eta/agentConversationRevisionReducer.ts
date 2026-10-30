// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentConversationRevisionReducer.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatMessageUi, AgentChatUiState, ConversationMessage, UserMessageUi } from './model/conversation';
export interface RevisionBoundary { userMessage: UserMessageUi; userMessageIndex: number; historyPrefix: ConversationMessage[]; journalPrefix: ConversationMessage[]; laterTurnCount: number; contextWasCompacted: boolean; }
export class AgentConversationRevisionReducer {
  static boundary(state: AgentChatUiState, targetMessageId: string): RevisionBoundary | null {
    const targetIndex = state.messages.findIndex(x => x.id === targetMessageId);
    if (targetIndex < 0) return null;
    let userMessageIndex = targetIndex;
    while (userMessageIndex >= 0 && state.messages[userMessageIndex].kind !== 'user') userMessageIndex--;
    if (userMessageIndex < 0) return null;
    const userMessage = state.messages[userMessageIndex] as UserMessageUi;
    const userMessageIndices = state.messages.flatMap((x, i) => x.kind === 'user' ? [i] : []);
    const ordinal = userMessageIndices.indexOf(userMessageIndex);
    const source = state.journal.length > 0 ? state.journal : state.history;
    const historyUsers = source.flatMap((x, i) => x.role === 'user' ? [i] : []);
    const retained = historyUsers.length - (userMessageIndices.length - ordinal);
    const historyIndex = retained < 0 ? undefined : historyUsers[retained];
    const prefix = historyIndex === undefined ? [] : source.slice(0, historyIndex);
    const targetTurn = prefix.reduce((sum, x) => sum + x.compactedUserTurns + (x.role === 'user' ? 1 : 0), 0) + 1;
    const invalidates = prefix.some(x => x.contextSummary && x.summaryThroughUserTurn >= targetTurn);
    const historyPrefix = prefix.filter(x => !(x.contextSummary && x.summaryThroughUserTurn >= targetTurn));
    return { userMessage, userMessageIndex, historyPrefix, journalPrefix: historyPrefix, laterTurnCount: userMessageIndices.length - ordinal - 1, contextWasCompacted: historyIndex === undefined || invalidates };
  }
  static deleteFromTurn(state: AgentChatUiState, targetMessageId: string): AgentChatUiState | null {
    const boundary = this.boundary(state, targetMessageId);
    return boundary ? { ...state, messages: state.messages.slice(0, boundary.userMessageIndex), history: boundary.historyPrefix, journal: boundary.journalPrefix, messageEdit: null } : null;
  }
  static visibleMessagesForEdit(messages: AgentChatMessageUi[], targetMessageId: string | null): AgentChatMessageUi[] {
    if (targetMessageId === null) return messages;
    const index = messages.findIndex(x => x.id === targetMessageId);
    return index < 0 ? messages : messages.slice(0, index + 1);
  }
}
