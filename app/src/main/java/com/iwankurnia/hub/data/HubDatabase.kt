package com.iwankurnia.hub.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [NotificationItem::class, AppPref::class, ItemFts::class],
    version = 2,
    exportSchema = true,
)
abstract class HubDatabase : RoomDatabase() {
    abstract fun hubDao(): HubDao
}
