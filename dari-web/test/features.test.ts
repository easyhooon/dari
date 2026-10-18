import { afterEach, describe, expect, it, vi } from 'vitest'
import { matchRanges } from '../src/dom'
import { exportJson, exportText, messageText, suggestedFileName } from '../src/export'
import { distinctOrigins, EMPTY_FILTER, filterEntries } from '../src/filter'
import { defaultCollapsedPaths, parseStructured, pathsContainingMatch } from '../src/json'
import { loadSettings, saveSettings } from '../src/settings'
import { handlerStats } from '../src/stats'
import type { MessageEntry } from '../src/types'

function entry(patch: Partial<MessageEntry> = {}): MessageEntry {
  return {
    id: 1,
    requestId: '1',
    handlerName: 'getUser',
    displayName: null,
    direction: 'WEB_TO_APP',
    tag: null,
    requestData: '{"id":1}',
    responseData: null,
    requestDataTruncated: false,
    responseDataTruncated: false,
    status: 'IN_PROGRESS',
    requestTimestamp: 1000,
    responseTimestamp: null,
    pageUrl: 'https://shop.test/cart',
    ...patch,
  }
}

const ctx = { pageUrl: 'https://shop.test/cart', userAgent: 'UA', exportedAt: 0 }

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('filter', () => {
  it('combines conditions with AND and searches payloads', () => {
    const entries = [
      entry({ id: 1, status: 'ERROR', responseData: 'card declined' }),
      entry({ id: 2, status: 'SUCCESS', responseData: 'card ok' }),
      entry({ id: 3, status: 'ERROR', direction: 'APP_TO_WEB' }),
    ]
    const ids = (f: Partial<typeof EMPTY_FILTER>) => filterEntries(entries, { ...EMPTY_FILTER, ...f }).map((e) => e.id)
    expect(ids({ status: 'ERROR' })).toEqual([1, 3])
    expect(ids({ status: 'ERROR', query: 'CARD' })).toEqual([1])
    expect(ids({ direction: 'APP_TO_WEB' })).toEqual([3])
    expect(ids({ origin: 'https://other.test' })).toEqual([])
    expect(distinctOrigins(entries)).toEqual(['https://shop.test'])
  })

  it('finds case-insensitive, non-overlapping matches', () => {
    expect(matchRanges('aAaa', 'aa')).toEqual([
      [0, 2],
      [2, 4],
    ])
  })
})

describe('json', () => {
  it('folds containers from depth 2 except those holding a match', () => {
    const value = parseStructured('{"a":{"b":{"c":1},"d":{"e":"kim"}}}')!
    expect([...defaultCollapsedPaths(value)].sort()).toEqual(['$/a/b', '$/a/d'])
    expect([...pathsContainingMatch(value, 'KIM')].sort()).toEqual(['$', '$/a', '$/a/d'])
  })

  it('treats scalars and invalid JSON as plain text', () => {
    expect(parseStructured('42')).toBeUndefined()
    expect(parseStructured('{oops')).toBeUndefined()
    expect(parseStructured('[]')).toEqual([])
  })
})

describe('export', () => {
  it('writes JSON with the desktop field order', () => {
    const doc = JSON.parse(exportJson([entry({ responseTimestamp: 1250, status: 'SUCCESS' })], ctx))
    expect(doc.format_version).toBe(2)
    expect(doc.meta.source).toBe('dari-web')
    const keys = Object.keys(doc.messages[0])
    expect(keys.slice(0, 4)).toEqual(['id', 'request_id', 'handler_name', 'display_name'])
    expect(keys.slice(-5)).toEqual(['response_raw_preview_truncated', 'status', 'request_timestamp', 'response_timestamp', 'duration_ms'])
    expect(doc.messages[0].duration_ms).toBe(250)
  })

  it('writes text with decoded payloads between separators', () => {
    const date = (ms: number) => `t${ms}`
    const text = exportText([entry(), entry({ id: 2, handlerName: 'pay' })], ctx, date)
    expect(text.startsWith('Page: https://shop.test/cart\nUser agent: UA\n')).toBe(true)
    expect(text.split('='.repeat(60))).toHaveLength(3)
    const one = messageText(entry(), date)
    expect(one).toContain('Request time: t1000')
    expect(one).toContain('---------- Request (Decoded) ----------\n\n{\n  "id": 1\n}')
    expect(one).toContain('---------- Response (Decoded) ----------\n\n(empty)')
  })

  it('names files like the desktop app', () => {
    expect(suggestedFileName('json', new Date(2026, 9, 19, 8, 5, 3))).toBe('dari_export_20261019_080503.json')
  })
})

describe('stats', () => {
  it('sorts by slowest average, untimed last', () => {
    const stats = handlerStats([
      entry({ id: 1, handlerName: 'fast', responseTimestamp: 1010 }),
      entry({ id: 2, handlerName: 'slow', responseTimestamp: 1900, status: 'ERROR' }),
      entry({ id: 3, handlerName: 'slow', responseTimestamp: 1100 }),
      entry({ id: 4, handlerName: 'fire' }),
    ])
    expect(stats.map((s) => [s.handlerName, s.avgMs])).toEqual([
      ['slow', 500],
      ['fast', 10],
      ['fire', null],
    ])
    expect(stats[0]).toMatchObject({ callCount: 2, errorCount: 1, minMs: 100, maxMs: 900 })
  })
})

describe('settings', () => {
  it('prefers stored values and ignores malformed ones', () => {
    expect(loadSettings({ theme: 'dark' }).theme).toBe('dark')
    saveSettings({ theme: 'light', foldJson: false, showTags: true, launcher: { left: 10, top: 20 } })
    expect(loadSettings({ theme: 'dark' })).toEqual({ theme: 'light', foldJson: false, showTags: true, launcher: { left: 10, top: 20 } })
    localStorage.setItem('dari-web:settings', '{"theme":"neon","launcher":"x"}')
    expect(loadSettings()).toMatchObject({ theme: 'auto', launcher: null })
  })

  it('falls back to defaults when storage is blocked', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })
    expect(loadSettings().theme).toBe('auto')
    expect(() => saveSettings(loadSettings())).not.toThrow()
  })
})
