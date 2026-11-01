package com.iwankurnia.hub.capture

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The content intents of notifications the listener has seen; the listener writes them here. An entry
 * goes when its notification leaves the shade (unless Hub cleared it for the shade option), and at most
 * [MAX_ENTRIES] are kept, least recently used dropped first, so cleared ones cannot pile up.
 */
@Singleton
class LiveActionsRegistry @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LiveActions {

    private val content = object : LinkedHashMap<String, PendingIntent>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PendingIntent>?) = size > MAX_ENTRIES
    }

    /** Set by the listener while it is connected; cancels the system notification with that key. */
    @Volatile
    var canceller: ((String) -> Unit)? = null

    fun register(sbn: StatusBarNotification) {
        val intent = sbn.notification.contentIntent
        synchronized(content) { if (intent != null) content[sbn.key] = intent else content.remove(sbn.key) }
    }

    fun unregister(key: String) {
        synchronized(content) { content.remove(key) }
    }

    /**
     * Sends the content intent. On Android 14+ a `PendingIntent` sent from the foreground may only start
     * the posting app's activity when the sender opts in, so the options allow it. False when there is
     * none or the app has since cancelled it.
     */
    override fun openContent(notifKey: String): Boolean {
        val pendingIntent = synchronized(content) { content[notifKey] } ?: return false
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

    companion object {
        const val MAX_ENTRIES = 500
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
