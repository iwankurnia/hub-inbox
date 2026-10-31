package com.iwankurnia.hub.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Whether an item is in the stream or in the Deleted bin. */
enum class ItemState { ACTIVE, DELETED }

/** One captured notification, or one message of a chat notification. */
@Entity(
    tableName = "items",
    indices = [Index("notifKey"), Index("packageName"), Index("sortAt"), Index("conversationId")],
)
data class NotificationItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val notifKey: String,
    val conversationId: String?, // shortcutId, else notifKey
    val title: String, // conversation or sender name
    val text: String, // preview / message text
    val bigText: String?,
    val subText: String?,
    val sender: String?, // author of a group-chat message, null otherwise
    val messageTimestamp: Long?, // MessagingStyle message time, null for non-chat
    val postedAt: Long,
    val sortAt: Long, // == postedAt until a snooze wakes it
    val state: ItemState = ItemState.ACTIVE,
    val isRead: Boolean = false,
    val snoozedUntil: Long? = null,
    val deletedAt: Long? = null,
    val hasReply: Boolean = false,
)
