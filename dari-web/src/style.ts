// Lives in the panel's shadow root, so page CSS can't reach it and it can't leak out.
// Colours follow the desktop app's DariTheme.
const LIGHT = `
  --dari-bg: #ffffff; --dari-surface: #f8fafc; --dari-hover: #f2f5f8; --dari-border: #e2e7ed;
  --dari-text: #111820; --dari-text-2: #46505b; --dari-text-3: #66707b; --dari-accent: #2d6ab1;
  --dari-success: #1c7a45; --dari-warning: #8a5a00; --dari-danger: #b42318; --dari-code: #eef2f6;
  --dari-shadow: 0 8px 28px rgba(17, 24, 32, .18);
  --dari-syntax-string: #2f6b4f; --dari-syntax-number: #2d5fa8; --dari-syntax-keyword: #8a4b9c; --dari-match: #ffe28a;
`
const DARK = `
  --dari-bg: #16191d; --dari-surface: #1d2126; --dari-hover: #23282e; --dari-border: #2f353d;
  --dari-text: #e8ecf1; --dari-text-2: #b3bbc5; --dari-text-3: #8f98a3; --dari-accent: #6ea2e6;
  --dari-success: #4cc27a; --dari-warning: #e3a83a; --dari-danger: #ff6b60; --dari-code: #131619;
  --dari-shadow: 0 8px 28px rgba(0, 0, 0, .5);
  --dari-syntax-string: #8ccba6; --dari-syntax-number: #8fb8f0; --dari-syntax-keyword: #c79ad6; --dari-match: #6e5a14;
`

export const STYLE = `
:host { all: initial; ${LIGHT} }
@media (prefers-color-scheme: dark) { :host(:not([data-theme="light"])) { ${DARK} } }
:host([data-theme="dark"]) { ${DARK} }
* { box-sizing: border-box; }
button, select, input { font: inherit; color: inherit; }

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

.dari-root { position: relative; display: flex; flex-direction: column; height: 100%; }
.dari-bar { display: flex; flex-direction: column; gap: 6px; padding: 6px 8px; border-bottom: 1px solid var(--dari-border); background: var(--dari-bg); }
.dari-bar-row { display: flex; gap: 8px; align-items: center; }
.dari-selects { flex-wrap: wrap; gap: 6px; }
.dari-select { max-width: 100%; padding: 4px 6px; font-size: 12px; background: var(--dari-surface); border: 1px solid var(--dari-border); border-radius: 6px; }
.dari-select[hidden] { display: none; }
.dari-back-bar { flex-direction: row; align-items: center; gap: 8px; margin: 0 -10px; padding: 6px 10px; position: sticky; top: 0; z-index: 1; }
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
.dari-meta { grid-column: 2 / 4; display: flex; flex-wrap: wrap; gap: 2px 10px; color: var(--dari-text-3); font-size: 12px; }
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
.dari-section-actions { display: flex; gap: 10px; font-weight: 400; }
.dari-link { font-size: 12px; color: var(--dari-accent); background: none; border: 0; padding: 2px 0; cursor: pointer; white-space: nowrap; }
.dari-tag { padding: 0 6px; border-radius: 4px; background: var(--dari-code); color: var(--dari-text-2); }
mark.dari-match { background: var(--dari-match); color: inherit; border-radius: 2px; }
.dari-pre { margin: 0; padding: 8px; background: var(--dari-code); border-radius: 6px; white-space: pre-wrap; word-break: break-all; font: 12px/1.5 ui-monospace, Menlo, Consolas, monospace; }
.dari-none { color: var(--dari-text-3); font-style: italic; }

.dari-json { white-space: normal; }
.dari-j-line { white-space: pre-wrap; word-break: break-all; }
.dari-j-toggle, .dari-j-gap { display: inline-block; width: 16px; margin-right: 2px; }
.dari-j-summary { cursor: pointer; }
.dari-j-toggle { padding: 0; border: 0; background: none; color: var(--dari-text-3); cursor: pointer; line-height: 1; }
.dari-j-key { color: var(--dari-text); font-weight: 600; }
.dari-j-string { color: var(--dari-syntax-string); }
.dari-j-number { color: var(--dari-syntax-number); }
.dari-j-keyword { color: var(--dari-syntax-keyword); }
.dari-j-punct, .dari-j-summary { color: var(--dari-text-3); }

.dari-menu { position: absolute; top: 40px; right: 8px; z-index: 3; min-width: 180px; padding: 4px; display: flex; flex-direction: column; background: var(--dari-bg); border: 1px solid var(--dari-border); border-radius: 8px; box-shadow: var(--dari-shadow); }
.dari-menu[hidden] { display: none; }
.dari-menu-item { text-align: left; padding: 8px 10px; border: 0; border-radius: 6px; background: none; cursor: pointer; }
.dari-menu-item:hover { background: var(--dari-hover); }
.dari-notice { position: absolute; left: 50%; bottom: 16px; z-index: 3; transform: translateX(-50%); max-width: calc(100% - 32px); padding: 8px 12px; border-radius: 8px; background: var(--dari-text); color: var(--dari-bg); font-size: 12px; box-shadow: var(--dari-shadow); }
.dari-notice[hidden] { display: none; }

.dari-stats { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 12px; }
.dari-stats th { text-align: right; font-weight: 400; color: var(--dari-text-3); padding: 4px; border-bottom: 1px solid var(--dari-border); }
.dari-stats td { text-align: right; padding: 6px 4px; border-bottom: 1px solid var(--dari-border); white-space: nowrap; font-variant-numeric: tabular-nums; }
.dari-stats th:first-child, .dari-stats td:first-child { text-align: left; }
.dari-stats-name { white-space: normal !important; overflow-wrap: anywhere; font-weight: 600; }
.dari-stats-name .dari-handler { display: block; margin-left: 14px; }
.dari-stats-error { color: var(--dari-danger); font-weight: 600; }
.dari-stats-slow { color: var(--dari-warning); font-weight: 600; }

.dari-settings { display: flex; flex-direction: column; margin-top: 8px; }
.dari-setting { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--dari-border); }
.dari-version { padding-top: 12px; color: var(--dari-text-3); font-size: 12px; }
`
