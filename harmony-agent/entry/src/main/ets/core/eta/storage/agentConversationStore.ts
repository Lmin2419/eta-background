// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentConversationStore.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { AgentChatUiState, AgentChatMessageUi, ConversationMessage, SystemNoticeCode, emptyChatState, conversationMessage } from '../model/conversation';
import { emptyRoleplayMessages, revisionContent } from '../model/runtimeProjection';
import { RoleplayConversationReducer } from '../roleplayConversationReducer';
import { ConversationDao, ConversationDatabase, MessageRow, ConversationRow, messageRow } from './conversationDao';
import { ConversationContent, projectSnapshot, sameMetadata } from './agentConversationPersistence';
import { decodeRevisions, encodeRevisions, decodeTranscript, encodeTranscript, valueEqual, parseEffort, strings, noticeCodes, toMessageRow, fromMessageRow } from './conversationCodec';
import { storeText, restoreText, TextReader, containsText, streamContains } from './chunkedText';
import { readRoleplaySummary } from './roleplaySummaryReader';
export const MESSAGE_PAGE_SIZE = 128;
export interface ConversationSnapshot {
  selectedConversationId: string | null; conversationsById: Map<string, AgentChatUiState>;
  titles: Map<string, string>; updatedAt: Map<string, number>; unalignedConversationIds: Set<string>;
}
export function emptyConversationSnapshot(): ConversationSnapshot { return { selectedConversationId: null, conversationsById: new Map(), titles: new Map(), updatedAt: new Map(), unalignedConversationIds: new Set() }; }
export interface MessagePage { messages: AgentChatMessageUi[]; startIndex: number; hasEarlierMessages: boolean; }
export async function putConversation(dao: ConversationDao, row: ConversationRow): Promise<void> {
  await dao.putConversation({ ...row,
    appliedRuntimeRunIdsJson: await storeText(dao, 'conversations', row.id, 'runs', row.appliedRuntimeRunIdsJson),
    roleplayJson: await storeText(dao, 'conversations', row.id, 'roleplay', row.roleplayJson),
    revisionsJson: await storeText(dao, 'conversations', row.id, 'revisions', row.revisionsJson) });
}
export async function putMessage(dao: ConversationDao, row: MessageRow): Promise<void> {
  await dao.putMessage({ ...row, content: await storeText(dao, 'conversation_messages', row.id, 'content', row.content),
    imagesJson: await storeText(dao, 'conversation_messages', row.id, 'images', row.imagesJson) });
}
async function restoreMessage(dao: ConversationDao, row: MessageRow): Promise<MessageRow> {
  return { ...row, content: await restoreText(dao, 'conversation_messages', row.id, 'content', row.content),
    imagesJson: await restoreText(dao, 'conversation_messages', row.id, 'images', row.imagesJson) };
}
async function messages(dao: ConversationDao, rows: MessageRow[]): Promise<AgentChatMessageUi[]> {
  const result: AgentChatMessageUi[] = [];
  for (const row of rows) { const value = fromMessageRow(await restoreMessage(dao, row)); if (value) result.push(value); }
  return result;
}
export class AgentConversationStore {
  constructor(readonly database: ConversationDatabase, private now: () => number = Date.now, private failure: (phase: string) => void = () => {}) {}
  save(snapshot: ConversationSnapshot): Promise<void> { return this.saveChanges(snapshot.selectedConversationId, projectSnapshot(snapshot), new Map()); }
  async saveChanges(selectedId: string | null, current: Map<string, ConversationContent>, previous: Map<string, ConversationContent>): Promise<void> {
    let phase = 'read_metadata';
    try {
      await this.database.transaction(async dao => {
        const stored = new Map((await dao.conversationIdentities()).map(row => [row.id, row]));
        phase = 'delete_conversations';
        for (const id of stored.keys()) if (!current.has(id)) await dao.deleteConversation(id);
        for (const [id, state] of current) {
          if (!state.isConversationLoaded) {
            const row = stored.get(id);
            if (row && (row.title !== state.title || row.updatedAt !== state.updatedAt)) await dao.updateTitle(id, state.title, state.updatedAt);
            continue;
          }
          const prior = previous.get(id), old = stored.has(id) && prior?.isConversationLoaded ? prior : undefined;
          if (!old || !sameMetadata(state, old)) {
            phase = 'write_metadata';
            await putConversation(dao, { id, title: state.title, thinkingEnabled: state.reasoningEffort !== 'off', reasoningEffort: state.reasoningEffort,
              historyJson: '[]', appliedRuntimeRunIdsJson: JSON.stringify(state.appliedRuntimeRunIds),
              roleplayJson: state.roleplay ? JSON.stringify(state.roleplay) : '', revisionsJson: state.roleplay ? encodeRevisions(state.roleplayMessages) : '',
              createdAt: stored.get(id)?.createdAt ?? (state.updatedAt || this.now()), updatedAt: state.updatedAt || this.now(), modelId: state.modelId });
          }
          if (!old || !valueEqual(state.history, old.history) || !valueEqual(state.journal, old.journal)) {
            const checkpoint = await dao.checkpointRow(id); phase = 'write_checkpoint';
            await dao.putCheckpoint({ conversationId: id,
              historyJson: !checkpoint || !old || !valueEqual(state.history, old.history) ? await storeText(dao, 'conversation_context_checkpoints', id, 'history', encodeTranscript(state.history)) : checkpoint.historyJson,
              journalJson: !checkpoint || !old || !valueEqual(state.journal, old.journal) ? await storeText(dao, 'conversation_context_checkpoints', id, 'journal', encodeTranscript(state.journal)) : checkpoint.journalJson });
          }
          const oldMessages = old?.messages || [];
          const aligned = old && old.messageStartIndex === state.messageStartIndex && await dao.nextMessageIndex(id) === old.messageStartIndex + oldMessages.length;
          let prefix = 0;
          while (aligned && prefix < Math.min(oldMessages.length, state.messages.length)) {
            if (!valueEqual(oldMessages[prefix], state.messages[prefix]) && !valueEqual(toMessageRow(oldMessages[prefix], id, state.messageStartIndex + prefix), toMessageRow(state.messages[prefix], id, state.messageStartIndex + prefix))) break;
            prefix++;
          }
          if (!old || !valueEqual(old.messages, state.messages) || old.messageStartIndex !== state.messageStartIndex) {
            phase = 'delete_message_suffix'; await dao.deleteMessagesFrom(id, state.messageStartIndex + prefix);
            for (let i = prefix; i < state.messages.length; i++) {
              const row = toMessageRow(state.messages[i], id, state.messageStartIndex + i);
              if (row) { phase = 'write_message'; await putMessage(dao, row); }
            }
          }
          if (state.roleplay && !valueEqual(state.roleplayMessages.revisions, old?.roleplayMessages.revisions)) {
            const visible = new Set(state.messages.map(m => m.id));
            for (const [messageId, revision] of state.roleplayMessages.revisions) {
              if (!visible.has(messageId) && !valueEqual(revision, old?.roleplayMessages.revisions.get(messageId))) {
                phase = 'write_offscreen_revision';
                const found = await dao.messageContents(id, [messageId]);
                if (found.length) await dao.updateMessageContent(id, messageId, await storeText(dao, 'conversation_messages', messageId, 'content', revisionContent(revision)));
                else if (old) throw Error('Roleplay revision message missing');
              }
            }
          }
        }
        phase = 'write_selection';
        const fallback = [...current].sort((a, b) => b[1].updatedAt - a[1].updatedAt)[0]?.[0] ?? null;
        await dao.setSelectedId(selectedId !== null && current.has(selectedId) ? selectedId : fallback);
      });
    } catch (error) { this.failure(phase); throw error; }
  }
  load(): Promise<ConversationSnapshot> {
    return this.database.transaction(async dao => {
      const snapshot = emptyConversationSnapshot(), index = await dao.conversationIndex();
      for (const row of index) {
        const effort = parseEffort(row.reasoningEffort);
        const preview = row.messageId ? fromMessageRow({ ...messageRow(row.messageId, row.id, 0, row.messageType || '', row.messagePreview || ''), toolName: row.toolName }) : null;
        const metadata = row.hasRoleplay ? await dao.metadataRow(row.id) : null;
        const summary = metadata ? await readRoleplaySummary(new TextReader(dao, 'conversations', row.id, 'roleplay', metadata.roleplayJson)) : null;
        snapshot.conversationsById.set(row.id, { ...emptyChatState(), messages: preview ? [preview] : [], reasoningEffort: effort,
          thinkingEnabled: effort !== 'off', modelId: row.modelId, isConversationLoaded: false,
          conversationCharacterName: summary?.name ?? null, conversationCharacterAvatarPath: summary?.avatarPath ?? null });
        snapshot.titles.set(row.id, row.title === '新对话' ? '' : row.title); snapshot.updatedAt.set(row.id, row.updatedAt);
      }
      const savedId = await dao.selectedId(); snapshot.selectedConversationId = savedId && snapshot.conversationsById.has(savedId) ? savedId : index[0]?.id ?? null;
      if (snapshot.selectedConversationId) {
        const id = snapshot.selectedConversationId, loaded = await this.readConversation(dao, id);
        if (loaded) { snapshot.conversationsById.set(id, loaded); if (await dao.nextMessageIndex(id) !== loaded.messageStartIndex + loaded.messages.length) snapshot.unalignedConversationIds.add(id); }
      }
      return snapshot;
    });
  }
  private async page(dao: ConversationDao, id: string, before: number): Promise<MessagePage> {
    const rows = await dao.messageRowsBefore(id, before, MESSAGE_PAGE_SIZE), startIndex = rows[0]?.sortIndex ?? 0;
    return { messages: await messages(dao, rows), startIndex, hasEarlierMessages: await dao.hasMessagesBefore(id, startIndex) };
  }
  private async readConversation(dao: ConversationDao, id: string): Promise<AgentChatUiState | null> {
    const metadata = await dao.metadataRow(id); if (!metadata) return null;
    const page = await this.page(dao, id, Number.MAX_SAFE_INTEGER), cp = await dao.checkpointRow(id);
    const decode = async (field: string, raw: string): Promise<ConversationMessage[]> => decodeTranscript(await restoreText(dao, 'conversation_context_checkpoints', id, field, raw));
    const journal = await decode('journal', cp?.journalJson || ''), history = await decode('history', cp?.historyJson || '');
    if (!history.length) for (let offset = 0;;) {
      const rows = await dao.messageRowsPage(id, MESSAGE_PAGE_SIZE, offset, true);
      for (const row of rows) { const r = await restoreMessage(dao, row); if (r.type === 'user' || (r.type === 'assistant' && r.content.trim())) history.push(conversationMessage(r.type, r.content)); }
      if (rows.length < MESSAGE_PAGE_SIZE) break; offset += rows.length;
    }
    const roleplay = await restoreText(dao, 'conversations', id, 'roleplay', metadata.roleplayJson), effort = parseEffort(metadata.reasoningEffort);
    let state: AgentChatUiState = { ...emptyChatState(), messages: page.messages, history, journal,
      roleplay: roleplay.trim() ? JSON.parse(roleplay) : null,
      roleplayMessages: decodeRevisions(await restoreText(dao, 'conversations', id, 'revisions', metadata.revisionsJson)),
      appliedRuntimeRunIds: strings(await restoreText(dao, 'conversations', id, 'runs', metadata.appliedRuntimeRunIdsJson)),
      reasoningEffort: effort, thinkingEnabled: effort !== 'off', modelId: metadata.modelId,
      messageStartIndex: page.startIndex, hasEarlierMessages: page.hasEarlierMessages };
    state = { ...state, roleplayOriginalContents: await this.originalContents(dao, id, RoleplayConversationReducer.requiredOriginalContentIds(state)) };
    return RoleplayConversationReducer.decorate(state);
  }
  loadConversation(id: string): Promise<AgentChatUiState | null> { return this.database.transaction(dao => this.readConversation(dao, id)); }
  loadEarlierMessages(id: string, before: number): Promise<MessagePage> { return this.database.transaction(dao => this.page(dao, id, before)); }
  loadRecentMessages(id: string): Promise<MessagePage> { return this.loadEarlierMessages(id, Number.MAX_SAFE_INTEGER); }
  static shouldReleaseEarlierMessages(count: number): boolean { return count > MESSAGE_PAGE_SIZE * 3; }
  private async originalContents(dao: ConversationDao, id: string, ids: Set<string>): Promise<Map<string, string>> {
    const result = new Map<string, string>(), keys = [...ids];
    for (let offset = 0; offset < keys.length; offset += MESSAGE_PAGE_SIZE) for (const r of await dao.messageContents(id, keys.slice(offset, offset + MESSAGE_PAGE_SIZE))) result.set(r.id, await restoreText(dao, 'conversation_messages', r.id, 'content', r.content));
    return result;
  }
  loadRoleplayMessageContents(id: string, ids: Set<string>): Promise<Map<string, string>> { return this.database.transaction(dao => this.originalContents(dao, id, ids)); }
  loadAllMessages(id: string): Promise<AgentChatMessageUi[]> {
    return this.database.transaction(async dao => {
      const result: AgentChatMessageUi[] = [];
      for (let offset = 0;;) { const rows = await dao.messageRowsPage(id, MESSAGE_PAGE_SIZE, offset); result.push(...await messages(dao, rows)); if (rows.length < MESSAGE_PAGE_SIZE) break; offset += rows.length; }
      return result;
    });
  }
  searchConversationIds(query: string, codes: SystemNoticeCode[] = [], checkCancelled: () => void = () => {}): Promise<Set<string>> {
    return this.database.transaction(async dao => {
      const found = new Set<string>(); if (!query.trim()) return found;
      const wireCodes = codes.map(c => noticeCodes[c]);
      for (let offset = 0;;) {
        const rows = await dao.searchRowsPage(MESSAGE_PAGE_SIZE, offset);
        for (const r of rows) {
          checkCancelled(); if (found.has(r.conversationId)) continue;
          const body = ['user', 'assistant', 'thinking', 'tool'].includes(r.type) && await streamContains(new TextReader(dao, 'conversation_messages', r.id, 'content', r.content), query, checkCancelled);
          const details = r.type === 'tool' ? [r.toolName, r.argumentsSummary, r.resultSummary] : r.type === 'system_notice' ? [r.resultSummary] : r.type === 'tool_summary' ? strings(r.toolsJson) : [];
          if (body || details.some(v => v !== null && containsText(v, query)) || (r.type === 'system_notice' && wireCodes.includes(r.content))) found.add(r.conversationId);
        }
        if (rows.length < MESSAGE_PAGE_SIZE) break; offset += rows.length;
      }
      return found;
    });
  }
  static unloadConversation(state: AgentChatUiState): AgentChatUiState {
    const last = state.messages[state.messages.length - 1]; let preview = last;
    if (last?.kind === 'user') preview = { ...last, content: last.content.slice(0, 256), images: [] };
    else if (last?.kind === 'assistant') preview = { ...last, content: last.content.slice(0, 256), searchSuggestionsHtml: [] };
    else if (last?.kind === 'thinking') preview = { ...last, content: last.content.slice(0, 256) };
    else if (last?.kind === 'tool') preview = { ...last, argumentsSummary: last.argumentsSummary.slice(0, 256), command: last.command?.slice(0, 256) ?? null, resultSummary: last.resultSummary?.slice(0, 256) ?? null };
    return { ...state, messages: preview ? [preview] : [], history: [], journal: [], roleplay: null, roleplayMessages: emptyRoleplayMessages(),
      appliedRuntimeRunIds: [], isConversationLoaded: false, messageStartIndex: 0, hasEarlierMessages: false,
      conversationCharacterName: state.roleplay?.characterName ?? state.roleplay?.name ?? state.conversationCharacterName,
      conversationCharacterAvatarPath: state.roleplay?.avatarPath ?? state.conversationCharacterAvatarPath, roleplayOriginalContents: new Map() };
  }
}
