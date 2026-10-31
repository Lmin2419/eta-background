// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/model/OpenAiResponsesProviderTest.kt @ 3b1794d (11).
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { JsonObject, ProviderEvent } from '../../entry/src/main/ets/core/eta/protocol/providerTypes';
import { buildResponsesRequest, attachOutputItems, outputItems } from '../../entry/src/main/ets/core/eta/protocol/responsesRequestBuilder';
import { formatCitations } from '../../entry/src/main/ets/core/eta/protocol/citationFormatter';
import { config, event, responseTextEvent, messageItem, reasoningItem, withSseServer, complete, timelineLabel, latch } from './support/providerHttpFixture';
import { agentWebToolFixture } from './support/agentWebToolFixture';
test('hostedSearchDeclarationExcludesSameNamedFunctionAndPreservesOtherTools', () => {
  const functions = agentWebToolFixture();
  for (const enabled of [false, true]) {
    const request = buildResponsesRequest({ ...config(), hostedWebSearchEnabled: enabled }, [], functions), tools = request.tools as JsonObject[];
    assert.equal(tools.filter(t => t.type === 'web_search').length, enabled ? 1 : 0); assert.equal(tools.filter(t => t.type === 'function' && t.name === 'web_search').length, enabled ? 0 : 1); assert.equal(tools.filter(t => t.name === 'fetch_url').length, 1);
  }
  assert.equal(functions.length, 2);
});
test('nativeReasoningDeltasArriveBeforeDoneAndTypedFinalContentIsNotDuplicated', async () => {
  const firstDelta = latch(), completed = latch(), observed = latch(); let completedBeforeClose = false;
  const reasoningText = '先分析输入，再整理答案。', nativeItem = { id: 'rs_native', type: 'reasoning', summary: [], content: [{ type: 'reasoning_text', text: reasoningText }] };
  const re = (type: string, key: string, text: string) => event(type, { item_id: 'rs_native', output_index: 0, content_index: 0, [key]: text });
  const first = re('response.reasoning_text.delta', 'delta', '先分析输入，');
  const remaining = re('response.reasoning_text.delta', 'delta', '再整理答案。') + re('response.reasoning_text.done', 'text', reasoningText) + event('response.output_item.done', { output_index: 0, item: nativeItem }) + responseTextEvent('response.output_text.delta', 'msg_1', 1, 'delta', '答案') + event('response.completed', { response: { status: 'completed', output: [nativeItem, messageItem('msg_1', '答案')] } });
  await withSseServer('', async url => {
    const events: ProviderEvent[] = []; const result = await complete(url, e => { events.push(e); if (e.type === 'BlockDelta' && e.kind === 'THINKING') firstDelta.done(); if (e.type === 'Completed') completed.done(); }, '测试推理');
    await observed.wait(); assert.equal(completedBeforeClose, true);
    assert.deepEqual(events.filter(e => e.type === 'BlockDelta' && e.kind === 'THINKING').map(e => e.type === 'BlockDelta' && e.delta), ['先分析输入，', '再整理答案。']);
    const ends = events.filter(e => e.type === 'BlockEnd' && e.kind === 'THINKING'); assert.equal(ends.length, 1); assert.equal(ends[0].type === 'BlockEnd' && ends[0].content, reasoningText); assert.equal(ends[0].type === 'BlockEnd' && ends[0].replaceContent, false);
    assert.equal(result.reasoning_content, reasoningText); assert.equal(result.content, '答案'); assert.deepEqual(outputItems(result)?.[0], nativeItem);
  }, async out => { out.write(first); await firstDelta.wait(); out.write(remaining); await completed.wait(); completedBeforeClose = true; observed.done(); });
});
test('typedReasoningPartsKeepTheirIdentityAndOverrideLegacyAlias', async () => {
  const item = { id: 'rs_parts', type: 'reasoning', reasoning_text: '旧字段不应重复追加', content: [{ type: 'reasoning_text', text: '第一段' }, { type: 'reasoning_text', text: '第二段补全' }] };
  const body = event('response.reasoning_text.delta', { item_id: 'rs_parts', output_index: 0, content_index: 1, delta: '第二段' }) + event('response.completed', { response: { status: 'completed', output: [item] } });
  await withSseServer(body, async url => { const events: ProviderEvent[] = [], result = await complete(url, e => events.push(e), '测试推理'); assert.equal(result.reasoning_content, '第一段\n第二段补全'); const corrections = events.filter(e => e.type === 'BlockEnd' && e.replaceContent); assert.equal(corrections.length, 1); assert.equal(corrections[0].type === 'BlockEnd' && corrections[0].index, 0); assert.equal(corrections[0].type === 'BlockEnd' && corrections[0].content, '第二段补全'); });
});
test('requestUsesTypedInputProtectedFieldsAndOptionalHostedSearch', () => {
  const assistant = { role: 'assistant', content: '先检查' }; attachOutputItems(assistant, [{ id: 'rs_1', type: 'reasoning', encrypted_content: 'opaque' }]);
  const messages = [{ role: 'user', content: [{ type: 'text', text: '看图' }, { type: 'image_url', image_url: { url: 'data:image/png;base64,AA==' } }] }, assistant, { role: 'tool', tool_call_id: 'call_1', content: '{"ok":true}' }];
  const tools = [{ type: 'function', function: { name: 'device_info', description: '读取设备', parameters: { type: 'object' } } }];
  const request = buildResponsesRequest({ ...config(), hostedWebSearchEnabled: true, reasoningCapabilities: { supportedEfforts: ['high'], canDisable: true }, reasoningEffort: 'high', extraBodyJson: '{"model":"wrong","store":true,"metadata":{"source":"eta"}}', customBody: [{ key: 'stream', value: false }] }, messages, tools);
  assert.equal(request.model, 'test-model'); assert.equal(request.instructions, '系统提示'); assert.equal(request.stream, true); assert.equal(request.store, false); assert.equal('previous_response_id' in request, false); assert.equal((request.metadata as JsonObject).source, 'eta'); assert.deepEqual(request.reasoning, { summary: 'auto', effort: 'high' });
  const input = request.input as JsonObject[], actualTools = request.tools as JsonObject[];
  assert.equal((input[0].content as JsonObject[])[1].type, 'input_image'); assert.equal(input[1].type, 'reasoning'); assert.equal(input[2].type, 'function_call_output'); assert.equal(actualTools[0].strict, false); assert.equal(actualTools[1].type, 'web_search');
});
test('requestUsesMergedInstructionsAndTypedDurableHistoryMessages', () => {
  const messages = [{ role: 'system', content: '基础约束' }, { role: 'user', content: '旧问题' }, { role: 'system', content: '压缩上下文' }, { role: 'assistant', content: '旧回答' }, { role: 'user', content: '当前问题' }];
  const request = buildResponsesRequest(config(), messages, []), input = request.input as JsonObject[];
  assert.equal(request.instructions, '基础约束\n\n压缩上下文'); assert.deepEqual(input.map(m => m.role), ['user', 'assistant', 'user']); assert.deepEqual(input.map(m => m.type), ['message', 'message', 'message']); assert.equal(input[1].content, '旧回答');
});
test('completeUsesTerminalOutputAsAuthorityAndEmitsSemanticEvents', async () => {
  const output = [{ id: 'rs_1', type: 'reasoning', encrypted_content: 'opaque', summary: [{ type: 'summary_text', text: '完整摘要' }] }, { id: 'ws_1', type: 'web_search_call', status: 'completed' }, { id: 'msg_1', type: 'message', content: [{ type: 'output_text', text: '你好世界', annotations: [{ type: 'url_citation', start_index: 2, end_index: 4, url: 'https://example.com/source', title: '来源' }] }] }, { id: 'fc_1', type: 'function_call', call_id: 'call_1', name: 'device_info', arguments: '{"detail":true}' }];
  const response = { status: 'completed', output, usage: { input_tokens: 10, output_tokens: 8, total_tokens: 18, input_tokens_details: { cached_tokens: 3 }, output_tokens_details: { reasoning_tokens: 5 } } };
  const body = event('response.reasoning_summary_text.delta', { delta: '完整' }) + event('response.output_item.added', { item: { id: 'ws_1', type: 'web_search_call' } }) + event('response.output_item.done', { item: { id: 'ws_1', type: 'web_search_call', status: 'completed' } }) + event('response.output_text.delta', { delta: '你好' }) + event('response.completed', { response });
  await withSseServer(body, async (url, received) => {
    const events: ProviderEvent[] = [], result = await complete(url, e => events.push(e)); assert.ok(received().includes('"store":false')); assert.equal(result.reasoning_content, '完整摘要'); assert.ok(String(result.content).includes('[[1]]')); assert.equal((result.tool_calls as JsonObject[])[0].id, 'call_1'); assert.ok(outputItems(result));
    assert.equal(events.filter(e => e.type === 'HostedToolStarted').length, 1); assert.equal(events.filter(e => e.type === 'HostedToolFinished').length, 1); const uses = events.filter(e => e.type === 'Usage'); assert.equal(uses.length, 1); assert.equal(uses[0].usage.contextTokens, 18); assert.equal(uses[0].usage.cachedTokens, 3); assert.equal(uses[0].usage.reasoningTokens, 5);
  });
});
test('completeUsesStreamedTextWhenCompletedOutputIsEmpty', async () => {
  const body = event('response.reasoning_summary_text.delta', { delta: '简要分析' }) + event('response.output_text.delta', { delta: '你好' }) + event('response.output_text.delta', { delta: '世界' }) + event('response.completed', { response: { status: 'completed' } });
  await withSseServer(body, async url => { const result = await complete(url); assert.equal(result.content, '你好世界'); assert.equal(result.reasoning_content, '简要分析'); assert.equal(result.finish_reason, 'stop'); assert.equal(outputItems(result), null); });
});
test('completePreservesInterleavedResponseItemOrderAndBlockIdentity', async () => {
  const output = [reasoningItem('rs_1', '先判断'), messageItem('msg_1', '我先查一下。'), { id: 'ws_1', type: 'web_search_call', status: 'completed' }, reasoningItem('rs_2', '整理结果'), messageItem('msg_2', '这是最终答案。')];
  let body = responseTextEvent('response.reasoning_summary_text.delta', 'rs_1', 0, 'delta', '先判断') + responseTextEvent('response.reasoning_summary_text.done', 'rs_1', 0, 'text', '先判断') + responseTextEvent('response.output_text.delta', 'msg_1', 1, 'delta', '我先查一下。') + responseTextEvent('response.output_text.done', 'msg_1', 1, 'text', '我先查一下。');
  body += event('response.output_item.added', { output_index: 2, item: { id: 'ws_1', type: 'web_search_call' } }) + event('response.output_item.done', { output_index: 2, item: { id: 'ws_1', type: 'web_search_call', status: 'completed' } });
  body += responseTextEvent('response.reasoning_summary_text.delta', 'rs_2', 3, 'delta', '整理结果') + responseTextEvent('response.reasoning_summary_text.done', 'rs_2', 3, 'text', '整理结果') + responseTextEvent('response.output_text.delta', 'msg_2', 4, 'delta', '这是最终答案。') + responseTextEvent('response.output_text.done', 'msg_2', 4, 'text', '这是最终答案。') + event('response.completed', { response: { status: 'completed', output } });
  await withSseServer(body, async url => { const events: ProviderEvent[] = [], result = await complete(url, e => events.push(e), '搜索'); assert.equal(result.content, '我先查一下。这是最终答案。'); assert.deepEqual(events.map(timelineLabel).filter(Boolean), ['start:THINKING:0', 'delta:THINKING:0:先判断', 'end:THINKING:0', 'start:TEXT:1', 'delta:TEXT:1:我先查一下。', 'end:TEXT:1', 'hosted-start:ws_1', 'hosted-end:ws_1', 'start:THINKING:2', 'delta:THINKING:2:整理结果', 'end:THINKING:2', 'start:TEXT:3', 'delta:TEXT:3:这是最终答案。', 'end:TEXT:3']); });
});
test('completeUsesStreamedToolCallWhenCompletedOutputIsEmpty', async () => {
  const body = event('response.output_item.added', { output_index: 0, item: { id: 'fc_1', type: 'function_call', call_id: 'call_1', name: 'device_info' } }) + event('response.function_call_arguments.delta', { item_id: 'fc_1', delta: '{"detail":true}' }) + event('response.completed', { response: { status: 'completed', output: [] } });
  await withSseServer(body, async url => { const result = await complete(url, () => {}, '读取设备'), call = (result.tool_calls as JsonObject[])[0]; assert.equal(call.id, 'call_1'); assert.deepEqual(call.function, { name: 'device_info', arguments: '{"detail":true}' }); assert.equal(result.finish_reason, 'tool_calls'); assert.equal(outputItems(result), null); });
});
test('completeRejectsEofWithoutTerminalEvent', async () => { await withSseServer(event('response.output_text.delta', { delta: 'partial' }), async url => { await assert.rejects(complete(url, () => {}, 'hi'), /缺少合法终止事件/); }); });
test('citationsDeduplicateAndFallBackForInvalidOffsets', () => {
  const formatted = formatCitations('中文回答', [{ start: 0, end: 2, url: 'https://example.com/a', title: 'A' }, { start: 0, end: 2, url: 'https://example.com/a', title: '重复' }, { start: 99, end: 120, url: 'https://example.com/b', title: 'B' }]);
  assert.equal(formatted.split('https://example.com/a').length - 1, 1); assert.ok(formatted.includes('[[1]]')); assert.ok(formatted.includes('来源：')); assert.ok(formatted.includes('https://example.com/b'));
});
