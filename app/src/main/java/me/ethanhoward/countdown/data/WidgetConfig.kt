package me.ethanhoward.countdown.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Which event a placed widget shows, keyed by the system's appWidgetId. Styling lives on [Event]. */
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
)
