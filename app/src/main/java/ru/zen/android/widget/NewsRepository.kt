package ru.zen.android.widget

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream

/**
 * Источник данных виджета «Дзен.Новости» — тот же API, что использует вкладка
 * «Новости» приложения Дзен (ru.zen.android), 26.10.2:
 *
 *   https://dzen.ru/news/api/v4/rubric?rubric=index
 *   -> data.rubrics[] — ВСЕ рубрики меню (Главное, Свежее, СВО, Спорт, …) сразу,
 *      каждая со stories[]: annot.snippets[0].doc {title, url, source_name, logo, pub_date}.
 *
 *   https://dzen.ru/news/api/v4/rubric?rubric=showbusiness  — Шоу-бизнес
 *   https://dzen.ru/news/api/v4/rubric?region=Donetsk      — региональная рубрика
 *
 * Оптимизации:
 *  - gzip (1,6 МБ JSON сжимается до ~250 КБ);
 *  - кэш рубрики считается «свежим» 10 минут — повторный тап по рубрике
 *    не ходит в сеть (force=true у «Обновить» сбрасывает);
 *  - иконки источников предзагружаются фоном (пул 4 потока) в файлы cacheDir;
 *    фабрика строк читает только файлы — сеть на трэде отрисовки нет.
 */
object NewsRepository {

    private const val TAG = "DzenWidget"
    private const val TIMEOUT_MS = 10000
    private const val URL_RUBRIC = "https://dzen.ru/news/api/v4/rubric"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) ZenWidget/18"
    private const val PREFS = "dzen_news_widget"

    /** Кэш рубрики валиден, пока не прошло FRESH_MS. */
    private const val FRESH_MS = 10L * 60 * 1000
    private const val TOPICS_PER_PAGE = 4

    /** Рубрика: [name] — подпись в виджете; [rubric] — алиас ?rubric=;
     *  [region] — алиас ?region= (региональная рубрика). */
    data class Topic(val name: String, val rubric: String?, val region: String? = null)

    /** Базовые рубрики (без регионов) — порядок как в приложении Дзен. */
    private val BASE_TOPICS = listOf(
        Topic("Главное", "index"),
        Topic("Свежее", "chronologic"),
        Topic("СВО", "svo"),
        Topic("Спорт", "sport"),
        Topic("Шоу-бизнес", "showbusiness"),
        Topic("Происшествия", "incident"),
        Topic("Авто", "auto"),
        Topic("Политика", "politics"),
        Topic("Общество", "society"),
        Topic("Экономика", "business"),
        Topic("В мире", "world"),
        Topic("Культура", "culture"),
        Topic("Технологии", "computers"),
        Topic("Наука", "science"),
    )

    /** Список вкладок с учётом регионов Дзен пользователя:
     *  [Главное, [Регион 1], [Регион 2], Свежее, …] — как в веб-версии
     *  (вторая вкладка города добавляется, только если регион №2 задан). */
    fun topics(context: Context?): List<Topic> {
        val list = mutableListOf(TOPICS_MAIN)
        if (context != null) {
            val a1 = WidgetPrefs.getRegion1Alias(context)
            if (a1.isNotEmpty()) {
                list.add(Topic(WidgetPrefs.getRegion1Name(context), null, a1))
            }
            val a2 = WidgetPrefs.getRegion2Alias(context)
            if (a2.isNotEmpty() && a2 != a1) {
                list.add(Topic(WidgetPrefs.getRegion2Name(context).ifEmpty { a2 }, null, a2))
            }
        }
        list.addAll(BASE_TOPICS.drop(1))
        return list
    }

    private val TOPICS_MAIN = Topic("Главное", "index")

    private val fetching = java.util.concurrent.atomic.AtomicBoolean()
    private var cachedContext: Context? = null

    /** Пул предзагрузки иконок: 4 параллельных загрузки, не блокируют UI. */
    private val iconPool = Executors.newFixedThreadPool(4)
    private val iconsPending = AtomicInteger(0)

    /** Запоминаем контекст (для кэша) — из onUpdate/onReceive. */
    fun bindContext(context: Context) {
        cachedContext = context.applicationContext
    }

    /** Файл кэша иконки: cacheDir/fav_<hash(url)>. Общий для репозитория и фабрики. */
    fun iconFileFor(context: Context, url: String): File =
        File(context.cacheDir, "fav_" + Integer.toHexString(url.hashCode()))

    /** Ключ кэша рубрики: алиас rubric или region. */
    /** Версия кэша рубрик: префикс сбрасывает устаревшие кэши (включая
     *  чужие региональные данные) при обновлении приложения. */
    private const val CACHE_VERSION = "v18b_"

    /** Ключ кэша рубрики: алиас rubric или region (с версионным префиксом). */
    /** Ключ кэша рубрики: алиас rubric или region (с версионным префиксом). */
    private fun cacheKey(tab: Int): String {
        val t = topicFor(tab, cachedContext)
        return CACHE_VERSION + (t.rubric ?: "region_" + t.region ?: "main")
    }

    /** Тема вкладки с учётом регионов Дзен пользователя. */
    fun topicFor(tab: Int, context: Context? = null): Topic {
        val list = topics(context)
        return list.getOrElse(tab) { list.first() }
    }

    /** Число страниц полосы вкладок (4 вкладки на страницу). */
    fun topicPageCount(context: Context?): Int =
        (topics(context).size + TOPICS_PER_PAGE - 1) / TOPICS_PER_PAGE

    /** Кириллица/латиница -> алиас региона Дзен.
     *  «Абакан» → Abakan, «Ростов-на-Дону» → Rostov-na-Donu;
     *  не найденный в карте текст возвращается как есть (алиас в латинице). */
    fun aliasForCity(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return t
        return CITY_ALIASES[t.lowercase()] ?: t
    }

    /** Кэшированные новости рубрики [tab] в исходном порядке API
     *  (тот же порядок, что показывает вкладка «Новости» приложения). */
    fun getNews(tab: Int, seed: Int): List<NewsItem> {
        return loadCache(cacheKey(tab))
    }

    /** Фоновая подгрузка рубрики [tab] (одна за раз).
     *  [force]=false: если кэш свежий (< 10 мин) — сеть не трогаем. */
    fun fetchTabAsync(
        context: Context,
        tab: Int,
        force: Boolean = false,
        onComplete: (() -> Unit)? = null,
    ) {
        if (!fetching.compareAndSet(false, true)) return
        Thread {
            try {
                fetchTab(context.applicationContext, tab, force)
            } finally {
                fetching.set(false)
            }
            onComplete?.let {
                try {
                    it()
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    /** Загружает рубрику [tab] в кэш. true при успехе. */
    private fun fetchTab(context: Context, tab: Int, force: Boolean): Boolean {
        val topic = topicFor(tab, context)
        val key = cacheKey(tab)
        val alias = topic.rubric ?: topic.region ?: "main"
        if (!force && isFresh(key) && loadCache(key).isNotEmpty()) {
            Log.w(TAG, "feed[${topic.name}]: cache fresh (<${FRESH_MS / 60000} мин), skip net")
            return true
        }
        val query = when {
            topic.region != null -> "region=" + topic.region
            else -> "rubric=" + topic.rubric
        }
        val body = httpGet(URL_RUBRIC + "?" + query) ?: return false
        val parsedAll = parseRubrics(body)
        // Региональный запрос: берём ТОЛЬКО запрошенный регион —
        // другие рубрики ответа кэш не трогают.
        val parsed = if (topic.region != null) parsedAll.filterKeys { it == alias } else parsedAll
        // Регион: синхронизируем отображаемое имя вкладки с ответом API Дзен
        // (например, «Абакан»), чтобы вкладка показывала официальный город.
        if (topic.region != null) {
            regionNameFromBody(body, alias)?.let { name ->
                val a1 = WidgetPrefs.getRegion1Alias(context)
                val a2 = WidgetPrefs.getRegion2Alias(context)
                if (alias == a1) WidgetPrefs.setRegion1Name(context, name)
                else if (alias == a2) WidgetPrefs.setRegion2Name(context, name)
            }
        }
        if (parsed.isEmpty()) {
            Log.w(TAG, "feed[${topic.name}]: 0 stories")
            return false
        }
        // «Главное» (index) отдаёт все рубрики меню разом — сохраним каждую.
        var saved = 0
        val now = System.currentTimeMillis()
        val allItems = mutableListOf<NewsItem>()
        for ((k, items) in parsed) {
            if (items.isNotEmpty()) {
                // Регионы сохраняем под тем же ключом, что и cacheKey()
                // (region_<алиас>), иначе лента не подхватит кэш.
                val saveKey = if (topic.region != null) CACHE_VERSION + "region_" + k
                else CACHE_VERSION + k
                saveCache(saveKey, items, now)
                saved++
                allItems.addAll(items)
            }
        }
        val own = parsed[alias]?.size ?: 0
        Log.w(TAG, "feed[${topic.name}]: $own items (saved $saved rubrics)")
        prefetchIcons(context, allItems)
        return own > 0
    }

    private fun isFresh(key: String): Boolean {
        val ts = cachedContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getLong("feed_ts_$key", 0L) ?: 0L
        return ts > 0 && System.currentTimeMillis() - ts < FRESH_MS
    }

    /** Предзагрузка иконок всех элементов (уникальные URL, отсутствующие в файле).
     *  Завершение последней загрузки — одна перерисовка виджета (иконки появятся). */
    private fun prefetchIcons(context: Context, items: List<NewsItem>) {
        val appCtx = context.applicationContext
        val missing = items
            .map { it.logoUrl }
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { url ->
                val f = iconFileFor(appCtx, url)
                !f.exists() || f.length() < 100
            }
        if (missing.isEmpty()) return
        Log.w(TAG, "icons: prefetch ${missing.size} of ${items.size} items")
        for (url in missing) {
            iconsPending.incrementAndGet()
            iconPool.execute {
                try {
                    downloadIconFile(appCtx, url)
                } catch (_: Exception) {
                } finally {
                    if (iconsPending.decrementAndGet() == 0) {
                        try {
                            DzenNewsWidgetProvider.refreshAllWidgets(appCtx)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    /** Скачивание иконки в файл кэша. Пустая заглушка (< 100 Б) удаляется. */
    private fun downloadIconFile(context: Context, url: String) {
        val file = iconFileFor(context, url)
        if (file.exists() && file.length() >= 100) return
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "ZenWidget/18")
        if ((200..299).contains(conn.responseCode)) {
            file.outputStream().use { out ->
                conn.inputStream.use { it.copyTo(out) }
            }
        }
        conn.disconnect()
        if (file.exists() && file.length() < 100) file.delete()
    }

    /** Ответ rubric -> карты алиас -> список новостей (data.rubrics[].stories[]). */
    /** Отображаемое имя региона из ответа API (data.rubrics[].rubric.name). */
    private fun regionNameFromBody(body: String, alias: String): String? {
        return try {
            val rubrics = JSONObject(body).optJSONObject("data")?.optJSONArray("rubrics")
                ?: return null
            for (i in 0 until rubrics.length()) {
                val r = rubrics.getJSONObject(i).optJSONObject("rubric") ?: continue
                if (r.optString("alias") == alias) {
                    return r.optString("name").trim().ifEmpty { null }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Ответ rubric -> карты алиас -> список новостей (data.rubrics[].stories[]). */
    private fun parseRubrics(body: String): Map<String, List<NewsItem>> {
        val result = LinkedHashMap<String, List<NewsItem>>()
        try {
            val rubrics = JSONObject(body).optJSONObject("data")?.optJSONArray("rubrics")
                ?: return result
            for (i in 0 until rubrics.length()) {
                val entry = rubrics.getJSONObject(i)
                val alias = entry.optJSONObject("rubric")?.optString("alias") ?: continue
                val stories = entry.optJSONArray("stories") ?: continue
                val list = parseStories(stories)
                if (list.isNotEmpty()) result[alias] = list
            }
        } catch (e: Exception) {
            Log.w(TAG, "feed: rubric parse failed: ${e.message}")
        }
        return result
    }

    /** stories[] -> NewsItem (annot.snippets[0].doc). */
    private fun parseStories(stories: JSONArray): List<NewsItem> {
        val result = mutableListOf<NewsItem>()
        for (i in 0 until stories.length()) {
            val story = stories.optJSONObject(i) ?: continue
            val annot = story.optJSONObject("annot") ?: continue
            val doc = annot.optJSONArray("snippets")
                ?.optJSONObject(0)
                ?.optJSONObject("doc")
                ?: continue
            val item = storyFromDoc(doc, annot) ?: continue
            result.add(item)
        }
        return result.distinctBy { it.url }
    }

    /** doc + annot -> NewsItem. url — страница статьи Дзен (annot.story_url,
     *  dzen.ru/news/story/...), НЕ instory_url (агрегатор «Эта новость в СМИ»).
     *  logo — реальный логотип источника (avatars.dzeninfra.ru). */
    private fun storyFromDoc(doc: JSONObject, annot: JSONObject): NewsItem? {
        val title = doc.optString("title").trim()
        if (title.isEmpty()) return null
        var url = annot.optString("story_url").trim()
        if (url.isEmpty()) url = annot.optString("instory_url").trim()
        if (url.isEmpty()) url = doc.optString("url").trim()
        if (url.isEmpty()) return null
        val source = doc.optString("source_name").trim().ifEmpty { "Дзен" }
        // Рабочий URL логотипа: logo_square + "/logo-square".
        // Поле "logo" (суффикс -regular) на avatars.dzeninfra.ru отдаёт 404,
        // а именно "logo-square" грузит живой актуальный логотип (как веб-Дзен).
        var logoUrl = doc.optString("logo_square").trim()
        if (logoUrl.isNotEmpty()) logoUrl += "/logo-square"
        else logoUrl = doc.optString("logo").trim()
        val ts = doc.optString("pub_date").trim().toLongOrNull() ?: 0L
        return NewsItem(
            id = url,
            title = title,
            source = source,
            time = formatAge(ts),
            url = url,
            logoUrl = logoUrl,
        )
    }

    /** unix ts (сек) -> «только что» / «35 мин» / «2 ч» / «вчера» / «dd.MM». */
    private fun formatAge(tsSec: Long): String {
        if (tsSec <= 0) return ""
        val ageMin = (System.currentTimeMillis() / 1000 - tsSec) / 60
        return when {
            ageMin < 10 -> "только что"
            ageMin < 60 -> "$ageMin мин"
            ageMin < 60 * 24 -> "${ageMin / 60} ч"
            ageMin < 60 * 48 -> "вчера"
            else -> java.text.SimpleDateFormat("dd.MM", Locale.getDefault())
                .format(java.util.Date(tsSec * 1000))
        }
    }

    private fun saveCache(key: String, items: List<NewsItem>, ts: Long) {
        val json = JSONArray()
        items.forEach {
            json.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("source", it.source)
                    put("time", it.time)
                    put("url", it.url)
                    put("logoUrl", it.logoUrl)
                },
            )
        }
        cachedContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("feed_cache_$key", json.toString())
            ?.putLong("feed_ts_$key", ts)
            ?.apply()
    }

    private fun loadCache(key: String): List<NewsItem> {
        val raw = cachedContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getString("feed_cache_$key", "") ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<NewsItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    NewsItem(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        source = o.optString("source"),
                        time = o.optString("time"),
                        url = o.optString("url"),
                        logoUrl = o.optString("logoUrl"),
                    ),
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** GET с gzip-сжатием: 1,6 МБ JSON идёт сетью как ~250 КБ. */
    private fun httpGet(urlStr: String): String? {
        try {
            Log.w(TAG, "feed: get $urlStr")
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Encoding", "gzip")
            conn.requestMethod = "GET"
            val ok = (200..299).contains(conn.responseCode)
            val body = if (ok) {
                val raw = conn.inputStream.readBytes()
                if (conn.getContentEncoding()?.equals("gzip", true) == true) {
                    GZIPInputStream(ByteArrayInputStream(raw))
                        .readBytes()
                        .toString(Charsets.UTF_8)
                } else {
                    raw.toString(Charsets.UTF_8)
                }
            } else ""
            conn.disconnect()
            return if (ok && body.isNotEmpty()) body else null
        } catch (e: Exception) {
            Log.w(TAG, "feed: http failed ($urlStr): ${e.message}")
            return null
        }
    }

    /** Город (кириллица, без учёта регистра) -> алиас региона Дзен (латиница).
     *  Алиасы проверены по API dzen.ru/news/api/v4/rubric?region=… */
    private val CITY_ALIASES = mapOf(
        "москва" to "Moscow",
        "санкт-петербург" to "Saint_Petersburg",
        "новосибирск" to "Novosibirsk",
        "екатеринбург" to "Yekaterinburg",
        "казань" to "Kazan",
        "нижний новгород" to "Nizhny_Novgorod",
        "челябинск" to "Chelyabinsk",
        "самара" to "Samara",
        "омск" to "Omsk",
        "ростов-на-дону" to "Rostov-na-Donu",
        "ростов" to "Rostov-na-Donu",
        "уфа" to "Ufa",
        "краснодар" to "Krasnodar",
        "пермь" to "Perm",
        "воронеж" to "Voronezh",
        "волгоград" to "Volgograd",
        "красноярск" to "Krasnoyarsk",
        "тюмень" to "Tyumen",
        "томск" to "Tomsk",
        "иркутск" to "Irkutsk",
        "барнаул" to "Barnaul",
        "владивосток" to "Vladivostok",
        "хабаровск" to "Khabarovsk",
        "ярославль" to "Yaroslavl",
        "тула" to "Tula",
        "калининград" to "Kaliningrad",
        "севастополь" to "Sevastopol",
        "сочи" to "Sochi",
        "мурманск" to "Murmansk",
        "вологда" to "Vologda",
        "псков" to "Pskov",
        "смоленск" to "Smolensk",
        "брянск" to "Bryansk",
        "курск" to "Kursk",
        "белгород" to "Belgorod",
        "липецк" to "Lipetsk",
        "тамбов" to "Tambov",
        "рязань" to "Ryazan",
        "калуга" to "Kaluga",
        "иваново" to "Ivanovo",
        "кострома" to "Kostroma",
        "владимир" to "Vladimir",
        "тверь" to "Tver",
        "оренбург" to "Orenburg",
        "саратов" to "Saratov",
        "саранск" to "Saransk",
        "петрозаводск" to "Petrozavodsk",
        "сыктывкар" to "Syktyvkar",
        "йошкар-ола" to "Yoshkar-Ola",
        "симверополь" to "Simferopol",
        "чебоксары" to "Cheboksary",
        "абакан" to "Abakan",
        "донецк" to "Donetsk",
        "запорожье" to "Zaporizhia",
        "махачкала" to "Makhachkala",
        "чита" to "Chita",
        "кемерово" to "Kemerovo",
        "киров" to "Kirov",
        "магадан" to "Magadan",
        "якутск" to "Yakutsk",
        "ульяновск" to "Ulyanovsk",
        "пенза" to "Penza",
        "ставрополь" to "Stavropol",
        "улан-удэ" to "Ulan-Ude",
        "салехард" to "Salekhard",
        "ханты-мансийск" to "Khanty-Mansiysk",
    )
}
