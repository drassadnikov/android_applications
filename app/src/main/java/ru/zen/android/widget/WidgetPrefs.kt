package ru.zen.android.widget

import android.content.Context

/**
 * Состояние виджета «Дзен.Новости»:
 * активная тематика, счётчик «Обновить», время последнего обновления,
 * кэш курсов валют ЦБ (USD/EUR/CNY в рублях).
 */
object WidgetPrefs {
    private const val PREFS = "dzen_news_widget"
    private const val KEY_TAB = "active_tab"
    private const val KEY_PAGE = "topic_page"
    private const val KEY_FEED_PAGE = "feed_page"
    private const val KEY_SEED = "refresh_seed"
    private const val KEY_LAST_UPDATE = "last_update_millis"
    private const val KEY_USD = "rate_usd"
    private const val KEY_EUR = "rate_eur"
    private const val KEY_CNY = "rate_cny"
    private const val KEY_USD_DIR = "rate_usd_dir"
    private const val KEY_EUR_DIR = "rate_eur_dir"
    private const val KEY_CNY_DIR = "rate_cny_dir"
    private const val KEY_BASE_USD = "base_usd"
    private const val KEY_BASE_EUR = "base_eur"
    private const val KEY_BASE_CNY = "base_cny"
    private const val KEY_BASE_DATE = "base_date"

    fun getActiveTab(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_TAB, 0)

    fun setActiveTab(context: Context, tab: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TAB, tab)
            .apply()
    }

    /** Страница полосы тематик (0..pageCount-1). */
    fun getTopicPage(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PAGE, 0)

    fun setTopicPage(context: Context, page: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PAGE, page)
            .apply()
    }

    /** Страница ленты новостей (0..pageCount-1). */
    fun getFeedPage(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_FEED_PAGE, 0)

    fun setFeedPage(context: Context, page: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FEED_PAGE, page)
            .apply()
    }

    /** Счётчик нажатий «Обновить»: каждый клик сдвигает ленту. */
    fun getRefreshSeed(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SEED, 0)

    fun bumpRefreshSeed(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SEED, getRefreshSeed(context) + 1)
            .apply()
    }

    /** Время последнего обновления (0 — ещё не обновлялось). */
    fun getLastUpdateMillis(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATE, 0L)

    fun setLastUpdateMillis(context: Context, millis: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_UPDATE, millis)
            .apply()
    }

    /** Кэш курсов: null, если ещё не загружены. */
    fun getRates(context: Context): Triple<Float, Float, Float>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val usd = p.getFloat(KEY_USD, 0f)
        val eur = p.getFloat(KEY_EUR, 0f)
        val cny = p.getFloat(KEY_CNY, 0f)
        return if (usd > 0f && eur > 0f && cny > 0f) Triple(usd, eur, cny) else null
    }

    fun setRates(context: Context, usd: Float, eur: Float, cny: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_USD, usd)
            .putFloat(KEY_EUR, eur)
            .putFloat(KEY_CNY, cny)
            .apply()
    }

    /** Направления: 1 — рост, -1 — падение, 0 — без данных/без изменения. */
    fun getRateDirs(context: Context): Triple<Int, Int, Int> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(
            p.getInt(KEY_USD_DIR, 0),
            p.getInt(KEY_EUR_DIR, 0),
            p.getInt(KEY_CNY_DIR, 0),
        )
    }

    fun setRateDirs(context: Context, usd: Int, eur: Int, cny: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_USD_DIR, usd)
            .putInt(KEY_EUR_DIR, eur)
            .putInt(KEY_CNY_DIR, cny)
            .apply()
    }

    /** Курс предыдущего дня (база для стрелок): null, если ещё не загружен. */
    fun getRateBaseline(context: Context): Triple<Float, Float, Float>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val usd = p.getFloat(KEY_BASE_USD, 0f)
        val eur = p.getFloat(KEY_BASE_EUR, 0f)
        val cny = p.getFloat(KEY_BASE_CNY, 0f)
        return if (usd > 0f && eur > 0f && cny > 0f) Triple(usd, eur, cny) else null
    }

    fun setRateBaseline(context: Context, usd: Float, eur: Float, cny: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_BASE_USD, usd)
            .putFloat(KEY_BASE_EUR, eur)
            .putFloat(KEY_BASE_CNY, cny)
            .apply()
    }

    /** Дата курса-базы (yyyy-MM-dd). */
    fun getRateBaselineDate(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE_DATE, "") ?: ""

    fun setRateBaselineDate(context: Context, date: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_DATE, date)
            .apply()
    }

    /** Время последней загрузки курсов (0 — ещё не загружались). */
    fun getRatesTs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("rates_ts", 0L)

    fun setRatesTs(context: Context, millis: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("rates_ts", millis)
            .apply()
    }
}
