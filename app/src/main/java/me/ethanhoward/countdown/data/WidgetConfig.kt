package me.ethanhoward.countdown.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BackgroundType { COLOR, PHOTO }

enum class TextColorMode { AUTO, LIGHT, DARK }

/** Per-widget settings, keyed by the system's appWidgetId. */
@Entity(
    tableName = "widget_config",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("eventId")],
)
data class WidgetConfig(
    @PrimaryKey val appWidgetId: Int,
    val eventId: Long?,
    val backgroundType: BackgroundType = BackgroundType.COLOR,
    /** ARGB color used when [backgroundType] is COLOR. Alpha is ignored; see [opacity]. */
    val backgroundColor: Int = DEFAULT_COLOR,
    /** 0f (transparent) .. 1f (opaque). */
    val opacity: Float = 1f,
    /** Blur radius in px applied to photos, 0 .. [MAX_BLUR]. */
    val blur: Float = 0f,
    /** Contrast multiplier applied to photos, [MIN_CONTRAST] .. [MAX_CONTRAST]; 1 = unchanged. */
    val contrast: Float = 1f,
    /** Absolute path of the processed background image (PHOTO only). */
    val imagePath: String? = null,
    /** Average relative luminance of the final background, 0..1, used for AUTO text color. */
    val backgroundLuminance: Float = 0f,
    val textColorMode: TextColorMode = TextColorMode.AUTO,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val DEFAULT_COLOR: Int = 0xFF3F51B5.toInt()
        const val MAX_BLUR = 25f
        const val MIN_CONTRAST = 0.25f
        const val MAX_CONTRAST = 1.5f
    }
}
