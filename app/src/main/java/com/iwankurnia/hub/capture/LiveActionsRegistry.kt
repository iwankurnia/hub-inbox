package com.iwankurnia.hub.capture

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** The reply, action and content intents of notifications still in the shade; the listener writes them here. */
@Singleton
class LiveActionsRegistry @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LiveActions {

    private class Live(
        val reply: Notification.Action?,
        val replyInput: RemoteInput?,
        val actions: List<Notification.Action>,
        val content: PendingIntent?,
    )

    private val live = ConcurrentHashMap<String, Live>()

    /** Set by the listener while it is connected; cancels the system notification with that key. */
    @Volatile
    var canceller: ((String) -> Unit)? = null

    fun register(sbn: StatusBarNotification) {
        val notification = sbn.notification
        // Some compat actions carry no intent; they could never be sent, so they are not offered.
        val all = notification.actions.orEmpty().filter { it.actionIntent != null }
        val reply = all.firstOrNull { action -> action.remoteInputs.orEmpty().any { it.allowFreeFormInput } }
        live[sbn.key] = Live(
            reply = reply,
            replyInput = reply?.remoteInputs?.first { it.allowFreeFormInput },
            actions = all.filter { it !== reply },
            content = notification.contentIntent,
        )
    }

    fun unregister(key: String) {
        live.remove(key)
    }

    override fun hasReply(notifKey: String): Boolean = live[notifKey]?.reply != null

    override fun reply(notifKey: String, text: String): Boolean {
        val entry = live[notifKey] ?: return false
        val action = entry.reply ?: return false
        val input = entry.replyInput ?: return false
        val intent = Intent()
        val results = Bundle().apply { putCharSequence(input.resultKey, text) }
        RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
        val pendingIntent = action.actionIntent ?: return false
        return send { pendingIntent.send(context, 0, intent) }
    }

    override fun actions(notifKey: String): List<LiveActions.Action> =
        live[notifKey]?.actions.orEmpty().mapNotNull { action ->
            val pendingIntent = action.actionIntent ?: return@mapNotNull null
            LiveActions.Action(title = action.title?.toString().orEmpty(), send = { send { pendingIntent.send() } })
        }

    override fun openContent(notifKey: String): Boolean {
        val content = live[notifKey]?.content ?: return false
        return send { content.send() }
    }

    override fun cancel(notifKey: String) {
        canceller?.invoke(notifKey)
    }

    /** False when the app has since cancelled the intent. */
    private inline fun send(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (_: PendingIntent.CanceledException) {
        false
    }
}
