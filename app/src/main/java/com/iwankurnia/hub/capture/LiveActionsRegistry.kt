package com.iwankurnia.hub.capture

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** The content intents of notifications still in the shade; the listener writes them here. */
@Singleton
class LiveActionsRegistry @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LiveActions {

    private val content = ConcurrentHashMap<String, PendingIntent>()

    /** Set by the listener while it is connected; cancels the system notification with that key. */
    @Volatile
    var canceller: ((String) -> Unit)? = null

    fun register(sbn: StatusBarNotification) {
        val intent = sbn.notification.contentIntent
        if (intent != null) content[sbn.key] = intent else content.remove(sbn.key)
    }

    fun unregister(key: String) {
        content.remove(key)
    }

    /**
     * Sends the content intent. On Android 14+ a `PendingIntent` sent from the foreground may only start
     * the posting app's activity when the sender opts in, so the options allow it. False when there is
     * none or the app has since cancelled it.
     */
    override fun openContent(notifKey: String): Boolean {
        val pendingIntent = content[notifKey] ?: return false
        return try {
            pendingIntent.send(context, 0, null, null, null, null, sendOptions())
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        }
    }

    override fun cancel(notifKey: String) {
        canceller?.invoke(notifKey)
    }

    private fun sendOptions(): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
}
