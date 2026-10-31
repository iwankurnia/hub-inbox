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

/** One row of a conversation thread in the detail sheet. */
sealed interface ThreadEntry {
    data class Incoming(val item: NotificationItem) : ThreadEntry
    data class Outgoing(val reply: SentReply) : ThreadEntry
}

/** The single data contract every screen, the listener and the widgets code against. */
interface HubRepository {
    /** ALL: ACTIVE; UNREAD: ACTIVE, unread and not snoozed; DELETED: DELETED. Newest `sortAt` first (DELETED by `deletedAt`). */
    fun pagedItems(filter: HubFilter): Flow<PagingData<NotificationItem>>
    fun unreadCount(packageName: String? = null): Flow<Int>

    /** ACTIVE items only, most recently seen app first. */
    fun appCounts(): Flow<List<AppCount>>

    /** Same app and conversation (else same title), ACTIVE or DELETED, oldest first, interleaved with sent replies by time. */
    fun thread(item: NotificationItem, limit: Int = 30): Flow<List<ThreadEntry>>
    suspend fun item(id: Long): NotificationItem?
    fun itemFlow(id: Long): Flow<NotificationItem?>
    suspend fun markRead(ids: List<Long>)
    suspend fun markUnread(ids: List<Long>)
    suspend fun markAllRead(packageName: String?)
    suspend fun markReadByKey(notifKey: String)

    /** state = DELETED, deletedAt = now. */
    suspend fun delete(ids: List<Long>)

    /** state = ACTIVE, deletedAt = null. */
    suspend fun restore(ids: List<Long>)

    /** Hard delete. */
    suspend fun purge(ids: List<Long>)
    suspend fun emptyDeleted()
    suspend fun purgeDeletedOlderThan(cutoffMs: Long)

    /** snoozedUntil = until, isRead = true. */
    suspend fun snooze(ids: List<Long>, untilMs: Long)
    suspend fun unsnooze(ids: List<Long>)

    /** Items with snoozedUntil <= now become unread with sortAt = now and snoozedUntil cleared; returns them. */
    suspend fun wakeDue(nowMs: Long): List<NotificationItem>
    suspend fun nextSnoozeAt(): Long?

    /** Full-text prefix match, newest first, at most 200, every state. */
    fun search(query: String): Flow<List<NotificationItem>>
    suspend fun capture(c: CapturedNotification): CaptureResult
    suspend fun recordSentReply(itemId: Long, text: String)
    fun appPrefs(): Flow<List<AppPref>>
    suspend fun setAppIncluded(packageName: String, included: Boolean)

    /** True when the app has no row yet. */
    suspend fun isAppIncluded(packageName: String): Boolean
}
