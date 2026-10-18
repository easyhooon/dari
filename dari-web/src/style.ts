// Lives in the panel's shadow root, so page CSS can't reach it and it can't leak out.
// Colours follow the desktop app's DariTheme.
const LIGHT = `
  --dari-bg: #ffffff; --dari-surface: #f8fafc; --dari-hover: #f2f5f8; --dari-border: #e2e7ed;
  --dari-text: #111820; --dari-text-2: #46505b; --dari-text-3: #66707b; --dari-accent: #2d6ab1;
  --dari-success: #1c7a45; --dari-warning: #8a5a00; --dari-danger: #b42318; --dari-code: #eef2f6;
  --dari-shadow: 0 8px 28px rgba(17, 24, 32, .18);
`
const DARK = `
  --dari-bg: #16191d; --dari-surface: #1d2126; --dari-hover: #23282e; --dari-border: #2f353d;
  --dari-text: #e8ecf1; --dari-text-2: #b3bbc5; --dari-text-3: #8f98a3; --dari-accent: #6ea2e6;
  --dari-success: #4cc27a; --dari-warning: #e3a83a; --dari-danger: #ff6b60; --dari-code: #131619;
  --dari-shadow: 0 8px 28px rgba(0, 0, 0, .5);
`

export const STYLE = `
:host { all: initial; ${LIGHT} }
@media (prefers-color-scheme: dark) { :host(:not([data-theme="light"])) { ${DARK} } }
:host([data-theme="dark"]) { ${DARK} }
* { box-sizing: border-box; }
button { font: inherit; color: inherit; }

.dari-launcher { position: fixed; z-index: 2147483000; right: 16px; bottom: 16px; display: flex; align-items: center; gap: 6px; padding: 8px 12px; border: 0; border-radius: 999px; background: var(--dari-accent); color: #fff; font: 600 13px/1 system-ui, -apple-system, sans-serif; box-shadow: var(--dari-shadow); cursor: pointer; touch-action: none; user-select: none; -webkit-user-select: none; }
.dari-launcher[hidden] { display: none; }
.dari-badge { min-width: 18px; padding: 2px 5px; border-radius: 999px; background: rgba(255, 255, 255, .25); font-size: 11px; text-align: center; }
.dari-badge-error { background: var(--dari-danger); }

.dari-backdrop { position: fixed; z-index: 2147483001; inset: 0; background: rgba(0, 0, 0, .25); }
.dari-sheet { position: fixed; z-index: 2147483002; left: 0; right: 0; bottom: 0; height: 75vh; display: flex; flex-direction: column; background: var(--dari-bg); color: var(--dari-text); font: 13px/1.45 system-ui, -apple-system, sans-serif; border-radius: 12px 12px 0 0; box-shadow: var(--dari-shadow); overflow: hidden; }
@media (min-width: 640px) { .dari-sheet { left: auto; right: 16px; bottom: 16px; width: 460px; height: min(75vh, 720px); border-radius: 12px; } }
.dari-backdrop[hidden], .dari-sheet[hidden] { display: none; }
.dari-header { display: flex; align-items: center; gap: 4px; padding: 8px 8px 8px 14px; border-bottom: 1px solid var(--dari-border); }
.dari-header-title { flex: 1; font-size: 15px; font-weight: 600; }
.dari-icon-btn { border: 0; background: none; padding: 6px 8px; border-radius: 6px; color: var(--dari-text-2); cursor: pointer; }
.dari-icon-btn:hover { background: var(--dari-hover); }
.dari-body { flex: 1; min-height: 0; overflow: auto; overscroll-behavior: contain; }

.dari-root { display: flex; flex-direction: column; min-height: 100%; }
.dari-bar { display: flex; gap: 8px; align-items: center; padding: 6px 8px; border-bottom: 1px solid var(--dari-border); background: var(--dari-bg); position: sticky; top: 0; z-index: 1; }
.dari-bar[hidden] { display: none; }
.dari-filter { flex: 1; min-width: 0; padding: 6px 8px; font: inherit; color: inherit; background: var(--dari-surface); border: 1px solid var(--dari-border); border-radius: 6px; outline: none; }
.dari-filter:focus { border-color: var(--dari-accent); }
.dari-count { color: var(--dari-text-3); font-size: 12px; white-space: nowrap; }
.dari-empty { padding: 32px 12px; text-align: center; color: var(--dari-text-3); }
.dari-row { display: grid; grid-template-columns: 18px 1fr auto; gap: 2px 8px; padding: 8px 10px; border-bottom: 1px solid var(--dari-border); cursor: pointer; }
.dari-row:hover, .dari-row:active { background: var(--dari-hover); }
.dari-dir { grid-row: span 2; font-weight: 600; color: var(--dari-text-3); }
.dari-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
.dari-handler { color: var(--dari-text-3); font-weight: 400; margin-left: 6px; }
.dari-meta { grid-column: 2 / 4; display: flex; gap: 10px; color: var(--dari-text-3); font-size: 12px; }
.dari-status { font-size: 11px; font-weight: 600; align-self: start; }
.dari-status-IN_PROGRESS { color: var(--dari-warning); }
.dari-status-SUCCESS { color: var(--dari-success); }
.dari-status-ERROR { color: var(--dari-danger); }
.dari-detail { padding: 0 10px 16px; }
.dari-back { color: var(--dari-accent); background: none; border: 0; padding: 4px 0; cursor: pointer; }
.dari-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
.dari-table { width: 100%; border-collapse: collapse; margin: 8px 0; }
.dari-table th { text-align: left; font-weight: 400; color: var(--dari-text-3); padding: 3px 8px 3px 0; white-space: nowrap; vertical-align: top; width: 1%; }
.dari-table td { padding: 3px 0; word-break: break-all; }
.dari-section { margin-top: 12px; }
.dari-section-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; margin-bottom: 4px; }
.dari-copy { font-size: 12px; color: var(--dari-accent); background: none; border: 0; cursor: pointer; }
.dari-pre { margin: 0; padding: 8px; background: var(--dari-code); border-radius: 6px; white-space: pre-wrap; word-break: break-all; font: 12px/1.5 ui-monospace, Menlo, Consolas, monospace; }
.dari-none { color: var(--dari-text-3); font-style: italic; }
`
