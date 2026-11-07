package me.ethanhoward.countdown.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import me.ethanhoward.countdown.appContainer
import me.ethanhoward.countdown.domain.CountdownCalculator

object WidgetRefresher {
    private const val WORK_NAME = "countdown-refresh"

    /** Written to each widget's Glance state on refresh so a running composition recomposes with the new time. */
    val REFRESHED_AT = longPreferencesKey("refreshed_at")

    /** Slack added after a boundary so we never render a moment too early. */
    private val SLACK: Duration = Duration.ofSeconds(5)

    /** Re-renders every placed widget and schedules the next refresh. */
    suspend fun refreshAll(context: Context, fromWorker: Boolean = false) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(CountdownWidget::class.java).forEach { refresh(context, it) }
        scheduleNext(context, fromWorker)
    }

    suspend fun refresh(context: Context, glanceId: GlanceId) {
        updateAppWidgetState(context, glanceId) { it[REFRESHED_AT] = System.currentTimeMillis() }
        CountdownWidget().update(context, glanceId)
    }

    /**
     * Schedules a one-off refresh at the next moment any widget's text could change: local midnight,
     * or sooner for timed events in their final day. Cancels the chain when no widgets are placed.
     */
    suspend fun scheduleNext(context: Context, fromWorker: Boolean = false) {
        val workManager = WorkManager.getInstance(context)
        val hasWidgets = GlanceAppWidgetManager(context).getGlanceIds(CountdownWidget::class.java).isNotEmpty()
        if (!hasWidgets) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val container = context.appContainer
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val eventIds = container.widgetConfigRepository.getAll().mapNotNull { it.eventId }.toSet()
        val next = container.eventRepository.getAll()
            .filter { it.id in eventIds }
            .mapNotNull { CountdownCalculator.nextChange(it.targetDate, it.targetTime, now, zone) }
            .plus(CountdownCalculator.nextMidnight(now, zone))
            .min()
        val delay = Duration.between(now, next).plus(SLACK).coerceAtLeast(SLACK)

        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        // A worker can't REPLACE itself without cancelling its own run, so it appends instead.
        val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetRefresher.refreshAll(applicationContext, fromWorker = true)
        return Result.success()
    }
}
