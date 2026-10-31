// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/ResponsesRequestBuilder.kt,
// ResponsesEphemeralState.kt, CodexResponsesRequest.kt and OpenAiRequestMessages.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { JsonObject, ProviderModelConfig, array, object, string, copy } from './providerTypes';
import { providerMessageText } from './providerMessageContent';
import { mergeExtraBody, mergeCustomBody } from './requestBodyMerge';
import { applyResponsesReasoning } from './providerReasoning';
export const RESPONSES_OUTPUT_ITEMS = '_eta_responses_output_items';
export function outputItems(message: JsonObject): unknown[] | null { return Array.isArray(message[RESPONSES_OUTPUT_ITEMS]) ? message[RESPONSES_OUTPUT_ITEMS] as unknown[] : null; }
export function attachOutputItems(message: JsonObject, items: unknown[]): void { message[RESPONSES_OUTPUT_ITEMS] = copy(items); }
export function collectInstructions(source: JsonObject[], roles: string[]): string { return source.filter(m => roles.includes(string(m.role))).map(m => providerMessageText(m.content).trim()).filter(Boolean).join('\n\n'); }
function userContent(raw: unknown): unknown {
  if (typeof raw === 'string') return raw; const content: JsonObject[] = [];
  for (const value of array(raw)) {
    const part = object(value); if (!part) continue;
    if (['text', 'input_text'].includes(string(part.type))) content.push({ type: 'input_text', text: string(part.text) });
    else if (['image_url', 'input_image'].includes(string(part.type))) { const url = string(object(part.image_url)?.url ?? part.image_url); if (url.trim()) content.push({ type: 'input_image', image_url: url }); }
  }
  return content;
}
function inputMessages(messages: JsonObject[]): unknown[] {
  const input: unknown[] = [];
  for (const m of messages) {
    const items = outputItems(m); if (items) { input.push(...copy(items)); continue; }
    switch (m.role) {
      case 'tool': input.push({ type: 'function_call_output', call_id: string(m.tool_call_id), output: string(m.content) }); break;
      case 'assistant': {
        const content = string(m.content); if (content.trim() && content !== 'null') input.push({ type: 'message', role: 'assistant', content });
        array(m.tool_calls).forEach((value, index) => { const call = object(value), fn = object(call?.function); if (!call || !fn) return;
          input.push({ type: 'function_call', call_id: string(call.id).trim() ? string(call.id) : 'tool_call_' + index, name: string(fn.name), arguments: string(fn.arguments).trim() ? string(fn.arguments) : '{}' }); }); break;
      }
      case 'system': case 'developer': break; default: input.push({ type: 'message', role: 'user', content: userContent(m.content) });
    }
  }
  return input;
}
export function buildResponsesRequest(config: ProviderModelConfig, messages: JsonObject[], tools: JsonObject[]): JsonObject {
  const result: JsonObject = {}; mergeExtraBody(result, config.extraBodyJson); mergeCustomBody(result, config.customBody || []);
  const hosted = config.hostedWebSearchEnabled === true, responseTools: JsonObject[] = [];
  for (const tool of tools) { const fn = object(tool.function); if (!fn || hosted && fn.name === 'web_search') continue;
    responseTools.push({ type: 'function', name: string(fn.name), description: string(fn.description), parameters: copy(fn.parameters ?? {}), strict: false }); }
  if (hosted) responseTools.push({ type: 'web_search' });
  Object.assign(result, { model: config.model, instructions: collectInstructions(messages, ['system', 'developer']) || config.systemPrompt, input: inputMessages(messages), stream: true, store: false });
  if (responseTools.length) { result.tools = responseTools; result.tool_choice = 'auto'; } else { delete result.tools; delete result.tool_choice; }
  delete result.previous_response_id; delete result.reasoning; applyResponsesReasoning(result, config);
  // Pure request compatibility is ported/tested; subscription login and authenticated transport remain C.
  if (config.authMode === 'openai_chatgpt') {
    const unsupported = ['max_output_tokens', 'max_tokens', 'temperature', 'top_p', 'background', 'conversation'].filter(k => Object.prototype.hasOwnProperty.call(result, k));
    if (unsupported.length) throw Error('Codex 不支持请求参数：' + unsupported.join(', '));
    const include = array(result.include); if (!include.includes('reasoning.encrypted_content')) include.push('reasoning.encrypted_content'); result.include = include;
    if (!Object.prototype.hasOwnProperty.call(result, 'parallel_tool_calls')) result.parallel_tool_calls = true;
  }
  return result;
}
