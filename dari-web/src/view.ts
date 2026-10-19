import { button, copyText, download, h, highlighted } from './dom'
import { browserContext, exportJson, exportText, formatSize, messageText, suggestedFileName, VERSION, type ExportContext } from './export'
import { distinctOrigins, distinctTags, EMPTY_FILTER, filterEntries, type Filter } from './filter'
import { allContainerPaths, defaultCollapsedPaths, parseStructured, pathsContainingMatch, renderTree } from './json'
import type { Settings, Theme } from './settings'
import { handlerStats, SLOW_HANDLER_THRESHOLD_MS } from './stats'
import type { MessageDirection, MessageEntry, MessageStatus } from './types'
import { pageOriginOf } from './url'
import { utf8Size } from './wire'

export { h } from './dom'

/** What the view reads entries from: the page's own store, or a page streamed to the viewer. */
export interface EntrySource {
  getEntries(): readonly MessageEntry[]
  subscribe(listener: () => void): () => void
  clear(): void
}

export interface ViewHost {
  settings(): Settings
  updateSettings(patch: Partial<Settings>): void
  /** Omitted where there's nothing to close, which hides ✕. */
  close?(): void
  /** Offered in Settings only where there is a launcher. */
  resetLauncher?(): void
  /** Defaults to this browser and page. */
  exportContext?(): ExportContext
}

type Mode = { kind: 'list' } | { kind: 'detail'; id: number } | { kind: 'stats' } | { kind: 'settings' }

interface FoldState {
  data: string
  collapsed: Set<string>
}

const NOTICE_MILLIS = 2000

/** Header, filters and the list / detail / stats / settings screens inside the panel. */
export class DariView {
  readonly root: HTMLElement
  private readonly body: HTMLElement
  private readonly bar: HTMLElement
  private readonly search: HTMLInputElement
  private readonly count: HTMLElement
  private readonly directionSelect: HTMLSelectElement
  private readonly statusSelect: HTMLSelectElement
  private readonly tagSelect: HTMLSelectElement
  private readonly originSelect: HTMLSelectElement
  private readonly menu: HTMLElement
  private readonly notice: HTMLElement
  private readonly unsubscribe: () => void
  private mode: Mode = { kind: 'list' }
  private filter: Filter = { ...EMPTY_FILTER }
  private folds = new Map<string, FoldState>()
  private renderedDetail: { entry: MessageEntry; query: string; fold: boolean; version: number } | null = null
  private foldVersion = 0
  private scrolledTo: string | null = null
  private noticeTimer: ReturnType<typeof setTimeout> | undefined

  constructor(
    private readonly store: EntrySource,
    private readonly host: ViewHost,
  ) {
    this.search = h('input', {
      class: 'dari-filter',
      type: 'search',
      placeholder: 'Search handler, id, payload',
      'aria-label': 'Search',
    }) as HTMLInputElement
    this.search.addEventListener('input', () => this.setFilter({ query: this.search.value }))
    this.count = h('span', { class: 'dari-count' })

    this.directionSelect = select('Direction', [
      ['', 'All directions'],
      ['WEB_TO_APP', 'Web → App'],
      ['APP_TO_WEB', 'App → Web'],
    ])
    this.directionSelect.addEventListener('change', () =>
      this.setFilter({ direction: (this.directionSelect.value || null) as MessageDirection | null }),
    )
    this.statusSelect = select('Status', [
      ['', 'All statuses'],
      ['IN_PROGRESS', 'Pending'],
      ['SUCCESS', 'OK'],
      ['ERROR', 'Error'],
    ])
    this.statusSelect.addEventListener('change', () =>
      this.setFilter({ status: (this.statusSelect.value || null) as MessageStatus | null }),
    )
    this.tagSelect = select('Tag', [])
    this.tagSelect.addEventListener('change', () => this.setFilter({ tag: this.tagSelect.value || null }))
    this.originSelect = select('Origin', [])
    this.originSelect.addEventListener('change', () => this.setFilter({ origin: this.originSelect.value || null }))

    this.bar = h(
      'div',
      { class: 'dari-bar' },
      h('div', { class: 'dari-bar-row' }, this.search, this.count),
      h('div', { class: 'dari-bar-row dari-selects' }, this.directionSelect, this.statusSelect, this.tagSelect, this.originSelect),
    )

    this.menu = h(
      'div',
      { class: 'dari-menu', role: 'menu' },
      this.menuItem('Response Time', () => this.go({ kind: 'stats' })),
      this.menuItem('Copy all as Text', () => this.copyAll('txt')),
      this.menuItem('Copy all as JSON', () => this.copyAll('json')),
      this.menuItem('Download JSON', () => this.downloadAll()),
      this.menuItem('Settings', () => this.go({ kind: 'settings' })),
    )
    this.menu.hidden = true
    const header = h(
      'div',
      { class: 'dari-header' },
      h('span', { class: 'dari-header-title' }, 'Dari'),
      button('Clear', 'dari-icon-btn dari-clear', () => store.clear()),
      button('⋯', 'dari-icon-btn dari-more', () => (this.menu.hidden = !this.menu.hidden), {
        'aria-label': 'More',
        'aria-haspopup': 'menu',
      }),
      host.close ? button('✕', 'dari-icon-btn dari-close', () => host.close?.(), { 'aria-label': 'Close' }) : null,
    )
    this.body = h('div', { class: 'dari-body' })
    this.notice = h('div', { class: 'dari-notice', role: 'status' })
    this.notice.hidden = true
    this.root = h('div', { class: 'dari-root' }, header, this.bar, this.body, this.menu, this.notice)
    this.root.addEventListener('click', (event) => {
      const target = event.target as Element
      if (!this.menu.hidden && !target.closest('.dari-menu, .dari-more')) this.menu.hidden = true
    })

    this.unsubscribe = store.subscribe(() => this.render())
    this.render()
  }

  destroy(): void {
    this.unsubscribe()
    clearTimeout(this.noticeTimer)
  }

  /** Re-renders everything, e.g. after a settings change. */
  refresh(): void {
    this.renderedDetail = null
    this.render()
  }

  render(): void {
    const entries = this.store.getEntries()
    this.syncOptionalSelect(this.tagSelect, 'All tags', distinctTags(entries), this.filter.tag, 0)
    this.syncOptionalSelect(this.originSelect, 'All origins', distinctOrigins(entries), this.filter.origin, 1)
    const visible = filterEntries(entries, this.filter)
    this.count.textContent = isFiltering(this.filter) ? `${visible.length} / ${entries.length}` : `${entries.length}`

    switch (this.mode.kind) {
      case 'detail': {
        const id = this.mode.id
        const entry = entries.find((e) => e.id === id)
        if (entry) {
          this.bar.hidden = true
          this.renderDetail(entry)
          return
        }
        this.mode = { kind: 'list' }
        this.renderList(entries, visible)
        return
      }
      case 'stats':
        this.renderStats(entries, visible)
        return
      case 'settings':
        this.renderSettings()
        return
      case 'list':
        this.renderList(entries, visible)
    }
  }

  private go(mode: Mode): void {
    this.menu.hidden = true
    this.mode = mode
    this.renderedDetail = null
    this.body.scrollTop = 0
    this.render()
  }

  private setFilter(patch: Partial<Filter>): void {
    this.filter = { ...this.filter, ...patch }
    this.scrolledTo = null
    this.render()
  }

  // Shown only once there is a choice to make, or while a value is selected, like the desktop app.
  private syncOptionalSelect(el: HTMLSelectElement, allLabel: string, values: string[], selected: string | null, min: number): void {
    const options: Array<[string, string]> = [['', allLabel], ...values.map((v): [string, string] => [v, v])]
    if (selected !== null && !values.includes(selected)) options.push([selected, selected])
    const signature = options.map(([v]) => v).join('\u0000')
    if (el.dataset.signature !== signature) {
      el.replaceChildren(...options.map(([value, label]) => h('option', { value }, label)))
      el.dataset.signature = signature
    }
    el.value = selected ?? ''
    el.hidden = values.length <= min && selected === null
  }

  private renderList(entries: readonly MessageEntry[], visible: MessageEntry[]): void {
    this.bar.hidden = false
    this.renderedDetail = null
    if (visible.length === 0) {
      this.body.replaceChildren(h('div', { class: 'dari-empty' }, entries.length === 0 ? 'No bridge messages yet' : 'No matches'))
      return
    }
    const showTags = this.host.settings().showTags
    this.body.replaceChildren(...visible.slice().reverse().map((e) => this.renderRow(e, showTags)))
  }

  private renderRow(entry: MessageEntry, showTags: boolean): HTMLElement {
    const row = h(
      'div',
      { class: `dari-row dari-row-${entry.status}`, role: 'button', tabindex: '0', 'data-id': String(entry.id) },
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
        showTags && entry.tag ? h('span', { class: 'dari-tag' }, entry.tag) : null,
      ),
    )
    const open = () => this.go({ kind: 'detail', id: entry.id })
    row.addEventListener('click', open)
    row.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') open()
    })
    return row
  }

  private renderDetail(entry: MessageEntry): void {
    const query = this.filter.query.trim()
    const fold = this.host.settings().foldJson
    const last = this.renderedDetail
    // Unrelated arrivals shouldn't rebuild the payload the user is reading.
    if (last && last.entry === entry && last.query === query && last.fold === fold && last.version === this.foldVersion) return
    this.renderedDetail = { entry, query, fold, version: this.foldVersion }

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
      ['Size', `${formatSize(utf8Size(entry.requestData))} / ${formatSize(utf8Size(entry.responseData))}`],
    ]
    const scrollTop = this.body.scrollTop
    this.body.replaceChildren(
      h(
        'div',
        { class: 'dari-detail' },
        this.backBar(
          entry.displayName ?? entry.handlerName,
          button('Copy as Text', 'dari-link', () => this.copy(messageText(entry), 'Copied message')),
        ),
        h(
          'table',
          { class: 'dari-table' },
          h(
            'tbody',
            {},
            ...rows
              .filter(([, v]) => v !== null && v !== '')
              .map(([k, v]) => h('tr', {}, h('th', {}, k), h('td', {}, ...highlighted(v!, query)))),
          ),
        ),
        this.payloadSection(entry, 'Request', entry.requestData, entry.requestDataTruncated, query, fold),
        entry.status === 'IN_PROGRESS' && entry.responseData === null
          ? null
          : this.payloadSection(entry, 'Response', entry.responseData, entry.responseDataTruncated, query, fold),
      ),
    )
    const target = `${entry.id}\u0000${query}`
    if (query !== '' && this.scrolledTo !== target) {
      this.scrolledTo = target
      this.body.querySelector('.dari-section .dari-match')?.scrollIntoView?.({ block: 'center' })
    } else {
      this.body.scrollTop = scrollTop
    }
  }

  private payloadSection(
    entry: MessageEntry,
    title: string,
    data: string | null,
    truncated: boolean,
    query: string,
    fold: boolean,
  ): HTMLElement {
    const head = h('div', { class: 'dari-section-head' }, h('span', { class: 'dari-section-title' }, truncated ? `${title} (truncated)` : title))
    const actions = h('span', { class: 'dari-section-actions' })
    head.append(actions)
    if (data === null) return h('div', { class: 'dari-section' }, head, h('div', { class: 'dari-none' }, 'No data'))

    const value = parseStructured(data)
    let body: HTMLElement
    if (value === undefined) {
      body = h('pre', { class: 'dari-pre' }, ...highlighted(data, query))
    } else {
      const key = `${entry.id}:${title}`
      let state = this.folds.get(key)
      if (!state || state.data !== data) {
        const collapsed = defaultCollapsedPaths(value)
        for (const path of pathsContainingMatch(value, query)) collapsed.delete(path)
        state = { data, collapsed }
        this.folds.set(key, state)
      }
      const fs = state
      const rerender = () => {
        this.foldVersion++
        this.render()
      }
      if (fold && allContainerPaths(value).size > 0) {
        actions.append(
          button('Expand all', 'dari-link', () => {
            fs.collapsed.clear()
            rerender()
          }),
          button('Collapse all', 'dari-link', () => {
            fs.collapsed = allContainerPaths(value)
            rerender()
          }),
        )
      }
      body = h(
        'div',
        { class: 'dari-pre' },
        renderTree(value, {
          collapsed: fs.collapsed,
          folding: fold,
          query,
          onToggle: (path) => {
            if (!fs.collapsed.delete(path)) fs.collapsed.add(path)
            rerender()
          },
        }),
      )
    }
    actions.append(button('Copy', 'dari-link', () => this.copy(prettyCopy(data), `Copied ${title.toLowerCase()}`)))
    return h('div', { class: 'dari-section' }, head, body)
  }

  private renderStats(entries: readonly MessageEntry[], visible: MessageEntry[]): void {
    this.bar.hidden = false
    const stats = handlerStats(visible)
    const caption = isFiltering(this.filter) ? `${visible.length} of ${entries.length} messages` : `${entries.length} messages`
    const content =
      stats.length === 0
        ? h('div', { class: 'dari-empty' }, 'No calls to measure yet')
        : h(
            'table',
            { class: 'dari-stats' },
            h('thead', {}, h('tr', {}, ...['Handler', 'Calls', 'Errors', 'Avg', 'Min', 'Max'].map((t) => h('th', {}, t)))),
            h(
              'tbody',
              {},
              ...stats.map((s) =>
                h(
                  'tr',
                  {},
                  h(
                    'td',
                    { class: 'dari-stats-name' },
                    h('span', { class: 'dari-dir' }, s.direction === 'WEB_TO_APP' ? '↑ ' : '↓ '),
                    s.displayName ?? s.handlerName,
                    s.displayName ? h('span', { class: 'dari-handler' }, s.handlerName) : null,
                  ),
                  h('td', {}, String(s.callCount)),
                  h('td', s.errorCount > 0 ? { class: 'dari-stats-error' } : {}, String(s.errorCount)),
                  h('td', (s.avgMs ?? 0) >= SLOW_HANDLER_THRESHOLD_MS ? { class: 'dari-stats-slow' } : {}, ms(s.avgMs)),
                  h('td', {}, ms(s.minMs)),
                  h('td', {}, ms(s.maxMs)),
                ),
              ),
            ),
          )
    this.body.replaceChildren(
      h('div', { class: 'dari-detail' }, this.backBar('Response Time', h('span', { class: 'dari-count' }, caption)), content),
    )
  }

  private renderSettings(): void {
    this.bar.hidden = true
    const settings = this.host.settings()
    const theme = select('Theme', [
      ['auto', 'Match system'],
      ['light', 'Light'],
      ['dark', 'Dark'],
    ])
    theme.value = settings.theme
    theme.addEventListener('change', () => this.host.updateSettings({ theme: theme.value as Theme }))
    const reset = this.host.resetLauncher
      ? button('Reset button position', 'dari-link', () => {
          this.host.resetLauncher?.()
          this.toast('Button moved back to the corner')
        })
      : null
    this.body.replaceChildren(
      h(
        'div',
        { class: 'dari-detail' },
        this.backBar('Settings'),
        h(
          'div',
          { class: 'dari-settings' },
          h('label', { class: 'dari-setting' }, h('span', {}, 'Appearance'), theme),
          this.checkbox('Fold nested JSON', settings.foldJson, (foldJson) => this.host.updateSettings({ foldJson })),
          this.checkbox('Show tags in the list', settings.showTags, (showTags) => this.host.updateSettings({ showTags })),
          reset ? h('div', { class: 'dari-setting' }, reset) : null,
          h('div', { class: 'dari-version' }, `dari-web ${VERSION}`),
        ),
      ),
    )
  }

  private checkbox(label: string, checked: boolean, onChange: (checked: boolean) => void): HTMLElement {
    const input = h('input', { type: 'checkbox' }) as HTMLInputElement
    input.checked = checked
    input.addEventListener('change', () => onChange(input.checked))
    return h('label', { class: 'dari-setting' }, h('span', {}, label), input)
  }

  private backBar(title: string, trailing?: HTMLElement): HTMLElement {
    return h(
      'div',
      { class: 'dari-bar dari-back-bar' },
      button('‹ Back', 'dari-back', () => this.go({ kind: 'list' })),
      h('span', { class: 'dari-title' }, title),
      trailing,
    )
  }

  private menuItem(label: string, onClick: () => void): HTMLElement {
    return button(label, 'dari-menu-item', onClick, { role: 'menuitem' })
  }

  /** Exports follow the current filters, oldest first, like the desktop app. */
  private exportable(): MessageEntry[] {
    return filterEntries(this.store.getEntries(), this.filter)
  }

  private exportContext(): ExportContext {
    return this.host.exportContext?.() ?? browserContext()
  }

  private copyAll(kind: 'txt' | 'json'): void {
    this.menu.hidden = true
    const entries = this.exportable()
    if (entries.length === 0) return this.toast('Nothing to copy')
    const context = this.exportContext()
    const text = kind === 'txt' ? exportText(entries, context) : exportJson(entries, context)
    this.copy(text, `Copied ${plural(entries.length)}`)
  }

  private downloadAll(): void {
    this.menu.hidden = true
    const entries = this.exportable()
    if (entries.length === 0) return this.toast('Nothing to save')
    try {
      download(suggestedFileName('json'), exportJson(entries, this.exportContext()), 'application/json')
      this.toast(`Saved ${plural(entries.length)}`)
    } catch {
      this.toast('Download failed; try Copy all as JSON')
    }
  }

  private copy(text: string, success: string): void {
    void copyText(text).then((ok) => this.toast(ok ? success : 'Copy failed'))
  }

  private toast(text: string): void {
    clearTimeout(this.noticeTimer)
    this.notice.textContent = text
    this.notice.hidden = false
    this.noticeTimer = setTimeout(() => (this.notice.hidden = true), NOTICE_MILLIS)
  }
}

function select(label: string, options: Array<[string, string]>): HTMLSelectElement {
  return h(
    'select',
    { class: 'dari-select', 'aria-label': label },
    ...options.map(([value, text]) => h('option', { value }, text)),
  ) as HTMLSelectElement
}

function isFiltering(filter: Filter): boolean {
  return filter.query.trim() !== '' || filter.direction !== null || filter.status !== null || filter.tag !== null || filter.origin !== null
}

function prettyCopy(data: string): string {
  return parseStructured(data) === undefined ? data : JSON.stringify(JSON.parse(data), null, 2)
}

function plural(n: number): string {
  return n === 1 ? '1 message' : `${n} messages`
}

function ms(value: number | null): string {
  return value === null ? '–' : `${value} ms`
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
