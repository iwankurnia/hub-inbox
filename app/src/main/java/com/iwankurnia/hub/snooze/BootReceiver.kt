package com.iwankurnia.hub.snooze

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Alarms do not survive a reboot: sets the next due snooze again. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: SnoozeScheduler

    override fun onReceive(context: Context, intent: Intent) {
        runAsync { handle(intent) }
    }

    internal suspend fun handle(intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) scheduler.reschedule()
    }
}
