# ArenaMW Android Y001s r1

Очищенный single-player Android-сборщик ArenaMW для ветки Y001s. По умолчанию используется `pporsilkde/AMW` `main`. Патчи применяются по содержимому и семантическим якорям, а не по номерам строк `@@ -N,+M`.

Patch-stage идемпотентен и рассчитан на изменения desktop `main`: перенос кода выше/ниже сам по себе не ломает patch. Неоднозначный или исчезнувший якорь вызывает остановку сборки для ручной перебазировки. Активирован Android Mali/Magic/VFX stability stage, ранее лежавший вне реальной цепочки.

Основная команда:

```bash
cd buildscripts
./build.sh --arch arm64 --ccache --release
```

Правила сопровождения патчей: `PATCHING.md`.
