# ArenaMW Mobile V13.7.5

## Water presets

The Android graphics dialog keeps the stable `Water/shader mode=simple` path and now exposes three tiers:

- Simple / RTT 256 / refraction off
- Simple / RTT 256 / refraction on
- Simple / RTT 512 / refraction on

Automatic presets:

- Very Low: RTT 256, refraction off
- Performance: RTT 256, refraction off
- Balanced: RTT 256, refraction on
- Quality: RTT 512, refraction on
- Battery: RTT 256, refraction off

PBR water is still unavailable on Android.

## Narrow camera-angle shader artifact

Two stability fixes are applied together:

1. Sisah2/Openmw3 shader conversion is patched to keep translated OpenGL built-in matrices and fog parameters at high precision. The old ArenaMW highp workaround was accidentally lost when switching from the pinned older NG-GL4ES revision to Openmw3.
2. Simple-water reflection/refraction projective coordinates are clamped inside the RTT texture domain and the projective divisor is protected against a near-zero value. This prevents a tiny camera-angle window from sampling outside the RTT and briefly smearing/wrapping the image.

The NG-GL4ES cache patchset is bumped to `arenamw-openmw3-android-r21e-v4`, so CI rebuilds only NG-GL4ES for the precision change.
