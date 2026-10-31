// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/model/ProviderReasoningTest.kt @ 3b1794d (23).
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { JsonObject, ProviderModelConfig, ReasoningEffort, ModelReasoningCapabilities, REASONING_EFFORTS } from '../../entry/src/main/ets/core/eta/protocol/providerTypes';
import { applyResponsesReasoning as responses, applyOpenAiCompatibleReasoning as chat, applyAnthropicReasoning as anthropic } from '../../entry/src/main/ets/core/eta/protocol/providerReasoning';
function config(source: string, effort: ReasoningEffort, model = 'test-model'): ProviderModelConfig { return { providerSourceType: source, baseUrl: 'https://example.com/v1', apiKey: 'key', model, systemPrompt: 'system', thinkingEnabled: effort !== 'off', reasoningEffort: effort, reasoningCapabilities: { supportedEfforts: REASONING_EFFORTS.filter(e => e !== 'off' && e !== 'default'), canDisable: true } }; }
test('xaiUsesAdvertisedEffortAndLeavesDefaultToTheServer', () => {
  const capabilities: ModelReasoningCapabilities = { supportedEfforts: ['low', 'high'], mandatory: true }, selected = { ...config('xai', 'high'), reasoningCapabilities: capabilities };
  const r: JsonObject = {}; chat(r, selected); assert.equal(r.reasoning_effort, 'high'); const d: JsonObject = {}; chat(d, { ...selected, reasoningEffort: 'default' }); assert.equal(d.reasoning_effort, undefined);
  for (const effort of ['off', 'xhigh'] as ReasoningEffort[]) assert.throws(() => chat({}, { ...selected, reasoningEffort: effort }));
  const disabled: JsonObject = {}; chat(disabled, { ...selected, reasoningEffort: 'off', reasoningCapabilities: { ...capabilities, mandatory: false, canDisable: true } }); assert.equal(disabled.reasoning_effort, 'none');
});
test('mandatoryModelRejectsOffInsteadOfSilentlyDisablingReasoning', () => { assert.throws(() => chat({}, { ...config('moonshot', 'off', 'kimi-k3'), reasoningCapabilities: { supportedEfforts: ['high'], mandatory: true } })); });
test('defaultDoesNotOverrideAdvancedRequestBody', () => { const r = { reasoning_effort: 'medium', thinking_budget: 1234 }; chat(r, config('openai', 'default')); assert.equal(r.reasoning_effort, 'medium'); assert.equal(r.thinking_budget, 1234); });
test('openAiNamedEffortOverridesAdvancedValue', () => { const r = { reasoning_effort: 'low' }; chat(r, config('openai', 'xhigh')); assert.equal(r.reasoning_effort, 'xhigh'); });
test('openAiUsesConfiguredCapabilitiesInsteadOfOverridingThemByModelName', () => {
  for (const authMode of ['api_key', 'openai_chatgpt']) for (const model of ['gpt-6.1-sol', 'remote-model-alias', 'gpt-5.5']) { const r: JsonObject = {}; responses(r, { ...config('openai', 'max', model), authMode, reasoningCapabilities: { supportedEfforts: ['max'] } }); assert.deepEqual(r.reasoning, { summary: 'auto', effort: 'max' }); }
});
test('configuredEffortConstraintsDoNotDependOnModelName', () => { for (const model of ['gpt-6.1-sol', 'remote-model-alias']) assert.throws(() => responses({}, { ...config('openai', 'max', model), reasoningCapabilities: { supportedEfforts: ['low', 'high'] } })); });
test('configuredMaxIsEncodedByResponsesAndChatCompletions', () => {
  for (const model of ['gpt-6.1-sol', 'gpt-6-astra', 'gpt-6-luna', 'gpt-5.6-sol', 'remote-model-alias']) { const r: JsonObject = {}, c: JsonObject = {}; responses(r, config('openai', 'max', model)); chat(c, config('openai', 'max', model)); assert.equal((r.reasoning as JsonObject).effort, 'max'); assert.equal(c.reasoning_effort, 'max'); }
});
test('openAiOffUsesDocumentedNoneValue', () => { const r: JsonObject = {}; chat(r, config('openai', 'off')); assert.equal(r.reasoning_effort, 'none'); });
test('responsesEncodesMaxAndOffWithoutModelNameRules', () => {
  const astra: JsonObject = {}; responses(astra, config('openai', 'max', 'gpt-6-astra')); assert.equal((astra.reasoning as JsonObject).effort, 'max');
  for (const model of ['gpt-6-sol', 'gpt-6-luna']) { const r: JsonObject = {}; responses(r, config('openai', 'off', model)); assert.equal((r.reasoning as JsonObject).effort, 'none'); }
});
test('anthropicMaxUsesAdaptiveThinkingAndLargeOutputLimit', () => { const r: JsonObject = { max_tokens: 4096, output_config: { other: true } }; anthropic(r, config('anthropic', 'max')); assert.deepEqual(r.thinking, { type: 'adaptive', display: 'summarized' }); assert.deepEqual(r.output_config, { other: true, effort: 'max' }); assert.equal(r.max_tokens, 65536); });
test('anthropicOffUsesDisabledThinkingAndRemovesOnlyEffortOverride', () => { const r: JsonObject = { thinking: { type: 'adaptive' }, output_config: { effort: 'high', other: true } }; anthropic(r, config('anthropic', 'off')); assert.deepEqual(r.thinking, { type: 'disabled' }); assert.deepEqual(r.output_config, { other: true }); });
test('anthropicRejectsUnsupportedMinimalEffort', () => { assert.throws(() => anthropic({}, config('anthropic', 'minimal'))); });
test('deepSeekWritesThinkingTypeAndExactEffort', () => { const r: JsonObject = {}; chat(r, config('deepseek', 'max')); assert.deepEqual(r.thinking, { type: 'enabled' }); assert.equal(r.reasoning_effort, 'max'); });
test('deepSeekFlashLowKeepsLowEffort', () => { const r: JsonObject = {}; chat(r, config('deepseek', 'low', 'deepseek-flash')); assert.deepEqual(r.thinking, { type: 'enabled' }); assert.equal(r.reasoning_effort, 'low'); });
test('kimiK3UsesTopLevelEffortWithoutThinkingObject', () => { const r: JsonObject = {}; chat(r, config('moonshot', 'high', 'kimi-k3')); assert.equal(r.reasoning_effort, 'high'); assert.equal(r.thinking, undefined); });
test('qwenBudgetReservesAnswerTokensBelowCompletionLimit', () => { const r: JsonObject = { max_completion_tokens: 20000 }; chat(r, config('bailian', 'high', 'qwen3.7-plus')); assert.equal(r.enable_thinking, true); assert.equal(r.thinking_budget, 17500); });
test('qwen38NamedEffortClearsIncompatibleBudgetOverride', () => { const r: JsonObject = { thinking_budget: 8192 }; chat(r, config('bailian', 'xhigh', 'qwen3.8-max')); assert.equal(r.reasoning_effort, 'xhigh'); assert.equal(r.thinking_budget, undefined); });
test('bailianNewModelsUseNamedEffort', () => { for (const [model, effort] of [['deepseek-v4.1-flash', 'low'], ['kimi-k3', 'high']] as [string, ReasoningEffort][]) { const r: JsonObject = {}; chat(r, config('bailian', effort, model)); assert.equal(r.reasoning_effort, effort); } });
test('siliconFlowMapsEveryNamedLevelToBudget', () => { for (const [effort, budget] of Object.entries({ minimal: 128, low: 1024, medium: 4096, high: 8192, xhigh: 16384, max: 32768 })) { const r: JsonObject = {}; chat(r, config('siliconflow', effort as ReasoningEffort)); assert.equal(r.thinking_budget, budget); } });
test('mimoOffUsesDocumentedDisabledThinkingType', () => { const r: JsonObject = {}; chat(r, config('mimo', 'off', 'mimo-v2.6-pro')); assert.deepEqual(r.thinking, { type: 'disabled' }); });
test('openRouterUsesNestedReasoningEffort', () => { const r: JsonObject = { reasoning: { exclude: true } }; chat(r, config('openrouter', 'low')); assert.deepEqual(r.reasoning, { exclude: true, effort: 'low' }); });
test('stepFunUsesOnlyVerifiedNamedLevels', () => {
  const r: JsonObject = {}; chat(r, config('stepfun', 'low', 'step-3.5-flash-2603')); assert.equal(r.reasoning_effort, 'low');
  for (const model of ['step-5-preview', 'step-3.7-flash']) { const m: JsonObject = {}; chat(m, config('stepfun', 'medium', model)); assert.equal(m.reasoning_effort, 'medium'); }
  assert.throws(() => chat({}, config('stepfun', 'medium', 'step-3.5-flash-2603')));
});
test('defaultOnlyProvidersDoNotInventRequestFields', () => { for (const source of ['minimax', 'custom']) { const r = { metadata: 'kept' }; chat(r, config(source, 'default')); assert.deepEqual(Object.keys(r), ['metadata']); } });
