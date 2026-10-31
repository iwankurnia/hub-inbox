package com.iwankurnia.hub.data

import androidx.paging.PagingData
import com.iwankurnia.hub.capture.CaptureResult
import com.iwankurnia.hub.capture.CapturedNotification
import kotlinx.coroutines.flow.Flow

/** The three list tabs. */
enum class HubTab { ALL, UNREAD, DELETED }

/** Per-app counts for the account rail and the app-count widget. */
data class AppCount(val packageName: String, val unread: Int, val total: Int, val lastSeenAt: Long)

/** Which tab, optionally narrowed to one app. */
data class HubFilter(val tab: HubTab = HubTab.ALL, val packageName: String? = null)

/** The single data contract every screen, the listener and the widgets code against. */
interface HubRepository {
    /** ALL: ACTIVE; UNREAD: ACTIVE and unread; DELETED: DELETED. Newest `sortAt` first (DELETED by `deletedAt`). */
    fun pagedItems(filter: HubFilter): Flow<PagingData<NotificationItem>>
    fun unreadCount(packageName: String? = null): Flow<Int>

    /** ACTIVE items only, most recently seen app first. */
    fun appCounts(): Flow<List<AppCount>>

    suspend fun item(id: Long): NotificationItem?
    suspend fun markRead(ids: List<Long>)
    suspend fun markUnread(ids: List<Long>)
    suspend fun markAllRead(packageName: String?)
    suspend fun markReadByKey(notifKey: String)

    /** The distinct notification keys of these items, in any state; used to clear their system notifications. */
    suspend fun notifKeys(ids: List<Long>): List<String>

    /** The distinct notification keys of ACTIVE unread items, optionally for one app; what [markAllRead] will touch. */
    suspend fun unreadNotifKeys(packageName: String?): List<String>

    /** state = DELETED, deletedAt = now. */
    suspend fun delete(ids: List<Long>)

    /** state = ACTIVE, deletedAt = null. */
    suspend fun restore(ids: List<Long>)

    /** Hard delete. */
    suspend fun purge(ids: List<Long>)
    suspend fun emptyDeleted()
    suspend fun purgeDeletedOlderThan(cutoffMs: Long)

    /** Full-text prefix match, newest first, at most 200, every state. */
    fun search(query: String): Flow<List<NotificationItem>>
    suspend fun capture(c: CapturedNotification): CaptureResult
    fun appPrefs(): Flow<List<AppPref>>
    suspend fun setAppIncluded(packageName: String, included: Boolean)

    /** True when the app has no row yet. */
    suspend fun isAppIncluded(packageName: String): Boolean

    /** The newest [limit] unread items (ACTIVE and unread), newest `sortAt` first; for the list widget. */
    suspend fun latestUnread(limit: Int): List<NotificationItem>
}
