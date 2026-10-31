package com.iwankurnia.hub.snooze

import android.content.Context
import com.iwankurnia.hub.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** The fixed snooze times of spec 4.6. */
object SnoozeTimes {

    /** One picker entry: [label] such as "Tomorrow", [detail] such as "Thu 08:00", and the wake-up time. */
    data class Option(val label: String, val detail: String, val atMs: Long)

    /** The four labels, passed in so the times can be tested without resources. */
    data class Labels(val inOneHour: String, val thisAfternoon: String, val tomorrow: String, val monday: String) {
        companion object {
            fun from(context: Context) = Labels(
                inOneHour = context.getString(R.string.snooze_in_one_hour),
                thisAfternoon = context.getString(R.string.snooze_this_afternoon),
                tomorrow = context.getString(R.string.snooze_tomorrow),
                monday = context.getString(R.string.snooze_monday),
            )
        }
    }

    private val MORNING: LocalTime = LocalTime.of(8, 0)
    private val AFTERNOON: LocalTime = LocalTime.of(14, 0)
    private val AFTERNOON_CUTOFF: LocalTime = LocalTime.of(13, 0)

    /**
     * In 1 hour; this afternoon (14:00 today, or tomorrow once it is 13:00); tomorrow 08:00; Monday 08:00
     * (today when it is Monday before 08:00). [Option.detail] is "HH:mm" for today, else "EEE HH:mm".
     */
    fun options(nowMs: Long, zone: ZoneId, labels: Labels, locale: Locale = Locale.getDefault()): List<Option> {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val today = now.toLocalDate()

        val inOneHour = now.plusHours(1)
        val afternoonDay = if (now.toLocalTime() < AFTERNOON_CUTOFF) today else today.plusDays(1)
        val afternoon = afternoonDay.atTime(AFTERNOON).atZone(zone)
        val tomorrow = today.plusDays(1).atTime(MORNING).atZone(zone)
        val mondayDay = if (now.dayOfWeek == DayOfWeek.MONDAY && now.toLocalTime() < MORNING) {
            today
        } else {
            today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        }
        val monday = mondayDay.atTime(MORNING).atZone(zone)

        val timeOnly = DateTimeFormatter.ofPattern("HH:mm", locale)
        val dayAndTime = DateTimeFormatter.ofPattern("EEE HH:mm", locale)
        fun option(label: String, at: ZonedDateTime) = Option(
            label = label,
            detail = (if (at.toLocalDate() == today) timeOnly else dayAndTime).format(at),
            atMs = at.toInstant().toEpochMilli(),
        )

        return listOf(
            option(labels.inOneHour, inOneHour),
            option(labels.thisAfternoon, afternoon),
            option(labels.tomorrow, tomorrow),
            option(labels.monday, monday),
        )
    }

    /** A custom time at or before [nowMs] (say, earlier today) would wake at once; it becomes a minute from now. */
    fun clampSnoozeTime(pickedMs: Long, nowMs: Long): Long = if (pickedMs <= nowMs) nowMs + MIN_SNOOZE_MS else pickedMs

    private const val MIN_SNOOZE_MS = 60_000L
}
