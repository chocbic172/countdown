package me.ethanhoward.countdown.data

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
)
