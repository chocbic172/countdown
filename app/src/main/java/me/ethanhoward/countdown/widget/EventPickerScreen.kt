package me.ethanhoward.countdown.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import me.ethanhoward.countdown.data.BackgroundType
import me.ethanhoward.countdown.domain.CountdownCalculator
import me.ethanhoward.countdown.ui.events.dateFormatter
import me.ethanhoward.countdown.ui.events.timeFormatter
import me.ethanhoward.countdown.ui.events.usesDarkText

/** Shown when placing (or reconfiguring) a widget: the only choice is which event it shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventPickerScreen(
    viewModel: EventPickerViewModel,
    onPick: (Long) -> Unit,
    onNewEvent: () -> Unit,
    onEditEvent: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = Instant.now()
    val zone = ZoneId.systemDefault()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose an event") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                FilledTonalButton(
                    onClick = onNewEvent,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New event")
                }
            }
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        if (state.events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "You don't have any events yet.\nCreate one to show it on this widget.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                Text(
                    "The widget uses the event's look. To change it, edit the event.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(state.events, key = { it.event.id }) { item ->
                val event = item.event
                val selected = event.id == state.currentEventId
                val text = CountdownCalculator.format(CountdownCalculator.calculate(event.targetDate, event.targetTime, now, zone))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(event.id) }),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MiniWidget(item, text.number)
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(event.title, style = MaterialTheme.typography.titleMedium)
                            val date = event.targetDate.format(dateFormatter) +
                                (event.targetTime?.let { " · " + it.format(timeFormatter) } ?: "")
                            Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {onEditEvent(event.id)}) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit Event")
                        }
                        RadioButton(selected = selected, onClick = null)
                    }
                }
            }
        }
    }
}

/** A thumbnail of how the widget will look with this event. */
@Composable
private fun MiniWidget(item: PickableEvent, number: String) {
    val look = item.event.appearance
    val textColor = if (look.usesDarkText(look.backgroundLuminance)) Color(0xFF1B1B1F) else Color.White
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val thumb = item.thumbnail
        if (look.backgroundType == BackgroundType.PHOTO && thumb != null) {
            Image(thumb.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Color(look.backgroundColor).copy(alpha = look.opacity)))
        }
        Text(number, color = textColor, fontSize = if (number.length > 3) 14.sp else 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
