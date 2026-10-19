// @vitest-environment node
import { randomBytes } from 'node:crypto'
import { afterEach, describe, expect, it } from 'vitest'
import { parseDevices } from '../src/relay/adb'
import { parseArgs } from '../src/relay/args'
import { startRelay, type Relay } from '../src/relay/server'
import { acceptKey, encodeFrame, FrameParser, OP_TEXT } from '../src/relay/ws'
import type { PageEvent, ViewerEvent } from '../src/remote-protocol'
import type { WireMessageDetail } from '../src/wire'

describe('ws codec', () => {
  it('computes the RFC 6455 accept key', () => {
    expect(acceptKey('dGhlIHNhbXBsZSBub25jZQ==')).toBe('s3pPLMBiTxaQ9kYGzzhZRbK+xOo=')
  })

  it.each([5, 200, 70_000])('round-trips a masked %i byte frame split across chunks', (size) => {
    const payload = randomBytes(size)
    const bytes = encodeFrame(OP_TEXT, payload, randomBytes(4))
    const parser = new FrameParser(true)
    const frames = [...parser.push(bytes.subarray(0, 3)), ...parser.push(bytes.subarray(3))]
    expect(frames).toHaveLength(1)
    expect(frames[0]!.opcode).toBe(OP_TEXT)
    expect(frames[0]!.fin).toBe(true)
    expect(Buffer.compare(frames[0]!.payload, payload)).toBe(0)
  })

  it('rejects unmasked client frames', () => {
    expect(() => new FrameParser(true).push(encodeFrame(OP_TEXT, Buffer.from('hi')))).toThrow()
  })
})

describe('cli helpers', () => {
  it('parses serve options', () => {
    expect(parseArgs([])).toEqual({ port: 9333, adb: true, open: false })
    expect(parseArgs(['serve', '--port', '9400', '--no-adb', '--open'])).toEqual({ port: 9400, adb: false, open: true })
    expect(parseArgs(['--port=9401'])).toMatchObject({ port: 9401 })
    expect(parseArgs(['--help'])).toBe('help')
    expect(() => parseArgs(['--port', 'x'])).toThrow('Invalid port')
    expect(() => parseArgs(['--nope'])).toThrow('Unknown argument')
  })

  it('reads ready devices from adb output', () => {
    const output = 'List of devices attached\nR3CY70P3F4H\tdevice\nemulator-5554\toffline\nABC\tunauthorized\n\n'
    expect(parseDevices(output)).toEqual(['R3CY70P3F4H'])
  })
})

describe('relay', () => {
  let relay: Relay | undefined
  const sockets: WebSocket[] = []

  afterEach(async () => {
    for (const s of sockets.splice(0)) s.close()
    await relay?.close()
    relay = undefined
  })

  const start = async () => {
    relay = await startRelay({ port: 0, viewerHtml: '<html>viewer</html>', viewerScript: () => 'console.log(1)' })
    return relay
  }

  const connect = (path: string, headers: Record<string, string> = {}, host = '127.0.0.1') =>
    new Promise<{ ws: WebSocket; events: unknown[] }>((resolve, reject) => {
      const ws = new (WebSocket as unknown as new (url: string, init: object) => WebSocket)(
        `ws://${host}:${relay!.port}${path}`,
        { headers },
      )
      sockets.push(ws)
      const events: unknown[] = []
      ws.onmessage = (e) => events.push(JSON.parse(String(e.data)))
      ws.onopen = () => resolve({ ws, events })
      ws.onerror = () => reject(new Error('rejected'))
    })
  const viewer = () => connect('/viewer', { origin: `http://localhost:${relay!.port}` }, 'localhost')
  const send = (ws: WebSocket, event: PageEvent) => ws.send(JSON.stringify(event))
  const until = async (check: () => boolean) => {
    for (let i = 0; i < 100 && !check(); i++) await new Promise((r) => setTimeout(r, 10))
    expect(check()).toBe(true)
  }

  const hello = (sessionId: string): PageEvent => ({
    type: 'hello',
    protocol: 1,
    page: { sessionId, url: 'https://shop.example/cart', title: 'Cart', userAgent: 'test', dariWebVersion: '0.1.0' },
  })
  const message = (id: number, status = 'IN_PROGRESS'): WireMessageDetail => ({
    summary: { id, handlerName: 'getUser', direction: 'WEB_TO_APP', status, requestTimestamp: 1, requestSizeBytes: 0, responseSizeBytes: 0 },
    requestDataTruncated: false,
    responseDataTruncated: false,
  })

  it('serves the viewer only to loopback hosts', async () => {
    const { port } = await start()
    const page = await fetch(`http://127.0.0.1:${port}/`)
    expect(page.status).toBe(200)
    expect(await page.text()).toContain('viewer')
    expect((await fetch(`http://127.0.0.1:${port}/viewer.js`)).headers.get('content-type')).toContain('javascript')
    expect((await fetch(`http://127.0.0.1:${port}/nope`)).status).toBe(404)
  })

  it('rejects viewers from other origins', async () => {
    await start()
    await expect(connect('/viewer', { origin: 'https://evil.example' })).rejects.toThrow()
    await expect(connect('/viewer')).rejects.toThrow()
  })

  it('relays page events and replays them to a late viewer', async () => {
    await start()
    const page = await connect('/page')
    send(page.ws, hello('s1'))
    send(page.ws, { type: 'added', message: message(1) })

    const first = await viewer()
    await until(() => first.events.length >= 2)
    expect(first.events[0]).toMatchObject({ type: 'pages', pages: [{ sessionId: 's1', title: 'Cart', connected: true }] })
    expect(first.events[1]).toMatchObject({ type: 'added', pageId: 's1', message: { summary: { id: 1 } } })

    send(page.ws, { type: 'updated', message: message(1, 'SUCCESS') })
    await until(() => first.events.length >= 3)
    expect(first.events[2]).toMatchObject({ type: 'updated', pageId: 's1', message: { summary: { status: 'SUCCESS' } } })

    const late = await viewer()
    await until(() => late.events.length >= 2)
    expect(late.events[1]).toMatchObject({ type: 'added', message: { summary: { id: 1, status: 'SUCCESS' } } })
  })

  it('forwards a viewer clear to the page and marks closed pages', async () => {
    await start()
    const page = await connect('/page')
    send(page.ws, hello('s1'))
    const view = await viewer()
    await until(() => view.events.length >= 1)

    view.ws.send(JSON.stringify({ type: 'clear', pageId: 's1' }))
    await until(() => page.events.length >= 1)
    expect(page.events[0]).toEqual({ type: 'clear' })

    page.ws.close()
    await until(() =>
      view.events.some((e) => {
        const event = e as ViewerEvent
        return event.type === 'pages' && event.pages[0]?.connected === false
      }),
    )
  })

  it('keeps a reconnecting page in its slot', async () => {
    await start()
    const view = await viewer()
    const a = await connect('/page')
    send(a.ws, hello('s1'))
    const b = await connect('/page')
    send(b.ws, hello('s1'))
    await until(() => view.events.filter((e) => (e as ViewerEvent).type === 'reset').length >= 2)
    const pages = view.events.filter((e) => (e as ViewerEvent).type === 'pages').pop() as Extract<ViewerEvent, { type: 'pages' }>
    expect(pages.pages).toHaveLength(1)
  })
})
