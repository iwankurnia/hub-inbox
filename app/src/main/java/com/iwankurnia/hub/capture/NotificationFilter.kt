package com.iwankurnia.hub.capture

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.StatusBarNotification

/** Decides which posted notifications never become Hub items (spec 4.1). */
class NotificationFilter(private val ownPackage: String) {

    fun shouldDrop(sbn: StatusBarNotification, channelImportance: Int?, hideSilent: Boolean): Boolean {
        val notification = sbn.notification
        val flags = notification.flags
        return sbn.packageName == ownPackage ||
            sbn.isOngoing ||
            flags and Notification.FLAG_FOREGROUND_SERVICE != 0 ||
            flags and Notification.FLAG_GROUP_SUMMARY != 0 ||
            notification.category == Notification.CATEGORY_TRANSPORT ||
            notification.category == Notification.CATEGORY_PROGRESS ||
            notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
            (hideSilent && channelImportance != null && channelImportance <= NotificationManager.IMPORTANCE_LOW)
    }
}
