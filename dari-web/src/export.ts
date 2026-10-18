import type { MessageEntry } from './types'
import { utf8Size } from './wire'

declare const __DARI_WEB_VERSION__: string
export const VERSION: string = typeof __DARI_WEB_VERSION__ === 'string' ? __DARI_WEB_VERSION__ : 'dev'

// Same message fields and text layout as the app and desktop exporters (`MessageExport`), so files look alike.
const FORMAT_VERSION = 2
const SEPARATOR = `\n${'='.repeat(60)}\n\n`

export interface ExportContext {
  pageUrl: string | null
  userAgent: string | null
  exportedAt: number
}

export function browserContext(now = Date.now()): ExportContext {
  return {
    pageUrl: typeof location === 'undefined' ? null : location.href.split(/[?#]/)[0]!,
    userAgent: typeof navigator === 'undefined' ? null : navigator.userAgent,
    exportedAt: now,
  }
}

/** One message as readable text. */
export function messageText(entry: MessageEntry, formatDate: (millis: number) => string = defaultDate): string {
  const lines = [`Handler: ${entry.handlerName}`]
  if (entry.displayName?.trim()) lines.push(`Display name: ${entry.displayName}`)
  lines.push(
    `Direction: ${entry.direction === 'WEB_TO_APP' ? 'Web → App' : 'App → Web'}`,
    `Status: ${entry.status}`,
    `Tag: ${entry.tag ?? '-'}`,
  )
  if (entry.pageUrl !== null) lines.push(`Page: ${entry.pageUrl}`)
  lines.push(`Request ID: ${entry.requestId ?? '-'}`, '', `Request time: ${formatDate(entry.requestTimestamp)}`)
  if (entry.responseTimestamp !== null) {
    lines.push(
      `Response time: ${formatDate(entry.responseTimestamp)}`,
      `Duration: ${entry.responseTimestamp - entry.requestTimestamp} ms`,
    )
  }
  const requestSize = utf8Size(entry.requestData)
  const responseSize = utf8Size(entry.responseData)
  lines.push(
    '',
    `Request size: ${formatSize(requestSize)}${entry.requestDataTruncated ? ' (truncated)' : ''}`,
    `Response size: ${formatSize(responseSize)}${entry.responseDataTruncated ? ' (truncated)' : ''}`,
    `Total size: ${formatSize(requestSize + responseSize)}`,
    '',
    '---------- Request (Decoded) ----------',
    '',
    prettyOrRaw(entry.requestData) ?? '(empty)',
    '',
    '---------- Response (Decoded) ----------',
    '',
    prettyOrRaw(entry.responseData) ?? '(empty)',
  )
  return lines.join('\n') + '\n'
}

/** Messages oldest first, under a header describing the page. */
export function exportText(
  entries: readonly MessageEntry[],
  context: ExportContext,
  formatDate: (millis: number) => string = defaultDate,
): string {
  const header = [
    `Page: ${context.pageUrl ?? '-'}`,
    `User agent: ${context.userAgent ?? '-'}`,
    `Dari web: ${VERSION}`,
    `Exported: ${formatDate(context.exportedAt)}`,
  ].join('\n')
  return header + '\n' + SEPARATOR + entries.map((e) => messageText(e, formatDate)).join(SEPARATOR)
}

export function exportJson(entries: readonly MessageEntry[], context: ExportContext): string {
  const document = {
    format_version: FORMAT_VERSION,
    meta: {
      source: 'dari-web',
      dari_web_version: VERSION,
      page_url: context.pageUrl,
      user_agent: context.userAgent,
      exported_at: context.exportedAt,
    },
    messages: entries.map(exportable),
  }
  return JSON.stringify(document, null, 2)
}

export function suggestedFileName(extension: 'txt' | 'json', now = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  const stamp =
    `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_` +
    `${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  return `dari_export_${stamp}.${extension}`
}

// Field names and order match ExportableMessage in :dari; protobuf fields stay null on the web.
function exportable(e: MessageEntry): Record<string, unknown> {
  return {
    id: e.id,
    request_id: e.requestId,
    handler_name: e.handlerName,
    display_name: e.displayName,
    direction: e.direction,
    tag: e.tag,
    page_url: e.pageUrl,
    request_data: e.requestData,
    response_data: e.responseData,
    request_data_truncated: e.requestDataTruncated,
    response_data_truncated: e.responseDataTruncated,
    request_content_type: null,
    request_original_size_bytes: null,
    request_decode_status: null,
    request_raw_preview_base64: null,
    request_raw_preview_size_bytes: null,
    request_raw_preview_truncated: null,
    response_content_type: null,
    response_original_size_bytes: null,
    response_decode_status: null,
    response_raw_preview_base64: null,
    response_raw_preview_size_bytes: null,
    response_raw_preview_truncated: null,
    status: e.status,
    request_timestamp: e.requestTimestamp,
    response_timestamp: e.responseTimestamp,
    duration_ms: e.responseTimestamp === null ? null : e.responseTimestamp - e.requestTimestamp,
  }
}

export function prettyOrRaw(text: string | null): string | null {
  if (text === null) return null
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function defaultDate(millis: number): string {
  return new Date(millis).toString()
}
