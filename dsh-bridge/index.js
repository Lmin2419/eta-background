// dsh-bridge: DeepSeek Harness plugin that mirrors desktop Sessions to the
// 胖小胖 Android app over one authenticated WebSocket.

import { randomBytes } from 'node:crypto'
import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import { Bridge } from './lib/bridge.js'
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
  }

  ctx.on('api-session/added', summary => bridge.onSessionAdded(summary))
  ctx.on('api-session/status', (sessionId, running) => bridge.onStatus(sessionId, running))
  ctx.on('api-session/activity', (sessionId, updatedAt) => bridge.onActivity(sessionId, updatedAt))
  ctx.on('api-session/error', (sessionId, message) => bridge.onSessionError(sessionId, message))
  // Prepended so the phone sees approvals even while the Web UI's forwarder holds them.
  ctx.on('approval/request', (request, next) => bridge.requestApproval(request, next), true)

  ctx.effect(async () => {
    const { token, source } = resolveToken(config)
    let handle
    try {
      handle = await startServer({ bridge, token, host, port, logger })
    } catch (error) {
      logger.error(`dsh-bridge: cannot listen on ${host}:${port}: ${error?.message ?? error}`)
      return () => bridge.dispose()
    }
    logger.info(`dsh-bridge ${VERSION} listening on http://${host}:${handle.port} (token from ${source})`)
    return async () => {
      bridge.dispose()
      await handle.close()
    }
  }, 'dsh-bridge: server')
}
