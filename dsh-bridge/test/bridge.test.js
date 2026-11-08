import { test } from 'node:test'
import assert from 'node:assert/strict'
import { Bridge } from '../lib/bridge.js'
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
      follow(_request, signal) { return follow.iterate(signal) },
      observeProjections(_request, signal) { return projections.iterate(signal) },
    },
  }
}

const workspaces = {
  list: () => [{ id: 'w1', title: 'repo', path: '/repo', sessionIds: ['s1'] }],
  archivedSessionIds: [],
  pinnedSessionIds: ['s1'],
}

async function setup() {
  const fake = fakeController()
  const bridge = new Bridge({ controller: fake.controller, workspaces, logger: quiet, version: 'test' })
  const handle = await startServer({ bridge, token: TOKEN, host: '127.0.0.1', port: 0, logger: quiet })
  return { fake, bridge, handle, url: `ws://127.0.0.1:${handle.port}/v1/ws` }
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
  assert.deepEqual(await res.json(), { ok: true, protocol: 1 })
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
