#!/usr/bin/env bash
# Drive the running sandbox IDE with cliclick to take real screenshots of
# each Visual Java dialog. Uses Cmd+Shift+A (Find Action) → type the action
# name → Enter, which is reliable because it doesn't depend on pixel
# coordinates of toolbar buttons.
#
# Requirements:
#   brew install cliclick   (you already have this)
#   The sandbox IDE running (scripts/dev.sh) with an FXML file in the
#   active editor tab. Recommended: Hello.fxml.
#
# What it captures:
#   13-wireup-dialog.png       Wire-Up Recipe dialog
#   14-pojo-binding.png        POJO Binding wizard
#   15-menu-editor.png         Menu Editor dialog
#   16-tab-order.png           Tab Order dialog
#
# (The non-dialog screenshots — palette / properties / outline / canvas —
# are static and well-handled by the embed-real-renders mockups in
# scripts/generate-mockup-screenshots.py. This script focuses on the
# dialogs, where having a REAL capture matters most.)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMG="$ROOT/docs/images"
mkdir -p "$IMG"

if ! command -v cliclick >/dev/null 2>&1; then
    echo "cliclick not found — install with: brew install cliclick" >&2
    exit 1
fi
if ! command -v screencapture >/dev/null 2>&1; then
    echo "screencapture not found — this script is macOS-only." >&2
    exit 1
fi

# cliclick needs Accessibility access to send keystrokes — check up front.
if cliclick -V 2>&1 | grep -qi "accessibility"; then
    cat <<'EOF' >&2

cliclick says Accessibility access is NOT enabled. Without it, keystrokes
(Cmd+Shift+A, Enter, Esc) silently fail and this script can't drive the IDE.

To enable:
  System Settings -> Privacy & Security -> Accessibility -> add Terminal
  (or iTerm / your shell host), check the box, restart this terminal.

EOF
    exit 1
fi

# `screencapture` needs Screen Recording access on macOS Catalina+. Test by
# capturing a 1x1 region to /tmp; if that fails, the terminal lacks the
# Screen Recording entitlement.
PROBE="/tmp/visualjava-screencapture-probe.$$.png"
if ! screencapture -R 0,0,1,1 "$PROBE" 2>/dev/null; then
    cat <<'EOF' >&2

screencapture failed — the terminal lacks Screen Recording permission.
Without it, screencapture writes nothing useful (the "could not create
image from display" error).

To enable:
  System Settings -> Privacy & Security -> Screen & System Audio Recording
  -> add Terminal (or iTerm / your shell host), check the box, RESTART
  THE TERMINAL. The permission is only applied to new terminal sessions
  after the change.

EOF
    exit 1
fi
[ -f "$PROBE" ] || true
rm -f "$PROBE"

# Tweak these if your IDE feels sluggish:
SETTLE_AFTER_FIND_ACTION=0.4   # Find Action popup appears
SETTLE_AFTER_TYPE=0.5          # narrowing the action list
SETTLE_AFTER_OPEN=1.6          # dialog opens (plugin warming + render)
SETTLE_AFTER_CLOSE=0.4

# Capture a dialog by name.
#
# cliclick syntax notes:
#   - kp:<name>   single named key only (return, esc, delete, …) — NO modifiers
#   - kd:<mod>    hold a modifier (cmd, shift, ctrl, alt/option, fn)
#   - ku:<mod>    release a modifier
#   - t:<text>    type characters — pairs with kd: to make hotkeys
# So Cmd+Shift+A is:  kd:cmd kd:shift t:a ku:shift ku:cmd
capture_dialog() {
    local out="$1"; local action="$2"
    echo "    -> $out  (action: $action)"
    # Cmd+Shift+A → Find Action
    cliclick kd:cmd kd:shift t:a ku:shift ku:cmd
    sleep "$SETTLE_AFTER_FIND_ACTION"
    # Clear any leftover search text: Cmd+A, then Delete
    cliclick kd:cmd t:a ku:cmd
    cliclick kp:delete
    # Type the action name (we omit punctuation — Find Action does fuzzy matching
    # against the registered text, so "Visual Java Wire-Up" matches
    # "Visual Java — Wire-Up Recipe…").
    cliclick t:"$action"
    sleep "$SETTLE_AFTER_TYPE"
    # Activate the top match
    cliclick kp:return
    sleep "$SETTLE_AFTER_OPEN"
    # Full-screen capture — the dialog is centred and the IDE chrome behind
    # it is fine to keep in frame; crop later if you want.
    screencapture "$IMG/$out"
    sleep "$SETTLE_AFTER_CLOSE"
    # Close the dialog
    cliclick kp:esc
    sleep "$SETTLE_AFTER_CLOSE"
}

cat <<'EOF'

Visual Java — cliclick capture walkthrough
==========================================

Set-up:
  1. Run scripts/dev.sh in another terminal — sandbox IDE.
  2. In the sandbox, open Hello.fxml (or any non-trivial FXML) so the
     designer tab is the SELECTED EDITOR.
  3. CLICK ONCE IN THE SANDBOX so it has keyboard focus. This script will
     not switch app focus for you.

Then press Enter here. Tab back to the sandbox within 3 seconds.

EOF
read -r -p "Ready? " _
echo "    Switch to the sandbox window NOW..."
for i in 3 2 1; do
    echo "    ${i}..."
    sleep 1
done
echo

echo "==> Capturing dialogs"
capture_dialog "13-wireup-dialog.png"  "Visual Java Wire-Up"
capture_dialog "14-pojo-binding.png"   "Visual Java Bind POJO"
capture_dialog "15-menu-editor.png"    "Visual Java Menu Editor"
capture_dialog "16-tab-order.png"      "Visual Java Tab Order"

cat <<EOF

==> Done. Captured to $IMG/.

If a dialog screenshot looks wrong (e.g., the IDE wasn't focused, or the
action search picked the wrong entry), re-run only that capture by
copying the corresponding line of this script.

The other screenshots (overview, palette, outline, properties, etc.) are
mockups with embedded real form renders — produced by
   scripts/generate-mockup-screenshots.py
which gives much better results than trying to drive multi-region IDE
captures for those.

EOF
