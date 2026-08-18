ArenaMW Mobile V13.7.18 — Android Local Map
Основа: присланный ArenaMW_Mobile(4).zip.

Из AndroidLocalMap перенесён только single-player фикс localmap.cpp.
MP AOI/Player/Processor изменения не используются в ArenaMW.

Новый patchset: arenamw-android-v13.7.18-01-26-localmap-pbo
Новый patch: 26-android-localmap-fog-pbo.patch

Applicator:
- применяет patch 26 после 01-25;
- применяет patch 26 и при миграции старого incremental cache;
- допускает, что тот же фикс уже закоммичен в AMW main, и тогда пишет already present upstream вместо ошибки.

Проверено:
- чистая цепочка 01-26: PASS;
- adoption старого cache 01-25: PASS;
- AMW upstream уже содержит localmap fix: PASS.
