// HTTP listener: unauthenticated health check, bearer-token WebSocket at /v1/ws.

import { createServer } from 'node:http'
import { timingSafeEqual } from 'node:crypto'
import { acceptUpgrade, rejectUpgrade } from './ws.js'
import { PROTOCOL_VERSION, RUNTIME } from './bridge.js'

// Each connection pings at its own interval (15 s by default; a phone in the
// background asks for a longer one) and is dropped after three silent intervals.
const DEFAULT_HEARTBEAT_MS = 15_000
const TICK_MS = 5_000
const MISSED_BEATS = 3

/**
 * @param {string} presented
 * @param {string} expected
 */
export function tokenMatches(presented, expected) {
  const a = Buffer.from(presented)
  const b = Buffer.from(expected)
  return a.length === b.length && timingSafeEqual(a, b)
}

/** @param {import('node:http').IncomingMessage} req */
function bearerOf(req) {
  const header = req.headers.authorization
  if (typeof header === 'string' && header.startsWith('Bearer ')) return header.slice(7).trim()
  return ''
}

/**
 * @param {{ bridge: import('./bridge.js').Bridge, token: string, host: string, port: number, logger: any }} options
 * @returns {Promise<{ server: import('node:http').Server, port: number, close: () => Promise<void> }>}
 */
export function startServer({ bridge, token, host, port, logger }) {
  const server = createServer((req, res) => {
    const url = new URL(req.url ?? '/', 'http://bridge')
    if (req.method === 'GET' && url.pathname === '/v1/health') {
      res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' })
      res.end(JSON.stringify({
        ok: true,
        protocol: PROTOCOL_VERSION,
        version: bridge.host.version,
        runtime: RUNTIME,
        dsh: { version: bridge.host.dshVersion ?? null, api: bridge.hostApi() },
      }))
      return
    }
    res.writeHead(404, { 'content-type': 'text/plain' })
    res.end('not found')
  })

  /** @type {Set<import('./ws.js').WsConnection>} */
  const connections = new Set()

  server.on('upgrade', (req, socket) => {
    const url = new URL(req.url ?? '/', 'http://bridge')
    if (url.pathname !== '/v1/ws') return rejectUpgrade(socket, 404, 'Not Found')
    if (!tokenMatches(bearerOf(req), token)) {
      logger.warn(`dsh-bridge: rejected connection with a bad token from ${req.headers['x-forwarded-for'] ?? req.socket.remoteAddress}`)
      return rejectUpgrade(socket, 401, 'Unauthorized')
    }
    const conn = acceptUpgrade(req, socket)
    if (conn === null) return
    connections.add(conn)
    conn.on('close', () => connections.delete(conn))
    bridge.attach(conn)
  })

  // Heartbeat: detect dead mobile links quickly instead of waiting for TCP timeouts.
  const heartbeat = setInterval(() => {
    const now = Date.now()
    for (const conn of connections) {
      const interval = conn.heartbeatMs ?? DEFAULT_HEARTBEAT_MS
      if (now - conn.lastSeen > interval * MISSED_BEATS) {
        conn.close(1001, 'heartbeat timeout')
      } else if (now - (conn.lastPing ?? 0) >= interval - TICK_MS / 2) {
        conn.lastPing = now
        conn.ping()
      }
    }
  }, TICK_MS)
  heartbeat.unref?.()

  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(port, host, () => {
      server.off('error', reject)
      const address = server.address()
      resolve({
        server,
        port: typeof address === 'object' && address !== null ? address.port : port,
        close: () => new Promise((done) => {
          clearInterval(heartbeat)
          for (const conn of connections) conn.close(1001, 'bridge stopping')
          server.close(() => done())
          server.closeAllConnections?.()
        }),
      })
    })
  })
}
