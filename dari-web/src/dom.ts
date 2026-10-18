type Attrs = Record<string, string>
export type Child = Node | string | null | false | undefined

// Payloads are untrusted page data, so everything goes through text nodes, never innerHTML.
export function h(tag: string, attrs: Attrs = {}, ...children: Child[]): HTMLElement {
  const el = document.createElement(tag)
  for (const [key, value] of Object.entries(attrs)) el.setAttribute(key, value)
  for (const child of children) {
    if (child === null || child === false || child === undefined) continue
    el.append(typeof child === 'string' ? document.createTextNode(child) : child)
  }
  return el
}

export function button(label: string, className: string, onClick: () => void, attrs: Attrs = {}): HTMLElement {
  const el = h('button', { type: 'button', class: className, ...attrs }, label)
  el.addEventListener('click', onClick)
  return el
}

/** Case-insensitive, non-overlapping occurrences of [query] in [text], as [start, end) pairs. */
export function matchRanges(text: string, query: string): Array<[number, number]> {
  if (query === '') return []
  const haystack = text.toLowerCase()
  const needle = query.toLowerCase()
  const ranges: Array<[number, number]> = []
  let start = haystack.indexOf(needle)
  while (start >= 0) {
    ranges.push([start, start + needle.length])
    start = haystack.indexOf(needle, start + needle.length)
  }
  return ranges
}

/** [text] as nodes with every match of [query] wrapped in `<mark>`. */
export function highlighted(text: string, query: string): Node[] {
  const ranges = matchRanges(text, query)
  if (ranges.length === 0) return [document.createTextNode(text)]
  const nodes: Node[] = []
  let last = 0
  for (const [start, end] of ranges) {
    if (start > last) nodes.push(document.createTextNode(text.slice(last, start)))
    nodes.push(h('mark', { class: 'dari-match' }, text.slice(start, end)))
    last = end
  }
  if (last < text.length) nodes.push(document.createTextNode(text.slice(last)))
  return nodes
}

/** Clipboard API needs a secure context, which many WebView pages lack, so fall back to execCommand. */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // Fall through to the legacy path.
  }
  const area = document.createElement('textarea')
  area.value = text
  area.setAttribute('readonly', '')
  area.style.cssText = 'position:fixed;top:0;left:0;opacity:0;'
  document.body.append(area)
  // iOS WebViews ignore select() alone.
  area.focus({ preventScroll: true })
  area.select()
  area.setSelectionRange(0, text.length)
  try {
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    area.remove()
  }
}

export function download(fileName: string, text: string, type: string): void {
  const url = URL.createObjectURL(new Blob([text], { type }))
  const link = h('a', { href: url, download: fileName }) as HTMLAnchorElement
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
