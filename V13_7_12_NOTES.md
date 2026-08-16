# V13.7.12 — Android render-safe cleanup

- AMW(8) checkpoint verified: existing patches 01–23 still apply.
- Added engine patch 24.
- Android no longer constructs BloomProcessor or NativeEffectsProcessor at startup.
- HDR shader define is hard-disabled on Android.
- Enhanced PBR lighting uniform is hard-disabled on Android.
- F3/F4 HDR/Bloom hotkeys are disabled on Android.
- SMAA/Bloom/fog/god rays/sharpening/dithering are hard-disabled in NativeEffectsProcessor as a second safety barrier.
- settings-default.cfg now defaults enhanced PBR, sharpening and dithering to false.
- Launcher presets explicitly write sharpening=false and dithering=false so stale settings.cfg cannot reactivate them.
