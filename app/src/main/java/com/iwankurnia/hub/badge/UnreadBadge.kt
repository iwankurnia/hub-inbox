package com.iwankurnia.hub.badge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.iwankurnia.hub.HubIntents
import com.iwankurnia.hub.R
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.data.HubRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The unread count on the launcher icon (spec 4.12). Launchers badge an app from its own notifications,
 * so Hub keeps one silent notification showing the count while anything is unread, and cancels it at
 * zero or when the setting is off. The listener's own-package filter keeps it out of Hub's list.
 */
@Singleton
class UnreadBadge internal constructor(
    private val context: Context,
    private val repository: HubRepository,
    private val enabled: Flow<Boolean>,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(@ApplicationContext context: Context, repository: HubRepository, prefs: HubPrefs) :
        this(context, repository, prefs.unreadBadge, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private val started = AtomicBoolean(false)

    /** Starts following the count; later calls do nothing, so the app and the listener may both call it. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(enabled, repository.unreadCount()) { on, unread -> if (on) unread else 0 }
                .distinctUntilChanged()
                .collect(::show)
        }
    }

    private fun show(unread: Int) {
        val manager = NotificationManagerCompat.from(context)
        if (unread <= 0) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!canPost(manager)) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.badge_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(true) },
        )
        val open = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            HubIntents.open(context, tab = "UNREAD"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hub)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.resources.getQuantityString(R.plurals.badge_unread_count, unread, unread))
            .setNumber(unread)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post: no badge, nothing else to do.
        }
    }

    private fun canPost(manager: NotificationManagerCompat): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && manager.areNotificationsEnabled()
    }

    companion object {
        const val CHANNEL_ID = "unread"
        const val NOTIFICATION_ID = 1
        private const val REQUEST_CODE = 1
    }
}
