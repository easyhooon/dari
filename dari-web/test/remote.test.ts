import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { install, type DariWeb } from '../src/install'
import { connectRemote, remotePageUrl, type SocketLike } from '../src/remote'
import type { PageEvent, ViewerEvent } from '../src/remote-protocol'
import { MessageStore } from '../src/store'
import { deviceOf, mountViewer, pageOptionLabel, type Viewer } from '../src/viewer-app'
import type { WireMessageDetail } from '../src/wire'

class FakeSocket implements SocketLike {
  readyState = 0
  sent: unknown[] = []
  closed = false
  onopen: ((event: unknown) => void) | null = null
  onmessage: ((event: { data: unknown }) => void) | null = null
  onclose: ((event: unknown) => void) | null = null
  onerror: ((event: unknown) => void) | null = null

  constructor(readonly url: string) {}

  send(data: string): void {
    this.sent.push(JSON.parse(data))
  }
  close(): void {
    this.closed = true
  }
  open(): void {
    this.readyState = 1
    this.onopen?.({})
  }
  drop(): void {
    this.readyState = 3
    this.onclose?.({})
  }
  receive(event: unknown): void {
    this.onmessage?.({ data: JSON.stringify(event) })
  }
}

function fakeSockets() {
  const sockets: FakeSocket[] = []
  return {
    sockets,
    create: (url: string) => {
      const socket = new FakeSocket(url)
      sockets.push(socket)
      return socket
    },
  }
}

const types = (socket: FakeSocket) => socket.sent.map((e) => (e as PageEvent).type)

describe('remote sender', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('normalizes the relay URL', () => {
    expect(remotePageUrl('ws://localhost:9333')).toBe('ws://localhost:9333/page')
    expect(remotePageUrl('http://localhost:9333')).toBe('ws://localhost:9333/page')
    expect(remotePageUrl('wss://relay.test/custom')).toBe('wss://relay.test/custom')
    expect(() => remotePageUrl('ftp://localhost')).toThrow()
    expect(() => remotePageUrl('not a url')).toThrow()
  })

  it('sends hello and existing entries, then diffs', () => {
    const store = new MessageStore()
    store.recorder.webToAppRequest({ handlerName: 'getUser', requestId: '1', data: { id: 7 } })
    const { sockets, create } = fakeSockets()
    connectRemote('ws://localhost:9333', store, create)
    const socket = sockets[0]!
    expect(socket.url).toBe('ws://localhost:9333/page')

    socket.open()
    expect(socket.sent[0]).toMatchObject({ type: 'hello', protocol: 1, page: { dariWebVersion: expect.any(String) } })
    expect(socket.sent[1]).toMatchObject({ type: 'added', message: { summary: { handlerName: 'getUser', status: 'IN_PROGRESS' } } })

    store.recorder.webToAppResponse({ requestId: '1', isSuccess: true, data: 'ok' })
    expect(socket.sent[2]).toMatchObject({ type: 'updated', message: { summary: { status: 'SUCCESS' }, responseData: 'ok' } })

    store.clear()
    expect(types(socket)).toEqual(['hello', 'added', 'updated', 'cleared'])
  })

  it('reports entries dropped past maxEntries', () => {
    const store = new MessageStore({ maxEntries: 2 })
    const { sockets, create } = fakeSockets()
    connectRemote('ws://localhost:9333', store, create)
    sockets[0]!.open()
    for (const name of ['a', 'b', 'c']) store.recorder.webToAppRequest({ handlerName: name })
    const sent = sockets[0]!.sent
    expect(sent[sent.length - 2]).toEqual({ type: 'removed', ids: [1] })
    expect(sent[sent.length - 1]).toMatchObject({ type: 'added', message: { summary: { id: 3 } } })
  })

  it('clears the store on command and resends everything after reconnecting', () => {
    const store = new MessageStore()
    const { sockets, create } = fakeSockets()
    const sender = connectRemote('ws://localhost:9333', store, create)
    sockets[0]!.open()
    store.recorder.webToAppRequest({ handlerName: 'a' })

    sockets[0]!.drop()
    store.recorder.webToAppRequest({ handlerName: 'b' })
    vi.advanceTimersByTime(1000)
    expect(sockets).toHaveLength(2)
    sockets[1]!.open()
    expect(types(sockets[1]!)).toEqual(['hello', 'added', 'added'])
    expect((sockets[1]!.sent[0] as { page: { sessionId: string } }).page.sessionId).toBe(
      (sockets[0]!.sent[0] as { page: { sessionId: string } }).page.sessionId,
    )

    sockets[1]!.receive({ type: 'clear' })
    expect(store.getEntries()).toHaveLength(0)

    sender.close()
    expect(sockets[1]!.closed).toBe(true)
    sockets[1]!.drop()
    vi.advanceTimersByTime(20_000)
    expect(sockets).toHaveLength(2)
  })

  it('backs off while the relay is down', () => {
    const { sockets, create } = fakeSockets()
    connectRemote('ws://localhost:9333', new MessageStore(), create)
    sockets[0]!.drop()
    vi.advanceTimersByTime(1000)
    sockets[1]!.drop()
    vi.advanceTimersByTime(1000)
    expect(sockets).toHaveLength(2)
    vi.advanceTimersByTime(1000)
    expect(sockets).toHaveLength(3)
  })
})

describe('install remote option', () => {
  let dari: DariWeb | undefined
  afterEach(() => {
    dari?.uninstall()
    dari = undefined
  })

  it('rejects a malformed remote URL before touching the page', () => {
    expect(() => install({ remote: 'localhost:9333' })).toThrow()
    expect(document.querySelector('[data-dari-web]')).toBeNull()
    dari = install()
  })
})

describe('viewer', () => {
  let viewer: Viewer | undefined
  afterEach(() => {
    viewer?.destroy()
    viewer = undefined
    localStorage.clear()
  })

  const message = (id: number, handlerName: string, status = 'SUCCESS'): WireMessageDetail => ({
    summary: { id, handlerName, direction: 'WEB_TO_APP', status, requestTimestamp: 1000, responseTimestamp: 1100, requestSizeBytes: 0, responseSizeBytes: 0 },
    requestData: '{"id":7}',
    requestDataTruncated: false,
    responseDataTruncated: false,
  })
  const page = (sessionId: string, connectedAt: number, connected = true) => ({
    sessionId,
    url: `https://shop.example/${sessionId}`,
    title: `Page ${sessionId}`,
    userAgent: 'Mozilla/5.0 (Linux; Android 15; SM-S936N; wv) AppleWebKit/537.36',
    dariWebVersion: '0.1.0',
    connected,
    connectedAt,
  })

  const open = () => {
    const { sockets, create } = fakeSockets()
    viewer = mountViewer({ url: 'ws://localhost:9333/viewer', createSocket: create })
    sockets[0]!.open()
    const shadow = viewer.host.shadowRoot!
    return {
      socket: sockets[0]!,
      shadow,
      emit: (event: ViewerEvent) => sockets[0]!.receive(event),
      rows: () => [...shadow.querySelectorAll('.dari-row .dari-name')].map((e) => e.textContent),
      picker: () => shadow.querySelector('.dari-viewer-top select') as HTMLSelectElement,
    }
  }

  it('shows the newest page and switches on demand', () => {
    const v = open()
    v.emit({ type: 'pages', pages: [page('a', 1)] })
    v.emit({ type: 'added', pageId: 'a', message: message(1, 'getUser') })
    expect(v.rows()).toEqual(['getUser'])
    expect(v.picker().hidden).toBe(true)
    expect(v.shadow.querySelector('.dari-close')).toBeNull()

    v.emit({ type: 'pages', pages: [page('a', 1), page('b', 2)] })
    v.emit({ type: 'added', pageId: 'b', message: message(1, 'pay', 'ERROR') })
    expect(v.rows()).toEqual(['pay'])
    expect(v.picker().hidden).toBe(false)

    v.picker().value = 'a'
    v.picker().dispatchEvent(new Event('change'))
    expect(v.rows()).toEqual(['getUser'])
    // A pinned page that is still live stays selected.
    v.emit({ type: 'pages', pages: [page('a', 1), page('b', 2), page('c', 3)] })
    expect(v.picker().value).toBe('a')
  })

  it('follows a reloaded page once the pinned one closes', () => {
    const v = open()
    v.emit({ type: 'pages', pages: [page('a', 1), page('b', 2)] })
    v.picker().value = 'a'
    v.picker().dispatchEvent(new Event('change'))
    v.emit({ type: 'pages', pages: [page('a', 1, false), page('b', 2), page('c', 3)] })
    expect(v.picker().value).toBe('c')
  })

  it('applies updates and sends clear to the relay', () => {
    const v = open()
    v.emit({ type: 'pages', pages: [page('a', 1)] })
    v.emit({ type: 'added', pageId: 'a', message: message(1, 'getUser', 'IN_PROGRESS') })
    v.emit({ type: 'updated', pageId: 'a', message: message(1, 'getUser', 'SUCCESS') })
    expect(v.shadow.querySelector('.dari-status')!.textContent).toBe('OK')

    ;(v.shadow.querySelector('.dari-clear') as HTMLElement).click()
    expect(v.socket.sent).toEqual([{ type: 'clear', pageId: 'a' }])
    v.emit({ type: 'cleared', pageId: 'a' })
    expect(v.rows()).toEqual([])
  })

  it('labels pages by device', () => {
    expect(deviceOf(page('a', 1).userAgent)).toBe('Android WebView')
    expect(deviceOf('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148')).toBe('iOS WebView')
    expect(deviceOf(null)).toBeNull()
    expect(pageOptionLabel(page('a', new Date(2026, 9, 19, 9, 5, 3).getTime(), false))).toBe('(closed) Page a · Android WebView 09:05:03')
  })
})
