package com.iwankurnia.hub.capture

/** One message from a `MessagingStyle` notification. */
data class CapturedMessage(val sender: String?, val text: String, val timestamp: Long)

/** What the listener read from a posted notification, ready for [com.iwankurnia.hub.data.HubRepository.capture]. */
data class CapturedNotification(
    val packageName: String,
    val notifKey: String,
    val conversationId: String?,
    val title: String,
    val text: String,
    val bigText: String?,
    val subText: String?,
    val postedAt: Long,
    val messages: List<CapturedMessage>, // empty for non-chat
    val hasReply: Boolean,
)

/** Outcome of a capture. */
sealed interface CaptureResult {
    data class Inserted(val ids: List<Long>) : CaptureResult
    data class Updated(val id: Long) : CaptureResult
    data object Ignored : CaptureResult
}
