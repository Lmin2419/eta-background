// Translate DeepSeek Harness session wire events into the small item vocabulary
// the phone renders. Everything here is pure so it can be unit tested without DSH.

/** Image previews offered per user message. */
const MAX_IMAGE_REFS = 6

export const TOOL_ARGS_LIMIT = 4000
export const TOOL_RESULT_LIMIT = 6000
const HIDDEN_USER_SOURCES = /-context$|^instructions?$|^system/
const TRIGGER_TEXT_LIMIT = 4000

/**
 * @param {unknown} value
 * @returns {value is Record<string, any>}
 */
function isObject(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/**
 * @param {string} text
 * @param {number} limit
 * @returns {{ text: string, truncated?: true }}
 */
export function clip(text, limit) {
  if (text.length <= limit) return { text }
  return { text: text.slice(0, limit), truncated: true }
}

/**
 * Join the text blocks of a content array.
 * @param {unknown} content
 * @returns {string}
 */
export function textOf(content) {
  if (typeof content === 'string') return content
  if (!Array.isArray(content)) return ''
  const parts = []
  for (const block of content) {
    if (isObject(block) && block.type === 'text' && typeof block.text === 'string') parts.push(block.text)
  }
  return parts.join('\n')
}

/**
 * @param {unknown} usage
 */
function firstTokenTime(stream) {
  if (!Array.isArray(stream)) return undefined
  let first
  for (const record of stream) {
    if (!isObject(record)) continue
    let time
    if (record.type === 'text-chunks' || record.type === 'reasoning-chunks' || record.type === 'tool-call-chunks') time = record.time0
    else if (record.type === 'chunk' && isObject(record.chunk)
      && (record.chunk.type === 'text-delta' || record.chunk.type === 'reasoning-delta' || record.chunk.type === 'tool-call-delta')) time = record.time
    if (typeof time === 'number' && (first === undefined || time < first)) first = time
  }
  return first
}

function usageOf(usage) {
  if (!isObject(usage)) return undefined
  const out = {}
  if (typeof usage.inputTokens === 'number') out.input = usage.inputTokens
  if (typeof usage.outputTokens === 'number') out.output = usage.outputTokens
  if (typeof usage.totalTokens === 'number') out.total = usage.totalTokens
  if (typeof usage.cacheReadTokens === 'number') out.cacheRead = usage.cacheReadTokens
  if (typeof usage.cacheWriteTokens === 'number') out.cacheWrite = usage.cacheWriteTokens
  if (typeof usage.reasoningTokens === 'number') out.reasoning = usage.reasoningTokens
  return out
}

/** Whether a surface event re-projects earlier history instead of appending new transcript. */
function isReplacement(event) {
  return isObject(event.surfaceOp) && event.surfaceOp.op === 'replace'
}

/**
 * Translate one durable Session wire event.
 * @param {{ type: string, seq: number, time: number, data: any, surfaceOp?: unknown }} event
 * @returns {Record<string, any> | null} one phone item, or null when the event has no transcript meaning.
 */
export function translateEvent(event) {
  if (!isObject(event) || typeof event.type !== 'string') return null
  const base = { seq: event.seq, time: event.time }
  const data = isObject(event.data) ? event.data : {}
  switch (event.type) {
    case 'user/message': {
      if (isReplacement(event)) return null
      const content = Array.isArray(data.content) ? data.content : []
      const source = isObject(data.source) ? data.source : {}
      // Harness-injected context (runtime/time snapshots, instructions) is model-only.
      if (typeof source.kind === 'string' && HIDDEN_USER_SOURCES.test(source.kind)) return null
      const files = []
      const imageRefs = []
      let images = 0
      for (const block of content) {
        if (!isObject(block)) continue
        if (block.type === 'image') {
          images++
          // Durable attachment reference; the phone fetches the bytes with attachments.image.
          if (isObject(block.attachment) && typeof block.attachment.attachmentId === 'string' && imageRefs.length < MAX_IMAGE_REFS) imageRefs.push(block.attachment)
        }
        if (block.type === 'file') files.push(String(block.attachment?.name ?? 'file'))
      }
      const kind = typeof source.kind === 'string' ? source.kind : 'unknown'
      if (kind !== 'user' && kind !== 'retry' && kind !== 'unknown') {
        // Runtime-triggered input (subagent results, schedules, team, webhooks): shown as a
        // compact trigger row on the desktop, never as the human's own bubble.
        return {
          k: 'trigger',
          ...base,
          id: String(data.id ?? `t${event.seq}`),
          kind,
          text: clip(textOf(content), TRIGGER_TEXT_LIMIT).text,
          ...(typeof source.summary === 'string' ? { summary: source.summary } : {}),
          ...(typeof source.stopReason === 'string' ? { stopReason: source.stopReason } : {}),
          ...(typeof source.senderSessionId === 'string' ? { sender: source.senderSessionId } : {}),
        }
      }
      return {
        k: 'user',
        ...base,
        id: String(data.id ?? `u${event.seq}`),
        text: textOf(content),
        images,
        ...(imageRefs.length > 0 ? { imageRefs } : {}),
        files,
        source: typeof source.kind === 'string' ? source.kind : 'unknown',
        ...(typeof source.rpcId === 'string' ? { rpcId: source.rpcId } : {}),
      }
    }
    case 'assistant/message': {
      if (isReplacement(event)) return null
      const message = isObject(data.message) ? data.message : {}
      const blocks = []
      for (const block of Array.isArray(message.content) ? message.content : []) {
        if (!isObject(block)) continue
        if (block.type === 'text' && typeof block.text === 'string') blocks.push({ type: 'text', text: block.text })
        else if (block.type === 'reasoning' && typeof block.text === 'string') blocks.push({ type: 'reasoning', text: block.text })
        else if (block.type === 'tool-call') {
          blocks.push({
            type: 'tool',
            id: String(block.id),
            name: String(block.name ?? ''),
            args: clip(String(block.arguments ?? ''), TOOL_ARGS_LIMIT).text,
          })
        }
      }
      const usage = usageOf(data.usage)
      const source = isObject(message.source) ? message.source : {}
      const firstTokenAt = firstTokenTime(data.stream)
      return {
        k: 'assistant',
        ...base,
        id: String(message.id ?? `a${event.seq}`),
        turn: data.turn,
        step: data.step,
        blocks,
        ...(usage ? { usage } : {}),
        ...(typeof source.provider === 'string' && typeof source.model === 'string'
          ? { model: { provider: source.provider, model: source.model } }
          : {}),
        ...(firstTokenAt === undefined ? {} : { firstTokenAt }),
        ...(data.interrupted === true ? { interrupted: true } : {}),
      }
    }
    case 'tool/result': {
      if (isReplacement(event)) return null
      const message = isObject(data.message) ? data.message : {}
      const content = Array.isArray(message.content) ? message.content : []
      const images = content.filter(block => isObject(block) && block.type === 'image').length
      const { text, truncated } = clip(textOf(content), TOOL_RESULT_LIMIT)
      const error = isObject(data.error)
        ? { code: String(data.error.code ?? ''), reason: data.error.reason === undefined ? undefined : String(data.error.reason) }
        : undefined
      return {
        k: 'tool_result',
        ...base,
        callId: String(message.toolCallId ?? ''),
        isError: message.isError === true,
        text,
        ...(truncated ? { truncated } : {}),
        ...(images > 0 ? { images } : {}),
        ...(error ? { error } : {}),
      }
    }
    case 'turn/start':
      return { k: 'turn_start', ...base, turn: data.turn }
    case 'step/start':
      return { k: 'step_start', ...base, turn: data.turn, step: data.step }
    case 'tool/call':
      return { k: 'tool_call', ...base, callId: String(data.callId ?? ''), name: String(data.name ?? '') }
    case 'turn/end': {
      const reason = isObject(data.reason) ? data.reason : {}
      const item = { k: 'turn_end', ...base, turn: data.turn, reason: String(reason.kind ?? 'unknown') }
      if (isObject(reason.error) && typeof reason.error.message === 'string') item.message = reason.error.message
      if (isObject(reason.reason) && typeof reason.reason.kind === 'string') item.cause = reason.reason.kind
      return item
    }
    case 'llm/retry': {
      const failure = isObject(data.failure) ? data.failure : {}
      return {
        k: 'notice',
        ...base,
        code: 'retry',
        text: String(failure.message ?? ''),
        retry: data.retry,
        maxRetries: data.maxRetries,
        delayMs: data.delayMs,
      }
    }
    case 'compaction/start':
      return { k: 'notice', ...base, code: 'compaction_start', text: '' }
    case 'compaction/summary':
      return { k: 'notice', ...base, code: 'compaction', text: clip(textOf(data.summary), TOOL_RESULT_LIMIT).text }
    case 'session/title':
      return typeof data.title === 'string' ? { k: 'title', ...base, title: data.title } : null
    default:
      return null
  }
}

/**
 * Translate a list of history records (`{ type: 'event', event }`).
 * @param {readonly unknown[]} records
 * @returns {Record<string, any>[]}
 */
export function translateRecords(records) {
  const items = []
  for (const record of records) {
    if (!isObject(record) || record.type !== 'event') continue
    const item = translateEvent(record.event)
    if (item !== null) items.push(item)
  }
  return items
}

/**
 * Extract pending inbox items from the `inbox` projection value.
 * @param {unknown} inbox
 * @returns {{ id: string, text: string, placement: string }[]}
 */
export function inboxItems(inbox) {
  if (!isObject(inbox)) return []
  const out = []
  for (const [target, placement] of [['next-turn', 'queued'], ['next-step', 'steering']]) {
    const list = Array.isArray(inbox[target]) ? inbox[target] : []
    for (const entry of list) {
      if (!isObject(entry)) continue
      const message = isObject(entry.message) ? entry.message : entry
      const rpcId = isObject(message.source) && typeof message.source.rpcId === 'string' ? message.source.rpcId : undefined
      out.push({
        id: String(message.id ?? entry.id ?? ''),
        text: textOf(message.content),
        placement,
        ...(rpcId ? { rpcId } : {}),
      })
    }
  }
  return out
}

/**
 * Accumulates one live assistant attempt into ordered blocks and produces
 * coalesced deltas for the phone.
 */
export class LiveAttempt {
  /** @param {string} attemptId */
  constructor(attemptId) {
    this.attemptId = attemptId
    /** @type {Map<number, { index: number, kind: string, text: string, id?: string, name?: string }>} */
    this.blocks = new Map()
    /** @type {Map<number, { index: number, kind: string, text: string, id?: string, name?: string }>} */
    this.pending = new Map()
  }

  /** @param {number} index @param {string} kind */
  block(index, kind) {
    let block = this.blocks.get(index)
    if (block === undefined) {
      block = { index, kind, text: '' }
      this.blocks.set(index, block)
    }
    return block
  }

  /**
   * @param {number} index
   * @param {string} kind
   * @param {string} text
   * @param {{ id?: string, name?: string }} [extra]
   */
  append(index, kind, text, extra = {}) {
    const block = this.block(index, kind)
    block.text += text
    if (extra.id !== undefined) block.id = extra.id
    if (extra.name !== undefined) block.name = extra.name
    let delta = this.pending.get(index)
    if (delta === undefined) {
      delta = { index, kind, text: '' }
      this.pending.set(index, delta)
    }
    delta.text += text
    if (extra.id !== undefined) delta.id = extra.id
    if (extra.name !== undefined) delta.name = extra.name
  }

  /**
   * Apply one provider-neutral StreamChunk.
   * @param {any} chunk
   */
  applyChunk(chunk) {
    if (!isObject(chunk)) return
    switch (chunk.type) {
      case 'block-start': {
        const kind = chunk.blockType === 'reasoning' ? 'reasoning' : chunk.blockType === 'tool-call' ? 'tool' : 'text'
        this.append(chunk.index, kind, '')
        return
      }
      case 'text-delta':
        this.append(chunk.index, 'text', String(chunk.text ?? ''))
        return
      case 'reasoning-delta':
        this.append(chunk.index, 'reasoning', String(chunk.text ?? ''))
        return
      case 'tool-call-delta':
        this.append(chunk.index, 'tool', String(chunk.argumentsDelta ?? ''), {
          id: chunk.id === undefined ? undefined : String(chunk.id),
          name: chunk.name === undefined ? undefined : String(chunk.name),
        })
        return
      default:
    }
  }

  /**
   * Apply one compact AssistantStreamRecord from a reconnect baseline.
   * @param {any} record
   */
  applyRecord(record) {
    if (!isObject(record)) return
    switch (record.type) {
      case 'text-chunks':
        this.append(record.index, 'text', (record.texts ?? []).join(''))
        return
      case 'reasoning-chunks':
        this.append(record.index, 'reasoning', (record.texts ?? []).join(''))
        return
      case 'tool-call-chunks':
        this.append(record.index, 'tool', (record.args ?? []).join(''), {
          id: record.id === undefined ? undefined : String(record.id),
          name: record.name === undefined ? undefined : String(record.name),
        })
        return
      case 'chunk':
        this.applyChunk(record.chunk)
        return
      default:
    }
  }

  /** @returns {object[]} coalesced deltas since the previous drain */
  drain() {
    const out = [...this.pending.values()].sort((a, b) => a.index - b.index)
    this.pending.clear()
    return out
  }

  /** @returns {object[]} complete accumulated blocks */
  snapshot() {
    return [...this.blocks.values()].sort((a, b) => a.index - b.index).map(block => ({ ...block }))
  }
}

/**
 * Normalize one Session list summary row.
 * @param {any} summary
 * @param {Map<string, { id: string, title: string }>} [workspaceBySession]
 */
export function sessionRow(summary, workspaceBySession) {
  const values = isObject(summary?.projections?.values) ? summary.projections.values : {}
  const workspace = workspaceBySession?.get(String(summary.sessionId))
  return {
    id: String(summary.sessionId),
    title: typeof values.title === 'string' ? values.title : null,
    updatedAt: summary.updatedAt,
    running: summary.running === true,
    blank: summary.blank === true,
    live: summary.agentAvailable === true,
    ...(summary.cwd ? { cwd: summary.cwd } : {}),
    ...(summary.parentSessionId ? { parentId: summary.parentSessionId } : {}),
    ...(summary.origin === 'subagent' ? { subagent: true } : {}),
    ...(workspace ? { workspaceId: workspace.id } : {}),
  }
}
