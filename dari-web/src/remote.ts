import { VERSION } from './export'
import { REMOTE_PROTOCOL, type PageCommand, type PageEvent, type PageInfo } from './remote-protocol'
import type { MessageStore } from './store'
import type { MessageEntry } from './types'
import { sanitizePageUrl } from './url'
import { toWireDetail } from './wire'

const RETRY_MIN_MILLIS = 1000
const RETRY_MAX_MILLIS = 10_000
const OPEN = 1

/** The part of `WebSocket` the sender uses, so tests can pass a fake. */
export interface SocketLike {
  readonly readyState: number
  onopen: ((event: unknown) => void) | null
  onmessage: ((event: { data: unknown }) => void) | null
  onclose: ((event: unknown) => void) | null
  onerror: ((event: unknown) => void) | null
  send(data: string): void
  close(): void
}

export interface RemoteSender {
  close(): void
}

/** `ws://host:port` → `ws://host:port/page`; `http(s)` is accepted too. Throws on a malformed URL. */
export function remotePageUrl(target: string): string {
  const url = new URL(target)
  if (url.protocol === 'http:') url.protocol = 'ws:'
  else if (url.protocol === 'https:') url.protocol = 'wss:'
  if (url.protocol !== 'ws:' && url.protocol !== 'wss:') throw new Error(`remote must be a ws:// URL: ${target}`)
  if (url.pathname === '/' || url.pathname === '') url.pathname = '/page'
  return url.toString()
}

/** Streams store changes to a relay and keeps reconnecting, resending everything after each connect. */
export function connectRemote(
  target: string,
  store: MessageStore,
  createSocket: (url: string) => SocketLike = (url) => new WebSocket(url) as unknown as SocketLike,
): RemoteSender {
  const url = remotePageUrl(target)
  const sessionId = randomId()
  let socket: SocketLike | null = null
  let sent = new Map<number, MessageEntry>()
  let retryMillis = RETRY_MIN_MILLIS
  let timer: ReturnType<typeof setTimeout> | undefined
  let closed = false

  const post = (event: PageEvent) => socket?.send(JSON.stringify(event))

  const sync = () => {
    if (!socket || socket.readyState !== OPEN) return
    const entries = store.getEntries()
    if (entries.length === 0) {
      if (sent.size > 0) post({ type: 'cleared' })
      sent.clear()
      return
    }
    const ids = new Set(entries.map((e) => e.id))
    const removed = [...sent.keys()].filter((id) => !ids.has(id))
    if (removed.length > 0) {
      for (const id of removed) sent.delete(id)
      post({ type: 'removed', ids: removed })
    }
    // The store replaces an entry object when it changes, so identity is enough.
    for (const entry of entries) {
      const previous = sent.get(entry.id)
      if (previous === entry) continue
      sent.set(entry.id, entry)
      post({ type: previous ? 'updated' : 'added', message: toWireDetail(entry) })
    }
  }

  const open = () => {
    if (closed) return
    let ws: SocketLike
    try {
      ws = createSocket(url)
    } catch {
      return retry()
    }
    socket = ws
    ws.onopen = () => {
      retryMillis = RETRY_MIN_MILLIS
      sent = new Map()
      post({ type: 'hello', protocol: REMOTE_PROTOCOL, page: pageInfo(sessionId) })
      sync()
    }
    ws.onmessage = (event) => {
      if (typeof event.data !== 'string') return
      try {
        const command = JSON.parse(event.data) as PageCommand
        if (command.type === 'clear') store.clear()
      } catch {
        // Ignore anything that isn't a command.
      }
    }
    ws.onclose = () => {
      if (socket !== ws) return
      socket = null
      retry()
    }
    // A close event always follows.
    ws.onerror = () => {}
  }

  const retry = () => {
    if (closed) return
    clearTimeout(timer)
    timer = setTimeout(open, retryMillis)
    retryMillis = Math.min(retryMillis * 2, RETRY_MAX_MILLIS)
  }

  const unsubscribe = store.subscribe(sync)
  open()

  return {
    close: () => {
      closed = true
      clearTimeout(timer)
      unsubscribe()
      const ws = socket
      socket = null
      ws?.close()
    },
  }
}

function pageInfo(sessionId: string): PageInfo {
  return {
    sessionId,
    url: typeof location === 'undefined' ? null : sanitizePageUrl(location.href),
    title: typeof document === 'undefined' ? null : document.title || null,
    userAgent: typeof navigator === 'undefined' ? null : navigator.userAgent,
    dariWebVersion: VERSION,
  }
}

// crypto.randomUUID needs a secure context, which plain-http WebView pages aren't.
function randomId(): string {
  return Math.random().toString(36).slice(2) + Date.now().toString(36)
}
