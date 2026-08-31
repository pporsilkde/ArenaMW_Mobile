#!/bin/sh
# ArenaMW Android cumulative patch applicator — anchor edition.
# All unified diffs are applied by code/context anchors; @@ line coordinates are ignored.
set -eu

SRC=${1:-}
if [ -z "$SRC" ] || [ ! -d "$SRC/.git" ]; then
    echo "apply-arenamw-patches.sh: expected ArenaMW git source directory" >&2
    exit 2
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ANCHOR_PATCH="$SCRIPT_DIR/../anchor_patch.py"
PATCHSET_ID="arenamw-android-y001-anchor-main-safe"
MARKER="$SRC/.arenamw_android_patchset"

copy_if_changed() {
    from=$1; to=$2
    if ! cmp -s "$from" "$to" 2>/dev/null; then
        mkdir -p "$(dirname "$to")"
        cp "$from" "$to"
        echo "==> update $(basename "$to")"
    else
        echo "==> already current $(basename "$to")"
    fi
}

apply_anchor_patch() {
    p=$1
    echo "==> anchor patch $(basename "$p")"
    python3 "$ANCHOR_PATCH" "$SRC" "$p"
}

verify_patchset() {
    test -f "$SRC/apps/openmw/android_main.cpp" || { echo "ERROR: Android entry point missing" >&2; return 31; }
    grep -q "android" "$SRC/apps/openmw/android_main.cpp" || true
    # Stable magic/VFX guards are intentionally part of Y001 Android.
    grep -q "Skipping free VFX" "$SRC/apps/openmw/mwrender/effectmanager.cpp" || { echo "ERROR: Mali/VFX stability guard missing" >&2; return 32; }
    echo "==> ArenaMW Android Y001 feature patchset verified"
}

finish_patchset() {
    copy_if_changed "$SCRIPT_DIR/android_main.cpp" "$SRC/apps/openmw/android_main.cpp"
    sh "$SCRIPT_DIR/patch-sse2neon.sh" "$SRC"
    verify_patchset
    printf '%s\n' "$PATCHSET_ID" > "$MARKER"
    echo "==> ArenaMW Android patch set is present: $PATCHSET_ID"
}

if [ -f "$MARKER" ] && [ "$(cat "$MARKER" 2>/dev/null || true)" = "$PATCHSET_ID" ]; then
    finish_patchset
    echo "==> ArenaMW Android patch set already verified"
    exit 0
fi

if ! git -C "$SRC" diff --quiet --ignore-submodules -- || ! git -C "$SRC" diff --cached --quiet --ignore-submodules --; then
    echo "==> stale ArenaMW patch state detected; resetting source checkout"
    git -C "$SRC" reset --hard HEAD >/dev/null
    git -C "$SRC" clean -fdx >/dev/null
fi

cleanup_on_error() {
    status=$?
    if [ "$status" -ne 0 ]; then
        echo "ERROR: ArenaMW Android anchor patch stage failed (status $status)." >&2
        echo "       Restoring clean checkout; ambiguous anchors are never forced." >&2
        git -C "$SRC" reset --hard HEAD >/dev/null 2>&1 || true
        git -C "$SRC" clean -fdx >/dev/null 2>&1 || true
    fi
    exit "$status"
}
trap cleanup_on_error EXIT INT TERM HUP

# Active cumulative chain only. Historical superseded patches were removed from
# the clean repository so there is one source of truth for the Android port.
for n in \
  01-loadingscreen-disable-for-now.patch \
  02-windowmanagerimp-always-show-mouse-when-possible-pat.patch \
  03-android-fix-context-being-lost-on-app-minimize.patch \
  04-settingswindow-save-user-settings-file-when-ok-is-pr.patch \
  05-components-misc-stringops-use-boost-format-instead-o.patch \
  06-android-safe-render-v1.patch \
  07-android-complex-water-v3.patch \
  08-android-gles-shader-compat-v4.patch \
  09-android-complex-water-gles-compat-v5.patch \
  10-android-complex-water-geometry-shadows-v6.patch \
  11-android-gles-shadow-matrix-v7.patch \
  12-android-mobile-tuning-v8.patch \
  16-android-mobile-settings-ui-v13-2.patch \
  17-android-clean-ui-v13-3.patch \
  18-android-world-distance-land-v13-4.patch \
  19-android-ui-safety-controls-v13-5.patch \
  20-android-stock-actor-collision-quickloot-use-v13-6.patch \
  21-android-water-fastpath-v13-7.patch \
  22-android-simple-water-shadow-distance-v13-7-4.patch \
  23-android-water-angle-stability-v13-7-5.patch \
  24-android-disable-removed-render-effects-v13-7-12.patch \
  25-android-compact-display-v13-7-13.patch \
  26-android-localmap-fog-pbo.patch \
  27-amw-hud-cellstore-include.patch
do
    apply_anchor_patch "$SCRIPT_DIR/$n"
done

# Semantic Python patch: exact named functions/code anchors, no line coordinates.
python3 "$SCRIPT_DIR/28-android-magic-mali-stability.py" "$SRC"

finish_patchset
trap - EXIT INT TERM HUP
exit 0
