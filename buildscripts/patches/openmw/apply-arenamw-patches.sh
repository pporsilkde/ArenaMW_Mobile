#!/bin/sh
# ArenaMW Android cumulative patch applicator.
#
# The engine patch chain contains overlapping cumulative diffs, so reverse-
# checking every individual patch is not a reliable way to detect an already
# patched tree.  Instead we use an untracked patch-set marker.  `git clean -fdx`
# removes the marker whenever CI refreshes the upstream checkout.
set -eu

SRC="${1:-}"
if [ -z "$SRC" ]; then
    echo "apply-arenamw-patches.sh: missing ArenaMW source directory" >&2
    exit 2
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PATCHSET_ID="arenamw-android-v13.8-01-28-stable-shadows"
MARKER="$SRC/.arenamw_android_patchset"

update_android_main() {
    if ! cmp -s "$SCRIPT_DIR/android_main.cpp" "$SRC/apps/openmw/android_main.cpp"; then
        cp "$SCRIPT_DIR/android_main.cpp" "$SRC/apps/openmw/android_main.cpp"
        echo "==> update android_main.cpp"
    else
        echo "==> already current android_main.cpp"
    fi
}

finish_patchset() {
    update_android_main
    # Keep MaskedOcclusionCulling enabled on the project's NDK r21e toolchain.
    sh "$SCRIPT_DIR/patch-sse2neon.sh" "$SRC"
    printf '%s\n' "$PATCHSET_ID" > "$MARKER"
    echo "==> ArenaMW Android patch set is present: $PATCHSET_ID"
}

if [ -f "$MARKER" ] && [ "$(cat "$MARKER" 2>/dev/null || true)" = "$PATCHSET_ID" ]; then
    # Cheap verification path for a restored incremental cache.  Neither helper
    # rewrites files when they are already current, preserving useful mtimes.
    update_android_main
    sh "$SCRIPT_DIR/patch-sse2neon.sh" "$SRC"
    echo "==> ArenaMW Android patch set already verified: $PATCHSET_ID"
    exit 0
fi

# Migration path for a cache created by V13.7.16 or older.  Patch 25 is the
# last engine diff in that chain, so if it can be cleanly reversed the cached
# tree already has the cumulative Android engine patches.  Adopt it and add the
# new marker instead of trying to reverse-check earlier overlapping patches.
if git -C "$SRC" apply --reverse --check --whitespace=nowarn \
    "$SCRIPT_DIR/25-android-compact-display-v13-7-13.patch" >/dev/null 2>&1; then
    echo "==> adopting previously patched ArenaMW cache"
    # V13.7.18 adds a new local-map-only patch after the old 01-25 chain.
    # Apply it during cache adoption as well, otherwise an incremental cache would
    # keep the Android fog-of-war PBO bug until a full clean rebuild.
    if git -C "$SRC" apply --check --whitespace=nowarn \
        "$SCRIPT_DIR/26-android-localmap-fog-pbo.patch" >/dev/null 2>&1; then
        echo "==> apply 26-android-localmap-fog-pbo.patch"
        git -C "$SRC" apply --whitespace=nowarn "$SCRIPT_DIR/26-android-localmap-fog-pbo.patch"
    elif git -C "$SRC" apply --reverse --check --whitespace=nowarn \
        "$SCRIPT_DIR/26-android-localmap-fog-pbo.patch" >/dev/null 2>&1; then
        echo "==> already present 26-android-localmap-fog-pbo.patch"
    else
        echo "ERROR: Android local-map patch cannot be applied to adopted ArenaMW cache" >&2
        exit 22
    fi
    if git -C "$SRC" apply --check --whitespace=nowarn \
        "$SCRIPT_DIR/27-amw-hud-cellstore-include.patch" >/dev/null 2>&1; then
        echo "==> apply 27-amw-hud-cellstore-include.patch"
        git -C "$SRC" apply --whitespace=nowarn "$SCRIPT_DIR/27-amw-hud-cellstore-include.patch"
    elif git -C "$SRC" apply --reverse --check --whitespace=nowarn \
        "$SCRIPT_DIR/27-amw-hud-cellstore-include.patch" >/dev/null 2>&1; then
        echo "==> already present 27-amw-hud-cellstore-include.patch"
    else
        echo "ERROR: ArenaMW HUD CellStore hotfix cannot be applied to adopted cache" >&2
        exit 22
    fi
    if git -C "$SRC" apply --check --whitespace=nowarn \
        "$SCRIPT_DIR/28-android-stable-shadows-v13-8.patch" >/dev/null 2>&1; then
        echo "==> apply 28-android-stable-shadows-v13-8.patch"
        git -C "$SRC" apply --whitespace=nowarn "$SCRIPT_DIR/28-android-stable-shadows-v13-8.patch"
    elif git -C "$SRC" apply --reverse --check --whitespace=nowarn \
        "$SCRIPT_DIR/28-android-stable-shadows-v13-8.patch" >/dev/null 2>&1; then
        echo "==> already present 28-android-stable-shadows-v13-8.patch"
    else
        echo "ERROR: Android stable-shadow patch cannot be applied to adopted cache" >&2
        exit 22
    fi
    finish_patchset
    exit 0
fi

# A fresh/update checkout must be clean before the cumulative chain is applied.
# If it is dirty here, the caller should reset it and retry rather than risk a
# half-patched source tree.
if ! git -C "$SRC" diff --quiet --ignore-submodules -- || \
   ! git -C "$SRC" diff --cached --quiet --ignore-submodules --; then
    echo "ERROR: ArenaMW source is dirty but has no valid Android patch-set marker" >&2
    echo "       Reset/clean the checkout and retry the patch applicator." >&2
    exit 19
fi

apply_patch_file() {
    p="$1"
    name=$(basename "$p")
    if ! patch --dry-run -d "$SRC" -p1 -s < "$p" >/dev/null 2>&1; then
        echo "ERROR: legacy patch does not apply to this ArenaMW revision: $name" >&2
        exit 20
    fi
    echo "==> apply $name"
    patch -d "$SRC" -p1 -s < "$p"
}

apply_git_patch() {
    p="$1"
    name=$(basename "$p")
    if ! git -C "$SRC" apply --check --whitespace=nowarn "$p" >/dev/null 2>&1; then
        echo "ERROR: git patch does not apply to this ArenaMW revision: $name" >&2
        exit 21
    fi
    echo "==> apply $name"
    git -C "$SRC" apply --whitespace=nowarn "$p"
}

apply_patch_file_allow_present() {
    p="$1"
    name=$(basename "$p")
    if patch --dry-run -d "$SRC" -p1 -s < "$p" >/dev/null 2>&1; then
        echo "==> apply $name"
        patch -d "$SRC" -p1 -s < "$p"
    elif patch --dry-run -R -d "$SRC" -p1 -s < "$p" >/dev/null 2>&1; then
        echo "==> already present upstream $name"
    else
        echo "ERROR: legacy patch neither applies nor is already present: $name" >&2
        exit 22
    fi
}

for n in \
  01-loadingscreen-disable-for-now.patch \
  02-windowmanagerimp-always-show-mouse-when-possible-pat.patch \
  03-android-fix-context-being-lost-on-app-minimize.patch \
  04-settingswindow-save-user-settings-file-when-ok-is-pr.patch \
  05-components-misc-stringops-use-boost-format-instead-o.patch
do
    apply_patch_file "$SCRIPT_DIR/$n"
done

apply_git_patch_allow_present() {
    p="$1"
    name=$(basename "$p")
    if git -C "$SRC" apply --check --whitespace=nowarn "$p" >/dev/null 2>&1; then
        echo "==> apply $name"
        git -C "$SRC" apply --whitespace=nowarn "$p"
    elif git -C "$SRC" apply --reverse --check --whitespace=nowarn "$p" >/dev/null 2>&1; then
        echo "==> already present upstream $name"
    else
        echo "ERROR: git patch neither applies nor is already present: $name" >&2
        exit 22
    fi
}

for n in \
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
  21-android-water-fastpath-v13-7.patch
do
    apply_git_patch "$SCRIPT_DIR/$n"
done

# Patch 22 only changes mobile water defaults/migration and the independent
# shadow-distance clamp. Keep its settings-default hunk free of branding/comment
# context, because ArenaMW main legitimately changes ArenaMW/ArenaMP wording.
# Also accept the whole functional change if it is later merged upstream.
apply_patch_file_allow_present "$SCRIPT_DIR/22-android-simple-water-shadow-distance-v13-7-4.patch"

apply_git_patch "$SCRIPT_DIR/23-android-water-angle-stability-v13-7-5.patch"
# Patch 24 also touches branding-adjacent settings comments that may change in
# ArenaMW main. Use the tolerant legacy patch path while preserving all code hunks.
apply_patch_file_allow_present "$SCRIPT_DIR/24-android-disable-removed-render-effects-v13-7-12.patch"
apply_git_patch "$SCRIPT_DIR/25-android-compact-display-v13-7-13.patch"

# This fix may also be committed directly to ArenaMW main. Accept both states so
# the mobile builder can track main without failing after the source-side merge.
apply_git_patch_allow_present "$SCRIPT_DIR/26-android-localmap-fog-pbo.patch"
apply_git_patch_allow_present "$SCRIPT_DIR/27-amw-hud-cellstore-include.patch"

# Mobile shadow quality: correct compare-then-filter PCF for the GLES sampling path plus
# rotation-invariant, texel-snapped cascades. Depends on patches 10 and 11.
apply_git_patch "$SCRIPT_DIR/28-android-stable-shadows-v13-8.patch"

finish_patchset
