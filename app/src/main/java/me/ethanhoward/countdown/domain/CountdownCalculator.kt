package me.ethanhoward.countdown.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** What a countdown should display at a given moment. */
sealed interface CountdownValue {
    /** [days] whole calendar days remain (>= 1). */
    data class Days(val days: Long) : CountdownValue
    /** A timed event is less than 24 hours away; [hours] is rounded up (1..24). */
    data class Hours(val hours: Long) : CountdownValue
    /** The event is today (all-day event, or a timed event whose time has passed today). */
    data object Today : CountdownValue
    /** The event was [days] calendar days ago (>= 1). */
    data class Past(val days: Long) : CountdownValue
}

/** Display text split so widgets can render the number large and the unit small. */
data class CountdownText(val number: String, val unit: String)

object CountdownCalculator {

    private val HOUR: Duration = Duration.ofHours(1)
    private val DAY: Duration = Duration.ofHours(24)

    fun calculate(date: LocalDate, time: LocalTime?, now: Instant, zone: ZoneId): CountdownValue {
        if (time != null) {
            val remaining = Duration.between(now, ZonedDateTime.of(date, time, zone).toInstant())
            if (!remaining.isNegative && !remaining.isZero && remaining <= DAY) {
                return CountdownValue.Hours(ceilHours(remaining))
            }
        }
        val today = now.atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days > 0 -> CountdownValue.Days(days)
            days == 0L -> CountdownValue.Today
            else -> CountdownValue.Past(-days)
        }
    }

    /**
     * The next instant after [now] at which [calculate] may return a different value for this event,
     * or null if it will never change again except at midnight (which callers handle separately).
     */
    fun nextChange(date: LocalDate, time: LocalTime?, now: Instant, zone: ZoneId): Instant? {
        if (time == null) return null
        val target = ZonedDateTime.of(date, time, zone).toInstant()
        val remaining = Duration.between(now, target)
        return when {
            remaining.isNegative || remaining.isZero -> null
            // Enters "hours" mode 24h before the event.
            remaining > DAY -> target.minus(DAY)
            // In hours mode the rounded-up value drops each time remaining crosses a whole hour.
            else -> target.minus(HOUR.multipliedBy(ceilHours(remaining) - 1))
        }
    }

    /** Next local midnight after [now]; day counts change then. */
    fun nextMidnight(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()

    fun format(value: CountdownValue): CountdownText = when (value) {
        is CountdownValue.Days -> CountdownText(value.days.toString(), if (value.days == 1L) "day" else "days")
        is CountdownValue.Hours -> CountdownText(value.hours.toString(), if (value.hours == 1L) "hour" else "hours")
        CountdownValue.Today -> CountdownText("Today", "")
        is CountdownValue.Past -> CountdownText(value.days.toString(), if (value.days == 1L) "day ago" else "days ago")
    }

    private fun ceilHours(d: Duration): Long {
        val hours = d.toHours()
        return if (d == HOUR.multipliedBy(hours)) hours else hours + 1
    }
}
