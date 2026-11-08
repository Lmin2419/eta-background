import { test } from 'node:test'
import assert from 'node:assert/strict'
import { LiveAttempt, inboxItems, sessionRow, translateEvent, translateRecords } from '../lib/translate.js'

test('user message keeps text, attachments and rpc id', () => {
  const item = translateEvent({
    type: 'user/message',
    seq: 7,
    time: 100,
    surfaceOp: 'append',
    data: {
      id: 'm1',
      role: 'user',
      content: [
        { type: 'text', text: 'hello' },
        { type: 'image', attachment: {} },
        { type: 'file', attachment: { name: 'a.txt' } },
      ],
      source: { kind: 'user', rpcId: 'r1' },
    },
  })
  assert.deepEqual(item, {
    k: 'user', seq: 7, time: 100, id: 'm1', text: 'hello', images: 1, files: ['a.txt'], source: 'user', rpcId: 'r1',
  })
})

test('harness-injected context messages are hidden', () => {
  for (const kind of ['runtime-context', 'time-context']) {
    assert.equal(translateEvent({
      type: 'user/message', seq: 1, time: 1, data: { id: 'x', content: [{ type: 'text', text: 'ctx' }], source: { kind } },
    }), null)
  }
  assert.equal(translateEvent({
    type: 'user/message', seq: 2, time: 1, data: { id: 'y', content: [], source: { kind: 'retry', rpcId: 'r' } },
  }).source, 'retry')
})

test('replacement surface events are skipped', () => {
  const item = translateEvent({
    type: 'user/message', seq: 9, time: 1, surfaceOp: { op: 'replace', startSeq: 1, endSeq: 2 },
    data: { id: 'x', content: [], source: { kind: 'user' } },
  })
  assert.equal(item, null)
})

test('assistant message maps reasoning, text, tool calls and usage', () => {
  const item = translateEvent({
    type: 'assistant/message',
    seq: 10,
    time: 5,
    data: {
      turn: 1,
      step: 2,
      message: {
        id: 'a1',
        role: 'assistant',
        content: [
          { type: 'reasoning', text: 'think' },
          { type: 'text', text: 'answer' },
          { type: 'tool-call', id: 'c1', name: 'bash', arguments: '{"cmd":"ls"}' },
        ],
      },
      stream: [],
      usage: { inputTokens: 10, outputTokens: 3, cacheReadTokens: 8, reasoningTokens: 1 },
      interrupted: true,
    },
  })
  assert.deepEqual(item, {
    k: 'assistant',
    seq: 10,
    time: 5,
    id: 'a1',
    turn: 1,
    step: 2,
    blocks: [
      { type: 'reasoning', text: 'think' },
      { type: 'text', text: 'answer' },
      { type: 'tool', id: 'c1', name: 'bash', args: '{"cmd":"ls"}' },
    ],
    usage: { input: 10, output: 3, cacheRead: 8, reasoning: 1 },
    interrupted: true,
  })
})

test('tool result is clipped and carries error identity', () => {
  const item = translateEvent({
    type: 'tool/result',
    seq: 11,
    time: 6,
    data: {
      message: { toolCallId: 'c1', isError: true, content: [{ type: 'text', text: 'x'.repeat(7000) }] },
      error: { name: 'E', code: 'EXIT', reason: 'exit 1' },
    },
  })
  assert.equal(item.k, 'tool_result')
  assert.equal(item.callId, 'c1')
  assert.equal(item.isError, true)
  assert.equal(item.text.length, 6000)
  assert.equal(item.truncated, true)
  assert.deepEqual(item.error, { code: 'EXIT', reason: 'exit 1' })
})

test('turn end reports reason, cause and failure message', () => {
  assert.deepEqual(
    translateEvent({ type: 'turn/end', seq: 3, time: 1, data: { turn: 1, reason: { kind: 'aborted', reason: { kind: 'user' } } } }),
    { k: 'turn_end', seq: 3, time: 1, turn: 1, reason: 'aborted', cause: 'user' },
  )
  assert.deepEqual(
    translateEvent({ type: 'turn/end', seq: 4, time: 1, data: { turn: 2, reason: { kind: 'error', error: { message: 'boom', code: 'X' } } } }),
    { k: 'turn_end', seq: 4, time: 1, turn: 2, reason: 'error', message: 'boom' },
  )
})

test('unknown and log-only events produce nothing', () => {
  assert.deepEqual(translateRecords([
    { type: 'event', event: { type: 'request/header', seq: 1, time: 1, data: {} } },
    { type: 'event', event: { type: 'step/end', seq: 2, time: 1, data: {} } },
    { type: 'event', event: { type: 'session/title', seq: 3, time: 1, data: { title: 'T' } } },
  ]), [{ k: 'title', seq: 3, time: 1, title: 'T' }])
})

test('live attempt coalesces deltas and rebuilds from compact records', () => {
  const live = new LiveAttempt('att')
  live.applyChunk({ type: 'block-start', index: 0, blockType: 'reasoning' })
  live.applyChunk({ type: 'reasoning-delta', index: 0, text: 'a' })
  live.applyChunk({ type: 'reasoning-delta', index: 0, text: 'b' })
  live.applyChunk({ type: 'text-delta', index: 1, text: 'hi' })
  assert.deepEqual(live.drain(), [
    { index: 0, kind: 'reasoning', text: 'ab' },
    { index: 1, kind: 'text', text: 'hi' },
  ])
  assert.deepEqual(live.drain(), [])
  live.applyChunk({ type: 'tool-call-delta', index: 2, id: 'c', name: 'bash', argumentsDelta: '{"a"' })
  live.applyChunk({ type: 'tool-call-delta', index: 2, id: 'c', argumentsDelta: ':1}' })
  assert.deepEqual(live.drain(), [{ index: 2, kind: 'tool', text: '{"a":1}', id: 'c', name: 'bash' }])

  const rebuilt = new LiveAttempt('att2')
  rebuilt.applyRecord({ type: 'reasoning-chunks', time0: 0, index: 0, dt: [0, 1], texts: ['a', 'b'] })
  rebuilt.applyRecord({ type: 'text-chunks', time0: 0, index: 1, dt: [0], texts: ['hi'] })
  rebuilt.applyRecord({ type: 'chunk', time: 1, chunk: { type: 'text-delta', index: 1, text: '!' } })
  assert.deepEqual(rebuilt.snapshot(), [
    { index: 0, kind: 'reasoning', text: 'ab' },
    { index: 1, kind: 'text', text: 'hi!' },
  ])
})

test('inbox projection becomes queued and steering items', () => {
  assert.deepEqual(inboxItems({
    'next-turn': [{ id: 'q1', content: [{ type: 'text', text: 'later' }] }],
    'next-step': [{ message: { id: 'q2', content: [{ type: 'text', text: 'now' }] } }],
  }), [
    { id: 'q1', text: 'later', placement: 'queued' },
    { id: 'q2', text: 'now', placement: 'steering' },
  ])
})

test('session row reads title projection and workspace', () => {
  const row = sessionRow({
    sessionId: 's1', updatedAt: 9, running: true, blank: false, agentAvailable: true, cwd: '/w',
    projections: { kind: 'sequenced', asOfSeq: 3, values: { title: 'Fix bug' } },
  }, new Map([['s1', { id: 'w1', title: 'repo' }]]))
  assert.deepEqual(row, {
    id: 's1', title: 'Fix bug', updatedAt: 9, running: true, blank: false, live: true, cwd: '/w', workspaceId: 'w1',
  })
})
