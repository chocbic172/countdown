package me.ethanhoward.countdown.widget

import android.app.Application
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
import me.ethanhoward.countdown.data.WidgetConfig
import me.ethanhoward.countdown.ui.components.EventThumbnails
import me.ethanhoward.countdown.ui.components.EventWithThumbnail

data class EventPickerUiState(
    val loaded: Boolean = false,
    val events: List<EventWithThumbnail> = emptyList(),
    /** The event this widget currently shows, when reconfiguring. */
    val currentEventId: Long? = null,
)

class EventPickerViewModel(application: Application, private val appWidgetId: Int) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val currentEventId = MutableStateFlow<Long?>(null)
    private val thumbnails = EventThumbnails()

    val state: StateFlow<EventPickerUiState> = combine(
        container.eventRepository.observeAll().map(thumbnails::load).flowOn(Dispatchers.IO),
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
}
