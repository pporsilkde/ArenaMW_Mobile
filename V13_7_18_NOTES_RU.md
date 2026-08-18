# ArenaMW Mobile V13.7.18 — Android Local Map fix

- Добавлен `26-android-localmap-fog-pbo.patch`.
- На Android отключён `osg::PixelBufferObject` только для динамической fog-of-war текстуры локальной карты.
- Это устраняет чёрный большой Local Map при рабочей HUD-миникарте на NG-GL4ES.
- Desktop/Windows поведение не меняется: PBO остаётся включённым вне `__ANDROID__`.
- Applicator обновлён до patchset `arenamw-android-v13.7.18-01-26-localmap-pbo`.
- Миграция старого incremental cache теперь обязательно применяет patch 26 перед записью нового marker, поэтому чистая пересборка не требуется только ради этого фикса.
- MP AOI/Player части из исходного `AndroidLocalMap` намеренно не перенесены в ArenaMW: они относятся только к ArenaMP/TES3MP.
