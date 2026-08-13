# ArenaMW Android — Complex Water V3

Текущая тестовая ветка: сложная PBR-вода адаптирована для Android GLES/NG-GL4ES. См. `COMPLEX_WATER_V3_RU.md`.

# ArenaMW Android — original-builder port

This is a minimal single-player adaptation of the original working `Android_ArenaMP_NG` builder. The toolchain and dependency versions are intentionally kept close to the original instead of being modernized all at once.

## Changes from Android_ArenaMP_NG

- Engine source: `https://github.com/MrZer0x0/ArenaMW.git` (`main` by default).
- Native target: `openmw` -> `libopenmw.so`.
- RakNet ExternalProject removed completely.
- No TES3MP client/server/master/browser CMake targets or network libraries.
- No `libtes3mp.so` rename hack in `gradlew`.
- Launcher does not pass `--connect IP:port`; server IP/port preferences are removed.
- ArenaMW resources are packed directly.
- Original NDK r21e, AGP 4.0.2 and NG-GL4ES dependency stack retained for compatibility.
- `sse2neon` Clang guard is relaxed in the temporary ArenaMW source tree for NDK r21e / Clang 9.

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

## ArenaMW Android V12 stable profile

V12 removes the unstable HDR/Bloom/SSR/SMAA Android patch chain, fixes the mobile settings UI in both layout and C++/HPP logic, caps terrain detail at Medium, adds launcher shadow controls, preserves mod enable/order state, and uses the OpenMW launcher icon. See `STABLE_ANDROID_UI_MODS_V12_RU.md`.

## ArenaMW Android V13

V13 rebases the Android water patches onto ArenaMW NEW CUMULATIVE 002
(NPC animations/dialogue facing/water shader selector) and fixes launcher icons
for all Android product flavors. See `CUMULATIVE002_ADAPT_V13_RU.md`.

## V15 / Cumulative 004

V15 rebases the Android builder on NEW CUMULATIVE 004, removes aggressive GL4ES texture shrink from Battery Saver, keeps terrain composite/splat quality at -1/2048 for all launcher presets, removes all shadow controls/writes from the Android launcher, and moves shadow tuning back to the in-game settings only. The incremental ArenaMW cache now fingerprints the Android patchset so a changed patch can reuse the previous binary tree safely. See `C004_ANDROID_OPTIMIZED_V15_RU.md`.
