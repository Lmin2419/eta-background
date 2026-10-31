// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/AgentConversationStoreTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// 17 original tests; Room/platform services replaced by the requested explicit memory ports.
import test from 'node:test';
import assert from 'node:assert/strict';
import { fixture, snapshot, binding, session } from './support/conversationFixture';
import { AgentChatUiState, emptyChatState, userMessage, agentMessage, thinkingMessage, toolActivityMessage, noticeMessage, conversationMessage } from '../../entry/src/main/ets/core/eta/model/conversation';
import { AgentConversationStore, putConversation, putMessage } from '../../entry/src/main/ets/core/eta/storage/agentConversationStore';
import { AgentConversationPersistence } from '../../entry/src/main/ets/core/eta/storage/agentConversationPersistence';
import { conversationRow, messageRow } from '../../entry/src/main/ets/core/eta/storage/conversationDao';
import { restoreText } from '../../entry/src/main/ets/core/eta/storage/chunkedText';
import { RoleplayConversationReducer } from '../../entry/src/main/ets/core/eta/roleplayConversationReducer';

test('repeatedSavePreservesRoleBindingRevisionsPendingRewriteAndOriginalJournal', async () => {
  const { store } = fixture(), original = { ...conversationMessage('assistant', '原始回答'), messageId: 'assistant-role-1' }, role = binding();
  const state = { ...emptyChatState(), messages: [agentMessage(original.messageId, original.content)], journal: [original], history: [original], roleplay: role,
    roleplayMessages: { ...emptyChatState().roleplayMessages, links: new Map([[original.messageId, { transcriptMessageId: original.messageId, blockOrder: 0 }]]), pendingRewrites: new Map([['rewrite-in-flight', original.messageId]]) } };
  let revised = RoleplayConversationReducer.edit(state, original.messageId, '用户修订的回答')!;
  for (let i = 0; i < 2; i++) {
    const snap = snapshot({ role: revised, ordinary: { ...emptyChatState(), messages: [userMessage('ordinary-user', '查看电量')] } }, 'role');
    snap.titles.set('role', '旅人'); snap.updatedAt.set('ordinary', 2); await store.save(snap);
    const restored = await store.load(); revised = restored.conversationsById.get('role')!;
    assert.deepEqual(revised.roleplay, role); assert.deepEqual(revised.journal, [original]); assert.equal(revised.history[0].content, '用户修订的回答');
    assert.deepEqual([...revised.roleplayMessages.pendingRewrites.keys()], ['rewrite-in-flight']);
    assert.deepEqual(revised.roleplayMessages.revisions.get(original.messageId)?.candidates, ['原始回答', '用户修订的回答']);
    assert.equal(revised.messages[0].kind === 'assistant' && revised.messages[0].candidateCount, 2);
    assert.equal(restored.conversationsById.get('ordinary')?.roleplay, null); assert.equal(restored.conversationsById.get('ordinary')?.roleplayMessages.revisions.size, 0);
  }
  const switched = RoleplayConversationReducer.select(revised, original.messageId, 0)!;
  assert.equal(switched.history[0].content, '原始回答'); assert.deepEqual(switched.journal, [original]);
});
test('saveAndLoadPreservesConversations', async () => {
  const { store } = fixture(), reply = '| 项目 | 内容 |\n| --- | --- |\n| 电量 | 88% |';
  const state: AgentChatUiState = { ...emptyChatState(), messages: [userMessage('user-1', '看一下当前屏幕', [], true),
    { ...thinkingMessage('thinking-1', '需要先观察屏幕'), elapsedSeconds: 3, collapsed: true },
    toolActivityMessage('tool-1', 'run_command', 'Success', '执行命令 · Android · root', 'pm list packages | head', 'ok=true, chars=100', 1),
    { ...agentMessage('assistant-1', reply), usage: { contextTokens: 100, inputTokens: 30, outputTokens: 40, reasoningTokens: 20, cachedTokens: 10 } }],
    history: [conversationMessage('user', '看一下当前屏幕'), { ...conversationMessage('assistant'), reasoningContent: '需要先观察屏幕', toolCallsJson: '[{"id":"toolu_1","type":"function","function":{"name":"observe_screen","arguments":"{}"}}]' },
      { ...conversationMessage('tool', '{"ok":true}'), toolCallId: 'toolu_1' }, conversationMessage('assistant', reply)],
    input: '不应该保存草稿', isStreaming: true, thinkingEnabled: true, reasoningEffort: 'high' };
  const snap = snapshot({ 'conv-1': state }); snap.titles.set('conv-1', '屏幕分析'); snap.updatedAt.set('conv-1', 1234); await store.save(snap);
  const loaded = await store.load(), restored = loaded.conversationsById.get('conv-1')!;
  assert.equal(loaded.selectedConversationId, 'conv-1'); assert.equal(loaded.titles.get('conv-1'), '屏幕分析'); assert.equal(loaded.updatedAt.get('conv-1'), 1234);
  assert.equal(restored.input, ''); assert.equal(restored.isStreaming, false); assert.equal(restored.thinkingEnabled, true); assert.equal(restored.reasoningEffort, 'high');
  assert.deepEqual(restored.messages, state.messages); assert.deepEqual(restored.history, state.history);
});
test('saveAndLoadPreservesSemanticSystemNoticesWithoutTranslatedContent', async () => {
  const { store } = fixture(), notice = noticeMessage('assistant-run-1-1', 'RuntimeFailed', 'upstream timeout');
  const snap = snapshot({ 'conv-notice': { ...emptyChatState(), messages: [notice] } }); snap.titles.set('conv-notice', ''); await store.save(snap);
  const restored = await store.load(); assert.equal(restored.titles.get('conv-notice'), ''); assert.deepEqual(restored.conversationsById.get('conv-notice')?.messages, [notice]);
});
test('conversationModelBindingRoundTripsAndChangeAloneIsPersisted', async () => {
  const { store } = fixture(), writer = new AgentConversationPersistence(store), base = { ...emptyChatState(), messages: [userMessage('user-1', '你好')], reasoningEffort: 'off' };
  const snap = (modelId: string) => snapshot({ 'conv-a': { ...base, modelId }, 'conv-b': base }, 'conv-a');
  await writer.save(snap('model-x')); assert.equal((await store.load()).conversationsById.get('conv-a')?.modelId, 'model-x');
  await writer.save(snap('model-y')); const loaded = await store.load(); assert.equal(loaded.conversationsById.get('conv-a')?.modelId, 'model-y'); assert.equal(loaded.conversationsById.get('conv-b')?.modelId, null);
});
test('unknownStoredEffortFallsBackToDefault', async () => {
  const { store, db } = fixture(); await db.putConversation({ ...conversationRow('conv-unknown', 'Unknown'), reasoningEffort: 'future_effort' }); await db.setSelectedId('conv-unknown');
  const restored = (await store.load()).conversationsById.get('conv-unknown')!; assert.equal(restored.reasoningEffort, 'default'); assert.equal(restored.thinkingEnabled, true);
});
test('initialLoadKeepsOnlyTheSelectedMessageWindowAndEarlierPagesRemainComplete', async () => {
  const { store, db } = fixture(), longContent = 'x'.repeat(20000);
  const primary = [userMessage('conv-0-user-long', longContent), ...Array.from({ length: 130 }, (_, i) => agentMessage(`conv-0-assistant-${i}`, `assistant-${i}`))];
  const states: Record<string, AgentChatUiState> = { 'conv-0': { ...emptyChatState(), messages: primary } };
  for (let i = 1; i <= 59; i++) states[`conv-${i}`] = { ...emptyChatState(), messages: [userMessage(`conv-${i}-user`, `message-conv-${i}`)] };
  const snap = snapshot(states, 'conv-0'); for (let i = 0; i < 60; i++) { snap.titles.set(`conv-${i}`, `title-conv-${i}`); snap.updatedAt.set(`conv-${i}`, i); } await store.save(snap);
  const loaded = await store.load(), current = loaded.conversationsById.get('conv-0')!;
  assert.equal(loaded.conversationsById.size, 60); assert.equal(current.messages.length, 128); assert.equal(current.messageStartIndex, 3); assert.equal(current.hasEarlierMessages, true);
  assert.deepEqual(current.messages[127], agentMessage('conv-0-assistant-129', 'assistant-129'));
  assert.ok([...loaded.conversationsById].filter(([id]) => id !== 'conv-0').every(([, s]) => !s.isConversationLoaded));
  const earlier = await store.loadEarlierMessages('conv-0', current.messageStartIndex); assert.equal(earlier.startIndex, 0); assert.equal(earlier.hasEarlierMessages, false);
  assert.deepEqual([...earlier.messages, ...current.messages], primary); assert.deepEqual(await store.loadAllMessages('conv-0'), primary); assert.equal(db.maxMessagePage, 128);
});
test('unopenedConversationOnlyReadsItsBoundedPreview', async () => {
  const { store, db } = fixture(); await db.putConversation(conversationRow('active', '当前', 2)); await db.putConversation({ ...conversationRow('dormant', '未打开'), revisionsJson: 'invalid deferred revisions' });
  await putMessage(db, messageRow('dormant-last', 'dormant', 0, 'assistant', '内容'.repeat(20000))); await db.setSelectedId('active');
  const dormant = (await store.load()).conversationsById.get('dormant')!;
  assert.equal(dormant.isConversationLoaded, false); assert.deepEqual(dormant.history, []); assert.deepEqual(dormant.journal, []); assert.equal(dormant.messages[0].kind === 'assistant' && dormant.messages[0].content.length, 256);
});
test('unloadedCharacterKeepsItsIdentityWithoutRetainingTheCard', async () => {
  const { store } = fixture(), role = { ...emptyChatState(), roleplay: { ...binding(), characterId: 'character', characterJson: '角色😀'.repeat(20000), cardSnapshotJson: '角色😀'.repeat(20000), avatarPath: '/test/avatar.png', userDescription: '很长的用户设定'.repeat(5000) } };
  const snap = snapshot({ active: emptyChatState(), role }); snap.updatedAt.set('active', 2); await store.save(snap);
  const placeholder = (await store.load()).conversationsById.get('role')!;
  assert.equal(placeholder.isConversationLoaded, false); assert.equal(placeholder.roleplay, null); assert.equal(placeholder.conversationCharacterName, '旅人'); assert.equal(placeholder.conversationCharacterAvatarPath, '/test/avatar.png');
  const unloaded = AgentConversationStore.unloadConversation(role); assert.equal(unloaded.roleplay, null); assert.equal(unloaded.conversationCharacterName, '旅人'); assert.equal(unloaded.conversationCharacterAvatarPath, '/test/avatar.png');
});
test('searchFindsUnloadedChunkBoundariesAndTreatsSqlWildcardsLiterally', async () => {
  const { store, db } = fixture(); await db.putConversation(conversationRow('literal', '文字')); await db.putConversation(conversationRow('other', '另一条', 2));
  await putMessage(db, messageRow('literal-message', 'literal', 0, 'assistant', 'x'.repeat(16381) + '跨越边界 100%_done')); await putMessage(db, messageRow('other-message', 'other', 0, 'assistant', '100xxfinished'));
  for (const q of ['跨越边界', '%_', 'DONE']) assert.deepEqual(await store.searchConversationIds(q), new Set(['literal']));
  for (const q of ['eta:chunks', '[]']) assert.deepEqual(await store.searchConversationIds(q), new Set());
});
test('searchRetainsUnicodeCaseFoldingAndQueriesSpanningManyChunks', async () => {
  const { store, db } = fixture(), query = '开始' + 'a'.repeat(60000) + '结束';
  for (const [id, text] of [['unicode', 'äpfel'], ['large', '前缀' + query + ' 后缀'], ['folded', 'İKſ']]) { await db.putConversation(conversationRow(id)); await putMessage(db, messageRow(`${id}-message`, id, 0, 'assistant', text)); }
  for (const [id, q] of [['unicode', 'ÄPFEL'], ['large', query], ['folded', 'iks']]) assert.deepEqual(await store.searchConversationIds(q), new Set([id]));
});
test('searchIncludesMatchingLocalizedNoticeCodesFromEarlierMessages', async () => {
  const { store, db } = fixture(); await db.putConversation(conversationRow('notice', '标题')); await putMessage(db, messageRow('old-notice', 'notice', 0, 'system_notice', 'interrupted')); await putMessage(db, messageRow('new-message', 'notice', 1, 'assistant', '新的回答'));
  assert.deepEqual(await store.searchConversationIds('已中断', ['Interrupted']), new Set(['notice']));
});
test('savePreservesCompleteContextAndDisplayedMessages', async () => {
  const { store, db } = fixture(), displayed = '展示消息-' + 'd'.repeat(120000);
  const history = [...Array.from({ length: 20 }, (_, i) => conversationMessage('assistant', `历史-${i}-` + 'h'.repeat(20000))), conversationMessage('user', '最新上下文')];
  await store.save(snapshot({ 'conv-large': { ...emptyChatState(), messages: [userMessage('user-large', displayed)], history } }));
  const cp = (await db.checkpointRow('conv-large'))!, raw = await restoreText(db, 'conversation_context_checkpoints', 'conv-large', 'history', cp.historyJson);
  assert.ok(raw.length > 96000); const restored = (await store.load()).conversationsById.get('conv-large')!;
  assert.deepEqual(restored.history, history); assert.deepEqual(restored.journal, history); assert.deepEqual(restored.messages, [userMessage('user-large', displayed)]); assert.equal(restored.history[20].content, '最新上下文');
});
test('loadIgnoresLegacyHistoryColumnAndFallsBackToMessageRows', async () => {
  const { store, db } = fixture(); await db.putConversation({ ...conversationRow('conv-legacy-large', '旧长对话'), historyJson: 'x'.repeat(2500000) }); await putMessage(db, messageRow('legacy-user', 'conv-legacy-large', 0, 'user', '从消息记录恢复'));
  const restored = (await store.load()).conversationsById.get('conv-legacy-large')!; assert.equal(restored.history[0].content, '从消息记录恢复'); assert.deepEqual(restored.messages, [userMessage('legacy-user', '从消息记录恢复')]);
});
test('loadKeepsDatabaseEmptyUntilFirstMessageIsSent', async () => { const { store } = fixture(); const loaded = await store.load(); assert.equal(loaded.conversationsById.size, 0); assert.equal(loaded.selectedConversationId, null); });
test('characterGreetingIsLocalAndOrdinaryNewConversationReturnsToEta', async () => {
  const { store } = fixture(), state = await session(store), b = { ...binding(), characterId: 'local-character', userName: '小林' };
  await state.startCharacterConversation(b, '你好，{{user}}，我是{{char}}。');
  assert.equal(state.homeState.isStreaming, false); assert.equal(state.homeState.messages[0].kind === 'assistant' && state.homeState.messages[0].content, '你好，小林，我是旅人。'); assert.deepEqual(state.homeState.roleplay, b);
  assert.deepEqual(state.homeState.appliedRuntimeRunIds, []); assert.equal(state.homeState.roleplayMessages.pendingRewrites.size, 0);
  await state.createConversation(); assert.equal(state.homeState.roleplay, null); assert.deepEqual(state.homeState.history, []); assert.deepEqual(state.homeState.messages, []); assert.equal(state.homeState.isStreaming, false);
});
test('creatingConversationKeepsEmptyStateOutOfHistoryAndDatabase', async () => {
  const { store, db } = fixture(), state = await session(store); await state.createConversation(); await state.createConversation();
  assert.equal(state.snapshot.selectedConversationId, null); assert.equal(state.snapshot.conversationsById.size, 0); assert.equal(db.state.conversations.size, 0);
});
test('savingEmptySnapshotClearsPreviouslyPersistedConversations', async () => {
  const { store } = fixture(); await store.save(snapshot({ 'conv-1': { ...emptyChatState(), messages: [userMessage('user-1', 'hello')] } })); await store.save(snapshot({}, null));
  const restored = await store.load(); assert.equal(restored.conversationsById.size, 0); assert.equal(restored.selectedConversationId, null);
});
