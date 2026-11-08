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

/**
 * Callback for widgets pinned from inside the app (AppWidgetManager.requestPinAppWidget). The
 * system skips the configure activity in that flow, so we link the widget to the event here.
 */
class PinWidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || eventId < 0) return
        launchAsync {
            context.appContainer.widgetConfigRepository.save(WidgetConfig(appWidgetId = appWidgetId, eventId = eventId))
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            WidgetRefresher.refresh(context, glanceId)
            WidgetRefresher.scheduleNext(context)
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "me.ethanhoward.countdown.EXTRA_EVENT_ID"
    }
}
