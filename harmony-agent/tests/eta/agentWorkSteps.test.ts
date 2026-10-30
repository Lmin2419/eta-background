// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/components/AgentWorkStepsTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { toWorkSteps as steps, outcomeLine } from '../../entry/src/main/ets/core/eta/agentWorkSteps';
import { toolActivityMessage, thinkingMessage, ToolActivityStatusUi } from '../../entry/src/main/ets/core/eta/model/conversation';
const tool = (id: string, name: string, status: ToolActivityStatusUi = 'Success', result: string | null = null) => toolActivityMessage(id, name, status, name, null, result);
test('consecutiveSameToolCallsFormOneGroup', () => { const result = steps([tool('t1', 'read_file'), tool('t2', 'read_file'), tool('t3', 'read_file'), tool('t4', 'run_command')]); assert.equal(result.length, 2); assert.equal(result[0].kind, 'group'); if (result[0].kind === 'group') assert.deepEqual(result[0].calls.map(x => x.id), ['t1', 't2', 't3']); assert.equal(result[1].kind, 'single'); });
test('thinkingBreaksToolGroup', () => { assert.deepEqual(steps([tool('t1', 'read_file'), thinkingMessage('k1', '想'), tool('t2', 'read_file')]).map(x => x.key), ['t1', 'k1', 't2']); });
test('browserCallsStayIndividual', () => { const result = steps([tool('b1', 'browser_use'), tool('b2', 'browser_use')]); assert.equal(result.length, 2); assert.ok(result.every(x => x.kind === 'single')); });
test('groupKeyStaysStableWhileCallsAppend', () => { const first = steps([tool('t1', 'read_file'), tool('t2', 'read_file')]); const next = steps([tool('t1', 'read_file'), tool('t2', 'read_file'), tool('t3', 'read_file')]); assert.equal(first[0].key, next[0].key); });
test('terminalOutcomeUsesLastOutputLine', () => { assert.equal(outcomeLine(tool('t1', 'run_command', 'Success', '执行完成\nline 1\nline 2\n…')), 'line 2'); });
test('trivialSuccessHasNoOutcome', () => { assert.equal(outcomeLine(tool('t1', 'observe_screen', 'Success', '完成')), null); assert.equal(outcomeLine(tool('t2', 'observe_screen', 'Success', '完成 · 2 张图片')), '2 张图片'); });
test('failureOutcomeStripsPrefixAndCode', () => { assert.equal(outcomeLine(tool('t1', 'tap', 'Failed', '失败 · 找不到元素 · code=E_NOT_FOUND')), '找不到元素'); });
test('failedTerminalFallsBackToOutput', () => { assert.equal(outcomeLine(tool('t1', 'run_command', 'Failed', '失败 · 退出码 1\nno such file')), '退出码 1'); });
test('runningToolHasNoOutcome', () => { assert.equal(outcomeLine(tool('t1', 'read_file', 'Running', 'x')), null); });
