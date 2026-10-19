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
  // Viewer page served by `dari-web serve`.
  {
    entry: ['src/viewer.ts'],
    format: ['iife'],
    minify: true,
    target: 'es2018',
    define,
  },
  {
    entry: ['src/cli.ts'],
    format: ['esm'],
    platform: 'node',
    target: 'node18',
    banner: { js: '#!/usr/bin/env node' },
    define,
  },
])
