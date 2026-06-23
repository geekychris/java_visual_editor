#!/usr/bin/env bash
# Build the plugin .zip and walk through installing it into your real IntelliJ.
#
# Why this is not fully automatic: IntelliJ requires a UI confirmation to
# install a plugin from disk. This script builds the zip, copies its path to
# the clipboard (on macOS), and tells you the exact steps.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

"$ROOT/scripts/build.sh"

ZIP="$(ls -t "$ROOT/plugin/build/distributions"/*.zip | head -1)"

cat <<EOF

==> Plugin built at:
    $ZIP

EOF

# macOS: put the path on the clipboard for one-click paste.
if command -v pbcopy >/dev/null 2>&1; then
    printf "%s" "$ZIP" | pbcopy
    echo "    (path copied to clipboard)"
    echo
fi

cat <<'EOF'
==> Install into your real IntelliJ:

    1. In IntelliJ, open Settings (Cmd+,).
    2. Plugins → click the gear icon (top) → "Install Plugin from Disk…"
    3. Paste / pick the path printed above. Click OK.
    4. Restart the IDE when prompted.

==> Strongly recommended after first install:

    The bundled JavaFX plugin's Scene Builder editor hooks into FXML undo
    events and locks the EDT. Disable it so Visual Java owns FXML files:

    Settings → Plugins → search "JavaFX" → uncheck → restart.

==> After install, smoke test:

    File → New → FXML Form → pick a directory → name "Hello" → Open the file.
    The dual-tab Designer should appear with the Palette / Properties /
    Form Outline tool windows.

==> Dev loop (no install, just sandbox):

    scripts/dev.sh
EOF
