#!/usr/bin/env bash
# Walk through capturing the screenshots embedded in docs/USER_GUIDE.md.
#
# How it works: at each step, we tell you what to set up in the sandbox IDE,
# then macOS `screencapture -W` lets you click the IDE window to capture it
# to docs/images/<step>.png. Press Enter to advance.
#
# Requires macOS (uses `screencapture`). On other platforms, use the OS's
# native screenshot tool and save the file with the suggested filename.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMG="$ROOT/docs/images"
mkdir -p "$IMG"

if ! command -v screencapture >/dev/null 2>&1; then
    echo "ERROR: this script uses macOS 'screencapture'. On other platforms," >&2
    echo "       take screenshots manually and save them under $IMG/." >&2
    exit 1
fi

shot() {
    local file="$1" instruction="$2"
    echo
    echo "─── $file ─────────────────────────────────────────────────────"
    echo "$instruction"
    echo
    read -r -p "Press Enter, then click the sandbox window to capture… " _
    screencapture -W "$IMG/$file"
    echo "  saved: $IMG/$file"
}

cat <<'EOF'

Visual Java — screenshot capture walkthrough
============================================

Set-up:
  1. Run `scripts/dev.sh` in another terminal so the sandbox IDE is up.
  2. Open the sample-fxml project in the sandbox.
  3. Resize the sandbox window to roughly 1400 x 900 so everything fits.

This script will guide you through ~15 captures. Press Ctrl-C any time to
stop and resume later — already-captured images are kept.

EOF
read -r -p "Ready to start? Press Enter… " _

shot "00-welcome.png" \
"Make sure the Welcome screen / Recent Projects view is visible if you have one,
otherwise the project window is fine. We just need a 'looks like IntelliJ' shot."

shot "01-overview.png" \
"Open Hello.fxml. Make Palette (left), Properties (right), and the Designer
canvas all visible. This is the headline screenshot."

shot "02-new-form-dialog.png" \
"File → New → FXML Form. Capture the dialog with template list visible."

shot "03-palette.png" \
"Maximise the Palette tool window or move the divider so Components fills
most of the height. Show categories expanded."

shot "04-form-outline.png" \
"Now show the Form Outline part of the Palette tool window (the bottom
panel) with the tree expanded for the current form."

shot "05-properties.png" \
"Show the Properties tool window with several rows visible for a Button or
similar selected component."

shot "06-vj-forms.png" \
"Open the VJ Forms tool window (left, secondary). Show forms + controllers
tree."

shot "07-designer-toolbar.png" \
"Zoom into the designer toolbar — the strip with View toggles, Align cluster,
Run, Tab Order…, Menu…, Wire-Up…, Bind POJO…, Wire All buttons."

shot "08-selection-handles.png" \
"Click a Button on the form so its red selection box + 4 corner handles
show. Zoom such that handles are clearly visible."

shot "09-smart-guides.png" \
"Drag a component near another's edge so a pink smart guide line appears.
Capture mid-drag."

shot "10-ruler-guides.png" \
"Drag from the top or left ruler to drop a guide on the form. Capture with
at least one user guide line visible (solid blue)."

shot "11-rightclick-events.png" \
"Right-click a Button. Show the popup with the Add on… events list + Rename
fx:id… + Delete entries."

shot "12-generated-handler.png" \
"Switch to the controller .java file after wiring an event. Show the
generated method with the commented cheat-sheet body."

shot "13-wireup-dialog.png" \
"Click Wire-Up… on the toolbar. Show the dialog with recipes on the left and
roles on the right."

shot "14-pojo-binding.png" \
"Click Bind POJO…. Type or browse to com.example.Contact. Show the dialog
with property → widget rows."

shot "15-menu-editor.png" \
"Click Menu… on the toolbar. Show the Menu Editor dialog with File/Edit/Help
expanded in the tree."

shot "16-tab-order.png" \
"Click Tab Order…. Show the list of focusable widgets in tab order."

shot "17-column-editor.png" \
"Drop a TableView on a form, right-click → Edit columns…. Show the dialog
with 2-3 columns filled in."

shot "18-running-app.png" \
"Click Run on the toolbar (or ./gradlew run from sample-fxml). Capture the
running JavaFX window with one of the form templates filled in."

cat <<'EOF'

==> All done. Captures saved to: $IMG
==> The user guide already references these filenames, so re-rendering it
    (or just refreshing IntelliJ's Markdown preview) should now show them.

EOF
