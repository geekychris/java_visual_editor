#!/usr/bin/env bash
# Recapture 13-16 with window-id capture so they're just the dialog, not full desktop.
set -uo pipefail

source /tmp/vj-capture-helpers.sh

IMG="/Users/chris/code/java_forms/docs/images"
MAIN_WID="$(sandbox_wid 'hello.fxml')"

# Find a layer-0 sandbox window OTHER than the main editor.
find_dialog_wid() {
    python3 <<PY
from Quartz import *
SANDBOX_PID = $SANDBOX_PID
wins = CGWindowListCopyWindowInfo(
    kCGWindowListOptionOnScreenOnly | kCGWindowListExcludeDesktopElements,
    kCGNullWindowID)
for w in wins:
    if w.get('kCGWindowOwnerPID') != SANDBOX_PID: continue
    if w.get('kCGWindowLayer', 0) != 0: continue
    if w.get('kCGWindowNumber') == $MAIN_WID: continue
    print(w.get('kCGWindowNumber'))
    break
PY
}

# Capture a dialog after firing its Find Action.
recapture() {
    local out="$1" action="$2"
    sandbox_focus
    find_action "$action"
    sleep 1.5
    local wid; wid="$(find_dialog_wid)"
    if [ -z "$wid" ]; then
        echo "    !! no dialog window appeared for '$action'" >&2
        return 1
    fi
    screencapture -l "$wid" -o "$IMG/$out"
    echo "    -> $out (wid=$wid, $(stat -f%z "$IMG/$out") bytes)"
    sleep 0.3
    press_esc
    sleep 0.8
}

recapture "13-wireup-dialog.png" "Visual Java Wire-Up"
recapture "14-pojo-binding.png"  "Visual Java Bind POJO"
recapture "15-menu-editor.png"   "Visual Java Menu Editor"
recapture "16-tab-order.png"     "Visual Java Tab Order"
