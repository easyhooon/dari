import { DEFAULT_RELAY_PORT } from '../remote-protocol'

export const HELP = `Usage: dari-web serve [options]

Relays bridge traffic from pages installed with \`remote\` to a viewer at http://localhost:<port>.

Options:
  --port <n>   Port to listen on (default ${DEFAULT_RELAY_PORT})
  --no-adb     Don't run \`adb reverse\` for connected Android devices
  --open       Open the viewer in the default browser
  -h, --help   Show this help
  -v, --version
`

export interface Args {
  port: number
  adb: boolean
  open: boolean
}

export function parseArgs(argv: string[]): Args | 'help' | 'version' {
  const args: Args = { port: DEFAULT_RELAY_PORT, adb: true, open: false }
  const rest = argv[0] === 'serve' ? argv.slice(1) : argv
  for (let i = 0; i < rest.length; i++) {
    const arg = rest[i]!
    if (arg === '-h' || arg === '--help') return 'help'
    if (arg === '-v' || arg === '--version') return 'version'
    if (arg === '--no-adb') args.adb = false
    else if (arg === '--open') args.open = true
    else if (arg === '--port' || arg.startsWith('--port=')) {
      const value = arg.includes('=') ? arg.slice('--port='.length) : rest[++i]
      const port = Number(value)
      if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error(`Invalid port: ${value}`)
      args.port = port
    } else {
      throw new Error(`Unknown argument: ${arg}`)
    }
  }
  return args
}
