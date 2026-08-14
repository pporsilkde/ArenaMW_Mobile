#!/usr/bin/env bash
set -euo pipefail

SRC="${1:?usage: apply-series.sh <ArenaMW-source-dir>}"
HERE="$(cd "$(dirname "$0")" && pwd)"

patches=(
  01-amw2-loading-screen.patch
  02-amw2-window-focus-mouse.patch
  03-amw2-android-context-loss.patch
  04-amw2-settings-save.patch
  05-amw2-stringops-compat.patch
  06-amw2-safe-render.patch
  07-amw2-water-gles.patch
  08-amw2-gles-shader-compat.patch
  09-amw2-gles-shadow-matrix.patch
  10-amw2-mobile-tuning.patch
  11-amw2-shadow-runtime-safe.patch
  12-amw2-android-disable-moc-ndkr21e.patch
  13-amw2-android-gamma.patch
)

if [[ ! -d "$SRC/.git" ]]; then
  echo "ArenaMW source is not a Git checkout: $SRC" >&2
  exit 2
fi

# ExternalProject can reuse the source tree from the incremental cache. Always
# return it to the AMW2 checkout first; the actual binary build directory stays
# intact, so only translation units touched by changed patches are recompiled.
git -C "$SRC" reset --hard HEAD >/dev/null
git -C "$SRC" clean -fd >/dev/null

for patch_name in "${patches[@]}"; do
  patch_file="$HERE/$patch_name"
  echo "==> AMW2 patch: $patch_name"
  git -C "$SRC" apply --check --whitespace=nowarn "$patch_file"
  git -C "$SRC" apply --whitespace=nowarn "$patch_file"
done

# Android entry point belongs to the mobile builder rather than the desktop AMW2
# repository, so keep it as one explicit builder-owned file.
cp "$HERE/android_main.cpp" "$SRC/apps/openmw/android_main.cpp"
