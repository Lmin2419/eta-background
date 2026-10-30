// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/components/AgentChatFinalResultTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveFinalResultMessageIds as final } from '../../entry/src/main/ets/core/eta/agentChatFinalResult';
import { userMessage as u, agentMessage as a, toolActivityMessage } from '../../entry/src/main/ets/core/eta/model/conversation';
const tool = (id: string) => toolActivityMessage(id, 'browser_use', 'Success', 'query');
test('onlyLastAgentMessageOfEachTurnIsFinalResult', () => { assert.deepEqual(final([u('user-1', '查一下资料'), a('agent-1', '我来帮你搜索。'), tool('tool-1'), a('agent-2', '从搜索结果可以看到……'), tool('tool-2'), a('agent-3', '最终答案')]), new Set(['agent-3'])); });
test('multipleTurnsEachHaveTheirOwnFinalResult', () => { assert.deepEqual(final([u('user-1', '第一问'), a('agent-1', '第一答'), u('user-2', '第二问'), a('agent-2', '中间步骤'), tool('tool-1'), a('agent-3', '第二答')]), new Set(['agent-1', 'agent-3'])); });
test('turnInterruptedAfterToolKeepsPreviousAgentMessageAsFinalResult', () => { assert.deepEqual(final([u('user-1', '任务'), a('agent-1', '我先试试。'), tool('tool-1')]), new Set(['agent-1'])); });
test('turnWithoutAgentMessageProducesNoFinalResult', () => { assert.deepEqual(final([u('user-1', '任务'), tool('tool-1')]), new Set()); });
test('streamingTurnDoesNotMarkIntermediateMessageAsFinalResult', () => { assert.deepEqual(final([u('user-1', '任务'), a('agent-1', '我来帮你搜索。'), tool('tool-1')], true), new Set()); });
test('streamingKeepsFinalResultOfCompletedEarlierTurns', () => { assert.deepEqual(final([u('user-1', '第一问'), a('agent-1', '第一答'), u('user-2', '第二问'), a('agent-2', '中间步骤'), tool('tool-1')], true), new Set(['agent-1'])); });
test('streamingEndRestoresFinalResultOfLastTurn', () => { const messages = [u('user-1', '任务'), a('agent-1', '中间步骤'), tool('tool-1'), a('agent-2', '最终答案')]; assert.deepEqual(final(messages, true), new Set()); assert.deepEqual(final(messages, false), new Set(['agent-2'])); });
