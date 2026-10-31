package ru.zen.android.widget

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Курсы валют ЦБ РФ: сколько рублей стоит 1 USD / 1 EUR / 1 CNY
 * + ключевая ставка Банка России (% годовых).
 *
 * Курсы — официальный XML-сервис ЦБ (www.cbr.ru/scripts/XML_daily.asp):
 * без ключей, с указанием даты (?date_req=dd.MM.yyyy), кодировка windows-1251.
 * Ключевая ставка — страница ЦБ (https://cbr.ru/hd_base/KeyRate/):
 * таблица «Дата | Ставка», свежая строка сверху (XML_daily.asp элемент
 * <KeyRate> больше не отдаёт).
 *
 * Направление (▲/▼) — реальный ежедневный тренд: сегодня vs предыдущий
 * рабочий день ЦБ (для ключевой ставки — vs ранее загруженное значение).
 *
 * Примечание: домен cbr-ru.ru снят с регистрации, daily_json.js мёртв,
 * датированные запросы frankfurter/er-api на сети телефона возвращают 404.
 */
object RatesRepository {

    private const val TAG = "DzenWidget"
    private const val TIMEOUT_MS = 10000
    private const val URL_TODAY = "https://www.cbr.ru/scripts/XML_daily.asp"
    private const val URL_DATE = "https://www.cbr.ru/scripts/XML_daily.asp?date_req="
    private const val URL_KEY_RATE = "https://cbr.ru/hd_base/KeyRate/"

    private val CBR_CHARSET: Charset by lazy { Charset.forName("windows-1251") }
    private val DATE_FMT = SimpleDateFormat("dd.MM.yyyy", Locale.US)

    private val fetching = java.util.concurrent.atomic.AtomicBoolean()

    /** Кэш курсов валиден, пока не прошло FRESH_MS (курс ЦБ меняется раз в день). */
    private const val FRESH_MS = 10L * 60 * 1000

    /** Фоновая подгрузка (один раз, пока предыдущая не завершилась).
     *  [force]=false: если кэш свежий (< 10 мин) — сеть не трогаем. */
    fun fetchAndCacheAsync(
        context: Context,
        force: Boolean = false,
        onComplete: (() -> Unit)? = null,
    ) {
        if (!fetching.compareAndSet(false, true)) return
        Thread {
            try {
                val fresh = !force &&
                    WidgetPrefs.getRatesTs(context) > 0 &&
                    System.currentTimeMillis() - WidgetPrefs.getRatesTs(context) < FRESH_MS &&
                    WidgetPrefs.getRateKr(context) != null
                if (fresh) {
                    Log.w(TAG, "rates: cache fresh (<${FRESH_MS / 60000} мин), skip net")
                } else {
                    fetchAndCache(context.applicationContext)
                }
            } finally {
                fetching.set(false)
            }
            if (onComplete != null) {
                try {
                    onComplete()
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    private class DayRate(
        val date: String?, // dd.MM.yyyy
        val usd: Float,
        val eur: Float,
        val cny: Float,
    )

    /** Загружает курс ЦБ + направление (сегодня vs вчера). true при успехе. */
    fun fetchAndCache(context: Context): Boolean {
        val today = fetchDay(null) ?: return false

        // Предыдущий день для стрелок
        var yesterday: DayRate? = null
        today.date?.let { d ->
            try {
                val cal = Calendar.getInstance()
                cal.time = DATE_FMT.parse(d)!!
                cal.add(Calendar.DAY_OF_MONTH, -1)
                yesterday = fetchDay(DATE_FMT.format(cal.time))
            } catch (_: Exception) {
            }
        }

        val usd = dir(yesterday?.usd, today.usd)
        val eur = dir(yesterday?.eur, today.eur)
        val cny = dir(yesterday?.cny, today.cny)

        WidgetPrefs.setRates(context, today.usd, today.eur, today.cny)
        WidgetPrefs.setRateDirs(context, usd, eur, cny)
        WidgetPrefs.setRatesTs(context, System.currentTimeMillis())
        Log.w(
            TAG,
            String.format(
                Locale.US,
                "rates CBR: USD=%.2f(%+d) EUR=%.2f(%+d) CNY=%.2f(%+d) prev=%s",
                today.usd, usd, today.eur, eur, today.cny, cny,
                yesterday?.date ?: "-",
            ),
        )
        fetchAndCacheKeyRate(context)
        return true
    }

    /** 1 — рост, -1 — падение, 0 — без изменения/без данных за вчера. */
    private fun dir(old: Float?, new: Float): Int =
        when {
            old == null -> 0
            new > old -> 1
            new < old -> -1
            else -> 0
        }

    /** Загружает курс дня [dateReq] (dd.MM.yyyy; null — текущий). */
    private fun fetchDay(dateReq: String?): DayRate? {
        val urlStr = if (dateReq == null) URL_TODAY else "$URL_DATE$dateReq"
        try {
            Log.w(TAG, "rates: get $urlStr")
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.requestMethod = "GET"
            val ok = (200..299).contains(conn.responseCode)
            val body = if (ok) String(conn.inputStream.readBytes(), CBR_CHARSET) else ""
            conn.disconnect()
            if (!ok) return null
            return parseCbrXml(body, dateReq)
        } catch (e: Exception) {
            Log.w(TAG, "rates cbr failed ($urlStr): ${e.message}")
            return null
        }
    }

    /** Парсинг XML ЦБ: VunitRate (курс за 1 единицу) по CharCode. */
    private fun parseCbrXml(body: String, dateReq: String?): DayRate? {
        val date = Regex("""ValCurs\s+Date="([^"]+)""").find(body)
            ?.groupValues?.get(1)?.ifEmpty { dateReq } ?: dateReq
        fun rate(code: String): Float? {
            for (chunk in body.split("<Valute")) {
                if (!chunk.contains("<CharCode>$code</CharCode>")) continue
                val m = Regex("<VunitRate>([\\d.,]+)</VunitRate>").find(chunk) ?: continue
                return m.groupValues[1].replace(",", ".").toFloatOrNull()
            }
            return null
        }
        val usd = rate("USD") ?: return null
        val eur = rate("EUR") ?: return null
        val cny = rate("CNY") ?: return null
        return DayRate(date, usd, eur, cny)
    }

    /** Ключевая ставка: отдельная страница ЦБ (таблица «Дата | Ставка»,
     *  свежая строка сверху). Направление — vs ранее загруженное значение. */
    private fun fetchAndCacheKeyRate(context: Context) {
        val kr = fetchKeyRate() ?: return
        val prev = WidgetPrefs.getRateKr(context)
        val dir = when {
            prev == null -> 0
            kr.first > prev.first -> 1
            kr.first < prev.first -> -1
            else -> 0
        }
        WidgetPrefs.setRateKr(context, kr.first, kr.second)
        WidgetPrefs.setRateKrDir(context, dir)
        Log.w(
            TAG,
            String.format(
                Locale.US,
                "key rate CBR: %.2f%% (дата %s, prev=%s, dir=%+d)",
                kr.first, kr.second, prev?.first?.toString() ?: "-", dir,
            ),
        )
    }

    /** (ставка %, дата dd.MM.yyyy) — null, если страница не распарсилась. */
    private fun fetchKeyRate(): Pair<Float, String>? {
        try {
            Log.w(TAG, "kr: get $URL_KEY_RATE")
            val conn = URL(URL_KEY_RATE).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.setRequestProperty("Accept-Encoding", "gzip")
            conn.requestMethod = "GET"
            val ok = (200..299).contains(conn.responseCode)
            val raw = if (ok) conn.inputStream.readBytes() else ByteArray(0)
            val gz = conn.getContentEncoding()?.equals("gzip", true) == true
            conn.disconnect()
            if (!ok) return null
            val html = if (gz) {
                GZIPInputStream(ByteArrayInputStream(raw)).readBytes().toString(Charsets.UTF_8)
            } else {
                raw.toString(Charsets.UTF_8)
            }
            val m = Regex("<td>(\\d{2}\\.\\d{2}\\.\\d{4})</td>\\s*<td>([\\d.,]+)</td>")
                .find(html) ?: return null
            val rate = m.groupValues[2].replace(",", ".").toFloatOrNull() ?: return null
            return Pair(rate, m.groupValues[1])
        } catch (e: Exception) {
            Log.w(TAG, "kr: cbr key rate failed: ${e.message}")
            return null
        }
    }
}
