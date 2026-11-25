#!/usr/bin/env bash
# Pulls goa_webapp's current CSS + self-hosted fonts into this app's own static
# resources, so the shop renders with the exact same brand look without a live,
# cross-origin runtime dependency on heimfeld.life for THESE two files specifically
# (see the "Style-Sync" decision in the shop concept: option B, synced at deploy time
# rather than linked live).
#
# This covers style.css/Brugty.ttf only - the two assets that define HOW the look works
# and rarely change. The ACTUAL per-edition values (which colors, which of the two font
# pairs, the background image, the logo) are fetched live instead, at startup and
# periodically, via GoaWebappBrandingClient/GET /api/branding - see that class and
# fragments/head.html. That data changes far more often (every new "Jahr"/edition) and a
# missed manual re-sync there would otherwise leave the shop visibly stale/off-brand.
#
# Run this:
#   - once after cloning, to get a local starting copy
#   - again if goa_webapp's style.css itself changes structurally (a new CSS custom
#     property, a new font) or Brugty.ttf is swapped for a different file
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
