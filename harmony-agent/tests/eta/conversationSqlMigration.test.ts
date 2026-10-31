// Harmony adapter regressions for Eta's storage contract @ 3b1794d. Original 44 tests remain in E07b1.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { SqliteConversationConnection } from './support/sqliteConversationConnection';
import { SqlConversationDatabase } from '../../entry/src/main/ets/core/eta/storage/sqlConversationDatabase';
import { conversationRow, messageRow, ConversationDao } from '../../entry/src/main/ets/core/eta/storage/conversationDao';
import { LegacyConversationMigration, LEGACY_MIGRATION_KEY, readMigrationRecord, legacyRows } from '../../entry/src/main/ets/core/eta/storage/legacyConversationMigration';
import { LegacySnapshotSource, MigrationFailure } from '../../entry/src/main/ets/core/eta/storage/migrationPorts';
import { SqlLegacySnapshotSource } from '../../entry/src/main/ets/core/eta/storage/sqlLegacySnapshotSource';
import { AgentConversationStore } from '../../entry/src/main/ets/core/eta/storage/agentConversationStore';
import { AgentConversationPersistence } from '../../entry/src/main/ets/core/eta/storage/agentConversationPersistence';
import { ConversationDocument } from '../../entry/src/main/ets/core/conversations';
import { snapshot, storedState, loadingState } from './support/conversationFixture';
import { userMessage } from '../../entry/src/main/ets/core/eta/model/conversation';
const hash = async (text: string): Promise<string> => createHash('sha256').update(text).digest('hex');
function oldDocument(): ConversationDocument {
  return { version: 1, selectedId: 'b', conversations: ['a', 'b'].map((id, index) => ({
    id, title: '标题 ' + id, createdAt: 100 + index, updatedAt: 200 + index, profileId: 'saved-profile', preferredModel: 'gpt-test', mode: 'chat',
    turns: [{ id: 'same-local-turn-id', request: '请求👩🏽‍💻', reply: '回复', reasoning: '思考', status: 'completed', mode: 'chat', modelLabel: '旧模型名', startedAt: 123, endedAt: 321,
      steps: [], notice: '', contextNote: '原上下文说明', revisionOf: 'old-turn', attachments: ['attachment-1'], usage: { input: 7, output: 3, total: 10 } }] })) };
}
class Source implements LegacySnapshotSource {
  raw = JSON.stringify(oldDocument(), null, 2); reads = 0; changeOnRead = 0;
  async read() { this.reads++; if (this.reads === this.changeOnRead) this.raw += ' '; return { document: this.raw, evidence: this.raw }; }
}
function fixture(path = ':memory:') {
  const sql = new SqliteConversationConnection(path), db = new SqlConversationDatabase(sql), source = new Source();
  const migration = new LegacyConversationMigration(db, source, hash, { resolve: (p, m) => p + ':' + m }, () => 1234);
  return { sql, db, source, migration, store: new AgentConversationStore(db) };
}
test('SQL rows preserve metadata updates, bounded paging, chunks, index previews and delete cascades', async t => {
  const f = fixture(); t.after(() => f.sql.close());
  const state = loadingState('a', 600); state.messages[599] = userMessage(state.messages[599].id, '正文'.repeat(30000));
  await f.store.save(snapshot({ a: state }));
  const tail = await f.store.loadConversation('a'); assert.equal(tail?.messages.length, 128); assert.equal(tail?.messages[127].kind === 'user' && tail.messages[127].content, '正文'.repeat(30000));
  await f.db.transaction(async dao => {
    const row = await dao.metadataRow('a'); await dao.putConversation({ ...row!, historyJson: '[]', title: '重命名' });
    assert.equal(await dao.messageCount('a'), 600); assert.equal((await dao.conversationIndex())[0].messagePreview?.length, 256);
    assert.equal((await dao.messageRowsPage('a', 8, 20))[0].sortIndex, 20);
    assert.deepEqual((await dao.messageRowsBefore('a', 150, 3)).map(m => m.sortIndex), [147, 148, 149]);
    await dao.deleteConversation('a'); assert.equal(await dao.messageCount('a'), 0); assert.equal(await dao.checkpointRow('a'), null);
  });
  assert.equal((await f.sql.query('SELECT COUNT(*) AS n FROM agent_text_chunks'))[0].n, 0);
});
test('SQL transaction serializes all access, rejects retained DAO and rolls back foreign-key or unique conflicts', async t => {
  const f = fixture(); t.after(() => f.sql.close()); let retained: ConversationDao | undefined;
  await f.db.transaction(async dao => { retained = dao; await dao.putConversation(conversationRow('a')); });
  await assert.rejects(async () => retained!.messageCount('a'), /closed/);
  await assert.rejects(f.db.transaction(async dao => { await dao.putMessage(messageRow('first', 'a', 0, 'user', 'hi')); await dao.putMessage(messageRow('second', 'a', 0, 'user', 'collision')); }));
  assert.equal(await f.db.transaction(dao => dao.messageCount('a')), 0);
  await assert.rejects(f.db.transaction(dao => dao.putMessage(messageRow('missing', 'absent', 0, 'user', 'no parent'))));
  const order: number[] = [];
  await Promise.all(Array.from({ length: 12 }, (_, i) => f.db.transaction(async dao => { order.push(i); await dao.messageCount('a'); order.push(i); })));
  assert.deepEqual(order, Array.from({ length: 12 }, (_, i) => [i, i]).flat());
});
test('SQL commit failure preserves persistence baseline and memory; retry writes the failed snapshot', async t => {
  const f = fixture(); t.after(() => f.sql.close()); const persistence = new AgentConversationPersistence(f.store), state = snapshot({ a: storedState('a') });
  await persistence.save(state); const before = JSON.stringify(state.conversationsById.get('a'));
  state.titles.set('a', 'new title'); f.sql.failCommit = true; await assert.rejects(persistence.save(state));
  assert.equal(JSON.stringify(state.conversationsById.get('a')), before); f.sql.failCommit = false;
  assert.equal((await f.store.load()).titles.get('a'), 'a'); await persistence.save(state); assert.equal((await f.store.load()).titles.get('a'), 'new title');
});
test('migration verifies counts, hashes, selected ID, stable IDs and original metadata; repeat is idempotent', async t => {
  const f = fixture(); t.after(() => f.sql.close()); const raw = f.source.raw;
  const first = await f.migration.run(); assert.equal(first.status, 'verified'); assert.equal(first.manifest.conversations, 2); assert.equal(first.manifest.messages, 6);
  const loaded = await f.store.load(); assert.equal(loaded.selectedConversationId, 'b'); assert.equal(loaded.conversationsById.get('b')?.modelId, 'saved-profile:gpt-test');
  assert.equal((await f.migration.run()).status, 'already_verified'); assert.equal(f.source.raw, raw);
  await f.db.migrationTransaction(async dao => {
    assert.equal(await readMigrationRecord(dao, LEGACY_MIGRATION_KEY + ':source'), raw);
    assert.deepEqual(JSON.parse((await readMigrationRecord(dao, LEGACY_MIGRATION_KEY + ':conversation:a'))!), oldDocument().conversations[0]);
    assert.deepEqual((await dao.messageRowsPage('a', 20, 0)).map(r => r.id), legacyRows(oldDocument().conversations[0]).map(r => r.id));
  });
});
test('migration failure halfway rolls back marker, rows and backups; failure diagnostic contains no body', async t => {
  const f = fixture(); t.after(() => f.sql.close()); const raw = f.source.raw;
  f.sql.failSql = 'INSERT INTO conversation_messages'; await assert.rejects(f.migration.run(), (e: MigrationFailure) => e.code === 'write_failed');
  f.sql.failSql = '';
  await f.db.migrationTransaction(async dao => { assert.equal(await dao.migrationRecord(LEGACY_MIGRATION_KEY), null); assert.equal((await dao.conversationIdentities()).length, 0); });
  assert.equal((await f.sql.query('SELECT COUNT(*) AS n FROM agent_text_chunks'))[0].n, 0);
  assert.deepEqual(await f.sql.query('SELECT code,at FROM migration_diagnostics'), [{ code: 'write_failed', at: 1234 }]);
  assert.equal(f.source.raw, raw); assert.equal((await f.migration.run()).status, 'verified');
});
test('migration restart recovers after failure and after success using the durable independent marker', async t => {
  const dir = mkdtempSync(join(tmpdir(), 'eta-sql-')); t.after(() => rmSync(dir, { recursive: true, force: true })); const path = join(dir, 'new.db');
  const first = fixture(path); first.sql.failSql = 'INSERT INTO conversation_messages'; await assert.rejects(first.migration.run()); first.sql.close();
  const second = fixture(path); assert.equal((await second.migration.run()).status, 'verified'); second.sql.close();
  const third = fixture(path); t.after(() => third.sql.close()); assert.equal((await third.migration.run()).status, 'already_verified');
});
test('migration commit failure cannot publish its success marker or any migrated conversations', async t => {
  const f = fixture(); t.after(() => f.sql.close()); await f.db.transaction(d => d.conversationIdentities());
  f.sql.failCommit = true; await assert.rejects(f.migration.run(), (e: MigrationFailure) => e.code === 'write_failed'); f.sql.failCommit = false;
  await f.db.migrationTransaction(async d => { assert.equal(await d.migrationRecord(LEGACY_MIGRATION_KEY), null); assert.equal((await d.conversationIdentities()).length, 0); });
  assert.equal((await f.migration.run()).status, 'verified');
});
test('missing backup chunk blocks repeat verification while the original snapshot remains readable', async t => {
  const f = fixture(); t.after(() => f.sql.close()); const doc = oldDocument(); doc.conversations[0].turns[0].reply = '完整数据'.repeat(20000); f.source.raw = JSON.stringify(doc);
  await f.migration.run(); const original = f.source.raw;
  await f.sql.execute("DELETE FROM agent_text_chunks WHERE ownerTable='migration_records' AND ownerId=? AND chunkIndex=1", [LEGACY_MIGRATION_KEY + ':source']);
  await assert.rejects(f.migration.run()); assert.equal(f.source.raw, original); assert.deepEqual(JSON.parse((await f.source.read()).document), doc);
});
test('damaged source or invalid selection blocks migration without truncating or fixing the old snapshot', async t => {
  const f = fixture(); t.after(() => f.sql.close());
  for (const raw of ['{broken', JSON.stringify({ ...oldDocument(), selectedId: 'missing' }), JSON.stringify({ ...oldDocument(), version: 2 })]) {
    f.source.raw = raw; await assert.rejects(f.migration.run(), (e: MigrationFailure) => e.code === 'source_invalid'); assert.equal(f.source.raw, raw);
  }
  assert.equal((await f.db.transaction(dao => dao.conversationIdentities())).length, 0);
});
test('long Unicode body and exact raw snapshot survive chunk boundaries without legacy length caps', async t => {
  const f = fixture(); t.after(() => f.sql.close()); const doc = oldDocument(); const body = '文👩🏽‍💻\n'.repeat(60000);
  doc.conversations[0].turns[0].reply = body; doc.conversations[0].turns[0].reasoning = '思'.repeat(40000); f.source.raw = JSON.stringify(doc, null, 3);
  await f.migration.run(); const messages = (await f.store.loadConversation('a'))!.messages;
  assert.equal(messages.find(m => m.kind === 'assistant')?.content, body);
  assert.equal(await f.db.migrationTransaction(dao => readMigrationRecord(dao, LEGACY_MIGRATION_KEY + ':source')), f.source.raw);
});
test('migration never overwrites occupied target or a snapshot changed during or after conversion', async t => {
  const f = fixture(); t.after(() => f.sql.close()); f.source.changeOnRead = 2;
  await assert.rejects(f.migration.run(), (e: MigrationFailure) => e.code === 'source_changed');
  assert.equal((await f.db.transaction(d => d.conversationIdentities())).length, 0);
  f.source.changeOnRead = 0; await f.migration.run(); f.source.raw += '\n';
  await assert.rejects(f.migration.run(), (e: MigrationFailure) => e.code === 'source_changed');
  const other = fixture(); t.after(() => other.sql.close()); await other.db.transaction(d => d.putConversation(conversationRow('occupied')));
  await assert.rejects(other.migration.run(), (e: MigrationFailure) => e.code === 'target_occupied'); assert.equal((await other.store.load()).titles.has('occupied'), true);
});
test('verification on repeat detects tampered body and refuses a false migration success', async t => {
  const f = fixture(); t.after(() => f.sql.close()); await f.migration.run();
  await f.sql.execute("UPDATE conversation_messages SET content='damaged' WHERE type='assistant'");
  await assert.rejects(f.migration.run(), (e: MigrationFailure) => e.code === 'verification_failed');
});
test('running legacy tasks become historical interrupted notices, never runnable state or queues', async t => {
  const f = fixture(); t.after(() => f.sql.close()); const doc = oldDocument(); const turn = doc.conversations[0].turns[0]; turn.status = 'running';
  turn.steps = [{ id: 's', tool: 'tap', status: 'running', startedAt: 200, detail: 'old detail' }]; f.source.raw = JSON.stringify(doc); await f.migration.run();
  const state = (await f.store.loadConversation('a'))!; assert.equal(state.isStreaming, false); assert.equal(state.isCompacting, false); assert.deepEqual(state.queuedMessages, []);
  assert.ok(state.messages.some(m => m.kind === 'notice' && m.code === 'Interrupted'));
  assert.ok(state.messages.some(m => m.kind === 'tool' && m.status === 'Unknown'));
  assert.equal(JSON.parse((await f.db.migrationTransaction(d => readMigrationRecord(d, LEGACY_MIGRATION_KEY + ':conversation:a')))!).turns[0].status, 'running');
});
test('read-only legacy RDB source preserves raw row evidence and does not write, even for large rows', async t => {
  const dir = mkdtempSync(join(tmpdir(), 'eta-old-')); t.after(() => rmSync(dir, { recursive: true, force: true })); const path = join(dir, 'old.db');
  const write = new SqliteConversationConnection(path), doc = oldDocument(); doc.conversations[0].turns[0].reply = '长👩🏽‍💻'.repeat(50000);
  await write.execute('CREATE TABLE documents(id TEXT PRIMARY KEY,payload TEXT)'); await write.execute('CREATE TABLE conversations(id TEXT PRIMARY KEY,payload TEXT)');
  const meta = JSON.stringify({ version: 1, selectedId: 'b' }, null, 4), raw = doc.conversations.map(c => JSON.stringify(c, null, 3));
  await write.execute('INSERT INTO documents VALUES(?,?)', ['conversation_meta', meta]);
  for (let i = 0; i < raw.length; i++) await write.execute('INSERT INTO conversations VALUES(?,?)', [doc.conversations[i].id, raw[i]]); write.close();
  const read = new SqliteConversationConnection(path, true); t.after(() => read.close()); const source = new SqlLegacySnapshotSource(read), found = await source.read();
  assert.deepEqual(JSON.parse(found.document), doc); assert.deepEqual(JSON.parse(found.evidence), { meta, rows: raw }); assert.deepEqual(read.executions, []);
});
