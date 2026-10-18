// vConsole theme variables, with light fallbacks for pages that style the tab outside vConsole.
export const STYLE = `
.dari-root { display: flex; flex-direction: column; height: 100%; font-size: 13px; color: var(--VC-FG-0, #000); }
.dari-bar { display: flex; gap: 8px; align-items: center; padding: 6px 8px; border-bottom: 1px solid var(--VC-FG-3, #ddd); background: var(--VC-BG-2, #fff); position: sticky; top: 0; z-index: 1; }
.dari-bar[hidden] { display: none; }
.dari-filter { flex: 1; min-width: 0; padding: 4px 8px; font: inherit; color: inherit; background: var(--VC-BG-1, #f6f6f6); border: 1px solid var(--VC-FG-3, #ddd); border-radius: 4px; }
.dari-count { color: var(--VC-FG-1, #888); font-size: 12px; white-space: nowrap; }
.dari-empty { padding: 24px 12px; text-align: center; color: var(--VC-FG-1, #888); }
.dari-row { display: grid; grid-template-columns: 18px 1fr auto; gap: 2px 8px; padding: 8px; border-bottom: 1px solid var(--VC-FG-3, #eee); cursor: pointer; }
.dari-row:active { background: var(--VC-BG-COLOR-ACTIVE, #eee); }
.dari-dir { grid-row: span 2; font-weight: 600; color: var(--VC-FG-1, #888); }
.dari-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
.dari-handler { color: var(--VC-FG-1, #888); font-weight: 400; margin-left: 6px; }
.dari-meta { grid-column: 2 / 4; display: flex; gap: 10px; color: var(--VC-FG-1, #888); font-size: 12px; }
.dari-status { font-size: 11px; font-weight: 600; padding: 1px 6px; border-radius: 3px; align-self: start; }
.dari-status-IN_PROGRESS { color: var(--VC-ORANGE, #b26a00); background: var(--VC-WARN-BG, #fffbe5); }
.dari-status-SUCCESS { color: var(--VC-TEXTGREEN, #1a7f37); background: var(--VC-BG-1, #eef8f0); }
.dari-status-ERROR { color: var(--VC-RED, #d1242f); background: var(--VC-ERROR-BG, #fff0f0); }
.dari-detail { padding: 0 8px 16px; }
.dari-back { font: inherit; color: var(--VC-LINK, #0969da); background: none; border: 0; padding: 4px 0; cursor: pointer; }
.dari-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
.dari-table { width: 100%; border-collapse: collapse; margin: 8px 0; }
.dari-table th { text-align: left; font-weight: 400; color: var(--VC-FG-1, #888); padding: 3px 8px 3px 0; white-space: nowrap; vertical-align: top; width: 1%; }
.dari-table td { padding: 3px 0; word-break: break-all; }
.dari-section { margin-top: 12px; }
.dari-section-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; margin-bottom: 4px; }
.dari-copy { font: inherit; font-size: 12px; color: var(--VC-LINK, #0969da); background: none; border: 0; cursor: pointer; }
.dari-pre { margin: 0; padding: 8px; background: var(--VC-BG-1, #f6f6f6); border-radius: 4px; white-space: pre-wrap; word-break: break-all; font: 12px/1.45 ui-monospace, Menlo, Consolas, monospace; max-height: 50vh; overflow: auto; }
.dari-none { color: var(--VC-FG-1, #888); font-style: italic; }
`
