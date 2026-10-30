// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/AgentRunEventCoalescerTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { AgentRunEventCoalescer } from '../../entry/src/main/ets/core/eta/agentRunEventCoalescer';
import { AssistantBlockDelta } from '../../entry/src/main/ets/core/eta/model/conversation';
function delta(index: number, text: string, chars: number = text.length, kind: AssistantBlockDelta['kind'] = 'TEXT'): AssistantBlockDelta { return { round: 1, kind, index, deltaChars: chars, delta: text }; }
test('coalescesAdjacentDeltasFromTheSameBlock', () => {
  const c = new AgentRunEventCoalescer(); assert.equal(c.append('run-1', delta(0, '你')), null); assert.equal(c.append('run-1', delta(0, '好')), null); assert.deepEqual(c.flush('run-1'), delta(0, '你好', 2));
});
test('flushesThePreviousBlockBeforeBufferingANewOne', () => {
  const c = new AgentRunEventCoalescer(), text = delta(0, '回答'), thinking = delta(1, '分析', 2, 'THINKING');
  assert.equal(c.append('run-1', text), null); assert.deepEqual(c.append('run-1', thinking), text); assert.deepEqual(c.flush('run-1'), thinking);
});
test('keepsRunsIndependent', () => {
  const c = new AgentRunEventCoalescer(), a = delta(0, 'A'), b = delta(0, 'B'); c.append('run-1', a); c.append('run-2', b); assert.deepEqual(c.flush('run-1'), a); assert.deepEqual(c.flush('run-2'), b);
});
