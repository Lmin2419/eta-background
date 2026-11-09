import { test } from 'node:test'
import assert from 'node:assert/strict'
import { Bridge, answerItems, questionItems } from '../lib/bridge.js'
import { startServer } from '../lib/server.js'

const TOKEN = 'test-token-0123456789'
const quiet = { info() {}, warn() {}, error() {} }

/** Push-driven async iterable used to script follow/projection streams. */
function channel() {
  const items = []
  let waiter = null
  let done = false
  return {
    push(item) { items.push(item); waiter?.() },
    end() { done = true; waiter?.() },
    iterate(signal) {
      return (async function* () {
        while (true) {
          while (items.length > 0) yield items.shift()
          if (done || signal.aborted) return
          await new Promise((resolve) => {
            waiter = resolve
            signal.addEventListener('abort', resolve, { once: true })
          })
          waiter = null
        }
      })()
    },
  }
}

function fakeController() {
  const follow = channel()
  const projections = channel()
  const calls = []
  return {
    follow,
    projections,
    calls,
    controller: {
      async list() {
        return { items: [{ sessionId: 's1', updatedAt: 5, running: false, blank: false, agentAvailable: false, projections: { kind: 'cached', asOfSeq: 1, values: { title: 'One' } } }] }
      },
      async create(request) { calls.push(['create', request]); return { sessionId: 's2' } },
      async prompt(request) { calls.push(['prompt', request]); return { accepted: true } },
      cancel(request) { calls.push(['cancel', request]); return { accepted: true } },
      async events(request) {
        calls.push(['events', request])
        return { records: [{ type: 'event', event: { type: 'turn/start', seq: 1, time: 1, data: { turn: 1 } } }], hasMore: false, cursor: 1 }
      },
      async modelCatalog() {
        return { default: { provider: 'deepseek', model: 'chat' }, routableProviders: ['deepseek'], groups: [{ id: 'deepseek', name: 'DeepSeek', models: [{ id: 'chat', name: 'Chat' }] }], failures: [] }
      },
      follow(request, signal) { calls.push(['follow', request]); return follow.iterate(signal) },
      async projections({ sessionId }) {
        if (sessionId === 's1') return { kind: 'sequenced', asOfSeq: 1, values: { subagentCatalog: [{ id: 'c1', mode: 'continuable', label: 'scan', name: 'lister', createdAt: 3 }, { id: 'x', mode: 'external', createdAt: 4 }] } }
        return { kind: 'sequenced', asOfSeq: 1, values: { title: 'child', tokenUsage: { uncachedInputTokens: 1, outputTokens: 2, cacheReadTokens: 3, cacheWriteTokens: 0 }, subagentTiming: { settledMs: 50 } } }
      },
      async state({ sessionId }) { return { cursor: 1, active: true, running: sessionId === 'c1', queue: [], pendingInput: [] } },
      observeProjections(_request, signal) { return projections.iterate(signal) },
    },
  }
}

function fakeWorkspaces() {
  return {
    list: () => [{ id: 'w1', title: 'repo', path: '/repo', sessionIds: ['s1'] }],
    archivedSessionIds: [],
    pinnedSessionIds: ['s1'],
    async archiveSession(id) {
      if (id === 'busy') throw new (class WorkspaceActiveSessionError extends Error {})('active')
      this.pinnedSessionIds = this.pinnedSessionIds.filter(item => item !== id)
      this.archivedSessionIds = [...this.archivedSessionIds, id]
    },
    async unarchiveSession(id) { this.archivedSessionIds = this.archivedSessionIds.filter(item => item !== id) },
    async pinSession(id) { this.pinnedSessionIds = [id, ...this.pinnedSessionIds] },
    async unpinSession(id) { this.pinnedSessionIds = this.pinnedSessionIds.filter(item => item !== id) },
  }
}

async function setup(extra = {}) {
  const fake = fakeController()
  const workspaces = fakeWorkspaces()
  const bridge = new Bridge({ controller: fake.controller, workspaces, logger: quiet, version: 'test', ...extra })
  const handle = await startServer({ bridge, token: TOKEN, host: '127.0.0.1', port: 0, logger: quiet })
  return { fake, bridge, workspaces, handle, url: `ws://127.0.0.1:${handle.port}/v1/ws` }
}

/** Open a client that records every server message. */
function connect(url, token = TOKEN) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url, { headers: { Authorization: `Bearer ${token}` } })
    const messages = []
    const waiters = []
    let seq = 0
    ws.onmessage = (event) => {
      const message = JSON.parse(event.data)
      messages.push(message)
      for (const waiter of [...waiters]) {
        if (waiter.match(message)) {
          waiters.splice(waiters.indexOf(waiter), 1)
          waiter.resolve(message)
        }
      }
    }
    ws.onerror = () => reject(new Error('connect failed'))
    ws.onopen = () => resolve({
      ws,
      messages,
      next(match) {
        const found = messages.find(match)
        if (found) {
          messages.splice(messages.indexOf(found), 1)
          return Promise.resolve(found)
        }
        return new Promise((done, fail) => {
          const timer = setTimeout(() => fail(new Error('timed out waiting for message')), 2000)
          waiters.push({ match, resolve: (m) => { clearTimeout(timer); messages.splice(messages.indexOf(m), 1); done(m) } })
        })
      },
      async call(op, params = {}) {
        const id = String(++seq)
        ws.send(JSON.stringify({ id, op, ...params }))
        const res = await this.next(m => m.t === 'res' && m.id === id)
        if (!res.ok) throw Object.assign(new Error(res.error.message), { code: res.error.code })
        return res.data
      },
    })
  })
}

test('rejects a wrong token', async () => {
  const { handle, url } = await setup()
  await assert.rejects(connect(url, 'wrong-token-xxxxxxxxxx'))
  await handle.close()
})

test('health endpoint is open', async () => {
  const { handle } = await setup()
  const res = await fetch(`http://127.0.0.1:${handle.port}/v1/health`)
  assert.deepEqual(await res.json(), { ok: true, protocol: 1, version: 'test', runtime: 'node' })
  await handle.close()
})

test('list, create, prompt, cancel, models and older history', async () => {
  const { fake, handle, url } = await setup()
  const c = await connect(url)
  const hello = await c.call('hello')
  assert.equal(hello.protocol, 1)
  assert.deepEqual(hello.approvals, [])

  const list = await c.call('sessions.list')
  assert.deepEqual(list.sessions, [{ id: 's1', title: 'One', updatedAt: 5, running: false, blank: false, live: false, workspaceId: 'w1', pinned: true }])
  assert.deepEqual(list.workspaces, [{ id: 'w1', title: 'repo', path: '/repo' }])

  assert.deepEqual(await c.call('sessions.create', { workspaceId: 'w1' }), { sessionId: 's2' })
  const prompt = await c.call('sessions.prompt', { sessionId: 's1', text: 'hi', mode: 'steer', requestId: 'r1' })
  assert.deepEqual(prompt, { accepted: true, requestId: 'r1' })
  await c.call('sessions.cancel', { sessionId: 's1' })
  const older = await c.call('sessions.older', { sessionId: 's1', beforeSeq: 10 })
  assert.deepEqual(older.items, [{ k: 'turn_start', seq: 1, time: 1, turn: 1 }])
  const models = await c.call('models.list')
  assert.equal(models.groups[0].models[0].id, 'chat')

  assert.deepEqual(fake.calls.find(call => call[0] === 'prompt')[1], {
    requestId: 'r1', sessionId: 's1', mode: 'steer', content: [{ type: 'text', text: 'hi' }],
  })
  await assert.rejects(c.call('sessions.prompt', {}), /sessionId is required/)
  await assert.rejects(c.call('nope'), /unknown op/)
  c.ws.close()
  await handle.close()
})

test('follow streams snapshot, live deltas, items and projections', async () => {
  const { fake, handle, url } = await setup()
  const c = await connect(url)
  await c.call('sessions.follow', { sessionId: 's1' })
  fake.follow.push({
    type: 'snapshot',
    header: { cwd: '/repo' },
    cursor: 3,
    hasMore: true,
    records: [
      { type: 'event', event: { type: 'user/message', seq: 2, time: 1, surfaceOp: 'append', data: { id: 'u', content: [{ type: 'text', text: 'q' }], source: { kind: 'user' } } } },
    ],
    projections: { asOfSeq: 3, values: { title: 'One', inbox: { 'next-turn': [], 'next-step': [] } } },
    assistantStream: { revision: 2, activeAttempt: { attemptId: 'a1', startedAfterSeq: 3, turn: 1, step: 1, nextIndex: 1, stream: [{ type: 'text-chunks', time0: 0, index: 0, dt: [0], texts: ['Hel'] }] } },
  })
  const snapshot = await c.next(m => m.t === 'snapshot')
  assert.equal(snapshot.title, 'One')
  assert.equal(snapshot.cwd, '/repo')
  assert.equal(snapshot.items[0].text, 'q')
  assert.deepEqual(snapshot.live, { attemptId: 'a1', turn: 1, step: 1, blocks: [{ index: 0, kind: 'text', text: 'Hel' }] })

  fake.follow.push({ type: 'assistant-stream', frame: { type: 'chunk', attemptId: 'a1', revision: 3, index: 1, time: 2, chunk: { type: 'text-delta', index: 0, text: 'lo' } } })
  fake.follow.push({ type: 'assistant-stream', frame: { type: 'chunk', attemptId: 'a1', revision: 4, index: 2, time: 2, chunk: { type: 'text-delta', index: 0, text: '!' } } })
  const delta = await c.next(m => m.t === 'live' && m.op === 'delta')
  assert.deepEqual(delta.deltas, [{ index: 0, kind: 'text', text: 'lo!' }])

  fake.follow.push({ type: 'event', event: { type: 'assistant/message', seq: 4, time: 3, data: { turn: 1, step: 1, message: { id: 'm', content: [{ type: 'text', text: 'Hello!' }] }, stream: [] } } })
  fake.follow.push({ type: 'assistant-stream', frame: { type: 'end', attemptId: 'a1', revision: 5, index: 3, outcome: { kind: 'committed', eventType: 'assistant/message', seq: 4 } } })
  const item = await c.next(m => m.t === 'item')
  assert.equal(item.item.blocks[0].text, 'Hello!')
  const end = await c.next(m => m.t === 'live' && m.op === 'end')
  assert.deepEqual({ committed: end.committed, seq: end.seq }, { committed: true, seq: 4 })

  // Projection at the snapshot cut is dropped, a later one is forwarded.
  fake.projections.push({ type: 'projection', seq: 3, changes: [{ key: 'title', val: 'stale' }] })
  fake.projections.push({ type: 'projection', seq: 5, changes: [{ key: 'title', val: 'Two' }, { key: 'inbox', val: { 'next-turn': [{ id: 'q', content: [{ type: 'text', text: 'more' }] }], 'next-step': [] } }] })
  const projection = await c.next(m => m.t === 'projection')
  assert.equal(projection.title, 'Two')
  assert.deepEqual(projection.queue, [{ id: 'q', text: 'more', placement: 'queued' }])
  c.ws.close()
  await handle.close()
})

test('follow resumes from afterSeq without resending held items', async () => {
  const { fake, handle, url } = await setup()
  const c = await connect(url)
  await c.call('sessions.follow', { sessionId: 's1', afterSeq: 2 })
  fake.follow.push({
    type: 'snapshot', header: {}, cursor: 3, hasMore: false,
    records: [
      { type: 'event', event: { type: 'turn/start', seq: 1, time: 1, data: { turn: 1 } } },
      { type: 'event', event: { type: 'user/message', seq: 2, time: 1, data: { id: 'u', content: [{ type: 'text', text: 'old' }], source: { kind: 'user' } } } },
      { type: 'event', event: { type: 'turn/end', seq: 3, time: 1, data: { turn: 1, reason: { kind: 'completed' } } } },
    ],
    projections: { asOfSeq: 3, values: {} },
  })
  const snapshot = await c.next(m => m.t === 'snapshot')
  assert.equal(snapshot.resumed, true)
  assert.deepEqual(snapshot.items.map(i => i.seq), [3])
  c.ws.close()
  await handle.close()
})

test('approval goes to phone and desktop; phone answer wins and withdraws desktop prompt', async () => {
  const { bridge, handle, url } = await setup()
  const c = await connect(url)
  await c.call('hello')
  let downstreamSignal
  const next = () => new Promise((_resolve, reject) => {
    downstreamSignal = request.signal
    request.signal.addEventListener('abort', () => reject(new Error('withdrawn')), { once: true })
  })
  const original = new AbortController()
  const request = { agent: { id: 's1', session: { id: 's1' } }, toolName: 'bash', callId: 'c1', reason: 'rm -rf', signal: original.signal }
  const pending = bridge.requestApproval(request, next)
  const ask = await c.next(m => m.t === 'approval')
  assert.equal(ask.approval.toolName, 'bash')
  assert.equal(ask.approval.sessionId, 's1')
  await c.call('approval.answer', { approvalId: ask.approval.id, outcome: 'allowed-once' })
  assert.equal(await pending, 'allowed-once')
  assert.equal(downstreamSignal.aborted, true)
  const done = await c.next(m => m.t === 'approval-done')
  assert.equal(done.outcome, 'allowed-once')
  assert.equal(original.signal.aborted, false)
  c.ws.close()
  await handle.close()
})

test('approval answered on desktop clears the phone card', async () => {
  const { bridge, handle, url } = await setup()
  const c = await connect(url)
  await c.call('hello')
  const request = { agent: { id: 's1', session: { id: 's1' } }, toolName: 'bash' }
  const pending = bridge.requestApproval(request, () => Promise.resolve('rejected'))
  assert.equal(await pending, 'rejected')
  await c.next(m => m.t === 'approval')
  const done = await c.next(m => m.t === 'approval-done')
  assert.equal(done.outcome, 'rejected')
  assert.equal(bridge.pendingApprovals().length, 0)
  c.ws.close()
  await handle.close()
})

test('approval without a phone passes straight through', async () => {
  const { bridge, handle } = await setup()
  assert.equal(await bridge.requestApproval({ agent: { id: 's' }, toolName: 'x' }, () => Promise.resolve('rejected')), 'rejected')
  // With no phone a downstream failure is DSH's own outcome, not a wait for a phone.
  await assert.rejects(bridge.requestApproval({ agent: { id: 's' }, toolName: 'x' }, () => Promise.reject(new Error('no ui'))), /no ui/)
  assert.equal(bridge.pendingApprovals().length, 0)
  await handle.close()
})

test('an approval raised before the phone connects is offered in hello', async () => {
  const { bridge, handle, url } = await setup()
  const pending = bridge.requestApproval({ agent: { id: 's1' }, toolName: 'bash' }, () => new Promise(() => {}))
  const c = await connect(url)
  const hello = await c.call('hello')
  assert.equal(hello.runtime, 'node')
  assert.equal(hello.approvals.length, 1)
  await c.call('approval.answer', { approvalId: hello.approvals[0].id, outcome: 'rejected' })
  assert.equal(await pending, 'rejected')
  c.ws.close()
  await handle.close()
})

test('questions race phone and desktop; the phone answer is normalised per question', async () => {
  const { bridge, handle, url } = await setup()
  const c = await connect(url)
  await c.call('hello')
  let withdrawn = false
  const request = {
    agent: { id: 's1', session: { id: 's1' } },
    wait: { callId: 'call-1', timed: true },
    questions: [
      { id: 'q1', question: 'Which?', options: [{ label: 'A' }, { label: 'B' }] },
      { id: 'q2', question: 'Also?', options: [{ label: 'X' }, { label: 'Y' }], multiSelect: true },
      { id: 'plan', question: 'Approve plan?', detail: '# Plan', options: [{ label: 'Approve' }, { label: 'Revise' }], intent: { kind: 'plan-review', approve: 'Approve' } },
    ],
  }
  const next = () => new Promise((_resolve, reject) => {
    request.signal.addEventListener('abort', () => { withdrawn = true; reject(new Error('withdrawn')) }, { once: true })
  })
  const pending = bridge.requestQuestion(request, next)
  const ask = await c.next(m => m.t === 'question')
  assert.equal(ask.question.callId, 'call-1')
  assert.equal(ask.question.timed, true)
  assert.equal(ask.question.questions[2].intent.kind, 'plan-review')
  await c.call('question.answer', {
    questionId: ask.question.id,
    answers: [{ id: 'q1', selected: ['B', 'A'] }, { id: 'q2', selected: ['X', 'Z', 'Y'], custom: 'more' }],
  })
  assert.deepEqual(await pending, {
    answers: [{ id: 'q1', selected: ['B'] }, { id: 'q2', selected: ['X', 'Y'], custom: 'more' }, { id: 'plan', selected: [] }],
  })
  assert.equal(withdrawn, true)
  assert.equal((await c.next(m => m.t === 'question-done')).outcome, 'answered')
  c.ws.close()
  await handle.close()
})

test('a desktop countdown that ends is passed through as a timeout', async () => {
  const { bridge, handle, url } = await setup()
  const c = await connect(url)
  await c.call('hello')
  const timeout = Object.assign(new Error('timed out'), { code: 'ASK_TIMED_OUT' })
  await assert.rejects(bridge.requestQuestion({ agent: { id: 's1' }, questions: [{ id: 'q', question: '?' }] }, () => Promise.reject(timeout)), /timed out/)
  assert.equal((await c.next(m => m.t === 'question-done')).outcome, 'timeout')
  await c.next(m => m.t === 'question')
  // With a phone connected, a missing desktop answerer leaves the phone to answer.
  const noUi = Object.assign(new Error('none'), { code: 'NO_PROVIDER' })
  const pending = bridge.requestQuestion({ agent: { id: 's1' }, questions: [{ id: 'q', question: '?', options: [{ label: 'ok' }] }] }, () => Promise.reject(noUi))
  const ask = await c.next(m => m.t === 'question')
  await c.call('question.answer', { questionId: ask.question.id, answers: [{ id: 'q', selected: ['ok'] }] })
  assert.deepEqual(await pending, { answers: [{ id: 'q', selected: ['ok'] }] })
  c.ws.close()
  await handle.close()
})

test('single-select custom text replaces the choice', () => {
  const questions = questionItems([{ id: 'q', question: '?', options: [{ label: 'a' }] }, { question: 'no id' }, null])
  assert.equal(questions.length, 1)
  assert.deepEqual(answerItems([{ id: 'q', selected: ['a'], custom: '  mine ' }], questions), [{ id: 'q', selected: [], custom: 'mine' }])
})

test('archive, unarchive, pin and unpin go through the workspace registry', async () => {
  const { handle, url, workspaces } = await setup()
  const c = await connect(url)
  await c.call('hello')
  assert.deepEqual(await c.call('sessions.archive', { sessionId: 's1' }), { archived: true, pinned: false })
  assert.deepEqual(await c.next(m => m.t === 'session-flags'), { t: 'session-flags', sessionId: 's1', archived: true, pinned: false })
  const list = await c.call('sessions.list')
  assert.equal(list.sessions[0].archived, true)
  assert.equal(list.sessions[0].pinned, undefined)
  assert.deepEqual(await c.call('sessions.unarchive', { sessionId: 's1' }), { archived: false, pinned: false })
  assert.deepEqual(await c.call('sessions.pin', { sessionId: 's1' }), { archived: false, pinned: true })
  assert.deepEqual(await c.call('sessions.unpin', { sessionId: 's1' }), { archived: false, pinned: false })
  assert.deepEqual(workspaces.pinnedSessionIds, [])
  await assert.rejects(c.call('sessions.archive', { sessionId: 'busy' }), err => err.code === 'busy')
  c.ws.close()
  await handle.close()
})

test('turn ends are announced with the last assistant text; subagents stay quiet', async () => {
  const { bridge, handle, url } = await setup()
  const c = await connect(url)
  await c.call('hello')
  const session = { id: 's1', header: {} }
  bridge.onSessionEvent(session, { type: 'turn/start', data: { turn: 2 } })
  bridge.onSessionEvent(session, { type: 'assistant/message', data: { message: { content: [{ type: 'text', text: 'All done.' }] } } })
  bridge.onSessionEvent({ id: 'c1', header: { origin: 'subagent' } }, { type: 'turn/end', data: { turn: 1, reason: { kind: 'completed' } } })
  bridge.onSessionEvent(session, { type: 'turn/end', time: 9, data: { turn: 2, reason: { kind: 'completed' } } })
  assert.deepEqual(await c.next(m => m.t === 'turn'), { t: 'turn', sessionId: 's1', turn: 2, reason: 'completed', text: 'All done.', time: 9 })
  assert.equal(c.messages.some(m => m.t === 'turn' && m.sessionId === 'c1'), false)
  c.ws.close()
  await handle.close()
})

test('a client can lengthen its heartbeat within bounds', async () => {
  const { handle, url } = await setup()
  const c = await connect(url)
  assert.deepEqual(await c.call('client.heartbeat', { intervalMs: 60_000 }), { intervalMs: 60_000 })
  assert.deepEqual(await c.call('client.heartbeat', { intervalMs: 1 }), { intervalMs: 10_000 })
  assert.deepEqual(await c.call('client.heartbeat', { intervalMs: 10_000_000 }), { intervalMs: 120_000 })
  c.ws.close()
  await handle.close()
})

test('status and session list events are broadcast', async () => {
  const { bridge, handle, url } = await setup()
  const c = await connect(url)
  await c.call('hello')
  bridge.onStatus('s1', true)
  assert.deepEqual(await c.next(m => m.t === 'status'), { t: 'status', sessionId: 's1', running: true })
  bridge.onSessionAdded({ sessionId: 's3', updatedAt: 1, running: false, blank: true, agentAvailable: true })
  const added = await c.next(m => m.t === 'session')
  assert.equal(added.session.id, 's3')
  c.ws.close()
  await handle.close()
})

test('subagent directory and child follow address', async () => {
  const { fake, handle, url } = await setup()
  const c = await connect(url)
  const dir = await c.call('sessions.subagents', { sessionId: 's1' })
  assert.deepEqual(dir.children, [
    { id: 'c1', mode: 'continuable', createdAt: 3, label: 'scan', name: 'lister', title: 'child', usage: { uncachedInputTokens: 1, outputTokens: 2, cacheReadTokens: 3, cacheWriteTokens: 0 }, timing: { settledMs: 50 }, running: true },
    { id: 'x', mode: 'external', createdAt: 4 },
  ])
  await c.call('sessions.follow', { sessionId: 'c1', parentId: 's1', mode: 'continuable' })
  const follow = fake.calls.find(call => call[0] === 'follow')[1]
  assert.deepEqual(follow.address, { kind: 'subagent', parentSessionId: 's1', childSessionId: 'c1', mode: 'continuable' })
  await assert.rejects(c.call('subagents.interrupt', { childSessionId: 'c1' }), /not available/)
  c.ws.close()
  await handle.close()
})

test('snapshot forwards stats, usage, pressure and subagent projections', async () => {
  const { fake, handle, url } = await setup()
  const c = await connect(url)
  await c.call('sessions.follow', { sessionId: 's1' })
  fake.follow.push({ type: 'snapshot', header: {}, cursor: 1, hasMore: false, records: [], projections: { asOfSeq: 1, values: {
    sessionStats: { turns: 1 }, tokenUsage: { outputTokens: 2 }, contextPressure: { contextWindow: 9 }, subagentCatalog: [{ id: 'c' }],
  } } })
  const snap = await c.next(m => m.t === 'snapshot')
  assert.deepEqual([snap.stats, snap.usage, snap.pressure, snap.subagents, snap.timing], [{ turns: 1 }, { outputTokens: 2 }, { contextWindow: 9 }, [{ id: 'c' }], null])
  fake.projections.push({ type: 'projection', seq: 5, changes: [{ key: 'tokenUsage', val: { outputTokens: 7 } }] })
  const update = await c.next(m => m.t === 'projection')
  assert.deepEqual(update.usage, { outputTokens: 7 })
  c.ws.close()
  await handle.close()
})
