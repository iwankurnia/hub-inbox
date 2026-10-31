package com.iwankurnia.hub.ui.search

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_TIME = DateTimeFormatter.ofPattern("EEE HH:mm")
private val DATE = DateTimeFormatter.ofPattern("d MMM")

/** "HH:mm" today, "EEE HH:mm" within the last week, "d MMM" before that. */
fun timeLabel(atMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(atMs).atZone(zone)
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(at.toLocalDate(), today)
    return when {
        days == 0L -> TIME.format(at)
        days in 1..6 -> DAY_TIME.format(at)
        else -> DATE.format(at)
    }
}
