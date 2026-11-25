#!/usr/bin/env bash
# Pulls goa_webapp's current CSS + self-hosted fonts into this app's own static
# resources, so the shop renders with the exact same brand look without a live,
# cross-origin runtime dependency on heimfeld.life (see the "Style-Sync" decision
# in the shop concept: option B, synced at deploy time rather than linked live).
#
# Run this:
#   - once after cloning, to get a local starting copy
#   - again any time goa_webapp's branding changes (new logo colors, a font swap, a new
#     "Jahr"/Edition) - style.css itself rarely changes, but the CURRENT EDITION'S colors/
#     fonts/background (see below) are picked up fresh every run
#
# style.css only defines HOW the CSS custom properties below are used - the actual values
# (which colors, which of the two font pairs, which background photo) are specific to
# whichever Edition is currently "active" on goa_webapp and are normally injected
# server-side per request by its fragments/head.html, reading live from its database. This
# app has no such database access (deliberately - see the shop concept's "no product data
# here" boundary), so instead this script scrapes that same rendered <style> block straight
# off the live homepage and freezes it into css/edition.css, synced at deploy time just
# like style.css/Brugty.ttf. This WILL drift from goa_webapp between syncs if an Edition's
# look changes - re-run this script after any such change on goa_webapp.
#
# SOURCE defaults to the live production site; point it at a local goa_webapp
# checkout's "src/main/resources/static" instead for local development against
# work-in-progress styling, e.g.:
#   SOURCE=file:///path/to/goa_webapp/src/main/resources/static ./scripts/sync-styles.sh
# (css/edition.css is skipped in this mode - there's no running server to scrape a
# rendered page from; the previously-synced edition.css, if any, is left untouched.)
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

fetch_edition_vars() {
    if [[ "$SOURCE" == file://* ]]; then
        echo "Skipping css/edition.css: SOURCE is a local file checkout, not a running server to scrape."
        return
    fi
    local html vars
    html="$(curl -fsSL "$SOURCE/")"
    # The one <style> block fragments/head.html renders for the current edition - matched
    # by a custom property every edition always sets, not by position on the page.
    vars="$(printf '%s' "$html" | grep -Pzo '(?s)<style>\s*:root\s*\{[^<]*?--color-primary[^<]*?\}\s*</style>' \
        | tr -d '\0' | sed -e 's/^<style>//' -e 's/<\/style>$//')"
    if [[ -z "$vars" ]]; then
        echo "Warning: could not find the current edition's CSS variables on $SOURCE/ -" >&2
        echo "leaving css/edition.css as-is. style.css's own built-in defaults apply if" >&2
        echo "this file doesn't exist yet - check manually if the shop looks off-brand." >&2
        return
    fi
    mkdir -p "$TARGET_DIR/css"
    printf '%s\n' "$vars" > "$TARGET_DIR/css/edition.css"
    echo "synced css/edition.css (current edition's colors/fonts/background reference)"

    # --bg-image: url('/path/or/https://...') - a site-root-relative path needs rewriting
    # to an absolute URL (this app isn't served from the same domain) and, so the shop
    # doesn't have a live runtime dependency on heimfeld.life for its background either, a
    # local copy to actually serve from.
    local bg_path
    bg_path="$(printf '%s' "$vars" | grep -oP "(?<=--bg-image: url\(')[^']*(?='\))" || true)"
    if [[ -n "$bg_path" && "$bg_path" == /* ]]; then
        # Root-relative - the CSS reference needs no rewriting, it'll resolve correctly
        # once this app serves the same path from its own static resources.
        local bg_dest="$TARGET_DIR${bg_path}"
        mkdir -p "$(dirname "$bg_dest")"
        curl -fsSL "$SOURCE$bg_path" -o "$bg_dest"
        echo "synced $bg_path (edition background image, referenced as a local, root-relative path)"
    elif [[ -n "$bg_path" ]]; then
        echo "edition.css references a background image by absolute URL ($bg_path) - left as a live reference, not copied locally."
    fi
}

fetch "css/style.css"
fetch "fonts/Brugty.ttf"
fetch_edition_vars

echo "Style sync from $SOURCE complete."
