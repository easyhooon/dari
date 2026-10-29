import { createServer, type IncomingMessage, type ServerResponse } from 'node:http'
import type { AddressInfo } from 'node:net'
import type { Duplex } from 'node:stream'
import {
  REMOTE_PROTOCOL,
  type PageCommand,
  type PageEvent,
  type PageSummary,
  type ViewerCommand,
  type ViewerEvent,
} from '../remote-protocol'
import type { WireMessageDetail } from '../wire'
import { reject, upgrade, type WsConnection } from './ws'

const MAX_MESSAGES_PER_PAGE = 2000
const MAX_CLOSED_PAGES = 5

export interface RelayOptions {
  /** 0 picks a free port. */
  port: number
  viewerHtml: string
  viewerScript: () => string
  log?: (line: string) => void
}

export interface Relay {
  readonly port: number
  close(): Promise<void>
}

interface Page {
  summary: PageSummary
  messages: Map<number, WireMessageDetail>
  socket: WsConnection | null
}

/** Serves the viewer and relays page events to every open viewer. Listens on loopback only. */
export function startRelay(options: RelayOptions): Promise<Relay> {
  const log = options.log ?? (() => {})
  const pages = new Map<string, Page>()
  const viewers = new Set<WsConnection>()
  let port = options.port

  const allowedHost = (host: string | undefined) =>
    host === `localhost:${port}` || host === `127.0.0.1:${port}` || host === `[::1]:${port}`

  const broadcast = (event: ViewerEvent) => {
    const text = JSON.stringify(event)
    for (const viewer of viewers) viewer.send(text)
  }
  const pageList = (): ViewerEvent => ({ type: 'pages', pages: [...pages.values()].map((p) => p.summary) })

  const server = createServer((request: IncomingMessage, response: ServerResponse) => {
    // Host check keeps DNS-rebound origins out.
    if (!allowedHost(request.headers.host)) return send(response, 403, 'text/plain', 'Forbidden')
    const path = new URL(request.url ?? '/', 'http://localhost').pathname
    if (request.method !== 'GET') return send(response, 405, 'text/plain', 'Method Not Allowed')
    if (path === '/') return send(response, 200, 'text/html; charset=utf-8', options.viewerHtml)
    if (path === '/viewer.js') return send(response, 200, 'text/javascript; charset=utf-8', options.viewerScript())
    send(response, 404, 'text/plain', 'Not Found')
  })

  server.on('upgrade', (request: IncomingMessage, socket: Duplex) => {
    const path = new URL(request.url ?? '/', 'http://localhost').pathname
    if (path === '/page') {
      const ws = upgrade(request, socket)
      if (ws) acceptPage(ws)
    } else if (path === '/viewer') {
      // Only the viewer page served here may read the stream.
      const host = request.headers.host
      if (!allowedHost(host) || request.headers.origin !== `http://${host}`) return reject(socket, 403, 'Forbidden')
      const ws = upgrade(request, socket)
      if (ws) acceptViewer(ws)
    } else {
      reject(socket, 404, 'Not Found')
    }
  })

  function acceptPage(ws: WsConnection): void {
    let page: Page | null = null
    ws.onMessage = (text) => {
      const event = parse<PageEvent>(text)
      if (!event) return
      if (event.type === 'hello') {
        if (event.protocol !== REMOTE_PROTOCOL || typeof event.page?.sessionId !== 'string') return ws.close(1002)
        const id = event.page.sessionId.slice(0, 64)
        const previous = pages.get(id)
        if (previous?.socket && previous.socket !== ws) previous.socket.close()
        page = {
          summary: { ...event.page, sessionId: id, connected: true, connectedAt: Date.now() },
          messages: new Map(),
          socket: ws,
        }
        pages.delete(id)
        pages.set(id, page)
        log(`page ${previous ? 'reconnected' : 'connected'}: ${event.page.url ?? '(no url)'}`)
        broadcast(pageList())
        broadcast({ type: 'reset', pageId: id })
        return
      }
      if (!page) return ws.close(1002)
      const pageId = page.summary.sessionId
      switch (event.type) {
        case 'added':
        case 'updated': {
          const id = event.message?.summary?.id
          if (typeof id !== 'number') return
          page.messages.set(id, event.message)
          if (page.messages.size > MAX_MESSAGES_PER_PAGE) page.messages.delete(page.messages.keys().next().value!)
          broadcast({ type: event.type, pageId, message: event.message })
          return
        }
        case 'removed':
          if (!Array.isArray(event.ids)) return
          for (const id of event.ids) page.messages.delete(id)
          broadcast({ type: 'removed', pageId, ids: event.ids })
          return
        case 'cleared':
          page.messages.clear()
          broadcast({ type: 'cleared', pageId })
      }
    }
    ws.onClose = () => {
      if (!page || page.socket !== ws) return
      page.socket = null
      page.summary = { ...page.summary, connected: false }
      log(`page disconnected: ${page.summary.url ?? '(no url)'}`)
      pruneClosedPages()
      broadcast(pageList())
    }
  }

  function acceptViewer(ws: WsConnection): void {
    viewers.add(ws)
    log(`viewer connected (${viewers.size} open)`)
    ws.send(JSON.stringify(pageList()))
    for (const [pageId, page] of pages) {
      for (const message of page.messages.values()) ws.send(JSON.stringify({ type: 'added', pageId, message } satisfies ViewerEvent))
    }
    ws.onMessage = (text) => {
      const command = parse<ViewerCommand>(text)
      if (command?.type !== 'clear') return
      const page = pages.get(command.pageId)
      if (!page) return
      if (page.socket) {
        page.socket.send(JSON.stringify({ type: 'clear' } satisfies PageCommand))
      } else {
        page.messages.clear()
        broadcast({ type: 'cleared', pageId: command.pageId })
      }
    }
    ws.onClose = () => {
      viewers.delete(ws)
    }
  }

  function pruneClosedPages(): void {
    const closed = [...pages.values()].filter((p) => !p.socket)
    for (const page of closed.slice(0, Math.max(0, closed.length - MAX_CLOSED_PAGES))) pages.delete(page.summary.sessionId)
  }

  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(options.port, '127.0.0.1', () => {
      port = (server.address() as AddressInfo).port
      resolve({
        port,
        close: () =>
          new Promise<void>((done) => {
            for (const viewer of viewers) viewer.close(1001)
            for (const page of pages.values()) page.socket?.close(1001)
            server.close(() => done())
          }),
      })
    })
  })
}

function send(response: ServerResponse, status: number, type: string, body: string): void {
  response.writeHead(status, {
    'Content-Type': type,
    'Cache-Control': 'no-store',
    'Content-Security-Policy': "default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; connect-src 'self'; img-src data:",
    'X-Content-Type-Options': 'nosniff',
  })
  response.end(body)
}

function parse<T>(text: string): T | null {
  try {
    const value = JSON.parse(text) as unknown
    return value && typeof value === 'object' ? (value as T) : null
  } catch {
    return null
  }
}
