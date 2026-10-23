import type { MessageDirection, MessageEntry, MessageStatus } from './types'
import { pageOriginOf } from './url'

export interface Filter {
  query: string
  direction: MessageDirection | null
  status: MessageStatus | null
  tag: string | null
  origin: string | null
}

export const EMPTY_FILTER: Filter = { query: '', direction: null, status: null, tag: null, origin: null }

/** Same rules as the desktop app's `visibleMessages`; payloads are searched locally since they're in memory. */
export function filterEntries(entries: readonly MessageEntry[], filter: Filter): MessageEntry[] {
  const query = filter.query.trim().toLowerCase()
  return entries.filter(
    (e) =>
      (filter.direction === null || e.direction === filter.direction) &&
      (filter.status === null || e.status === filter.status) &&
      (filter.tag === null || e.tag === filter.tag) &&
      (filter.origin === null || pageOriginOf(e.pageUrl) === filter.origin) &&
      (query === '' ||
        [e.handlerName, e.displayName, e.tag, e.requestId, e.pageUrl, e.requestData, e.responseData].some(
          (v) => v !== null && v.toLowerCase().includes(query),
        )),
  )
}

export function distinctTags(entries: readonly MessageEntry[]): string[] {
  return distinct(entries.map((e) => e.tag))
}

export function distinctOrigins(entries: readonly MessageEntry[]): string[] {
  return distinct(entries.map((e) => pageOriginOf(e.pageUrl)))
}

function distinct(values: Array<string | null>): string[] {
  return [...new Set(values.filter((v): v is string => v !== null))].sort()
}
