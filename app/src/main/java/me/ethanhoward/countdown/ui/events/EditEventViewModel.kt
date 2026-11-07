package me.ethanhoward.countdown.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ethanhoward.countdown.appContainer
import me.ethanhoward.countdown.data.Event
import me.ethanhoward.countdown.data.EventRepository

data class EditEventUiState(
    val isNew: Boolean,
    val loaded: Boolean,
    val draft: EventDraft = EventDraft(),
    val finished: Boolean = false,
)

class EditEventViewModel(private val repository: EventRepository, private val eventId: Long?) : ViewModel() {
    private var original: Event? = null
    private val _state = MutableStateFlow(EditEventUiState(isNew = eventId == null, loaded = eventId == null))
    val state: StateFlow<EditEventUiState> = _state.asStateFlow()

    init {
        if (eventId != null) viewModelScope.launch {
            val event = repository.get(eventId)
            original = event
            _state.update {
                if (event == null) it.copy(finished = true)
                else it.copy(loaded = true, draft = EventDraft(event.title, event.targetDate, event.targetTime))
            }
        }
    }

    fun onChange(draft: EventDraft) = _state.update { it.copy(draft = draft) }

    fun save() {
        val draft = _state.value.draft
        if (!draft.isValid) return
        viewModelScope.launch {
            val base = original ?: Event(title = "", targetDate = draft.date)
            repository.save(base.copy(title = draft.title.trim(), targetDate = draft.date, targetTime = draft.time))
            _state.update { it.copy(finished = true) }
        }
    }

    fun delete() {
        val event = original ?: return
        viewModelScope.launch {
            repository.delete(event)
            _state.update { it.copy(finished = true) }
        }
    }

    companion object {
        const val ARG_EVENT_ID = "eventId"

        val Factory = viewModelFactory {
            initializer {
                val id = createSavedStateHandle().get<Long>(ARG_EVENT_ID)?.takeIf { it > 0 }
                EditEventViewModel(this[APPLICATION_KEY]!!.appContainer.eventRepository, id)
            }
        }
    }
}
