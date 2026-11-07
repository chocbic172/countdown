package me.ethanhoward.countdown.ui.events

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import me.ethanhoward.countdown.data.Event
import me.ethanhoward.countdown.domain.CountdownCalculator
import me.ethanhoward.countdown.widget.CountdownWidgetReceiver
import me.ethanhoward.countdown.widget.PinWidgetReceiver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    onAddEvent: () -> Unit,
    onEditEvent: (Long) -> Unit,
    viewModel: EventListViewModel = viewModel(factory = EventListViewModel.Factory),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Re-evaluate countdowns each minute while the screen is visible.
    val now by produceState(Instant.now()) {
        while (true) {
            delay(60_000)
            value = Instant.now()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Countdown") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEvent) { Icon(Icons.Default.Add, contentDescription = "Add event") }
        },
    ) { padding ->
        val list = events
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No events yet.\nTap + to add something to count down to.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(list, key = { it.id }) { event ->
                    EventCard(event, now, onClick = { onEditEvent(event.id) }, onAddWidget = { requestPinWidget(context, event) })
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: Event, now: Instant, onClick: () -> Unit, onAddWidget: () -> Unit) {
    val text = CountdownCalculator.format(
        CountdownCalculator.calculate(event.targetDate, event.targetTime, now, ZoneId.systemDefault()),
    )
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                val date = event.targetDate.format(dateFormatter) +
                    (event.targetTime?.let { " · " + it.format(timeFormatter) } ?: "")
                Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text.number, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (text.unit.isNotEmpty()) Text(text.unit, style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onAddWidget) {
                Icon(Icons.Outlined.Widgets, contentDescription = "Add widget to home screen")
            }
        }
    }
}

private fun requestPinWidget(context: Context, event: Event) {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) {
        Toast.makeText(context, "Long-press your home screen and choose Widgets to add a countdown", Toast.LENGTH_LONG).show()
        return
    }
    val callback = PendingIntent.getBroadcast(
        context,
        event.id.toInt(),
        Intent(context, PinWidgetReceiver::class.java).putExtra(PinWidgetReceiver.EXTRA_EVENT_ID, event.id),
        // Mutable so the system can add EXTRA_APPWIDGET_ID.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
    manager.requestPinAppWidget(ComponentName(context, CountdownWidgetReceiver::class.java), null, callback)
}
