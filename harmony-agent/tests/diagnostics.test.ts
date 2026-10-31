import test from 'node:test';
import assert from 'node:assert/strict';
import { DiagnosticLog, DiagnosticTransport, diagnosticReport, emptyEnvironment, hostOf, redact } from '../entry/src/main/ets/core/diagnostics';
import { FoundationController } from '../entry/src/main/ets/core/foundation/controller';
import { ConversationRepository } from '../entry/src/main/ets/core/conversations';
import { ProfileRepository } from '../entry/src/main/ets/core/profiles';
import { DraftRepository } from '../entry/src/main/ets/core/drafts';
import { HttpTransport } from '../entry/src/main/ets/core/model';

class Storage {
  raw = ''; fail = false; writes = 0;
  async read() { if (this.fail) throw new Error('disk unavailable'); return this.raw; }
  async write(value: string) { if (this.fail) throw new Error('disk full'); this.writes++; this.raw = value; }
}
async function until(check: () => boolean): Promise<void> { for (let i = 0; i < 1000; i++) { if (check()) return; await new Promise(r => setImmediate(r)); } assert.fail('expected transition did not happen'); }

test('redact removes keys, authorization values, URL paths and query strings', () => {
  const text = 'POST https://user:pw@api.example.com/v1/chat?key=AIzaSyA1234567890abcdefghijk failed: Authorization: Bearer sk-proj-abcdefghijklmnop '
    + 'api_key="sk-live-0123456789" token=abc123 my-own-secret-value';
  const out = redact(text, ['my-own-secret-value'], 1000);
  for (const leaked of ['user:pw', '/v1/chat', 'AIzaSy', 'sk-proj-abcdefghijklmnop', 'sk-live-0123456789', 'abc123', 'my-own-secret-value']) assert.equal(out.includes(leaked), false, leaked);
  assert.match(out, /https:\/\/api\.example\.com\/…/);
  assert.match(out, /Bearer \[已隐藏\]/);
  assert.equal(hostOf('https://k:v@api.example.com:8443/v1?x=1'), 'api.example.com:8443');
});

test('redact flattens newlines and enforces the length limit', () => {
  const out = redact('line one\nline two\r\n' + 'x'.repeat(500), [], 40);
  assert.equal(out.includes('\n'), false);
  assert.equal(out.length, 41);
  assert.ok(out.endsWith('…'));
});

test('diagnostic log is bounded, persists, restores and survives a corrupted document', async () => {
  const storage = new Storage(); let now = 1000;
  const log = new DiagnosticLog(storage, 5, () => now++, 0);
  for (let i = 0; i < 8; i++) log.record('http', 'request ' + i);
  assert.equal(log.events().length, 5);
  assert.equal(log.events()[0].stage, 'request 3');
  await log.flush();
  const restored = new DiagnosticLog(storage, 5, () => now++, 0);
  restored.record('lifecycle', 'early event');
  await restored.load();
  assert.deepEqual(restored.events().map(e => e.stage), ['request 4', 'request 5', 'request 6', 'request 7', 'early event']);
  storage.raw = '{broken';
  const broken = new DiagnosticLog(storage, 5, () => now++, 0); await broken.load();
  assert.equal(broken.events().length, 1); assert.equal(broken.events()[0].area, 'storage');
  storage.fail = true;
  const unreadable = new DiagnosticLog(storage, 5, () => now++, 0); await unreadable.load();
  assert.equal(unreadable.events().length, 1);
  await assert.rejects(unreadable.flush(), /disk full/);
});

test('diagnostic transport logs timing and size but never request bodies, headers or replies', async () => {
  const log = new DiagnosticLog(undefined, 50); let clock = 0;
  const inner: HttpTransport = {
    post: async () => 'REPLY-CONTENT-SHOULD-NOT-APPEAR',
    stream: async (_url, _headers, _body, onChunk) => { clock += 120; onChunk('hello '); onChunk('world'); clock += 30; },
    cancel() {}
  };
  const transport = new DiagnosticTransport(inner, log, () => clock);
  assert.equal(transport.get, undefined, 'optional capabilities are not invented');
  const headers = { Authorization: 'Bearer sk-abcdefghijklmnopqrstuvwxyz' };
  await transport.post('https://api.example.com/v1/chat/completions', headers, '{"messages":[{"content":"PRIVATE-QUESTION"}]}');
  const chunks: string[] = [];
  await transport.stream!('https://api.example.com/v1/chat/completions', headers, 'PRIVATE-QUESTION', c => chunks.push(c));
  assert.deepEqual(chunks, ['hello ', 'world']);
  const text = JSON.stringify(log.events());
  for (const leaked of ['PRIVATE-QUESTION', 'REPLY-CONTENT', 'sk-abcdefghijklmnop', '/v1/chat']) assert.equal(text.includes(leaked), false, leaked);
  assert.match(log.events()[1].detail, /首包 120ms · 总计 150ms · 2 段 · 11 字符/);
});

test('diagnostic transport records failures and cancellation and rethrows them', async () => {
  const log = new DiagnosticLog(undefined, 50);
  const failing = new DiagnosticTransport({ post: async () => { throw new Error('模型服务返回 HTTP 401，请检查 API 地址、密钥和额度'); }, cancel() {} }, log);
  await assert.rejects(failing.post('https://a.example/v1/chat', {}, ''), /HTTP 401/);
  const cancelled = new DiagnosticTransport({ post: async () => { throw new Error('CANCELLED'); }, cancel() {} }, log);
  await assert.rejects(cancelled.post('https://a.example/v1/chat', {}, ''), /CANCELLED/);
  assert.match(log.events()[0].detail, /^失败 · 模型服务返回 HTTP 401/);
  assert.match(log.events()[1].detail, /^已取消/);
});

test('report lists environment facts and states what was removed', () => {
  const env = { ...emptyEnvironment(), appVersion: '0.9.1', versionCode: '90001', device: 'Phone (X1)', api: '26' };
  const report = diagnosticReport(env, [{ at: 0, area: 'http', stage: 'POST a.example', detail: '成功' }], 0);
  assert.match(report, /应用版本：0\.9\.1（90001）/);
  assert.match(report, /折叠状态：未知/);
  assert.match(report, /\[网络\] POST a\.example · 成功/);
  assert.match(report, /已自动隐去/);
});

test('controller records a chat round, exposes the diagnostics screen and never logs message text or keys', async () => {
  const keys = new Map<string, string>(); let counter = 0;
  const profiles = new ProfileRepository(new Storage(), { put: async (id, key) => { keys.set(id, key); }, get: async id => keys.get(id) || '', remove: async id => { keys.delete(id); } }, () => 'p' + ++counter);
  await profiles.load();
  await profiles.save({ id: '', name: 'Service', baseUrl: 'https://a.example/v1', model: 'alpha', models: ['alpha'], streaming: false, screenshots: false, allowInsecureHttp: false }, 'sk-secretkey-123456789', true);
  const answer = JSON.stringify({ choices: [{ message: { role: 'assistant', content: 'MODEL-ANSWER-TEXT' }, finish_reason: 'stop' }] });
  const diagnostics = new DiagnosticLog(undefined, 100);
  let controller!: FoundationController;
  const chats = new ConversationRepository(new Storage(), () => 'c' + ++counter, Date.now, () => controller?.changed(), 2), drafts = new DraftRepository(new Storage());
  controller = new FoundationController({
    chats, drafts, profiles, diagnostics, uuid: () => 'r' + ++counter,
    transport: () => new DiagnosticTransport({ post: async () => answer, cancel() {} }, diagnostics),
    environment: async () => ({ ...emptyEnvironment(), appVersion: '0.9.1', versionCode: '90001' }),
    load: async () => { await chats.load(); await drafts.load(); }
  });
  await controller.initialize();
  assert.equal(controller.snapshot().appVersion, '0.9.1 (90001)');
  controller.draft('PRIVATE-USER-QUESTION'); await controller.send();
  await controller.runtime.idle();
  await until(() => !controller.snapshot().chat.active);
  assert.equal(controller.snapshot().diagnostics.lines.length, 0, 'event lines are only built on the diagnostics screen');
  controller.foreground(false); controller.foreground(true);
  controller.openDiagnostics(); await until(() => controller.snapshot().diagnostics.total > 0);
  const view = controller.snapshot().diagnostics;
  assert.equal(controller.snapshot().screen, 'diagnostics');
  assert.ok(view.lines.some(l => l.includes('[网络] POST a.example')));
  assert.ok(view.lines.some(l => l.includes('[回复] 回复完成')));
  assert.ok(view.lines[0].includes('回到前台'), 'newest first');
  const report = controller.diagnosticReport();
  for (const leaked of ['PRIVATE-USER-QUESTION', 'MODEL-ANSWER-TEXT', 'sk-secretkey']) assert.equal(report.includes(leaked), false, leaked);
  controller.clearDiagnostics(); assert.equal(controller.snapshot().diagnostics.total, 0);
  assert.equal(controller.back(), true); assert.equal(controller.snapshot().screen, 'chat');
  await controller.flush();
});
