package com.iwankurnia.hub.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A reply the user sent from Hub; shown in the thread and used to drop the app's echo of it. */
@Entity(tableName = "sent_replies")
data class SentReply(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val conversationId: String?,
    val text: String,
    val sentAt: Long,
)
