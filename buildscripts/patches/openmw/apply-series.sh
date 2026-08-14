#!/usr/bin/env bash
set -euo pipefail

SRC="${1:?usage: apply-series.sh <ArenaMW-source-dir>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
PATCH="$HERE/01-amw3-android-mobile-v16.patch"

if [[ ! -d "$SRC/.git" ]]; then
  echo "ArenaMW source is not a Git checkout: $SRC" >&2
  exit 2
fi

# ExternalProject may restore a previously patched source tree. Return it to the
# pinned AMW3 base before every configure; the binary tree is intentionally kept.
git -C "$SRC" reset --hard HEAD >/dev/null
git -C "$SRC" clean -fd >/dev/null

echo "==> AMW3 V16 patch: $(basename "$PATCH")"
git -C "$SRC" apply --check --whitespace=nowarn "$PATCH"
git -C "$SRC" apply --whitespace=nowarn "$PATCH"

# Keep real MaskedOcclusionCulling on NDK r21e. The current AMW3 checkout bundles
# a modern sse2neon that requires Clang 11+, so install the pinned compatible
# upstream header only in the build checkout.
"$HERE/prepare-sse2neon-v1.6.0.sh" "$SRC"
MOC_HEADER="$SRC/extern/maskedoc/sse2neon.h"
if grep -q 'Clang versions earlier than 11 are not supported' "$MOC_HEADER"; then
  echo 'MOC compatibility validation failed: modern Clang>=11 guard is still present' >&2
  exit 20
fi
echo "==> MOC compatibility header verified before ArenaMW configure: $(sha256sum "$MOC_HEADER" | awk '{print $1}')"

# Android entry point belongs to the mobile builder, not desktop ArenaMW.
cp "$HERE/android_main.cpp" "$SRC/apps/openmw/android_main.cpp"
