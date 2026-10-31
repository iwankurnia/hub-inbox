package com.iwankurnia.hub.snooze

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class SnoozeTimesTest {

    private val zone = ZoneId.of("Europe/London")
    private val labels = SnoozeTimes.Labels(
        inOneHour = "In 1 hour",
        thisAfternoon = "This afternoon",
        tomorrow = "Tomorrow",
        monday = "Monday",
    )

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun options(now: String) = SnoozeTimes.options(at(now), zone, labels, Locale.UK)

    @Test
    fun options_fiveFixedTimes() {
        // Wednesday morning. The fifth picker entry, "Pick date & time", is not a fixed time.
        val options = options("2026-11-04T09:30")

        assertEquals(
            listOf(
                SnoozeTimes.Option("In 1 hour", "10:30", at("2026-11-04T10:30")),
                SnoozeTimes.Option("This afternoon", "14:00", at("2026-11-04T14:00")),
                SnoozeTimes.Option("Tomorrow", "Thu 08:00", at("2026-11-05T08:00")),
                SnoozeTimes.Option("Monday", "Mon 08:00", at("2026-11-09T08:00")),
            ),
            options,
        )
    }

    @Test
    fun thisAfternoon_rollsToTomorrowAfter13() {
        val afternoon = options("2026-11-04T13:00")[1]

        assertEquals(SnoozeTimes.Option("This afternoon", "Thu 14:00", at("2026-11-05T14:00")), afternoon)
    }

    @Test
    fun monday_nextMonday() {
        assertEquals(at("2026-11-09T08:00"), options("2026-11-07T22:00")[3].atMs) // Saturday
        assertEquals(SnoozeTimes.Option("Monday", "08:00", at("2026-11-09T08:00")), options("2026-11-09T07:00")[3])
        assertEquals(at("2026-11-16T08:00"), options("2026-11-09T08:00")[3].atMs) // Monday, already 08:00
    }

    @Test
    fun inOneHour_acrossMidnightShowsDay() {
        assertEquals(SnoozeTimes.Option("In 1 hour", "Thu 00:15", at("2026-11-05T00:15")), options("2026-11-04T23:15")[0])
    }
}
