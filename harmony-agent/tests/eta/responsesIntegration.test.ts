// Harmony integration for app/src/main/kotlin/io/github/mangi/eta/agent/model/OpenAiResponsesProvider.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). Additional regressions, not counted as original Eta tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import { HttpTransport, OpenAIClient, ModelSettings } from '../../entry/src/main/ets/core/model';
import { RunControl } from '../../entry/src/main/ets/core/types';
import { JsonObject, effectiveRequest } from '../../entry/src/main/ets/core/eta/protocol/providerTypes';
import { ProviderSseReader } from '../../entry/src/main/ets/core/eta/protocol/providerSseReader';
import { OpenAiResponsesStream } from '../../entry/src/main/ets/core/eta/protocol/openAiResponsesProvider';
import { AgentModelFailure } from '../../entry/src/main/ets/core/eta/protocol/agentModelFailure';
import { buildResponsesRequest, attachOutputItems } from '../../entry/src/main/ets/core/eta/protocol/responsesRequestBuilder';
import { encodeTranscript, decodeTranscript } from '../../entry/src/main/ets/core/eta/storage/conversationCodec';
import { conversationMessage } from '../../entry/src/main/ets/core/eta/model/conversation';
import { FoundationChatRuntime } from '../../entry/src/main/ets/core/foundation/chatRuntime';
import { ProviderEditor } from '../../entry/src/main/ets/core/foundation/providerEditor';
import { ConversationRepository } from '../../entry/src/main/ets/core/conversations';
import { ProfileRepository } from '../../entry/src/main/ets/core/profiles';
import { DraftRepository } from '../../entry/src/main/ets/core/drafts';
import { foundationPickerProviders } from '../../entry/src/main/ets/core/eta/foundationModelPicker';
import { config, event, latch } from './support/providerHttpFixture';
const settings: ModelSettings = { baseUrl: 'https://api.example.com/v1/responses', model: 'gpt-test', apiKey: 'fixture-key', screenshots: false, protocol: 'responses', streaming: false };
function transport(chunks: string[], before: (body: string, url: string) => void = () => {}): HttpTransport {
  return { async post() { throw Error('Responses must use SSE'); }, async get() { return JSON.stringify({ data: [{ id: 'gpt-test' }, { id: 'new-model' }] }); }, cancel() {},
    async stream(url, _headers, body, receive, status) { before(body, url); status?.(200); for (const chunk of chunks) if (receive(chunk) === false) break; } };
}
const delta = event('response.output_text.delta', { delta: '已收到' }), done = event('response.completed', { response: { status: 'completed' } });
class Storage { raw = ''; fail = false; async read() { return this.raw; } async write(raw: string) { if (this.fail) throw Error('disk full'); this.raw = raw; } }
async function runtimeFixture(io: () => HttpTransport) {
  const chat = new Storage(), metadata = new Storage(), drafts = new DraftRepository(new Storage()), keys = new Map<string, string>(); let next = 0;
  const profiles = new ProfileRepository(metadata, { async get(id) { return keys.get(id) || ''; }, async put(id, value) { keys.set(id, value); }, async remove(id) { keys.delete(id); } }, () => 'p' + ++next); await profiles.load();
  const profile = await profiles.save({ ...settings, id: '', name: 'Responses', model: 'gpt-test', models: ['gpt-test'], allowInsecureHttp: false }, settings.apiKey, true);
  const chats = new ConversationRepository(chat, () => 'c' + ++next); await chats.load(); await drafts.load(); const id = await chats.create(profile.id);
  const runtime = new FoundationChatRuntime(chats, profiles, io, () => 'run' + ++next, () => {});
  return { chat, metadata, chats, id, profiles, profile, runtime, drafts };
}
test('SSE handles every UTF-16 transport split, including CRLF and terminal buffered events', () => {
  const source = ': comment\r\nevent: delta\r\ndata: 中文👩🏽‍💻\r\ndata: line2\r\n\r\ndata: terminal\r\n\r\ndata: late\n\n';
  for (let split = 0; split <= source.length; split++) { const values: string[] = [], parser = new ProviderSseReader(new RunControl(), (_, data) => { values.push(data); return data !== 'terminal'; }); parser.feed(source.slice(0, split)); parser.feed(source.slice(split)); parser.finish(); assert.deepEqual(values, ['中文👩🏽‍💻\nline2', 'terminal']); }
});
test('Responses failure diagnostics do not echo provider message, unknown code or body fields', () => {
  const privateText = 'secret body and key';
  for (const payload of [{ error: { type: privateText, message: privateText, param: 'tools[0].parameters.properties.private_field' } }, { type: 'error', code: privateText, message: privateText }]) {
    const parser = new OpenAiResponsesStream(new RunControl()); assert.throws(() => parser.feed('data: ' + JSON.stringify(payload) + '\n\n'), e => e instanceof AgentModelFailure && !e.message.includes(privateText) && !e.message.includes('private_field'));
  }
});
test('non-chat request purpose strips custom bodies, hosted search and tools; durable codec strips opaque output', () => {
  const request = effectiveRequest({ config: { ...config(), hostedWebSearchEnabled: true, extraBodyJson: '{"danger":"fixture"}', customBody: [{ key: 'metadata', value: 'fixture' }] }, messages: [], tools: [{ type: 'function' }], sessionId: 's', purpose: 'compaction' });
  const body = buildResponsesRequest(request.config, request.messages, request.tools); assert.equal(body.tools, undefined); assert.equal(body.danger, undefined); assert.equal(body.metadata, undefined);
  const m = { ...conversationMessage('assistant', 'visible') }; attachOutputItems(m as unknown as JsonObject, [{ encrypted_content: 'opaque-run-only' }]);
  assert.ok(!encodeTranscript([m]).includes('opaque-run-only')); assert.ok(!JSON.stringify(decodeTranscript(JSON.stringify([m]))).includes('opaque-run-only'));
});
test('Responses adapter uses protected SSE request, final authority, and keeps opaque items for one run', async () => {
  let body: JsonObject = {}; const content = [{ id: 'rs', type: 'reasoning', encrypted_content: 'opaque', summary: [] }, { id: 'msg', type: 'message', content: [{ type: 'output_text', text: '权威正文' }] }];
  const io = transport([delta, event('response.completed', { response: { status: 'completed', output: content, usage: { input_tokens: 2, output_tokens: 3, total_tokens: 5 } } })], (raw, url) => { body = JSON.parse(raw); assert.equal(url, 'https://api.example.com/v1/responses'); });
  const visible: string[] = [], reply = await new OpenAIClient(settings, io, []).complete([{ role: 'user', content: 'hello' }], new RunControl(), text => visible.push(text));
  assert.equal(body.stream, true); assert.equal(body.store, false); assert.equal(body.tools, undefined); assert.equal(visible[0], '已收到'); assert.equal(visible[visible.length - 1], '权威正文'); assert.equal(reply.message.content, '权威正文'); assert.deepEqual(reply.usage, { input: 2, output: 3, total: 5 }); assert.deepEqual(reply.message.providerParts, content);
  await new OpenAIClient(settings, io, []).complete([reply.message, { role: 'user', content: 'next' }], new RunControl()); assert.equal((body.input as JsonObject[])[0].encrypted_content, 'opaque');
});
test('Responses runtime durably saves the user turn before HTTP, persists streaming output, and reload never sends', async () => {
  let calls = 0; const f = await runtimeFixture(() => transport([delta, done], () => { calls++; const c = JSON.parse(f.chat.raw).conversations.find((c: { id: string }) => c.id === f.id); assert.equal(c.turns[0].request, 'hello'); assert.equal(c.turns[0].status, 'running'); }));
  f.runtime.start(f.id, 'hello', f.profile.id, 'gpt-test'); await f.runtime.idle(); assert.equal(calls, 1); const turn = f.chats.get(f.id)!.turns[0]; assert.equal(turn.reply, '已收到'); assert.equal(turn.status, 'completed');
  const restored = new ConversationRepository(f.chat, () => 'new'); await restored.load(); assert.equal(restored.get(f.id)!.turns[0].reply, '已收到'); assert.equal(calls, 1);
});
test('Responses stop keeps received content and makes no retry or queued restart', async () => {
  const entered = latch(); let rejectStream: (error: Error) => void = () => {}, calls = 0;
  const f = await runtimeFixture(() => ({ async post() { return ''; }, async stream(_url, _headers, _body, receive) { calls++; receive(delta); entered.done(); await new Promise<void>((_, reject) => { rejectStream = reject; }); }, cancel() { rejectStream(Error('CANCELLED')); } }));
  f.runtime.start(f.id, 'stop me', f.profile.id, 'gpt-test'); await entered.wait(); assert.equal(f.runtime.activity(f.id)?.reply, '已收到'); f.runtime.stop(f.id); await f.runtime.idle();
  assert.equal(calls, 1); assert.equal(f.chats.get(f.id)!.turns[0].reply, '已收到'); assert.equal(f.chats.get(f.id)!.turns[0].status, 'stopped');
});
test('Responses save failure blocks network, while missing terminal preserves partial reply without retry', async () => {
  let calls = 0; const f = await runtimeFixture(() => transport([delta], () => { calls++; })); f.chat.fail = true;
  f.runtime.start(f.id, 'never send', f.profile.id, 'gpt-test'); await f.runtime.idle(); assert.equal(calls, 0);
  f.chat.fail = false; f.runtime.start(f.id, 'partial once', f.profile.id, 'gpt-test'); await f.runtime.idle(); assert.equal(calls, 1); const turn = f.chats.get(f.id)!.turns[0]; assert.equal(turn.status, 'failed'); assert.equal(turn.reply, '已收到');
});
test('Responses test connection then model catalog retains draft values and saved identity', async () => {
  const requests: string[] = [], io = () => transport([event('response.output_text.delta', { delta: 'OK' }), done], (_raw, url) => requests.push(url)); const f = await runtimeFixture(io), editor = new ProviderEditor(f.profiles, f.drafts, io, () => {});
  await editor.open(f.profile.id); editor.text('name', 'unsaved'); const before = editor.snapshot().draft;
  assert.equal(await editor.probe(true), true); assert.equal(editor.state.connection, '文本连接成功'); assert.equal(editor.state.draft.apiKey, before.apiKey); assert.equal(editor.state.draft.name, 'unsaved'); assert.equal(editor.state.draft.id, before.id); assert.deepEqual(editor.state.draft.models, ['gpt-test']); assert.deepEqual(editor.state.catalog, ['new-model']); assert.equal(requests.length, 1); assert.equal(f.profiles.document.profiles[0].name, 'Responses'); editor.close();
});
test('Responses enters the picker while protocols awaiting implementation remain disabled', async () => {
  const f = await runtimeFixture(() => transport([delta, done]));
  const all = foundationPickerProviders([f.profile, { ...f.profile, id: 'other', protocol: 'anthropic' }]); assert.equal(all[0].isEnabled, true); assert.equal(all[1].isEnabled, false);
});
