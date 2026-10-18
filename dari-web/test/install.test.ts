import { describe, expect, it, vi } from 'vitest'
import { install, type VConsoleLike, type VConsolePluginLike } from '../src/install'

class FakePlugin implements VConsolePluginLike {
  readonly handlers = new Map<string, (data?: any) => void>()
  constructor(readonly id: string, readonly name: string) {}
  on(event: string, callback: (data?: any) => void) {
    this.handlers.set(event, callback)
    return this
  }
}

class FakeVConsole implements VConsoleLike {
  static VConsolePlugin = FakePlugin
  readonly plugins = new Map<string, FakePlugin>()
  tab: HTMLElement | null = null
  tools: Array<{ name: string; onClick: () => void }> = []

  addPlugin(plugin: VConsolePluginLike) {
    const p = plugin as FakePlugin
    if (this.plugins.has(p.id)) return false
    this.plugins.set(p.id, p)
    p.handlers.get('renderTab')?.((el: HTMLElement) => (this.tab = el))
    p.handlers.get('addTool')?.((tools: typeof this.tools) => (this.tools = tools))
    return true
  }

  removePlugin(id: string) {
    this.plugins.get(id)?.handlers.get('remove')?.()
    return this.plugins.delete(id)
  }
}

describe('install', () => {
  it('records through hook and renders rows newest first', () => {
    const vConsole = new FakeVConsole()
    install(vConsole, {
      hook(recorder) {
        recorder.webToAppRequest({ handlerName: 'first' })
        recorder.webToAppRequest({ handlerName: 'second', requestId: '2' })
      },
    })
    const names = [...vConsole.tab!.querySelectorAll('.dari-name')].map((el) => el.textContent)
    expect(names).toEqual(['second', 'first'])
  })

  it('shows payloads as text, never as markup', () => {
    const vConsole = new FakeVConsole()
    const dari = install(vConsole)
    dari.recorder.webToAppRequest({ handlerName: '<img src=x onerror=alert(1)>', data: '<b>hi</b>' })
    const tab = vConsole.tab!
    expect(tab.querySelector('img')).toBeNull()
    ;(tab.querySelector('.dari-row') as HTMLElement).click()
    expect(tab.querySelector('b')).toBeNull()
    expect(tab.querySelector('.dari-pre')?.textContent).toBe('<b>hi</b>')
  })

  it('opens detail, follows updates and goes back', () => {
    const vConsole = new FakeVConsole()
    const dari = install(vConsole)
    dari.recorder.webToAppRequest({ handlerName: 'getUser', requestId: '1', data: { id: 1 } })
    const tab = vConsole.tab!
    ;(tab.querySelector('.dari-row') as HTMLElement).click()
    expect(tab.querySelector('.dari-pre')?.textContent).toBe('{\n  "id": 1\n}')
    dari.recorder.webToAppResponse({ requestId: '1', isSuccess: false, data: 'nope' })
    expect(tab.querySelectorAll('.dari-pre')[1]?.textContent).toBe('nope')
    ;(tab.querySelector('.dari-back') as HTMLElement).click()
    expect(tab.querySelector('.dari-status')?.textContent).toBe('Error')
  })

  it('filters rows and clears from the tool button', () => {
    const vConsole = new FakeVConsole()
    const dari = install(vConsole)
    dari.recorder.webToAppRequest({ handlerName: 'getUser' })
    dari.recorder.webToAppRequest({ handlerName: 'pay', data: 'card' })
    const tab = vConsole.tab!
    const filter = tab.querySelector('.dari-filter') as HTMLInputElement
    filter.value = 'CARD'
    filter.dispatchEvent(new Event('input'))
    expect(tab.querySelectorAll('.dari-row')).toHaveLength(1)
    expect(tab.querySelector('.dari-count')?.textContent).toBe('1 / 2')
    vConsole.tools.find((t) => t.name === 'Clear')!.onClick()
    expect(dari.getEntries()).toHaveLength(0)
    expect(tab.querySelector('.dari-empty')?.textContent).toBe('No bridge messages yet')
  })

  it('uninstall undoes the hook once and removes the tab', () => {
    const vConsole = new FakeVConsole()
    const unhook = vi.fn()
    const dari = install(vConsole, { hook: () => unhook })
    dari.uninstall()
    dari.uninstall()
    expect(unhook).toHaveBeenCalledTimes(1)
    expect(vConsole.plugins.size).toBe(0)
  })

  it('removes the tab when hook throws', () => {
    const vConsole = new FakeVConsole()
    expect(() =>
      install(vConsole, {
        hook: () => {
          throw new Error('no bridge')
        },
      }),
    ).toThrow('no bridge')
    expect(vConsole.plugins.size).toBe(0)
  })

  it('rejects a duplicate plugin id', () => {
    const vConsole = new FakeVConsole()
    install(vConsole)
    expect(() => install(vConsole)).toThrow('already has a plugin')
  })

  it('fails clearly when VConsolePlugin cannot be found', () => {
    const bare: VConsoleLike = { addPlugin: () => true, removePlugin: () => true }
    expect(() => install(bare)).toThrow('VConsolePlugin not found')
  })
})
