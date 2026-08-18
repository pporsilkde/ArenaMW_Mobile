# ArenaMW Mobile V13.7.20

Исправление сборки для актуального `AMW main` после FIX30.

## Что исправлено
- `22-android-simple-water-shadow-distance-v13-7-4.patch` больше не зависит от текста комментария
  `ArenaMW legacy water` / `ArenaMP legacy water` в `files/settings-default.cfg`.
- Функциональное поведение patch 22 сохранено:
  - Android UI предлагает только Off/Simple water;
  - старый `shader=true` мигрирует на `simple`;
  - `new` принудительно переводится в `simple` на мобильном профиле;
  - water RTT = 256, refraction = false, reflection detail = 2;
  - дальность теней берётся напрямую из настройки пользователя и ограничена 8192.
- Patch 22 теперь допускается как уже полностью присутствующий upstream.
- Сохранены patch 26 AndroidLocalMap и patch 27 HUD CellStore FIX30.

Причина ошибки в build 53: upstream изменил только текст рядом с `[Water]`, из-за чего строгий
`git apply --check` отклонял весь patch 22 до начала компиляции.
- Дополнительно отвязан patch 24 от комментария `Enhanced PBR ... ArenaMW/ArenaMP`, чтобы следующий
  шаг цепочки не падал после того же Update MW.
- Patchset marker: `arenamw-android-v13.7.20-01-27-upstream-robust-22-24`.
