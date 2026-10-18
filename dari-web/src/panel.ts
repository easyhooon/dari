import type { MessageStore } from './store'
import { STYLE } from './style'
import { DariView, h } from './view'

const DRAG_THRESHOLD = 5
const EDGE = 8

/** Floating launcher + sheet, isolated from page CSS in a shadow root. */
export class DariPanel {
  readonly host: HTMLElement
  private readonly view: DariView
  private readonly launcher: HTMLElement
  private readonly badge: HTMLElement
  private readonly backdrop: HTMLElement
  private readonly sheet: HTMLElement
  private readonly unsubscribe: () => void
  private readonly onKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && this.isOpen) this.hide()
  }
  private dragged = false

  constructor(
    private readonly store: MessageStore,
    theme: 'auto' | 'light' | 'dark' = 'auto',
  ) {
    this.host = h('div', { 'data-dari-web': '' })
    if (theme !== 'auto') this.host.setAttribute('data-theme', theme)
    const shadow = this.host.attachShadow({ mode: 'open' })

    this.badge = h('span', { class: 'dari-badge' }, '0')
    this.launcher = h('button', { class: 'dari-launcher', type: 'button', 'aria-label': 'Open Dari' }, 'Dari', this.badge)
    this.launcher.addEventListener('click', () => {
      if (this.dragged) return
      this.show()
    })
    this.enableDrag(this.launcher)

    const clear = h('button', { class: 'dari-icon-btn dari-clear', type: 'button' }, 'Clear')
    clear.addEventListener('click', () => store.clear())
    const close = h('button', { class: 'dari-icon-btn dari-close', type: 'button', 'aria-label': 'Close' }, '✕')
    close.addEventListener('click', () => this.hide())

    this.view = new DariView(store)
    this.backdrop = h('div', { class: 'dari-backdrop' })
    this.backdrop.addEventListener('click', () => this.hide())
    this.sheet = h(
      'div',
      { class: 'dari-sheet', role: 'dialog', 'aria-label': 'Dari' },
      h('div', { class: 'dari-header' }, h('span', { class: 'dari-header-title' }, 'Dari'), clear, close),
      h('div', { class: 'dari-body' }, this.view.root),
    )
    this.backdrop.hidden = true
    this.sheet.hidden = true

    const style = h('style')
    style.textContent = STYLE
    shadow.append(style, this.launcher, this.backdrop, this.sheet)

    this.unsubscribe = store.subscribe(() => this.updateBadge())
    this.updateBadge()
    document.addEventListener('keydown', this.onKeydown)
  }

  get isOpen(): boolean {
    return !this.sheet.hidden
  }

  show(): void {
    this.launcher.hidden = true
    this.backdrop.hidden = false
    this.sheet.hidden = false
  }

  hide(): void {
    this.launcher.hidden = false
    this.backdrop.hidden = true
    this.sheet.hidden = true
  }

  destroy(): void {
    this.unsubscribe()
    this.view.destroy()
    document.removeEventListener('keydown', this.onKeydown)
    this.host.remove()
  }

  private updateBadge(): void {
    const entries = this.store.getEntries()
    this.badge.textContent = String(entries.length)
    this.badge.classList.toggle(
      'dari-badge-error',
      entries.some((e) => e.status === 'ERROR'),
    )
  }

  // The launcher can be dragged out of the way of the page's own controls.
  private enableDrag(el: HTMLElement): void {
    let start: { x: number; y: number; left: number; top: number } | null = null
    el.addEventListener('pointerdown', (event) => {
      const rect = el.getBoundingClientRect()
      start = { x: event.clientX, y: event.clientY, left: rect.left, top: rect.top }
      this.dragged = false
      el.setPointerCapture?.(event.pointerId)
    })
    el.addEventListener('pointermove', (event) => {
      if (!start) return
      const dx = event.clientX - start.x
      const dy = event.clientY - start.y
      if (!this.dragged && Math.hypot(dx, dy) < DRAG_THRESHOLD) return
      this.dragged = true
      const maxLeft = window.innerWidth - el.offsetWidth - EDGE
      const maxTop = window.innerHeight - el.offsetHeight - EDGE
      el.style.left = `${clamp(start.left + dx, EDGE, maxLeft)}px`
      el.style.top = `${clamp(start.top + dy, EDGE, maxTop)}px`
      el.style.right = 'auto'
      el.style.bottom = 'auto'
    })
    const end = () => {
      start = null
    }
    el.addEventListener('pointerup', end)
    el.addEventListener('pointercancel', end)
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), Math.max(min, max))
}
