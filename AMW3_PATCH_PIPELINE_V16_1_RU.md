> Историческая заметка V16.1. В V16.2 pinned SHA отменён и используется live `main`.

# AMW3 Patch Pipeline V16.1 — Full Git checkout fix

Исправление относится только к загрузке закреплённой базы AMW(3).

## Причина падения V16

V16 фиксирует `ARENAMW_GIT_TAG=46b24244ab486de0f7cd51d64cbcf9ae042e8e92`, но одновременно использовал `GIT_SHALLOW TRUE`. Если этот commit не является вершиной shallow-клона, ExternalProject клонирует только верхушку `main`, после чего `git checkout` не может прочитать дерево закреплённого SHA и падает с `fatal: unable to read tree`.

## Что изменено

- `GIT_SHALLOW FALSE` для ArenaMW ExternalProject.
- Новый cache epoch `arenamw-android-arm64-amw3-v16-1-fullgit`, поэтому повреждённый shallow checkout V16 не восстанавливается.
- Для pinned 40-char SHA cached-source updater использует обычный `git fetch --no-tags origin <sha>` без `--depth`.
- Вся AMW3 V16 engine patch-серия, MOC, тени 1024/8192, launcher presets, OpenMW icon и NG-GL4ES tuning оставлены без изменений.

После первого полного checkout последующие сборки снова используют incremental ArenaMW cache.
