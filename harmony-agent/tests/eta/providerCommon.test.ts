// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/model/ProviderSseReaderTest.kt (4),
// ProviderRequestHeadersTest.kt (7), ProviderUrlsTest.kt (1) @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { RunControl } from '../../entry/src/main/ets/core/types';
import { readProviderSse, ProviderTextStream } from '../../entry/src/main/ets/core/eta/protocol/providerSseReader';
import { mergeProviderHeaders, sessionNameUuid, validateProviderHeaders } from '../../entry/src/main/ets/core/eta/protocol/providerRequestHeaders';
import { ProviderUrls as urls } from '../../entry/src/main/ets/core/eta/protocol/providerUrls';
class TrackedStream implements ProviderTextStream {
  closed = false; private sent = false;
  constructor(private content: string, private rejectEof = false) {}
  async read(): Promise<string | null> { if (this.sent) { if (this.rejectEof) throw Error('Reader waited beyond terminal event'); return null; } this.sent = true; return this.content; }
  async close(): Promise<void> { this.closed = true; }
}
test('framingHandlesCommentsMultilineDataAndFinalFrameWithoutBlankLine', async () => {
  const source = ': keepalive\r\n\r\nevent: delta\r\ndata: 第一行\r\ndata: 第二行\r\n\r\nevent: done\ndata: 终态', events: string[][] = [];
  await readProviderSse(new TrackedStream(source), new RunControl(), (name, data) => { events.push([name, data]); return true; });
  assert.deepEqual(events, [['delta', '第一行\n第二行'], ['done', '终态']]);
});
test('terminalEventClosesStreamWithoutReadingAgain', async () => {
  const stream = new TrackedStream('data: completed\n\n', true);
  await readProviderSse(stream, new RunControl(), (_, data) => { assert.equal(data, 'completed'); return false; }); assert.equal(stream.closed, true);
});
test('cancellationStopsBufferedEventsAndClosesStream', async () => {
  const stream = new TrackedStream('data: first\n\ndata: second\n\n'), control = new RunControl(), received: string[] = [];
  await assert.rejects(readProviderSse(stream, control, (_, data) => { received.push(data); control.stop(); return true; }), /CANCELLED/);
  assert.deepEqual(received, ['first']); assert.equal(stream.closed, true);
});
test('consumerFailurePropagatesAndClosesStream', async () => {
  const stream = new TrackedStream('data: invalid\n\n'), expected = Error('Invalid fixture');
  await assert.rejects(readProviderSse(stream, new RunControl(), () => { throw expected; }), e => e === expected); assert.equal(stream.closed, true);
});
const headers = (url: string, session: string): Record<string, string> => { const h = {}; mergeProviderHeaders(h, url, [], session); return h; };
const get = (headers: Record<string, string>, name: string): string | undefined => headers[Object.keys(headers).find(k => k.toLowerCase() === name.toLowerCase()) || ''];
test('codexUsesEtaOriginatorAndTheRuntimeSession', () => {
  const h = {}; mergeProviderHeaders(h, 'https://chatgpt.com/backend-api/codex', [], 'conversation-fixture', 'openai_chatgpt');
  assert.equal(get(h, 'originator'), 'eta'); assert.equal(get(h, 'session-id'), 'conversation-fixture'); assert.equal(get(h, 'OpenAI-Beta'), 'responses=experimental');
});
test('grokSubscriptionUsesManagedRoutingWithoutPretendingToBeAClientVersion', () => {
  const h = {}; mergeProviderHeaders(h, 'https://cli-chat-proxy.grok.com/v1', [], 'fixture-session', 'xai_grok', 'grok-fixture');
  assert.equal(get(h, 'X-XAI-Token-Auth'), 'xai-grok-cli'); assert.equal(get(h, 'x-grok-model-override'), 'grok-fixture'); assert.equal(get(h, 'User-Agent'), 'Harmony-Agent'); assert.equal(get(h, 'x-grok-client-version'), undefined);
});
test('subscriptionRejectsCustomAuthenticationAndRoutingHeaders', () => {
  for (const name of ['Authorization', 'Cookie', 'X-Grok-Model-Override', 'OpenAI-Project', 'ChatGPT-Account-ID', 'Originator', 'Session-Id']) {
    assert.throws(() => mergeProviderHeaders({}, 'https://api.openai.com/v1', [{ name, value: 'secret-fixture' }], 'fixture', 'openai_chatgpt'), e => e instanceof Error && !e.message.includes('secret-fixture'));
  }
});
test('openCodeSessionIsStableAndIsolatedFromOtherConversations', () => {
  const first = headers('https://opencode.ai/zen/go/v1', 'conversation-1'), retry = headers('https://opencode.ai/zen/go/v1', 'conversation-1'), next = headers('https://opencode.ai/zen/go/v1', 'conversation-2');
  assert.equal(get(first, 'x-opencode-session'), get(retry, 'x-opencode-session')); assert.notEqual(get(first, 'x-opencode-session'), get(next, 'x-opencode-session')); assert.equal(get(first, 'User-Agent'), 'Harmony-Agent');
});
test('sessionHeaderDoesNotLeakToUnrelatedHosts', () => { for (const url of ['https://example.com/v1', 'https://opencode.ai.example.com/v1']) assert.equal(get(headers(url, 'conversation-1'), 'x-opencode-session'), undefined); });
test('customUserAgentOverridesDefaultButSessionAndAuthStayManaged', () => {
  const h = { Authorization: 'Bearer test-key' };
  mergeProviderHeaders(h, 'https://opencode.ai/zen/go/v1', [{ name: 'user-agent', value: 'test-client' }, { name: 'Authorization', value: 'bad' }, { name: 'X-OpenCode-Session', value: 'fixed' }], 'conversation-1');
  assert.equal(get(h, 'User-Agent'), 'test-client'); assert.equal(Object.keys(h).filter(k => k.toLowerCase() === 'user-agent').length, 1); assert.equal(h.Authorization, 'Bearer test-key'); assert.notEqual(get(h, 'x-opencode-session'), 'fixed');
});
test('editorRejectsInvalidAndDuplicateHeadersWithoutEchoingValues', () => {
  assert.equal(validateProviderHeaders([{ name: 'User-Agent', value: 'test-client' }]), null);
  for (const h of [[{ name: 'X-Test', value: 'secret\r\nInjected: value' }], [{ name: 'bad:name', value: 'secret' }], [{ name: 'Authorization', value: 'secret' }], [{ name: 'X-Test', value: '1' }, { name: ' x-test ', value: '2' }]]) { const e = validateProviderHeaders(h); assert.ok(e && !e.includes('secret')); }
});
test('appendsProviderSpecificPathsToRootBaseUrl', () => {
  assert.equal(urls.openAiChatCompletionsUrl('https://api.openai.com/v1/'), 'https://api.openai.com/v1/chat/completions');
  assert.equal(urls.openAiModelsUrl('https://api.openai.com/v1'), 'https://api.openai.com/v1/models'); assert.equal(urls.openAiResponsesUrl('https://api.openai.com/v1/'), 'https://api.openai.com/v1/responses');
  assert.equal(urls.anthropicMessagesUrl('https://api.anthropic.com/'), 'https://api.anthropic.com/v1/messages'); assert.equal(urls.anthropicModelsUrl('https://api.anthropic.com'), 'https://api.anthropic.com/v1/models');
  assert.equal(urls.geminiInteractionsUrl(' https://generativelanguage.googleapis.com/v1beta/ '), 'https://generativelanguage.googleapis.com/v1beta/interactions'); assert.equal(urls.geminiModelsUrl('https://proxy.invalid/google/v1beta/'), 'https://proxy.invalid/google/v1beta/models');
});
// Additional platform regression, not counted among the 12 original tests above.
test('portable Java name UUID matches MD5 version/variant for Unicode and multiple blocks', () => {
  for (const text of ['', 'conversation-1', '中文👩🏽‍💻'.repeat(100)]) {
    const bytes = createHash('md5').update(text).digest(); bytes[6] = bytes[6] & 15 | 48; bytes[8] = bytes[8] & 63 | 128;
    const hex = bytes.toString('hex'); assert.equal(sessionNameUuid(text), `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`);
  }
});
