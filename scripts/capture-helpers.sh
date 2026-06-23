#!/usr/bin/env bash
# Helpers used by interactive screenshot capture session.
# Sourced from inline Bash calls; not executable standalone.

set -uo pipefail

SANDBOX_PID="$(pgrep -f 'idea.plugin.in.sandbox.mode=true' | head -1)"
if [ -z "$SANDBOX_PID" ]; then
    echo "sandbox not running" >&2
    return 1 2>/dev/null || exit 1
fi

IMG="/Users/chris/code/java_forms/docs/images"

# Get the main editor window ID (layer 0, owner = sandbox).
# Optionally filter by name substring (case-insensitive).
sandbox_wid() {
    local match="${1:-}"
    python3 <<PY
from Quartz import (CGWindowListCopyWindowInfo, kCGNullWindowID,
                   kCGWindowListOptionOnScreenOnly,
                   kCGWindowListExcludeDesktopElements)
wins = CGWindowListCopyWindowInfo(
    kCGWindowListOptionOnScreenOnly | kCGWindowListExcludeDesktopElements,
    kCGNullWindowID)
match = "$match".lower()
candidates = []
for w in wins:
    if w.get('kCGWindowOwnerPID') != $SANDBOX_PID: continue
    if w.get('kCGWindowLayer', 0) != 0: continue
    name = (w.get('kCGWindowName') or '').lower()
    if match and match not in name: continue
    b = w.get('kCGWindowBounds', {})
    candidates.append((w.get('kCGWindowNumber'), name, int(b.get('Width', 0)) * int(b.get('Height', 0))))
# Pick the largest-area window matching
if not candidates:
    print('')
else:
    candidates.sort(key=lambda t: -t[2])
    print(candidates[0][0])
PY
}

# List all sandbox-owned visible windows for debugging.
sandbox_windows() {
    python3 <<PY
from Quartz import *
wins = CGWindowListCopyWindowInfo(
    kCGWindowListOptionOnScreenOnly | kCGWindowListExcludeDesktopElements,
    kCGNullWindowID)
for w in wins:
    if w.get('kCGWindowOwnerPID') != $SANDBOX_PID: continue
    name = w.get('kCGWindowName') or ''
    wid = w.get('kCGWindowNumber')
    layer = w.get('kCGWindowLayer', 0)
    b = w.get('kCGWindowBounds', {})
    print(f'wid={wid} layer={layer} name={name!r} bounds={dict(b)}')
PY
}

# Get screen bounds of main editor window (x y w h).
sandbox_bounds() {
    osascript <<EOF
tell application "System Events"
    set proc to first process whose unix id is $SANDBOX_PID
    set w to front window of proc
    set p to position of w
    set s to size of w
    return (item 1 of p as string) & " " & (item 2 of p as string) & " " & (item 1 of s as string) & " " & (item 2 of s as string)
end tell
EOF
}

# Activate sandbox + dismiss any modal popovers.
sandbox_focus() {
    osascript -e "tell application \"System Events\" to set frontmost of first process whose unix id is $SANDBOX_PID to true" >/dev/null
    sleep 0.6
    osascript -e 'tell application "System Events" to key code 53' >/dev/null 2>&1 || true
    sleep 0.3
}

# Send Return via System Events. cliclick's kp:return is silently dropped
# in IntelliJ's Find Action popup (observed 2026-06-22); keystroke works.
press_return() {
    osascript -e 'tell application "System Events" to keystroke return'
}

press_esc() {
    osascript -e 'tell application "System Events" to key code 53'
}

# Find Action: type a search string, hit Return.
# Skips Cmd+A/Delete — the popup opens with an empty search field already
# focused, so the preamble can misfire (Cmd+A here toggled "Include
# disabled actions" instead of selecting text). Uses keystroke return.
find_action() {
    local q="$1"
    cliclick kd:cmd kd:shift t:a ku:shift ku:cmd
    sleep 0.7
    cliclick t:"$q"
    sleep 0.8
    press_return
    sleep 1.0
}

# Capture the largest sandbox window (main editor by default) by its ID.
capture_main_window() {
    local out="$1"
    local wid; wid="$(sandbox_wid)"
    if [ -z "$wid" ]; then
        echo "could not find sandbox main window" >&2
        return 1
    fi
    screencapture -l "$wid" -o "$IMG/$out"
    echo "    -> $IMG/$out ($(stat -f%z "$IMG/$out") bytes, wid=$wid)"
}

# Capture the topmost sandbox window matching a name substring.
capture_named_window() {
    local out="$1"
    local match="$2"
    local wid; wid="$(sandbox_wid "$match")"
    if [ -z "$wid" ]; then
        echo "could not find sandbox window matching '$match'" >&2
        sandbox_windows >&2
        return 1
    fi
    screencapture -l "$wid" -o "$IMG/$out"
    echo "    -> $IMG/$out ($(stat -f%z "$IMG/$out") bytes, wid=$wid match='$match')"
}

# Capture a region (relative to screen). Args: out x y w h
capture_region() {
    local out="$1" x="$2" y="$3" w="$4" h="$5"
    screencapture -R "$x,$y,$w,$h" "$IMG/$out"
    echo "    -> $IMG/$out ($(stat -f%z "$IMG/$out") bytes, region=${x},${y},${w}x${h})"
}
