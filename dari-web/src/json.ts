import { h, highlighted, matchRanges } from './dom'

type Json = null | boolean | number | string | Json[] | { [key: string]: Json }

// Same as the desktop viewer: containers deeper than this start folded.
const DEFAULT_COLLAPSE_DEPTH = 2
const ROOT = '$'

/** Parsed object/array, or undefined for anything that should show as plain text. */
export function parseStructured(text: string): Json[] | { [key: string]: Json } | undefined {
  const trimmed = text.trimStart()
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return undefined
  try {
    const value = JSON.parse(text) as Json
    return typeof value === 'object' && value !== null ? value : undefined
  } catch {
    return undefined
  }
}

export function defaultCollapsedPaths(value: Json): Set<string> {
  const paths = new Set<string>()
  walk(value, ROOT, 0, (path, depth) => {
    if (depth >= DEFAULT_COLLAPSE_DEPTH) paths.add(path)
  })
  return paths
}

export function allContainerPaths(value: Json): Set<string> {
  const paths = new Set<string>()
  walk(value, ROOT, 0, (path) => paths.add(path))
  return paths
}

/** Containers that hold a match in a key or value, so folding never hides a search hit. */
export function pathsContainingMatch(value: Json, query: string): Set<string> {
  const paths = new Set<string>()
  if (query.trim() === '') return paths
  const visit = (node: Json, path: string): boolean => {
    if (!isContainer(node)) return matchRanges(JSON.stringify(node), query).length > 0
    let found = false
    for (const [key, child] of children(node)) {
      const keyHit = !Array.isArray(node) && matchRanges(JSON.stringify(key), query).length > 0
      if (visit(child, childPath(path, key)) || keyHit) found = true
    }
    if (found) paths.add(path)
    return found
  }
  visit(value, ROOT)
  return paths
}

export interface TreeOptions {
  collapsed: ReadonlySet<string>
  folding: boolean
  query: string
  onToggle: (path: string) => void
}

export function renderTree(value: Json, options: TreeOptions): HTMLElement {
  const root = h('div', { class: 'dari-json' })
  appendNode(root, value, ROOT, null, 0, false, options)
  return root
}

function appendNode(
  out: HTMLElement,
  value: Json,
  path: string,
  key: string | null,
  depth: number,
  comma: boolean,
  options: TreeOptions,
): void {
  const keyNodes = key === null ? [] : [span('dari-j-key', `${JSON.stringify(key)}`, options.query), ': ']
  const tail = comma ? ',' : ''
  if (!isContainer(value)) {
    out.append(line(depth, null, ...keyNodes, span(tokenClass(value), JSON.stringify(value), options.query), tail))
    return
  }
  const [open, close] = Array.isArray(value) ? ['[', ']'] : ['{', '}']
  const entries = children(value)
  if (entries.length === 0) {
    out.append(line(depth, null, ...keyNodes, h('span', { class: 'dari-j-punct' }, open + close), tail))
    return
  }
  const collapsed = options.folding && options.collapsed.has(path)
  const toggle = options.folding ? toggleButton(path, collapsed, options.onToggle) : null
  if (collapsed) {
    // The summary is a bigger tap target than the arrow on phones.
    const more = h('span', { class: 'dari-j-summary', role: 'button' }, summary(value))
    more.addEventListener('click', () => options.onToggle(path))
    out.append(line(depth, toggle, ...keyNodes, more, tail))
    return
  }
  out.append(line(depth, toggle, ...keyNodes, h('span', { class: 'dari-j-punct' }, open)))
  entries.forEach(([childKey, child], index) => {
    const label = Array.isArray(value) ? null : childKey
    appendNode(out, child, childPath(path, childKey), label, depth + 1, index < entries.length - 1, options)
  })
  out.append(line(depth, null, h('span', { class: 'dari-j-punct' }, close), tail))
}

function line(depth: number, toggle: HTMLElement | null, ...content: Array<Node | string>): HTMLElement {
  const el = h('div', { class: 'dari-j-line' }, toggle ?? h('span', { class: 'dari-j-gap' }), ...content)
  el.style.paddingLeft = `${depth * 14}px`
  return el
}

function toggleButton(path: string, collapsed: boolean, onToggle: (path: string) => void): HTMLElement {
  const el = h(
    'button',
    { type: 'button', class: 'dari-j-toggle', 'aria-label': collapsed ? 'Expand' : 'Collapse' },
    collapsed ? '▸' : '▾',
  )
  el.addEventListener('click', () => onToggle(path))
  return el
}

function span(className: string, text: string, query: string): HTMLElement {
  return h('span', { class: className }, ...highlighted(text, query))
}

function tokenClass(value: Json): string {
  if (typeof value === 'string') return 'dari-j-string'
  if (typeof value === 'number') return 'dari-j-number'
  return 'dari-j-keyword'
}

function summary(value: Json[] | { [key: string]: Json }): string {
  if (Array.isArray(value)) return `[${value.length} ${value.length === 1 ? 'item' : 'items'}]`
  const size = Object.keys(value).length
  return `{${size} ${size === 1 ? 'field' : 'fields'}}`
}

function isContainer(value: Json): value is Json[] | { [key: string]: Json } {
  return typeof value === 'object' && value !== null
}

function children(value: Json[] | { [key: string]: Json }): Array<[string, Json]> {
  return Array.isArray(value) ? value.map((v, i) => [String(i), v]) : Object.entries(value)
}

// JSON Pointer escaping keeps "a/b" and nested "a" → "b" apart.
function childPath(path: string, key: string): string {
  return `${path}/${key.replace(/~/g, '~0').replace(/\//g, '~1')}`
}

function walk(value: Json, path: string, depth: number, onContainer: (path: string, depth: number) => void): void {
  if (!isContainer(value)) return
  const entries = children(value)
  if (entries.length === 0) return
  onContainer(path, depth)
  for (const [key, child] of entries) walk(child, childPath(path, key), depth + 1, onContainer)
}
