import { defineConfig } from 'tsup'

export default defineConfig([
  {
    entry: ['src/index.ts'],
    format: ['esm', 'cjs'],
    dts: true,
    clean: true,
    target: 'es2018',
  },
  // Script tag build: exposes `window.DariWeb`.
  {
    entry: ['src/index.ts'],
    format: ['iife'],
    globalName: 'DariWeb',
    minify: true,
    target: 'es2018',
  },
])
