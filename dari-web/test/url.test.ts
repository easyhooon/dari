import { describe, expect, it } from 'vitest'
import { pageOriginOf, sanitizePageUrl } from '../src/url'

describe('sanitizePageUrl', () => {
  it('drops user info, query and fragment', () => {
    expect(sanitizePageUrl('https://a:b@x.com/p?q=1#f')).toBe('https://x.com/p')
  })

  it('keeps an @ in the path', () => {
    expect(sanitizePageUrl('https://x.com/@me')).toBe('https://x.com/@me')
  })

  it('returns null for blank input', () => {
    expect(sanitizePageUrl('  ')).toBeNull()
    expect(sanitizePageUrl('?only=query')).toBeNull()
    expect(sanitizePageUrl(null)).toBeNull()
  })
})

describe('pageOriginOf', () => {
  it('lowercases scheme and host, keeps port', () => {
    expect(pageOriginOf('HTTPS://u@Shop.Example.com:8443/cart')).toBe('https://shop.example.com:8443')
  })

  it('returns null without a scheme', () => {
    expect(pageOriginOf('file.html')).toBeNull()
  })
})
