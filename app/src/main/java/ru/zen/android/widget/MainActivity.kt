package ru.zen.android.widget

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/**
 * Экран запуска «Дзен.Новости».
 *
 * Приложение-компаньон к Дзен (ru.zen.android): при запуске проверяет,
 * что Дзен установлен. Без Дзена виджет не работает — показываем сообщение.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.tv_status)
        val details = findViewById<TextView>(R.id.tv_details)

        if (DzenInfo.isInstalled(this)) {
            status.setText(R.string.status_dzen_ok)
            status.setTextColor(0xFF4CAF50.toInt())
            details.setText(R.string.details_dzen_ok)
        } else {
            status.setText(R.string.status_dzen_missing)
            status.setTextColor(0xFFE53935.toInt())
            details.setText(R.string.details_dzen_missing)
        }

        // Приложение активно — подгружаем курсы (стрелки в виджете)
        // Приложение активно — подгружаем курсы (стрелки в виджете)
        RatesRepository.fetchAndCacheAsync(applicationContext)

        // Регионы Дзен (вкладки «город»): до двух, ввод кириллицей —
        // виджет сам найдёт алиас региона (Абакан → Abakan).
        val etRegion1 = findViewById<EditText>(R.id.et_region1)
        val etRegion2 = findViewById<EditText>(R.id.et_region2)
        val btnRegion = findViewById<Button>(R.id.btn_region)
        // Поля показываем кириллицей (официальное имя региона из API Дзен);
        // алиас (латиница) — только если имя ещё не синхронизировано.
        etRegion1.setText(
            WidgetPrefs.getRegion1Name(this).ifEmpty { WidgetPrefs.getRegion1Alias(this) }
        )
        etRegion2.setText(
            WidgetPrefs.getRegion2Name(this).ifEmpty { WidgetPrefs.getRegion2Alias(this) }
        )
        btnRegion.setOnClickListener {
            val a1 = NewsRepository.aliasForCity(etRegion1.text.toString())
            val a2 = NewsRepository.aliasForCity(etRegion2.text.toString())
            if (a1.isNotEmpty()) {
                WidgetPrefs.setRegion1Alias(this, a1)
                // Имя показываем кириллицей: сохраняем введённый текст,
                // затем API Дзен синхронизирует официальное название.
                WidgetPrefs.setRegion1Name(
                    this, etRegion1.text.toString().trim().ifEmpty { a1 }
                )
                WidgetPrefs.setRegion2Alias(this, a2)
                WidgetPrefs.setRegion2Name(
                    this, etRegion2.text.toString().trim().ifEmpty { a2 }
                )
                // Сразу подгружаем ленты регионов (имена вкладок
                // синхронизируются из ответа API Дзен)
                NewsRepository.fetchTabAsync(applicationContext, 1, force = true) {
                    if (a2.isNotEmpty() && a2 != a1) {
                        NewsRepository.fetchTabAsync(applicationContext, 2, force = true)
                    }
                }
            }
        }
    }
}
