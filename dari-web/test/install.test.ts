import { afterEach, describe, expect, it, vi } from 'vitest'
import { install, type DariWeb } from '../src/install'

let dari: DariWeb | null = null

function setup(...args: Parameters<typeof install>): DariWeb {
  dari = install(...args)
  return dari
}

function shadow(): ShadowRoot {
  return document.querySelector('[data-dari-web]')!.shadowRoot!
}

function el<T extends HTMLElement = HTMLElement>(selector: string): T {
  return shadow().querySelector(selector) as T
}

afterEach(() => {
  dari?.uninstall()
  dari = null
  localStorage.clear()
})

describe('install', () => {
  it('records through hook and renders rows newest first', () => {
    setup({
      hook(recorder) {
        recorder.webToAppRequest({ handlerName: 'first' })
        recorder.webToAppRequest({ handlerName: 'second', requestId: '2' })
      },
    })
    const names = [...shadow().querySelectorAll('.dari-name')].map((e) => e.textContent)
    expect(names).toEqual(['second', 'first'])
    expect(el('.dari-badge').textContent).toBe('2')
  })

  it('opens from the launcher and closes on Escape and the backdrop', () => {
    setup()
    expect(el('.dari-sheet').hidden).toBe(true)
    el('.dari-launcher').click()
    expect(el('.dari-sheet').hidden).toBe(false)
    expect(el('.dari-launcher').hidden).toBe(true)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(el('.dari-sheet').hidden).toBe(true)
    dari!.show()
    el('.dari-backdrop').click()
    expect(el('.dari-sheet').hidden).toBe(true)
  })

  it('marks the badge when a call fails', () => {
    const { recorder } = setup()
    recorder.webToAppRequest({ handlerName: 'pay', requestId: '1' })
    expect(el('.dari-badge').classList.contains('dari-badge-error')).toBe(false)
    recorder.webToAppResponse({ requestId: '1', isSuccess: false })
    expect(el('.dari-badge').classList.contains('dari-badge-error')).toBe(true)
  })

  it('shows payloads as text, never as markup', () => {
    const { recorder } = setup()
    recorder.webToAppRequest({ handlerName: '<img src=x onerror=alert(1)>', data: '<b>hi</b>' })
    expect(shadow().querySelector('img')).toBeNull()
    el('.dari-row').click()
    expect(shadow().querySelector('b')).toBeNull()
    expect(el('.dari-pre').textContent).toBe('<b>hi</b>')
  })

  it('opens detail, follows updates and goes back', () => {
    const { recorder } = setup()
    recorder.webToAppRequest({ handlerName: 'getUser', requestId: '1', data: { id: 1 } })
    el('.dari-row').click()
    expect(el('.dari-j-number').textContent).toBe('1')
    recorder.webToAppResponse({ requestId: '1', isSuccess: false, data: 'nope' })
    expect(shadow().querySelectorAll('.dari-pre')[1]?.textContent).toBe('nope')
    el('.dari-back').click()
    expect(el('.dari-status').textContent).toBe('Error')
  })

  it('filters rows and clears from the header', () => {
    const { recorder } = setup()
    recorder.webToAppRequest({ handlerName: 'getUser' })
    recorder.webToAppRequest({ handlerName: 'pay', data: 'card' })
    const filter = el<HTMLInputElement>('.dari-filter')
    filter.value = 'CARD'
    filter.dispatchEvent(new Event('input'))
    expect(shadow().querySelectorAll('.dari-row')).toHaveLength(1)
    expect(el('.dari-count').textContent).toBe('1 / 2')
    el('.dari-clear').click()
    expect(dari!.getEntries()).toHaveLength(0)
    expect(el('.dari-empty').textContent).toBe('No bridge messages yet')
  })

  it('filters by status and highlights search hits', () => {
    const { recorder } = setup()
    recorder.webToAppRequest({ handlerName: 'getUser', requestId: '1', data: { name: 'Kim' } })
    recorder.webToAppRequest({ handlerName: 'pay', requestId: '2' })
    recorder.webToAppResponse({ requestId: '2', isSuccess: false })
    const status = el<HTMLSelectElement>('select[aria-label="Status"]')
    status.value = 'ERROR'
    status.dispatchEvent(new Event('change'))
    expect([...shadow().querySelectorAll('.dari-name')].map((e) => e.textContent)).toEqual(['pay'])
    status.value = ''
    status.dispatchEvent(new Event('change'))
    const filter = el<HTMLInputElement>('.dari-filter')
    filter.value = 'kim'
    filter.dispatchEvent(new Event('input'))
    el('.dari-row').click()
    expect(el('.dari-match').textContent).toBe('Kim')
  })

  it('shows the tag picker once tags exist and keeps origin hidden for one page', () => {
    setup({ tag: 'web' }).recorder.webToAppRequest({ handlerName: 'a' })
    expect(el('select[aria-label="Tag"]').hidden).toBe(false)
    expect(el('select[aria-label="Origin"]').hidden).toBe(true)
  })

  it('folds nested JSON and expands it on demand', () => {
    const { recorder } = setup()
    recorder.webToAppRequest({ handlerName: 'deep', data: { a: { b: { c: 1 } } } })
    el('.dari-row').click()
    expect(el('.dari-j-summary').textContent).toBe('{1 field}')
    el('.dari-j-summary').parentElement!.querySelector<HTMLElement>('.dari-j-toggle')!.click()
    expect(el('.dari-j-summary')).toBeNull()
    expect(el('.dari-j-number').textContent).toBe('1')
    const collapseAll = [...shadow().querySelectorAll<HTMLElement>('.dari-link')].find((b) => b.textContent === 'Collapse all')!
    collapseAll.click()
    expect(el('.dari-j-summary').textContent).toBe('{1 field}')
  })

  it('shows response time stats from the menu', () => {
    const now = vi.spyOn(Date, 'now')
    const { recorder } = setup()
    now.mockReturnValue(1000)
    recorder.webToAppRequest({ handlerName: 'slow', requestId: '1' })
    now.mockReturnValue(1800)
    recorder.webToAppResponse({ requestId: '1', isSuccess: true })
    now.mockRestore()
    el('.dari-more').click()
    expect(el('.dari-menu').hidden).toBe(false)
    const item = [...shadow().querySelectorAll<HTMLElement>('.dari-menu-item')].find((b) => b.textContent === 'Response Time')!
    item.click()
    expect(el('.dari-stats-slow').textContent).toBe('800 ms')
  })

  it('saves settings and restores them on the next install', () => {
    setup()
    el('.dari-more').click()
    ;[...shadow().querySelectorAll<HTMLElement>('.dari-menu-item')].find((b) => b.textContent === 'Settings')!.click()
    const theme = el<HTMLSelectElement>('select[aria-label="Theme"]')
    theme.value = 'dark'
    theme.dispatchEvent(new Event('change'))
    expect(document.querySelector('[data-dari-web]')!.getAttribute('data-theme')).toBe('dark')
    dari!.uninstall()
    setup({ theme: 'light' })
    expect(document.querySelector('[data-dari-web]')!.getAttribute('data-theme')).toBe('dark')
  })

  it('applies a fixed theme', () => {
    setup({ theme: 'dark' })
    expect(document.querySelector('[data-dari-web]')!.getAttribute('data-theme')).toBe('dark')
  })

  it('uninstall undoes the hook once and removes the panel', () => {
    const unhook = vi.fn()
    const first = install({ hook: () => unhook })
    first.uninstall()
    first.uninstall()
    expect(unhook).toHaveBeenCalledTimes(1)
    expect(document.querySelector('[data-dari-web]')).toBeNull()
  })

  it('removes the panel when hook throws', () => {
    expect(() =>
      install({
        hook: () => {
          throw new Error('no bridge')
        },
      }),
    ).toThrow('no bridge')
    expect(document.querySelector('[data-dari-web]')).toBeNull()
    setup()
  })

  it('rejects a second install until the first is removed', () => {
    setup()
    expect(() => install()).toThrow('already installed')
    expect(document.querySelectorAll('[data-dari-web]')).toHaveLength(1)
  })
})
