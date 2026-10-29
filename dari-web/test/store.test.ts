import { describe, expect, it, vi } from 'vitest'
import { MessageStore, stringify } from '../src/store'

const env = { now: () => 0, pageUrl: () => null }

describe('MessageStore', () => {
  it('drops the oldest entries past maxEntries', () => {
    const store = new MessageStore({ maxEntries: 2 }, env)
    for (const name of ['a', 'b', 'c']) store.recorder.webToAppRequest({ handlerName: name })
    expect(store.getEntries().map((e) => e.handlerName)).toEqual(['b', 'c'])
    expect(store.getEntries().map((e) => e.id)).toEqual([2, 3])
  })

  it('redacts on request and on response', () => {
    const store = new MessageStore(
      { redact: (e) => ({ ...e, requestData: '***', responseData: e.responseData && '***' }) },
      env,
    )
    store.recorder.webToAppRequest({ handlerName: 'login', requestId: '1', data: 'pw' })
    store.recorder.webToAppResponse({ requestId: '1', isSuccess: true, data: 'token' })
    const [entry] = store.getEntries()
    expect(entry?.requestData).toBe('***')
    expect(entry?.responseData).toBe('***')
  })

  it('notifies listeners and stops after unsubscribe', () => {
    const store = new MessageStore({}, env)
    const listener = vi.fn()
    const unsubscribe = store.subscribe(listener)
    store.recorder.appToWebRequest({ handlerName: 'a' })
    store.recorder.appToWebResponse({ requestId: 'unknown', isSuccess: true })
    store.clear()
    unsubscribe()
    store.recorder.appToWebRequest({ handlerName: 'b' })
    // The unmatched response does not notify.
    expect(listener).toHaveBeenCalledTimes(2)
  })

  it('rejects non-positive limits', () => {
    expect(() => new MessageStore({ maxEntries: 0 }, env)).toThrow()
    expect(() => new MessageStore({ maxContentLength: 0 }, env)).toThrow()
  })
})

describe('stringify', () => {
  it('keeps strings and serializes the rest', () => {
    expect(stringify('raw')).toBe('raw')
    expect(stringify({ a: 1 })).toBe('{"a":1}')
    expect(stringify(undefined)).toBeNull()
  })

  it('falls back to String() for values JSON cannot encode', () => {
    const cyclic: Record<string, unknown> = {}
    cyclic.self = cyclic
    expect(stringify(cyclic)).toBe('[object Object]')
    expect(stringify(BigInt(10))).toBe('10')
  })
})
