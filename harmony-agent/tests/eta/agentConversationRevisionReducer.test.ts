// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/AgentConversationRevisionReducerTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { AgentConversationRevisionReducer as R } from '../../entry/src/main/ets/core/eta/agentConversationRevisionReducer';
import { conversationMessage as h, userMessage as u } from '../../entry/src/main/ets/core/eta/model/conversation';
import { state, conversationState } from './conversationFixtures';
test('editingUserTurnCoveredBySummaryDropsStaleSummary', () => {
  const summary = { ...h('assistant', '旧操作结果'), contextSummary: true, compactedUserTurns: 1, summaryThroughUserTurn: 2 };
  const initial = state({ messages: [u('old', '旧请求'), u('latest', '最新请求')], history: [summary, h('user', '最新请求')] });
  const boundary = R.boundary(initial, 'latest')!; assert.equal(boundary.contextWasCompacted, true); assert.deepEqual(boundary.historyPrefix, []);
  const next = { ...initial, messages: [...initial.messages, u('next', '后续请求')], history: [...initial.history, h('user', '后续请求')] };
  assert.ok(R.boundary(next, 'next')!.historyPrefix.includes(summary));
});
test('boundaryMapsAssistantToItsUserTurnAndKeepsToolTranscriptPrefix', () => {
  const b = R.boundary(conversationState(), 'assistant-2')!;
  assert.equal(b.userMessage.id, 'user-2'); assert.equal(b.userMessageIndex, 4); assert.equal(b.laterTurnCount, 1); assert.equal(b.contextWasCompacted, false); assert.deepEqual(b.historyPrefix.map(x => x.role), ['user', 'assistant', 'tool', 'assistant']);
});
test('deleteFromMiddleTurnTruncatesMessagesAndHistoryTogether', () => {
  const initial = { ...conversationState(), appliedRuntimeRunIds: ['run-1', 'run-2'] };
  const revised = R.deleteFromTurn(initial, 'assistant-2')!;
  assert.deepEqual(revised.messages.map(x => x.id), ['user-1', 'thinking-1', 'tool-1', 'assistant-1']); assert.deepEqual(revised.history.map(x => x.role), ['user', 'assistant', 'tool', 'assistant']); assert.deepEqual(revised.appliedRuntimeRunIds, ['run-1', 'run-2']);
});
test('compactedCheckpointAlignsRetainedTurnsFromTheTail', () => {
  const initial = { ...conversationState(), history: [h('system', '已压缩'), h('user', '第二问'), h('assistant', '第二答'), h('user', '第三问'), h('assistant', '第三答')] };
  const missing = R.boundary(initial, 'user-1')!, retained = R.boundary(initial, 'user-2')!;
  assert.equal(missing.contextWasCompacted, true); assert.deepEqual(missing.historyPrefix, []); assert.equal(retained.contextWasCompacted, false); assert.deepEqual(retained.historyPrefix.map(x => x.role), ['system']);
});
test('visibleMessagesStopAtEditedUserWithoutMutatingTheSource', () => {
  const messages = conversationState().messages;
  assert.deepEqual(R.visibleMessagesForEdit(messages, 'user-2').map(x => x.id), ['user-1', 'thinking-1', 'tool-1', 'assistant-1', 'user-2']); assert.equal(messages.length, 8);
});
test('invalidMessageDoesNotChangeConversation', () => {
  const initial = conversationState(); assert.equal(R.boundary(initial, 'missing'), null); assert.equal(R.deleteFromTurn(initial, 'missing'), null); assert.equal(R.visibleMessagesForEdit(initial.messages, 'missing'), initial.messages);
});
