package ru.zen.android.widget

import android.app.Activity
import android.os.Bundle
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
        RatesRepository.fetchAndCacheAsync(applicationContext)
    }
}
