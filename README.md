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
