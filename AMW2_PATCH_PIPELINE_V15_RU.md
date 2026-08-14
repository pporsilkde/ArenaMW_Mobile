# ArenaMW Mobile — AMW2-native Patch Pipeline V15

Эта версия адаптирована под исходники из `AMW(2).zip`. Старые патчи Android-порта не переносятся механически по номерам строк: серия пересобрана заново относительно новой архитектуры AMW2.

## Почему понадобилась новая серия

Новая база AMW2 уже содержит переработанные PBR/complex-water, графические профили и изменённый shadow/runtime-код. Старые water/postfx/shadow патчи пересекались с этой реализацией и падали уже на стадии `git apply`, в частности на `apps/openmw/mwrender/water.cpp`.

V15 использует чистый checkout AMW2 и накладывает только актуальную Android-разницу во время `ExternalProject_Add(... PATCH_COMMAND ...)`.

## Активная AMW2-native серия

1. `01-amw2-loading-screen.patch` — Android loading-screen compatibility.
2. `02-amw2-window-focus-mouse.patch` — focus/mouse fixes.
3. `03-amw2-android-context-loss.patch` — безопасное восстановление Android GL context/logging.
4. `04-amw2-settings-save.patch` — сохранение runtime-настроек.
5. `05-amw2-stringops-compat.patch` — совместимость старого Android toolchain.
6. `06-amw2-safe-render.patch` — безопасный Android render path.
7. `07-amw2-water-gles.patch` — сохраняет новую PBR-water AMW2, добавляя отдельный GLES-safe Android shader path.
8. `08-amw2-gles-shader-compat.patch` — GLSL/GLES compatibility.
9. `09-amw2-gles-shadow-matrix.patch` — Android/GLES shadow matrix path.
10. `10-amw2-mobile-tuning.patch` — мобильные defaults/UI tuning.
11. `11-amw2-shadow-runtime-safe.patch` — безопасное runtime-переключение теней и hard caps.
12. `12-amw2-android-disable-moc-ndkr21e.patch` — на Android/NDK r21e не компилирует Masked Software Occlusion Culling; desktop AMW2 остаётся штатным.
13. `13-amw2-android-gamma.patch` — AMW2-native shader gamma: безопасно читает/ограничивает `OPENMW_GAMMA` и применяет его к основным scene/water/PBR fragment shaders без старого fixed-function хака.

`patches/openmw/apply-series.sh` всегда делает `git reset --hard HEAD` + `git clean -fd`, затем сначала `git apply --check`, потом применяет каждый патч. Поэтому кэшированный source tree не накапливает повторно наложенные изменения.

## Тени

Ограничения дублируются на нескольких уровнях:

- игровое меню: shadow map только 256 / 512 / 1024;
- renderer/runtime: shadow map clamp 256..1024;
- renderer/runtime: maximum shadow map distance clamp 0..8192;
- Android launcher: профиль также делает `coerceIn(256, 1024)` и `coerceIn(0, 8192)`;
- изменение размера/cascade shadow maps сбрасывает зависимые per-view texture/FBO ресурсы;
- изменение distance/fade не требует лишней полной пересборки FBO.

Это защищает и от старого `settings.cfg`, где могли остаться 2048/4096/8192 для размера карты.

## Masked Occlusion Culling и NDK r21e

V15 **не патчит `extern/maskedoc/sse2neon.h`** и не снимает его compiler guard.

Для Android создаётся интерфейсная цель `maskedoc`, а `SceneUtil::OcclusionCuller` получает консервативную no-op реализацию: `testVisibleAABB()` возвращает `true`. В Android `settings.cfg` дополнительно записывается `Camera / occlusion culling = false`.

Это убирает ошибку Clang 9/NDK r21e без исполнения SIMD-кода, рассчитанного на более новый Clang. На desktop стандартный MOC AMW2 не меняется.

## Android Graphics & Performance

Desktop Qt launcher в Android-сборке отключён (`BUILD_LAUNCHER=OFF`), поэтому графический интерфейс реализован в реальном Kotlin launcher:

- кнопка **Graphics & Performance** расположена непосредственно под **Mods**;
- master-профиль: Auto / Low / Medium / High / Ultra / Custom;
- Auto один раз выбирает консервативный профиль по RAM/CPU/device hardware;
- отдельные уровни: OSG, distance/preload, terrain, shaders/PBR, lighting, shadows, grass;
- ручная правка отдельной категории переводит master в Custom;
- OSG worker/thread параметры выставляются до загрузки `libopenmw.so`;
- профиль также записывается в `settings.cfg` перед запуском.

В текущем Android-safe V15 native HDR/Bloom/SSR/SMAA принудительно не включаются из лаунчера: старый framebuffer-copy postfx путь не переносится вслепую на новую AMW2 базу. Их следует портировать отдельно после стабильной GLES-сборки.

## Инкрементальная сборка

GitHub Actions использует стабильный epoch `arenamw-android-arm64-amw2-v15` и отдельный fingerprint файлов из `buildscripts/patches/openmw`.
Ключ кэша тяжёлых сторонних native-зависимостей считает только их собственные patch-наборы и **не включает `buildscripts/patches/openmw/**`**, поэтому добавление следующего engine-патча не должно заново собирать OSG/Bullet/NG-GL4ES.

Если меняется патч:

1. cached AMW source возвращается к чистому `HEAD`;
2. ExternalProject patch/configure/build/install/done stamps инвалидируются;
3. бинарный CMake build tree и ccache сохраняются;
4. серия применяется заново;
5. пересобираются прежде всего translation units, затронутые изменившимися патчами.

## Проверки V15

Выполнено на точной распаковке `AMW(2).zip`:

- последовательный `git apply --check` + apply всей серии 01..13;
- SHA-256 `extern/maskedoc/sse2neon.h` до/после серии совпадает;
- CMake smoke-test при `ANDROID=TRUE`: `maskedoc` имеет тип `INTERFACE_LIBRARY`;
- подтверждены hard caps 1024 / 8192 после применения патчей;
- оба active patch directories (`patches/openmw` и `buildscripts/patches/openmw`) идентичны;
- `bash -n` для `apply-series.sh`;
- все Android resource XML успешно разбираются;
- GitHub Actions YAML успешно разбирается;
- проверены новые Kotlin resource references и отсутствие старых `pref_graphics_preset`/`GraphicsPresets.resolve` в активном коде.

Полную Gradle-компиляцию в локальном контейнере выполнить нельзя: Gradle wrapper 6.1.1 отсутствует в локальном кеше и пытается получить дистрибутив из `services.gradle.org`, а у контейнера нет сетевого доступа. Поэтому окончательная компиляционная проверка Kotlin/NDK должна пройти в GitHub Actions.
