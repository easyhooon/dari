// Mirrors `MessageEntry` in dari-core. Field names and enum values must stay in sync.

export type MessageDirection = 'WEB_TO_APP' | 'APP_TO_WEB'

export type MessageStatus = 'IN_PROGRESS' | 'SUCCESS' | 'ERROR'

export interface MessageEntry {
  id: number
  requestId: string | null
  handlerName: string
  displayName: string | null
  direction: MessageDirection
  tag: string | null
  requestData: string | null
  responseData: string | null
  requestDataTruncated: boolean
  responseDataTruncated: boolean
  status: MessageStatus
  requestTimestamp: number
  responseTimestamp: number | null
  pageUrl: string | null
}

export interface RequestEvent {
  handlerName: string
  displayName?: string | null
  requestId?: string | null
  /** Strings are kept as is; anything else is JSON-stringified. */
  data?: unknown
  /** Overrides `DariWebOptions.fireAndForget` for this message. */
  fireAndForget?: boolean
}

export interface ResponseEvent {
  requestId: string | null | undefined
  isSuccess: boolean
  data?: unknown
}

/** What a bridge hook reports. Mirrors `DariInterceptor` on the native side. */
export interface Recorder {
  webToAppRequest(event: RequestEvent): void
  webToAppResponse(event: ResponseEvent): void
  appToWebRequest(event: RequestEvent): void
  appToWebResponse(event: ResponseEvent): void
}

export interface DariWebOptions {
  /** Patches the app's bridge and reports traffic. May return a function that undoes the patch. */
  hook?: (recorder: Recorder) => void | (() => void)
  /** Rewrites an entry before it is stored, e.g. to mask tokens. */
  redact?: (entry: MessageEntry) => MessageEntry
  /** Groups entries when several bridges report to one panel. */
  tag?: string | null
  /** Oldest entries are dropped past this count. Default 500, same as the native side. */
  maxEntries?: number
  /** Payloads longer than this many characters are truncated. Default 500,000. */
  maxContentLength?: number
  /** Treat requests as finished on arrival when no response is expected. Default false. */
  fireAndForget?: boolean
  /** `auto` follows the system setting. Default `auto`. */
  theme?: 'auto' | 'light' | 'dark'
}
