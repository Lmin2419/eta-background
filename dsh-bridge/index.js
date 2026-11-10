// dsh-bridge: DeepSeek Harness plugin that mirrors desktop Sessions to the
// 胖小胖 Android app over one authenticated WebSocket.

import { randomBytes } from 'node:crypto'
import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import { Bridge, RUNTIME } from './lib/bridge.js'
import { startServer } from './lib/server.js'

export const name = 'dsh-bridge'
export const inject = ['sessionController', 'workspaceRegistry']

const VERSION = (() => {
  try {
    return JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8')).version
  } catch {
    return '0.0.0'
  }
})()

/**
 * Version of the DSH runtime this plugin is loaded into, read from the package
 * the running entry script belongs to. Best effort: `null` when the layout is
 * not recognised (the bridge itself relies on feature detection, not on this).
 * @returns {string | null}
 */
export function detectDshVersion() {
  const names = ['@deepseek-ai/dsh/package.json', '@deepseek-ai/dsh-api-session-controller/package.json', '@deepseek-ai/dsh-base/package.json']
  for (const from of [process.argv[1], process.execPath]) {
    if (typeof from !== 'string' || from.length === 0) continue
    for (const name of names) {
      try {
        const version = JSON.parse(readFileSync(createRequire(from).resolve(name), 'utf8')).version
        if (typeof version === 'string' && version.length > 0) return version
      } catch { /* try the next location */ }
    }
  }
  return null
}

/**
 * Resolve the shared secret: explicit config, then DSH_BRIDGE_TOKEN, then a
 * token file created on first start with owner-only permissions.
 * @param {{ token?: string, tokenFile?: string }} config
 * @returns {{ token: string, source: string }}
 */
export function resolveToken(config) {
  if (typeof config.token === 'string' && config.token.length > 0) return { token: config.token, source: 'config' }
  const env = process.env.DSH_BRIDGE_TOKEN
  if (typeof env === 'string' && env.length > 0) return { token: env, source: 'env DSH_BRIDGE_TOKEN' }
  const file = config.tokenFile ?? join(homedir(), '.dsh-bridge', 'token')
  if (existsSync(file)) {
    const stored = readFileSync(file, 'utf8').trim()
    if (stored.length >= 16) return { token: stored, source: file }
  }
  const token = randomBytes(32).toString('base64url')
  mkdirSync(dirname(file), { recursive: true, mode: 0o700 })
  writeFileSync(file, `${token}\n`, { mode: 0o600 })
  try { chmodSync(file, 0o600) } catch { /* Windows */ }
  return { token, source: `${file} (new)` }
}

/**
 * @param {any} ctx - Cordis context.
 * @param {{ host?: string, port?: number, token?: string, tokenFile?: string }} [config]
 */
export function apply(ctx, config = {}) {
  const logger = ctx.logger ?? console
  const host = typeof config.host === 'string' ? config.host : (process.env.DSH_BRIDGE_HOST ?? '127.0.0.1')
  const port = Number(config.port ?? process.env.DSH_BRIDGE_PORT ?? 7788)
  const services = {
    controller: ctx.sessionController,
    workspaces: ctx.workspaceRegistry,
    subagents: undefined,
    userQuestions: undefined,
    agentOf: (sessionId) => {
      const agents = typeof ctx.get === 'function' ? ctx.get('agents') : ctx.agents
      return agents?.get?.(sessionId)
    },
    sessionOf: (sessionId) => {
      const sessions = typeof ctx.get === 'function' ? ctx.get('sessions') : ctx.sessions
      return sessions?.get?.(sessionId)
    },
    dshVersion: detectDshVersion(),
    logger,
    version: VERSION,
  }
  const bridge = new Bridge(services)
  // Optional: subagent continuation and stop exist only when the profile composes subagents.
  if (typeof ctx.inject === 'function') {
    ctx.inject(['subagents'], (child) => {
      services.subagents = child.subagents
      child.effect(() => () => { services.subagents = undefined }, 'dsh-bridge: subagents')
    })
    // Optional: answering continued questions needs the userQuestions service.
    ctx.inject(['userQuestions'], (child) => {
      services.userQuestions = child.userQuestions
      child.effect(() => () => { services.userQuestions = undefined }, 'dsh-bridge: userQuestions')
    })
  }

  ctx.on('api-session/added', summary => bridge.onSessionAdded(summary))
  ctx.on('api-session/status', (sessionId, running) => bridge.onStatus(sessionId, running))
  ctx.on('api-session/activity', (sessionId, updatedAt) => bridge.onActivity(sessionId, updatedAt))
  ctx.on('api-session/error', (sessionId, message) => bridge.onSessionError(sessionId, message))
  // Workspace registry writes: new or renamed workspaces, membership, archive and pin sets.
  ctx.on('domain/changed', (change) => { if (change?.domain === 'workspace') bridge.onWorkspacesChanged() })
  // Every Session's events, for turn-end reminders on the phone.
  ctx.on('session/event', (session, event) => bridge.onSessionEvent(session, event), { global: true })
  // Prepended so the phone sees approvals and questions even while the desktop UI's forwarder holds them.
  ctx.on('approval/request', (request, next) => bridge.requestApproval(request, next), true)
  ctx.on('user-questions/request', (request, next) => bridge.requestQuestion(request, next), true)

  ctx.effect(async () => {
    const { token, source } = resolveToken(config)
    let handle
    try {
      handle = await startServer({ bridge, token, host, port, logger })
    } catch (error) {
      logger.error(`dsh-bridge: cannot listen on ${host}:${port}: ${error?.message ?? error}`)
      return () => bridge.dispose()
    }
    const stopFeed = bridge.startProjectionFeed()
    logger.info(`dsh-bridge ${VERSION} (${RUNTIME}, DSH ${services.dshVersion ?? 'unknown'}, api ${bridge.hostApi().join('+')}) listening on http://${host}:${handle.port} (token from ${source})`)
    return async () => {
      stopFeed()
      bridge.dispose()
      await handle.close()
    }
  }, 'dsh-bridge: server')
}
