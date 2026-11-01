package com.iwankurnia.hub.capture

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.StatusBarNotification

/** Decides which posted notifications never become Hub items (spec 4.1). */
class NotificationFilter(private val ownPackage: String) {

    /** Why a notification is left out of Hub, or [Drop.NONE] when it is captured. */
    fun dropReason(sbn: StatusBarNotification, channelImportance: Int?, hideSilent: Boolean): Drop {
        val notification = sbn.notification
        val flags = notification.flags
        return when {
            sbn.packageName == ownPackage -> Drop.OWN
            sbn.isOngoing || flags and Notification.FLAG_FOREGROUND_SERVICE != 0 -> Drop.ONGOING
            flags and Notification.FLAG_GROUP_SUMMARY != 0 -> Drop.SUMMARY
            notification.category == Notification.CATEGORY_TRANSPORT ||
                notification.category == Notification.CATEGORY_PROGRESS ||
                notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 -> Drop.MEDIA
            hideSilent && channelImportance != null && channelImportance <= NotificationManager.IMPORTANCE_LOW -> Drop.SILENT
            else -> Drop.NONE
        }
    }

    fun shouldDrop(sbn: StatusBarNotification, channelImportance: Int?, hideSilent: Boolean): Boolean =
        dropReason(sbn, channelImportance, hideSilent) != Drop.NONE

    enum class Drop {
        NONE, OWN, ONGOING, SUMMARY, MEDIA, SILENT;

        /** Dropped from Hub, but still something "Keep the status bar clear" should remove from the shade. */
        val clearable: Boolean get() = this == SUMMARY || this == SILENT
    }
}
