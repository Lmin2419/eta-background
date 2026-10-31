// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/OpenAiResponsesProvider.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { JsonObject, ProviderEventSink, ProviderRunControl, AssistantBlockKind, ProviderUsage, ProviderRequest, effectiveRequest, array, object, string, integer } from './providerTypes';
import { ProviderSseReader } from './providerSseReader';
import { attachOutputItems, buildResponsesRequest } from './responsesRequestBuilder';
import { mergeProviderHeaders } from './providerRequestHeaders';
import { ProviderUrls } from './providerUrls';
import { CitationAnnotation, formatCitations } from './citationFormatter';
import { AgentModelFailure } from './agentModelFailure';
export interface ProviderStreamingTransport {
  stream(url: string, headers: Record<string, string>, body: string, onChunk: (chunk: string) => boolean | void, onStatus?: (status: number) => void): Promise<void>;
}
export async function completeOpenAiResponses(request: ProviderRequest, control: ProviderRunControl, transport: ProviderStreamingTransport, emit: ProviderEventSink = () => {}): Promise<JsonObject> {
  const effective = effectiveRequest(request), config = effective.config;
  if (config.openAiEndpointMode !== 'responses') throw Error('当前 Provider 未配置为 Responses API');
  if (config.authMode && config.authMode !== 'api_key') throw Error('鸿蒙版尚未接入订阅账号登录');
  if (/[\r\n\u0000]/.test(config.apiKey)) throw Error('API Key 包含无效字符');
  const body = JSON.stringify(buildResponsesRequest(config, effective.messages, effective.tools));
  const headers: Record<string, string> = { 'Content-Type': 'application/json; charset=utf-8', Accept: 'text/event-stream' };
  if (config.apiKey.trim()) headers.Authorization = 'Bearer ' + config.apiKey;
  mergeProviderHeaders(headers, config.baseUrl, config.customHeaders || [], request.sessionId, config.authMode);
  const parser = new OpenAiResponsesStream(control, emit); control.check(); emit({ type: 'RequestStarted' });
  await transport.stream(ProviderUrls.openAiResponsesUrl(config.baseUrl), headers, body, chunk => parser.feed(chunk), httpCode => { emit({ type: 'ResponseHeaders', httpCode }); });
  control.check(); const result = parser.finish(); emit({ type: 'Completed', reason: string(result.finish_reason) || null }); return result;
}
interface Identity { family: string; itemId: string; outputIndex: number; partIndex: number; }
interface Block { kind: AssistantBlockKind; index: number; identity: Identity; content: string; ended: boolean; }
interface FunctionCall { itemId: string; callId: string; name: string; arguments: string; }
interface StreamCall extends FunctionCall { index: number; ended: boolean; }
interface ContentPart { kind: AssistantBlockKind; identity: Identity; rawContent: string; content: string; }
interface FinalOutput { text: string; reasoning: string; toolCalls: FunctionCall[]; contentParts: ContentPart[]; }
const hostedNames: Record<string, string> = { web_search_call: '网页搜索', file_search_call: '文件搜索', code_interpreter_call: '代码执行', computer_call: '计算机操作', image_generation_call: '图像生成', mcp_call: 'MCP 工具' };
function matches(a: Identity, b: Identity): boolean {
  if (a.family !== b.family || a.partIndex !== b.partIndex) return false;
  if (a.itemId.trim() && b.itemId.trim()) return a.itemId === b.itemId;
  if (a.outputIndex >= 0 && b.outputIndex >= 0) return a.outputIndex === b.outputIndex; return true;
}
function matchesItem(a: Identity, item: string, index: number): boolean { return a.itemId.trim() && item.trim() ? a.itemId === item : a.outputIndex >= 0 && index >= 0 ? a.outputIndex === index : true; }
function identity(event: JsonObject, family: string): Identity { return { family, itemId: string(event.item_id), outputIndex: integer(event.output_index), partIndex: family === 'reasoning_summary' ? integer(event.summary_index, integer(event.content_index, 0)) : integer(event.content_index, 0) }; }
function nonblank(value: unknown, fallback: string): string { const text = string(value); return text.trim() ? text : fallback; }
function hostedId(e: JsonObject): string { return nonblank(e.item_id, nonblank(e.id, nonblank(object(e.item)?.id, 'web_search_' + integer(e.output_index, 0)))); }
function separated(text: string, value: string): string { return !value.trim() ? text : text + (text && !text.endsWith('\n') ? '\n' : '') + value; }
function numberField(object: JsonObject | null, ...keys: string[]): number | null {
  for (const key of keys) { const v = object?.[key]; if (typeof v === 'number' && Number.isFinite(v)) return Math.trunc(v); if (typeof v === 'string' && /^-?\d+$/.test(v)) return Number(v); } return null;
}
function parseUsage(u: JsonObject | null): ProviderUsage | null {
  if (!u) return null;
  const result = { contextTokens: numberField(u, 'total_tokens'), inputTokens: numberField(u, 'input_tokens', 'prompt_tokens'), outputTokens: numberField(u, 'output_tokens', 'completion_tokens'),
    cachedTokens: numberField(object(u.input_tokens_details), 'cached_tokens') ?? numberField(object(u.prompt_tokens_details), 'cached_tokens'),
    reasoningTokens: numberField(object(u.output_tokens_details), 'reasoning_tokens') ?? numberField(object(u.completion_tokens_details), 'reasoning_tokens') };
  return Object.values(result).some(v => v !== null) ? result : null;
}
function finalOutput(output: unknown[]): FinalOutput {
  let text = '', reasoning = ''; const annotations: CitationAnnotation[] = [], calls: FunctionCall[] = [], parts: ContentPart[] = [];
  output.forEach((value, index) => {
    const item = object(value); if (!item) return; const itemId = string(item.id);
    if (item.type === 'message') array(item.content).forEach((value, contentIndex) => {
      const part = object(value); if (!part || part.type !== 'output_text') return; const partText = string(part.text), offset = text.length; text += partText;
      const local: CitationAnnotation[] = [];
      for (const value of array(part.annotations)) {
        const annotation = object(value); if (!annotation) continue; const citation = object(annotation.url_citation) || annotation;
        if (citation.type !== 'url_citation' && !Object.prototype.hasOwnProperty.call(citation, 'url')) continue;
        const start = integer(citation.start_index), end = integer(citation.end_index);
        const c = { start: start >= 0 ? start : null, end: end >= 0 ? end : null, url: string(citation.url), title: string(citation.title) };
        local.push(c); annotations.push({ ...c, start: c.start === null ? null : c.start + offset, end: c.end === null ? null : c.end + offset });
      }
      parts.push({ kind: 'TEXT', identity: { family: 'output_text', itemId, outputIndex: index, partIndex: contentIndex }, rawContent: partText, content: formatCitations(partText, local) });
    });
    else if (item.type === 'reasoning') {
      array(item.summary).forEach((value, summaryIndex) => { const part = object(value); if (!part) return; const content = string(part.text); reasoning = separated(reasoning, content);
        parts.push({ kind: 'THINKING', identity: { family: 'reasoning_summary', itemId, outputIndex: index, partIndex: summaryIndex }, rawContent: content, content }); });
      const native = array(item.content).flatMap((value, partIndex) => { const part = object(value); return part?.type === 'reasoning_text' ? [{ partIndex, content: string(part.text) }] : []; });
      if (!native.length && string(item.reasoning_text)) native.push({ partIndex: 0, content: string(item.reasoning_text) });
      for (const p of native) { reasoning = separated(reasoning, p.content); parts.push({ kind: 'THINKING', identity: { family: 'reasoning', itemId, outputIndex: index, partIndex: p.partIndex }, rawContent: p.content, content: p.content }); }
    } else if (item.type === 'function_call') calls.push({ itemId: nonblank(item.id, 'item_' + index), callId: nonblank(item.call_id, 'tool_call_' + index), name: nonblank(item.name, 'unknown_tool'), arguments: nonblank(item.arguments, '{}') });
  });
  return { text: formatCitations(text, annotations), reasoning, toolCalls: calls, contentParts: parts };
}
/** Incremental parser returns false at the first legal terminal event, allowing transport closure. */
export class OpenAiResponsesStream {
  private reader: ProviderSseReader; private streamedText = ''; private streamedReasoning = '';
  private calls = new Map<string, StreamCall>(); private hosted = new Map<string, boolean>(); private blocks: Block[] = [];
  private active: Block | null = null; private nextIndex = 0; private terminal: JsonObject | null = null; private terminalType = ''; private sawEvent = false; private result: JsonObject | null = null;
  constructor(private control: ProviderRunControl, private emit: ProviderEventSink = () => {}) { this.reader = new ProviderSseReader(control, (name, data) => this.event(name, data)); }
  feed(chunk: string): boolean { return this.reader.feed(chunk); }
  private end(block: Block, content = block.content, force = false): void {
    if (block.ended && !force) return;
    this.emit({ type: 'BlockEnd', kind: block.kind, index: block.index, blockId: block.identity.itemId.trim() ? block.identity.itemId : null, name: null, content, replaceContent: force && content !== block.content });
    block.ended = true; if (this.active === block) this.active = null;
  }
  private endActive(): void { if (this.active) this.end(this.active); }
  private delta(e: JsonObject, kind: AssistantBlockKind, family: string, delta: string): void {
    if (!delta) return; const id = identity(e, family); let block = this.active;
    if (!block || block.kind !== kind || !matches(block.identity, id)) {
      this.endActive(); block = { kind, index: this.nextIndex++, identity: id, content: '', ended: false }; this.blocks.push(block); this.active = block;
      this.emit({ type: 'BlockStart', kind, index: block.index, blockId: id.itemId.trim() ? id.itemId : null, name: null });
    }
    block.content += delta; this.emit({ type: 'BlockDelta', kind, index: block.index, delta });
  }
  private done(e: JsonObject, family: string): void {
    const id = identity(e, family), block = this.active && matches(this.active.identity, id) ? this.active : [...this.blocks].reverse().find(b => !b.ended && matches(b.identity, id)); if (!block) return;
    const authoritative = string(e.text);
    if (authoritative && authoritative.startsWith(block.content)) { const suffix = authoritative.slice(block.content.length); if (suffix) { block.content += suffix; this.emit({ type: 'BlockDelta', kind: block.kind, index: block.index, delta: suffix }); } }
    this.end(block);
  }
  private startHosted(id: string, name: string): void { this.endActive(); if (!this.hosted.has(id)) { this.hosted.set(id, false); this.emit({ type: 'HostedToolStarted', id, name }); } }
  private endHosted(id: string, name: string, success: boolean): void { this.startHosted(id, name); if (this.hosted.get(id) !== true) { this.hosted.set(id, true); this.emit({ type: 'HostedToolFinished', id, name, success }); } }
  private startCall(c: StreamCall): void { this.emit({ type: 'BlockStart', kind: 'TOOL_CALL', index: c.index, blockId: c.callId.trim() ? c.callId : null, name: c.name.trim() ? c.name : null }); }
  private endCall(c: StreamCall): void { this.emit({ type: 'BlockEnd', kind: 'TOOL_CALL', index: c.index, blockId: c.callId.trim() ? c.callId : null, name: c.name.trim() ? c.name : null, content: c.arguments, replaceContent: false }); }
  private event(eventName: string, data: string): boolean {
    const payload = data.trim(); if (payload === '[DONE]') return true; this.sawEvent = true;
    let e: JsonObject | null; try { e = object(JSON.parse(payload)); } catch (_) { throw new AgentModelFailure('INVALID_STREAM', false, '模型 SSE 数据格式无效'); }
    if (!e) throw new AgentModelFailure('INVALID_STREAM', false, '模型 SSE 数据格式无效');
    const error = object(e.error); if (error) throw AgentModelFailure.stream(error);
    const type = nonblank(e.type, eventName);
    switch (type) {
      case 'response.output_text.delta': { const delta = string(e.delta); this.streamedText += delta; this.delta(e, 'TEXT', 'output_text', delta); break; }
      case 'response.reasoning_summary_text.delta': case 'response.reasoning_text.delta': { const delta = string(e.delta); this.streamedReasoning += delta; this.delta(e, 'THINKING', type === 'response.reasoning_text.delta' ? 'reasoning' : 'reasoning_summary', delta); break; }
      case 'response.output_text.done': this.done(e, 'output_text'); break;
      case 'response.reasoning_summary_text.done': this.done(e, 'reasoning_summary'); break;
      case 'response.reasoning_text.done': this.done(e, 'reasoning'); break;
      case 'response.output_item.added': {
        const item = object(e.item) || {}, itemId = nonblank(item.id, 'item_' + integer(e.output_index, 0)), itemType = string(item.type);
        if (itemType === 'function_call') { this.endActive(); const call = { itemId, index: this.nextIndex++, callId: string(item.call_id), name: string(item.name), arguments: string(item.arguments), ended: false }; this.calls.set(itemId, call); this.startCall(call); }
        else if (hostedNames[itemType]) this.startHosted(itemId, hostedNames[itemType]); break;
      }
      case 'response.function_call_arguments.delta': {
        const call = this.calls.get(string(e.item_id)); if (!call) break; const delta = string(e.delta); call.arguments += delta;
        if (delta) this.emit({ type: 'BlockDelta', kind: 'TOOL_CALL', index: call.index, delta }); break;
      }
      case 'response.function_call_arguments.done': { const call = this.calls.get(string(e.item_id)); if (call && Object.prototype.hasOwnProperty.call(e, 'arguments')) call.arguments = string(e.arguments); break; }
      case 'response.output_item.done': {
        const item = object(e.item) || {}, id = nonblank(item.id, 'item_' + integer(e.output_index, 0)), itemType = string(item.type);
        this.blocks.filter(b => !b.ended && matchesItem(b.identity, id, integer(e.output_index))).forEach(b => this.end(b));
        if (itemType === 'function_call') { const call = this.calls.get(id); if (call) { call.callId = nonblank(item.call_id, call.callId); call.name = nonblank(item.name, call.name); if (Object.prototype.hasOwnProperty.call(item, 'arguments')) call.arguments = string(item.arguments); if (!call.ended) { call.ended = true; this.endCall(call); } } }
        else if (hostedNames[itemType]) this.endHosted(id, hostedNames[itemType], item.status !== 'failed'); break;
      }
      case 'response.web_search_call.in_progress': case 'response.web_search_call.searching': this.startHosted(hostedId(e), '网页搜索'); break;
      case 'response.web_search_call.completed': this.endHosted(hostedId(e), '网页搜索', true); break;
      case 'response.web_search_call.failed': this.endHosted(hostedId(e), '网页搜索', false); break;
      case 'response.completed': case 'response.done': case 'response.incomplete': case 'response.failed':
        this.terminal = object(e.response) || e;
        if (type === 'response.done') { if (!['completed', 'failed', 'incomplete'].includes(string(this.terminal.status))) throw AgentModelFailure.incompleteStream('Codex 终止事件缺少有效状态'); this.terminalType = 'response.' + string(this.terminal.status); }
        else this.terminalType = type; break;
      case 'error': throw AgentModelFailure.stream(e);
    }
    return this.terminal === null;
  }
  private reconcile(part: ContentPart): void {
    const matchesParts = this.blocks.filter(b => b.kind === part.kind && matches(b.identity, part.identity));
    if (matchesParts.length === 1) {
      const b = matchesParts[0]; if (b.ended && part.content === b.content) return;
      if (!b.ended && part.rawContent.startsWith(b.content)) { const suffix = part.rawContent.slice(b.content.length); if (suffix) { b.content += suffix; this.emit({ type: 'BlockDelta', kind: b.kind, index: b.index, delta: suffix }); } }
      this.end(b, part.content, true); return;
    }
    if (matchesParts.length) { matchesParts.filter(b => !b.ended).forEach(b => this.end(b)); return; }
    this.endActive(); const b = { kind: part.kind, index: this.nextIndex++, identity: part.identity, content: '', ended: false }; this.blocks.push(b);
    this.emit({ type: 'BlockStart', kind: b.kind, index: b.index, blockId: part.identity.itemId.trim() ? part.identity.itemId : null, name: null });
    if (part.content) { b.content += part.content; this.emit({ type: 'BlockDelta', kind: b.kind, index: b.index, delta: part.content }); } this.end(b, part.content);
  }
  finish(): JsonObject {
    if (this.result) return this.result; this.control.check(); this.reader.finish();
    if (!this.sawEvent) throw AgentModelFailure.incompleteStream('模型接口未返回 SSE data chunk');
    if (!this.terminal) throw AgentModelFailure.incompleteStream('模型接口 Responses SSE 流缺少合法终止事件');
    if (this.terminalType === 'response.failed') throw AgentModelFailure.stream(object(this.terminal.error) || {});
    const output = array(this.terminal.output), hasOutput = output.length > 0;
    const final: FinalOutput = this.terminalType === 'response.completed' && !hasOutput ? { text: this.streamedText, reasoning: this.streamedReasoning, toolCalls: [...this.calls.values()].map(c => ({ ...c, arguments: nonblank(c.arguments, '{}') })), contentParts: [] } : finalOutput(output);
    final.contentParts.forEach(p => this.reconcile(p)); this.endActive(); this.blocks.filter(b => !b.ended).forEach(b => this.end(b));
    for (const call of this.calls.values()) if (!call.ended) this.endCall(call);
    for (const call of final.toolCalls) if (![...this.calls.values()].some(c => c.callId === call.callId)) { const c = { ...call, index: this.nextIndex++, ended: true }; this.startCall(c); this.endCall(c); }
    for (const [id, ended] of this.hosted) if (!ended) this.emit({ type: 'HostedToolFinished', id, name: '网页搜索', success: true });
    const usage = parseUsage(object(this.terminal.usage)); if (usage) this.emit({ type: 'Usage', usage });
    const reason = string(object(this.terminal.incomplete_details)?.reason).toLowerCase();
    const finishReason = this.terminalType === 'response.completed' ? final.toolCalls.length ? 'tool_calls' : 'stop' : /max|length/.test(reason) ? 'length' : /filter|content/.test(reason) ? 'content_filter' : 'incomplete';
    this.result = { role: 'assistant', content: final.text, reasoning_content: final.reasoning, finish_reason: finishReason };
    if (final.toolCalls.length) this.result.tool_calls = final.toolCalls.map(c => ({ id: c.callId, type: 'function', function: { name: c.name, arguments: c.arguments } }));
    if (hasOutput) attachOutputItems(this.result, output); return this.result;
  }
}
