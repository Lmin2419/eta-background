// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AssistantConversationSeed.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatUiState, conversationMessage, userMessage } from './model/conversation';
import { AgentRuntimeHistoryReducer } from './agentRuntimeHistoryReducer';
export class AssistantConversationSeed {
  static apply(state: AgentChatUiState, runId: string, prompt: string): AgentChatUiState {
    const id = `user-${runId}`;
    if (AgentRuntimeHistoryReducer.wasApplied(state, runId) || state.messages.some(x => x.id === id)) return state;
    const user = { ...conversationMessage('user', prompt), messageId: id };
    return { ...state, messages: [...state.messages, userMessage(id, prompt)], history: [...state.history, user], journal: [...(state.journal.length > 0 ? state.journal : state.history), user] };
  }
}
