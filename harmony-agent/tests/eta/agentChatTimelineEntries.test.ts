// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/components/AgentChatTimelineEntriesTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { toTimelineEntries as timeline } from '../../entry/src/main/ets/core/eta/agentChatTimelineEntries';
import { userMessage as u, agentMessage as a, thinkingMessage as t } from '../../entry/src/main/ets/core/eta/model/conversation';
test('unchangedWorkProcessReusesPreviousEntry', () => {
  const thinking = t('think-1', '分析'), first = [u('user-1', '问题'), thinking, a('agent-1', '答', true)], previous = timeline(first), next = timeline([first[0], thinking, a('agent-1', '答案', true)], previous); assert.equal(next[1], previous[1]);
});
test('changedWorkProcessMemberCreatesNewEntry', () => { const previous = timeline([t('think-1', '分析', true)]), next = timeline([t('think-1', '分析中', true)], previous); assert.notEqual(previous[0], next[0]); });
test('prependingTheStartOfAWorkProcessPreservesItsVisibleKey', () => {
  const partial = t('think-tail', '后续思考'), answer = a('answer', '结果'), previous = timeline([partial, answer]), next = timeline([u('user', '问题'), t('think-start', '起始思考'), partial, answer], previous);
  assert.equal(previous[0].key, next[1].key); assert.equal(previous[1].key, next[2].key); assert.equal(next[1].kind, 'work'); if (next[1].kind === 'work') assert.equal(next[1].messages.length, 2);
});
test('splittingAPreviouslyPrependedGroupDoesNotReuseItsKeyTwice', () => {
  const first = t('think-first', '起始思考'), last = t('think-last', '后续思考'), previous = timeline([first, last], timeline([last]));
  const next = timeline([first, a('answer', '中间结果'), last], previous); assert.equal(next.length, new Set(next.map(x => x.key)).size);
});
