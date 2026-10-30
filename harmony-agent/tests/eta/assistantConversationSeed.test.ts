// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/AssistantConversationSeedTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { AssistantConversationSeed as S } from '../../entry/src/main/ets/core/eta/assistantConversationSeed';
import { AgentRuntimeHistoryReducer as H } from '../../entry/src/main/ets/core/eta/agentRuntimeHistoryReducer';
import { conversationMessage as h, userMessage as u } from '../../entry/src/main/ets/core/eta/model/conversation';
import { state } from './conversationFixtures';
test('livePromptIsDurableAndNotDuplicatedByRepeatedRecovery', () => {
  const live = S.apply(state(), 'voice-1', '查询天气'); assert.deepEqual(live.messages, [u('user-voice-1', '查询天气')]); assert.equal(live.history[0].content, '查询天气'); assert.deepEqual(live.history, live.journal); assert.equal(S.apply(live, 'voice-1', '查询天气'), live);
  const completed = H.apply(live, 'voice-1', [h('assistant', '晴天')]).state; assert.deepEqual(completed.history.map(x => x.content), ['查询天气', '晴天']); assert.equal(S.apply(completed, 'voice-1', '查询天气'), completed);
});
test('seedingNextPromptPreservesUnsummarizedJournal', () => {
  const original = h('user', '旧指令'), summary = { ...h('assistant', '摘要'), contextSummary: true };
  const next = S.apply(state({ history: [summary], journal: [original] }), 'voice-2', '新指令');
  assert.deepEqual(next.history.map(x => x.content), ['摘要', '新指令']); assert.deepEqual(next.journal.map(x => x.content), ['旧指令', '新指令']);
});
