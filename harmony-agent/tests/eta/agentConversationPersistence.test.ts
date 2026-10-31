// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/AgentConversationPersistenceTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// SQL audit/failure triggers translated to the transactional DAO's mutation log/failure injection.
import test from 'node:test';
import assert from 'node:assert/strict';
import { fixture, snapshot, storedState, binding } from './support/conversationFixture';
import { AgentConversationPersistence } from '../../entry/src/main/ets/core/eta/storage/agentConversationPersistence';
import { AgentConversationStore } from '../../entry/src/main/ets/core/eta/storage/agentConversationStore';
import { agentMessage, userMessage, thinkingMessage, conversationMessage } from '../../entry/src/main/ets/core/eta/model/conversation';
import { emptyRoleplayMessages } from '../../entry/src/main/ets/core/eta/model/runtimeProjection';
import { decodeTranscript } from '../../entry/src/main/ets/core/eta/storage/conversationCodec';
import { restoreText } from '../../entry/src/main/ets/core/eta/storage/chunkedText';
import { MemoryConversationDatabase } from './support/memoryConversationDatabase';
async function checkpoint(db: MemoryConversationDatabase, id: string, field: 'history' | 'journal') {
  const cp = (await db.checkpointRow(id))!;
  return decodeTranscript(await restoreText(db, 'conversation_context_checkpoints', id, field, field === 'history' ? cp.historyJson : cp.journalJson));
}
test('searchSuggestionsStayInLoadedConversationAndNeverReachMessageStorage', async () => {
  const { store } = fixture(), message = { ...agentMessage('search-answer', '正文与引用'), searchSuggestionsHtml: ['<div>短期建议</div>'] }, state = { ...storedState('search'), messages: [message] };
  await new AgentConversationPersistence(store).save(snapshot({ search: state }));
  const restored = (await store.loadAllMessages('search'))[0]; assert.equal(restored.kind === 'assistant' && restored.content, message.content); assert.deepEqual(restored.kind === 'assistant' && restored.searchSuggestionsHtml, []);
  const unloaded = AgentConversationStore.unloadConversation(state).messages[0]; assert.deepEqual(unloaded.kind === 'assistant' && unloaded.searchSuggestionsHtml, []);
});
test('appendingOnlyWritesTheChangedConversationAndNewMessages', async () => {
  const { store, db } = fixture(), writer = new AgentConversationPersistence(store), initial = snapshot({ active: storedState('active'), dormant: storedState('dormant') });
  await writer.save(initial); db.audit = []; const active = initial.conversationsById.get('active')!, addition = conversationMessage('user', '继续');
  const next = { ...initial, conversationsById: new Map(initial.conversationsById), updatedAt: new Map(initial.updatedAt) };
  next.conversationsById.set('active', { ...active, messages: [...active.messages, userMessage('new-user', '继续')], history: [...active.history, addition], journal: [...active.journal, addition], appliedRuntimeRunIds: ['applied-run'] }); next.updatedAt.set('active', 9);
  await writer.save(next);
  for (const [table, id] of [['conversations', 'dormant'], ['conversation_context_checkpoints', 'dormant'], ['conversation_messages', 'active-user'], ['conversation_messages', 'active-thinking'], ['conversation_messages', 'dormant-thinking']]) assert.equal(db.events(table, id), 0);
  assert.equal(db.events('conversation_messages', 'new-user'), 1); assert.equal(db.state.conversations.get('active')?.createdAt, 1); assert.equal(db.state.conversations.get('active')?.updatedAt, 9);
  assert.deepEqual(await checkpoint(db, 'active', 'journal'), next.conversationsById.get('active')?.journal);
});
test('selectionAndDraftChangesDoNotRewriteStoredContent', async () => {
  const { store, db } = fixture(), writer = new AgentConversationPersistence(store), initial = snapshot({ active: storedState('active'), other: storedState('other') }); await writer.save(initial); db.audit = [];
  const next = { ...initial, selectedConversationId: 'other', conversationsById: new Map(initial.conversationsById) }; next.conversationsById.set('active', { ...initial.conversationsById.get('active')!, input: '尚未发送的草稿' });
  await writer.save(next); assert.equal(db.audit.length, 0); assert.equal(await db.selectedId(), 'other');
});
test('contextCompressionDoesNotRewriteTheUnchangedJournalOrMessages', async () => {
  const { store, db } = fixture(), writer = new AgentConversationPersistence(store), initial = snapshot({ active: storedState('active') }); await writer.save(initial); db.audit = [];
  await writer.save({ ...initial, conversationsById: new Map([['active', { ...initial.conversationsById.get('active')!, history: [{ ...conversationMessage('assistant', '摘要'), contextSummary: true }] }]]) });
  assert.equal(db.events('agent_text_chunks', 'active', 'journal'), 0); assert.equal(db.events('conversation_messages', 'active-thinking'), 0);
  assert.deepEqual(await checkpoint(db, 'active', 'journal'), initial.conversationsById.get('active')?.journal); assert.equal((await checkpoint(db, 'active', 'history'))[0].content, '摘要');
});
test('aFailedTransactionRollsBackAndTheSameWriterCanRetry', async () => {
  const { store, db } = fixture(), writer = new AgentConversationPersistence(store), initial = snapshot({ active: storedState('active') }); await writer.save(initial); db.rejectMessage = 'rejected-user';
  const next = { ...initial, conversationsById: new Map([['active', { ...initial.conversationsById.get('active')!, messages: [...initial.conversationsById.get('active')!.messages, userMessage('rejected-user', '新消息')], history: [conversationMessage('user', '新历史')], appliedRuntimeRunIds: ['new-run'] }]]), titles: new Map([['active', '新标题']]), updatedAt: new Map([['active', 10]]) };
  await assert.rejects(writer.save(next)); assert.equal(db.state.conversations.get('active')?.title, 'active'); assert.deepEqual(await checkpoint(db, 'active', 'history'), initial.conversationsById.get('active')?.history); assert.equal(db.state.conversations.get('active')?.appliedRuntimeRunIdsJson, '[]');
  db.rejectMessage = ''; await writer.save(next); assert.equal(db.state.conversations.get('active')?.title, '新标题'); assert.equal(await db.messageCount('active'), 3); assert.deepEqual(await checkpoint(db, 'active', 'history'), next.conversationsById.get('active')?.history); assert.equal(db.state.conversations.get('active')?.appliedRuntimeRunIdsJson, '["new-run"]');
});
test('deletingAConversationCleansItsChunksAndPreservesOthers', async () => {
  const { store, db } = fixture(), writer = new AgentConversationPersistence(store), initial = snapshot({ active: storedState('active'), deleted: storedState('deleted') }); await writer.save(initial); db.audit = [];
  await writer.save({ ...initial, conversationsById: new Map([['active', initial.conversationsById.get('active')!]]) });
  assert.deepEqual([...db.state.conversations.keys()], ['active']); assert.equal(db.events('conversation_messages', 'active-thinking'), 0); assert.equal([...db.state.chunks.values()].filter(c => ['deleted', 'deleted-thinking'].includes(c.ownerId)).length, 0);
});
test('restartAfterSavingAroundAStreamingPlaceholderKeepsEveryMessage', async () => {
  const { store } = fixture(), running = { ...storedState('active'), messages: [userMessage('user', '请求'), { ...agentMessage('assistant-run', '', true), renderMarkdown: false }, thinkingMessage('thinking', '思考'), thinkingMessage('later', '继续思考')] };
  await new AgentConversationPersistence(store).save(snapshot({ active: running })); const loaded = await store.load(); assert.deepEqual(loaded.unalignedConversationIds, new Set(['active']));
  await new AgentConversationPersistence(store, loaded).save({ ...loaded, conversationsById: new Map([['active', { ...loaded.conversationsById.get('active')!, messages: [...loaded.conversationsById.get('active')!.messages, agentMessage('answer', '最终回答')] }]]) });
  const reloaded = await store.load(); assert.deepEqual(reloaded.conversationsById.get('active')?.messages.map(m => m.id), ['user', 'thinking', 'later', 'answer']); assert.equal(reloaded.unalignedConversationIds.size, 0);
});
test('appendingAndDeletingWithinATailWindowNeverRewritesTheUnloadedPrefix', async () => {
  const { store, db } = fixture(), messages = Array.from({ length: 300 }, (_, i) => userMessage(`user-${i}`, `消息-${i}`));
  await new AgentConversationPersistence(store).save(snapshot({ active: { ...storedState('active'), messages } }));
  const loaded = await store.load(), writer = new AgentConversationPersistence(store, loaded), tail = loaded.conversationsById.get('active')!; assert.equal(tail.messageStartIndex, 172); db.audit = [];
  const appended = { ...tail, messages: [...tail.messages, agentMessage('new-answer', '新回答')] };
  await writer.save({ ...loaded, conversationsById: new Map([['active', appended]]) });
  assert.equal(db.events('conversation_messages', 'user-0'), 0); assert.equal(db.events('conversation_messages', 'user-171'), 0); assert.deepEqual(await store.loadAllMessages('active'), [...messages, appended.messages[128]]);
  await writer.save({ ...loaded, conversationsById: new Map([['active', { ...appended, messages: [] }]]) });
  assert.deepEqual(await store.loadAllMessages('active'), messages.slice(0, 172)); assert.equal(db.events('conversation_messages', 'user-171'), 0);
});
test('renamingAnUnloadedConversationPreservesItsMessagesAndContext', async () => {
  const { store, db } = fixture(); await new AgentConversationPersistence(store).save(snapshot({ active: storedState('active'), dormant: storedState('dormant') }));
  const before = (await store.loadConversation('dormant'))!, loaded = await store.load(); assert.equal(loaded.conversationsById.get('dormant')?.isConversationLoaded, false); db.audit = [];
  await new AgentConversationPersistence(store, loaded).save({ ...loaded, titles: new Map([...loaded.titles, ['dormant', '新名称']]), updatedAt: new Map([...loaded.updatedAt, ['dormant', 20]]) });
  assert.equal(db.events('conversation_messages', 'dormant-thinking'), 0); assert.equal(db.events('conversation_context_checkpoints', 'dormant'), 0);
  const after = (await store.loadConversation('dormant'))!; assert.deepEqual(after.messages, before.messages); assert.deepEqual(after.history, before.history); assert.equal(db.state.conversations.get('dormant')?.title, '新名称');
});
test('adoptingAnEarlierPageDoesNotRewriteItsUnchangedRows', async () => {
  const { store, db } = fixture(), original = { ...storedState('active'), messages: Array.from({ length: 200 }, (_, i) => userMessage(`user-${i}`, `消息-${i}`)) };
  await new AgentConversationPersistence(store).save(snapshot({ active: original })); const loaded = await store.load(), writer = new AgentConversationPersistence(store, loaded), state = loaded.conversationsById.get('active')!;
  const page = await store.loadEarlierMessages('active', state.messageStartIndex), expanded = { ...state, messages: [...page.messages, ...state.messages], messageStartIndex: page.startIndex, hasEarlierMessages: page.hasEarlierMessages };
  await writer.adoptConversation('active', expanded, loaded.titles.get('active')!, loaded.updatedAt.get('active')!); db.audit = [];
  await writer.save({ ...loaded, conversationsById: new Map([['active', expanded]]) }); assert.equal(db.audit.length, 0); assert.deepEqual(await store.loadAllMessages('active'), original.messages);
});
test('offscreenRoleplayRevisionsUpdateStoredRowsWithoutLoadingTheirMessageWindow', async () => {
  const { store } = fixture(), messages = Array.from({ length: 300 }, (_, i) => i % 2 === 0 ? userMessage(`message-${i}`, `正文-${i}`) : agentMessage(`message-${i}`, `正文-${i}`));
  const original = { ...storedState('active'), messages, roleplay: binding(), roleplayMessages: { ...emptyRoleplayMessages(), links: new Map(messages.map(m => [m.id, { transcriptMessageId: m.id, blockOrder: 0 }])) } };
  await new AgentConversationPersistence(store).save(snapshot({ active: original })); const loaded = await store.load(), tail = loaded.conversationsById.get('active')!; assert.equal(tail.messageStartIndex, 172);
  const edited = '修订'.repeat(10000), revised = { ...tail, roleplayMessages: { ...tail.roleplayMessages, revisions: new Map([['message-0', { original: '正文-0', candidates: ['正文-0', edited], selected: 1 }], ['message-1', { original: '正文-1', candidates: ['正文-1', '新回答'], selected: 1 }]]) } };
  await new AgentConversationPersistence(store, loaded).save({ ...loaded, conversationsById: new Map([['active', revised]]) });
  const all = await store.loadAllMessages('active'); assert.equal(all.length, 300); assert.deepEqual(all[0], userMessage('message-0', edited, [], true)); assert.deepEqual(all[1], agentMessage('message-1', '新回答')); assert.deepEqual(all.slice(2), messages.slice(2)); assert.deepEqual(await store.searchConversationIds('新回答'), new Set(['active']));
});
test('aNewOffscreenRevisionWithAMissingTargetRollsBackWithoutRejectingOldMetadata', async () => {
  const { store, db } = fixture(), original = { ...storedState('active'), roleplay: binding(), roleplayMessages: { ...emptyRoleplayMessages(), revisions: new Map([['legacy-missing', { original: '旧正文', candidates: ['旧正文'], selected: 0 }]]) } };
  await new AgentConversationPersistence(store).save(snapshot({ active: original })); const loaded = await store.load(), writer = new AgentConversationPersistence(store, loaded), current = loaded.conversationsById.get('active')!;
  const revised = { ...current, roleplayMessages: { ...current.roleplayMessages, revisions: new Map([...current.roleplayMessages.revisions, ['missing-target', { original: '原文', candidates: ['原文', '修改'], selected: 1 }]]) } };
  await assert.rejects(writer.save({ ...loaded, conversationsById: new Map([['active', revised]]), titles: new Map([['active', '不应提交']]) }), /missing/);
  assert.deepEqual((await store.loadConversation('active'))?.roleplayMessages, current.roleplayMessages); assert.equal(db.state.conversations.get('active')?.title, 'active');
  await writer.save({ ...loaded, titles: new Map([['active', '合法改名']]) }); assert.equal(db.state.conversations.get('active')?.title, '合法改名'); assert.deepEqual((await store.loadConversation('active'))?.roleplayMessages, current.roleplayMessages);
});
