package ru.zen.android.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * Фабрика строк ListView ленты новостей.
 * Клик по строке — через setOnClickFillInIntent + EXTRA_ITEM_URI:
 * провайдер ставит шаблон ACTION_VIEW (setPackage на Дзен), фабрика
 * подставляет URL конкретной статьи.
 */
class DzenNewsWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<NewsItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val tab = WidgetPrefs.getActiveTab(context)
        val seed = WidgetPrefs.getRefreshSeed(context)
        items = NewsRepository.getNews(tab, seed)
        android.util.Log.w("DzenWidget", "factory: tab=$tab seed=$seed items=${items.size}")
    }

    override fun onDestroy() {}

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.dzen_news_item)
        val item = items.getOrNull(position)
        if (item == null) {
            views.setTextViewText(R.id.news_title, "")
            views.setTextViewText(R.id.news_meta, "")
            return views
        }
        views.setTextViewText(R.id.news_title, item.title)
        views.setTextViewText(R.id.news_meta, "${item.source} • ${item.time}")

        // Иконка источника: локальный drawable -> favicon из сети (кэш в файле) -> буква
        var logoShown = false
        // Приоритет — живой актуальный логотип из CDN Дзен (кэш-файл).
        loadFavicon(item.logoUrl)?.let {
            views.setImageViewBitmap(R.id.src_logo, it)
            logoShown = true
        }
        // Fallback: статичный PNG, если живой иконка ещё не подгрузилась.
        if (!logoShown) {
            logoFor(item.source)?.let {
                views.setImageViewResource(R.id.src_logo, it)
                logoShown = true
            }
        }
        if (logoShown) {
            views.setInt(R.id.news_initial, "setVisibility", View.GONE)
        } else {
            // Бренд-метка источника: «RT»/«В»/«МК» + фирменный цвет (как в Дзен).
            val style = styleFor(item.source)
            views.setTextViewText(
                R.id.news_initial,
                style?.label ?: item.source.take(1).uppercase(),
            )
            views.setInt(R.id.news_initial, "setVisibility", View.VISIBLE)
            views.setInt(
                R.id.avatar,
                "setBackgroundResource",
                style?.bgRes
                    ?: AVATARS[(item.source.hashCode() and Int.MAX_VALUE) % AVATARS.size],
            )
        }

        val fillIn = Intent().setData(Uri.parse(item.url))
        views.setOnClickFillInIntent(R.id.row_root, fillIn)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getCount(): Int = items.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    /** Иконка источника: ТОЛЬКО файл кэша (предзагружен NewsRepository фоном).
     *  Сети здесь нет — строка рисуется мгновенно; пока файла нет — буква. */
    private fun loadFavicon(url: String): android.graphics.Bitmap? {
        if (url.isEmpty()) return null
        return try {
            val file = NewsRepository.iconFileFor(context, url)
            if (file.exists() && file.length() >= 100) {
                decodeScaled(file.path)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Декод в малом размере (inSampleSize): иконка 22dp, не тянем 512px. */
    private fun decodeScaled(path: String): android.graphics.Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        val target = 88
        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= target &&
            bounds.outHeight / (sample * 2) >= target
        ) {
            sample *= 2
        }
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return android.graphics.BitmapFactory.decodeFile(path, opts)
    }

    /** Логотип источника: точное, затем без учёта регистра (API отдаёт «РИА НОВОСТИ»). */
    private fun logoFor(source: String): Int? {
        SOURCE_LOGOS[source]?.let { return it }
        val upper = source.trim().uppercase()
        for ((name, drawable) in SOURCE_LOGOS) {
            if (name.uppercase() == upper) return drawable
        }
        return null
    }
    /** Бренд-стиль источника для fallback (нет логотипа из API):
     *  метка 1-2 символа + фирменный цвет фона (как в приложении Дзен). */
    data class SourceStyle(val label: String, val bgRes: Int)

    private fun styleFor(source: String): SourceStyle? {
        SOURCE_STYLE[source]?.let { return it }
        val upper = source.trim().uppercase()
        for ((name, style) in SOURCE_STYLE) {
            if (name.uppercase() == upper) return style
        }
        return null
    }
    companion object {
        val SOURCE_LOGOS = mapOf(
            "РБК" to R.drawable.src_rbc,
            "ТАСС" to R.drawable.src_tass,
            "РИА Новости" to R.drawable.src_ria,
            "РЕН ТВ" to R.drawable.src_ren,
            "Известия" to R.drawable.src_iz,
            "Коммерсантъ" to R.drawable.src_kommersant,
            "Ведомости" to R.drawable.src_vedomosti,
            "Газета.Ru" to R.drawable.src_gazeta,
            "Интерфакс" to R.drawable.src_interfax,
            "Хабр" to R.drawable.src_habr,
            "iXBT" to R.drawable.src_ixbt,
            "Дром" to R.drawable.src_drom,
            "Авторевю" to R.drawable.src_autoreview,
            "Кинопоиск" to R.drawable.src_kinopoisk,
            "Иви" to R.drawable.src_ivi,
            "Афиша" to R.drawable.src_afisha,
            "Матч ТВ" to R.drawable.src_matchtv,
            "Москва 24" to R.drawable.src_m24,
            "Наука и жизнь" to R.drawable.src_nplus1,
            "Спорт-Экспресс" to R.drawable.src_sportex,
            "Чемпионат" to R.drawable.src_championat,
            "Профи.РФ" to R.drawable.src_profi,
            "3Dnews" to R.drawable.src_3dnews,
            "Cosmos" to R.drawable.src_cosmos,
            "Большая Волга" to R.drawable.src_bvolga,
        )

        /** Бренд-стили (метка + цвет) для источников без PNG-логотипа. */
        val SOURCE_STYLE = mapOf(
            "RT на русском" to SourceStyle("RT", R.drawable.src_bg_green),
            "Деловая газета \"Взгляд\"" to SourceStyle("В", R.drawable.src_bg_black),
            "Life.ru" to SourceStyle("L", R.drawable.src_bg_red),
            "Lenta.ru" to SourceStyle("Л", R.drawable.src_bg_red),
            "Комсомольская правда" to SourceStyle("КП", R.drawable.src_bg_red),
            "Комсомольская Правда - Спорт" to SourceStyle("КП", R.drawable.src_bg_red),
            "Комсомольская правда Чувашия" to SourceStyle("КП", R.drawable.src_bg_red),
            "Московский Комсомолец" to SourceStyle("МК", R.drawable.src_bg_red),
            "Московский Комсомолец Чувашия" to SourceStyle("МК", R.drawable.src_bg_red),
            "МК Донбасс" to SourceStyle("МК", R.drawable.src_bg_red),
            "КП - Донецк" to SourceStyle("КП", R.drawable.src_bg_red),
            "Российская газета" to SourceStyle("РГ", R.drawable.src_bg_darkred),
            "Царьград" to SourceStyle("Ц", R.drawable.src_bg_darkred),
            "\"Знамя труда Чувашия\"" to SourceStyle("З", R.drawable.src_bg_darkred),
            "7Дней.ру" to SourceStyle("7", R.drawable.src_bg_red),
            "Донецк Медиа" to SourceStyle("ДМ", R.drawable.src_bg_red),
            "Donbasstoday.ru" to SourceStyle("Д", R.drawable.src_bg_red),
            "Вести" to SourceStyle("В", R.drawable.src_bg_blue),
            "Pro Город" to SourceStyle("ПГ", R.drawable.src_bg_blue),
            "Pro Город Чебоксары" to SourceStyle("ПГ", R.drawable.src_bg_blue),
            "Донецкое агентство новостей" to SourceStyle("ДАН", R.drawable.src_bg_blue),
            "Regions.ru" to SourceStyle("R", R.drawable.src_bg_blue),
            "ИА Регнум" to SourceStyle("Р", R.drawable.src_bg_blue),
            "Агентство Москва" to SourceStyle("А", R.drawable.src_bg_blue),
            "РИАМО" to SourceStyle("РМ", R.drawable.src_bg_blue),
            "ПРАЙМ" to SourceStyle("П", R.drawable.src_bg_blue),
            "Finam.Ru" to SourceStyle("Ф", R.drawable.src_bg_blue),
            "За рулем" to SourceStyle("ЗР", R.drawable.src_bg_blue),
            "Автоновости дня" to SourceStyle("А", R.drawable.src_bg_blue),
            "Советский спорт" to SourceStyle("СС", R.drawable.src_bg_blue),
            "Правда ПФО" to SourceStyle("П", R.drawable.src_bg_blue),
            "Мой город.Онлайн" to SourceStyle("М", R.drawable.src_bg_blue),
            "BFM.ru" to SourceStyle("B", R.drawable.src_bg_blue),
            "Ньюс-НН" to SourceStyle("Н", R.drawable.src_bg_blue),
            "Ямал-Медиа" to SourceStyle("Я", R.drawable.src_bg_blue),
            "Официальный портал Мэра и Правительства Москвы" to SourceStyle("М", R.drawable.src_bg_blue),
            "Главный региональный" to SourceStyle("Г", R.drawable.src_bg_blue),
            "Спортс\"" to SourceStyle("С", R.drawable.src_bg_blue),
            "Чувашинформ" to SourceStyle("Ч", R.drawable.src_bg_blue),
            "NEWS.ru" to SourceStyle("N", R.drawable.src_bg_blue),
            "Ferra" to SourceStyle("F", R.drawable.src_bg_orange),
            "Муз-ТВ" to SourceStyle("М", R.drawable.src_bg_yellow),
            "StavkiPrognozy" to SourceStyle("С", R.drawable.src_bg_yellow),
            "Леди Mail.ru" to SourceStyle("L", R.drawable.src_bg_yellow),
            "Чебоксары Live" to SourceStyle("Ч", R.drawable.src_bg_yellow),
            "Naked Science" to SourceStyle("NS", R.drawable.src_bg_purple),
            "TechInsider" to SourceStyle("T", R.drawable.src_bg_purple),
            "Science XXI" to SourceStyle("S", R.drawable.src_bg_purple),
            "MiraNews" to SourceStyle("M", R.drawable.src_bg_purple),
        )
        val AVATARS = intArrayOf(
            R.drawable.dzen_avatar_blue,
            R.drawable.dzen_avatar_cyan,
            R.drawable.dzen_avatar_green,
            R.drawable.dzen_avatar_orange,
            R.drawable.dzen_avatar_purple,
            R.drawable.dzen_avatar_red,
        )
    }
}
