// Minimal RFC 6455 WebSocket server side, zero dependencies.
// Supports text/binary messages, fragmentation, ping/pong and close.
// Extensions (permessage-deflate) are not negotiated.

import { createHash } from 'node:crypto'
import { EventEmitter } from 'node:events'

const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11'
const MAX_MESSAGE_BYTES = 16 * 1024 * 1024

/**
 * Complete the HTTP upgrade handshake.
 * @param {import('node:http').IncomingMessage} req
 * @param {import('node:stream').Duplex} socket
 * @returns {WsConnection | null}
 */
export function acceptUpgrade(req, socket) {
  const key = req.headers['sec-websocket-key']
  const version = req.headers['sec-websocket-version']
  const upgrade = String(req.headers.upgrade ?? '').toLowerCase()
  if (typeof key !== 'string' || upgrade !== 'websocket' || version !== '13') {
    rejectUpgrade(socket, 400, 'Bad Request')
    return null
  }
  const accept = createHash('sha1').update(key + GUID).digest('base64')
  socket.write(
    'HTTP/1.1 101 Switching Protocols\r\n'
    + 'Upgrade: websocket\r\n'
    + 'Connection: Upgrade\r\n'
    + `Sec-WebSocket-Accept: ${accept}\r\n\r\n`,
  )
  return new WsConnection(socket)
}

/**
 * @param {import('node:stream').Duplex} socket
 * @param {number} status
 * @param {string} text
 */
export function rejectUpgrade(socket, status, text) {
  socket.end(`HTTP/1.1 ${status} ${text}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`)
}

export class WsConnection extends EventEmitter {
  /** @param {import('node:stream').Duplex} socket */
  constructor(socket) {
    super()
    this.socket = socket
    this.open = true
    this.buffer = Buffer.alloc(0)
    /** @type {Buffer[]} */
    this.fragments = []
    this.fragmentOpcode = 0
    this.fragmentBytes = 0
    this.lastSeen = Date.now()
    socket.setNoDelay?.(true)
    socket.on('data', chunk => this.onData(chunk))
    socket.on('close', () => this.finish(1006, 'socket closed'))
    socket.on('error', () => this.finish(1006, 'socket error'))
  }

  /** @param {string} text */
  sendText(text) {
    if (!this.open) return false
    this.socket.write(frame(0x1, Buffer.from(text, 'utf8')))
    return true
  }

  /** @param {Buffer} [payload] */
  ping(payload = Buffer.alloc(0)) {
    if (!this.open) return
    this.socket.write(frame(0x9, payload))
  }

  /**
   * @param {number} [code]
   * @param {string} [reason]
   */
  close(code = 1000, reason = '') {
    if (!this.open) return
    const body = Buffer.alloc(2 + Buffer.byteLength(reason))
    body.writeUInt16BE(code, 0)
    body.write(reason, 2)
    this.socket.write(frame(0x8, body))
    this.socket.end()
    this.finish(code, reason)
  }

  /** Bytes queued in the socket but not yet flushed to the kernel. */
  get bufferedAmount() {
    return this.socket.writableLength ?? 0
  }

  /**
   * @param {number} code
   * @param {string} reason
   */
  finish(code, reason) {
    if (!this.open) return
    this.open = false
    this.socket.destroy()
    this.emit('close', code, reason)
  }

  /** @param {Buffer} chunk */
  onData(chunk) {
    this.lastSeen = Date.now()
    this.buffer = this.buffer.length === 0 ? chunk : Buffer.concat([this.buffer, chunk])
    for (;;) {
      const parsed = parseFrame(this.buffer)
      if (parsed === null) return
      if (parsed === 'too-large') {
        this.close(1009, 'message too large')
        return
      }
      this.buffer = this.buffer.subarray(parsed.length)
      if (!parsed.masked) {
        // Clients must mask every frame (RFC 6455 §5.1).
        this.close(1002, 'unmasked client frame')
        return
      }
      this.onFrame(parsed.fin, parsed.opcode, parsed.payload)
      if (!this.open) return
    }
  }

  /**
   * @param {boolean} fin
   * @param {number} opcode
   * @param {Buffer} payload
   */
  onFrame(fin, opcode, payload) {
    switch (opcode) {
      case 0x0: // continuation
        if (this.fragmentOpcode === 0) return this.close(1002, 'unexpected continuation')
        this.pushFragment(payload)
        if (fin) this.flushFragments()
        return
      case 0x1:
      case 0x2:
        if (this.fragmentOpcode !== 0) return this.close(1002, 'interleaved message')
        if (fin) return this.deliver(opcode, payload)
        this.fragmentOpcode = opcode
        this.pushFragment(payload)
        return
      case 0x8: {
        const code = payload.length >= 2 ? payload.readUInt16BE(0) : 1000
        this.close(code === 1005 || code === 1006 ? 1000 : code)
        return
      }
      case 0x9:
        if (this.open) this.socket.write(frame(0xA, payload))
        return
      case 0xA:
        this.emit('pong')
        return
      default:
        this.close(1002, 'unknown opcode')
    }
  }

  /** @param {Buffer} payload */
  pushFragment(payload) {
    this.fragmentBytes += payload.length
    if (this.fragmentBytes > MAX_MESSAGE_BYTES) return this.close(1009, 'message too large')
    this.fragments.push(payload)
  }

  flushFragments() {
    const opcode = this.fragmentOpcode
    const payload = Buffer.concat(this.fragments)
    this.fragments = []
    this.fragmentOpcode = 0
    this.fragmentBytes = 0
    this.deliver(opcode, payload)
  }

  /**
   * @param {number} opcode
   * @param {Buffer} payload
   */
  deliver(opcode, payload) {
    if (opcode === 0x1) this.emit('message', payload.toString('utf8'))
    else this.emit('binary', payload)
  }
}

/**
 * @param {Buffer} buf
 * @returns {null | 'too-large' | { fin: boolean, opcode: number, masked: boolean, payload: Buffer, length: number }}
 */
function parseFrame(buf) {
  if (buf.length < 2) return null
  const fin = (buf[0] & 0x80) !== 0
  const opcode = buf[0] & 0x0f
  const masked = (buf[1] & 0x80) !== 0
  let len = buf[1] & 0x7f
  let offset = 2
  if (len === 126) {
    if (buf.length < 4) return null
    len = buf.readUInt16BE(2)
    offset = 4
  } else if (len === 127) {
    if (buf.length < 10) return null
    const big = buf.readBigUInt64BE(2)
    if (big > BigInt(MAX_MESSAGE_BYTES)) return 'too-large'
    len = Number(big)
    offset = 10
  }
  if (len > MAX_MESSAGE_BYTES) return 'too-large'
  const maskOffset = offset
  if (masked) offset += 4
  if (buf.length < offset + len) return null
  const payload = Buffer.from(buf.subarray(offset, offset + len))
  if (masked) {
    for (let i = 0; i < payload.length; i++) payload[i] ^= buf[maskOffset + (i & 3)]
  }
  return { fin, opcode, masked, payload, length: offset + len }
}

/**
 * Build one unmasked server frame.
 * @param {number} opcode
 * @param {Buffer} payload
 * @returns {Buffer}
 */
function frame(opcode, payload) {
  const len = payload.length
  let header
  if (len < 126) {
    header = Buffer.alloc(2)
    header[1] = len
  } else if (len < 65536) {
    header = Buffer.alloc(4)
    header[1] = 126
    header.writeUInt16BE(len, 2)
  } else {
    header = Buffer.alloc(10)
    header[1] = 127
    header.writeBigUInt64BE(BigInt(len), 2)
  }
  header[0] = 0x80 | opcode
  return Buffer.concat([header, payload])
}
