#!/bin/sh
# Finishes a Linux tarball update after Dari has quit: swaps the staged folder in for the old one,
# relaunches it, and rolls back if it doesn't start.
#   dari-finish-update-linux.sh <old pid> <target folder> <workspace>
set -u

pid="$1"
target="$2"
workspace="$3"
# Tests replace the launcher and the settle delay so they don't start anything or wait.
run_app="${DARI_UPDATE_OPEN:-/usr/bin/env}"
settle="${DARI_UPDATE_SETTLE_DELAY:-3}"

# Starts bin/Dari detached and counts it as launched if it is still running after the settle delay
# or already exited cleanly; a broken image usually dies right away.
launch() {
  "$run_app" "$1/bin/Dari" > /dev/null 2>&1 < /dev/null &
  child=$!
  sleep "$settle"
  if kill -0 "$child" 2> /dev/null; then
    return 0
  fi
  wait "$child"
}

case "$(basename "$workspace")" in
  .dari-update-*) ;;
  *) echo "Invalid update workspace: $workspace"; exit 2 ;;
esac
if [ "$(dirname "$workspace")" != "$(dirname "$target")" ] || [ ! -x "$target/bin/Dari" ] ||
  [ ! -x "$workspace/Dari/bin/Dari" ] || [ -e "$workspace/Previous" ]; then
  echo "Invalid update arguments."
  exit 2
fi

# Wait up to 30 seconds for Dari to exit before touching its folder.
tries=0
while kill -0 "$pid" 2> /dev/null; do
  tries=$((tries + 1))
  if [ "$tries" -ge 300 ]; then
    echo "Dari did not exit within 30 seconds. Nothing was installed."
    exit 1
  fi
  sleep 0.1
done

if ! mv "$target" "$workspace/Previous"; then
  launch "$target"
  echo "The existing app could not be moved. Nothing was installed."
  exit 1
fi
if ! mv "$workspace/Dari" "$target"; then
  if mv "$workspace/Previous" "$target"; then
    launch "$target"
    echo "The update could not be installed. The previous version was restored."
  else
    echo "The previous version could not be restored. Recover it from $workspace/Previous."
  fi
  exit 1
fi
if ! launch "$target"; then
  if mv "$target" "$workspace/Dari" && mv "$workspace/Previous" "$target"; then
    launch "$target"
    echo "The update could not be opened. The previous version was restored."
  else
    echo "The previous version could not be restored. Recover it from $workspace/Previous."
  fi
  exit 1
fi
rm -rf "$workspace"
