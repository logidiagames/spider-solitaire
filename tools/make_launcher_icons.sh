#!/usr/bin/env bash
# Regenerates every launcher icon from the single game icon master:
#   app/src/main/ic_launcher-playstore.png  (512x512, also the Play Store listing icon)
#
# The art runs to the edges and has small black rounded corners, so the corners are trimmed
# first. Android 8+ gets an adaptive icon whose foreground places the art over the visible 72 dp
# of the 108 dp layer, padded with the felt green (@color/launcher_green); Android 7 gets the
# whole art as a rounded-square icon and a circular round icon (tools/icon_mask.py). Uses only
# macOS `sips` and the Python standard library.
set -euo pipefail

cd "$(dirname "$0")/.."
MASTER=app/src/main/ic_launcher-playstore.png
RES=app/src/main/res
FELT=016104   # must match @color/launcher_green
TRIM=488      # 512 minus the black rounded corners (about 12 px each side)

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
sips -c "$TRIM" "$TRIM" "$MASTER" --out "$work/art.png" >/dev/null

for entry in mdpi:1 hdpi:1.5 xhdpi:2 xxhdpi:3 xxxhdpi:4; do
    density=${entry%%:*}
    scale=${entry##*:}
    dir="$RES/mipmap-$density"
    mkdir -p "$dir"
    px() { python3 -I -c "print(round($1 * $scale))"; }

    legacy=$(px 48)
    sips -z "$legacy" "$legacy" "$work/art.png" --out "$work/legacy.png" >/dev/null
    python3 -I tools/icon_mask.py "$work/legacy.png" "$dir/ic_launcher.png" rounded
    python3 -I tools/icon_mask.py "$work/legacy.png" "$dir/ic_launcher_round.png" circle

    inner=$(px 72)
    layer=$(px 108)
    sips -z "$inner" "$inner" "$work/art.png" --out "$work/inner.png" >/dev/null
    sips -p "$layer" "$layer" --padColor "$FELT" "$work/inner.png" \
        --out "$dir/ic_launcher_foreground.png" >/dev/null 2>&1
    echo "$density: legacy ${legacy}px, adaptive foreground ${layer}px (art ${inner}px)"
done
