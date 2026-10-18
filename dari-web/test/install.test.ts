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
    expect(el('.dari-pre').textContent).toBe('{\n  "id": 1\n}')
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
