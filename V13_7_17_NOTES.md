# V13.7.17 — Incremental patch reliability / MOC build fix

- Fixes the AMW native build failure in `extern/maskedoc/sse2neon.h` on NDK r21e/Clang 9.
- Keeps MaskedOcclusionCulling enabled; the existing narrow sse2neon compatibility shim is now guaranteed to run.
- Adds one idempotent `apply-arenamw-patches.sh` used by both ExternalProject and GitHub Actions.
- Fixes a more serious incremental-cache issue where a refreshed `AMW main` checkout could resume directly at configure and skip the Android patch command.
- Adds an untracked patch-set marker and migration path for older fully-patched caches.
- If a cached source tree is partial/dirty, CI resets only the source checkout and retries the cumulative patch application once.
- Bumps the ArenaMW incremental cache epoch to `v2` so the first V13.7.17 build starts from a trustworthy ArenaMW source/build cache state.
- Native dependency cache remains separate; this change does not intentionally disable MaskedOcclusionCulling or NG-GL4ES.
