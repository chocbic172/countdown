package me.ethanhoward.countdown.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/**
 * An event the user is counting down to. Dates and times are "floating": they are interpreted in
 * the device's current time zone, so "Christmas, 25 Dec" stays on 25 Dec when travelling.
 */
@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetDate: LocalDate,
    /** Optional time of day. When null the event is treated as an all-day event. */
    val targetTime: LocalTime? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** How every widget showing this event looks. */
    @Embedded val appearance: EventAppearance = EventAppearance(),
)

enum class BackgroundType { COLOR, PHOTO }

enum class TextColorMode { AUTO, LIGHT, DARK }

/**
 * Widget styling, stored with the event. Column defaults must match [Migrations.MIGRATION_1_2].
 */
data class EventAppearance(
    @ColumnInfo(defaultValue = "'COLOR'")
    val backgroundType: BackgroundType = BackgroundType.COLOR,
    /** Opaque ARGB color used when [backgroundType] is COLOR; see [opacity] for transparency. */
    @ColumnInfo(defaultValue = "-12627531")
    val backgroundColor: Int = DEFAULT_COLOR,
    /** 0f (transparent) .. 1f (opaque). */
    @ColumnInfo(defaultValue = "1")
    val opacity: Float = 1f,
    /** Blur radius applied to photos, 0 .. [MAX_BLUR] (px at a 1024 px image). */
    @ColumnInfo(defaultValue = "0")
    val blur: Float = 0f,
    /** Contrast multiplier applied to photos, [MIN_CONTRAST] .. [MAX_CONTRAST]; 1 = unchanged. */
    @ColumnInfo(defaultValue = "1")
    val contrast: Float = 1f,
    /** Absolute path of the processed, ready-to-draw background (PHOTO only). */
    val imagePath: String? = null,
    /** Absolute path of the downscaled original, so adjustments can be re-applied later. */
    val sourceImagePath: String? = null,
    /** Average relative luminance of the final background, 0..1, used for AUTO text color. */
    @ColumnInfo(defaultValue = "0")
    val backgroundLuminance: Float = 0f,
    @ColumnInfo(defaultValue = "'AUTO'")
    val textColorMode: TextColorMode = TextColorMode.AUTO,
    /** Bumped whenever the appearance is saved so widgets reload the image. */
    @ColumnInfo(defaultValue = "0")
    val appearanceUpdatedAt: Long = 0,
) {
    companion object {
        const val DEFAULT_COLOR: Int = -12627531 // 0xFF3F51B5
        const val MAX_BLUR = 25f
        const val MIN_CONTRAST = 0.5f
        const val MAX_CONTRAST = 2f
    }
}
