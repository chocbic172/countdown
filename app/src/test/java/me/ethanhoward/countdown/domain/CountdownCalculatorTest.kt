package me.ethanhoward.countdown.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownCalculatorTest {

    private val london = ZoneId.of("Europe/London")
    private fun at(s: String, zone: ZoneId = london): Instant = LocalDateTime.parse(s).atZone(zone).toInstant()
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun allDayEventInFutureCountsCalendarDays() {
        assertEquals(CountdownValue.Days(10), CountdownCalculator.calculate(d("2026-12-25"), null, at("2026-12-15T23:59"), london))
        assertEquals(CountdownValue.Days(9), CountdownCalculator.calculate(d("2026-12-25"), null, at("2026-12-16T00:00"), london))
    }

    @Test fun allDayEventTomorrowIsOneDay() {
        assertEquals(CountdownValue.Days(1), CountdownCalculator.calculate(d("2026-12-25"), null, at("2026-12-24T20:00"), london))
    }

    @Test fun allDayEventToday() {
        assertEquals(CountdownValue.Today, CountdownCalculator.calculate(d("2026-12-25"), null, at("2026-12-25T08:00"), london))
    }

    @Test fun pastEvent() {
        assertEquals(CountdownValue.Past(1), CountdownCalculator.calculate(d("2026-12-25"), null, at("2026-12-26T00:01"), london))
        assertEquals(CountdownValue.Past(7), CountdownCalculator.calculate(d("2026-12-25"), null, at("2027-01-01T12:00"), london))
    }

    @Test fun timedEventSwitchesToHoursWithin24h() {
        val date = d("2026-12-25"); val time = LocalTime.of(18, 0)
        assertEquals(CountdownValue.Days(1), CountdownCalculator.calculate(date, time, at("2026-12-24T17:59"), london))
        assertEquals(CountdownValue.Hours(24), CountdownCalculator.calculate(date, time, at("2026-12-24T18:00"), london))
        assertEquals(CountdownValue.Hours(3), CountdownCalculator.calculate(date, time, at("2026-12-25T15:00"), london))
        assertEquals(CountdownValue.Hours(4), CountdownCalculator.calculate(date, time, at("2026-12-25T14:30"), london))
        assertEquals(CountdownValue.Hours(1), CountdownCalculator.calculate(date, time, at("2026-12-25T17:59"), london))
    }

    @Test fun timedEventAfterItsTimeIsTodayThenPast() {
        val date = d("2026-12-25"); val time = LocalTime.of(18, 0)
        assertEquals(CountdownValue.Today, CountdownCalculator.calculate(date, time, at("2026-12-25T18:00"), london))
        assertEquals(CountdownValue.Past(1), CountdownCalculator.calculate(date, time, at("2026-12-26T01:00"), london))
    }

    @Test fun dstSpringForwardDayStillCountsAsOneDay() {
        // UK clocks go forward on 2027-03-28; that day has only 23 hours.
        assertEquals(CountdownValue.Days(1), CountdownCalculator.calculate(d("2027-03-29"), null, at("2027-03-28T12:00"), london))
        assertEquals(CountdownValue.Days(2), CountdownCalculator.calculate(d("2027-03-29"), null, at("2027-03-27T23:30"), london))
    }

    @Test fun timeZoneDeterminesWhichDayItIs() {
        val now = Instant.parse("2026-12-24T23:30:00Z")
        assertEquals(CountdownValue.Days(1), CountdownCalculator.calculate(d("2026-12-25"), null, now, london))
        assertEquals(CountdownValue.Today, CountdownCalculator.calculate(d("2026-12-25"), null, now, ZoneId.of("Europe/Berlin")))
    }

    @Test fun nextChangeForAllDayEventIsNull() {
        assertNull(CountdownCalculator.nextChange(d("2026-12-25"), null, at("2026-12-20T10:00"), london))
    }

    @Test fun nextChangeEntersHoursModeThenTicksHourly() {
        val date = d("2026-12-25"); val time = LocalTime.of(18, 0)
        assertEquals(at("2026-12-24T18:00"), CountdownCalculator.nextChange(date, time, at("2026-12-20T10:00"), london))
        assertEquals(at("2026-12-25T15:00"), CountdownCalculator.nextChange(date, time, at("2026-12-25T14:30"), london))
        assertEquals(at("2026-12-25T16:00"), CountdownCalculator.nextChange(date, time, at("2026-12-25T15:00"), london))
        assertEquals(at("2026-12-25T18:00"), CountdownCalculator.nextChange(date, time, at("2026-12-25T17:10"), london))
        assertNull(CountdownCalculator.nextChange(date, time, at("2026-12-25T18:00"), london))
    }

    @Test fun nextMidnight() {
        assertEquals(at("2026-12-26T00:00"), CountdownCalculator.nextMidnight(at("2026-12-25T00:00"), london))
        assertEquals(at("2026-12-26T00:00"), CountdownCalculator.nextMidnight(at("2026-12-25T23:59"), london))
    }

    @Test fun formatting() {
        assertEquals(CountdownText("1", "day"), CountdownCalculator.format(CountdownValue.Days(1)))
        assertEquals(CountdownText("42", "days"), CountdownCalculator.format(CountdownValue.Days(42)))
        assertEquals(CountdownText("5", "hours"), CountdownCalculator.format(CountdownValue.Hours(5)))
        assertEquals(CountdownText("Today", ""), CountdownCalculator.format(CountdownValue.Today))
        assertEquals(CountdownText("3", "days ago"), CountdownCalculator.format(CountdownValue.Past(3)))
    }
}
