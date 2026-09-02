# ArenaMW Android Y001s r4 — main-safe single-player builder

## Y001s r4 — Help identity and gold icon pass

The launcher Help hub now identifies itself as **ArenaMW (fork OpenMW)** and its generic black/white file, performance, network and update symbols are replaced with transparent gold/orange beveled PNG icons that match the in-game on-screen control art. Existing gold control icons are kept unchanged.


Cleaned Android builder for the ArenaMW Y001s single-player line. It tracks `pporsilkde/AMW` `main` by default. Android source changes are applied by semantic/context anchors; unified-diff line numbers are not used to locate hunks.

The NDK r21e / NG-GL4ES compatibility stack is intentionally retained. The historical water/PostFX/mobile tuning work remains in the active patch chain where it is still Android-specific.

> **Y001s r1 dependency-anchor fix:** repeated MyGUI resource blocks now use explicit semantic anchors, and the anchor engine uses those anchors for both first application and already-applied/idempotence checks. The CI cache epoch was bumped so failed or partially patched dependency trees are not restored.

## Changes from Android_ArenaMP_NG

- Engine source: `https://github.com/pporsilkde/AMW.git` (`main` by default).
- Native target: `openmw` -> `libopenmw.so`.
- RakNet ExternalProject removed completely.
- No TES3MP client/server/master/browser CMake targets or network libraries.
- No `libtes3mp.so` rename hack in `gradlew`.
- Launcher does not pass `--connect IP:port`; server IP/port preferences are removed.
- ArenaMW resources are packed directly.
- Original NDK r21e, AGP 4.0.2 and NG-GL4ES dependency stack retained for compatibility.
- `sse2neon` Clang guard is relaxed in the temporary ArenaMW source tree for NDK r21e / Clang 9.

## Patch maintenance (Y001s)

`buildscripts/patches/anchor_patch.py` ignores unified-diff hunk coordinates when locating source changes. It matches real code context and supports explicit `ARENA_ANCHOR:` markers for locations that would otherwise be ambiguous. Already-applied hunks are accepted, so the chain can be rerun safely.

The current chain has been rebased against the ArenaMW Y001s desktop tree, including the X040 render-thread shutdown changes and the Y001s HUD/graphics layout. `28-android-magic-mali-stability.py` is now an active stage rather than an unused file.

See `PATCHING.md` for maintenance rules and tests.

## Native build

```bash
cd buildscripts
./build.sh --arch arm64 --ccache --release
```

## APK build

Use JDK 11 for Gradle. `MainlineDebug` keeps the native Release/O3 build but uses Android's normal debug signing, so the artifact can be installed immediately:

```bash
./gradlew assembleMainlineDebug
```

GitHub Actions workflow is included at `.github/workflows/android.yml`.

## Source override

```bash
ARENAMW_GIT_TAG=main buildscripts/build.sh --arch arm64 --ccache --release
```

`ARENAMW_REPOSITORY` can also be overridden.

## Android PostFX V9
HDR, Bloom, SSR и SMAA доступны как opt-in эффекты. Они выключены по умолчанию, но лаунчер больше не сбрасывает их на каждом запуске. См. `POSTFX_V9_RU.md`.


## ArenaMW Android V11

Последняя Android-правка: fail-safe FBO compositor для Bloom/SSR/SMAA/HDR и restart-safe переключение PBR quality. Подробности: `POSTFX_PBR_SAFE_V11_RU.md`.

## ArenaMW Android V13.2 / current AMW main

The builder tracks `pporsilkde/AMW` `main`, matching GitHub Actions. The complete patch chain was revalidated against commit `1f3e75652c63911823c5207a13d214249d17c256` from the supplied AMW(8) snapshot. V13.2 rebases the settings UI after upstream removed an old Effects block, physically removes the unsafe Effects/Advanced Android pages, fixes tab mapping, removes visible PBR-quality/SMAA-threshold controls, hard-disables native fog/god-rays/first-person depth bridging, enforces shadow-map 1024 and shadow-distance 8192 caps at runtime, adds a separate shadow-distance launcher control, preserves desktop-style portable `build.ini` metadata/load order, and reduces default streaming worker contention. See `AMW_ANDROID_V13_NOTES_RU.txt`.

Because the builder follows `main`, upstream drift is handled by the Y001s anchor patch engine. If a semantic/context anchor disappears or becomes ambiguous, the build stops and the affected patch must be rebased deliberately; line-number fuzzing is not used as a fallback.

## ArenaMW Android V13.3 notes
- In-game PBR, HDR and Bloom pages are removed on Android; SMAA is removed from Display.
- Launcher Graphics is placed directly below Mods.
- Official OpenMW application/start icon is used.
- Water mode is preset-aware: Very Low/Performance/Battery use `Water/shader mode = simple`; Balanced/Quality use `new` (PBR).

## ArenaMW Android V13.5 notes
- Restores stock OpenMW NPC collision-avoidance defaults (`NPCs avoid collisions=true`, `NPCs give way=true`) every launch so old Android prefs cannot leave actors overlapping.
- Hides the in-game Shadows section; launcher presets still apply shadows before renderer startup.
- Moves HUD FPS text to X=96 and moves the pause OSC button to the upper-left corner.
- Replaces the old Y/save-chat OSC action with the Wait icon mapped to `T`.
- Postprocess/PIC button sends `F11` only on a double tap; long hold still sends `F12`.


## V13.7.3 NG-GL4ES / NDK r21e compatibility

Sisah2/Openmw3 is built out-of-source. ArenaMW patches its pinned glslang diagnostic path formatting so Android NDK r21e does not require `std::filesystem::absolute()`. Shader compilation semantics are unchanged. See `V13_7_3_NOTES.md`.

## Y001s r2 — controls help and FPS limiter

- Built-in EN/RU controls guide now matches the actual Android OSC: F11 toggles HUD visibility, F12 takes a screenshot, Q toggles continuous autorun, and holding Take/Use (E) manipulates movable objects. ArenaMW-specific T wait and hold-Sneak = Z animation menu are documented separately.
- Graphics settings expose **By preset / 30 FPS / 60 FPS / Unlimited**. Very Low, Performance and Battery default to 30 FPS; Balanced and Quality default to 60 FPS. Manual FPS override does not force the visual preset to Custom.
