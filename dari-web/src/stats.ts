import type { MessageDirection, MessageEntry } from './types'

export interface HandlerStats {
  handlerName: string
  displayName: string | null
  direction: MessageDirection
  callCount: number
  errorCount: number
  timedCount: number
  avgMs: number | null
  minMs: number | null
  maxMs: number | null
}

// Same threshold as the in-app and desktop Response Time screens.
export const SLOW_HANDLER_THRESHOLD_MS = 500

/** Port of the desktop `handlerStats`: grouped by handler and direction, slowest average first, untimed last. */
export function handlerStats(entries: readonly MessageEntry[]): HandlerStats[] {
  const groups = new Map<string, MessageEntry[]>()
  for (const e of entries) {
    const key = `${e.direction}\u0000${e.handlerName}`
    const group = groups.get(key)
    if (group) group.push(e)
    else groups.set(key, [e])
  }
  const stats = [...groups.values()].map((group): HandlerStats => {
    const first = group[0]!
    // Fire-and-forget and in-progress calls have no response time.
    const durations = group.flatMap((e) => (e.responseTimestamp === null ? [] : [e.responseTimestamp - e.requestTimestamp]))
    const named = group.filter((e) => e.displayName?.trim())
    return {
      handlerName: first.handlerName,
      displayName: named.length > 0 ? named[named.length - 1]!.displayName : null,
      direction: first.direction,
      callCount: group.length,
      errorCount: group.filter((e) => e.status === 'ERROR').length,
      timedCount: durations.length,
      avgMs: durations.length > 0 ? Math.trunc(durations.reduce((a, b) => a + b, 0) / durations.length) : null,
      minMs: durations.length > 0 ? Math.min(...durations) : null,
      maxMs: durations.length > 0 ? Math.max(...durations) : null,
    }
  })
  return stats.sort((a, b) => {
    if (a.avgMs !== b.avgMs) {
      if (a.avgMs === null) return 1
      if (b.avgMs === null) return -1
      return b.avgMs - a.avgMs
    }
    return a.handlerName < b.handlerName ? -1 : a.handlerName > b.handlerName ? 1 : 0
  })
}
