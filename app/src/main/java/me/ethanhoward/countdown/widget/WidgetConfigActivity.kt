package me.ethanhoward.countdown.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.ethanhoward.countdown.ui.theme.CountdownTheme

/**
 * Shown when a widget is placed (and when it is reconfigured via long-press, or tapped while
 * unconfigured). Lets the user pick the event and style the background.
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
                val vm: WidgetConfigViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { WidgetConfigViewModel(this[APPLICATION_KEY]!!, appWidgetId) }
                    },
                )
                WidgetConfigScreen(
                    viewModel = vm,
                    onDone = {
                        setResult(RESULT_OK, result)
                        finish()
                    },
                    onCancel = ::finish,
                )
            }
        }
    }
}
