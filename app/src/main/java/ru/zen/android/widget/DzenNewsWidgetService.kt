package ru.zen.android.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViewsService

/** Сервис ленты новостей (ListView в RemoteViews). */
class DzenNewsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        DzenNewsWidgetFactory(applicationContext)
}
