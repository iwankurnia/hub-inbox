package com.iwankurnia.hub.ui.hub

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A grey date band in the stream: [label] on the left, [detail] on the right. */
data class DateBand(val label: String, val detail: String)

/**
 * Date bands and row times for the Hub list. Each calendar day is its own band: "Today" and
 * "Yesterday" with the full date, weekday names for the rest of the last week, then plain dates
 * with the year on the right. Future instants count as today.
 */
object DateBands {

    /** Days before today that still get a weekday name rather than a date. */
    private const val WEEK_DAYS = 6L

    fun forInstant(
        epochMs: Long,
        nowMs: Long,
        zone: ZoneId,
        locale: Locale = Locale.UK,
        todayLabel: String = "Today",
        yesterdayLabel: String = "Yesterday",
    ): DateBand {
        val date = bandDate(epochMs, nowMs, zone)
        val daysAgo = daysAgo(date, nowMs, zone)
        val fullDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)
        val dayMonth = DateTimeFormatter.ofPattern("d MMMM", locale)
        return when {
            daysAgo == 0L -> DateBand(todayLabel, date.format(fullDate))
            daysAgo == 1L -> DateBand(yesterdayLabel, date.format(fullDate))
            daysAgo <= WEEK_DAYS -> DateBand(date.format(DateTimeFormatter.ofPattern("EEEE", locale)), date.format(dayMonth))
            else -> DateBand(date.format(dayMonth), date.year.toString())
        }
    }

    /** True when [a] and [b] fall under the same band header. */
    fun sameBand(a: Long, b: Long, nowMs: Long, zone: ZoneId): Boolean =
        bandDate(a, nowMs, zone) == bandDate(b, nowMs, zone)

    /** Row time: `HH:mm` today, `EEE HH:mm` within the last six days, `d MMM` older. */
    fun rowTime(epochMs: Long, nowMs: Long, zone: ZoneId, locale: Locale = Locale.UK): String {
        val date = bandDate(epochMs, nowMs, zone)
        val pattern = when (daysAgo(date, nowMs, zone)) {
            0L -> "HH:mm"
            in 1L..WEEK_DAYS -> "EEE HH:mm"
            else -> "d MMM"
        }
        return format(epochMs, zone, pattern, locale)
    }

    /** The "until …" text on a snoozed row: `HH:mm` today, `EEE HH:mm` within six days, else `d MMM HH:mm`. */
    fun untilTime(epochMs: Long, nowMs: Long, zone: ZoneId, locale: Locale = Locale.UK): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        val daysAhead = ChronoUnit.DAYS.between(today(nowMs, zone), date)
        val pattern = when {
            daysAhead <= 0L -> "HH:mm"
            daysAhead <= WEEK_DAYS -> "EEE HH:mm"
            else -> "d MMM HH:mm"
        }
        return format(epochMs, zone, pattern, locale)
    }

    private fun format(epochMs: Long, zone: ZoneId, pattern: String, locale: Locale): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale))

    private fun today(nowMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

    private fun bandDate(epochMs: Long, nowMs: Long, zone: ZoneId): LocalDate {
        val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        val today = today(nowMs, zone)
        return if (date.isAfter(today)) today else date
    }

    private fun daysAgo(date: LocalDate, nowMs: Long, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(date, today(nowMs, zone))
}
