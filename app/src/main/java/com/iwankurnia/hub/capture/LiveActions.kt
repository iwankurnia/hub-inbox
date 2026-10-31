package com.iwankurnia.hub.capture

/** Content intents held by the listener for notifications still in the shade, and a way to clear them. */
interface LiveActions {
    /** Fires the content `PendingIntent`; false when the notification has gone or never had one. */
    fun openContent(notifKey: String): Boolean
    fun cancel(notifKey: String)
}

/** Used until the capture track supplies the listener's live actions: nothing is ever available. */
object NoLiveActions : LiveActions {
    override fun openContent(notifKey: String): Boolean = false
    override fun cancel(notifKey: String) = Unit
}
