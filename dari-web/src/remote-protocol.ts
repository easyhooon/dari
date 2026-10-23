import type { WireMessageDetail } from './wire'

// JSON text frames between page, relay and viewer. Entries reuse the `WireMessageDetail` shape.

export const REMOTE_PROTOCOL = 1
export const DEFAULT_RELAY_PORT = 9333

export interface PageInfo {
  /** Random per `install()`, so a page that reconnects keeps its slot in the relay. */
  sessionId: string
  url: string | null
  title: string | null
  userAgent: string | null
  dariWebVersion: string
}

/** Page → relay. `hello` resets the page's state; the current entries follow as `added`. */
export type PageEvent =
  | { type: 'hello'; protocol: number; page: PageInfo }
  | { type: 'added'; message: WireMessageDetail }
  | { type: 'updated'; message: WireMessageDetail }
  | { type: 'removed'; ids: number[] }
  | { type: 'cleared' }

/** Relay → page. */
export type PageCommand = { type: 'clear' }

/** A page as viewers see it; `sessionId` doubles as its id. */
export interface PageSummary extends PageInfo {
  connected: boolean
  connectedAt: number
}

/** Relay → viewer. */
export type ViewerEvent =
  | { type: 'pages'; pages: PageSummary[] }
  | { type: 'reset'; pageId: string }
  | { type: 'added'; pageId: string; message: WireMessageDetail }
  | { type: 'updated'; pageId: string; message: WireMessageDetail }
  | { type: 'removed'; pageId: string; ids: number[] }
  | { type: 'cleared'; pageId: string }

/** Viewer → relay. */
export type ViewerCommand = { type: 'clear'; pageId: string }
