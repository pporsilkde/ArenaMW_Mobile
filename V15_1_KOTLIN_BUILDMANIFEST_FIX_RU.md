# ArenaMW Mobile AMW2 V15.1 — BuildManifest Kotlin hotfix

Лог `logs_86164092475.zip` показывает, что native CMake target доходит до 100% и создаёт `libopenmw.so`.
Сборка APK останавливается позже на `:app:compileMainlineDebugKotlin` из-за двух обращений к отсутствующему `ModsCollection.applyOrderedSelection(...)`.

## Новый способ

`ModsCollection.kt` хранится без исправления. CI после checkout применяет:

`buildscripts/patches/android-builder/01-buildmanifest-ordered-selection.patch`

Шаг безопасен для повторного запуска:
- `git apply --check` -> применяет патч;
- если reverse-check проходит -> считает патч уже применённым;
- иначе завершает сборку до дорогостоящей native-компиляции.

## Логика метода

- case-insensitive сопоставление имён;
- точный порядок из `build.ini`;
- выбранные записи включаются;
- при `authoritative=true` остальные отключаются;
- весь список получает плотный `load_order`;
- существующий `update()` сохраняет изменения в SQLite.

Engine patch series `01..13`, GLES/PBR/water, shadow caps `1024/8192` и NDK r21e MOC path не изменяются.
