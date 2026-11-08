package me.ethanhoward.countdown.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ethanhoward.countdown.data.BackgroundType
import me.ethanhoward.countdown.data.Event
import me.ethanhoward.countdown.ui.events.usesDarkText

data class EventWithThumbnail(val event: Event, val thumbnail: Bitmap?)

/** Decodes and caches the photo thumbnails drawn by [MiniWidget]. Not thread-safe; call [load] from one flow. */
class EventThumbnails {
    private val cache = mutableMapOf<String, Bitmap?>()

    /** Reads from disk on a cache miss, so call this off the main thread. */
    fun load(events: List<Event>): List<EventWithThumbnail> {
        // Drop bitmaps for photos that have since been replaced or deleted.
        cache.keys.retainAll(events.mapNotNull { it.appearance.imagePath }.toSet())
        return events.map { EventWithThumbnail(it, thumbnail(it)) }
    }

    private fun thumbnail(event: Event): Bitmap? {
        val look = event.appearance
        if (look.backgroundType != BackgroundType.PHOTO) return null
        val path = look.imagePath ?: return null
        return cache.getOrPut(path) {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 })
        }
    }
}

/** A thumbnail of how the widget will look with this event. */
@Composable
fun MiniWidget(item: EventWithThumbnail, number: String) {
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
