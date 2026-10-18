import { h } from './dom'
import { loadSettings, saveSettings, type Settings, type Theme } from './settings'
import type { MessageStore } from './store'
import { STYLE } from './style'
import { DariView } from './view'

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
  private readonly onResize = () => this.placeLauncher()
  private settings: Settings
  private dragged = false

  constructor(
    private readonly store: MessageStore,
    theme?: Theme,
  ) {
    // A viewer's saved choice beats the theme the page was installed with.
    this.settings = loadSettings(theme ? { theme } : {})
    this.host = h('div', { 'data-dari-web': '' })
    this.applyTheme()
    const shadow = this.host.attachShadow({ mode: 'open' })

    this.badge = h('span', { class: 'dari-badge' }, '0')
    this.launcher = h('button', { class: 'dari-launcher', type: 'button', 'aria-label': 'Open Dari' }, 'Dari', this.badge)
    this.launcher.addEventListener('click', () => {
      if (this.dragged) return
      this.show()
    })
    this.enableDrag(this.launcher)

    this.view = new DariView(store, {
      settings: () => this.settings,
      updateSettings: (patch) => this.updateSettings(patch),
      close: () => this.hide(),
    })
    this.backdrop = h('div', { class: 'dari-backdrop' })
    this.backdrop.addEventListener('click', () => this.hide())
    this.sheet = h('div', { class: 'dari-sheet', role: 'dialog', 'aria-label': 'Dari' }, this.view.root)
    this.backdrop.hidden = true
    this.sheet.hidden = true

    const style = h('style')
    style.textContent = STYLE
    shadow.append(style, this.launcher, this.backdrop, this.sheet)

    this.unsubscribe = store.subscribe(() => this.updateBadge())
    this.updateBadge()
    document.addEventListener('keydown', this.onKeydown)
    window.addEventListener('resize', this.onResize)
    // Needs layout for the launcher size, so wait until the host is mounted.
    requestAnimationFrame(() => this.placeLauncher())
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
    this.placeLauncher()
  }

  destroy(): void {
    this.unsubscribe()
    this.view.destroy()
    document.removeEventListener('keydown', this.onKeydown)
    window.removeEventListener('resize', this.onResize)
    this.host.remove()
  }

  private updateSettings(patch: Partial<Settings>): void {
    this.settings = { ...this.settings, ...patch }
    saveSettings(this.settings)
    if ('theme' in patch) this.applyTheme()
    if ('launcher' in patch) this.placeLauncher()
    if ('foldJson' in patch || 'showTags' in patch) this.view.refresh()
  }

  private applyTheme(): void {
    if (this.settings.theme === 'auto') this.host.removeAttribute('data-theme')
    else this.host.setAttribute('data-theme', this.settings.theme)
  }

  private updateBadge(): void {
    const entries = this.store.getEntries()
    this.badge.textContent = String(entries.length)
    this.badge.classList.toggle(
      'dari-badge-error',
      entries.some((e) => e.status === 'ERROR'),
    )
  }

  // Saved positions are clamped, since the viewport may have shrunk since (rotation, split screen).
  private placeLauncher(): void {
    const el = this.launcher
    const saved = this.settings.launcher
    if (!saved) {
      el.style.left = el.style.top = el.style.right = el.style.bottom = ''
      return
    }
    if (el.hidden) return
    el.style.left = `${clamp(saved.left, EDGE, window.innerWidth - el.offsetWidth - EDGE)}px`
    el.style.top = `${clamp(saved.top, EDGE, window.innerHeight - el.offsetHeight - EDGE)}px`
    el.style.right = 'auto'
    el.style.bottom = 'auto'
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
      if (start && this.dragged) {
        const rect = el.getBoundingClientRect()
        this.settings = { ...this.settings, launcher: { left: Math.round(rect.left), top: Math.round(rect.top) } }
        saveSettings(this.settings)
      }
      start = null
    }
    el.addEventListener('pointerup', end)
    el.addEventListener('pointercancel', end)
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), Math.max(min, max))
}
