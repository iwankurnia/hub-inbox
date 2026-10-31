package com.iwankurnia.hub.capture

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person

/** Reads a posted notification into a [CapturedNotification] (spec 4.1). */
object NotificationMapper {

    fun map(sbn: StatusBarNotification, context: Context): CapturedNotification {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        return CapturedNotification(
            packageName = sbn.packageName,
            notifKey = sbn.key,
            conversationId = notification.shortcutId ?: sbn.key,
            title = title?.takeIf { it.isNotBlank() } ?: appLabel(context, sbn.packageName),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            postedAt = sbn.postTime,
            messages = messages(notification),
            hasReply = notification.actions.orEmpty().any { action ->
                action.remoteInputs.orEmpty().any { it.allowFreeFormInput }
            },
        )
    }

    /** One entry per non-blank message; the sender is null for the user's own messages and in one-to-one chats. */
    private fun messages(notification: Notification): List<CapturedMessage> {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            ?: return emptyList()
        val group = style.isGroupConversation
        return style.messages.mapNotNull { message ->
            val text = message.text?.toString()
            if (text.isNullOrBlank()) return@mapNotNull null
            val person = message.person
            val sender = if (!group || isUser(person, style.user)) null else person?.name?.toString()
            CapturedMessage(sender = sender, text = text, timestamp = message.timestamp)
        }
    }

    private fun isUser(person: Person?, user: Person): Boolean = when {
        person == null -> true
        person.key != null && user.key != null -> person.key == user.key
        else -> person.name?.toString() == user.name?.toString()
    }

    /** Title of last resort for notifications that carry none: the posting app's name. */
    private fun appLabel(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}
