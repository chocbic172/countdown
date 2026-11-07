package me.ethanhoward.countdown.widget

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
import me.ethanhoward.countdown.data.BackgroundType
import me.ethanhoward.countdown.data.TextColorMode
import me.ethanhoward.countdown.data.WidgetConfig
import me.ethanhoward.countdown.domain.ColorMath
import me.ethanhoward.countdown.domain.CountdownCalculator
import me.ethanhoward.countdown.ui.events.EventDraft
import me.ethanhoward.countdown.ui.events.EventForm

private val Swatches = listOf(
    0xFF3F51B5, 0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFFF9A825, 0xFFFB8C00,
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF6D4C41, 0xFF212121, 0xFFFAFAFA,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(viewModel: WidgetConfigViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showNewEvent by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onPhotoPicked)
    }

    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget settings") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) {
                    if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save widget")
                }
            }
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WidgetPreview(state)

            Section("Event") {
                EventPicker(state, viewModel::selectEvent, onNewEvent = { showNewEvent = true })
            }

            Section("Background") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    BackgroundType.entries.forEachIndexed { i, type ->
                        SegmentedButton(
                            selected = state.backgroundType == type,
                            onClick = { viewModel.setBackgroundType(type) },
                            shape = SegmentedButtonDefaults.itemShape(i, BackgroundType.entries.size),
                        ) { Text(if (type == BackgroundType.COLOR) "Solid color" else "Photo") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                when (state.backgroundType) {
                    BackgroundType.COLOR -> ColorPicker(state.color, viewModel::setColor)
                    BackgroundType.PHOTO -> {
                        OutlinedButton(
                            onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !state.loadingPhoto,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.hasPhoto) "Change photo" else "Choose photo")
                        }
                        if (state.hasPhoto) {
                            LabeledSlider("Blur", state.blur, 0f..WidgetConfig.MAX_BLUR, viewModel::setBlur) { "${it.roundToInt()}" }
                            LabeledSlider("Contrast", state.contrast, WidgetConfig.MIN_CONTRAST..WidgetConfig.MAX_CONTRAST, viewModel::setContrast) {
                                "${(it * 100).roundToInt()}%"
                            }
                        }
                    }
                }
                LabeledSlider("Opacity", state.opacity, 0f..1f, viewModel::setOpacity) { "${(it * 100).roundToInt()}%" }
            }

            Section("Text color") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TextColorMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = state.textColorMode == mode,
                            onClick = { viewModel.setTextColorMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, TextColorMode.entries.size),
                        ) { Text(mode.name.lowercase().replaceFirstChar(Char::titlecase)) }
                    }
                }
            }
        }
    }

    if (showNewEvent) NewEventDialog(
        onDismiss = { showNewEvent = false },
        onCreate = { viewModel.createEvent(it); showNewEvent = false },
    )

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
            text = { Text(message) },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 12.dp))
        content()
    }
}

/** Approximates the home-screen widget over a stand-in wallpaper so opacity is visible. */
@Composable
private fun WidgetPreview(state: WidgetConfigUiState) {
    val event = state.events.firstOrNull { it.id == state.eventId }
    val dark = ColorMath.useDarkText(state.textColorMode, state.backgroundLuminance)
    val textColor = if (dark) Color(0xFF1B1B1F) else Color.White
    val wallpaper = Brush.linearGradient(listOf(Color(0xFF5C6BC0), Color(0xFFEC407A), Color(0xFFFFCA28)))

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(wallpaper).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val photo = state.photoPreview
            if (state.backgroundType == BackgroundType.PHOTO && photo != null) {
                Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (state.backgroundType == BackgroundType.COLOR) {
                Box(Modifier.fillMaxSize().background(Color(state.color).copy(alpha = state.opacity)))
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            }
            if (state.loadingPhoto) {
                CircularProgressIndicator()
            } else if (event != null) {
                val text = CountdownCalculator.format(
                    CountdownCalculator.calculate(event.targetDate, event.targetTime, Instant.now(), ZoneId.systemDefault()),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Text(text.number, color = textColor, fontSize = 48.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        if (text.unit.isNotEmpty()) Text(text.unit, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(event.title, color = textColor, fontSize = 13.sp, maxLines = 2)
                    }
                }
            } else {
                Text("Choose an event", color = textColor, textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventPicker(state: WidgetConfigUiState, onSelect: (Long) -> Unit, onNewEvent: () -> Unit) {
    if (state.events.isEmpty()) {
        Text("You don't have any events yet.", style = MaterialTheme.typography.bodyMedium)
    } else {
        var expanded by remember { mutableStateOf(false) }
        val selected = state.events.firstOrNull { it.id == state.eventId }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected?.title ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Count down to") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.events.forEach { event ->
                    DropdownMenuItem(
                        text = { Text(event.title) },
                        onClick = { onSelect(event.id); expanded = false },
                    )
                }
            }
        }
    }
    TextButton(onClick = onNewEvent) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text("New event")
    }
}

@Composable
private fun ColorPicker(color: Int, onColor: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Swatches.forEach { swatch ->
            val selected = swatch == color
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(swatch))
                    .border(BorderStroke(if (selected) 3.dp else 1.dp, MaterialTheme.colorScheme.outline), CircleShape)
                    .clickable { onColor(swatch) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = if (ColorMath.luminance(swatch) > 0.179f) Color.Black else Color.White,
                    )
                }
            }
        }
    }
    var hex by remember(color) { mutableStateOf(ColorMath.toHex(color)) }
    OutlinedTextField(
        value = hex,
        onValueChange = { text ->
            hex = text.take(7)
            ColorMath.parseHex(hex)?.let(onColor)
        },
        label = { Text("Custom color (hex)") },
        prefix = { Text("#") },
        singleLine = true,
        isError = ColorMath.parseHex(hex) == null,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    format: (Float) -> String,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(format(value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun NewEventDialog(onDismiss: () -> Unit, onCreate: (EventDraft) -> Unit) {
    var draft by remember { mutableStateOf(EventDraft()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New event") },
        text = { EventForm(draft, { draft = it }) },
        confirmButton = { TextButton(onClick = { onCreate(draft) }, enabled = draft.isValid) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
