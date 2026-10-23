import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { MessageStore } from '../src/store'
import type { DariWebOptions, Recorder } from '../src/types'
import { toWireDetail } from '../src/wire'

interface FixtureEvent {
  at: number
  pageUrl?: string
  type: keyof Recorder
  [key: string]: unknown
}

interface Fixture {
  description: string
  options: DariWebOptions
  events: FixtureEvent[]
  expected: unknown[]
}

const dir = join(__dirname, '../../fixtures/messages')
const files = readdirSync(dir).filter((f) => f.endsWith('.json'))

describe('shared fixtures', () => {
  it('exist', () => {
    expect(files.length).toBeGreaterThan(0)
  })

  for (const file of files) {
    const fixture = JSON.parse(readFileSync(join(dir, file), 'utf8')) as Fixture
    it(`${file}: ${fixture.description}`, () => {
      let now = 0
      let pageUrl: string | null = null
      const store = new MessageStore(fixture.options, { now: () => now, pageUrl: () => pageUrl })
      for (const { at, pageUrl: url, type, ...event } of fixture.events) {
        now = at
        pageUrl = url ?? null
        ;(store.recorder[type] as (e: unknown) => void)(event)
      }
      expect(store.getEntries().map(toWireDetail)).toEqual(fixture.expected)
    })
  }
})
