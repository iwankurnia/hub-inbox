package com.iwankurnia.hub.snooze

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.iwankurnia.hub.HubIntents
import com.iwankurnia.hub.R
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.data.NotificationItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Fires when a snooze ends: wakes due items, tells the user, and sets the next alarm (spec 4.6). */
@AndroidEntryPoint
class SnoozeWakeReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: HubRepository

    @Inject
    lateinit var prefs: HubPrefs

    @Inject
    lateinit var scheduler: SnoozeScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        runAsync { wake(appContext, repository, prefs, scheduler, System.currentTimeMillis()) }
    }

    companion object {
        const val CHANNEL_ID = "snooze"

        /** Wakes every item due at [nowMs], posts one notification each when enabled, then reschedules. */
        internal suspend fun wake(
            context: Context,
            repository: HubRepository,
            prefs: HubPrefs,
            scheduler: SnoozeScheduler,
            nowMs: Long,
        ) {
            val woken = repository.wakeDue(nowMs)
            if (woken.isNotEmpty() && prefs.snoozeNotify.first()) {
                woken.forEach { post(context, it) }
            }
            scheduler.reschedule()
        }

        private fun post(context: Context, item: NotificationItem) {
            val manager = NotificationManagerCompat.from(context)
            if (!canPost(context, manager)) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.snooze_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.snooze_channel_description) },
            )
            val contentIntent = PendingIntent.getActivity(
                context,
                item.id.toInt(),
                HubIntents.open(context, itemId = item.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_hub)
                .setContentTitle(context.getString(R.string.snooze_notification_title, item.title))
                .setContentText(item.text)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()
            try {
                manager.notify(item.id.toInt(), notification)
            } catch (_: SecurityException) {
                // Permission withdrawn between the check and the post; the item is still woken.
            }
        }

        private fun canPost(context: Context, manager: NotificationManagerCompat): Boolean {
            val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            return permitted && manager.areNotificationsEnabled()
        }
    }
}
