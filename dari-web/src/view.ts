import type { MessageEntry } from './types'
import type { MessageStore } from './store'
import { STYLE } from './style'
import { pageOriginOf } from './url'
import { utf8Size } from './wire'

type Attrs = Record<string, string>
type Child = Node | string | null | false

// Payloads are untrusted page data, so everything goes through textContent, never innerHTML.
function h(tag: string, attrs: Attrs = {}, ...children: Child[]): HTMLElement {
  const el = document.createElement(tag)
  for (const [key, value] of Object.entries(attrs)) el.setAttribute(key, value)
  for (const child of children) {
    if (child === null || child === false) continue
    el.append(typeof child === 'string' ? document.createTextNode(child) : child)
  }
  return el
}

/** List + detail UI for the vConsole tab. */
export class DariView {
  readonly root: HTMLElement
  private readonly content: HTMLElement
  private readonly filterInput: HTMLInputElement
  private readonly count: HTMLElement
  private readonly bar: HTMLElement
  private selectedId: number | null = null
  private readonly unsubscribe: () => void

  constructor(private readonly store: MessageStore) {
    this.filterInput = h('input', {
      class: 'dari-filter',
      type: 'search',
      placeholder: 'Filter handler, id, payload',
    }) as HTMLInputElement
    this.filterInput.addEventListener('input', () => this.render())
    this.count = h('span', { class: 'dari-count' })
    this.bar = h('div', { class: 'dari-bar' }, this.filterInput, this.count)
    this.content = h('div')
    const style = h('style')
    style.textContent = STYLE
    this.root = h('div', { class: 'dari-root' }, style, this.bar, this.content)
    this.unsubscribe = store.subscribe(() => this.render())
    this.render()
  }

  destroy(): void {
    this.unsubscribe()
  }

  render(): void {
    const entries = this.store.getEntries()
    const selected = this.selectedId === null ? undefined : entries.find((e) => e.id === this.selectedId)
    if (selected) {
      this.bar.hidden = true
      this.content.replaceChildren(this.renderDetail(selected))
      return
    }
    this.selectedId = null
    this.bar.hidden = false
    const query = this.filterInput.value.trim().toLowerCase()
    const visible = (query ? entries.filter((e) => matches(e, query)) : entries).slice().reverse()
    this.count.textContent = query ? `${visible.length} / ${entries.length}` : `${entries.length}`
    if (visible.length === 0) {
      this.content.replaceChildren(
        h('div', { class: 'dari-empty' }, entries.length === 0 ? 'No bridge messages yet' : 'No matches'),
      )
      return
    }
    this.content.replaceChildren(...visible.map((e) => this.renderRow(e)))
  }

  private renderRow(entry: MessageEntry): HTMLElement {
    const row = h(
      'div',
      { class: 'dari-row', role: 'button', tabindex: '0', 'data-id': String(entry.id) },
      h('span', { class: 'dari-dir', title: directionLabel(entry) }, entry.direction === 'WEB_TO_APP' ? '↑' : '↓'),
      h(
        'span',
        { class: 'dari-name' },
        entry.displayName ?? entry.handlerName,
        entry.displayName ? h('span', { class: 'dari-handler' }, entry.handlerName) : null,
      ),
      h('span', { class: `dari-status dari-status-${entry.status}` }, statusLabel(entry)),
      h(
        'span',
        { class: 'dari-meta' },
        h('span', {}, formatTime(entry.requestTimestamp)),
        h('span', {}, durationLabel(entry)),
        entry.requestId ? h('span', {}, `#${entry.requestId}`) : null,
      ),
    )
    const open = () => {
      this.selectedId = entry.id
      this.render()
    }
    row.addEventListener('click', open)
    row.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') open()
    })
    return row
  }

  private renderDetail(entry: MessageEntry): HTMLElement {
    const back = h('button', { class: 'dari-back', type: 'button' }, '‹ Back')
    back.addEventListener('click', () => {
      this.selectedId = null
      this.render()
    })
    const rows: Array<[string, string | null]> = [
      ['Direction', directionLabel(entry)],
      ['Status', statusLabel(entry)],
      ['Request ID', entry.requestId],
      ['Handler', entry.handlerName],
      ['Tag', entry.tag],
      ['Page', entry.pageUrl],
      ['Origin', pageOriginOf(entry.pageUrl)],
      ['Started', formatTime(entry.requestTimestamp)],
      ['Duration', durationLabel(entry)],
      ['Size', `${formatBytes(utf8Size(entry.requestData))} / ${formatBytes(utf8Size(entry.responseData))}`],
    ]
    return h(
      'div',
      { class: 'dari-detail' },
      h('div', { class: 'dari-bar' }, back, h('span', { class: 'dari-title' }, entry.displayName ?? entry.handlerName)),
      h(
        'table',
        { class: 'dari-table' },
        h(
          'tbody',
          {},
          ...rows.filter(([, v]) => v !== null && v !== '').map(([k, v]) => h('tr', {}, h('th', {}, k), h('td', {}, v))),
        ),
      ),
      payloadSection('Request', entry.requestData, entry.requestDataTruncated),
      entry.status === 'IN_PROGRESS' && entry.responseData === null
        ? null
        : payloadSection('Response', entry.responseData, entry.responseDataTruncated),
    )
  }
}

function payloadSection(title: string, data: string | null, truncated: boolean): HTMLElement {
  const head = h('div', { class: 'dari-section-head' }, truncated ? `${title} (truncated)` : title)
  if (data !== null && typeof navigator !== 'undefined' && navigator.clipboard) {
    const copy = h('button', { class: 'dari-copy', type: 'button' }, 'Copy')
    copy.addEventListener('click', () => {
      navigator.clipboard.writeText(data).then(
        () => (copy.textContent = 'Copied'),
        () => (copy.textContent = 'Copy failed'),
      )
    })
    head.append(copy)
  }
  const body = data === null ? h('div', { class: 'dari-none' }, 'No data') : h('pre', { class: 'dari-pre' }, pretty(data))
  return h('div', { class: 'dari-section' }, head, body)
}

export function pretty(data: string): string {
  const trimmed = data.trimStart()
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return data
  try {
    return JSON.stringify(JSON.parse(data), null, 2)
  } catch {
    return data
  }
}

function matches(entry: MessageEntry, query: string): boolean {
  return [entry.handlerName, entry.displayName, entry.requestId, entry.requestData, entry.responseData].some(
    (v) => v !== null && v.toLowerCase().includes(query),
  )
}

function directionLabel(entry: MessageEntry): string {
  return entry.direction === 'WEB_TO_APP' ? 'Web → App' : 'App → Web'
}

function statusLabel(entry: MessageEntry): string {
  return entry.status === 'IN_PROGRESS' ? 'Pending' : entry.status === 'SUCCESS' ? 'OK' : 'Error'
}

function durationLabel(entry: MessageEntry): string {
  return entry.responseTimestamp === null ? '–' : `${entry.responseTimestamp - entry.requestTimestamp} ms`
}

function formatTime(millis: number): string {
  const d = new Date(millis)
  const pad = (n: number, w = 2) => String(n).padStart(w, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
