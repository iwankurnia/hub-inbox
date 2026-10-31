package com.iwankurnia.hub.ui.hub

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateBandsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jakarta")

    /** Saturday, 1 November 2026, 09:41 local time. */
    private val now = at(2026, 11, 1, 9, 41)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun today_yesterday_weekday_older() {
        assertEquals(DateBand("Today", "Sunday, 1 November"), DateBands.forInstant(at(2026, 11, 1, 0, 5), now, zone))
        assertEquals(DateBand("Yesterday", "Saturday, 31 October"), DateBands.forInstant(at(2026, 10, 31, 18, 31), now, zone))
        assertEquals(DateBand("Thursday", "29 October"), DateBands.forInstant(at(2026, 10, 29, 21, 14), now, zone))
        assertEquals(DateBand("Monday", "26 October"), DateBands.forInstant(at(2026, 10, 26, 8, 0), now, zone))
        assertEquals(DateBand("25 October", "2026"), DateBands.forInstant(at(2026, 10, 25, 23, 59), now, zone))
        assertEquals(DateBand("24 December", "2025"), DateBands.forInstant(at(2025, 12, 24, 12, 0), now, zone))
    }

    @Test
    fun sameBand_boundaryAtMidnight() {
        assertTrue(DateBands.sameBand(at(2026, 11, 1, 0, 0), at(2026, 11, 1, 9, 0), now, zone))
        assertFalse(DateBands.sameBand(at(2026, 11, 1, 0, 0), at(2026, 10, 31, 23, 59), now, zone))
        assertFalse(DateBands.sameBand(at(2026, 10, 31, 0, 0), at(2026, 10, 30, 23, 59), now, zone))
        assertTrue(DateBands.sameBand(at(2026, 10, 20, 0, 1), at(2026, 10, 20, 23, 59), now, zone))
        assertFalse(DateBands.sameBand(at(2026, 10, 20, 0, 0), at(2026, 10, 19, 23, 59), now, zone))
    }

    @Test
    fun rowTime_todayWeekOlder() {
        assertEquals("09:40", DateBands.rowTime(at(2026, 11, 1, 9, 40), now, zone))
        assertEquals("Sat 18:31", DateBands.rowTime(at(2026, 10, 31, 18, 31), now, zone))
        assertEquals("Mon 08:00", DateBands.rowTime(at(2026, 10, 26, 8, 0), now, zone))
        assertEquals("25 Oct", DateBands.rowTime(at(2026, 10, 25, 23, 59), now, zone))
    }
}
