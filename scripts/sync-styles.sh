#!/usr/bin/env bash
# Pulls goa_webapp's current CSS + self-hosted fonts into this app's own static
# resources, so the shop renders with the exact same brand look without a live,
# cross-origin runtime dependency on heimfeld.life (see the "Style-Sync" decision
# in the shop concept: option B, synced at deploy time rather than linked live).
#
# Run this:
#   - once after cloning, to get a local starting copy
#   - again any time goa_webapp's branding changes (new logo colors, a font swap, etc.)
#   - automatically during the Docker build (see Dockerfile), so every deploy is fresh
#
# SOURCE defaults to the live production site; point it at a local goa_webapp
# checkout's "src/main/resources/static" instead for local development against
# work-in-progress styling, e.g.:
#   SOURCE=file:///path/to/goa_webapp/src/main/resources/static ./scripts/sync-styles.sh
set -euo pipefail

SOURCE="${SOURCE:-https://heimfeld.life}"
TARGET_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/src/main/resources/static"

fetch() {
    local relative_path="$1"
    local destination="$TARGET_DIR/$relative_path"
    mkdir -p "$(dirname "$destination")"
    if [[ "$SOURCE" == file://* ]]; then
        cp "${SOURCE#file://}/$relative_path" "$destination"
    else
        curl -fsSL "$SOURCE/$relative_path" -o "$destination"
    fi
    echo "synced $relative_path"
}

fetch "css/style.css"
fetch "fonts/Brugty.ttf"

echo "Style sync from $SOURCE complete."
