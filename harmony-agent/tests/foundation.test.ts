import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { FoundationController } from '../entry/src/main/ets/core/foundation/controller';
import { ShellNavigation } from '../entry/src/main/ets/core/foundation/navigation';
import { ConversationRepository } from '../entry/src/main/ets/core/conversations';
import { ProfileRepository, SecretStorage } from '../entry/src/main/ets/core/profiles';
import { DraftRepository } from '../entry/src/main/ets/core/drafts';
import { HttpTransport } from '../entry/src/main/ets/core/model';

class Storage {
  raw = ''; fail = false; gate: (() => Promise<void>) | undefined;
  async read() { return this.raw; }
  async write(value: string) { await this.gate?.(); if (this.fail) throw new Error('disk full'); this.raw = value; }
}
function deferred<T>() { let resolve!: (v: T) => void; const promise = new Promise<T>(r => resolve = r); return { promise, resolve }; }
const answer = JSON.stringify({ choices: [{ message: { role: 'assistant', content: 'OK' }, finish_reason: 'stop' }] });
function basicIo(): HttpTransport { return { get: async () => JSON.stringify({ data: [{ id: 'alpha' }, { id: 'beta' }, { id: 'gamma' }] }), post: async () => answer, cancel() {} }; }
async function fixture(io: () => HttpTransport = basicIo, secrets?: SecretStorage) {
  const chatStorage = new Storage(), profileStorage = new Storage(), draftStorage = new Storage(); let counter = 0;
  const keys = new Map<string, string>(); const keyStore: SecretStorage = secrets || { put: async (id, key) => { keys.set(id, key); }, get: async id => keys.get(id) || '', remove: async id => { keys.delete(id); } };
  const profiles = new ProfileRepository(profileStorage, keyStore, () => 'p' + ++counter);
  await profiles.load();
  const a = await profiles.save({ id: '', name: 'Service A', baseUrl: 'https://a.example/v1', model: 'alpha', models: ['alpha', 'beta'], streaming: false, screenshots: false, allowInsecureHttp: false }, 'key-a', true);
  const b = await profiles.save({ id: '', name: 'Service B', baseUrl: 'https://b.example/v1', model: 'gamma', models: ['gamma'], streaming: false, screenshots: false, allowInsecureHttp: false }, 'key-b', true);
  let controller: FoundationController;
  const chats = new ConversationRepository(chatStorage, () => 'c' + ++counter, Date.now, () => controller?.changed(), 2), drafts = new DraftRepository(draftStorage);
  controller = new FoundationController({ chats, drafts, profiles, transport: io, uuid: () => 'r' + ++counter, load: async () => { await chats.load(); await drafts.load(); } });
  await controller.initialize();
  return { controller, chats, drafts, profiles, a, b, chatStorage, profileStorage, draftStorage, keys };
}
async function until(check: () => boolean): Promise<void> { for (let i = 0; i < 1000; i++) { if (check()) return; await new Promise(r => setImmediate(r)); } assert.fail('expected transition did not happen'); }

test('navigation returns through model sheet, provider detail, list, and chat in order', () => {
  const nav = new ShellNavigation(); nav.drawer = true; assert.equal(nav.back(), true); assert.equal(nav.screen, 'chat');
  nav.push('providers'); nav.push('provider'); nav.sheet = 'catalog'; nav.back(); assert.equal(nav.screen, 'provider');
  nav.back(); assert.equal(nav.screen, 'providers'); nav.back(); assert.equal(nav.screen, 'chat'); assert.equal(nav.back(), false);
});

test('committed conversation selection projects its own model and draft without a delayed UI notification', async () => {
  const f = await fixture(), c = f.controller, first = c.snapshot().chat.id;
  await c.useModel({ key: 'a', profileId: f.a.id, provider: f.a.name, model: 'beta' }); c.draft('first draft');
  await c.newConversation(); const second = c.snapshot().chat.id;
  assert.equal(c.snapshot().error, ''); assert.equal(c.snapshot().chat.model, 'beta'); assert.equal(c.snapshot().chat.profileId, f.a.id);
  await c.useModel({ key: 'b', profileId: f.b.id, provider: f.b.name, model: 'gamma' }); c.draft('second draft');
  await c.selectConversation(first); assert.equal(c.snapshot().chat.model, 'beta'); assert.equal(c.snapshot().chat.input, 'first draft');
  await c.selectConversation(second); assert.equal(c.snapshot().chat.model, 'gamma'); assert.equal(c.snapshot().chat.input, 'second draft'); await c.flush();
});

test('editing a different provider cannot change the conversation model or send credentials', async () => {
  const f = await fixture(), c = f.controller; const before = c.snapshot().chat.model;
  c.navigate('providers'); await c.openProvider(f.a.id); c.editor.text('name', 'Unrelated edit');
  assert.equal(c.snapshot().chat.model, before); assert.equal(c.snapshot().editor.draft.apiKey, 'key-a');
  c.back(); assert.equal(c.snapshot().screen, 'providers'); c.back(); assert.equal(c.snapshot().chat.provider, 'Service B'); await c.flush();
});

test('remote catalog is staged, search-all selects matching models, cancel keeps form and existing list', async () => {
  const f = await fixture(), c = f.controller; await c.openProvider(f.b.id);
  const before = c.snapshot().editor.draft;
  await c.probeProvider(false); assert.equal(c.snapshot().sheet, 'catalog'); assert.deepEqual(c.snapshot().editor.draft.models, ['gamma']);
  c.editor.search('alp'); c.editor.toggleVisible(); assert.deepEqual(c.snapshot().editor.selected, ['alpha']); c.back();
  assert.equal(c.snapshot().sheet, 'none'); assert.deepEqual(c.snapshot().editor.draft, { ...before, metadata: c.snapshot().editor.draft.metadata });
  await c.probeProvider(false); c.editor.toggle('beta'); c.importModels(); assert.deepEqual(c.snapshot().editor.draft.models, ['gamma', 'beta']);
  assert.deepEqual(f.profiles.document.profiles.find(p => p.id === f.b.id)?.models, ['gamma']);
  assert.equal(await c.editor.save(), true); assert.deepEqual(f.profiles.document.profiles.find(p => p.id === f.b.id)?.models, ['gamma', 'beta']); await c.flush();
});

test('successful text test with unavailable catalog stays successful and preserves every input', async () => {
  const f = await fixture(() => ({ ...basicIo(), get: async () => { throw new Error('HTTP 404'); } })); const c = f.controller; await c.openProvider(f.a.id);
  const before = c.snapshot().editor.draft; await c.probeProvider(true);
  assert.equal(c.snapshot().editor.connection, '文本连接成功'); assert.match(c.snapshot().editor.error, /目录获取未完成/);
  assert.deepEqual(c.snapshot().editor.draft, before); assert.equal(c.snapshot().sheet, 'none'); c.editor.close();
});

test('late catalog from a closed editor cannot open a sheet or overwrite the new provider', async () => {
  const response = deferred<string>(), entered = deferred<void>(); let calls = 0;
  const f = await fixture(() => ({ ...basicIo(), get: async () => { calls++; entered.resolve(); return response.promise; } })); const c = f.controller;
  c.navigate('providers'); await c.openProvider(f.a.id); const pending = c.probeProvider(false); await entered.promise;
  c.back(); await c.openProvider(f.b.id); response.resolve(JSON.stringify({ data: [{ id: 'stale' }] })); await pending;
  assert.equal(c.snapshot().editor.draft.id, f.b.id); assert.equal(c.snapshot().editor.draft.apiKey, 'key-b'); assert.equal(c.snapshot().sheet, 'none'); assert.equal(calls, 1); c.editor.close();
});

test('cleared key stays cleared after back/open, failed save retains the draft and saved service', async () => {
  const f = await fixture(), c = f.controller; await c.openProvider(f.a.id); c.editor.text('apiKey', ''); c.editor.close(); await c.openProvider(f.a.id);
  assert.equal(c.snapshot().editor.draft.apiKey, ''); c.editor.text('name', 'unsaved name'); f.profileStorage.fail = true;
  assert.equal(await c.editor.save(), false); assert.equal(c.snapshot().editor.draft.name, 'unsaved name'); assert.equal(c.snapshot().editor.draft.apiKey, '');
  assert.equal(f.profiles.document.profiles.find(p => p.id === f.a.id)?.name, 'Service A'); c.editor.close();
});

test('address echo preserves credentials, a real endpoint edit cancels the pending request and clears old key', async () => {
  const response = deferred<string>(), entered = deferred<void>(); let cancelled = 0;
  const f = await fixture(() => ({ ...basicIo(), get: async () => { entered.resolve(); return response.promise; }, cancel: () => cancelled++ })); const c = f.controller; await c.openProvider(f.a.id);
  c.editor.text('baseUrl', f.a.baseUrl); assert.equal(c.snapshot().editor.draft.apiKey, 'key-a');
  const probe = c.probeProvider(false); await entered.promise; c.editor.text('baseUrl', 'https://different.example/v1');
  response.resolve(JSON.stringify({ data: [{ id: 'old' }] })); await probe;
  assert.equal(c.snapshot().editor.draft.apiKey, ''); assert.equal(c.snapshot().editor.draft.baseUrl, 'https://different.example/v1'); assert.equal(c.snapshot().sheet, 'none'); assert.ok(cancelled > 0); c.editor.close();
});

test('new input typed during send admission is not cleared with the submitted draft', async () => {
  const f = await fixture(), c = f.controller, entered = deferred<void>(), release = deferred<void>(); let held = true;
  c.draft('submitted'); f.chatStorage.gate = async () => { if (held) { held = false; entered.resolve(); await release.promise; } };
  const sending = c.send(); await entered.promise; c.draft('newer draft'); release.resolve(); await sending; await c.runtime.idle();
  assert.equal(c.snapshot().chat.input, 'newer draft'); assert.equal(c.snapshot().chat.rows[0].request, 'submitted'); await c.flush();
});

test('storage failure before a user turn forbids network requests and preserves the input', async () => {
  let calls = 0; const f = await fixture(() => ({ ...basicIo(), post: async () => { calls++; return answer; } })); const c = f.controller;
  c.draft('keep this'); f.chatStorage.fail = true; await c.send(); await c.runtime.idle();
  assert.equal(calls, 0); assert.equal(c.snapshot().chat.input, 'keep this'); assert.match(c.snapshot().error, /disk full/); await c.flush();
});

test('stop during secure-key lookup leaves a durable stopped turn and never starts HTTP', async () => {
  const key = deferred<string>(), entered = deferred<void>(); let requests = 0;
  const store: SecretStorage = { put: async () => {}, remove: async () => {}, get: async () => { entered.resolve(); return key.promise; } };
  const f = await fixture(() => ({ ...basicIo(), post: async () => { requests++; return answer; } }), store); const c = f.controller;
  c.draft('hello'); await c.send(); await entered.promise; assert.equal(f.chats.get(c.snapshot().chat.id)?.turns.length, 1);
  c.stop(); key.resolve('secret'); await c.runtime.idle(); assert.equal(requests, 0); assert.equal(f.chats.get(c.snapshot().chat.id)?.turns[0].status, 'stopped'); await c.flush();
});

test('chat body includes no tool definitions even for an old device conversation', async () => {
  let request = ''; const f = await fixture(() => ({ ...basicIo(), post: async (_url, _headers, body) => { request = body; return answer; } })); const c = f.controller;
  await f.chats.preferences(c.snapshot().chat.id, f.a.id, 'device', 'alpha'); c.draft('hi'); await c.send(); await c.runtime.idle();
  assert.equal(JSON.parse(request).tools, undefined); assert.equal(f.chats.get(c.snapshot().chat.id)?.mode, 'chat'); assert.equal(c.snapshot().chat.rows[0].reply, 'OK'); await c.flush();
});

test('real HTTP SSE fragments render before completion, survive conversation switch, and persist full final text', { timeout: 10000 }, async () => {
  const release = deferred<void>(), chunkSent = deferred<void>(); let payload: Record<string, unknown> = {};
  const server = createServer(async (req, res) => {
    let body = ''; for await (const chunk of req) body += chunk; payload = JSON.parse(body);
    res.writeHead(200, { 'Content-Type': 'text/event-stream' });
    const frame = 'data: ' + JSON.stringify({ choices: [{ delta: { content: '第一段' }, finish_reason: null }] }) + '\n\n';
    res.write(frame.slice(0, 17)); res.write(frame.slice(17)); chunkSent.resolve(); await release.promise;
    res.end('data: ' + JSON.stringify({ choices: [{ delta: { content: '，完整回答' }, finish_reason: null }] }) + '\n\ndata: ' + JSON.stringify({ choices: [{ delta: {}, finish_reason: 'stop' }] }) + '\n\ndata: [DONE]\n\n');
  }); server.listen(0, '127.0.0.1'); await once(server, 'listening');
  const address = server.address(); assert.ok(address && typeof address !== 'string');
  const endpoint = 'http://127.0.0.1:' + address.port + '/v1';
  function transport(): HttpTransport {
    const abort = new AbortController();
    return { post: async () => answer, cancel: () => abort.abort(), stream: async (url, headers, body, onChunk) => {
      const response = await fetch(url, { method: 'POST', headers, body, signal: abort.signal }); assert.equal(response.status, 200);
      const reader = response.body!.getReader(), decoder = new TextDecoder(); while (true) { const next = await reader.read(); if (next.done) break; onChunk(decoder.decode(next.value, { stream: true })); }
    } };
  }
  try {
    const f = await fixture(transport), c = f.controller;
    await f.profiles.save({ ...f.a, baseUrl: endpoint, streaming: true, allowInsecureHttp: true }, '', false);
    await c.useModel({ key: 'x', profileId: f.a.id, provider: f.a.name, model: 'alpha' }); const first = c.snapshot().chat.id;
    c.draft('live test'); await c.send(); await chunkSent.promise; await until(() => c.snapshot().chat.rows[0]?.reply === '第一段');
    const runId = c.snapshot().chat.rows[0].id; assert.equal(c.snapshot().chat.active, true);
    await c.newConversation(); assert.equal(c.snapshot().chat.rows.length, 0); await c.selectConversation(first); assert.equal(c.snapshot().chat.rows[0].id, runId); assert.equal(c.snapshot().chat.rows[0].reply, '第一段');
    release.resolve(); await c.runtime.idle(); assert.equal(c.snapshot().chat.rows[0].reply, '第一段，完整回答'); assert.equal(c.snapshot().chat.rows[0].active, false); assert.equal(payload.tools, undefined);
    const reloaded = new ConversationRepository(f.chatStorage, () => 'new'); await reloaded.load(); assert.equal(reloaded.get(first)?.turns[0].reply, '第一段，完整回答'); await c.flush();
  } finally { release.resolve(); server.closeAllConnections(); await new Promise<void>(resolve => server.close(() => resolve())); }
});

test('background cancellation keeps streamed partial text and never accepts a late completion', async () => {
  const response = deferred<void>(), entered = deferred<void>();
  const f = await fixture(() => ({ ...basicIo(), stream: async (_url, _headers, _body, onChunk) => {
    onChunk('data: ' + JSON.stringify({ choices: [{ delta: { content: 'partial' }, finish_reason: null }] }) + '\n\n'); entered.resolve(); await response.promise;
    onChunk('data: ' + JSON.stringify({ choices: [{ delta: { content: 'late' }, finish_reason: 'stop' }] }) + '\n\ndata: [DONE]\n\n');
  } })); const c = f.controller;
  await f.profiles.save({ ...f.b, streaming: true }, 'key-b', true); c.draft('hello'); await c.send(); await entered.promise;
  c.runtime.setForeground(false); response.resolve(); await c.runtime.idle(); assert.equal(c.snapshot().chat.rows[0].reply, 'partial'); assert.equal(c.snapshot().chat.rows[0].active, false);
  assert.match(c.snapshot().chat.rows[0].notice, /后台/); c.runtime.setForeground(true); assert.equal(c.snapshot().chat.active, false); await c.flush();
});

test('cold restart restores saved catalog and draft without writing API keys into ordinary metadata', async () => {
  const f = await fixture(), c = f.controller; await c.openProvider(f.a.id); c.editor.addModel('new-model'); assert.equal(await c.editor.save(), true);
  c.draft('draft after restart'); await c.flush(); await c.editor.flush();
  assert.ok(!f.profileStorage.raw.includes('key-a')); assert.ok(!f.draftStorage.raw.includes('key-a'));
  const drafts = new DraftRepository(f.draftStorage); await drafts.load(); assert.equal(drafts.message(c.snapshot().chat.id).text, 'draft after restart');
  const data = JSON.parse(f.profileStorage.raw); assert.ok(data.profiles.find((p: { id: string }) => p.id === f.a.id).models.includes('new-model')); c.editor.close();
});


test('verified notification keeps route and selection when conversation no longer exists', async () => {
  const f=await fixture(), c=f.controller, before=c.snapshot().chat.id;
  c.drawer(true); c.navigate('settings');
  assert.equal(await c.notificationConversation('missing'),false);
  assert.equal(c.snapshot().screen,'settings'); assert.equal(c.snapshot().chat.id,before);
  assert.equal(c.snapshot().drawer,true); assert.equal(c.runtime.active(),false);
});
test('verified notification selects its conversation and closes pane without starting a run', async () => {
  const f=await fixture(), c=f.controller, first=c.snapshot().chat.id;
  c.draft('retained'); await c.newConversation(); c.drawer(true); c.navigate('settings');
  assert.equal(await c.notificationConversation(first),true);
  assert.equal(c.snapshot().screen,'chat'); assert.equal(c.snapshot().drawer,false);
  assert.equal(c.snapshot().chat.id,first); assert.equal(c.snapshot().chat.input,'retained');
  assert.equal(c.runtime.active(),false); await c.flush();
});
