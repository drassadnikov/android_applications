package ru.zen.android.widget

/**
 * Модель карточки новости в виджете «Дзен.Новости».
 *
 * @param logoUrl адрес иконки источника (favicon.yandex.net/favicon/v2/<domain>);
 *                пусто — только буквенный аватар.
 */
data class NewsItem(
    val id: String,
    val title: String,
    val source: String,
    val time: String,
    val url: String,
    val logoUrl: String = "",
)
