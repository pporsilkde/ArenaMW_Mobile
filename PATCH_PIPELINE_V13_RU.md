# ArenaMW Mobile V13 — patch-at-build pipeline

## Что изменено

ArenaMW/OpenMW больше не должен храниться в Android-builder уже изменённым.
`ExternalProject_Add(arenamw)` получает чистый репозиторий из `ARENAMW_REPOSITORY` / `ARENAMW_GIT_TAG`, после чего CMake применяет пронумерованную цепочку патчей из `patches/openmw`.

Активный хвост цепочки V13:

- `16-android-stable-settings-ui-v12.patch` — предыдущая стабильная Android UI база;
- `17-android-shadow-safe-caps-v13.patch` — безопасная горячая перенастройка теней, shadow map 256/512/1024, hard clamp 1024, distance hard clamp 8192, мобильные безопасные defaults;
- `18-android-ndkr21e-sse2neon.patch` — совместимость NDK r21e / Clang 9 только для Android ARM64. На других платформах исходная защита sse2neon Clang 11+ остаётся.

Старый `patch-sse2neon.sh` оставлен в дереве только для истории/совместимости, но CMake его больше не вызывает.

## Инкрементальная сборка

GitHub Actions cache теперь не меняет ключ при каждом изменении `.patch`.
Вместо этого workflow считает fingerprint `buildscripts/CMakeLists.txt + buildscripts/patches/openmw/*`.

Если upstream commit и fingerprint не изменились, cached source и object mtimes вообще не трогаются.

Если изменился только patch set:

1. cached `arenamw` откатывается `git reset --hard HEAD`;
2. удаляются только patch/configure/build/install/done stamps ExternalProject;
3. бинарная директория и уже собранные `.o` сохраняются;
4. CMake заново применяет numbered patches;
5. пересобираются только реально затронутые translation units.

Если изменился upstream commit, выполняется тот же reset, затем fetch/checkout нового commit и повторное применение патчей.

Для полного принудительного сброса остаётся `clean_arenamw_build=true` либо смена `ARENAMW_INCREMENTAL_CACHE_EPOCH`.

## Как добавлять следующий фикс

Новый engine fix добавлять отдельным файлом, например:

`buildscripts/patches/openmw/19-android-foo-fix-v14.patch`

и добавить одну строку `git -C <SOURCE_DIR> apply ...` после `18` в `buildscripts/CMakeLists.txt` (и зеркально в корневой `CMakeLists.txt` для локального builder path).

Не заменять исправленными `.cpp/.hpp/.glsl` исходники внутри cached ArenaMW и не коммитить вручную изменённый checkout ExternalProject.
