// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/model/CodexResponsesRequestTest.kt @ 3b1794d (5).
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Pure request/stream compatibility only. Subscription login and HTTP auth stay C, unavailable.
import test from 'node:test';
import assert from 'node:assert/strict';
import { ProviderModelConfig, JsonObject } from '../../entry/src/main/ets/core/eta/protocol/providerTypes';
import { buildResponsesRequest } from '../../entry/src/main/ets/core/eta/protocol/responsesRequestBuilder';
import { OpenAiResponsesStream } from '../../entry/src/main/ets/core/eta/protocol/openAiResponsesProvider';
import { AgentModelFailure } from '../../entry/src/main/ets/core/eta/protocol/agentModelFailure';
import { RunControl } from '../../entry/src/main/ets/core/types';
const config = (): ProviderModelConfig => ({ providerId: 'account', baseUrl: 'https://chatgpt.com/backend-api/codex', apiKey: '', model: 'fixture', systemPrompt: 'fixture', authMode: 'openai_chatgpt', openAiEndpointMode: 'responses' });
const tools = () => [{ type: 'function', function: { name: 'read_file', description: '读取文件', parameters: { type: 'object', properties: {} } } }];
function done(response: JsonObject): JsonObject { const parser = new OpenAiResponsesStream(new RunControl()); parser.feed('data: ' + JSON.stringify({ type: 'response.done', response }) + '\n\n'); return parser.finish(); }
test('codexUsesFlatToolsAndStatelessResponsesHistory', () => {
  const messages = [{ role: 'assistant', tool_calls: [{ id: 'call_a', type: 'function', function: { name: 'read_file', arguments: '{}' } }] }, { role: 'tool', tool_call_id: 'call_a', content: 'fixture' }];
  const r = buildResponsesRequest(config(), messages, tools()), tool = (r.tools as JsonObject[])[0], input = r.input as JsonObject[];
  assert.equal(tool.type, 'function'); assert.equal(tool.name, 'read_file'); assert.equal(input[0].name, 'read_file'); assert.equal('namespace' in input[0], false); assert.equal(input[1].call_id, 'call_a'); assert.equal(r.store, false); assert.equal(r.stream, true); assert.equal(r.parallel_tool_calls, true); assert.equal((r.include as string[])[0], 'reasoning.encrypted_content');
});
test('codexRequestEncodesAdvertisedMaxForModelsBeyondTheInitialPresets', () => { for (const model of ['gpt-6.1-sol', 'remote-model-alias']) { const r = buildResponsesRequest({ ...config(), model, reasoningEffort: 'max', reasoningCapabilities: { supportedEfforts: ['max'] } }, [], tools()); assert.equal((r.reasoning as JsonObject).effort, 'max'); assert.equal(r.stream, true); } });
test('codexRejectsUnsupportedOverridesAndLeavesApiKeyBehaviorIntact', () => {
  for (const field of ['temperature', 'top_p', 'max_output_tokens', 'max_tokens', 'background', 'conversation']) assert.throws(() => buildResponsesRequest({ ...config(), customBody: [{ key: field, value: 1 }] }, [], tools()), e => e instanceof Error && e.message.includes(field));
  assert.equal(buildResponsesRequest({ ...config(), authMode: 'api_key', extraBodyJson: '{"temperature":0.2}' }, [], tools()).temperature, 0.2);
});
test('doneEventPreservesFunctionCallsAndOpaqueReasoningForTheNextTurn', () => {
  const output = [{ type: 'reasoning', id: 'rs_a', encrypted_content: 'opaque-fixture', summary: [] }, { type: 'function_call', id: 'fc_a', call_id: 'call_a', name: 'read_file', arguments: '{}', status: 'completed' }];
  const assistant = done({ status: 'completed', output }); assert.equal(assistant.finish_reason, 'tool_calls'); assert.equal(((assistant.tool_calls as JsonObject[])[0].function as JsonObject).name, 'read_file');
  assert.equal((buildResponsesRequest(config(), [assistant], tools()).input as JsonObject[])[0].encrypted_content, 'opaque-fixture');
});
test('doneEventDoesNotTurnFailedOrIncompleteResponsesIntoSuccess', () => {
  assert.throws(() => done({ status: 'failed', error: { code: 'server_error', message: 'fixture' } }), AgentModelFailure);
  assert.equal(done({ status: 'incomplete', incomplete_details: { reason: 'max_output_tokens' }, output: [] }).finish_reason, 'length');
  assert.throws(() => done({ output: [] }), AgentModelFailure);
});
