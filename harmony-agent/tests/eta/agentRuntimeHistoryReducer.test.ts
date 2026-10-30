// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/AgentRuntimeHistoryReducerTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { AgentRuntimeHistoryReducer as H } from '../../entry/src/main/ets/core/eta/agentRuntimeHistoryReducer';
import { AgentConversationRevisionReducer as R } from '../../entry/src/main/ets/core/eta/agentConversationRevisionReducer';
import { conversationMessage as h, userMessage as u, contextSnapshot as snapshot } from '../../entry/src/main/ets/core/eta/model/conversation';
import { state } from './conversationFixtures';
test('journalRetainsOriginalsAndContextIncludesPostSnapshotSteps', () => {
  const original = h('user', '不能丢失的旧约束'), first = h('assistant', '快照前已执行'), later = h('assistant', '快照后已执行'), summary = { ...h('assistant', '摘要'), contextSummary: true };
  const initial = state({ history: [original], journal: [original] });
  const snap = { ...snapshot('run', [summary]), consumedUserTurns: 1, consumedTranscriptMessages: 1 };
  const result = H.apply(initial, 'run', [first, later], snap).state;
  assert.deepEqual(result.journal, [original, first, later]); assert.deepEqual(result.history, [summary, later]); assert.equal(H.apply(result, 'run', [first, later], snap).state, result);
});
test('editingSummarizedTurnRebuildsFromOriginalJournal', () => {
  const first = h('user', '原始指令'), answer = h('assistant', '原始结果'), second = h('user', '后续问题'), summary = { ...h('assistant', '不完整摘要'), contextSummary: true, compactedUserTurns: 2 };
  const initial = state({ messages: [u('u1', '原始指令'), u('u2', '后续问题')], history: [summary], journal: [first, answer, second] });
  const boundary = R.boundary(initial, 'u2')!; assert.deepEqual(boundary.historyPrefix, [first, answer]); assert.equal(boundary.contextWasCompacted, false);
});
test('replacementKeepsLaterUserMessagesAndIsAppliedOnce', () => {
  const old = h('user', '旧问题'), later = h('user', '后来追加'), summary = { ...h('assistant', '摘要'), contextSummary: true, compactedUserTurns: 1 };
  const snap = { ...snapshot('run-1', [summary]), consumedUserTurns: 1, coveredUserTurns: 1 };
  const first = H.apply(state({ history: [old, later] }), 'run-1', [h('assistant', '不应重复追加')], snap); assert.deepEqual(first.state.history, [summary, later]);
  const again = H.apply(first.state, 'run-1', [], snap); assert.equal(again.alreadyApplied, true); assert.deepEqual(again.state.history, first.state.history);
});
test('snapshotKeepsAcceptedButUnconsumedSteeringAfterCancellation', () => {
  const initial = state({ messages: [u('user-run-supplement-1', '已消费'), u('user-run-supplement-2', '待消费')] });
  const snap = { ...snapshot('run', [h('assistant', '已完成前序步骤')]), consumedSupplementCount: 1 };
  assert.deepEqual(H.apply(initial, 'run', [], snap).state.history.map(x => x.content), ['已完成前序步骤', '待消费']);
});
test('recoveryThenLiveDeliveryCommitsTranscriptOnlyOnce', () => {
  const transcript = [h('assistant', '完成')], recovered = H.apply(state({ isStreaming: true }), 'run-1', transcript), live = H.apply(recovered.state, 'run-1', transcript);
  assert.equal(live.alreadyApplied, true); assert.deepEqual(live.state.history, transcript); assert.deepEqual(live.state.appliedRuntimeRunIds, ['run-1']);
});
