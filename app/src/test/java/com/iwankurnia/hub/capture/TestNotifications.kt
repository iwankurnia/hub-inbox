package com.iwankurnia.hub.capture

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

/** Builders for real notifications wrapped the way the system hands them to a listener. */
internal object TestNotifications {
    const val PKG = "com.example.chat"
    const val CHANNEL = "test"

    fun builder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)

    fun sbn(
        notification: Notification,
        pkg: String = PKG,
        id: Int = 1,
        tag: String? = null,
        postTime: Long = 1_000L,
    ): StatusBarNotification =
        @Suppress("DEPRECATION")
        StatusBarNotification(pkg, pkg, id, tag, 10_001, 0, 0, notification, Process.myUserHandle(), postTime)

    fun pendingIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(context, action.hashCode(), Intent(action), PendingIntent.FLAG_MUTABLE)

    fun replyAction(context: Context): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, "Reply", pendingIntent(context, "reply"))
            .addRemoteInput(RemoteInput.Builder("reply_text").setLabel("Reply").build())
            .build()

    fun plainAction(context: Context, title: String): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, title, pendingIntent(context, title)).build()
}
