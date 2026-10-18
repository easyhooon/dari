import { DariPanel } from './panel'
import { MessageStore } from './store'
import type { DariWebOptions, MessageEntry, Recorder } from './types'

export interface DariWeb {
  /** Report traffic by hand, e.g. from code that `hook` can't reach. */
  readonly recorder: Recorder
  getEntries(): readonly MessageEntry[]
  clear(): void
  show(): void
  hide(): void
  /** Undoes `hook` and removes the panel. */
  uninstall(): void
}

let installed: DariWeb | null = null

/** Adds the floating Dari panel to the page and starts recording bridge traffic through `options.hook`. */
export function install(options: DariWebOptions = {}): DariWeb {
  if (installed) throw new Error('Dari is already installed; call uninstall() first')
  const store = new MessageStore(options)
  const panel = new DariPanel(store, options.theme)
  const mount = () => document.body.append(panel.host)
  if (document.body) {
    mount()
  } else {
    document.addEventListener('DOMContentLoaded', mount, { once: true })
  }

  let unhook: (() => void) | undefined
  try {
    unhook = options.hook?.(store.recorder) ?? undefined
  } catch (error) {
    document.removeEventListener('DOMContentLoaded', mount)
    panel.destroy()
    throw error
  }

  const dari: DariWeb = {
    recorder: store.recorder,
    getEntries: () => store.getEntries(),
    clear: () => store.clear(),
    show: () => panel.show(),
    hide: () => panel.hide(),
    uninstall: () => {
      if (installed !== dari) return
      installed = null
      unhook?.()
      document.removeEventListener('DOMContentLoaded', mount)
      panel.destroy()
    },
  }
  installed = dari
  return dari
}
