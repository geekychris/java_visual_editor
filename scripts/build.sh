#!/usr/bin/env bash
# Build the Visual Java IntelliJ plugin into an installable .zip.
#
# Output: plugin/build/distributions/plugin-<version>.zip
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> Building plugin distribution"
./gradlew :plugin:buildPlugin

DIST_DIR="$ROOT/plugin/build/distributions"
ZIP="$(ls -t "$DIST_DIR"/*.zip 2>/dev/null | head -1 || true)"

if [[ -z "${ZIP:-}" ]]; then
    echo "ERROR: no .zip produced under $DIST_DIR" >&2
    exit 1
fi

SIZE="$(du -h "$ZIP" | awk '{print $1}')"
echo
echo "==> Built: $ZIP ($SIZE)"
echo "==> Install with: scripts/install.sh"
