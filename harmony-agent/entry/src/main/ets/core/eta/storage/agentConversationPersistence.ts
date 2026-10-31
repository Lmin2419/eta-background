// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentConversationPersistence.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatUiState, AgentChatMessageUi, ConversationMessage } from '../model/conversation';
import { RoleplayBinding, RoleplayMessageState, emptyRoleplayMessages } from '../model/runtimeProjection';
import { SerialGate } from './conversationDao';
import { AgentConversationStore, ConversationSnapshot } from './agentConversationStore';
import { decodeRevisions, encodeRevisions, valueEqual } from './conversationCodec';
export interface ConversationContent {
  title: string; updatedAt: number; reasoningEffort: string; appliedRuntimeRunIds: string[];
  roleplay: RoleplayBinding | null; roleplayMessages: RoleplayMessageState; modelId: string | null;
  messages: AgentChatMessageUi[]; history: ConversationMessage[]; journal: ConversationMessage[];
  isConversationLoaded: boolean; messageStartIndex: number;
}
export function contentOf(state: AgentChatUiState, title: string, updatedAt: number): ConversationContent {
  // Kotlin immutable values become owned copies at this mutable TS port boundary.
  const copy = <T>(v: T): T => JSON.parse(JSON.stringify(v)) as T;
  return { title, updatedAt, reasoningEffort: state.reasoningEffort ?? (state.thinkingEnabled ? 'default' : 'off'),
    appliedRuntimeRunIds: state.appliedRuntimeRunIds.slice(), roleplay: state.roleplay ? copy(state.roleplay) : null,
    roleplayMessages: state.roleplay ? decodeRevisions(encodeRevisions(state.roleplayMessages)) : emptyRoleplayMessages(),
    modelId: state.modelId, messages: copy(state.messages), history: copy(state.history),
    journal: copy(state.journal.length ? state.journal : state.history),
    isConversationLoaded: state.isConversationLoaded, messageStartIndex: state.messageStartIndex };
}
export function projectSnapshot(snapshot: ConversationSnapshot): Map<string, ConversationContent> {
  return new Map([...snapshot.conversationsById].map(([id, state]) => [id, contentOf(state, snapshot.titles.get(id) || '', snapshot.updatedAt.get(id) || 0)]));
}
export function sameMetadata(a: ConversationContent, b: ConversationContent): boolean {
  return a.title === b.title && a.updatedAt === b.updatedAt && a.reasoningEffort === b.reasoningEffort &&
    valueEqual(a.appliedRuntimeRunIds, b.appliedRuntimeRunIds) && valueEqual(a.roleplay, b.roleplay) &&
    valueEqual(a.roleplayMessages, b.roleplayMessages) && a.modelId === b.modelId;
}
export class AgentConversationPersistence {
  private gate = new SerialGate(); private saved: Map<string, ConversationContent>;
  constructor(private store: AgentConversationStore, initial?: ConversationSnapshot) {
    this.saved = initial ? projectSnapshot(initial) : new Map();
    for (const id of initial?.unalignedConversationIds || []) this.saved.delete(id);
  }
  save(snapshot: ConversationSnapshot): Promise<void> {
    return this.gate.run(async () => {
      const current = projectSnapshot(snapshot);
      await this.store.saveChanges(snapshot.selectedConversationId, current, this.saved);
      this.saved = current; // Only after transaction commit; retries retain the previous baseline.
    });
  }
  adoptConversation(id: string, state: AgentChatUiState, title: string, updatedAt: number): Promise<void> {
    return this.gate.run(async () => { this.saved.set(id, contentOf(state, title, updatedAt)); });
  }
  forgetConversation(id: string): Promise<void> { return this.gate.run(async () => { this.saved.delete(id); }); }
}
