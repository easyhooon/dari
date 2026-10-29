export type Theme = 'auto' | 'light' | 'dark'

export interface Settings {
  theme: Theme
  foldJson: boolean
  showTags: boolean
  /** Launcher position after the user dragged it; null keeps the default corner. */
  launcher: { left: number; top: number } | null
}

const KEY = 'dari-web:settings'

/** Stored choices win over `defaults`, so a viewer's pick survives reloads. Storage may be missing or blocked. */
export function loadSettings(defaults: Partial<Settings> = {}): Settings {
  const base: Settings = { theme: 'auto', foldJson: true, showTags: true, launcher: null, ...defaults }
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return base
    const stored = JSON.parse(raw) as Partial<Settings>
    return {
      theme: stored.theme === 'light' || stored.theme === 'dark' || stored.theme === 'auto' ? stored.theme : base.theme,
      foldJson: typeof stored.foldJson === 'boolean' ? stored.foldJson : base.foldJson,
      showTags: typeof stored.showTags === 'boolean' ? stored.showTags : base.showTags,
      launcher: isPoint(stored.launcher) ? stored.launcher : base.launcher,
    }
  } catch {
    return base
  }
}

export function saveSettings(settings: Settings): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(settings))
  } catch {
    // Private mode or blocked storage: settings just won't persist.
  }
}

function isPoint(value: unknown): value is { left: number; top: number } {
  const p = value as { left?: unknown; top?: unknown } | null
  return !!p && typeof p.left === 'number' && typeof p.top === 'number'
}
