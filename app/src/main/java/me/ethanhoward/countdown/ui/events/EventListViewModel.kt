package me.ethanhoward.countdown.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import me.ethanhoward.countdown.appContainer
import me.ethanhoward.countdown.data.Event
import me.ethanhoward.countdown.data.EventRepository

class EventListViewModel(repository: EventRepository) : ViewModel() {
    /** Null until the first load completes. */
    val events: StateFlow<List<Event>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = viewModelFactory {
            initializer { EventListViewModel(this[APPLICATION_KEY]!!.appContainer.eventRepository) }
        }
    }
}
