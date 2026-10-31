package com.iwankurnia.hub.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HubDao {

    // Lists

    @Query(
        "SELECT * FROM items WHERE state = 'ACTIVE' AND (:pkg IS NULL OR packageName = :pkg) " +
            "ORDER BY sortAt DESC, id DESC",
    )
    fun pagingAll(pkg: String?): PagingSource<Int, NotificationItem>

    @Query(
        "SELECT * FROM items WHERE state = 'ACTIVE' AND isRead = 0 " +
            "AND (:pkg IS NULL OR packageName = :pkg) ORDER BY sortAt DESC, id DESC",
    )
    fun pagingUnread(pkg: String?): PagingSource<Int, NotificationItem>

    @Query(
        "SELECT * FROM items WHERE state = 'DELETED' AND (:pkg IS NULL OR packageName = :pkg) " +
            "ORDER BY deletedAt DESC, id DESC",
    )
    fun pagingDeleted(pkg: String?): PagingSource<Int, NotificationItem>

    @Query(
        "SELECT COUNT(*) FROM items WHERE state = 'ACTIVE' AND isRead = 0 " +
            "AND (:pkg IS NULL OR packageName = :pkg)",
    )
    fun unreadCount(pkg: String?): Flow<Int>

    @Query(
        "SELECT packageName, " +
            "SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END) AS unread, " +
            "COUNT(*) AS total, MAX(postedAt) AS lastSeenAt " +
            "FROM items WHERE state = 'ACTIVE' GROUP BY packageName ORDER BY lastSeenAt DESC",
    )
    fun appCounts(): Flow<List<AppCount>>

    // Single items

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun item(id: Long): NotificationItem?

    @Query("SELECT DISTINCT notifKey FROM items WHERE id IN (:ids)")
    suspend fun notifKeys(ids: List<Long>): List<String>

    @Query(
        "SELECT DISTINCT notifKey FROM items WHERE state = 'ACTIVE' AND isRead = 0 " +
            "AND (:pkg IS NULL OR packageName = :pkg)",
    )
    suspend fun unreadNotifKeys(pkg: String?): List<String>

    // Read state

    @Query("UPDATE items SET isRead = 1 WHERE id IN (:ids)")
    suspend fun markRead(ids: List<Long>)

    @Query("UPDATE items SET isRead = 0 WHERE id IN (:ids)")
    suspend fun markUnread(ids: List<Long>)

    @Query(
        "UPDATE items SET isRead = 1 WHERE state = 'ACTIVE' AND isRead = 0 " +
            "AND (:pkg IS NULL OR packageName = :pkg)",
    )
    suspend fun markAllRead(pkg: String?)

    @Query("UPDATE items SET isRead = 1 WHERE state = 'ACTIVE' AND isRead = 0 AND notifKey = :notifKey")
    suspend fun markReadByKey(notifKey: String)

    // Deleted bin

    @Query("UPDATE items SET state = 'DELETED', deletedAt = :now WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>, now: Long)

    @Query("UPDATE items SET state = 'ACTIVE', deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>)

    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun purge(ids: List<Long>)

    @Query("DELETE FROM items WHERE state = 'DELETED'")
    suspend fun emptyDeleted()

    @Query("DELETE FROM items WHERE state = 'DELETED' AND deletedAt < :cutoffMs")
    suspend fun purgeDeletedOlderThan(cutoffMs: Long)

    // Search

    @Query(
        "SELECT items.* FROM items JOIN items_fts ON items.id = items_fts.rowid " +
            "WHERE items_fts MATCH :match ORDER BY items.sortAt DESC, items.id DESC LIMIT 200",
    )
    fun search(match: String): Flow<List<NotificationItem>>

    // Capture

    @Insert
    suspend fun insert(item: NotificationItem): Long

    @Update
    suspend fun update(item: NotificationItem)

    @Query("SELECT * FROM items WHERE notifKey = :notifKey ORDER BY id DESC LIMIT 1")
    suspend fun newestForKey(notifKey: String): NotificationItem?

    @Query(
        "SELECT COUNT(*) > 0 FROM items WHERE notifKey = :notifKey AND sender IS :sender " +
            "AND text = :text AND messageTimestamp = :messageTimestamp",
    )
    suspend fun messageExists(notifKey: String, sender: String?, text: String, messageTimestamp: Long): Boolean

    // App preferences

    @Query("SELECT * FROM app_prefs ORDER BY packageName")
    fun appPrefs(): Flow<List<AppPref>>

    @Query("SELECT * FROM app_prefs WHERE packageName = :pkg")
    suspend fun appPref(pkg: String): AppPref?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppPrefIfAbsent(pref: AppPref)

    @Query("UPDATE app_prefs SET lastSeenAt = MAX(lastSeenAt, :seenAt) WHERE packageName = :pkg")
    suspend fun touchAppPref(pkg: String, seenAt: Long)

    @Query("UPDATE app_prefs SET includeInHub = :included WHERE packageName = :pkg")
    suspend fun setAppIncluded(pkg: String, included: Boolean)

    // Widgets

    @Query(
        "SELECT * FROM items WHERE state = 'ACTIVE' AND isRead = 0 ORDER BY sortAt DESC, id DESC LIMIT :limit",
    )
    suspend fun latestUnread(limit: Int): List<NotificationItem>
}
