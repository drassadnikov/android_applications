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
    private const val KEY_KR = "rate_kr"
    private const val KEY_KR_DIR = "rate_kr_dir"
    private const val KEY_KR_DATE = "rate_kr_date"

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

    /** Кэш ключевой ставки ЦБ: (значение, дата) — null, если ещё не загружена. */
    fun getRateKr(context: Context): Pair<Float, String>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val kr = p.getFloat(KEY_KR, 0f)
        return if (kr > 0f) Pair(kr, p.getString(KEY_KR_DATE, "") ?: "") else null
    }

    fun setRateKr(context: Context, rate: Float, date: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_KR, rate)
            .putString(KEY_KR_DATE, date)
            .apply()
    }

    /** Направление ключевой ставки: 1 — повышение, -1 — снижение, 0 — без изменения. */
    fun getRateKrDir(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_KR_DIR, 0)

    fun setRateKrDir(context: Context, dir: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_KR_DIR, dir)
            .apply()
    }

    // ---- Регионы Дзен (вкладки «город», до двух, как в веб-версии) ----

    private const val KEY_REGION_ALIAS = "region_alias"
    private const val KEY_REGION_NAME = "region_name"
    private const val KEY_REGION2_ALIAS = "region2_alias"
    private const val KEY_REGION2_NAME = "region2_name"

    /** Алиас региона №1 в API Дзен (латиница): Abakan, Donetsk, Moscow, … */
    fun getRegion1Alias(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REGION_ALIAS, "Abakan") ?: "Abakan"

    fun setRegion1Alias(context: Context, alias: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REGION_ALIAS, alias.trim())
            .apply()
    }

    /** Отображаемое название региона №1 — автосинхронизируется из API Дзен. */
    fun getRegion1Name(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REGION_NAME, "Абакан") ?: "Абакан"

    fun setRegion1Name(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REGION_NAME, name)
            .apply()
    }

    /** Алиас региона №2 ("" — вторая вкладка города выключена). */
    fun getRegion2Alias(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REGION2_ALIAS, "") ?: ""

    fun setRegion2Alias(context: Context, alias: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REGION2_ALIAS, alias.trim())
            .apply()
    }

    /** Отображаемое название региона №2 — автосинхронизируется из API Дзен. */
    fun getRegion2Name(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REGION2_NAME, "") ?: ""

    fun setRegion2Name(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REGION2_NAME, name)
            .apply()
    }
}
