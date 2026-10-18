import { defineConfig } from 'tsup'
import pkg from './package.json' with { type: 'json' }

const define = { __DARI_WEB_VERSION__: JSON.stringify(pkg.version) }

export default defineConfig([
  {
    entry: ['src/index.ts'],
    format: ['esm', 'cjs'],
    dts: true,
    clean: true,
    target: 'es2018',
    define,
  },
  // Script tag build: exposes `window.DariWeb`.
  {
    entry: ['src/index.ts'],
    format: ['iife'],
    globalName: 'DariWeb',
    minify: true,
    target: 'es2018',
    define,
  },
])
