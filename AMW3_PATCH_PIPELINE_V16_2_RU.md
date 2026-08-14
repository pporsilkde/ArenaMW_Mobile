# ArenaMW Mobile AMW3 Patch Pipeline V16.2 — live main

V16.2 больше не фиксирует конкретный commit AMW. База движка — `https://github.com/pporsilkde/AMW.git`, ветка `main`.

- `ARENAMW_GIT_TAG=main` в GitHub Actions, build.sh и CMake.
- `GIT_SHALLOW TRUE` снова включён, потому что используется ветка, а не commit SHA.
- На каждом CI-run workflow через `git ls-remote` получает актуальный HEAD `main`.
- Если HEAD не изменился, cached source/build сохраняют mtimes и инкрементальная сборка продолжается.
- Если HEAD изменился, cached source переключается на новый HEAD, build-time patch fingerprint обновляется, patch/configure/build stamps ArenaMW сбрасываются и серия патчей накладывается заново.
- Cache epoch: `arenamw-android-arm64-amw3-v16-2-main`, чтобы не поднимать full-git checkout V16.1.

Важно: поскольку база теперь живая, новый commit в `main` может однажды конфликтовать с патчами. В этом случае `git apply --check`/preconfigure patch-stage остановит CI до компиляции и покажет, какой patch нужно адаптировать.
