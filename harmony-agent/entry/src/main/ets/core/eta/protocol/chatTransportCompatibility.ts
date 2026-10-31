// Harmony compatibility adapter around OpenAiChatCompletionsProvider.kt @ 3b1794d.
// Preserves the existing 0.9 explicit non-stream setting and JSON-only HTTP responses without retrying.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import type { HttpTransport } from '../../model';
import { ProviderStreamingTransport } from './openAiResponsesProvider';
import { ProviderRunControl, object, array, string } from './providerTypes';
import { AgentModelFailure } from './agentModelFailure';
function jsonFrame(raw: string): string {
  let data; try { data = object(JSON.parse(raw)); } catch (_) { throw Error('服务返回的内容不是有效 JSON，请检查接口地址'); }
  if (!data) throw Error('模型没有返回有效的 assistant 消息');
  const error = object(data.error); if (error) throw AgentModelFailure.stream(error);
  const choice = object(array(data.choices)[0]), message = object(choice?.message);
  if (!message || message.role !== 'assistant' || message.content != null && typeof message.content !== 'string') throw Error('模型没有返回有效的 assistant 消息');
  if (message.tool_calls != null && !Array.isArray(message.tool_calls)) throw Error('无效工具调用格式');
  const calls = array(message.tool_calls);
  calls.forEach(rawCall => { const call = object(rawCall), fn = object(call?.function); if (!call || typeof call.id !== 'string' || !call.id || call.type !== 'function' || typeof fn?.name !== 'string' || !fn.name || typeof fn.arguments !== 'string') throw Error('无效工具调用格式'); });
  if (!message.content && !calls.length) throw Error('模型返回空消息');
  const delta = { ...message, ...(calls.length ? { tool_calls: calls.map((c, index) => ({ ...object(c), index })) } : {}) };
  return 'data: ' + JSON.stringify({ choices: [{ delta, finish_reason: string(choice?.finish_reason) || (calls.length ? 'tool_calls' : 'stop') }], ...(object(data.usage) ? { usage: data.usage } : {}) }) + '\n\ndata: [DONE]\n\n';
}
export function compatibleChatTransport(transport: HttpTransport, streaming: boolean | undefined, control: ProviderRunControl): ProviderStreamingTransport {
  return { stream: async (url, headers, body, onChunk, onStatus) => {
    const request = JSON.parse(body);
    // The current text-only shell registers no tools. Preserve its pre-E07b3 request contract.
    if (Array.isArray(request.tools) && !request.tools.length) { delete request.tools; delete request.tool_choice; }
    body = JSON.stringify(request);
    const useStream = streaming !== false && Boolean(transport.stream); let received = 0;
    const bounded = (text: string): void => { received += text.length; if (received > 2_000_000) throw Error('模型响应超过大小限制'); };
    if (!useStream) { const request = JSON.parse(body); request.stream = false; delete request.stream_options;
      const raw = await transport.post(url, { ...headers, Accept: 'application/json' }, JSON.stringify(request)); control.check(); bounded(raw); onChunk(jsonFrame(raw)); return; }
    let mode: 'unknown' | 'json' | 'sse' = 'unknown', pending = '';
    await transport.stream!(url, headers, body, chunk => {
      control.check(); bounded(chunk); if (mode === 'sse') return onChunk(chunk);
      pending += chunk; if (mode === 'json') return;
      const first = pending.trimStart()[0]; if (!first) return; if (first === '{' || first === '[') { mode = 'json'; return; }
      mode = 'sse'; const text = pending; pending = ''; return onChunk(text);
    }, onStatus);
    control.check(); if (mode as string === 'json') onChunk(jsonFrame(pending));
  } };
}
