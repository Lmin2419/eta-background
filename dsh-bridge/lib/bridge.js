// Phone connection handling: request/response ops, Session follows with live
// assistant streaming, global Session list updates, and tool approvals.
// `host` is the narrow slice of DSH services the bridge uses, so tests can fake it.

import { readFile, stat } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import { hostname, platform } from 'node:os'
import {
  LiveAttempt, clip, inboxItems, sessionRow, textOf, translateEvent, translateRecords,
} from './translate.js'

const MAX_FILES = 8
const MAX_PREVIEW_BYTES = 8 * 1024 * 1024

/** @param {string} path */
function mediaTypeOf(path) {
  const ext = path.toLowerCase().split('.').pop()
  return ext === 'png' ? 'image/png' : ext === 'webp' ? 'image/webp' : ext === 'gif' ? 'image/gif' : 'image/jpeg'
}

export const PROTOCOL_VERSION = 1
const DELTA_FLUSH_MS = 30
const BACKPRESSURE_BYTES = 1 << 20
const OP_TIMEOUT_MS = 30_000
const FOLLOW_WINDOW = { maxMessages: 200, turnWindow: { minMessages: 30, minTurns: 2 } }
const OLDER_WINDOW = { maxMessages: 200, turnWindow: { minMessages: 40, minTurns: 2 } }
const APPROVAL_OUTCOMES = new Set(['allowed-once', 'rejected', 'cancelled'])
const MAX_SUBAGENTS = 60
const TURN_TEXT_LIMIT = 600
const HEARTBEAT_MIN_MS = 10_000
const HEARTBEAT_MAX_MS = 120_000
const WORKSPACE_PUSH_MS = 150
// How long a request only the phone can answer survives with no phone connected.
const PHONE_GRACE_MS = 5 * 60_000

/** Which process hosts the plugin: the DSH Desktop app (Electron) or a Node `dsh` command. */
export const RUNTIME = typeof process.versions.electron === 'string' ? 'desktop' : 'node'

/**
 * @typedef {object} BridgeHost
 * @property {any} controller - DSH `sessionController` service.
 * @property {any} [workspaces] - DSH `workspaceRegistry` service.
 * @property {any} [subagents] - DSH `subagents` runtime (optional).
 * @property {any} [userQuestions] - DSH `userQuestions` service (optional).
 * @property {(sessionId: string) => any} [agentOf] - live root Agent of a Session, when one exists.
 * @property {(sessionId: string) => any} [sessionOf] - live Session, when one is loaded.
 * @property {string | null} [dshVersion] - version of the DSH runtime hosting the plugin, when it can be read.
 * @property {number} [phoneGraceMs] - test override for the phone-only grace period.
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
    /** @type {Map<string, any>} */
    this.questions = new Map()
    /** Last assistant text per Session, reported with the turn's end. @type {Map<string, string>} */
    this.lastText = new Map()
    /** @type {ReturnType<typeof setTimeout> | null} */
    this.workspaceTimer = null
    /** @type {ReturnType<typeof setTimeout> | null} */
    this.graceTimer = null
    /** Follows by Session, fed from the host-wide projection stream. @type {Map<string, Set<Follow>>} */
    this.followers = new Map()
    this.disposed = false
  }

  /**
   * Which optional `sessionController` methods this DSH has. DSH 0.2.2 added
   * `events`, `state` and `observeProjections`; the bridge works without them.
   */
  hostApi() {
    const controller = this.host.controller ?? {}
    return ['control', 'page', 'events', 'state', 'observeProjections', 'projections', 'search']
      .filter(name => typeof controller[name] === 'function')
  }

  /**
   * Follow the host-wide projection stream, the one projection feed every DSH
   * version has. It carries queue, title, usage and stats changes of every live
   * Session; the loop restarts if the stream ends while the bridge is running.
   * @returns {() => void} stop
   */
  startProjectionFeed() {
    const controller = this.host.controller
    if (typeof controller?.control !== 'function') {
      this.host.logger.warn('dsh-bridge: this DSH has no sessionController.control(); queue and title changes will not reach the phone live')
      return () => {}
    }
    const abort = new AbortController()
    const run = async () => {
      let delay = 500
      while (!abort.signal.aborted) {
        try {
          for await (const frame of controller.control(abort.signal)) {
            delay = 500
            if (frame?.type === 'projection') this.onProjectionChanged(String(frame.sessionId), projectionChanges(frame), frame.seq)
          }
        } catch (error) {
          if (!abort.signal.aborted) this.host.logger.warn(`dsh-bridge: projection feed ended: ${error}`)
        }
        if (abort.signal.aborted) return
        await new Promise((resolve) => {
          const timer = setTimeout(resolve, delay)
          timer.unref?.()
          abort.signal.addEventListener('abort', () => { clearTimeout(timer); resolve(undefined) }, { once: true })
        })
        delay = Math.min(delay * 2, 10_000)
      }
    }
    void run()
    return () => abort.abort()
  }

  /**
   * Adopt one authenticated WebSocket connection.
   * @param {import('./ws.js').WsConnection} conn
   */
  attach(conn) {
    const client = new Client(this, conn)
    this.clients.add(client)
    if (this.graceTimer !== null) clearTimeout(this.graceTimer)
    this.graceTimer = null
    conn.on('close', () => {
      this.clients.delete(client)
      client.dispose()
      if (this.clients.size === 0 && !this.disposed) this.startGrace()
    })
    return client
  }

  /**
   * The last phone left. Requests the desktop already declined wait for it to
   * come back; if it does not, they settle the way DSH would have settled them
   * without a phone, so an Agent is never left waiting on nobody.
   */
  startGrace() {
    if (this.graceTimer !== null) clearTimeout(this.graceTimer)
    this.graceTimer = setTimeout(() => {
      this.graceTimer = null
      if (this.clients.size === 0) this.releasePhoneOnly()
    }, this.host.phoneGraceMs ?? PHONE_GRACE_MS)
    this.graceTimer.unref?.()
  }

  releasePhoneOnly() {
    for (const pending of [...this.approvals.values(), ...this.questions.values()]) pending.release()
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
    // The registry may record the new Session's workspace just after this event.
    this.onWorkspacesChanged()
  }

  /**
   * One Session's projection changes from the host-wide feed. Clients following
   * the Session get every mirrored key; a new title (generated or renamed on the
   * desktop) also goes to every other phone so its Session list stays current.
   * @param {string} sessionId
   * @param {ReadonlyArray<{ key: string, val: unknown }>} changes
   * @param {number} seq
   */
  onProjectionChanged(sessionId, changes, seq) {
    if (changes.length === 0 || this.clients.size === 0) return
    const following = new Set()
    for (const follow of this.followers.get(sessionId) ?? []) {
      following.add(follow.client)
      follow.acceptProjection({ seq, changes })
    }
    const title = changes.find(change => change.key === 'title' && typeof change.val === 'string')
    if (title === undefined || this.isSubagent(sessionId)) return
    const text = JSON.stringify({ t: 'projection', sessionId, seq, title: title.val })
    for (const client of this.clients) if (!following.has(client)) client.conn.sendText(text)
  }

  /** @param {string} sessionId */
  isSubagent(sessionId) {
    try {
      return this.host.sessionOf?.(sessionId)?.header?.origin === 'subagent'
    } catch {
      return false
    }
  }

  /**
   * Whether a Session is running right now: `state()` where DSH has it, else the live Agent.
   * @param {string} sessionId @param {AbortSignal} signal
   */
  async isRunning(sessionId, signal) {
    const controller = this.host.controller
    if (typeof controller.state === 'function') {
      try {
        return (await controller.state({ sessionId }, signal))?.running === true
      } catch {
        return false
      }
    }
    try {
      return this.host.agentOf?.(sessionId)?.status === 'running'
    } catch {
      return false
    }
  }

  /**
   * Workspaces, their Session membership, and the archive and pin sets, in the
   * desktop sidebar's order.
   */
  workspaceState() {
    const registry = this.host.workspaces
    if (registry === undefined) return { workspaces: [], archived: [], pinned: [] }
    return {
      workspaces: registry.list().map(workspace => ({
        id: String(workspace.id),
        title: workspace.title,
        path: workspace.path,
        sessionIds: [...workspace.sessionIds].map(String),
      })),
      archived: [...(registry.archivedSessionIds ?? [])].map(String),
      pinned: [...(registry.pinnedSessionIds ?? [])].map(String),
    }
  }

  /**
   * The workspace registry changed (workspace created, renamed, reordered or
   * removed; a Session joined one; something was archived or pinned on the
   * desktop). Bursts collapse into one push of the whole, small state.
   */
  onWorkspacesChanged() {
    if (this.disposed || this.clients.size === 0 || this.workspaceTimer !== null) return
    this.workspaceTimer = setTimeout(() => {
      this.workspaceTimer = null
      if (this.clients.size === 0) return
      try {
        this.broadcast({ t: 'workspaces', ...this.workspaceState() })
      } catch (error) {
        this.host.logger.warn(`dsh-bridge: workspace push failed: ${error}`)
      }
    }, WORKSPACE_PUSH_MS)
    this.workspaceTimer.unref?.()
  }

  /** @param {string} sessionId @param {boolean} running */
  onStatus(sessionId, running) {
    if (this.clients.size > 0) this.broadcast({ t: 'status', sessionId: String(sessionId), running })
  }

  /** @param {string} sessionId @param {number} updatedAt */
  onActivity(sessionId, updatedAt) {
    if (this.clients.size > 0) this.broadcast({ t: 'activity', sessionId: String(sessionId), updatedAt })
  }

  /** Relay tunnel state changed (frpc connected, failed, restarted). */
  onTunnelChanged(state) {
    if (this.clients.size > 0) this.broadcast({ t: 'tunnel', ...state })
  }

  /** @param {string} sessionId @param {string} message */
  onSessionError(sessionId, message) {
    if (this.clients.size > 0) this.broadcast({ t: 'error', sessionId: String(sessionId), message })
  }

  /**
   * Global `session/event` hook: remembers each ordinary Session's latest
   * assistant text and announces every finished turn, so the phone can remind
   * the user without following the Session.
   * @param {any} session
   * @param {any} event
   */
  onSessionEvent(session, event) {
    if (session?.header?.origin === 'subagent') return
    const sessionId = String(session?.id ?? session?.header?.id ?? '')
    if (sessionId.length === 0) return
    if (event?.type === 'assistant/message') {
      const text = textOf(event.data?.message?.content).trim()
      if (text.length > 0) this.lastText.set(sessionId, text)
      return
    }
    if (event?.type === 'turn/start') {
      this.lastText.delete(sessionId)
      return
    }
    if (event?.type !== 'turn/end') return
    const text = this.lastText.get(sessionId) ?? ''
    this.lastText.delete(sessionId)
    if (this.clients.size === 0) return
    const end = translateEvent(event)
    if (end === null || end.k !== 'turn_end') return
    this.broadcast({
      t: 'turn',
      sessionId,
      turn: end.turn,
      reason: end.reason,
      ...(typeof end.message === 'string' ? { message: clip(end.message, TURN_TEXT_LIMIT).text } : {}),
      text: text.length > TURN_TEXT_LIMIT ? text.slice(-TURN_TEXT_LIMIT) : text,
      time: end.time,
    })
  }

  /**
   * `approval/request` waterfall listener. The request goes to the phone and to
   * the next answerer (the desktop UI) at the same time; the first answer wins
   * and the other side's prompt is withdrawn. It is tracked even while no phone
   * is connected, so a phone that connects later can still answer it.
   * @param {any} request
   * @param {() => Promise<string>} next
   * @returns {Promise<string>}
   */
  async requestApproval(request, next) {
    const sessionId = String(request.agent?.session?.id ?? request.agent?.id ?? '')
    const id = randomUUID()
    const entry = {
      id,
      sessionId,
      toolName: String(request.toolName ?? ''),
      ...(request.callId === undefined ? {} : { callId: String(request.callId) }),
      ...(request.reason === undefined ? {} : { reason: String(request.reason) }),
      ...(request.displayReason === undefined ? {} : { displayReason: request.displayReason }),
      createdAt: Date.now(),
    }
    // DSH answers 'unavailable' when no desktop UI can ask (window closed, headless):
    // with a phone connected that is the phone's question to answer, not a refusal.
    const race = this.raceWithDesktop(request, next, 'cancelled', () => true, value => value === 'unavailable')
    this.approvals.set(id, { entry, resolve: race.answer, release: race.release })
    this.broadcast({ t: 'approval', approval: entry })
    let outcome = 'cancelled'
    try {
      outcome = await race.result
      return outcome
    } finally {
      this.finishApproval(id, outcome)
    }
  }

  /**
   * Run one interaction against both the phone and the downstream answerer.
   * The downstream gets a signal this bridge aborts when the phone answers
   * first. A downstream failure falls back to the phone while one is connected
   * and is rethrown otherwise, so DSH behaves as without the bridge.
   * @template T
   * @param {any} request - waterfall payload; its `signal` is replaced.
   * @param {() => Promise<T>} next - downstream answerer.
   * @param {T} cancelled - value the phone side settles with when the request is aborted.
   * @param {(error: unknown) => boolean} [fallBack] - whether a downstream failure may wait for the phone.
   * @param {(value: T) => boolean} [declined] - whether a downstream value means "nobody there to ask".
   * @returns {{ answer: (value: T) => void, result: Promise<T>, release: () => void }}
   *   `release` settles a request that is waiting on the phone alone with the desktop's own outcome.
   */
  raceWithDesktop(request, next, cancelled, fallBack = () => true, declined = () => false) {
    /** @type {(value: T) => void} */
    let answer = () => {}
    /** @type {(error: unknown) => void} */
    let fail = () => {}
    const phone = new Promise((resolve, reject) => { answer = resolve; fail = reject })
    /** The desktop's outcome once it declined and only the phone can still answer. @type {{ value: T } | { error: unknown } | null} */
    let declinedWith = null
    const original = request.signal
    const onAbort = () => {
      if (cancelled === undefined) fail(original?.reason ?? new Error('aborted'))
      else answer(cancelled)
    }
    original?.addEventListener?.('abort', onAbort, { once: true })
    if (original?.aborted) onAbort()

    const withdraw = new AbortController()
    try {
      const combined = original === undefined ? withdraw.signal : AbortSignal.any([original, withdraw.signal])
      Object.defineProperty(request, 'signal', { value: combined, configurable: true, enumerable: true, writable: true })
    } catch {
      // Frozen request: the desktop still answers, its prompt just cannot be withdrawn.
    }
    const downstream = Promise.resolve().then(() => next())
    downstream.catch(() => {})
    const result = (
      Promise.race([
        phone.then(value => ({ from: 'phone', value }), error => ({ from: 'phone', error })),
        downstream.then(value => ({ from: 'desktop', value }), error => ({ from: 'desktop', error })),
      ]).then((winner) => {
        const phoneCanAnswer = !this.disposed && this.clients.size > 0
        if (winner.from === 'desktop' && 'error' in winner) {
          if (!phoneCanAnswer || !fallBack(winner.error)) throw winner.error
          declinedWith = { error: winner.error }
          return phone
        }
        if ('error' in winner) throw winner.error
        if (winner.from === 'desktop' && phoneCanAnswer && declined(winner.value)) {
          declinedWith = { value: winner.value }
          return phone
        }
        return winner.value
      })
    ).finally(() => {
      original?.removeEventListener?.('abort', onAbort)
      withdraw.abort(new Error('dsh-bridge: answered elsewhere'))
    })
    const release = () => {
      if (declinedWith === null) return
      if ('error' in declinedWith) fail(declinedWith.error)
      else answer(declinedWith.value)
    }
    return { answer, result, release }
  }

  /**
   * `user-questions/request` waterfall listener: `ask_user_question` calls,
   * plan reviews and question-shaped approvals reach the phone the same way
   * approvals do. Only a missing desktop answerer (`NO_PROVIDER`) waits for the
   * phone; a desktop countdown that ends (`ASK_TIMED_OUT`) is passed through so
   * DSH turns the call into a continued question.
   * @param {any} request
   * @param {() => Promise<any>} next
   */
  async requestQuestion(request, next) {
    const sessionId = String(request.agent?.session?.id ?? request.agent?.id ?? '')
    const id = randomUUID()
    const entry = {
      id,
      sessionId,
      ...(request.wait?.callId === undefined ? {} : { callId: String(request.wait.callId) }),
      timed: request.wait?.timed === true,
      questions: questionItems(request.questions),
      createdAt: Date.now(),
    }
    const race = this.raceWithDesktop(request, next, undefined, error => error?.code === 'NO_PROVIDER')
    this.questions.set(id, { entry, resolve: race.answer, release: race.release })
    this.broadcast({ t: 'question', question: entry })
    let outcome = 'cancelled'
    try {
      const answer = await race.result
      outcome = 'answered'
      return answer
    } catch (error) {
      if (error?.code === 'ASK_TIMED_OUT') outcome = 'timeout'
      throw error
    } finally {
      this.finishQuestion(id, outcome)
    }
  }

  /** @param {string} id @param {any} answers */
  answerQuestion(id, answers) {
    const pending = this.questions.get(id)
    if (pending === undefined) return false
    pending.resolve({ answers: answerItems(answers, pending.entry.questions) })
    return true
  }

  /** @param {string} id @param {string} outcome */
  finishQuestion(id, outcome) {
    if (!this.questions.delete(id)) return
    this.broadcast({ t: 'question-done', questionId: id, outcome })
  }

  pendingQuestions() {
    return [...this.questions.values()].map(pending => pending.entry)
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
    this.disposed = true
    if (this.workspaceTimer !== null) clearTimeout(this.workspaceTimer)
    this.workspaceTimer = null
    if (this.graceTimer !== null) clearTimeout(this.graceTimer)
    this.graceTimer = null
    for (const client of this.clients) client.conn.close(1001, 'bridge stopping')
    this.clients.clear()
    // Turning the plugin off must not answer for the user: a request the desktop is
    // still showing stays with the desktop, and one only the phone could answer
    // settles the way DSH would have settled it without a phone.
    this.releasePhoneOnly()
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
          runtime: RUNTIME,
          dsh: { version: this.host.dshVersion ?? null, api: this.bridge.hostApi() },
          approvals: this.bridge.pendingApprovals(),
          questions: this.bridge.pendingQuestions(),
          features: ['archive', 'pin', 'questions', 'turns', 'heartbeat', 'titles', 'workspaces', 'queue-by-request', 'image-preview', 'files', 'tunnel'],
          tunnel: this.host.tunnel?.() ?? null,
        }
      case 'client.heartbeat': {
        const interval = Number(m.intervalMs)
        if (!Number.isFinite(interval)) throw new BridgeError('bad-request', 'intervalMs is required')
        this.conn.heartbeatMs = Math.min(HEARTBEAT_MAX_MS, Math.max(HEARTBEAT_MIN_MS, Math.round(interval)))
        return { intervalMs: this.conn.heartbeatMs }
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
      case 'attachments.image':
        return this.attachmentImage(m)
      case 'sessions.cancel':
        await controller.cancel({ sessionId: requireString(m.sessionId, 'sessionId') })
        return { accepted: true }
      case 'sessions.rename':
        return controller.rename({ sessionId: requireString(m.sessionId, 'sessionId'), title: requireString(m.title, 'title') })
      case 'sessions.queue': {
        const sessionId = requireString(m.sessionId, 'sessionId')
        const action = queueAction(m.action)
        // The phone can act on a message it just sent before it learns the queue item's id.
        const itemId = typeof m.itemId === 'string' && m.itemId.length > 0
          ? m.itemId
          : await this.queueItemOf(sessionId, requireString(m.requestId, 'itemId or requestId'))
        if (itemId === undefined) throw new BridgeError('not-queued', 'the message is no longer waiting in the queue')
        try {
          await controller.updateQueue({ sessionId, itemId, action })
        } catch (error) {
          if (String(error?.code ?? '').includes('queue-item-not-found')) {
            throw new BridgeError('not-queued', 'the message is no longer waiting in the queue')
          }
          throw error
        }
        return { accepted: true, itemId }
      }
      case 'sessions.follow':
        this.follow(requireString(m.sessionId, 'sessionId'), typeof m.afterSeq === 'number' ? m.afterSeq : undefined, addressOf(m))
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
        const request = { address: addressOf(m), beforeSeq: m.beforeSeq, ...OLDER_WINDOW }
        // DSH 0.2.2 pages with `events`; earlier versions with `page`, which needs the
        // log cut of the follow's opening snapshot.
        const page = typeof controller.events === 'function'
          ? await controller.events(request, this.opSignal())
          : await controller.page({ ...request, throughSeq: this.follows.get(sessionId)?.openingCursor ?? m.beforeSeq }, this.opSignal())
        return { items: translateRecords(page.records), hasMore: page.hasMore === true, cursor: page.cursor ?? null }
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
      case 'sessions.subagents':
        return this.subagentDirectory(requireString(m.sessionId, 'sessionId'))
      case 'subagents.prompt': {
        const runtime = this.host.subagents
        if (runtime === undefined) throw new BridgeError('unavailable', 'subagents are not available in this DSH profile')
        const content = []
        if (typeof m.text === 'string' && m.text.trim().length > 0) content.push({ type: 'text', text: m.text })
        const request = {
          requestId: typeof m.requestId === 'string' && m.requestId.length > 0 ? m.requestId : randomUUID(),
          childSessionId: requireString(m.childSessionId, 'childSessionId'),
          delivery: m.mode === 'steer' ? 'steer' : 'queue',
          content,
          ...(typeof m.timeZone === 'string' ? { clientTimeZone: m.timeZone } : {}),
        }
        await runtime.prompt(request, this.opSignal())
        return { accepted: true, requestId: request.requestId }
      }
      case 'subagents.interrupt': {
        const runtime = this.host.subagents
        if (runtime === undefined) throw new BridgeError('unavailable', 'subagents are not available in this DSH profile')
        return runtime.interruptByUser(requireString(m.childSessionId, 'childSessionId'))
      }
      case 'sessions.archive':
      case 'sessions.unarchive':
      case 'sessions.pin':
      case 'sessions.unpin':
        return this.updateSessionFlag(m.op, requireString(m.sessionId, 'sessionId'))
      case 'question.answer': {
        const accepted = this.bridge.answerQuestion(requireString(m.questionId, 'questionId'), m.answers)
        return { accepted }
      }
      case 'question.reply':
        return this.replyToQuestion(requireString(m.sessionId, 'sessionId'), requireString(m.callId, 'callId'), m.answers)
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
      const row = sessionRow(summary, index)
      if (archived.has(id)) row.archived = true
      else if (pinned.has(id)) row.pinned = true
      sessions.push(row)
    }
    const workspaces = registry === undefined ? [] : registry.list().map(workspace => ({
      id: String(workspace.id),
      title: workspace.title,
      path: workspace.path,
    }))
    return { sessions, workspaces }
  }

  /**
   * Archive, unarchive, pin or unpin through the workspace registry, the same
   * registry-global sets the desktop sidebar uses.
   * @param {string} op
   * @param {string} sessionId
   */
  async updateSessionFlag(op, sessionId) {
    const registry = this.host.workspaces
    if (registry === undefined) throw new BridgeError('unavailable', 'the workspace registry is not available')
    try {
      if (op === 'sessions.archive') await registry.archiveSession(sessionId)
      else if (op === 'sessions.unarchive') await registry.unarchiveSession(sessionId)
      else if (op === 'sessions.pin') await registry.pinSession(sessionId)
      else await registry.unpinSession(sessionId)
    } catch (error) {
      const name = error?.constructor?.name ?? ''
      if (name === 'WorkspaceActiveSessionError') throw new BridgeError('busy', 'the session is still running')
      if (name === 'WorkspaceUnknownSessionError') throw new BridgeError('not-found', 'the session no longer exists')
      if (name === 'WorkspaceArchivedSessionPinError') throw new BridgeError('archived', 'an archived session cannot be pinned')
      throw error
    }
    const archived = (registry.archivedSessionIds ?? []).map(String).includes(sessionId)
    const pinned = !archived && (registry.pinnedSessionIds ?? []).map(String).includes(sessionId)
    this.bridge.broadcast({ t: 'session-flags', sessionId, archived, pinned })
    return { archived, pinned }
  }

  /**
   * Answer a continued question (its foreground wait already ended): DSH steers
   * the answer into the live root agent as a reply message.
   * @param {string} sessionId @param {string} callId @param {any} answers
   */
  async replyToQuestion(sessionId, callId, answers) {
    const service = this.host.userQuestions
    if (service === undefined) throw new BridgeError('unavailable', 'questions are not available in this DSH profile')
    const agent = this.host.agentOf?.(sessionId)
    if (agent === undefined || agent === null) throw new BridgeError('not-live', 'the session is not active; answer it on the computer')
    const projections = await this.host.controller.projections({ sessionId }, this.opSignal())
    const active = projections?.values?.userQuestions?.active ?? []
    const question = active.find(item => String(item.callId) === callId)
    if (question === undefined) return { accepted: false }
    const accepted = service.answer(agent, callId, { answers: answerItems(answers, questionItems(question.questions)) })
    return { accepted: accepted === true }
  }

  /**
   * Find the queued message a phone request created, by the request id DSH
   * records on the message.
   * @param {string} sessionId @param {string} requestId
   * @returns {Promise<string | undefined>}
   */
  async queueItemOf(sessionId, requestId) {
    const projections = await this.host.controller.projections({ sessionId }, this.opSignal())
    return inboxItems(projections?.values?.inbox).find(item => item.rpcId === requestId)?.id
  }

  /**
   * Bytes of one stored image attachment, for the phone's message preview.
   * @param {any} m
   */
  async attachmentImage(m) {
    const ref = m.ref
    if (!ref || typeof ref !== 'object' || typeof ref.attachmentId !== 'string') throw new BridgeError('bad-request', 'ref is required')
    const attachments = this.host.attachments
    if (!attachments || typeof attachments.imageHostPath !== 'function') throw new BridgeError('unsupported', 'image previews are not available on this DSH')
    let path
    try { path = attachments.imageHostPath(ref) } catch (error) { throw new BridgeError('not-found', String(error?.message ?? error)) }
    if (typeof path !== 'string' || path.length === 0) throw new BridgeError('unsupported', 'image previews are not available on this DSH')
    const info = await stat(path).catch(() => { throw new BridgeError('not-found', 'image file is gone') })
    if (info.size > MAX_PREVIEW_BYTES) throw new BridgeError('too-large', 'image is too large to preview')
    const data = (await readFile(path)).toString('base64')
    return { mediaType: typeof ref.mediaType === 'string' ? ref.mediaType : mediaTypeOf(path), data }
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
    if (Array.isArray(m.files) && m.files.length > 0) {
      const uploads = this.host.fileUploads
      if (!uploads || typeof uploads.upload !== 'function') throw new BridgeError('unsupported', 'this DSH cannot receive files from the phone')
      const agent = this.host.agentOf(sessionId) ?? (typeof uploads.resolveAgent === 'function' ? await uploads.resolveAgent(sessionId) : undefined)
      if (!agent) throw new BridgeError('not-found', `session ${sessionId} is not attached`)
      for (const file of m.files.slice(0, MAX_FILES)) {
        if (typeof file?.data !== 'string' || file.data.length === 0) continue
        const staged = await uploads.upload(agent, { data: file.data, ...(typeof file.name === 'string' ? { name: file.name } : {}) }, this.opSignal())
        content.push({ type: 'file', receiptId: staged.receiptId })
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

  /**
   * Direct subagent children of one Session with their status, usage and timing,
   * the same facts the desktop header directory shows.
   * @param {string} sessionId
   */
  async subagentDirectory(sessionId) {
    const controller = this.host.controller
    const signal = this.opSignal()
    const parent = await controller.projections({ sessionId }, signal)
    const catalog = Array.isArray(parent?.values?.subagentCatalog) ? parent.values.subagentCatalog : []
    const children = await Promise.all(catalog.slice(0, MAX_SUBAGENTS).map(async (entry) => {
      const id = String(entry.id)
      const row = { id, mode: String(entry.mode ?? 'unknown'), createdAt: entry.createdAt ?? 0 }
      if (typeof entry.label === 'string') row.label = entry.label
      if (typeof entry.name === 'string') row.name = entry.name
      if (row.mode === 'external') return row
      const [projections, running] = await Promise.all([
        controller.projections({ sessionId: id }, signal).catch(() => null),
        this.bridge.isRunning(id, signal),
      ])
      const values = projections?.values ?? {}
      if (typeof values.title === 'string') row.title = values.title
      if (values.tokenUsage) row.usage = values.tokenUsage
      if (values.subagentTiming) row.timing = values.subagentTiming
      row.running = running
      return row
    }))
    return { children, more: catalog.length > MAX_SUBAGENTS }
  }

  /** @param {string} sessionId @param {number | undefined} afterSeq */
  follow(sessionId, afterSeq, address) {
    this.follows.get(sessionId)?.stop()
    const follow = new Follow(this, sessionId, address)
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
  constructor(client, sessionId, address = { kind: 'session', sessionId }) {
    this.client = client
    this.sessionId = sessionId
    this.address = address
    this.abort = new AbortController()
    this.signal = AbortSignal.any([client.lifetime.signal, this.abort.signal])
    /** @type {LiveAttempt | null} */
    this.live = null
    this.flushTimer = null
    this.cursor = -1
    this.snapshotCursor = -1
    /** Log cut of the opening snapshot; older DSH versions page history against it. */
    this.openingCursor = undefined
    this.stopped = false
    /** @type {any[] | null} projection frames waiting for the snapshot cursor */
    this.heldProjections = []
  }

  stop() {
    this.stopped = true
    const followers = this.client.bridge.followers
    const set = followers.get(this.sessionId)
    if (set !== undefined) {
      set.delete(this)
      if (set.size === 0) followers.delete(this.sessionId)
    }
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
    // Projection changes arrive from the bridge's host-wide feed; they are held
    // until the snapshot fixes the cut they are compared against.
    const followers = this.client.bridge.followers
    if (!followers.has(this.sessionId)) followers.set(this.sessionId, new Set())
    followers.get(this.sessionId).add(this)
    const frames = controller.follow({
      address: this.address,
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
    this.openingCursor = frame.cursor
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
      ...extraProjections(values),
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

  /**
   * One projection change of this Session from the host-wide feed.
   * @param {{ seq: number, changes: ReadonlyArray<{ key: string, val: unknown }> }} frame
   */
  acceptProjection(frame) {
    if (this.stopped) return
    if (this.heldProjections !== null) this.heldProjections.push(frame)
    else this.onProjection(frame)
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
      const extra = EXTRA_PROJECTIONS[change.key]
      if (extra !== undefined) { update[extra] = projectionValue(change.key, change.val); changed = true }
    }
    if (changed) this.send(update)
  }
}

/**
 * Changes of one control-stream projection frame. DSH 0.2.2 sends every key an
 * event changed as `changes: [{ key, val }]`; earlier versions send one
 * `{ key, value }` per frame.
 * @param {any} frame
 * @returns {Array<{ key: string, val: unknown }>}
 */
export function projectionChanges(frame) {
  if (Array.isArray(frame?.changes)) return frame.changes.filter(change => typeof change?.key === 'string')
  if (typeof frame?.key === 'string') return [{ key: frame.key, val: frame.value }]
  return []
}

/** Projection keys mirrored to the phone under shorter names. */
const EXTRA_PROJECTIONS = {
  sessionStats: 'stats',
  tokenUsage: 'usage',
  contextPressure: 'pressure',
  subagentCatalog: 'subagents',
  subagentTiming: 'timing',
  userQuestions: 'questions',
}

/** @param {string} key @param {any} value */
function projectionValue(key, value) {
  if (value === undefined || value === null) return null
  if (key === 'userQuestions') {
    const active = Array.isArray(value.active) ? value.active : []
    return {
      active: active.map(item => ({
        callId: String(item.callId),
        state: item.state === 'continued' ? 'continued' : 'open',
        questions: questionItems(item.questions),
      })),
    }
  }
  return value
}

/** @param {Record<string, any>} values */
function extraProjections(values) {
  const out = {}
  for (const [key, name] of Object.entries(EXTRA_PROJECTIONS)) out[name] = projectionValue(key, values[key])
  return out
}

/**
 * Questions as the phone renders them: text, options, multi-select and the
 * plan-review / approval presentation intents.
 * @param {any} questions
 */
export function questionItems(questions) {
  if (!Array.isArray(questions)) return []
  return questions.filter(item => item !== null && typeof item === 'object' && typeof item.id === 'string').map(item => ({
    id: item.id,
    question: String(item.question ?? ''),
    ...(typeof item.detail === 'string' ? { detail: clip(item.detail, 20_000).text } : {}),
    ...(typeof item.header === 'string' ? { header: item.header } : {}),
    options: Array.isArray(item.options)
      ? item.options.filter(option => typeof option?.label === 'string').map(option => ({
        label: option.label,
        ...(typeof option.description === 'string' ? { description: option.description } : {}),
      }))
      : [],
    multiSelect: item.multiSelect === true,
    ...(item.intent !== null && typeof item.intent === 'object' && typeof item.intent.kind === 'string'
      ? { intent: { ...item.intent, ...(item.intent.callId === undefined ? {} : { callId: String(item.intent.callId) }) } }
      : {}),
  }))
}

/**
 * Normalise a phone answer into DSH's batch: one entry per asked question, in
 * question order; an unanswered question is sent as skipped.
 * @param {any} answers
 * @param {ReadonlyArray<{ id: string, options?: ReadonlyArray<{ label: string }>, multiSelect?: boolean }>} questions
 */
export function answerItems(answers, questions) {
  const byId = new Map()
  for (const answer of Array.isArray(answers) ? answers : []) {
    if (typeof answer?.id === 'string' && !byId.has(answer.id)) byId.set(answer.id, answer)
  }
  return questions.map((question) => {
    const answer = byId.get(question.id)
    const labels = new Set((question.options ?? []).map(option => option.label))
    let selected = Array.isArray(answer?.selected) ? answer.selected.filter(label => typeof label === 'string' && labels.has(label)) : []
    if (question.multiSelect !== true) selected = selected.slice(0, 1)
    const custom = typeof answer?.custom === 'string' && answer.custom.trim().length > 0 ? answer.custom.trim() : undefined
    // A single-select custom answer replaces the choice, as in the desktop form.
    if (custom !== undefined && question.multiSelect !== true) selected = []
    return { id: question.id, selected, ...(custom === undefined ? {} : { custom }) }
  })
}

/** Session address for follow/history: a direct subagent child when `parentId` is given. */
function addressOf(m) {
  const sessionId = requireString(m.sessionId, 'sessionId')
  if (typeof m.parentId !== 'string' || m.parentId.length === 0) return { kind: 'session', sessionId }
  const mode = m.mode === 'one-shot' || m.mode === 'continuable' ? m.mode : 'unknown'
  return { kind: 'subagent', parentSessionId: m.parentId, childSessionId: sessionId, mode }
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
