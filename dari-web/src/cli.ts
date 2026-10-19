import { execFile } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { VERSION } from './export'
import { AdbReverser } from './relay/adb'
import { HELP, parseArgs } from './relay/args'
import { startRelay } from './relay/server'
import { VIEWER_HTML } from './relay/viewer-html'

async function main(): Promise<void> {
  let args: ReturnType<typeof parseArgs>
  try {
    args = parseArgs(process.argv.slice(2))
  } catch (error) {
    console.error(`${(error as Error).message}\n\n${HELP}`)
    process.exit(2)
  }
  if (args === 'help') return void process.stdout.write(HELP)
  if (args === 'version') return void console.log(VERSION)

  const log = (line: string) => console.log(`[dari-web] ${line}`)
  const scriptUrl = new URL('./viewer.global.js', import.meta.url)
  let relay
  try {
    relay = await startRelay({ port: args.port, viewerHtml: VIEWER_HTML, viewerScript: () => readFileSync(scriptUrl, 'utf8'), log })
  } catch (error) {
    const code = (error as NodeJS.ErrnoException).code
    console.error(code === 'EADDRINUSE' ? `Port ${args.port} is in use; try --port <n>` : String(error))
    process.exit(1)
  }

  const url = `http://localhost:${relay.port}`
  log(`viewer: ${url}`)
  log(`pages:  install({ remote: 'ws://localhost:${relay.port}' })`)

  const adb = args.adb ? new AdbReverser(relay.port, log) : null
  adb?.start()
  if (args.open) openBrowser(url)

  let stopping = false
  const stop = () => {
    if (stopping) return
    stopping = true
    adb?.stop()
    void relay.close().then(() => process.exit(0))
    setTimeout(() => process.exit(0), 1000).unref()
  }
  process.on('SIGINT', stop)
  process.on('SIGTERM', stop)
}

function openBrowser(url: string): void {
  const [command, args] =
    process.platform === 'darwin' ? ['open', [url]] : process.platform === 'win32' ? ['cmd', ['/c', 'start', '', url]] : ['xdg-open', [url]]
  execFile(command, args, () => {})
}

void main()
