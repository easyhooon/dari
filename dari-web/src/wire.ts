import type { MessageDirection, MessageEntry, MessageStatus } from './types'

// Shapes of `WireMessageSummary` / `WireMessageDetail` in dari-inspector-protocol, encoded like
// `InspectorProtocol.json` (nulls omitted, defaults kept). Shared fixtures pin this shape.

export interface WireMessageSummary {
  id: number
  requestId?: string
  handlerName: string
  displayName?: string
  direction: string
  tag?: string
  status: string
  requestTimestamp: number
  responseTimestamp?: number
  requestSizeBytes: number
  responseSizeBytes: number
  pageUrl?: string
}

export interface WireMessageDetail {
  summary: WireMessageSummary
  requestData?: string
  responseData?: string
  requestDataTruncated: boolean
  responseDataTruncated: boolean
}

const encoder = new TextEncoder()

export function utf8Size(data: string | null): number {
  return data === null ? 0 : encoder.encode(data).length
}

export function toWireDetail(entry: MessageEntry): WireMessageDetail {
  const summary: WireMessageSummary = {
    id: entry.id,
    ...optional('requestId', entry.requestId),
    handlerName: entry.handlerName,
    ...optional('displayName', entry.displayName),
    direction: entry.direction,
    ...optional('tag', entry.tag),
    status: entry.status,
    requestTimestamp: entry.requestTimestamp,
    ...optional('responseTimestamp', entry.responseTimestamp),
    requestSizeBytes: utf8Size(entry.requestData),
    responseSizeBytes: utf8Size(entry.responseData),
    ...optional('pageUrl', entry.pageUrl),
  }
  return {
    summary,
    ...optional('requestData', entry.requestData),
    ...optional('responseData', entry.responseData),
    requestDataTruncated: entry.requestDataTruncated,
    responseDataTruncated: entry.responseDataTruncated,
  }
}

/** Back to an entry, for the viewer. Unknown enum values are coerced, since pages are untrusted. */
export function fromWireDetail(detail: WireMessageDetail): MessageEntry {
  const s = detail.summary
  return {
    id: s.id,
    requestId: s.requestId ?? null,
    handlerName: String(s.handlerName),
    displayName: s.displayName ?? null,
    direction: (s.direction === 'APP_TO_WEB' ? 'APP_TO_WEB' : 'WEB_TO_APP') as MessageDirection,
    tag: s.tag ?? null,
    requestData: detail.requestData ?? null,
    responseData: detail.responseData ?? null,
    requestDataTruncated: detail.requestDataTruncated === true,
    responseDataTruncated: detail.responseDataTruncated === true,
    status: (s.status === 'SUCCESS' || s.status === 'ERROR' ? s.status : 'IN_PROGRESS') as MessageStatus,
    requestTimestamp: s.requestTimestamp,
    responseTimestamp: s.responseTimestamp ?? null,
    pageUrl: s.pageUrl ?? null,
  }
}

function optional<K extends string, V>(key: K, value: V | null): { [P in K]?: V } {
  return (value === null ? {} : { [key]: value }) as { [P in K]?: V }
}
