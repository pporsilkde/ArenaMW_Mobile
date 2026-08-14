# ArenaMW Mobile AMW2 V15.3 — MOC pre-configure fix

## Что исправлено

Лог `logs_86170293760` показал, что после восстановления incremental cache CMake переходил сразу к `arenamw: configure`, не выполняя ArenaMW `PATCH_COMMAND`. В результате `prepare-sse2neon-v1.6.0.sh` не запускался, и MOC компилировался с новым bundled `sse2neon.h`, который останавливает NDK r21e / Clang 9.

Причина дополнительно подтверждена устройством `ExternalProject` при `UPDATE_DISCONNECTED TRUE`: patch stamp имеет имя `arenamw-patch_disconnected`, тогда как старый workflow инвалидировал `arenamw-patch`.

## Новая схема V15.3

- MOC **не отключён**.
- `extern/maskedoc` остаётся обычной `STATIC` библиотекой.
- `12-amw2-android-moc-ndkr21e.patch` по-прежнему включает `SSE2NEON_PRECISE_MINMAX=1` и `SSE2NEON_PRECISE_DIV=1` на Android ARM64.
- `PATCH_COMMAND` для ArenaMW теперь пустой и больше не считается надёжной точкой применения серии.
- `apply-series.sh <SOURCE_DIR>` выполняется первым `CONFIGURE_COMMAND`, то есть гарантированно перед `cmake <SOURCE_DIR>`.
- `apply-series.sh` сбрасывает cached checkout к `HEAD`, накладывает `01..13`, ставит `sse2neon v1.6.0`, проверяет отсутствие Clang>=11 guard и только после этого запускается конфигурация ArenaMW.
- workflow инвалидирует как `arenamw-patch`, так и `arenamw-patch_disconnected`.
- patch fingerprint записывается **только после успешной сборки `libopenmw.so`** и повторной проверки фактически установленного MOC header.

## Почему это не ломает incremental build

При неизменной AMW-базе и неизменном fingerprint configure stamp остаётся в кэше, поэтому pre-configure patching не запускается лишний раз и mtimes исходников сохраняются. При изменении AMW или patch series configure stamp удаляется, серия накладывается заново, но `arenamw-build` binary directory и сторонние зависимости остаются в кэше.

## Сохранённые ограничения

- Shadow map: максимум 1024.
- Shadow distance: максимум 8192.
- MOC: включён.
- NDK: r21e.
- Чистое AMW-дерево в builder-репозитории: да; engine-изменения накладываются во время сборки.
