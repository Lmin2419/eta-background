// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/AgentAppStateConversationLoadingTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// 15 source scenarios. Android service/archive/Looper replaced by explicit ports and awaited jobs.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { fixture, snapshot, loadingState, session } from './support/conversationFixture';
import { userMessage, conversationMessage } from '../../entry/src/main/ets/core/eta/model/conversation';
import { AgentConversationStore, putMessage } from '../../entry/src/main/ets/core/eta/storage/agentConversationStore';
import { AgentConversationSession } from '../../entry/src/main/ets/core/eta/storage/agentConversationSession';
import { messageRow } from '../../entry/src/main/ets/core/eta/storage/conversationDao';
import { runResult } from '../../entry/src/main/ets/core/eta/model/runtimeProjection';
import { ExportLabels } from '../../entry/src/main/ets/core/eta/conversationMarkdownExporter';
const result = (id: string, content: string) => ({ ...runResult(id, true, content), transcript: [conversationMessage('assistant', content)] });
const ids = (id: string, count = 3) => Array.from({ length: count }, (_, i) => `${id}-${i}`);
const labels: ExportLabels = { user: 'User', assistant: 'Assistant', thinking: 'Thinking', toolLineFormat: 'Tool: %1$s (%2$s)', toolsLineFormat: 'Tools: %1$s', argumentsFormat: 'Arguments: %1$s', resultFormat: 'Result: %1$s', imagesFormat: 'Images: %1$d', toolStatusRunning: 'Running', toolStatusSuccess: 'Completed', toolStatusFailed: 'Failed', toolStatusUnknown: 'Unknown', noticeStopped: 'Stopped', noticeEmptyResult: 'Empty result', noticeModelRetry: 'Retrying', noticeRuntimeFailed: 'Failed', noticeInterrupted: 'Interrupted' };
function loaded(state: AgentConversationSession, id: string): void { assert.equal(state.snapshot.selectedConversationId, id); assert.equal(state.homeState.isConversationLoaded, true); assert.equal(state.isLoadingConversation, false); assert.equal(state.conversationLoadFailed, false); }
test('notificationReplyPromptIsVisibleFromCheckpointBeforeAResultExists', async () => {
  const { store } = fixture(); await store.save(snapshot({ first: loadingState('first') }));
  const prompt = { conversationId: 'first', runId: 'inline-running', messageId: 'user-inline-running-supplement-1', text: '收左边的菜' };
  const state = await session(store, { pendingPrompts: async () => [prompt] });
  assert.ok(state.homeState.messages.some(m => m.kind === 'user' && m.content === '收左边的菜')); await state.refreshRuntimeResults();
  assert.equal(state.homeState.messages.filter(m => m.kind === 'user' && m.content === '收左边的菜').length, 1); assert.equal(state.isLoadingEarlierMessages, false);
});
test('notificationContinuationImportsTheAssistantArchiveBeforeSelectingItsChat', async () => {
  const { store } = fixture(); await store.save(snapshot({ first: loadingState('first') }));
  const chat = 'assistant-' + createHash('sha256').update('eta_voice:game', 'utf8').digest('hex').slice(0, 24);
  const state = await session(store, { archives: async () => [{ conversationId: chat, prompt: '收菜', title: '游戏任务', result: result('game-run', '请决定下一步') }] });
  assert.equal(await state.openConversationById(chat), true); loaded(state, chat); assert.ok(state.homeState.messages.some(m => m.kind === 'assistant' && m.content === '请决定下一步'));
  assert.equal(await state.openConversationById('missing-chat'), false); assert.equal(state.snapshot.selectedConversationId, chat);
});
test('fastConversationSwitchesKeepTheLastSelectionAndRestoreEveryBody', async () => {
  const { store, db } = fixture(); await store.save(snapshot({ first: loadingState('first'), second: loadingState('second'), third: loadingState('third') })); const state = await session(store);
  const second = state.selectConversation('second'), third = state.selectConversation('third'); await Promise.all([second, third]); loaded(state, 'third'); assert.deepEqual(state.homeState.messages.map(m => m.id), ids('third'));
  await state.selectConversation('second'); loaded(state, 'second'); assert.deepEqual(state.homeState.messages.map(m => m.id), ids('second'));
  await state.selectConversation('first'); loaded(state, 'first'); assert.deepEqual(state.homeState.messages.map(m => m.id), ids('first'));
  assert.equal((await db.messageCount('first')) + (await db.messageCount('second')) + (await db.messageCount('third')), 9);
});
test('deletingTheSelectedConversationLoadsTheNextConversation', async () => {
  const { store, db } = fixture(); await store.save(snapshot({ first: loadingState('first'), second: loadingState('second') })); const state = await session(store); await state.deleteConversation('first');
  loaded(state, 'second'); assert.deepEqual(state.homeState.messages.map(m => m.id), ids('second')); assert.equal(await db.messageCount('first'), 0); assert.equal(await db.messageCount('second'), 3);
});
test('deletingAnUnloadedConversationKeepsOtherStoredConversationsIntact', async () => {
  const { store, db } = fixture(); await store.save(snapshot({ first: loadingState('first'), second: loadingState('second'), third: loadingState('third') })); const state = await session(store);
  await state.deleteConversation('second'); assert.equal(await db.messageCount('second'), 0); await state.selectConversation('third'); loaded(state, 'third');
  assert.equal(state.homeState.messages.length, 3); assert.equal(await db.messageCount('first'), 3); assert.deepEqual(new Set(state.snapshot.conversationsById.keys()), new Set(['first', 'third']));
});
test('loadingAnEarlierPageThenDeletingATurnPreservesTheEarlierPrefix', async () => {
  const { store, db } = fixture(); await store.save(snapshot({ first: loadingState('first', 150) })); const state = await session(store);
  assert.equal(state.homeState.hasEarlierMessages, true); assert.equal(state.homeState.messages.length, 128); await state.loadEarlierMessages(); assert.equal(state.homeState.hasEarlierMessages, false);
  assert.deepEqual(state.homeState.messages.map(m => m.id), ids('first', 150)); await state.deleteMessageTurn('first-149'); assert.equal(await db.messageCount('first'), 149); assert.deepEqual((await store.loadAllMessages('first')).map(m => m.id), ids('first', 149));
});
test('searchAndExportIncludeMessagesOutsideTheLoadedWindow', async () => {
  const { store } = fixture(); await store.save(snapshot({ first: loadingState('first'), second: loadingState('second', 150) })); const state = await session(store);
  await state.search('second-message-0-end'); assert.deepEqual(state.searchMatches, new Set(['second'])); assert.equal(state.snapshot.selectedConversationId, 'first');
  const markdown = await state.exportMarkdown('second', labels); assert.ok(markdown?.includes('second-message-0-end')); assert.ok(markdown?.includes('second-message-149-end')); assert.equal(state.isLoadingConversation, false);
});
test('repeatedPrefetchRequestsLoadOnlyOnePage', async () => {
  const { store } = fixture(); await store.save(snapshot({ first: loadingState('first', 600) })); const state = await session(store);
  await Promise.all(Array.from({ length: 10 }, () => state.loadEarlierMessages()));
  assert.equal(state.homeState.messages.length, 256); assert.equal(new Set(state.homeState.messages.map(m => m.id)).size, 256); assert.equal(state.homeState.messages[0].id, 'first-344');
});
test('switchingConversationCancelsAnOutstandingHistoryRead', async () => {
  const { store, db } = fixture(); await store.save(snapshot({ first: loadingState('first', 600), second: loadingState('second') })); const state = await session(store);
  const page = state.loadEarlierMessages(); await state.selectConversation('second'); await page; loaded(state, 'second'); assert.equal(state.historyLoadState, 'Idle'); assert.deepEqual(state.homeState.messages.map(m => m.id), ids('second')); assert.equal(await db.messageCount('first'), 600);
});
test('failedPageKeepsTheCurrentWindowAndCanBeRetried', async () => {
  const { store, db } = fixture(), original = loadingState('first', 300), long = userMessage('first-100', '历史正文'.repeat(6000));
  await store.save(snapshot({ first: { ...original, messages: original.messages.map(m => m.id === long.id ? long : m) } })); const state = await session(store);
  await db.deleteTextChunks('conversation_messages', 'first-100', 'content'); await state.loadEarlierMessages(); assert.equal(state.historyLoadState, 'Failed'); assert.equal(state.homeState.messages.length, 128); assert.equal(state.homeState.messages[0].id, 'first-172');
  await putMessage(db, messageRow(long.id, 'first', 100, 'user', long.content)); await state.loadEarlierMessages(); assert.equal(state.historyLoadState, 'Idle'); assert.deepEqual(state.homeState.messages.find(m => m.id === long.id), long); assert.equal(await db.messageCount('first'), 300);
});
test('returningToTheLatestMessagesReleasesOldPagesWithoutDeletingHistory', async () => {
  const { store, db } = fixture(); await store.save(snapshot({ first: loadingState('first', 600) })); const state = await session(store);
  for (let i = 0; i < 3; i++) await state.loadEarlierMessages(); assert.equal(state.homeState.messages.length, 512);
  await state.updateHistoryViewport(true); assert.equal(state.homeState.messages.length, 128); assert.equal(state.homeState.messages[0].id, 'first-472'); assert.equal(await db.messageCount('first'), 600);
  await state.loadEarlierMessages(); assert.equal(state.homeState.messages.length, 256); assert.equal(state.homeState.messages[0].id, 'first-344');
});
test('scrollingBackToHistoryCancelsPageReleaseWithoutCancellingTheNextRead', async () => {
  const { store } = fixture(); await store.save(snapshot({ first: loadingState('first', 600) })); const state = await session(store);
  for (let i = 0; i < 3; i++) await state.loadEarlierMessages(); const release = state.updateHistoryViewport(true); await state.updateHistoryViewport(false); await state.loadEarlierMessages(); await release;
  assert.equal(state.homeState.messages.length, 600); assert.equal(state.homeState.hasEarlierMessages, false); assert.equal(state.historyLoadState, 'Idle');
});
test('concurrentResultsAndStopRemainBoundToTheirConversations', async () => {
  const { store } = fixture(); await store.save(snapshot({ first: loadingState('first'), second: loadingState('second') })); const state = await session(store);
  assert.equal(state.registerRun('run-a', 'first'), true); await state.selectConversation('second'); assert.equal(state.registerRun('run-b', 'second'), true); await state.selectConversation('first');
  state.stopCurrentRun(); assert.equal(state.runs.isStopRequested('run-a'), true); assert.equal(state.runs.isStopRequested('run-b'), false); await state.completeRun(result('run-b', '乙任务结果'));
  assert.equal(state.snapshot.selectedConversationId, 'first'); assert.equal(state.homeState.isStreaming, true); assert.ok(!state.homeState.messages.some(m => m.kind === 'assistant' && m.content === '乙任务结果')); assert.deepEqual(state.runs.observedRunIds, new Set(['run-a']));
  await state.completeRun(result('run-a', '甲任务结果')); assert.equal(state.homeState.isStreaming, false); await state.selectConversation('second');
  assert.ok(state.homeState.messages.some(m => m.kind === 'assistant' && m.content === '乙任务结果')); assert.ok(!state.homeState.messages.some(m => m.kind === 'assistant' && m.content === '甲任务结果'));
});
test('deletingAnActiveConversationWaitsForItsResultAndKeepsOtherRuns', async () => {
  const { store } = fixture(); await store.save(snapshot({ first: loadingState('first'), second: loadingState('second') })); const state = await session(store);
  state.registerRun('run-a', 'first'); await state.selectConversation('second'); state.registerRun('run-b', 'second'); await state.deleteConversation('first');
  assert.ok(state.snapshot.conversationsById.has('first')); assert.equal(state.runs.isStopRequested('run-a'), true); assert.equal(state.runs.isStopRequested('run-b'), false);
  await state.completeRun(result('run-a', '已保存的结果')); assert.ok(!state.snapshot.conversationsById.has('first')); assert.deepEqual(state.runs.observedRunIds, new Set(['run-b'])); assert.equal(state.homeState.isStreaming, true);
  await state.completeRun(result('run-a', '迟到的结果')); assert.ok(!state.snapshot.conversationsById.has('first')); await state.completeRun(result('run-b', '另一个任务结果'));
});
test('backgroundQueueContinuesWithItsCompletedHistoryWhileAnotherConversationRuns', async () => {
  const { store } = fixture(); await store.save(snapshot({ first: loadingState('first'), second: loadingState('second') })); let sent = 0;
  const state = await session(store, { dispatchQueued: async (id, prompt, history) => { assert.equal(id, 'second'); assert.equal(prompt, '后台后续消息'); assert.ok(history.some(m => m.content === '后台已完成的回复')); sent++; } });
  state.registerRun('run-a', 'first'); await state.selectConversation('second'); state.registerRun('run-b', 'second'); state.enqueue('后台后续消息'); await state.selectConversation('first'); await state.completeRun(result('run-b', '后台已完成的回复'));
  const saved = (await store.loadConversation('second'))!, answer = saved.journal.findIndex(m => m.content === '后台已完成的回复'), followup = saved.journal.findIndex(m => m.content === '后台后续消息');
  assert.ok(answer >= 0 && answer < followup); assert.equal(sent, 1); assert.equal(state.snapshot.selectedConversationId, 'first'); assert.equal(state.homeState.isStreaming, true); assert.equal(state.runs.activeRunId('first'), 'run-a'); assert.deepEqual(state.runs.observedRunIds, new Set(['run-a'])); await state.completeRun(result('run-a', '前台任务完成'));
});
