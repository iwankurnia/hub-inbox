package com.iwankurnia.hub.capture

/** Live reply, action and content intents held by the listener for notifications still in the shade. */
interface LiveActions {
    data class Action(val title: String, val send: () -> Boolean)

    fun hasReply(notifKey: String): Boolean
    fun reply(notifKey: String, text: String): Boolean
    fun actions(notifKey: String): List<Action>

    /** Fires the content `PendingIntent`. */
    fun openContent(notifKey: String): Boolean
    fun cancel(notifKey: String)
}

/** Used until the capture track supplies the listener's live actions: nothing is ever available. */
object NoLiveActions : LiveActions {
    override fun hasReply(notifKey: String): Boolean = false
    override fun reply(notifKey: String, text: String): Boolean = false
    override fun actions(notifKey: String): List<LiveActions.Action> = emptyList()
    override fun openContent(notifKey: String): Boolean = false
    override fun cancel(notifKey: String) = Unit
}
