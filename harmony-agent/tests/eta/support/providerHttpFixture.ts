// Desktop HTTP replacement for OpenAiResponsesProviderTest.withSseServer @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { createServer, ServerResponse } from 'node:http';
import { AddressInfo } from 'node:net';
import { ProviderStreamingTransport, completeOpenAiResponses } from '../../../entry/src/main/ets/core/eta/protocol/openAiResponsesProvider';
import { ProviderModelConfig, JsonObject, ProviderEvent, ProviderEventSink } from '../../../entry/src/main/ets/core/eta/protocol/providerTypes';
import { RunControl } from '../../../entry/src/main/ets/core/types';
import { AgentModelFailure } from '../../../entry/src/main/ets/core/eta/protocol/agentModelFailure';
export class NodeProviderTransport implements ProviderStreamingTransport {
  async stream(url: string, headers: Record<string, string>, body: string, onChunk: (chunk: string) => boolean | void, onStatus?: (status: number) => void): Promise<void> {
    const response = await fetch(url, { method: 'POST', headers, body, redirect: 'error' }); onStatus?.(response.status);
    if (!response.ok) throw AgentModelFailure.http(response.status, await response.text());
    const reader = response.body!.getReader(), decoder = new TextDecoder('utf-8', { fatal: true });
    try { for (;;) { const result = await reader.read(); if (result.done) { onChunk(decoder.decode()); break; } if (onChunk(decoder.decode(result.value, { stream: true })) === false) break; } }
    finally { await reader.cancel(); reader.releaseLock(); }
  }
}
export async function withSseServer(body: string | ((body: JsonObject) => string), block: (baseUrl: string, received: () => string) => Promise<void>, writeBody?: (response: ServerResponse) => Promise<void>, path = '/responses'): Promise<void> {
  let received = ''; const failures: unknown[] = [];
  const server = createServer(async (request, response) => {
    try {
      received = '';
      for await (const chunk of request) received += String(chunk);
      if (request.url !== path) { response.writeHead(404); response.end(); return; }
      response.writeHead(200, { 'Content-Type': 'text/event-stream' });
      if (writeBody) await writeBody(response); else response.write(typeof body === 'string' ? body : body(JSON.parse(received) as JsonObject)); response.end();
    } catch (e) { failures.push(e); response.destroy(); }
  });
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await block('http://127.0.0.1:' + (server.address() as AddressInfo).port, () => received); if (failures.length) throw failures[0]; }
  finally { server.closeAllConnections(); await new Promise<void>(resolve => server.close(() => resolve())); }
}
export function config(baseUrl = 'https://example.com/v1'): ProviderModelConfig { return { providerSourceType: 'custom', baseUrl, apiKey: 'test-key', model: 'test-model', systemPrompt: '系统提示', openAiEndpointMode: 'responses' }; }
export const event = (type: string, fields: JsonObject): string => `event: ${type}\ndata: ${JSON.stringify({ ...fields, type })}\n\n`;
export const responseTextEvent = (type: string, item_id: string, output_index: number, key: string, value: string): string => event(type, { item_id, output_index, summary_index: 0, content_index: 0, [key]: value });
export const reasoningItem = (id: string, text: string): JsonObject => ({ id, type: 'reasoning', summary: [{ text }] });
export const messageItem = (id: string, text: string): JsonObject => ({ id, type: 'message', content: [{ type: 'output_text', text }] });
export function complete(baseUrl: string, emit: ProviderEventSink = () => {}, text = '你好'): Promise<JsonObject> { return completeOpenAiResponses({ config: config(baseUrl), messages: [{ role: 'user', content: text }], tools: [], sessionId: 'fixture-session' }, new RunControl(), new NodeProviderTransport(), emit); }
export function timelineLabel(e: ProviderEvent): string | null {
  switch (e.type) { case 'BlockStart': return `start:${e.kind}:${e.index}`; case 'BlockDelta': return `delta:${e.kind}:${e.index}:${e.delta}`; case 'BlockEnd': return `end:${e.kind}:${e.index}`; case 'HostedToolStarted': return 'hosted-start:' + e.id; case 'HostedToolFinished': return 'hosted-end:' + e.id; default: return null; }
}
export function latch(): { done: () => void; wait: () => Promise<void> } {
  let done: () => void = () => {}; const promise = new Promise<void>(resolve => { done = resolve; });
  return { done, wait: async () => { let timer: ReturnType<typeof setTimeout> | undefined; try { await Promise.race([promise, new Promise<void>((_, reject) => { timer = setTimeout(() => reject(Error('SSE fixture latch timed out')), 5000); })]); } finally { clearTimeout(timer); } } };
}
