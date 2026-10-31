package com.iwankurnia.hub.testing

import com.iwankurnia.hub.capture.LiveActions

/** [LiveActions] whose answers are set by the test and whose calls are recorded. */
class RecordingLiveActions(
    var replyable: Boolean = true,
    var replySucceeds: Boolean = true,
    var actionTitles: List<String> = emptyList(),
    var opens: Boolean = true,
) : LiveActions {
    val replies = mutableListOf<Pair<String, String>>()
    val cancelled = mutableListOf<String>()
    val opened = mutableListOf<String>()
    val fired = mutableListOf<String>()

    override fun hasReply(notifKey: String): Boolean = replyable

    override fun reply(notifKey: String, text: String): Boolean {
        replies += notifKey to text
        return replySucceeds
    }

    override fun actions(notifKey: String): List<LiveActions.Action> =
        actionTitles.map { title -> LiveActions.Action(title) { fired += title; true } }

    override fun openContent(notifKey: String): Boolean {
        opened += notifKey
        return opens
    }

    override fun cancel(notifKey: String) {
        cancelled += notifKey
    }
}
