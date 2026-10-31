// Fixtures preserve the sizes and assertions of Eta storage/loading tests @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatUiState, emptyChatState, conversationMessage, userMessage, thinkingMessage } from '../../../entry/src/main/ets/core/eta/model/conversation';
import { RoleplayBinding } from '../../../entry/src/main/ets/core/eta/model/runtimeProjection';
import { AgentConversationStore, ConversationSnapshot } from '../../../entry/src/main/ets/core/eta/storage/agentConversationStore';
import { AgentConversationSession, ConversationSessionPorts } from '../../../entry/src/main/ets/core/eta/storage/agentConversationSession';
import { expandCharacterMacros } from '../../../entry/src/main/ets/core/eta/storage/characterMacroExpansion';
import { MemoryConversationDatabase } from './memoryConversationDatabase';
export function fixture() { const db = new MemoryConversationDatabase(); return { db, store: new AgentConversationStore(db, () => 123456) }; }
export function snapshot(states: Record<string, AgentChatUiState>, selected: string | null = Object.keys(states)[0] ?? null): ConversationSnapshot {
  return { selectedConversationId: selected, conversationsById: new Map(Object.entries(states)), titles: new Map(Object.keys(states).map(id => [id, id])), updatedAt: new Map(Object.keys(states).map(id => [id, 1])), unalignedConversationIds: new Set() };
}
export function storedState(id: string): AgentChatUiState {
  const history = [conversationMessage('assistant', '历史'.repeat(12000))];
  return { ...emptyChatState(), messages: [userMessage(`${id}-user`, '请求'), thinkingMessage(`${id}-thinking`, '思考'.repeat(12000))], history, journal: history };
}
export function loadingState(id: string, count = 3): AgentChatUiState {
  const messages = Array.from({ length: count }, (_, index) => userMessage(`${id}-${index}`, `${id}-message-${index}-end`));
  const history = messages.map(m => ({ ...conversationMessage('user', m.content), messageId: m.id }));
  return { ...emptyChatState(), messages, history, journal: history };
}
export function binding(): RoleplayBinding { return { characterId: 'character-1', characterJson: '{"data":{"name":"旅人"}}', name: '旅人', characterName: '旅人', userName: '朋友', userDescription: '同行的伙伴' }; }
export async function session(store: AgentConversationStore, ports: Partial<ConversationSessionPorts> = {}): Promise<AgentConversationSession> {
  let n = 0;
  const state = new AgentConversationSession(store, { uuid: () => `local-${++n}`, expandGreeting: (b, t) => expandCharacterMacros(t, { char: b.characterName ?? b.name, user: b.userName || '用户', persona: b.userDescription || '' }), ...ports });
  await state.initialize(); return state;
}
