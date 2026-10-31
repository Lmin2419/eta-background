// Ported from app/src/main/kotlin/io/github/mangi/eta/data/db/ConversationDao.kt,
// ConversationEntities.kt and ChunkedTextDao.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export interface ConversationRow {
  id: string; title: string; thinkingEnabled: boolean; reasoningEffort: string;
  historyJson: string; appliedRuntimeRunIdsJson: string; roleplayJson: string; revisionsJson: string;
  createdAt: number; updatedAt: number; modelId: string | null;
}
export function conversationRow(id: string, title = '', updatedAt = 1): ConversationRow {
  return { id, title, thinkingEnabled: false, reasoningEffort: 'default', historyJson: '[]',
    appliedRuntimeRunIdsJson: '[]', roleplayJson: '', revisionsJson: '', createdAt: updatedAt, updatedAt, modelId: null };
}
export type ConversationMetadata = Omit<ConversationRow, 'historyJson'>;
export interface ConversationIdentity { id: string; title: string; createdAt: number; updatedAt: number; }
export interface ConversationIndex extends ConversationIdentity {
  reasoningEffort: string; modelId: string | null; hasRoleplay: boolean;
  messageId: string | null; messageType: string | null; messagePreview: string | null; toolName: string | null;
}
export interface MessageRow {
  id: string; conversationId: string; sortIndex: number; type: string; content: string;
  imagesJson: string; isEdited: boolean; renderMarkdown: boolean | null;
  contextTokens: number | null; inputTokens: number | null; outputTokens: number | null;
  reasoningTokens: number | null; cachedTokens: number | null; elapsedSeconds: number | null;
  toolName: string | null; toolStatus: string | null; argumentsSummary: string | null;
  resultSummary: string | null; imageCount: number; toolsJson: string;
}
export function messageRow(id: string, conversationId: string, sortIndex: number, type: string, content: string): MessageRow {
  return { id, conversationId, sortIndex, type, content, imagesJson: '[]', isEdited: false, renderMarkdown: null,
    contextTokens: null, inputTokens: null, outputTokens: null, reasoningTokens: null, cachedTokens: null,
    elapsedSeconds: null, toolName: null, toolStatus: null, argumentsSummary: null, resultSummary: null,
    imageCount: 0, toolsJson: '[]' };
}
export interface CheckpointRow { conversationId: string; historyJson: string; journalJson: string; }
export interface TextChunk { ownerTable: string; ownerId: string; field: string; chunkIndex: number; content: string; }
/** Raw rows contain bounded chunk references. Domain code restores only requested fields. */
export interface ConversationDao {
  conversationIndex(): Promise<ConversationIndex[]>;
  conversationIdentities(): Promise<ConversationIdentity[]>;
  metadataRow(id: string): Promise<ConversationMetadata | null>;
  putConversation(row: ConversationRow): Promise<void>;
  updateTitle(id: string, title: string, updatedAt: number): Promise<void>;
  deleteConversation(id: string): Promise<void>;
  messageRowsPage(id: string, limit: number, offset: number, legacy?: boolean): Promise<MessageRow[]>;
  messageRowsBefore(id: string, before: number, limit: number): Promise<MessageRow[]>;
  searchRowsPage(limit: number, offset: number): Promise<MessageRow[]>;
  messageContents(id: string, ids: string[]): Promise<{ id: string; content: string }[]>;
  nextMessageIndex(id: string): Promise<number>;
  hasMessagesBefore(id: string, before: number): Promise<boolean>;
  messageCount(id: string): Promise<number>;
  putMessage(row: MessageRow): Promise<void>;
  deleteMessagesFrom(id: string, from: number): Promise<void>;
  updateMessageContent(id: string, messageId: string, stored: string): Promise<boolean>;
  checkpointRow(id: string): Promise<CheckpointRow | null>;
  putCheckpoint(row: CheckpointRow): Promise<void>;
  selectedId(): Promise<string | null>;
  setSelectedId(id: string | null): Promise<void>;
  textChunks(table: string, owner: string, field: string, limit: number, offset: number): Promise<TextChunk[]>;
  putTextChunk(chunk: TextChunk): Promise<void>;
  deleteTextChunks(table: string, owner: string, field: string): Promise<void>;
}
/** The adapter must provide serialized, atomic transactions, including read snapshots. */
export interface ConversationDatabase {
  transaction<T>(work: (dao: ConversationDao) => Promise<T>): Promise<T>;
}
export class SerialGate {
  private tail: Promise<void> = Promise.resolve();
  run<T>(work: () => Promise<T>): Promise<T> {
    const next = this.tail.then(work); this.tail = next.then(() => {}, () => {}); return next;
  }
}
