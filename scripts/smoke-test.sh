#!/usr/bin/env bash
# Launches a packaged ExampleApp and checks that its first window came up.
#
# The app's smoke-test hook (-Dexampleapp.smokeTest=<file>) writes `window-shown` to <file> once the
# first window is showing, then exits 0. That proves the whole chain a user's launch goes through:
# launcher, bundled runtime, JavaFX natives, and the app's own start-up. A fresh config directory
# keeps the runner's (or your) settings out of it. Linux without a display runs under xvfb-run.
#
# Usage: smoke-test.sh <launcher> <work-dir>
set -euo pipefail

launcher="${1:?launcher required}"
work="${2:?work directory required}"
[ -x "$launcher" ] || [ -f "$launcher" ] || { echo "::error::no launcher at $launcher"; exit 1; }
rm -rf "$work"
mkdir -p "$work/config"
marker="$work/smoke.txt"
log="$work/smoke.log"

export JAVA_TOOL_OPTIONS="-Dexampleapp.smokeTest=$marker"
export EXAMPLEAPP_CONFIG_DIR="$work/config"

run=("$launcher")
if [ "$(uname -s)" = "Linux" ] && [ -z "${DISPLAY:-}" ] && [ -z "${WAYLAND_DISPLAY:-}" ]; then
    command -v xvfb-run >/dev/null || { echo "::error::no display and no xvfb-run"; exit 1; }
    run=(xvfb-run -a "$launcher")
fi

# A hung start must fail the job rather than hold it until its timeout. Polled rather than `timeout`,
# which macOS lacks.
"${run[@]}" > "$log" 2>&1 &
pid=$!
for _ in $(seq 90); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
done
if kill -0 "$pid" 2>/dev/null; then
    echo "::warning::still running after 90 s, stopping it"
    kill "$pid" 2>/dev/null || true
fi
status=0
wait "$pid" || status=$?

echo "--- app output (exit $status) ---"
cat "$log"
echo "---"
if ! grep -q 'window-shown' "$marker" 2>/dev/null; then
    echo "::error::the app did not report its first window within 90 s (exit $status)"
    exit 1
fi
[ "$status" = 0 ] || { echo "::error::the window came up but the app exited with $status"; exit 1; }
echo "smoke test: the packaged app opened its window and exited cleanly"
