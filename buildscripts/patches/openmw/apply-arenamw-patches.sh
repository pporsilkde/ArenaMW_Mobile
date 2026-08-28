#!/bin/sh
# ArenaMW Android cumulative patch applicator.
# Robust/idempotent driver for clean, cached and partially patched ExternalProject trees.
set -eu

SRC=${1:-}
if [ -z "$SRC" ] || [ ! -d "$SRC/.git" ]; then
    echo "apply-arenamw-patches.sh: expected ArenaMW git source directory" >&2
    exit 2
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PATCHSET_ID="arenamw-android-v13.7.21-robust-01-27"
MARKER="$SRC/.arenamw_android_patchset"

copy_if_changed() {
    from=$1
    to=$2
    if ! cmp -s "$from" "$to" 2>/dev/null; then
        mkdir -p "$(dirname "$to")"
        cp "$from" "$to"
        echo "==> update $(basename "$to")"
    else
        echo "==> already current $(basename "$to")"
    fi
}

apply_legacy_patch() {
    p=$1
    name=$(basename "$p")
    if patch --dry-run -d "$SRC" -p1 -s < "$p" >/dev/null 2>&1; then
        echo "==> apply $name"
        patch -d "$SRC" -p1 -s < "$p"
        return 0
    fi
    if patch --dry-run -R -d "$SRC" -p1 -s < "$p" >/dev/null 2>&1; then
        echo "==> already present/upstream $name"
        return 0
    fi
    echo "ERROR: legacy patch neither applies nor is already present: $name" >&2
    patch --dry-run -d "$SRC" -p1 < "$p" >&2 || true
    return 22
}

apply_git_patch() {
    p=$1
    name=$(basename "$p")
    if git -C "$SRC" apply --check --whitespace=nowarn "$p" >/dev/null 2>&1; then
        echo "==> apply $name"
        git -C "$SRC" apply --whitespace=nowarn "$p"
        return 0
    fi
    if git -C "$SRC" apply --reverse --check --whitespace=nowarn "$p" >/dev/null 2>&1; then
        echo "==> already present/upstream $name"
        return 0
    fi
    if git -C "$SRC" apply --3way --check --whitespace=nowarn "$p" >/dev/null 2>&1; then
        echo "==> apply $name (git 3-way)"
        git -C "$SRC" apply --3way --whitespace=nowarn "$p"
        return 0
    fi
    echo "ERROR: git patch neither applies nor is already present: $name" >&2
    git -C "$SRC" apply --check --whitespace=nowarn "$p" >&2 || true
    return 23
}

finish_patchset() {
    copy_if_changed "$SCRIPT_DIR/android_main.cpp" "$SRC/apps/openmw/android_main.cpp"
    sh "$SCRIPT_DIR/patch-sse2neon.sh" "$SRC"
    printf '%s
' "$PATCHSET_ID" > "$MARKER"
    echo "==> ArenaMW Android patch set is present: $PATCHSET_ID"
}

if [ -f "$MARKER" ] && [ "$(cat "$MARKER" 2>/dev/null || true)" = "$PATCHSET_ID" ]; then
    finish_patchset
    echo "==> ArenaMW Android patch set already verified"
    exit 0
fi

# ExternalProject owns this checkout. If a previous CI run died in the middle of
# patching, automatically discard that partial state instead of aborting the next run.
if ! git -C "$SRC" diff --quiet --ignore-submodules -- ||    ! git -C "$SRC" diff --cached --quiet --ignore-submodules --; then
    echo "==> stale ArenaMW patch state detected; resetting source checkout"
    git -C "$SRC" reset --hard HEAD >/dev/null
    git -C "$SRC" clean -fdx >/dev/null
fi

cleanup_on_error() {
    status=$?
    if [ "$status" -ne 0 ]; then
        echo "ERROR: ArenaMW Android patch stage failed (status $status)." >&2
        echo "       Restoring clean source so a failed patch cannot poison the incremental cache." >&2
        git -C "$SRC" reset --hard HEAD >/dev/null 2>&1 || true
        git -C "$SRC" clean -fdx >/dev/null 2>&1 || true
    fi
    exit "$status"
}
trap cleanup_on_error EXIT INT TERM HUP

# Very old portability/UI fixes use classic patch format.
for n in   01-loadingscreen-disable-for-now.patch   02-windowmanagerimp-always-show-mouse-when-possible-pat.patch   03-android-fix-context-being-lost-on-app-minimize.patch   04-settingswindow-save-user-settings-file-when-ok-is-pr.patch   05-components-misc-stringops-use-boost-format-instead-o.patch
do
    apply_legacy_patch "$SCRIPT_DIR/$n"
done

# Engine Android chain. Every step accepts three safe states: apply now, already
# present/upstream, or a clean git three-way merge after harmless context drift.
for n in   06-android-safe-render-v1.patch   07-android-complex-water-v3.patch   08-android-gles-shader-compat-v4.patch   09-android-complex-water-gles-compat-v5.patch   10-android-complex-water-geometry-shadows-v6.patch   11-android-gles-shadow-matrix-v7.patch   12-android-mobile-tuning-v8.patch   16-android-mobile-settings-ui-v13-2.patch   17-android-clean-ui-v13-3.patch   18-android-world-distance-land-v13-4.patch   19-android-ui-safety-controls-v13-5.patch   20-android-stock-actor-collision-quickloot-use-v13-6.patch   21-android-water-fastpath-v13-7.patch
do
    apply_git_patch "$SCRIPT_DIR/$n"
done

# These two intentionally use classic patch: their settings/comment context can
# differ between ArenaMW revisions while the functional hunks remain compatible.
apply_legacy_patch "$SCRIPT_DIR/22-android-simple-water-shadow-distance-v13-7-4.patch"
apply_git_patch "$SCRIPT_DIR/23-android-water-angle-stability-v13-7-5.patch"
apply_legacy_patch "$SCRIPT_DIR/24-android-disable-removed-render-effects-v13-7-12.patch"
apply_git_patch "$SCRIPT_DIR/25-android-compact-display-v13-7-13.patch"
apply_git_patch "$SCRIPT_DIR/26-android-localmap-fog-pbo.patch"
apply_git_patch "$SCRIPT_DIR/27-amw-hud-cellstore-include.patch"

# 28-android-stable-shadows-v13-8.patch is intentionally NOT in the active
# chain. It was removed by the current builder revision after proving too brittle
# against ArenaMW/main. Keeping it in the directory is only for audit/history.
finish_patchset

trap - EXIT INT TERM HUP
exit 0
