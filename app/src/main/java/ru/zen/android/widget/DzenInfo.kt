package ru.zen.android.widget

import android.content.Context
import android.content.pm.PackageManager

/**
 * Хелпер: оригинальное приложение Дзен (ru.zen.android) — компаньон виджета.
 * Виджет «Дзен.Новости» работает только при установленном Дзене.
 */
object DzenInfo {

    /** Пакет оригинального приложения Дзен. */
    const val PACKAGE = "ru.zen.android"

    /** Главная активность Дзен (иконка/шапка виджета). */
    const val MAIN_ACTIVITY = "com.yandex.zen.ZenMainActivity"

    /** Установлено ли приложение Дзен на устройстве. */
    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
