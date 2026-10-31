package com.iwankurnia.hub.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [NotificationItem::class, SentReply::class, AppPref::class, ItemFts::class],
    version = 1,
    exportSchema = true,
)
abstract class HubDatabase : RoomDatabase() {
    abstract fun hubDao(): HubDao
}
