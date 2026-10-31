package com.iwankurnia.hub.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-app settings; an app with no row is included in Hub. */
@Entity(tableName = "app_prefs")
data class AppPref(
    @PrimaryKey val packageName: String,
    val includeInHub: Boolean = true,
    val lastSeenAt: Long,
)
