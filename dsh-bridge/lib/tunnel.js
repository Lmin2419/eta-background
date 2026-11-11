// frpc supervisor: the relay tunnel lives and dies with the plugin.
//
// The setup script leaves frpc and its config in ~/.dsh-bridge (bin/frpc[.exe], frpc.toml).
// While the plugin runs, it keeps one frpc child alive (restart with backoff), records its
// pid so a frpc orphaned by a hard DSH exit is replaced instead of duplicated, and reads
// frpc's log lines to report the tunnel state to the phone.

import { spawn } from 'node:child_process'
import { existsSync, readFileSync, writeFileSync, rmSync } from 'node:fs'
import { homedir, platform } from 'node:os'
import { join } from 'node:path'

const RESTART_MIN_MS = 2_000
const RESTART_MAX_MS = 60_000

/**
 * @typedef {{ state: 'absent' | 'starting' | 'connected' | 'error' | 'stopped', message?: string, since: number }} TunnelState
 */

/** Default locations written by deploy/desktop/setup-frpc.* */
export function tunnelPaths(home = process.env.DSH_BRIDGE_HOME ?? join(homedir(), '.dsh-bridge')) {
  return {
    home,
    binary: join(home, 'bin', platform() === 'win32' ? 'frpc.exe' : 'frpc'),
    config: join(home, 'frpc.toml'),
    pidFile: join(home, 'frpc.pid'),
  }
}

/**
 * Classify one frpc log line. frpc prints "login to server success", "start proxy success"
 * once connected, and "login to the server failed" / "connect to server error" on trouble.
 * @param {string} line
 * @returns {{ state: 'connected' | 'error' | 'starting', message?: string } | undefined}
 */
export function classifyLine(line) {
  const text = line.trim()
  if (text.length === 0) return undefined
  if (/start proxy success/i.test(text)) return { state: 'connected' }
  if (/login to (the )?server success/i.test(text)) return { state: 'starting', message: 'logged in' }
  if (/(login to (the )?server failed|connect to server error|start error|proxy .* start error|authorization failed|i\/o timeout|connection refused)/i.test(text)) {
    return { state: 'error', message: text.replace(/^.*?\]\s*/, '').slice(0, 200) }
  }
  return undefined
}

/** Stop a frpc left running by an earlier, hard-killed DSH. */
function killStale(pidFile, logger) {
  if (!existsSync(pidFile)) return
  const pid = Number(readFileSync(pidFile, 'utf8').trim())
  rmSync(pidFile, { force: true })
  if (!Number.isInteger(pid) || pid <= 0) return
  try {
    process.kill(pid)
    logger.info?.(`dsh-bridge: stopped stale frpc (pid ${pid})`)
  } catch { /* already gone */ }
}

/**
 * Start supervising frpc. Without a binary or config (tunnel not set up on this computer,
 * or the user relays some other way), the state stays `absent` and nothing runs.
 * @param {{ logger?: any, paths?: ReturnType<typeof tunnelPaths>, spawnImpl?: typeof spawn, onChange?: (state: TunnelState) => void }} [options]
 */
export function startTunnel(options = {}) {
  const logger = options.logger ?? console
  const paths = options.paths ?? tunnelPaths()
  const spawnImpl = options.spawnImpl ?? spawn
  /** @type {TunnelState} */
  let current = { state: 'absent', since: Date.now() }
  let child
  let timer
  let delay = RESTART_MIN_MS
  let stopped = false

  const set = (next) => {
    if (next.state === current.state && next.message === current.message) return
    current = { ...next, since: Date.now() }
    options.onChange?.(current)
  }

  const launch = () => {
    timer = undefined
    if (stopped) return
    if (!existsSync(paths.binary) || !existsSync(paths.config)) {
      set({ state: 'absent' })
      return
    }
    set({ state: 'starting' })
    let proc
    try {
      proc = spawnImpl(paths.binary, ['-c', paths.config], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
    } catch (error) {
      set({ state: 'error', message: String(error?.message ?? error) })
      schedule()
      return
    }
    child = proc
    if (proc.pid) {
      try { writeFileSync(paths.pidFile, String(proc.pid)) } catch { /* best effort */ }
    }
    let buffer = ''
    const onData = (chunk) => {
      buffer += chunk.toString()
      const lines = buffer.split(/\r?\n/)
      buffer = lines.pop() ?? ''
      for (const line of lines) {
        const verdict = classifyLine(line)
        if (!verdict) continue
        if (verdict.state === 'connected') delay = RESTART_MIN_MS
        set(verdict)
      }
    }
    proc.stdout?.on('data', onData)
    proc.stderr?.on('data', onData)
    proc.on('error', (error) => set({ state: 'error', message: String(error?.message ?? error) }))
    proc.on('exit', (code) => {
      if (child === proc) child = undefined
      try { rmSync(paths.pidFile, { force: true }) } catch { /* ignore */ }
      if (stopped) return
      set({ state: 'error', message: `frpc exited (${code ?? 'signal'})` })
      schedule()
    })
  }

  const schedule = () => {
    if (stopped || timer) return
    timer = setTimeout(launch, delay)
    timer.unref?.()
    delay = Math.min(RESTART_MAX_MS, delay * 2)
  }

  killStale(paths.pidFile, logger)
  launch()

  return {
    get state() { return current },
    stop() {
      stopped = true
      if (timer) clearTimeout(timer)
      timer = undefined
      const proc = child
      child = undefined
      if (proc && proc.exitCode === null) {
        try { proc.kill() } catch { /* ignore */ }
      }
      try { rmSync(paths.pidFile, { force: true }) } catch { /* ignore */ }
      set({ state: 'stopped' })
    },
  }
}
