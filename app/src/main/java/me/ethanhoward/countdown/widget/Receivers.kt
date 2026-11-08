package me.ethanhoward.countdown.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.ethanhoward.countdown.appContainer
import me.ethanhoward.countdown.data.WidgetConfig

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.launchAsync(block: suspend CoroutineScope.() -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } finally {
            if (pending !== null) {
                pending.finish()
            }
        }
    }
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        launchAsync { WidgetRefresher.scheduleNext(context) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        launchAsync {
            val repo = context.appContainer.widgetConfigRepository
            appWidgetIds.forEach { repo.delete(it) }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefresher.cancel(context)
    }
}

/** Refreshes widgets when the clock, time zone or app version changes. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> launchAsync { WidgetRefresher.refreshAll(context) }
        }
    }
}
