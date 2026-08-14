# ArenaMW Mobile — AMW3 Patch Pipeline V16

Исторически V16 был подготовлен на AMW(3); в V16.2 сборка следует live ветке `pporsilkde/AMW:main`.
Исходники движка и Android launcher не хранятся заранее пропатченными: GitHub Actions применяет patch-файлы во время сборки.

## Engine patch
`buildscripts/patches/openmw/01-amw3-android-mobile-v16.patch`

Содержит перенос Android/GLES, PBR-water, безопасных теней, MOC, gamma и mobile rendering на новую AMW3 базу. Дополнительно V16:
- удаляет из игрового Settings вкладки PBR, HDR, Bloom, Effects и Advanced;
- удаляет PBR Quality / MaterialQuality из вкладки Quality;
- удаляет SMAA и SMAA threshold из Display;
- не создаёт NativeEffects/Bloom framebuffer chain на Android;
- не делает first-person world-depth bridge на Android;
- сохраняет hard cap shadow map 1024 и shadow distance 8192.

## Android builder patch
`buildscripts/patches/android-builder/02-amw3-v16-launcher-ui-presets-nggl4es.patch`

Применяется после checkout Android builder и исправляет:
- стандартную OpenMW иконку и кнопку запуска;
- более читаемое оформление PreferenceCategory и Graphics & Performance;
- apply-once графические профили вместо перезаписи settings.cfg на каждом старте;
- сохранение выбранной в игре воды и теней между запусками;
- реальное включение MOC;
- streaming/preload настройки против заметных hitch при движении;
- безопасный NG-GL4ES cache/FBO/VBO/VAO setup и отключение подробного GL4ES log в release.

Профиль применяется только после Auto первого запуска или нажатия «Применить профиль». После этого настройки игры снова авторитетны до следующего явного применения профиля.
