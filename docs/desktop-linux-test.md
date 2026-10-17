# Desktop app: Linux test record

Where the Linux build of the desktop app (`.deb` and `.tar.gz`) has been tested by hand, and what is still unverified.

## Environment

| | |
|---|---|
| Distribution | Debian GNU/Linux 13.7 (trixie), Xfce desktop, X11 session |
| Architecture | x86_64 (amd64) |
| Machine | UTM 5.0.7 VM, QEMU x86_64 emulation (TCG) on an Apple M1 Pro, macOS 26.3.1. 4 vCPU, 6 GB RAM, no GPU |
| Build JDK | Temurin 17.0.20.1 |
| Gradle | 9.1.0 (wrapper) |
| Source | 2.3.0 (`d9966d0`) |
| Date | 2026-10-17 |

## Results

| Check | Result |
|---|---|
| `:dari-inspector-protocol:test` | Pass (5 tests) |
| `:dari-desktop-app:test` | Pass (65 tests) |
| `:dari-desktop-app:createReleaseDistributable` | Pass |
| `:dari-desktop-app:packageReleaseDeb` | Pass: `dari_2.3.0-1_amd64.deb`, 41 MB, Architecture `amd64` |
| `apt install ./dari_2.3.0-1_amd64.deb` | Pass: installs to `/opt/dari`, adds a Dari entry under Development in the app menu |
| Launch without adb | Pass: the window opens and shows "adb not found" |
| Launch with Debian's `adb` package | Pass: finds `/usr/bin/adb`, shows "No device" |
| Default rendering with no GPU | Pass: Skiko can't create a GL context, falls back to software rendering and draws normally |
| Synthetic loopback fixture (fake `adb` + local bridge server) | Pass: handshake, settings update, message list and detail, live new-message badge, JSON panels |
| Real device over Wi-Fi adb (Galaxy S25+, Android 17, sample app) | Pass: device and app listed, connects, shows history and live messages, request/response panels |
| Tarball (`tar -czf` of the distributable, 54 MB) | Pass: extracts to `Dari/`, `Dari/bin/Dari` launches and finds the device |
| Update button (Xfce, AWT `Desktop.browse` unsupported) | Pass: falls back to `xdg-open`, opens the download page in Firefox |
| Settings and log locations | Settings in `~/.java/.userPrefs/io/github/easyhooon/dari/desktop`, log in `~/.local/state/dari/dari.log` |

## Not verified

- **GPU rendering**: the VM has no GPU, so only the software fallback ran. Needs a Linux PC with a GPU.
- **Wayland**: only an X11 session was tested.
- **USB device connection**: the VM reached the phone over Wi-Fi adb only; USB passthrough wasn't used.
- **Other distributions** (Ubuntu, Fedora) and **arm64 Linux**: not tested. Only amd64 builds were made.
