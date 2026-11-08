// Phone connection handling: request/response ops, Session follows with live
// assistant streaming, global Session list updates, and tool approvals.
// `host` is the narrow slice of DSH services the bridge uses, so tests can fake it.

import { randomUUID } from 'node:crypto'
import { hostname, platform } from 'node:os'
import {
  LiveAttempt, inboxItems, sessionRow, translateEvent, translateRecords,
} from './translate.js'

export const PROTOCOL_VERSION = 1
const DELTA_FLUSH_MS = 30
const BACKPRESSURE_BYTES = 1 << 20
const OP_TIMEOUT_MS = 30_000
const FOLLOW_WINDOW = { maxMessages: 200, turnWindow: { minMessages: 30, minTurns: 2 } }
const OLDER_WINDOW = { maxMessages: 200, turnWindow: { minMessages: 40, minTurns: 2 } }
const APPROVAL_OUTCOMES = new Set(['allowed-once', 'rejected', 'cancelled'])

/**
 * @typedef {object} BridgeHost
 * @property {any} controller - DSH `sessionController` service.
 * @property {any} [workspaces] - DSH `workspaceRegistry` service.
 * @property {{ info: Function, warn: Function, error: Function }} logger
 * @property {string} version - bridge package version.
 */

class BridgeError extends Error {
  /** @param {string} code @param {string} message */
  constructor(code, message) {
    super(message)
    this.code = code
  }
}

/** @param {unknown} value @param {string} name */
function requireString(value, name) {
  if (typeof value !== 'string' || value.length === 0) throw new BridgeError('bad-request', `${name} is required`)
  return value
}

/** @param {unknown} error */
function errorPayload(error) {
  if (error instanceof BridgeError) return { code: error.code, message: error.message }
  const code = typeof error?.code === 'string' ? error.code : 'internal'
  const message = error instanceof Error ? error.message : String(error)
  return { code, message }
}

export class Bridge {
  /** @param {BridgeHost} host */
  constructor(host) {
    this.host = host
    /** @type {Set<Client>} */
    this.clients = new Set()
    /** @type {Map<string, any>} */
    this.approvals = new Map()
  }

  /**
   * Adopt one authenticated WebSocket connection.
   * @param {import('./ws.js').WsConnection} conn
   */
  attach(conn) {
    const client = new Client(this, conn)
    this.clients.add(client)
    conn.on('close', () => {
      this.clients.delete(client)
      client.dispose()
    })
    return client
  }

  /** @param {object} message */
  broadcast(message) {
    const text = JSON.stringify(message)
    for (const client of this.clients) client.conn.sendText(text)
  }

  // ---- Host event hooks -------------------------------------------------

  /** @param {any} summary */
  onSessionAdded(summary) {
    if (this.clients.size === 0) return
    try {
      this.broadcast({ t: 'session', session: sessionRow(summary, this.workspaceIndex()) })
    } catch (error) {
      this.host.logger.warn(`dsh-bridge: session row failed: ${error}`)
    }
  }

  /** @param {string} sessionId @param {boolean} running */
  onStatus(sessionId, running) {
    if (this.clients.size > 0) this.broadcast({ t: 'status', sessionId: String(sessionId), running })
  }

  /** @param {string} sessionId @param {number} updatedAt */
  onActivity(sessionId, updatedAt) {
    if (this.clients.size > 0) this.broadcast({ t: 'activity', sessionId: String(sessionId), updatedAt })
  }

  /** @param {string} sessionId @param {string} message */
  onSessionError(sessionId, message) {
    if (this.clients.size > 0) this.broadcast({ t: 'error', sessionId: String(sessionId), message })
  }

  /**
   * `approval/request` waterfall listener. With a phone connected, the request goes to
   * the phone and to the next answerer (the desktop Web UI) at the same time; the first
   * answer wins and the other side's prompt is withdrawn.
   * @param {any} request
   * @param {() => Promise<string>} next
   * @returns {Promise<string>}
   */
  async requestApproval(request, next) {
    if (this.clients.size === 0) return next()
    const sessionId = String(request.agent?.session?.id ?? request.agent?.id ?? '')
    const id = randomUUID()
    /** @type {(outcome: string) => void} */
    let resolvePhone = () => {}
    const phoneAnswer = new Promise((resolve) => { resolvePhone = resolve })
    const entry = {
      id,
      sessionId,
      toolName: String(request.toolName ?? ''),
      ...(request.callId === undefined ? {} : { callId: String(request.callId) }),
      ...(request.reason === undefined ? {} : { reason: String(request.reason) }),
      ...(request.displayReason === undefined ? {} : { displayReason: request.displayReason }),
      createdAt: Date.now(),
    }
    this.approvals.set(id, { entry, resolve: resolvePhone })
    this.broadcast({ t: 'approval', approval: entry })

    const original = request.signal
    const onAbort = () => resolvePhone('cancelled')
    original?.addEventListener?.('abort', onAbort, { once: true })
    if (original?.aborted) resolvePhone('cancelled')

    // Give the downstream answerer a signal we can abort when the phone answers first.
    const withdraw = new AbortController()
    let downstream = null
    try {
      const combined = original === undefined ? withdraw.signal : AbortSignal.any([original, withdraw.signal])
      Object.defineProperty(request, 'signal', { value: combined, configurable: true, enumerable: true, writable: true })
      downstream = Promise.resolve().then(() => next())
      downstream.catch(() => {})
    } catch {
      downstream = null // frozen request: the phone answers alone.
    }

    let outcome
    try {
      outcome = await (downstream === null
        ? phoneAnswer
        : Promise.race([
          phoneAnswer.then(value => ({ from: 'phone', value })),
          downstream.then(value => ({ from: 'desktop', value }), () => ({ from: 'desktop', value: 'unavailable' })),
        ]).then(async (winner) => {
          if (winner.from === 'desktop' && winner.value === 'unavailable') return phoneAnswer
          return winner.value
        }))
    } finally {
      original?.removeEventListener?.('abort', onAbort)
    }
    withdraw.abort(new Error('dsh-bridge: answered elsewhere'))
    this.finishApproval(id, outcome)
    return outcome
  }

  /** @param {string} id @param {string} outcome */
  answerApproval(id, outcome) {
    if (!APPROVAL_OUTCOMES.has(outcome)) throw new BridgeError('bad-request', 'invalid approval outcome')
    const pending = this.approvals.get(id)
    if (pending === undefined) return false
    pending.resolve(outcome)
    return true
  }

  /** @param {string} id @param {string} outcome */
  finishApproval(id, outcome) {
    if (!this.approvals.delete(id)) return
    this.broadcast({ t: 'approval-done', approvalId: id, outcome })
  }

  pendingApprovals() {
    return [...this.approvals.values()].map(pending => pending.entry)
  }

  // ---- Helpers ----------------------------------------------------------

  workspaceIndex() {
    const index = new Map()
    const registry = this.host.workspaces
    if (registry === undefined) return index
    for (const workspace of registry.list()) {
      for (const sessionId of workspace.sessionIds) index.set(String(sessionId), { id: String(workspace.id), title: workspace.title })
    }
    return index
  }

  dispose() {
    for (const client of this.clients) client.conn.close(1001, 'bridge stopping')
    for (const pending of this.approvals.values()) pending.resolve('cancelled')
  }
}

class Client {
  /**
   * @param {Bridge} bridge
   * @param {import('./ws.js').WsConnection} conn
   */
  constructor(bridge, conn) {
    this.bridge = bridge
    this.conn = conn
    this.lifetime = new AbortController()
    /** @type {Map<string, Follow>} */
    this.follows = new Map()
    conn.on('message', text => { void this.onMessage(text) })
  }

  get host() {
    return this.bridge.host
  }

  /** @param {object} message */
  send(message) {
    this.conn.sendText(JSON.stringify(message))
  }

  dispose() {
    this.lifetime.abort()
    for (const follow of this.follows.values()) follow.stop()
    this.follows.clear()
  }

  opSignal() {
    return AbortSignal.any([this.lifetime.signal, AbortSignal.timeout(OP_TIMEOUT_MS)])
  }

  /** @param {string} text */
  async onMessage(text) {
    let message
    try {
      message = JSON.parse(text)
    } catch {
      this.send({ t: 'res', id: null, ok: false, error: { code: 'bad-json', message: 'invalid JSON' } })
      return
    }
    const id = message?.id ?? null
    try {
      const data = await this.dispatch(message)
      this.send({ t: 'res', id, ok: true, data: data ?? null })
    } catch (error) {
      if (!(error instanceof BridgeError)) this.host.logger.warn(`dsh-bridge: op ${message?.op} failed: ${error?.stack ?? error}`)
      this.send({ t: 'res', id, ok: false, error: errorPayload(error) })
    }
  }

  /** @param {any} m */
  async dispatch(m) {
    const controller = this.host.controller
    switch (m?.op) {
      case 'ping':
        return { now: Date.now() }
      case 'hello':
        return {
          server: 'dsh-bridge',
          protocol: PROTOCOL_VERSION,
          version: this.host.version,
          host: hostname(),
          platform: platform(),
          approvals: this.bridge.pendingApprovals(),
        }
      case 'sessions.list':
        return this.listSessions()
      case 'sessions.search': {
        const result = await controller.search({ query: requireString(m.query, 'query') }, this.opSignal())
        return { items: result.items.map(item => ({ sessionId: String(item.sessionId), snippet: item.snippet })), hasMore: result.hasMore }
      }
      case 'sessions.create': {
        const request = {}
        if (typeof m.workspaceId === 'string') request.workspaceId = m.workspaceId
        else if (typeof m.cwd === 'string') request.cwd = m.cwd
        if (typeof m.agentPreset === 'string') request.agentPreset = m.agentPreset
        const created = await controller.create(request)
        return { sessionId: String(created.sessionId) }
      }
      case 'sessions.prompt':
        return this.prompt(m)
      case 'sessions.cancel':
        await controller.cancel({ sessionId: requireString(m.sessionId, 'sessionId') })
        return { accepted: true }
      case 'sessions.rename':
        return controller.rename({ sessionId: requireString(m.sessionId, 'sessionId'), title: requireString(m.title, 'title') })
      case 'sessions.queue':
        await controller.updateQueue({
          sessionId: requireString(m.sessionId, 'sessionId'),
          itemId: requireString(m.itemId, 'itemId'),
          action: queueAction(m.action),
        })
        return { accepted: true }
      case 'sessions.follow':
        this.follow(requireString(m.sessionId, 'sessionId'), typeof m.afterSeq === 'number' ? m.afterSeq : undefined)
        return { following: true }
      case 'sessions.unfollow': {
        const sessionId = requireString(m.sessionId, 'sessionId')
        this.follows.get(sessionId)?.stop()
        this.follows.delete(sessionId)
        return { following: false }
      }
      case 'sessions.older': {
        const sessionId = requireString(m.sessionId, 'sessionId')
        if (typeof m.beforeSeq !== 'number') throw new BridgeError('bad-request', 'beforeSeq is required')
        const page = await controller.events({
          address: { kind: 'session', sessionId },
          beforeSeq: m.beforeSeq,
          ...OLDER_WINDOW,
        }, this.opSignal())
        return { items: translateRecords(page.records), hasMore: page.hasMore, cursor: page.cursor }
      }
      case 'models.list':
        return modelCatalog(await controller.modelCatalog())
      case 'sessions.selectModel': {
        const request = {
          sessionId: requireString(m.sessionId, 'sessionId'),
          provider: requireString(m.provider, 'provider'),
          model: requireString(m.model, 'model'),
          ...(typeof m.reasoningEffort === 'string' ? { reasoningEffort: m.reasoningEffort } : {}),
        }
        const result = await controller.selectModel(request)
        return { selected: result.selected }
      }
      case 'approval.answer':
        return { accepted: this.bridge.answerApproval(requireString(m.approvalId, 'approvalId'), requireString(m.outcome, 'outcome')) }
      default:
        throw new BridgeError('unknown-op', `unknown op ${JSON.stringify(m?.op)}`)
    }
  }

  async listSessions() {
    const { items } = await this.host.controller.list({}, this.opSignal())
    const registry = this.host.workspaces
    const archived = new Set((registry?.archivedSessionIds ?? []).map(String))
    const pinned = new Set((registry?.pinnedSessionIds ?? []).map(String))
    const index = this.bridge.workspaceIndex()
    const sessions = []
    for (const summary of items) {
      const id = String(summary.sessionId)
      if (archived.has(id)) continue
      const row = sessionRow(summary, index)
      if (pinned.has(id)) row.pinned = true
      sessions.push(row)
    }
    const workspaces = registry === undefined ? [] : registry.list().map(workspace => ({
      id: String(workspace.id),
      title: workspace.title,
      path: workspace.path,
    }))
    return { sessions, workspaces }
  }

  /** @param {any} m */
  async prompt(m) {
    const sessionId = requireString(m.sessionId, 'sessionId')
    const content = []
    if (typeof m.text === 'string' && m.text.trim().length > 0) content.push({ type: 'text', text: m.text })
    if (Array.isArray(m.images)) {
      for (const image of m.images) {
        if (typeof image?.data !== 'string' || typeof image?.mediaType !== 'string') continue
        content.push({ type: 'image', mediaType: image.mediaType, data: image.data, ...(typeof image.name === 'string' ? { name: image.name } : {}) })
      }
    }
    const request = {
      requestId: typeof m.requestId === 'string' && m.requestId.length > 0 ? m.requestId : randomUUID(),
      sessionId,
      mode: m.mode === 'steer' ? 'steer' : 'queue',
      content,
      ...(typeof m.timeZone === 'string' ? { clientTimeZone: m.timeZone } : {}),
    }
    await this.host.controller.prompt(request, this.opSignal())
    return { accepted: true, requestId: request.requestId }
  }

  /** @param {string} sessionId @param {number | undefined} afterSeq */
  follow(sessionId, afterSeq) {
    this.follows.get(sessionId)?.stop()
    const follow = new Follow(this, sessionId)
    this.follows.set(sessionId, follow)
    void follow.run(afterSeq).catch((error) => {
      if (follow.stopped) return
      this.send({ t: 'follow-error', sessionId, error: errorPayload(error) })
      if (this.follows.get(sessionId) === follow) this.follows.delete(sessionId)
    })
  }
}

class Follow {
  /** @param {Client} client @param {string} sessionId */
  constructor(client, sessionId) {
    this.client = client
    this.sessionId = sessionId
    this.abort = new AbortController()
    this.signal = AbortSignal.any([client.lifetime.signal, this.abort.signal])
    /** @type {LiveAttempt | null} */
    this.live = null
    this.flushTimer = null
    this.cursor = -1
    this.snapshotCursor = -1
    this.stopped = false
    /** @type {any[] | null} projection frames waiting for the snapshot cursor */
    this.heldProjections = []
  }

  stop() {
    this.stopped = true
    this.abort.abort()
    if (this.flushTimer !== null) clearTimeout(this.flushTimer)
    this.flushTimer = null
  }

  /** @param {object} message */
  send(message) {
    if (!this.stopped) this.client.send(message)
  }

  /** @param {number | undefined} afterSeq */
  async run(afterSeq) {
    const controller = this.client.host.controller
    void this.observeProjections()
    const frames = controller.follow({
      address: { kind: 'session', sessionId: this.sessionId },
      assistantStream: true,
      ...FOLLOW_WINDOW,
    }, this.signal)
    for await (const frame of frames) {
      if (this.stopped) return
      switch (frame.type) {
        case 'snapshot':
          this.onSnapshot(frame, afterSeq)
          break
        case 'event': {
          this.flush()
          if (typeof frame.event?.seq === 'number') this.cursor = Math.max(this.cursor, frame.event.seq)
          const item = translateEvent(frame.event)
          if (item !== null) this.send({ t: 'item', sessionId: this.sessionId, item })
          break
        }
        case 'assistant-stream':
          this.onAssistantFrame(frame.frame)
          break
        default:
      }
    }
    if (!this.stopped) this.send({ t: 'follow-end', sessionId: this.sessionId })
  }

  /** @param {any} frame @param {number | undefined} afterSeq */
  onSnapshot(frame, afterSeq) {
    const records = Array.isArray(frame.records) ? frame.records : []
    let items = translateRecords(records)
    let resumed = false
    if (afterSeq !== undefined && afterSeq <= frame.cursor) {
      const firstSeq = records.length > 0 ? records[0]?.event?.seq : undefined
      if (records.length === 0 ? afterSeq === frame.cursor : typeof firstSeq === 'number' && firstSeq <= afterSeq + 1) {
        items = items.filter(item => item.seq > afterSeq)
        resumed = true
      }
    }
    this.cursor = frame.cursor
    this.snapshotCursor = typeof frame.projections?.asOfSeq === 'number' ? frame.projections.asOfSeq : frame.cursor
    const values = frame.projections?.values ?? {}
    const active = frame.assistantStream?.activeAttempt
    this.live = null
    let live = null
    if (active !== undefined && active !== null) {
      this.live = new LiveAttempt(String(active.attemptId))
      for (const record of active.stream ?? []) this.live.applyRecord(record)
      this.live.drain()
      live = { attemptId: this.live.attemptId, turn: active.turn, step: active.step, blocks: this.live.snapshot() }
    }
    this.send({
      t: 'snapshot',
      sessionId: this.sessionId,
      cursor: frame.cursor,
      hasMore: frame.hasMore === true,
      resumed,
      items,
      title: typeof values.title === 'string' ? values.title : null,
      model: values.modelSelection ?? null,
      queue: inboxItems(values.inbox),
      cwd: frame.header?.cwd ?? null,
      live,
    })
    const held = this.heldProjections ?? []
    this.heldProjections = null
    for (const projection of held) this.onProjection(projection)
  }

  /** @param {any} frame */
  onAssistantFrame(frame) {
    if (frame === null || typeof frame !== 'object') return
    switch (frame.type) {
      case 'start':
        this.flush()
        this.live = new LiveAttempt(String(frame.attemptId))
        this.send({ t: 'live', op: 'start', sessionId: this.sessionId, attemptId: this.live.attemptId, turn: frame.turn, step: frame.step })
        break
      case 'chunk':
        if (this.live === null || this.live.attemptId !== String(frame.attemptId)) return
        this.live.applyChunk(frame.chunk)
        this.scheduleFlush()
        break
      case 'end': {
        if (this.live === null || this.live.attemptId !== String(frame.attemptId)) return
        this.flush()
        const outcome = frame.outcome ?? {}
        this.send({
          t: 'live',
          op: 'end',
          sessionId: this.sessionId,
          attemptId: this.live.attemptId,
          committed: outcome.kind === 'committed',
          ...(typeof outcome.seq === 'number' ? { seq: outcome.seq } : {}),
        })
        this.live = null
        break
      }
      default:
    }
  }

  scheduleFlush() {
    if (this.flushTimer !== null) return
    this.flushTimer = setTimeout(() => {
      this.flushTimer = null
      // A slow link coalesces deltas instead of queueing frames.
      if (this.client.conn.bufferedAmount > BACKPRESSURE_BYTES) {
        this.scheduleFlush()
        return
      }
      this.flush()
    }, DELTA_FLUSH_MS)
  }

  flush() {
    if (this.flushTimer !== null) {
      clearTimeout(this.flushTimer)
      this.flushTimer = null
    }
    if (this.live === null) return
    const deltas = this.live.drain()
    if (deltas.length === 0) return
    this.send({ t: 'live', op: 'delta', sessionId: this.sessionId, attemptId: this.live.attemptId, deltas })
  }

  async observeProjections() {
    const controller = this.client.host.controller
    if (typeof controller.observeProjections !== 'function') return
    try {
      for await (const frame of controller.observeProjections({ sessionId: this.sessionId }, this.signal)) {
        if (this.stopped) return
        if (frame?.type !== 'projection') continue
        if (this.heldProjections !== null) this.heldProjections.push(frame)
        else this.onProjection(frame)
      }
    } catch (error) {
      if (!this.stopped) this.client.host.logger.warn(`dsh-bridge: projection stream for ${this.sessionId} ended: ${error}`)
    }
  }

  /** @param {any} frame */
  onProjection(frame) {
    // Changes at or before the snapshot cut are already in the snapshot values.
    if (typeof frame.seq === 'number' && frame.seq <= this.snapshotCursor) return
    const update = { t: 'projection', sessionId: this.sessionId, seq: frame.seq }
    let changed = false
    for (const change of frame.changes ?? []) {
      if (change.key === 'title') { update.title = change.val; changed = true }
      if (change.key === 'modelSelection') { update.model = change.val; changed = true }
      if (change.key === 'inbox') { update.queue = inboxItems(change.val); changed = true }
    }
    if (changed) this.send(update)
  }
}

/** @param {any} action */
function queueAction(action) {
  if (action === 'remove') return { kind: 'remove' }
  if (action === 'steer') return { kind: 'steer' }
  if (typeof action?.edit === 'string' && action.edit.trim().length > 0) {
    return { kind: 'edit', content: [{ type: 'text', text: action.edit }] }
  }
  throw new BridgeError('bad-request', 'action must be "remove", "steer" or { edit: text }')
}

/** @param {any} catalog */
function modelCatalog(catalog) {
  return {
    default: catalog.default,
    groups: (catalog.groups ?? []).map(group => ({
      id: group.id,
      name: group.name,
      models: (group.models ?? []).map(model => ({
        id: model.id,
        name: model.name,
        ...(model.description ? { description: model.description } : {}),
        ...(model.reasoning ? { reasoning: model.reasoning } : {}),
      })),
    })),
    failures: catalog.failures ?? [],
  }
}
