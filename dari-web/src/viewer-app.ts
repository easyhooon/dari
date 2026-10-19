import { h } from './dom'
import type { PageSummary, ViewerCommand, ViewerEvent } from './remote-protocol'
import type { SocketLike } from './remote'
import { loadSettings, saveSettings, type Settings } from './settings'
import { STYLE } from './style'
import type { MessageEntry } from './types'
import { DariView, type EntrySource } from './view'
import { fromWireDetail } from './wire'

const RETRY_MILLIS = 1000
const OPEN = 1

const VIEWER_STYLE = `
.dari-viewer { position: fixed; inset: 0; display: flex; flex-direction: column; background: var(--dari-bg); color: var(--dari-text); font: 13px/1.45 system-ui, -apple-system, sans-serif; }
.dari-viewer-top { display: flex; align-items: center; gap: 8px; padding: 6px 14px; border-bottom: 1px solid var(--dari-border); background: var(--dari-surface); color: var(--dari-text-2); font-size: 12px; }
.dari-viewer-dot { flex: none; width: 8px; height: 8px; border-radius: 50%; background: var(--dari-danger); }
.dari-viewer-dot-on { background: var(--dari-success); }
.dari-viewer-page { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dari-viewer-top .dari-select { flex: none; max-width: 40%; }
.dari-viewer-main { flex: 1; min-height: 0; width: 100%; max-width: 1100px; margin: 0 auto; border-left: 1px solid var(--dari-border); border-right: 1px solid var(--dari-border); }
`

export interface ViewerOptions {
  /** Relay endpoint; defaults to `/viewer` on the serving host. */
  url?: string
  createSocket?: (url: string) => SocketLike
  parent?: HTMLElement
}

export interface Viewer {
  readonly host: HTMLElement
  destroy(): void
}

/** Full-window viewer for pages streamed through the relay. */
export function mountViewer(options: ViewerOptions = {}): Viewer {
  const url = options.url ?? `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/viewer`
  const createSocket = options.createSocket ?? ((u: string) => new WebSocket(u) as unknown as SocketLike)

  let pages: PageSummary[] = []
  let messages = new Map<string, Map<number, MessageEntry>>()
  const cache = new Map<string, MessageEntry[]>()
  let selected: string | null = null
  let pinned = false
  let socket: SocketLike | null = null
  let timer: ReturnType<typeof setTimeout> | undefined
  let destroyed = false
  const listeners = new Set<() => void>()

  const entriesOf = (pageId: string | null): readonly MessageEntry[] => {
    if (pageId === null) return []
    let list = cache.get(pageId)
    if (!list) {
      list = [...(messages.get(pageId)?.values() ?? [])]
      cache.set(pageId, list)
    }
    return list
  }
  const changed = (pageId: string) => {
    cache.delete(pageId)
    if (pageId === selected) for (const listener of listeners) listener()
  }

  const source: EntrySource = {
    getEntries: () => entriesOf(selected),
    subscribe: (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    clear: () => {
      if (selected !== null && socket?.readyState === OPEN) {
        socket.send(JSON.stringify({ type: 'clear', pageId: selected } satisfies ViewerCommand))
      }
    },
  }

  let settings: Settings = loadSettings()
  const host = h('div', { 'data-dari-web-viewer': '' })
  const applyTheme = () => {
    if (settings.theme === 'auto') host.removeAttribute('data-theme')
    else host.setAttribute('data-theme', settings.theme)
  }
  applyTheme()

  const view = new DariView(source, {
    settings: () => settings,
    updateSettings: (patch) => {
      settings = { ...settings, ...patch }
      saveSettings(settings)
      if ('theme' in patch) applyTheme()
      if ('foldJson' in patch || 'showTags' in patch) view.refresh()
    },
    exportContext: () => {
      const page = pages.find((p) => p.sessionId === selected)
      return { pageUrl: page?.url ?? null, userAgent: page?.userAgent ?? null, exportedAt: Date.now() }
    },
  })

  const dot = h('span', { class: 'dari-viewer-dot', title: 'Relay' })
  const pageLabel = h('span', { class: 'dari-viewer-page' })
  const picker = h('select', { class: 'dari-select', 'aria-label': 'Page' }) as HTMLSelectElement
  picker.addEventListener('change', () => {
    pinned = true
    select(picker.value)
  })
  const style = h('style')
  style.textContent = STYLE + VIEWER_STYLE
  const shadow = host.attachShadow({ mode: 'open' })
  shadow.append(
    style,
    h(
      'div',
      { class: 'dari-viewer' },
      h('div', { class: 'dari-viewer-top' }, dot, pageLabel, picker),
      h('div', { class: 'dari-viewer-main' }, view.root),
    ),
  )
  ;(options.parent ?? document.body).append(host)

  function select(pageId: string | null): void {
    if (pageId === selected) return renderTop()
    selected = pageId
    renderTop()
    for (const listener of listeners) listener()
  }

  // Follows the newest live page unless the user picked one that is still around.
  function autoSelect(): void {
    const current = pages.find((p) => p.sessionId === selected)
    const newestLive = [...pages].reverse().find((p) => p.connected)
    if (pinned && current && (current.connected || !newestLive || newestLive.connectedAt < current.connectedAt)) {
      return renderTop()
    }
    pinned = false
    select((newestLive ?? pages[pages.length - 1])?.sessionId ?? null)
  }

  function renderTop(): void {
    const live = socket?.readyState === OPEN
    dot.classList.toggle('dari-viewer-dot-on', live)
    dot.title = live ? 'Connected to relay' : 'Relay offline, retrying'
    const page = pages.find((p) => p.sessionId === selected)
    if (!live) pageLabel.textContent = 'Relay offline, retrying…'
    else if (!page) pageLabel.textContent = 'Waiting for a page with install({ remote })'
    else pageLabel.textContent = `${page.connected ? '' : '(closed) '}${page.title ?? page.url ?? 'Untitled page'}${page.title && page.url ? ` · ${page.url}` : ''}`
    pageLabel.title = page?.url ?? ''
    picker.replaceChildren(
      ...[...pages].reverse().map((p) =>
        h('option', { value: p.sessionId }, pageOptionLabel(p)),
      ),
    )
    picker.value = selected ?? ''
    picker.hidden = pages.length <= 1
    document.title = page ? `Dari · ${page.title ?? page.url ?? 'page'}` : 'Dari Web Viewer'
  }

  function handle(event: ViewerEvent): void {
    switch (event.type) {
      case 'pages':
        pages = event.pages
        for (const id of messages.keys()) if (!pages.some((p) => p.sessionId === id)) messages.delete(id)
        autoSelect()
        return
      case 'reset':
        messages.set(event.pageId, new Map())
        changed(event.pageId)
        return
      case 'added':
      case 'updated': {
        let page = messages.get(event.pageId)
        if (!page) messages.set(event.pageId, (page = new Map()))
        const entry = fromWireDetail(event.message)
        page.set(entry.id, entry)
        changed(event.pageId)
        return
      }
      case 'removed': {
        const page = messages.get(event.pageId)
        for (const id of event.ids) page?.delete(id)
        changed(event.pageId)
        return
      }
      case 'cleared':
        messages.get(event.pageId)?.clear()
        changed(event.pageId)
    }
  }

  function connect(): void {
    if (destroyed) return
    let ws: SocketLike
    try {
      ws = createSocket(url)
    } catch {
      timer = setTimeout(connect, RETRY_MILLIS)
      return
    }
    socket = ws
    ws.onopen = () => {
      // The relay replays everything on connect.
      messages = new Map()
      cache.clear()
      pages = []
      renderTop()
      for (const listener of listeners) listener()
    }
    ws.onmessage = (event) => {
      if (typeof event.data !== 'string') return
      try {
        handle(JSON.parse(event.data) as ViewerEvent)
      } catch {
        // Malformed frame; skip it.
      }
    }
    ws.onclose = () => {
      if (socket !== ws) return
      socket = null
      renderTop()
      timer = setTimeout(connect, RETRY_MILLIS)
    }
    ws.onerror = () => {}
  }

  renderTop()
  connect()

  return {
    host,
    destroy: () => {
      destroyed = true
      clearTimeout(timer)
      const ws = socket
      socket = null
      ws?.close()
      view.destroy()
      host.remove()
    },
  }
}

// Several tabs of one page look alike, so name the device and when it connected.
export function pageOptionLabel(page: PageSummary): string {
  const d = new Date(page.connectedAt)
  const time = [d.getHours(), d.getMinutes(), d.getSeconds()].map((n) => String(n).padStart(2, '0')).join(':')
  const device = deviceOf(page.userAgent)
  return `${page.connected ? '' : '(closed) '}${page.title ?? page.url ?? 'Untitled page'} · ${device ? `${device} ` : ''}${time}`
}

export function deviceOf(userAgent: string | null): string | null {
  if (!userAgent) return null
  if (/; wv\)/.test(userAgent)) return 'Android WebView'
  if (/Android/.test(userAgent)) return 'Android'
  if (/iPhone|iPad|iPod/.test(userAgent)) return /Safari\//.test(userAgent) ? 'iOS Safari' : 'iOS WebView'
  if (/Macintosh/.test(userAgent)) return 'Mac'
  if (/Windows/.test(userAgent)) return 'Windows'
  if (/Linux/.test(userAgent)) return 'Linux'
  return null
}
