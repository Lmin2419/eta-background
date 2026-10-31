// Harmony adapter for app/src/main/kotlin/io/github/mangi/eta/agent/model/AgentProvider.kt,
// OpenAiResponsesProvider.kt and ResponsesEphemeralState.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import type { HttpTransport, ModelSettings } from '../../model';
import { runTextContext } from '../context/textContextRunner';
import { ProviderEventSink, ProviderRequest } from './providerTypes';
import { Message, ModelReply, RunControl, ToolCall } from '../../types';
import { ProviderModelConfig, JsonObject, ProviderUsage, string, ReasoningEffort } from './providerTypes';
import { completeOpenAiChatCompletions } from './openAiChatCompletionsProvider';
import { compatibleChatTransport } from './chatTransportCompatibility';
import { completeOpenAiResponses } from './openAiResponsesProvider';
import { attachOutputItems, outputItems } from './responsesRequestBuilder';
import { parseEffort } from '../storage/conversationCodec';
export function etaModelConfig(settings: ModelSettings, baseUrl: string): ProviderModelConfig {
  return { baseUrl, apiKey: settings.apiKey, model: settings.model.trim(), systemPrompt: settings.systemPrompt || '',
    providerId: settings.providerId, providerSourceType: settings.providerSourceType, openAiEndpointMode: settings.protocol === 'responses' ? 'responses' : 'chat_completions',
    contextWindow: settings.contextWindow, autoCompactionEnabled: settings.autoCompactionEnabled, thinkingEnabled: settings.thinkingEnabled, reasoningEffort: settings.reasoningEffort ? parseEffort(settings.reasoningEffort) as ReasoningEffort : null,
    reasoningCapabilities: settings.reasoningCapabilities, customHeaders: Object.entries(settings.extraHeaders || {}).map(([name, value]) => ({ name, value })),
    extraBodyJson: settings.maxOutputTokens ? JSON.stringify({ [settings.protocol === 'responses' ? 'max_output_tokens' : 'max_tokens']: settings.maxOutputTokens }) : '' };
}
export function etaRequestMessages(messages: Message[]): JsonObject[] {
  return messages.map(m => { const row: JsonObject = { role: m.role, content: m.content, ...(m._eta_context_summary ? { _eta_context_summary: true, _eta_compacted_users: m._eta_compacted_users, _eta_summary_through_user: m._eta_summary_through_user } : {}), ...(m.tool_calls ? { tool_calls: m.tool_calls } : {}), ...(m.tool_call_id ? { tool_call_id: m.tool_call_id } : {}) };
    if (m.providerProtocol === 'responses' && m.providerParts) attachOutputItems(row, m.providerParts); return row; });
}
export async function completeFoundationProvider(settings: ModelSettings, baseUrl: string, transport: HttpTransport, tools: JsonObject[], messages: Message[], control: RunControl, onText?: (text: string) => void, onReasoning?: (text: string) => void): Promise<ModelReply> {
  const responses = settings.protocol === 'responses';
  if (responses && !transport.stream) throw Error('Responses 需要流式网络传输');
  const text = new Map<number, string>(), reasoning = new Map<number, string>(); let usage: ProviderUsage | undefined;
  let lastText = '', lastReasoning = '';
  const publish = (kind: string): void => { if (kind === 'TEXT') { const next = [...text.values()].join(''); if (next !== lastText) { lastText = next; onText?.(next); } } else if (kind === 'THINKING') { const next = [...reasoning.values()].join(''); if (next !== lastReasoning) { lastReasoning = next; onReasoning?.(next); } } };
  const complete = responses ? completeOpenAiResponses : completeOpenAiChatCompletions;
  const wire = responses ? { stream: (...args: Parameters<NonNullable<HttpTransport['stream']>>) => transport.stream!(...args) } : compatibleChatTransport(transport, settings.streaming, control);
  const request: ProviderRequest = { config: etaModelConfig(settings, baseUrl), messages: etaRequestMessages(messages), tools, sessionId: settings.sessionId || '' };
  const receive: ProviderEventSink = event => {
    control.check();
    if (event.type === 'Usage') usage = event.usage;
    if (event.type === 'BlockDelta' || event.type === 'BlockEnd') {
      const target = event.kind === 'TEXT' ? text : event.kind === 'THINKING' ? reasoning : null;
      if (target) { target.set(event.index, event.type === 'BlockDelta' ? (target.get(event.index) || '') + event.delta : event.content); publish(event.kind); }
    }
  };
  const result = settings.contextCompaction ? (await runTextContext(request, control, (r, c, e) => complete(r, c, wire, e), receive, settings.contextCompaction)).assistant! : await complete(request, control, wire, receive);
  const finalText = string(result.content), finalReasoning = string(result.reasoning_content);
  if (finalText !== lastText) onText?.(finalText); if (finalReasoning !== lastReasoning) onReasoning?.(finalReasoning);
  const parts = outputItems(result), input = usage?.inputTokens || 0, output = usage?.outputTokens || 0;
  return { message: { role: 'assistant', content: finalText, ...(Array.isArray(result.tool_calls) ? { tool_calls: result.tool_calls as ToolCall[] } : {}), ...(parts ? { providerProtocol: 'responses', providerParts: parts as JsonObject[] } : {}) },
    finishReason: string(result.finish_reason), reasoning: finalReasoning,
    ...(usage ? { usage: { input, output, total: usage.contextTokens ?? input + output } } : {}) };
}
export function implementedProtocol(protocol?: string): boolean { return !protocol || protocol === 'chat_completions' || protocol === 'responses'; }

export const completeFoundationResponses = completeFoundationProvider;
