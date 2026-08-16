# V13.7.11 — AMW(8) revalidation + builder patch sync

- Upstream validation checkpoint updated to AMW(8), pporsilkde/AMW commit `1f3e75652c63911823c5207a13d214249d17c256`.
- Builder still tracks `pporsilkde/AMW` branch `main` by default.
- Restored missing active patch `23-android-water-angle-stability-v13-7-5.patch` into `buildscripts/patches/openmw/`.
- Verified the complete active OpenMW Android patch chain 01..23 against a clean AMW(8) checkout; all patches apply cleanly and `git diff --check` passes.
- AMW(8) upstream changes are primarily CharGen, AI/combat, dialogue/teleport and project icons/default settings; they do not currently conflict with the Android water/render/settings patches.
