#!/usr/bin/env bash
# Measures how long ExampleApp takes to start (Pauta PERF-19): several cold launches of a packaged app, each in
# a fresh config folder, and the median of each phase mark the app records (main, prepared, window,
# first-paint: StartupMarks), in milliseconds from the process's start. Linux without a display runs under
# xvfb-run. A change to the start-up path quotes these numbers in its pull request (PERF-18).
#
# Usage: measure-startup.sh <launcher> [runs]   (runs: 5 by default)
#   scripts/measure-startup.sh target/dist/ExampleApp/bin/ExampleApp 7
set -euo pipefail

launcher="${1:?launcher required}"
runs="${2:-5}"
[ -x "$launcher" ] || [ -f "$launcher" ] || { echo "no launcher at $launcher" >&2; exit 1; }
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

run=("$launcher")
if [ "$(uname -s)" = "Linux" ] && [ -z "${DISPLAY:-}" ] && [ -z "${WAYLAND_DISPLAY:-}" ]; then
    command -v xvfb-run >/dev/null || { echo "no display and no xvfb-run" >&2; exit 1; }
    run=(xvfb-run -a "$launcher")
fi

for i in $(seq "$runs"); do
    mkdir -p "$work/config-$i"
    JAVA_TOOL_OPTIONS="-Dexampleapp.startupMarks=$work/marks-$i.txt" \
        EXAMPLEAPP_CONFIG_DIR="$work/config-$i" "${run[@]}" > "$work/run-$i.log" 2>&1 || true
    [ -s "$work/marks-$i.txt" ] || { echo "run $i wrote no marks; its output:" >&2; cat "$work/run-$i.log" >&2; exit 1; }
done

echo "Median of $runs cold starts (ms from the process's start):"
for phase in main prepared window first-paint; do
    cat "$work"/marks-*.txt | awk -v p="$phase" '$1 == p { print $2 }' | sort -n |
        awk -v p="$phase" '{ v[NR] = $1 } END {
            if (NR == 0) exit
            m = (NR % 2) ? v[(NR + 1) / 2] : (v[NR / 2] + v[NR / 2 + 1]) / 2
            printf "  %-12s %6d\n", p, m
        }'
done
