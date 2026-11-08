package me.ethanhoward.countdown.ui.events

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
import me.ethanhoward.countdown.data.BackgroundType
import me.ethanhoward.countdown.data.EventAppearance
import me.ethanhoward.countdown.data.TextColorMode
import me.ethanhoward.countdown.domain.ColorMath
import me.ethanhoward.countdown.domain.CountdownCalculator

private val Swatches = listOf(
    0xFF3F51B5, 0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFFF9A825, 0xFFFB8C00,
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF6D4C41, 0xFF212121, 0xFFFAFAFA,
).map { it.toInt() }

private val LightText = Color.White
private val DarkText = Color(0xFF1B1B1F)

/** Callbacks for [AppearanceEditor]; implemented by the edit-event view model. */
interface AppearanceActions {
    fun setBackgroundType(type: BackgroundType)
    fun setColor(color: Int)
    fun setOpacity(value: Float)
    fun setBlur(value: Float)
    fun setContrast(value: Float)
    fun setTextColorMode(mode: TextColorMode)
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.padding(top = 4.dp, bottom = 12.dp))
        content()
    }
}

/** Whether text over this appearance should be dark, given the photo's measured luminance. */
fun EventAppearance.usesDarkText(photoLuminance: Float): Boolean {
    val luminance = if (backgroundType == BackgroundType.PHOTO) photoLuminance else ColorMath.luminance(backgroundColor)
    return ColorMath.useDarkText(textColorMode, luminance)
}

/**
 * Approximates the home-screen widget over a stand-in wallpaper so opacity is visible.
 * [photo] is the already-adjusted photo (blur/contrast/opacity baked in), if any.
 */
@Composable
fun WidgetPreview(draft: EventDraft, look: EventAppearance, photo: Bitmap?, photoLuminance: Float, loading: Boolean) {
    val textColor = if (look.usesDarkText(photoLuminance)) DarkText else LightText
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.padding(24.dp).fillMaxWidth().aspectRatio(2.2f).clip(RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                look.backgroundType == BackgroundType.PHOTO && photo != null ->
                    Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                look.backgroundType == BackgroundType.COLOR ->
                    Box(Modifier.fillMaxSize().background(Color(look.backgroundColor).copy(alpha = look.opacity)))
                else -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            }
            if (loading) {
                CircularProgressIndicator()
            } else {
                val text = CountdownCalculator.format(
                    CountdownCalculator.calculate(draft.date, draft.time, Instant.now(), ZoneId.systemDefault()),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Text(text.number, color = textColor, fontSize = 48.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        if (text.unit.isNotEmpty()) Text(text.unit, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            draft.title.ifBlank { "Event name" },
                            color = textColor,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceEditor(
    look: EventAppearance,
    hasPhoto: Boolean,
    loadingPhoto: Boolean,
    actions: AppearanceActions,
    onPickPhoto: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Section("Widget background") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BackgroundType.entries.forEachIndexed { i, type ->
                    SegmentedButton(
                        selected = look.backgroundType == type,
                        onClick = { actions.setBackgroundType(type) },
                        shape = SegmentedButtonDefaults.itemShape(i, BackgroundType.entries.size),
                        label = {
                            Text(if (type == BackgroundType.COLOR) "Solid colour" else "Photo")
                        },
                        icon = {
                            if (type == BackgroundType.COLOR) {
                                Icon(Icons.Outlined.FormatPaint, contentDescription = "Colour")
                            } else {
                                Icon(Icons.Outlined.Image, contentDescription = "Photo")
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when (look.backgroundType) {
                BackgroundType.COLOR -> ColorPicker(look.backgroundColor, actions::setColor)
                BackgroundType.PHOTO -> {
                    OutlinedButton(onClick = onPickPhoto, enabled = !loadingPhoto, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (hasPhoto) "Change photo" else "Choose photo")
                    }
                    if (hasPhoto) {
                        LabeledSlider("Blur", look.blur, 0f..EventAppearance.MAX_BLUR, actions::setBlur) { "${it.roundToInt()}" }
                        LabeledSlider("Contrast", look.contrast, EventAppearance.MIN_CONTRAST..EventAppearance.MAX_CONTRAST, actions::setContrast) {
                            "${(it * 100).roundToInt()}%"
                        }
                    }
                }
            }
            LabeledSlider("Opacity", look.opacity, 0f..1f, actions::setOpacity) { "${(it * 100).roundToInt()}%" }
        }

        Section("Text color") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TextColorMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = look.textColorMode == mode,
                        onClick = { actions.setTextColorMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, TextColorMode.entries.size),
                    ) { Text(mode.name.lowercase().replaceFirstChar(Char::titlecase)) }
                }
            }
        }
    }
}

@Composable
private fun ColorPicker(color: Int, onColor: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Swatches.forEach { swatch ->
            val selected = swatch == color
            Box(
                Modifier
                    .size(44.dp)
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
