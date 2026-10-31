// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/OpenAiChatCompletionsProvider.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { JsonObject, ProviderRequest, ProviderModelConfig, ProviderEventSink, ProviderRunControl, ProviderUsage, AssistantBlockKind, object, array, string, integer, effectiveRequest } from './providerTypes';
import { ProviderSseReader } from './providerSseReader';
import { ProviderStreamingTransport } from './openAiResponsesProvider';
import { forChatCompletions } from './openAiRequestMessages';
import { referencedIds } from './chatToolCallHistory';
import { applyOpenAiCompatibleReasoning } from './providerReasoning';
import { mergeExtraBody, mergeCustomBody } from './requestBodyMerge';
import { mergeProviderHeaders } from './providerRequestHeaders';
import { ProviderUrls } from './providerUrls';
import { resolveProviderSource } from '../providerSourceRegistry';
import { AgentModelFailure } from './agentModelFailure';
export function buildChatRequest(config: ProviderModelConfig, messages: JsonObject[], tools: JsonObject[]): JsonObject {
  const body: JsonObject = { model: config.model, stream: true, messages: forChatCompletions(messages), tools, tool_choice: 'auto' };
  if ((config.providerSourceType || resolveProviderSource(config.providerId || '', config.baseUrl, config.providerType)) !== 'openrouter') body.stream_options = { include_usage: true };
  mergeExtraBody(body, config.extraBodyJson); mergeCustomBody(body, config.customBody || []); applyOpenAiCompatibleReasoning(body, config);
  if (config.authMode === 'xai_grok') { body.model = config.model; body.stream = true; body.messages = forChatCompletions(messages);
    if (tools.length) { body.tools = tools; body.tool_choice = 'auto'; } else { delete body.tools; delete body.tool_choice; } }
  return body;
}
export function chatReasoning(delta: JsonObject): string {
  if (typeof delta.reasoning_content === 'string' && delta.reasoning_content) return delta.reasoning_content;
  if (typeof delta.reasoning === 'string' && delta.reasoning) return delta.reasoning;
  return array(delta.reasoning_details).map(object).map(d => d?.type === 'reasoning.text' && typeof d.text === 'string' ? d.text : d?.type === 'reasoning.summary' && typeof d.summary === 'string' ? d.summary : '').join('');
}
export function chatUsage(raw: JsonObject): ProviderUsage {
  const first = (...values: unknown[]): number | null => { for (const v of values) { const n = integer(v); if (n >= 0) return n; } return null; };
  return { contextTokens: first(raw.total_tokens), inputTokens: first(raw.prompt_tokens, raw.input_tokens), outputTokens: first(raw.completion_tokens, raw.output_tokens),
    reasoningTokens: first(object(raw.completion_tokens_details)?.reasoning_tokens, object(raw.output_tokens_details)?.reasoning_tokens),
    cachedTokens: first(object(raw.prompt_tokens_details)?.cached_tokens, raw.cache_read_input_tokens, raw.prompt_cache_hit_tokens, raw.cached_tokens) };
}
interface StreamCall { id: string | null; type: string; name: string; args: string; contentIndex: number; }
let responseSequence = 0;
/** Random per-response namespace plus local counter; IDs are opaque, not authentication material. */
function callPrefix(): string { return 'call_eta_' + Date.now().toString(36) + (++responseSequence).toString(36) + Math.random().toString(36).slice(2) + '_'; }
export class OpenAiChatCompletionsStream {
  private reader: ProviderSseReader;
  private calls = new Map<number, StreamCall>(); private nextIndex = 0;
  private active: { kind: AssistantBlockKind; index: number; content: string } | null = null;
  private content = ''; private reasoning = ''; private latestUsage: ProviderUsage | null = null;
  private sawData = false; private sawDone = false; private finishReason: string | null = null; private result: JsonObject | null = null;
  constructor(private control: ProviderRunControl, private emit: ProviderEventSink, private historyIds = new Set<string>()) {
    this.reader = new ProviderSseReader(control, (_event, data) => this.frame(data));
  }
  feed(chunk: string): boolean { return this.reader.feed(chunk); }
  private endVisible(): void { const b = this.active; if (!b) return; this.emit({ type: 'BlockEnd', kind: b.kind, index: b.index, blockId: null, name: null, content: b.content, replaceContent: false }); this.active = null; }
  private visible(kind: AssistantBlockKind, delta: string): void {
    if (!delta) return; if (this.active?.kind !== kind) { this.endVisible(); this.active = { kind, index: this.nextIndex++, content: '' }; this.emit({ type: 'BlockStart', kind, index: this.active.index, blockId: null, name: null }); }
    this.active!.content += delta; this.emit({ type: 'BlockDelta', kind, index: this.active!.index, delta });
  }
  private frame(data: string): boolean {
    this.control.check(); if (data === '[DONE]') { this.sawDone = true; return false; }
    let chunk: JsonObject | null; try { chunk = object(JSON.parse(data)); } catch (_) { throw Error('模型流返回无效 JSON'); }
    if (!chunk) throw Error('模型流返回无效 JSON'); this.sawData = true;
    const error = object(chunk.error); if (error) throw AgentModelFailure.stream(error);
    const usage = object(chunk.usage); if (usage) { const parsed = chatUsage(usage); if (Object.values(parsed).some(v => v !== null)) { this.latestUsage = parsed; this.emit({ type: 'Usage', usage: parsed }); } }
    const choice = object(array(chunk.choices)[0]); if (!choice) return true;
    const reason = string(choice.finish_reason).trim(); if (reason) this.finishReason = reason;
    if (reason === 'error') throw AgentModelFailure.stream({});
    const delta = object(choice.delta);
    if (delta) {
      const thinking = chatReasoning(delta); this.reasoning += thinking; this.visible('THINKING', thinking);
      const text = string(delta.content); this.content += text; this.visible('TEXT', text);
      const toolCalls = array(delta.tool_calls); if (toolCalls.length) this.endVisible();
      toolCalls.forEach((raw, position) => { const item = object(raw); if (!item) return; const index = integer(item.index, position);
        let call = this.calls.get(index); if (!call) { call = { id: null, type: 'function', name: '', args: '', contentIndex: this.nextIndex++ }; this.calls.set(index, call); this.emit({ type: 'BlockStart', kind: 'TOOL_CALL', index: call.contentIndex, blockId: null, name: null }); }
        const id = string(item.id); if (id.trim()) call.id = id; const type = string(item.type); if (type.trim()) call.type = type;
        const fn = object(item.function); if (fn) { call.name += string(fn.name); const args = string(fn.arguments); call.args += args; if (args) this.emit({ type: 'BlockDelta', kind: 'TOOL_CALL', index: call.contentIndex, delta: args }); }
      });
    }
    if (this.finishReason !== null) this.endVisible(); return true;
  }
  finish(): JsonObject {
    if (this.result) return this.result; this.reader.finish(); this.control.check();
    if (!this.sawData || (!this.sawDone && this.finishReason === null)) throw AgentModelFailure.incompleteStream('模型流中断，未正常结束');
    this.endVisible(); const calls = [...this.calls.entries()].sort((a, b) => a[0] - b[0]).map(pair => pair[1]);
    const reserved = new Set(this.historyIds), assigned = new Set(this.historyIds); for (const call of calls) if (call.id) reserved.add(call.id);
    const prefix = callPrefix(); let next = 0;
    for (const call of calls) { if (!call.id || assigned.has(call.id)) { do { call.id = prefix + next++; } while (reserved.has(call.id)); reserved.add(call.id); } assigned.add(call.id); }
    for (const call of [...calls].sort((a, b) => a.contentIndex - b.contentIndex)) this.emit({ type: 'BlockEnd', kind: 'TOOL_CALL', index: call.contentIndex, blockId: call.id, name: call.name.trim() ? call.name : null, content: call.args, replaceContent: false });
    this.result = { role: 'assistant', content: this.content, reasoning_content: this.reasoning, finish_reason: this.finishReason || '',
      ...(this.latestUsage ? { usage: Object.fromEntries([['total_tokens', this.latestUsage.contextTokens], ['input_tokens', this.latestUsage.inputTokens], ['output_tokens', this.latestUsage.outputTokens], ['reasoning_tokens', this.latestUsage.reasoningTokens], ['cached_tokens', this.latestUsage.cachedTokens]].filter(pair => pair[1] !== null)) } : {}), ...(calls.length ? { tool_calls: calls.map(c => ({ id: c.id, type: c.type, function: { name: c.name.trim(), arguments: c.args } })) } : {}) };
    return this.result;
  }
}
export function prepareChatRequest(original: ProviderRequest): { body: JsonObject; headers: Record<string, string>; historyIds: Set<string>; request: ProviderRequest } {
  const request = effectiveRequest(original), { config } = request;
  if (config.openAiEndpointMode && config.openAiEndpointMode !== 'chat_completions') throw Error('当前模型不是 Chat Completions 协议');
  if (config.authMode && config.authMode !== 'api_key') throw Error('鸿蒙版尚未接入订阅账号登录');
  if (/[\r\n\u0000]/.test(config.apiKey)) throw Error('API Key 包含无效字符');
  const headers: Record<string, string> = { 'Content-Type': 'application/json; charset=utf-8', Accept: 'text/event-stream' };
  if (config.apiKey.trim()) headers.Authorization = 'Bearer ' + config.apiKey.trim();
  mergeProviderHeaders(headers, config.baseUrl, config.customHeaders || [], request.sessionId, config.authMode, config.model);
  const body = buildChatRequest(config, request.messages, request.tools); if (request.purpose && request.purpose !== 'chat') { delete body.tools; delete body.tool_choice; }
  return { request, body, headers, historyIds: new Set([...referencedIds(original.messages), ...referencedIds(array(body.messages))]) };
}
export async function completeOpenAiChatCompletions(original: ProviderRequest, control: ProviderRunControl, transport: ProviderStreamingTransport, emit: ProviderEventSink = () => {}): Promise<JsonObject> {
  const { request, body, headers, historyIds } = prepareChatRequest(original); control.check();
  const parser = new OpenAiChatCompletionsStream(control, emit, historyIds); emit({ type: 'RequestStarted' });
  await transport.stream(ProviderUrls.openAiChatCompletionsUrl(request.config.baseUrl), headers, JSON.stringify(body), chunk => { control.check(); return parser.feed(chunk); }, httpCode => emit({ type: 'ResponseHeaders', httpCode }));
  control.check(); const result = parser.finish(); emit({ type: 'Completed', reason: string(result.finish_reason) || null }); return result;
}
