# Дзен.Новости — standalone-виджет (без приложения)

Готовый standalone-проект: **только виджет** «Дзен.Новости», приложения
в лаунчере нет (в манифесте нет launcher-activity). Пакет `ru.zen.android`.

## Что умеет виджет (v18.1)
- **Тематики с горизонтальным скроллом**: 12 вкладок (Главное, Донецк,
  Свежее, Общество, СВО, Авто, Кино, Спорт, Наука, Технологии, Мир,
  Экономика) в `HorizontalScrollView` — полосу можно двигать влево/вправо.
- **Шапка: курсы ЦБ РФ + ключевая ставка**: `Обновлено HH:mm • USD xx.xx▲/▼
  • EUR xx.xx▲/▼ • КС xx.xx%▲/▼`. Ключевая ставка — с
  `https://cbr.ru/hd_base/KeyRate/` (первая строка HTML-таблицы), стрелки —
  сравнение с предыдущим значением из кэша. CNY убран.
- **Вкладка «Донецк» из реального Дзен**: `rubric?region=Donetsk`
  (региональная лента Дзен — регион пользователя в профиле Дзен), кэш
  отдельно под этим алиасом.
  Все рубрики берутся с `dzen.ru/news/api/v4/rubric`; при недоступности
  сети — демо-лента.
- **Кнопка «Обновить» (⟳) в шапке**: broadcast `WIDGET_REFRESH` →
  счётчик в `WidgetPrefs` → лента визуально перестраивается.
- **Заголовки в 2 строки без обрезки**: шрифт 8sp (уменьшен на 2 размера
  относительно 10sp в v16), `maxLines=2`.
- **Вертикальный скролл ленты**: `ListView` с `scrollbars="vertical"`,
  читается вся «простыня».
- **Оригинальный логотип Дзен** (цветная «искра» с градиентом) —
  иконка приложения и шапка виджета.
- Клик по шапке — dzen.ru в браузере; клик по новости — URL в браузере
  (приложения нет, переход через `ACTION_VIEW`).

## 1. Собрать APK

Gradle 8.9 (локально:
`C:\Users\HOME-PC\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat`):

```
gradle.bat assembleDebug
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

## 2. Подписать тем же ключом, что и предыдущие версии

```
apksigner.bat sign ^
  --ks ..\dzen-widget-v6.keystore ^
  --ks-pass pass:dzen12345 --ks-key-alias dzen --key-pass pass:dzen12345 ^
  --out ..\Дзен.Новости_виджет_v18.apk app-debug.apk
```

`versionCode = 309273555` (выше, чем у Дзен 26.10.2 = 309273550),
поэтому при совпадении подписи установка идёт как апгрейд.

## 3. Установить на телефон

```
adb install ..\Дзен.Новости_виджет_v18.apk
```

Если на телефоне стоит Дзен, подписанный **другим** ключом
(например, пере-подписанная VK Store-версия), сначала:
`adb shell pm uninstall ru.zen.android` — виджет придётся переставить.

## 4. Поставить виджет

На главном экране: **долгое нажатие по пустому месту → Виджеты →
«Дзен.Новости»** → перетащить на экран. Размер 4×4 (можно растягивать).

## Структура кода

```
app/src/main/java/ru/zen/android/widget/
├── DzenNewsWidgetProvider.kt   # шапка, «Обновить», тематики, список
├── DzenNewsWidgetService.kt    # RemoteViewsService
├── DzenNewsWidgetFactory.kt    # строки ленты + клик → браузер
├── NewsRepository.kt           # 12 тематик: реальный API Дзен + демо-лента
├── RatesRepository.kt          # курсы ЦБ РФ + ключевая ставка (cbr.ru)
├── NewsItem.kt                 # модель
└── WidgetPrefs.kt              # активная тематика + счётчик «Обновить» + курсы

app/src/main/res/
├── layout/dzen_news_widget.xml # шапка + HorizontalScrollView + ListView
├── layout/dzen_news_item.xml   # аватар + заголовок 8sp/2 строки + метка
├── drawable/dzen_logo.xml      # оригинальный логотип Дзен (искра)
├── drawable/dzen_logo_star.xml # та же искра без фона (превью)
├── drawable/dzen_avatar_*.xml  # 6 круглых аватаров
└── xml/dzen_news_widget_info.xml
```
