import { defineConfig } from 'vitest/config'
import pkg from './package.json' with { type: 'json' }

export default defineConfig({
  define: { __DARI_WEB_VERSION__: JSON.stringify(pkg.version) },
  test: {
    environment: 'jsdom',
  },
})
