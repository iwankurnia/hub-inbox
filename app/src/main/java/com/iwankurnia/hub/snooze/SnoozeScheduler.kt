package com.iwankurnia.hub.snooze

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.iwankurnia.hub.data.HubRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one alarm set for the earliest snooze. Call [reschedule] after every snooze, unsnooze, delete
 * and wake-up. Uses an exact alarm when allowed, otherwise an inexact one (spec 4.6).
 */
@Singleton
class SnoozeScheduler internal constructor(
    private val context: Context,
    private val repository: HubRepository,
    private val setExact: (AlarmManager, Long, PendingIntent) -> Unit,
) {
    @Inject
    constructor(@ApplicationContext context: Context, repository: HubRepository) :
        this(context, repository, { manager, atMs, operation ->
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, operation)
        })

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun reschedule() {
        val next = repository.nextSnoozeAt()
        val operation = wakeIntent()
        when {
            next == null -> alarmManager.cancel(operation)
            canScheduleExact() -> try {
                setExact(alarmManager, next, operation)
            } catch (_: SecurityException) {
                // Exact-alarm permission revoked between the check and the call.
                setInexact(next, operation)
            }
            else -> setInexact(next, operation)
        }
    }

    private fun setInexact(atMs: Long, operation: PendingIntent) =
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, operation)

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun wakeIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, SnoozeWakeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 1
    }
}
