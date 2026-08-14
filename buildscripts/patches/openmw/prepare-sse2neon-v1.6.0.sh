#!/usr/bin/env bash
set -euo pipefail

SRC="${1:?usage: prepare-sse2neon-v1.6.0.sh <ArenaMW-source-dir>}"
TARGET="$SRC/extern/maskedoc/sse2neon.h"
REPO="https://github.com/DLTcollab/sse2neon.git"
TAG="v1.6.0"

if [[ ! -d "$SRC/extern/maskedoc" ]]; then
  echo "MaskedOcclusionCulling directory is missing: $SRC/extern/maskedoc" >&2
  exit 2
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "==> MOC: fetching sse2neon $TAG for legacy Android NDK r21e"
git clone --quiet --depth 1 --single-branch --branch "$TAG" "$REPO" "$tmp/sse2neon"
actual="$(git -C "$tmp/sse2neon" rev-parse --short=12 HEAD)"
resolved_tag="$(git -C "$tmp/sse2neon" describe --tags --exact-match 2>/dev/null || true)"
if [[ "$resolved_tag" != "$TAG" ]]; then
  echo "Unexpected sse2neon checkout: tag=${resolved_tag:-<none>} commit=$actual (expected tag $TAG)" >&2
  exit 3
fi

compat="$tmp/sse2neon/sse2neon.h"
[[ -s "$compat" ]] || { echo "Downloaded sse2neon.h is empty" >&2; exit 4; }
grep -q '^#define SSE2NEON_H' "$compat" || { echo "Invalid sse2neon header" >&2; exit 5; }
grep -q 'SSE2NEON_PRECISE_MINMAX' "$compat" || { echo "sse2neon header lacks precise MINMAX configuration" >&2; exit 6; }
grep -q 'SSE2NEON_PRECISE_DIV' "$compat" || { echo "sse2neon header lacks precise DIV configuration" >&2; exit 7; }
grep -q '_mm_shuffle_epi8' "$compat" || { echo "sse2neon header lacks SSSE3 coverage required by MOC" >&2; exit 8; }
grep -q '_mm_dp_ps' "$compat" || { echo "sse2neon header lacks SSE4.1 coverage required by MOC" >&2; exit 9; }
if grep -q 'Clang versions earlier than 11 are not supported' "$compat"; then
  echo "Selected compatibility header unexpectedly rejects Clang < 11" >&2
  exit 10
fi

install -m 0644 "$compat" "$TARGET"
header_sha="$(sha256sum "$TARGET" | awk '{print $1}')"
echo "==> MOC: installed sse2neon $TAG ($actual), sha256=$header_sha; MaskedOcclusionCulling remains enabled"
