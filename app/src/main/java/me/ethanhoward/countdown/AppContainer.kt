package me.ethanhoward.countdown

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.ethanhoward.countdown.data.AppDatabase
import me.ethanhoward.countdown.data.EventRepository
import me.ethanhoward.countdown.data.WidgetConfigRepository
import me.ethanhoward.countdown.widget.WidgetBackgroundProcessor
import me.ethanhoward.countdown.widget.WidgetRefresher

/** Manual dependency container, created once by [CountdownApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Scope for work that must outlive a screen, e.g. refreshing widgets after an edit. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val eventRepository: EventRepository by lazy {
        EventRepository(database.eventDao()) { WidgetRefresher.refreshAll(appContext) }
    }

    val widgetConfigRepository: WidgetConfigRepository by lazy {
        WidgetConfigRepository(database.widgetConfigDao(), File(appContext.filesDir, "widget_bg").apply { mkdirs() })
    }

    val backgroundProcessor: WidgetBackgroundProcessor by lazy { WidgetBackgroundProcessor(appContext) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as CountdownApp).container
