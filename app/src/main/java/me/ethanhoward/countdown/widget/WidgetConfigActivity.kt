package me.ethanhoward.countdown.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import me.ethanhoward.countdown.ui.events.EditEventScreen
import me.ethanhoward.countdown.ui.theme.CountdownTheme

/**
 * Shown when a widget is placed (and when it is reconfigured via long-press, or tapped while
 * unconfigured). The user only chooses which event the widget shows; its look comes from the event.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // If the user backs out of the initial configuration, the launcher removes the widget.
        setResult(RESULT_CANCELED, result)

        enableEdgeToEdge()
        setContent {
            CountdownTheme {
                val picker: EventPickerViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { EventPickerViewModel(this[APPLICATION_KEY]!!, appWidgetId) }
                    },
                )
                var creatingEvent by rememberSaveable { mutableStateOf(false) }

                fun choose(eventId: Long) {
                    lifecycleScope.launch {
                        picker.choose(eventId)
                        setResult(RESULT_OK, result)
                        finish()
                    }
                }

                if (creatingEvent) {
                    BackHandler { creatingEvent = false }
                    EditEventScreen(onDone = { savedId -> if (savedId != null) choose(savedId) else creatingEvent = false })
                } else {
                    EventPickerScreen(
                        viewModel = picker,
                        onPick = ::choose,
                        onNewEvent = { creatingEvent = true },
                        onCancel = ::finish,
                    )
                }
            }
        }
    }
}
