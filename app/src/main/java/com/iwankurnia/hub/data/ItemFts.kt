package com.iwankurnia.hub.data

import androidx.room.Entity
import androidx.room.Fts4

/** Full-text index over [NotificationItem]; Room keeps it in step with the items table. */
@Fts4(contentEntity = NotificationItem::class)
@Entity(tableName = "items_fts")
data class ItemFts(
    val title: String,
    val text: String,
    val bigText: String?,
)
