# ArenaMW Mobile V13.7.10

Build fix for V13.7.9:

- Added the missing `buildscripts/patches/gl4es/openmw3-android-highp.cmake` copy.
- Root and `buildscripts/` NG-GL4ES patch chains are synchronized.
- Fixes GitHub Actions failure during NG-GL4ES `patch_disconnected` step:
  `CMake Error: Not a file: .../buildscripts/patches/gl4es/openmw3-android-highp.cmake`.
- No renderer/gameplay/settings behavior was changed relative to V13.7.9.
