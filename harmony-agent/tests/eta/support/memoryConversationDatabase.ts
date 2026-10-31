// Test port for app/src/main/kotlin/io/github/mangi/eta/data/db/ConversationDao.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Room is replaced with an atomic in-memory DAO; this is not a device/RDB test.
import { ConversationDao, ConversationDatabase, ConversationRow, MessageRow, CheckpointRow, TextChunk, SerialGate } from '../../../entry/src/main/ets/core/eta/storage/conversationDao';
import { REFERENCE_PREFIX } from '../../../entry/src/main/ets/core/eta/storage/chunkedText';
interface MemoryState { conversations: Map<string, ConversationRow>; messages: Map<string, MessageRow>; checkpoints: Map<string, CheckpointRow>; chunks: Map<string, TextChunk>; selected: string | null; }
export interface Mutation { table: string; owner: string; field: string; }
const copy = <T>(value: T): T => structuredClone(value);
const chunkKey = (c: TextChunk): string => JSON.stringify([c.ownerTable, c.ownerId, c.field, c.chunkIndex]);
export class MemoryConversationDatabase implements ConversationDatabase, ConversationDao {
  state: MemoryState = { conversations: new Map(), messages: new Map(), checkpoints: new Map(), chunks: new Map(), selected: null };
  audit: Mutation[] = []; rejectMessage = ''; failCommit = false; private gate = new SerialGate();
  maxMessagePage = 0; maxChunkPage = 0;
  transaction<T>(work: (dao: ConversationDao) => Promise<T>): Promise<T> {
    return this.gate.run(async () => {
      const before = copy(this.state), audit = this.audit.slice();
      try { const result = await work(this); if (this.failCommit) throw Error('Injected transaction commit failure'); return result; }
      catch (error) { this.state = before; this.audit = audit; throw error; }
    });
  }
  private record(table: string, owner: string, field = ''): void { this.audit.push({ table, owner, field }); }
  async conversationIndex() {
    return [...this.state.conversations.values()].sort((a, b) => b.updatedAt - a.updatedAt || (a.id < b.id ? -1 : 1)).map(c => {
      const last = [...this.state.messages.values()].filter(m => m.conversationId === c.id).sort((a, b) => b.sortIndex - a.sortIndex)[0];
      const chunk = last && this.state.chunks.get(JSON.stringify(['conversation_messages', last.id, 'content', 0]));
      return { id: c.id, title: c.title, createdAt: c.createdAt, updatedAt: c.updatedAt, reasoningEffort: c.reasoningEffort, modelId: c.modelId,
        hasRoleplay: !!c.roleplayJson, messageId: last?.id ?? null, messageType: last?.type ?? null,
        messagePreview: last ? (chunk?.content ?? last.content).slice(0, 256) : null, toolName: last?.toolName ?? null };
    });
  }
  async conversationIdentities() { return [...this.state.conversations.values()].map(c => ({ id: c.id, title: c.title, createdAt: c.createdAt, updatedAt: c.updatedAt })); }
  async metadataRow(id: string) { const r = this.state.conversations.get(id); if (!r) return null; const { historyJson: _legacy, ...metadata } = r; return copy(metadata); }
  async putConversation(row: ConversationRow) { this.state.conversations.set(row.id, copy(row)); this.record('conversations', row.id); }
  async updateTitle(id: string, title: string, updatedAt: number) { const c = this.state.conversations.get(id); if (c) { this.state.conversations.set(id, { ...c, title, updatedAt }); this.record('conversations', id); } }
  async deleteConversation(id: string) {
    if (!this.state.conversations.has(id)) return;
    await this.deleteMessagesFrom(id, -1);
    this.state.conversations.delete(id); this.record('conversations', id);
    if (this.state.checkpoints.delete(id)) this.record('conversation_context_checkpoints', id);
    for (const [key, chunk] of this.state.chunks) if ((chunk.ownerTable === 'conversations' || chunk.ownerTable === 'conversation_context_checkpoints') && chunk.ownerId === id) { this.state.chunks.delete(key); this.record('agent_text_chunks', id, chunk.field); }
  }
  private rows(id: string): MessageRow[] { return [...this.state.messages.values()].filter(m => m.conversationId === id).sort((a, b) => a.sortIndex - b.sortIndex); }
  async messageRowsPage(id: string, limit: number, offset: number, legacy = false) {
    this.maxMessagePage = Math.max(this.maxMessagePage, limit);
    return copy(this.rows(id).filter(m => !legacy || m.type === 'user' || m.type === 'assistant').slice(offset, offset + limit));
  }
  async messageRowsBefore(id: string, before: number, limit: number) { this.maxMessagePage = Math.max(this.maxMessagePage, limit); return copy(this.rows(id).filter(m => m.sortIndex < before).slice(-limit)); }
  async searchRowsPage(limit: number, offset: number) { return copy([...this.state.messages.values()].sort((a, b) => a.conversationId === b.conversationId ? a.sortIndex - b.sortIndex : a.conversationId < b.conversationId ? -1 : 1).slice(offset, offset + limit)); }
  async messageContents(id: string, ids: string[]) { return this.rows(id).filter(m => ids.includes(m.id)).map(m => ({ id: m.id, content: m.content })); }
  async nextMessageIndex(id: string) { return Math.max(-1, ...this.rows(id).map(m => m.sortIndex)) + 1; }
  async hasMessagesBefore(id: string, before: number) { return this.rows(id).some(m => m.sortIndex < before); }
  async messageCount(id: string) { return this.rows(id).length; }
  async putMessage(row: MessageRow) {
    if (row.id === this.rejectMessage) throw Error('Injected message write failure');
    if (!this.state.conversations.has(row.conversationId)) throw Error('Foreign key');
    if (this.rows(row.conversationId).some(m => m.sortIndex === row.sortIndex && m.id !== row.id)) throw Error('Unique message index');
    this.state.messages.set(row.id, copy(row)); this.record('conversation_messages', row.id);
  }
  async deleteMessagesFrom(id: string, from: number) {
    for (const m of this.rows(id)) if (m.sortIndex >= from) {
      this.state.messages.delete(m.id); this.record('conversation_messages', m.id);
      for (const [key, c] of this.state.chunks) if (c.ownerTable === 'conversation_messages' && c.ownerId === m.id) { this.state.chunks.delete(key); this.record('agent_text_chunks', m.id, c.field); }
    }
  }
  async updateMessageContent(id: string, messageId: string, content: string) {
    const m = this.state.messages.get(messageId); if (!m || m.conversationId !== id) return false;
    this.state.messages.set(messageId, { ...m, content, isEdited: m.type === 'user' || m.isEdited }); this.record('conversation_messages', messageId); return true;
  }
  async checkpointRow(id: string) { return copy(this.state.checkpoints.get(id) ?? null); }
  async putCheckpoint(row: CheckpointRow) { if (!this.state.conversations.has(row.conversationId)) throw Error('Foreign key'); this.state.checkpoints.set(row.conversationId, copy(row)); this.record('conversation_context_checkpoints', row.conversationId); }
  async selectedId() { return this.state.selected; }
  async setSelectedId(id: string | null) { this.state.selected = id; }
  async textChunks(table: string, owner: string, field: string, limit: number, offset: number) { this.maxChunkPage = Math.max(this.maxChunkPage, limit); return copy([...this.state.chunks.values()].filter(c => c.ownerTable === table && c.ownerId === owner && c.field === field).sort((a, b) => a.chunkIndex - b.chunkIndex).slice(offset, offset + limit)); }
  async putTextChunk(chunk: TextChunk) { this.state.chunks.set(chunkKey(chunk), copy(chunk)); this.record('agent_text_chunks', chunk.ownerId, chunk.field); }
  async deleteTextChunks(table: string, owner: string, field: string) { for (const [key, c] of this.state.chunks) if (c.ownerTable === table && c.ownerId === owner && c.field === field) { this.state.chunks.delete(key); this.record('agent_text_chunks', owner, field); } }
  events(table: string, owner: string, field?: string): number { return this.audit.filter(e => e.table === table && e.owner === owner && (field === undefined || e.field === field)).length; }
}
