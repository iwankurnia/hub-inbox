package com.iwankurnia.hub.testing

import com.iwankurnia.hub.capture.LiveActions

/** [LiveActions] whose answer to [openContent] is set by the test and whose calls are recorded in order. */
class RecordingLiveActions(var opens: Boolean = true) : LiveActions {
    val cancelled = mutableListOf<String>()
    val opened = mutableListOf<String>()

    /** Every call as "cancel:key" or "open:key", in the order made. */
    val calls = mutableListOf<String>()

    override fun openContent(notifKey: String): Boolean {
        opened += notifKey
        calls += "open:$notifKey"
        return opens
    }

    override fun cancel(notifKey: String) {
        cancelled += notifKey
        calls += "cancel:$notifKey"
    }
}
