# V15.2 — MOC включён на Android/NDK r21e

V15.2 отменяет прежний Android no-op путь для MaskedOcclusionCulling.

## Что происходит при сборке

1. ArenaMW клонируется чистым ExternalProject.
2. Применяются AMW2-native patches 01–13.
3. Патч 12 больше НЕ отключает `maskedoc` и НЕ заменяет SceneUtil culler заглушкой.
4. `prepare-sse2neon-v1.6.0.sh` клонирует upstream `DLTcollab/sse2neon` tag `v1.6.0`, проверяет commit `31cb30b` и только внутри build checkout заменяет `extern/maskedoc/sse2neon.h`.
5. `MaskedOcclusionCulling.cpp` собирается как обычная STATIC library и подключается к SceneUtil.
6. Для ARM64 Android включены `SSE2NEON_PRECISE_MINMAX=1` и `SSE2NEON_PRECISE_DIV=1` — приоритет корректности occlusion-решений над небольшой ценой вычислений.

## Что НЕ меняется

- NDK остаётся r21e.
- Остальные native dependencies не переводятся на новый NDK.
- Desktop MOC не меняется.
- Исходники AMW в репозитории builder не хранятся пропатченными.
- Лимиты теней остаются: shadow map <= 1024, shadow distance <= 8192.

## Почему не снимается #error у нового sse2neon

Текущий upstream sse2neon официально требует Clang 11+ и содержит защиту для более старых компиляторов. V15.2 не удаляет эту защиту из новой версии; вместо этого для legacy NDK берётся старый фиксированный upstream release, который предназначен для старого поколения toolchain и покрывает нужные MOC SSE/SSE4.x intrinsics.
