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
        logoFor(item.source)?.let {
            views.setImageViewResource(R.id.src_logo, it)
            logoShown = true
        }
        if (!logoShown) {
            loadFavicon(item.logoUrl)?.let {
                views.setImageViewBitmap(R.id.src_logo, it)
                logoShown = true
            }
        }
        if (logoShown) {
            views.setInt(R.id.news_initial, "setVisibility", View.GONE)
        } else {
            views.setTextViewText(R.id.news_initial, item.source.take(1).uppercase())
            views.setInt(R.id.news_initial, "setVisibility", View.VISIBLE)
        }
        views.setInt(
            R.id.avatar,
            "setBackgroundResource",
            AVATARS[(item.source.hashCode() and Int.MAX_VALUE) % AVATARS.size],
        )

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
