# ArenaMW Mobile V13.7.19

- Сохранён Android LocalMap fix: PBO для fog-of-war отключён на Android/NG-GL4ES.
- Добавлен patch 27: явное подключение `mwworld/cellstore.hpp` в `mwgui/hud.cpp`.
- Исправляет Windows/MSVC ошибку `C2027: use of undefined type 'MWWorld::CellStore'` после переноса Update MW.
- Patch 27 допускает состояние, когда исправление уже внесено напрямую в ArenaMW main.
- Новый patchset marker: `arenamw-android-v13.7.19-01-27-hud-cellstore`.
