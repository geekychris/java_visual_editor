#!/usr/bin/env bash
# Kill any running sandbox + sidecar renderer, then launch a fresh sandbox IDE
# with the current source. Used during plugin development.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

pkill -9 -f "ideaIC-2025" 2>/dev/null || true
pkill -9 -f "preview-renderer.jar" 2>/dev/null || true
sleep 1

echo "==> Launching sandbox IntelliJ with Visual Java plugin"
exec ./gradlew runIde
