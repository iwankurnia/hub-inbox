package com.iwankurnia.hub.ui.hub

import com.iwankurnia.hub.R
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeChoicesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jakarta")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun morning_offersAllFour() {
        // Sunday, 1 November 2026, 09:41.
        val choices = snoozeChoices(at(1, 9, 41), zone)

        assertEquals(
            listOf(R.string.snooze_in_one_hour, R.string.snooze_this_afternoon, R.string.snooze_tomorrow, R.string.snooze_monday),
            choices.map { it.labelRes },
        )
        assertEquals(listOf(at(1, 10, 41), at(1, 14), at(2, 8), at(2, 8)), choices.map { it.untilMs })
    }

    @Test
    fun afternoonOnMonday_dropsThisAfternoon_andMondayIsNextWeek() {
        val choices = snoozeChoices(at(2, 15, 0), zone)

        assertEquals(
            listOf(R.string.snooze_in_one_hour, R.string.snooze_tomorrow, R.string.snooze_monday),
            choices.map { it.labelRes },
        )
        assertEquals(at(9, 8), choices.last().untilMs)
    }
}
