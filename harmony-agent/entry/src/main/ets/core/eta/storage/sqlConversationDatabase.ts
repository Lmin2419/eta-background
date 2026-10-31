// Ported from app/src/main/kotlin/io/github/mangi/eta/data/db/ConversationDao.kt,
// ConversationEntities.kt, ChunkedTextDao.kt and EtaDatabase.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ConversationDatabase, ConversationDao, ConversationRow, ConversationMetadata, ConversationIdentity, ConversationIndex, MessageRow, CheckpointRow, TextChunk, SerialGate } from './conversationDao';
import { MigrationDao, MigrationDatabase } from './migrationPorts';
export type SqlValue = string | number | null;
export type SqlRow = Record<string, SqlValue>;
/** Each connection belongs exclusively to this database's serial transaction owner. */
export interface ConversationSqlConnection {
  execute(sql: string, args?: SqlValue[]): Promise<void>;
  query(sql: string, args?: SqlValue[]): Promise<SqlRow[]>;
  begin(): Promise<void>; commit(): Promise<void>; rollback(): Promise<void>;
}
const CONVERSATION_COLUMNS = 'id,title,thinkingEnabled,reasoningEffort,historyJson,appliedRuntimeRunIdsJson,roleplayJson,revisionsJson,createdAt,updatedAt,modelId';
const METADATA_COLUMNS = CONVERSATION_COLUMNS.split(',').filter(c => c !== 'historyJson').join(',');
const MESSAGE_COLUMNS = 'id,conversationId,sortIndex,type,content,imagesJson,isEdited,renderMarkdown,contextTokens,inputTokens,outputTokens,reasoningTokens,cachedTokens,elapsedSeconds,toolName,toolStatus,argumentsSummary,resultSummary,imageCount,toolsJson';
export const CONVERSATION_SCHEMA: string[] = [
  `CREATE TABLE IF NOT EXISTS conversations (id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,thinkingEnabled INTEGER NOT NULL,reasoningEffort TEXT NOT NULL,historyJson TEXT NOT NULL,appliedRuntimeRunIdsJson TEXT NOT NULL,roleplayJson TEXT NOT NULL,revisionsJson TEXT NOT NULL,createdAt REAL NOT NULL,updatedAt REAL NOT NULL,modelId TEXT)`,
  `CREATE TABLE IF NOT EXISTS conversation_messages (id TEXT PRIMARY KEY NOT NULL,conversationId TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,sortIndex INTEGER NOT NULL,type TEXT NOT NULL,content TEXT NOT NULL,imagesJson TEXT NOT NULL,isEdited INTEGER NOT NULL,renderMarkdown INTEGER,contextTokens INTEGER,inputTokens INTEGER,outputTokens INTEGER,reasoningTokens INTEGER,cachedTokens INTEGER,elapsedSeconds REAL,toolName TEXT,toolStatus TEXT,argumentsSummary TEXT,resultSummary TEXT,imageCount INTEGER NOT NULL,toolsJson TEXT NOT NULL,UNIQUE(conversationId,sortIndex))`,
  `CREATE TABLE IF NOT EXISTS conversation_context_checkpoints (conversationId TEXT PRIMARY KEY NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,historyJson TEXT NOT NULL,journalJson TEXT NOT NULL)`,
  `CREATE TABLE IF NOT EXISTS agent_text_chunks (ownerTable TEXT NOT NULL,ownerId TEXT NOT NULL,field TEXT NOT NULL,chunkIndex INTEGER NOT NULL,content TEXT NOT NULL,PRIMARY KEY(ownerTable,ownerId,field,chunkIndex))`,
  `CREATE TABLE IF NOT EXISTS conversation_selection (slot INTEGER PRIMARY KEY CHECK(slot=1),conversationId TEXT)`,
  `CREATE TABLE IF NOT EXISTS migration_records (key TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL)`,
  `CREATE TABLE IF NOT EXISTS migration_diagnostics (sequence INTEGER PRIMARY KEY AUTOINCREMENT,code TEXT NOT NULL,at REAL NOT NULL)`,
  `CREATE TRIGGER IF NOT EXISTS delete_message_chunks AFTER DELETE ON conversation_messages BEGIN DELETE FROM agent_text_chunks WHERE ownerTable='conversation_messages' AND ownerId=OLD.id; END`,
  `CREATE TRIGGER IF NOT EXISTS delete_conversation_chunks AFTER DELETE ON conversations BEGIN DELETE FROM agent_text_chunks WHERE ownerId=OLD.id AND ownerTable IN ('conversations','conversation_context_checkpoints'); END`,
  `CREATE INDEX IF NOT EXISTS conversation_updated_index ON conversations(updatedAt DESC,id)`,
];
function rowMessage(row: SqlRow): MessageRow { return { ...row, isEdited: row.isEdited === 1, renderMarkdown: row.renderMarkdown === null ? null : row.renderMarkdown === 1 } as unknown as MessageRow; }
function rowMetadata(row: SqlRow): ConversationMetadata { return { ...row, thinkingEnabled: row.thinkingEnabled === 1 } as unknown as ConversationMetadata; }
function bindings(row: object, columns: string): SqlValue[] {
  const values = row as Record<string, string | number | boolean | null>;
  return columns.split(',').map(key => typeof values[key] === 'boolean' ? values[key] ? 1 : 0 : values[key] as SqlValue);
}
function upsert(table: string, columns: string, key: string): string {
  const fields = columns.split(',');
  // REPLACE would delete the parent and cascade its messages. Use UPDATE on conflict.
  return `INSERT INTO ${table}(${columns}) VALUES(${fields.map(() => '?').join(',')}) ON CONFLICT(${key}) DO UPDATE SET ${fields.filter(f => !key.split(',').includes(f)).map(f => `${f}=excluded.${f}`).join(',')}`;
}
/** Not exported: callers cannot retain a DAO and write outside its transaction. */
class SqlConversationDao implements MigrationDao {
  active = true;
  constructor(private sql: ConversationSqlConnection) {}
  private check(): void { if (!this.active) throw Error('Conversation transaction is closed'); }
  private run(sql: string, args: SqlValue[] = []): Promise<void> { this.check(); return this.sql.execute(sql, args); }
  private rows(sql: string, args: SqlValue[] = []): Promise<SqlRow[]> { this.check(); return this.sql.query(sql, args); }
  async conversationIndex(): Promise<ConversationIndex[]> {
    const rows = await this.rows(`SELECT c.id,c.title,c.createdAt,c.updatedAt,c.reasoningEffort,c.modelId,(c.roleplayJson<>'') AS hasRoleplay,m.id AS messageId,m.type AS messageType,substr(COALESCE(t.content,m.content),1,256) AS messagePreview,m.toolName FROM conversations c LEFT JOIN conversation_messages m ON m.conversationId=c.id AND m.sortIndex=(SELECT MAX(last.sortIndex) FROM conversation_messages last WHERE last.conversationId=c.id) LEFT JOIN agent_text_chunks t ON t.ownerTable='conversation_messages' AND t.ownerId=m.id AND t.field='content' AND t.chunkIndex=0 ORDER BY c.updatedAt DESC,c.id`);
    return rows.map(r => ({ ...r, hasRoleplay: r.hasRoleplay === 1 }) as unknown as ConversationIndex);
  }
  async conversationIdentities(): Promise<ConversationIdentity[]> { return await this.rows('SELECT id,title,createdAt,updatedAt FROM conversations ORDER BY id') as unknown as ConversationIdentity[]; }
  async metadataRow(id: string): Promise<ConversationMetadata | null> { const rows = await this.rows(`SELECT ${METADATA_COLUMNS} FROM conversations WHERE id=?`, [id]); return rows.length ? rowMetadata(rows[0]) : null; }
  putConversation(row: ConversationRow): Promise<void> { return this.run(upsert('conversations', CONVERSATION_COLUMNS, 'id'), bindings(row, CONVERSATION_COLUMNS)); }
  updateTitle(id: string, title: string, updatedAt: number): Promise<void> { return this.run('UPDATE conversations SET title=?,updatedAt=? WHERE id=?', [title, updatedAt, id]); }
  deleteConversation(id: string): Promise<void> { return this.run('DELETE FROM conversations WHERE id=?', [id]); }
  async messageRowsPage(id: string, limit: number, offset: number, legacy = false): Promise<MessageRow[]> { return (await this.rows(`SELECT ${MESSAGE_COLUMNS} FROM conversation_messages WHERE conversationId=? ${legacy ? "AND type IN ('user','assistant')" : ''} ORDER BY sortIndex LIMIT ? OFFSET ?`, [id, limit, offset])).map(rowMessage); }
  async messageRowsBefore(id: string, before: number, limit: number): Promise<MessageRow[]> { return (await this.rows(`SELECT ${MESSAGE_COLUMNS} FROM conversation_messages WHERE conversationId=? AND sortIndex<? ORDER BY sortIndex DESC LIMIT ?`, [id, before, limit])).reverse().map(rowMessage); }
  async searchRowsPage(limit: number, offset: number): Promise<MessageRow[]> { return (await this.rows(`SELECT ${MESSAGE_COLUMNS} FROM conversation_messages ORDER BY conversationId,sortIndex LIMIT ? OFFSET ?`, [limit, offset])).map(rowMessage); }
  async messageContents(id: string, ids: string[]): Promise<{ id: string; content: string }[]> {
    if (!ids.length) return [];
    return await this.rows(`SELECT id,content FROM conversation_messages WHERE conversationId=? AND id IN (${ids.map(() => '?').join(',')}) ORDER BY sortIndex`, [id, ...ids]) as { id: string; content: string }[];
  }
  async nextMessageIndex(id: string): Promise<number> { return Number((await this.rows('SELECT COALESCE(MAX(sortIndex),-1)+1 AS value FROM conversation_messages WHERE conversationId=?', [id]))[0].value); }
  async hasMessagesBefore(id: string, before: number): Promise<boolean> { return (await this.rows('SELECT 1 FROM conversation_messages WHERE conversationId=? AND sortIndex<? LIMIT 1', [id, before])).length > 0; }
  async messageCount(id: string): Promise<number> { return Number((await this.rows('SELECT COUNT(*) AS value FROM conversation_messages WHERE conversationId=?', [id]))[0].value); }
  putMessage(row: MessageRow): Promise<void> { return this.run(upsert('conversation_messages', MESSAGE_COLUMNS, 'id'), bindings(row, MESSAGE_COLUMNS)); }
  deleteMessagesFrom(id: string, from: number): Promise<void> { return this.run('DELETE FROM conversation_messages WHERE conversationId=? AND sortIndex>=?', [id, from]); }
  async updateMessageContent(id: string, messageId: string, stored: string): Promise<boolean> {
    if (!(await this.messageContents(id, [messageId])).length) return false;
    await this.run("UPDATE conversation_messages SET content=?,isEdited=CASE WHEN type='user' THEN 1 ELSE isEdited END WHERE conversationId=? AND id=?", [stored, id, messageId]); return true;
  }
  async checkpointRow(id: string): Promise<CheckpointRow | null> { const rows = await this.rows('SELECT conversationId,historyJson,journalJson FROM conversation_context_checkpoints WHERE conversationId=?', [id]); return rows.length ? rows[0] as unknown as CheckpointRow : null; }
  putCheckpoint(row: CheckpointRow): Promise<void> { return this.run(upsert('conversation_context_checkpoints', 'conversationId,historyJson,journalJson', 'conversationId'), [row.conversationId, row.historyJson, row.journalJson]); }
  async selectedId(): Promise<string | null> { const rows = await this.rows('SELECT conversationId FROM conversation_selection WHERE slot=1'); return rows.length ? rows[0].conversationId as string | null : null; }
  setSelectedId(id: string | null): Promise<void> { return this.run(upsert('conversation_selection', 'slot,conversationId', 'slot'), [1, id]); }
  async textChunks(table: string, owner: string, field: string, limit: number, offset: number): Promise<TextChunk[]> { return await this.rows('SELECT ownerTable,ownerId,field,chunkIndex,content FROM agent_text_chunks WHERE ownerTable=? AND ownerId=? AND field=? ORDER BY chunkIndex LIMIT ? OFFSET ?', [table, owner, field, limit, offset]) as unknown as TextChunk[]; }
  putTextChunk(c: TextChunk): Promise<void> { return this.run(upsert('agent_text_chunks', 'ownerTable,ownerId,field,chunkIndex,content', 'ownerTable,ownerId,field,chunkIndex'), [c.ownerTable, c.ownerId, c.field, c.chunkIndex, c.content]); }
  deleteTextChunks(table: string, owner: string, field: string): Promise<void> { return this.run('DELETE FROM agent_text_chunks WHERE ownerTable=? AND ownerId=? AND field=?', [table, owner, field]); }
  async migrationRecord(key: string): Promise<string | null> { const rows = await this.rows('SELECT value FROM migration_records WHERE key=?', [key]); return rows.length ? rows[0].value as string : null; }
  putMigrationRecord(key: string, value: string): Promise<void> { return this.run(upsert('migration_records', 'key,value', 'key'), [key, value]); }
  diagnostic(code: string, at: number): Promise<void> { return this.run('INSERT INTO migration_diagnostics(code,at) VALUES(?,?)', [code, at]); }
}
export class SqlConversationDatabase implements ConversationDatabase, MigrationDatabase {
  private gate = new SerialGate(); private initialized = false; private poisoned = false;
  constructor(private sql: ConversationSqlConnection) {}
  private async prepare(): Promise<void> {
    if (this.poisoned) throw Error('Conversation connection requires reopen');
    if (this.initialized) return;
    await this.sql.execute('PRAGMA foreign_keys=ON');
    const enabled = await this.sql.query('PRAGMA foreign_keys');
    if (Number(enabled[0]?.foreign_keys) !== 1) throw Error('Conversation foreign keys unavailable');
    await this.sql.begin();
    try { for (const statement of CONVERSATION_SCHEMA) await this.sql.execute(statement); await this.sql.commit(); this.initialized = true; }
    catch (error) { await this.rollback(); throw error; }
  }
  private async rollback(): Promise<void> { try { await this.sql.rollback(); } catch (_) { this.poisoned = true; throw Error('Conversation rollback failed; reopen required'); } }
  transaction<T>(work: (dao: ConversationDao) => Promise<T>): Promise<T> { return this.migrationTransaction(work); }
  migrationTransaction<T>(work: (dao: MigrationDao) => Promise<T>): Promise<T> {
    return this.gate.run(async () => {
      await this.prepare(); await this.sql.begin(); const dao = new SqlConversationDao(this.sql);
      try { const value = await work(dao); await this.sql.commit(); return value; }
      catch (error) { await this.rollback(); throw error; }
      finally { dao.active = false; }
    });
  }
}
