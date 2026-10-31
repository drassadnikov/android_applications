package ru.zen.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Провайдер виджета «Дзен.Новости».
 * Отрисовка: шапка (логотип + «Обновить»), строка статуса
 * (время последнего обновления + курсы ЦБ со цветными стрелками:
 *  подорожала — красный ▲, подешевела — зелёный ▼),
 * полоса рубрик с навигацией ‹ › (4 рубрики на страницу),
 * ListView ленты новостей с вертикальным скроллингом.
 *
 * Рубрики — реальные тематические каналы приложения Дзен (topic-channel).
 * Клик по строке ленты — ACTION_VIEW с URL статьи: в Дзен (setPackage),
 * если он установлен, иначе в браузере.
 */
class DzenNewsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        touchUpdate(context, force = false)
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    /** Клик по рубрике / «Обновить» / стрелкам навигации: запоминаем и пересобираем. */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        NewsRepository.bindContext(context)
        when (intent.action) {
            ACTION_TAB_CLICK -> {
                val tab = intent.getIntExtra(EXTRA_TAB, 0)
                Log.w(TAG, "tab click: $tab")
                WidgetPrefs.setActiveTab(context, tab)
                WidgetPrefs.setFeedPage(context, 0)
                touchUpdate(context, force = false)
                refreshAll(context)
            }
            ACTION_REFRESH -> {
                Log.w(TAG, "refresh click")
                WidgetPrefs.bumpRefreshSeed(context)
                touchUpdate(context, force = true)
                refreshAll(context)
            }
            ACTION_EXTERNAL_REFRESH -> {
                Log.w(TAG, "external refresh")
                touchUpdate(context, force = true)
                refreshAll(context)
            }
            ACTION_TOPIC_PAGE -> {
                val delta = intent.getIntExtra(EXTRA_DELTA, 0)
                val page = (WidgetPrefs.getTopicPage(context) + delta)
                    .coerceIn(0, TOPIC_PAGE_COUNT - 1)
                Log.w(TAG, "topic page: $page")
                WidgetPrefs.setTopicPage(context, page)
                refreshAll(context)
            }
        }
    }

    /** Пометка времени + фоновая подгрузка.
     *  [force]=false: свежие кэши (10 мин) не трогают сеть. */
    private fun touchUpdate(context: Context, force: Boolean) {
        val now = System.currentTimeMillis()
        WidgetPrefs.setLastUpdateMillis(context, now)
        val appCtx = context.applicationContext
        NewsRepository.bindContext(appCtx)
        val tab = WidgetPrefs.getActiveTab(appCtx)
        NewsRepository.fetchTabAsync(appCtx, tab, force) {
            refreshAllWidgets(appCtx)
        }
        RatesRepository.fetchAndCacheAsync(appCtx, force) {
            refreshAllWidgets(appCtx)
        }
    }

    /** Пометка времени + фоновая подгрузка активной рубрики и курсов ЦБ. */

    private fun refreshAll(context: Context) {
        refreshAllWidgets(context)
    }

    /** Сборка одного экземпляра виджета. */
    fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val views = RemoteViews(context.packageName, R.layout.dzen_news_widget)
        Log.w(TAG, "updateWidget id=$appWidgetId")

        // Строка статуса: «Обновлено 15:30 • USD 90.12▲ • EUR 98.34▼ • КС 14.00%»
        views.setTextViewText(R.id.info_line, buildInfoLine(context))

        // Шапка — открыть приложение Дзен (если установлено), иначе dzen.ru в браузере
        views.setOnClickPendingIntent(
            R.id.widget_header,
            dzenOrBrowserIntent(context, REQUEST_HEADER),
        )

        // Кнопка «Обновить» — broadcast WIDGET_REFRESH
        views.setOnClickPendingIntent(
            R.id.btn_refresh,
            PendingIntent.getBroadcast(
                context,
                REQUEST_REFRESH,
                Intent(context, DzenNewsWidgetProvider::class.java)
                    .setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        // Полоса рубрик: 4 рубрики на страницу + кнопки ‹ ›
        applyTopicPage(context, views)

        // Лента: ListView + RemoteViewsService + шаблон клика (URL статьи -> Дзен/браузер)
        val serviceIntent = Intent(context, DzenNewsWidgetService::class.java)
        // Сервис ленты: setRemoteAdapter на каждом обновлении — именно он
        // заставляет ListView перечитать адаптер (смена рубрики работает).
        views.setRemoteAdapter(R.id.news_list, serviceIntent)

        // Кликовый шаблон строк: ACTION_VIEW + URL статьи (fillIn из фабрики)
        val clickTemplate = Intent(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply {
                if (DzenInfo.isInstalled(context)) {
                    setPackage(DzenInfo.PACKAGE)
                }
            }
        var templateFlags = PendingIntent.FLAG_UPDATE_CURRENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            templateFlags = templateFlags or PendingIntent.FLAG_MUTABLE
        }
        val template = PendingIntent.getActivity(
            context,
            REQUEST_NEWS_ROW,
            clickTemplate,
            templateFlags,
        )
        views.setPendingIntentTemplate(R.id.news_list, template)
        // setEmptyView не ставим: он скрывает info_line, когда список заполнен

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /** «Обновлено 15:30 • USD 90.12▲ • EUR 98.34▼ • КС 14.00%».
     *  Цвет стрелок: рост — красный, падение — зелёный. */
    private fun buildInfoLine(context: Context): CharSequence {
        val last = WidgetPrefs.getLastUpdateMillis(context)
        val time = if (last > 0) {
            TIME_FORMAT.format(Date(last))
        } else {
            TIME_FORMAT.format(Date())
        }
        val sb = SpannableStringBuilder("Обновлено $time")
        val rates = WidgetPrefs.getRates(context)
        if (rates != null) {
            val dirs = WidgetPrefs.getRateDirs(context)
            appendRate(sb, "USD", rates.first, dirs.first)
            appendRate(sb, "EUR", rates.second, dirs.second)
        }
        val kr = WidgetPrefs.getRateKr(context)
        if (kr != null) {
            appendRate(sb, "КС", kr.first, WidgetPrefs.getRateKrDir(context), suffix = "%")
        }
        return sb
    }

    /** Курс/ставка + цветная стрелка: ▲ красный (рост), ▼ зелёный (падение). */
    private fun appendRate(
        sb: SpannableStringBuilder,
        code: String,
        value: Float,
        dir: Int,
        suffix: String = "",
    ) {
        val start = sb.length
        sb.append("  •  $code ${fmt(value)}$suffix")
        if (dir != 0) {
            sb.append(if (dir == 1) "▲" else "▼")
            val color = if (dir == 1) 0xFFE53935.toInt() else 0xFF4CAF50.toInt()
            sb.setSpan(
                ForegroundColorSpan(color),
                start,
                sb.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun fmt(v: Float): String =
        String.format(Locale.US, "%.2f", v)

    /** Полоса рубрик: 4 рубрики текущей страницы + кнопки ‹ ›. */
    private fun applyTopicPage(context: Context, views: RemoteViews) {
        val page = WidgetPrefs.getTopicPage(context).coerceIn(0, TOPIC_PAGE_COUNT - 1)
        val activeTab = WidgetPrefs.getActiveTab(context)
        val slotIds = intArrayOf(
            R.id.topic_a, R.id.topic_b, R.id.topic_c, R.id.topic_d,
        )
        slotIds.forEachIndexed { slot, viewId ->
            val tabIndex = page * TOPICS_PER_PAGE + slot
            if (tabIndex < NewsRepository.TOPICS.size) {
                views.setTextViewText(viewId, NewsRepository.TOPICS[tabIndex].name)
                views.setTextColor(
                    viewId,
                    if (tabIndex == activeTab) 0xFFFFFFFF.toInt() else 0xFFAAAAAA.toInt(),
                )
                views.setOnClickPendingIntent(viewId, tabIntent(context, tabIndex))
            } else {
                views.setTextViewText(viewId, "")
            }
        }
        views.setInt(
            R.id.topic_prev,
            "setVisibility",
            if (page > 0) View.VISIBLE else View.INVISIBLE,
        )
        views.setInt(
            R.id.topic_next,
            "setVisibility",
            if (page < TOPIC_PAGE_COUNT - 1) View.VISIBLE else View.INVISIBLE,
        )
        views.setOnClickPendingIntent(R.id.topic_prev, pageIntent(context, -1))
        views.setOnClickPendingIntent(R.id.topic_next, pageIntent(context, +1))
    }

    private fun pageIntent(context: Context, delta: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_PAGE_BASE + (delta + 1),
            Intent(context, DzenNewsWidgetProvider::class.java)
                .setAction(ACTION_TOPIC_PAGE)
                .putExtra(EXTRA_DELTA, delta),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Клик по шапке: приложение Дзен, если установлено; иначе браузер. */
    private fun dzenOrBrowserIntent(context: Context, request: Int): PendingIntent {
        val intent = if (DzenInfo.isInstalled(context)) {
            Intent()
                .setComponent(ComponentName(DzenInfo.PACKAGE, DzenInfo.MAIN_ACTIVITY))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(DZEN_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun tabIntent(context: Context, tab: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_TAB_BASE + tab,
            Intent(context, DzenNewsWidgetProvider::class.java)
                .setAction(ACTION_TAB_CLICK)
                .putExtra(EXTRA_TAB, tab),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val TAG = "DzenWidget"

        /** Публичная перерисовка всех экземпляров (после фоновой подгрузки). */
        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(
                ComponentName(context, DzenNewsWidgetProvider::class.java)
            ).forEach { id ->
                DzenNewsWidgetProvider().updateWidget(context, manager, id)
            }
        }

        const val ACTION_TAB_CLICK = "ru.zen.android.WIDGET_TAB_CLICK"
        const val ACTION_REFRESH = "ru.zen.android.WIDGET_REFRESH"
        const val ACTION_EXTERNAL_REFRESH = "ru.zen.android.WIDGET_REFRESH_EXT"
        const val ACTION_TOPIC_PAGE = "ru.zen.android.WIDGET_TOPIC_PAGE"


        const val EXTRA_TAB = "tab"
        const val EXTRA_DELTA = "delta"

        /** Главная страница Дзен (открывается в браузере). */
        const val DZEN_URL = "https://dzen.ru"
        /** Рубрик на страницу в полосе рубрик. */
        private const val TOPICS_PER_PAGE = 4

        /** Число страниц рубрик: 12 / 4. */
        val TOPIC_PAGE_COUNT = (NewsRepository.TOPICS.size + TOPICS_PER_PAGE - 1) / TOPICS_PER_PAGE

        private val TIME_FORMAT =
            SimpleDateFormat("HH:mm", Locale.getDefault())

        private const val REQUEST_HEADER = 100
        private const val REQUEST_REFRESH = 150
        private const val REQUEST_NEWS_ROW = 160
        private const val REQUEST_TAB_BASE = 200
        private const val REQUEST_PAGE_BASE = 290
    }
}
