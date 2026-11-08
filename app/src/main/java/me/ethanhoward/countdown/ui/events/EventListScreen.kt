package me.ethanhoward.countdown.ui.events

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LightMode
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import me.ethanhoward.countdown.domain.CountdownCalculator
import me.ethanhoward.countdown.ui.components.EventWithThumbnail
import me.ethanhoward.countdown.ui.components.MiniWidget
import me.ethanhoward.countdown.ui.theme.setAppDarkMode

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
        topBar = {
            TopAppBar(
                title = { Text("Countdown") },
                actions = {
                    val dark = isSystemInDarkTheme()
                    IconButton(onClick = { setAppDarkMode(context, !dark) }) {
                        Icon(
                            if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEvent) { Icon(Icons.Default.Add, contentDescription = "Add event") }
        },
    ) { padding ->
        val list = events
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp), contentAlignment = Alignment.Center) {
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
                items(list, key = { it.event.id }) { item ->
                    EventCard(item, now, onClick = { onEditEvent(item.event.id) })
                }
            }
        }
    }
}

@Composable
private fun EventCard(item: EventWithThumbnail, now: Instant, onClick: () -> Unit) {
    val event = item.event
    val text = CountdownCalculator.format(
        CountdownCalculator.calculate(event.targetDate, event.targetTime, now, ZoneId.systemDefault()),
    )
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniWidget(item, text.number)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                val date = event.targetDate.format(dateFormatter) +
                    (event.targetTime?.let { " · " + it.format(timeFormatter) } ?: "")
                Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onClick) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit event")
            }
        }
    }
}
