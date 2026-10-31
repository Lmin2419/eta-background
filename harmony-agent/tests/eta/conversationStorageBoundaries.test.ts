// Harmony port boundary checks for Eta ChunkedTextDao / AgentConversationPersistence @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test'; import assert from 'node:assert/strict';
import { fixture, snapshot, storedState } from './support/conversationFixture';
import { CHUNK_CHARS, REFERENCE_PREFIX, storeText, restoreText, TextReader } from '../../entry/src/main/ets/core/eta/storage/chunkedText';
import { readRoleplaySummary } from '../../entry/src/main/ets/core/eta/storage/roleplaySummaryReader';
import { AgentConversationPersistence } from '../../entry/src/main/ets/core/eta/storage/agentConversationPersistence';
import { userMessage } from '../../entry/src/main/ets/core/eta/model/conversation';
test('chunk references round trip literal prefixes and never split surrogate pairs', async () => {
  const { db } = fixture();
  for (const text of [REFERENCE_PREFIX + 'literal', 'x'.repeat(CHUNK_CHARS - 1) + '😀' + '尾'.repeat(CHUNK_CHARS)]) {
    const ref = await storeText(db, 'conversations', 'c', 'roleplay', text);
    assert.equal(await restoreText(db, 'conversations', 'c', 'roleplay', ref), text);
    for (const chunk of db.state.chunks.values()) { assert.ok(chunk.content.length <= CHUNK_CHARS); const end = chunk.content.charCodeAt(chunk.content.length - 1); assert.ok(end < 0xd800 || end > 0xdbff); }
  }
});
test('missing or inconsistent chunks and trailing roleplay JSON are rejected', async () => {
  const { db } = fixture(), raw = JSON.stringify({ cardSnapshotJson: '大卡片'.repeat(10000), characterName: '旅人', avatarPath: null });
  const ref = await storeText(db, 'conversations', 'c', 'roleplay', raw);
  assert.deepEqual(await readRoleplaySummary(new TextReader(db, 'conversations', 'c', 'roleplay', ref)), { name: '旅人', avatarPath: null });
  assert.equal(db.maxChunkPage, 1);
  await assert.rejects(readRoleplaySummary(new TextReader(db, 'conversations', 'c', 'roleplay', '{} trailing')), /trailing/);
  db.state.chunks.delete([...db.state.chunks.keys()][0]);
  await assert.rejects(restoreText(db, 'conversations', 'c', 'roleplay', ref), /missing|order/);
});
test('commit failure leaves the baseline retryable and never commits partial metadata', async () => {
  const { db, store } = fixture(), writer = new AgentConversationPersistence(store), first = snapshot({ active: storedState('active') });
  await writer.save(first); const before = structuredClone(db.state);
  const next = snapshot({ active: { ...storedState('active'), messages: [userMessage('replacement', '替换')] } });
  db.failCommit = true; await assert.rejects(writer.save(next)); assert.deepEqual(db.state, before);
  db.failCommit = false; await writer.save(next); assert.deepEqual(await store.loadAllMessages('active'), [userMessage('replacement', '替换')]);
});
test('mutating a caller-owned message cannot silently mutate the saved baseline', async () => {
  const { db, store } = fixture(), writer = new AgentConversationPersistence(store), message = userMessage('u', '原文'), first = snapshot({ active: { ...storedState('active'), messages: [message] } });
  await writer.save(first); db.audit = []; message.content = '编辑后的正文'; await writer.save(first);
  assert.deepEqual(await store.loadAllMessages('active'), [message]); assert.ok(db.events('conversation_messages', 'u') > 0);
});
