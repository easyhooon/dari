import { execFile, execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'

const POLL_MILLIS = 3000

/** `adb` on PATH, else the SDK's copy. */
export function findAdb(env: NodeJS.ProcessEnv = process.env): string {
  const sdks = [env.ANDROID_HOME, env.ANDROID_SDK_ROOT, join(homedir(), 'Library/Android/sdk'), join(homedir(), 'Android/Sdk')]
  for (const sdk of sdks) {
    if (!sdk) continue
    const adb = join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb')
    if (existsSync(adb)) return adb
  }
  return 'adb'
}

/** Serials of devices ready for commands, from `adb devices` output. */
export function parseDevices(output: string): string[] {
  return output
    .split('\n')
    .slice(1)
    .map((line) => line.trim().split(/\s+/))
    .filter((cols) => cols.length >= 2 && cols[1] === 'device')
    .map((cols) => cols[0]!)
}

/** Keeps `adb reverse tcp:<port>` set on every connected device, including ones plugged in later. */
export class AdbReverser {
  private readonly reversed = new Set<string>()
  private timer: ReturnType<typeof setInterval> | undefined
  private warned = false

  constructor(
    private readonly port: number,
    private readonly log: (line: string) => void,
    private readonly adb = findAdb(),
  ) {}

  start(): void {
    this.poll()
    this.timer = setInterval(() => this.poll(), POLL_MILLIS)
    this.timer.unref()
  }

  /** Synchronous so it can run from an exit handler. */
  stop(): void {
    clearInterval(this.timer)
    for (const serial of this.reversed) {
      try {
        execFileSync(this.adb, ['-s', serial, 'reverse', '--remove', `tcp:${this.port}`], { stdio: 'ignore', timeout: 3000 })
      } catch {
        // Device already gone.
      }
    }
    this.reversed.clear()
  }

  private poll(): void {
    execFile(this.adb, ['devices'], { timeout: 5000 }, (error, stdout) => {
      if (error) {
        if (!this.warned && (error as NodeJS.ErrnoException).code === 'ENOENT') {
          this.warned = true
          this.log('adb not found; run `adb reverse` yourself or set ANDROID_HOME (pass --no-adb to hide this)')
          clearInterval(this.timer)
        }
        return
      }
      const serials = new Set(parseDevices(stdout))
      for (const serial of this.reversed) if (!serials.has(serial)) this.reversed.delete(serial)
      for (const serial of serials) {
        if (this.reversed.has(serial)) continue
        this.reversed.add(serial)
        execFile(this.adb, ['-s', serial, 'reverse', `tcp:${this.port}`, `tcp:${this.port}`], { timeout: 5000 }, (err) => {
          if (err) {
            this.reversed.delete(serial)
            this.log(`adb reverse failed on ${serial}: ${err.message.split('\n')[0]}`)
          } else {
            this.log(`adb reverse tcp:${this.port} on ${serial}`)
          }
        })
      }
    })
  }
}
