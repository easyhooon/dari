import { createHash } from 'node:crypto'
import type { IncomingMessage } from 'node:http'
import type { Duplex } from 'node:stream'

// Just enough RFC 6455 for the relay: text frames, ping/pong and close. No extensions.

const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11'
export const MAX_MESSAGE_BYTES = 16 * 1024 * 1024

export const OP_CONTINUATION = 0x0
export const OP_TEXT = 0x1
export const OP_BINARY = 0x2
export const OP_CLOSE = 0x8
export const OP_PING = 0x9
export const OP_PONG = 0xa

export function acceptKey(key: string): string {
  return createHash('sha1').update(key + GUID).digest('base64')
}

export function encodeFrame(opcode: number, payload: Buffer, mask?: Buffer): Buffer {
  const length = payload.length
  const lengthBytes = length < 126 ? 0 : length <= 0xffff ? 2 : 8
  const header = Buffer.alloc(2 + lengthBytes + (mask ? 4 : 0))
  header[0] = 0x80 | opcode
  header[1] = (mask ? 0x80 : 0) | (lengthBytes === 0 ? length : lengthBytes === 2 ? 126 : 127)
  if (lengthBytes === 2) header.writeUInt16BE(length, 2)
  if (lengthBytes === 8) header.writeBigUInt64BE(BigInt(length), 2)
  if (!mask) return Buffer.concat([header, payload])
  mask.copy(header, 2 + lengthBytes)
  return Buffer.concat([header, applyMask(payload, mask)])
}

export interface Frame {
  fin: boolean
  opcode: number
  payload: Buffer
}

/** Splits a byte stream into frames. Throws on frames the relay refuses. */
export class FrameParser {
  private buffer: Buffer = Buffer.alloc(0)

  constructor(private readonly requireMask: boolean) {}

  push(chunk: Buffer): Frame[] {
    this.buffer = this.buffer.length === 0 ? chunk : Buffer.concat([this.buffer, chunk])
    const frames: Frame[] = []
    for (;;) {
      const frame = this.next()
      if (!frame) return frames
      frames.push(frame)
    }
  }

  private next(): Frame | null {
    const b = this.buffer
    if (b.length < 2) return null
    if (b[0]! & 0x70) throw new Error('Unsupported extension bits')
    const masked = (b[1]! & 0x80) !== 0
    if (this.requireMask && !masked) throw new Error('Client frames must be masked')
    let length = b[1]! & 0x7f
    let offset = 2
    if (length === 126) {
      if (b.length < 4) return null
      length = b.readUInt16BE(2)
      offset = 4
    } else if (length === 127) {
      if (b.length < 10) return null
      const big = b.readBigUInt64BE(2)
      if (big > BigInt(MAX_MESSAGE_BYTES)) throw new Error('Frame too large')
      length = Number(big)
      offset = 10
    }
    if (length > MAX_MESSAGE_BYTES) throw new Error('Frame too large')
    const maskOffset = offset
    if (masked) offset += 4
    if (b.length < offset + length) return null
    let payload: Buffer = b.subarray(offset, offset + length)
    if (masked) payload = applyMask(payload, b.subarray(maskOffset, maskOffset + 4))
    this.buffer = b.subarray(offset + length)
    return { fin: (b[0]! & 0x80) !== 0, opcode: b[0]! & 0x0f, payload }
  }
}

function applyMask(payload: Buffer, mask: Buffer): Buffer {
  const out = Buffer.alloc(payload.length)
  for (let i = 0; i < payload.length; i++) out[i] = payload[i]! ^ mask[i & 3]!
  return out
}

/** A server-side connection after the upgrade. */
export class WsConnection {
  private readonly parser = new FrameParser(true)
  private fragments: Buffer[] = []
  private fragmentBytes = 0
  private closed = false
  onMessage: (text: string) => void = () => {}
  onClose: () => void = () => {}

  constructor(private readonly socket: Duplex) {
    socket.on('data', (chunk: Buffer) => {
      try {
        for (const frame of this.parser.push(chunk)) this.handle(frame)
      } catch {
        this.close(1002)
      }
    })
    socket.on('close', () => this.finish())
    socket.on('error', () => this.finish())
  }

  get isOpen(): boolean {
    return !this.closed
  }

  send(text: string): void {
    if (this.closed) return
    this.socket.write(encodeFrame(OP_TEXT, Buffer.from(text, 'utf8')))
  }

  close(code = 1000): void {
    if (this.closed) return
    const payload = Buffer.alloc(2)
    payload.writeUInt16BE(code)
    this.socket.end(encodeFrame(OP_CLOSE, payload))
    this.finish()
  }

  private handle(frame: Frame): void {
    switch (frame.opcode) {
      case OP_PING:
        this.socket.write(encodeFrame(OP_PONG, frame.payload))
        return
      case OP_PONG:
        return
      case OP_CLOSE:
        this.close()
        return
      case OP_TEXT:
      case OP_BINARY:
      case OP_CONTINUATION: {
        this.fragmentBytes += frame.payload.length
        if (this.fragmentBytes > MAX_MESSAGE_BYTES) return this.close(1009)
        this.fragments.push(frame.payload)
        if (!frame.fin) return
        const data = Buffer.concat(this.fragments)
        this.fragments = []
        this.fragmentBytes = 0
        this.onMessage(data.toString('utf8'))
        return
      }
      default:
        this.close(1002)
    }
  }

  private finish(): void {
    if (this.closed) return
    this.closed = true
    this.socket.destroy()
    this.onClose()
  }
}

/** Completes the handshake, or answers 400 and returns null. */
export function upgrade(request: IncomingMessage, socket: Duplex): WsConnection | null {
  const key = request.headers['sec-websocket-key']
  if (request.headers.upgrade?.toLowerCase() !== 'websocket' || typeof key !== 'string') {
    reject(socket, 400, 'Bad Request')
    return null
  }
  socket.write(
    'HTTP/1.1 101 Switching Protocols\r\n' +
      'Upgrade: websocket\r\n' +
      'Connection: Upgrade\r\n' +
      `Sec-WebSocket-Accept: ${acceptKey(key)}\r\n\r\n`,
  )
  return new WsConnection(socket)
}

export function reject(socket: Duplex, status: number, reason: string): void {
  socket.end(`HTTP/1.1 ${status} ${reason}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`)
}
