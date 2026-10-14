#!/bin/sh
# Finishes a Dari update after the app has quit: swaps the staged Contents into the app bundle,
# relaunches it, and rolls back if anything fails. Modeled on Necto's update handoff.
#   dari-finish-update.sh <old pid> <target .app> <workspace>
set -u

pid="$1"
target="$2"
workspace="$3"
# Tests replace `open` and the retry delay so they don't launch anything or wait.
open_app="${DARI_UPDATE_OPEN:-/usr/bin/open}"
retry_delay="${DARI_UPDATE_RETRY_DELAY:-1}"

# LaunchServices keeps the quitting Dari registered for a moment. An `open` in that window sends
# a reopen event to the dead process and fails with -600 (procNotFound), so force a new instance
# and retry before giving up.
launch() {
  attempt=1
  while ! "$open_app" -n "$1"; do
    if [ "$attempt" -ge 5 ]; then
      return 1
    fi
    attempt=$((attempt + 1))
    sleep "$retry_delay"
  done
}

case "$(basename "$workspace")" in
  .dari-update-*) ;;
  *) echo "Invalid update workspace: $workspace"; exit 2 ;;
esac
if [ "$(dirname "$workspace")" != "$(dirname "$target")" ] || [ ! -d "$target/Contents" ] ||
  [ ! -d "$workspace/Contents" ] || [ -e "$workspace/PreviousContents" ]; then
  echo "Invalid update arguments."
  exit 2
fi

# Wait up to 30 seconds for Dari to exit before touching its bundle.
tries=0
while kill -0 "$pid" 2> /dev/null; do
  tries=$((tries + 1))
  if [ "$tries" -ge 300 ]; then
    echo "Dari did not exit within 30 seconds. Nothing was installed."
    exit 1
  fi
  sleep 0.1
done
# Let LaunchServices finish unregistering the old process before relaunching.
sleep "$retry_delay"

if ! mv "$target/Contents" "$workspace/PreviousContents"; then
  launch "$target"
  echo "The existing app could not be moved. Nothing was installed."
  exit 1
fi
if ! mv "$workspace/Contents" "$target/Contents"; then
  if mv "$workspace/PreviousContents" "$target/Contents"; then
    launch "$target"
    echo "The update could not be installed. The previous version was restored."
  else
    echo "The previous version could not be restored. Recover it from $workspace/PreviousContents."
  fi
  exit 1
fi
if ! launch "$target"; then
  if mv "$target/Contents" "$workspace/Contents" && mv "$workspace/PreviousContents" "$target/Contents"; then
    launch "$target"
    echo "The update could not be opened. The previous version was restored."
  else
    echo "The previous version could not be restored. Recover it from $workspace/PreviousContents."
  fi
  exit 1
fi
rm -rf "$workspace"
