package me.ethanhoward.countdown.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ethanhoward.countdown.appContainer
import me.ethanhoward.countdown.data.BackgroundType
import me.ethanhoward.countdown.data.Event
import me.ethanhoward.countdown.data.WidgetConfig

data class PickableEvent(val event: Event, val thumbnail: Bitmap?)

data class EventPickerUiState(
    val loaded: Boolean = false,
    val events: List<PickableEvent> = emptyList(),
    /** The event this widget currently shows, when reconfiguring. */
    val currentEventId: Long? = null,
)

class EventPickerViewModel(application: Application, private val appWidgetId: Int) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val currentEventId = MutableStateFlow<Long?>(null)
    private val thumbnails = mutableMapOf<String, Bitmap?>()

    val state: StateFlow<EventPickerUiState> = combine(
        container.eventRepository.observeAll().map { events -> events.map { PickableEvent(it, thumbnail(it)) } }.flowOn(Dispatchers.IO),
        currentEventId,
    ) { events, current -> EventPickerUiState(loaded = true, events = events, currentEventId = current) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventPickerUiState())

    init {
        viewModelScope.launch { currentEventId.value = container.widgetConfigRepository.get(appWidgetId)?.eventId }
    }

    /** Links this widget to [eventId] and redraws it. */
    suspend fun choose(eventId: Long) {
        val app = getApplication<Application>()
        container.widgetConfigRepository.save(WidgetConfig(appWidgetId = appWidgetId, eventId = eventId))
        WidgetRefresher.refresh(app, GlanceAppWidgetManager(app).getGlanceIdBy(appWidgetId))
        WidgetRefresher.scheduleNext(app)
    }

    private fun thumbnail(event: Event): Bitmap? {
        val look = event.appearance
        if (look.backgroundType != BackgroundType.PHOTO) return null
        val path = look.imagePath ?: return null
        return thumbnails.getOrPut(path) {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 })
        }
    }
}
