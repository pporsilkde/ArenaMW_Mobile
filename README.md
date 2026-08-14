# ArenaMW Android — Complex Water V3

Текущая тестовая ветка: сложная PBR-вода адаптирована для Android GLES/NG-GL4ES. См. `COMPLEX_WATER_V3_RU.md`.

# ArenaMW Android — original-builder port

This is a minimal single-player adaptation of the original working `Android_ArenaMP_NG` builder. The toolchain and dependency versions are intentionally kept close to the original instead of being modernized all at once.

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

The builder tracks `pporsilkde/AMW` `main`, matching GitHub Actions. The complete patch chain was revalidated against commit `731f005b609b37f204f974c36c57496ce5a66beb` from the supplied AMW(6) snapshot. V13.2 rebases the settings UI after upstream removed an old Effects block, physically removes the unsafe Effects/Advanced Android pages, fixes tab mapping, removes visible PBR-quality/SMAA-threshold controls, hard-disables native fog/god-rays/first-person depth bridging, enforces shadow-map 1024 and shadow-distance 8192 caps at runtime, adds a separate shadow-distance launcher control, preserves desktop-style portable `build.ini` metadata/load order, and reduces default streaming worker contention. See `AMW_ANDROID_V13_NOTES_RU.txt`.

Because the builder follows `main`, a future upstream change may intentionally make `git apply` fail. Rebase the affected patch rather than forcing or partially applying it.
