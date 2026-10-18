import { MessageStore } from './store'
import type { DariWebOptions, MessageEntry, Recorder } from './types'
import { DariView } from './view'

// Minimal vConsole surface, so the package needs no runtime dependency on it.
export interface VConsolePluginLike {
  on(event: string, callback: (data?: any) => void): unknown
}

export type VConsolePluginConstructor = new (id: string, name: string) => VConsolePluginLike

export interface VConsoleLike {
  addPlugin(plugin: VConsolePluginLike): boolean
  removePlugin(pluginId: string): boolean
}

export interface InstallOptions extends DariWebOptions {
  /** Needed only when it can't be found on the vConsole instance or `window.VConsole`. */
  VConsolePlugin?: VConsolePluginConstructor
}

export interface DariWeb {
  /** Report traffic by hand, e.g. from code that `hook` can't reach. */
  readonly recorder: Recorder
  getEntries(): readonly MessageEntry[]
  clear(): void
  /** Undoes `hook` and removes the tab. */
  uninstall(): void
}

/** Adds a Dari tab to [vConsole] and starts recording bridge traffic through `options.hook`. */
export function install(vConsole: VConsoleLike, options: InstallOptions = {}): DariWeb {
  const Plugin = resolvePluginConstructor(vConsole, options)
  const pluginId = options.pluginId ?? 'dari'
  const store = new MessageStore(options)

  let view: DariView | null = null
  const plugin = new Plugin(pluginId, options.pluginName ?? 'Dari')
  plugin.on('renderTab', (callback: (el: HTMLElement) => void) => {
    view = new DariView(store)
    callback(view.root)
  })
  plugin.on('addTool', (callback: (tools: unknown[]) => void) => {
    callback([{ name: 'Clear', global: false, onClick: () => store.clear() }])
  })
  plugin.on('remove', () => {
    view?.destroy()
    view = null
  })
  if (!vConsole.addPlugin(plugin)) {
    throw new Error(`vConsole already has a plugin with id "${pluginId}"`)
  }

  let unhook: (() => void) | undefined
  try {
    unhook = options.hook?.(store.recorder) ?? undefined
  } catch (error) {
    vConsole.removePlugin(pluginId)
    throw error
  }

  let installed = true
  return {
    recorder: store.recorder,
    getEntries: () => store.getEntries(),
    clear: () => store.clear(),
    uninstall: () => {
      if (!installed) return
      installed = false
      unhook?.()
      vConsole.removePlugin(pluginId)
    },
  }
}

function resolvePluginConstructor(vConsole: VConsoleLike, options: InstallOptions): VConsolePluginConstructor {
  const fromInstance = (vConsole as { constructor?: { VConsolePlugin?: VConsolePluginConstructor } }).constructor
    ?.VConsolePlugin
  const fromGlobal =
    typeof window === 'undefined'
      ? undefined
      : (window as { VConsole?: { VConsolePlugin?: VConsolePluginConstructor } }).VConsole?.VConsolePlugin
  const Plugin = options.VConsolePlugin ?? fromInstance ?? fromGlobal
  if (!Plugin) {
    throw new Error('VConsolePlugin not found; pass it as options.VConsolePlugin')
  }
  return Plugin
}
