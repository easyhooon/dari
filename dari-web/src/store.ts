import type { DariWebOptions, MessageDirection, MessageEntry, Recorder, RequestEvent, ResponseEvent } from './types'
import { sanitizePageUrl } from './url'

export const DEFAULT_MAX_ENTRIES = 500
export const DEFAULT_MAX_CONTENT_LENGTH = 500_000

type Listener = (entries: readonly MessageEntry[]) => void

export interface StoreEnvironment {
  now: () => number
  pageUrl: () => string | null | undefined
}

const browserEnvironment: StoreEnvironment = {
  now: () => Date.now(),
  pageUrl: () => (typeof location === 'undefined' ? null : location.href),
}

/** In-memory entry list, newest last. Matching follows `MessageRepository.updateEntry` on Android. */
export class MessageStore {
  private entries: MessageEntry[] = []
  private nextId = 1
  private readonly listeners = new Set<Listener>()
  private readonly maxEntries: number
  private readonly maxContentLength: number

  constructor(
    private readonly options: DariWebOptions = {},
    private readonly env: StoreEnvironment = browserEnvironment,
  ) {
    this.maxEntries = options.maxEntries ?? DEFAULT_MAX_ENTRIES
    this.maxContentLength = options.maxContentLength ?? DEFAULT_MAX_CONTENT_LENGTH
    if (this.maxEntries <= 0) throw new Error('maxEntries must be greater than 0')
    if (this.maxContentLength <= 0) throw new Error('maxContentLength must be greater than 0')
  }

  readonly recorder: Recorder = {
    webToAppRequest: (event) => this.addRequest('WEB_TO_APP', event),
    webToAppResponse: (event) => this.applyResponse(event),
    appToWebRequest: (event) => this.addRequest('APP_TO_WEB', event),
    appToWebResponse: (event) => this.applyResponse(event),
  }

  getEntries(): readonly MessageEntry[] {
    return this.entries
  }

  clear(): void {
    this.entries = []
    this.emit()
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  private addRequest(direction: MessageDirection, event: RequestEvent): void {
    const [data, truncated] = truncateIfNeeded(stringify(event.data), this.maxContentLength)
    const finished = event.fireAndForget ?? this.options.fireAndForget ?? false
    const entry: MessageEntry = {
      id: this.nextId++,
      requestId: event.requestId ?? null,
      handlerName: event.handlerName,
      displayName: event.displayName?.trim() ? event.displayName : null,
      direction,
      tag: this.options.tag ?? null,
      requestData: data,
      responseData: null,
      requestDataTruncated: truncated,
      responseDataTruncated: false,
      status: finished ? 'SUCCESS' : 'IN_PROGRESS',
      requestTimestamp: this.env.now(),
      responseTimestamp: null,
      pageUrl: sanitizePageUrl(this.env.pageUrl()),
    }
    this.entries = [...this.entries, this.redact(entry)]
    if (this.entries.length > this.maxEntries) {
      this.entries = this.entries.slice(this.entries.length - this.maxEntries)
    }
    this.emit()
  }

  private applyResponse(event: ResponseEvent): void {
    // Fire-and-forget messages have no id to match.
    if (event.requestId == null) return
    const [data, truncated] = truncateIfNeeded(stringify(event.data), this.maxContentLength)
    const now = this.env.now()
    let changed = false
    this.entries = this.entries.map((entry) => {
      if (entry.requestId !== event.requestId) return entry
      changed = true
      return this.redact({
        ...entry,
        responseData: data,
        responseDataTruncated: truncated,
        status: event.isSuccess ? 'SUCCESS' : 'ERROR',
        responseTimestamp: now,
      })
    })
    if (changed) this.emit()
  }

  private redact(entry: MessageEntry): MessageEntry {
    return this.options.redact ? this.options.redact(entry) : entry
  }

  private emit(): void {
    for (const listener of this.listeners) listener(this.entries)
  }
}

export function stringify(data: unknown): string | null {
  if (data === undefined || data === null) return null
  if (typeof data === 'string') return data
  try {
    return JSON.stringify(data) ?? String(data)
  } catch {
    return String(data)
  }
}

/** Same marker text as `MessageEntry.truncateIfNeeded`. */
export function truncateIfNeeded(data: string | null, maxLength: number): [string | null, boolean] {
  if (data === null || data.length <= maxLength) return [data, false]
  return [`${data.slice(0, maxLength)}\n\n...[truncated, original length: ${data.length} chars]`, true]
}
