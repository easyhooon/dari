// Ports of `MessageEntry.sanitizePageUrl` / `pageOrigin` in dari-core.

/** Drops user info, query and fragment, which can carry credentials or tokens. */
export function sanitizePageUrl(url: string | null | undefined): string | null {
  if (url == null) return null
  const stripped = withoutUserInfo(url.split('#')[0]!.split('?')[0]!)
  return stripped.trim() === '' ? null : stripped
}

/** `scheme://host[:port]` of [url], or null when it has no scheme. */
export function pageOriginOf(url: string | null | undefined): string | null {
  if (url == null) return null
  const schemeEnd = url.indexOf('://')
  if (schemeEnd <= 0) return null
  const authorityStart = schemeEnd + 3
  let authorityEnd = url.length
  for (const c of ['/', '?', '#']) {
    const i = url.indexOf(c, authorityStart)
    if (i >= 0 && i < authorityEnd) authorityEnd = i
  }
  const authority = url.substring(authorityStart, authorityEnd)
  const host = authority.substring(authority.lastIndexOf('@') + 1)
  return (url.substring(0, authorityStart) + host).toLowerCase()
}

function withoutUserInfo(url: string): string {
  const schemeEnd = url.indexOf('://')
  if (schemeEnd <= 0) return url
  const authorityStart = schemeEnd + 3
  const slash = url.indexOf('/', authorityStart)
  const authorityEnd = slash < 0 ? url.length : slash
  const at = url.lastIndexOf('@', authorityEnd - 1)
  return at < authorityStart ? url : url.substring(0, authorityStart) + url.substring(at + 1)
}
