package com.iwankurnia.hub.capture

import androidx.paging.PagingData
import com.iwankurnia.hub.data.AppCount
import com.iwankurnia.hub.data.AppPref
import com.iwankurnia.hub.data.HubFilter
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.data.NotificationItem
import com.iwankurnia.hub.data.ThreadEntry
import kotlinx.coroutines.flow.Flow
import java.util.Collections

/** Records what the listener asks of the repository; everything the listener never calls fails loudly. */
internal class FakeHubRepository : HubRepository {
    val captured: MutableList<CapturedNotification> = Collections.synchronizedList(mutableListOf())
    val markedReadKeys: MutableList<String> = Collections.synchronizedList(mutableListOf())
    /** Every capture and mark-read, in the order the listener made them. */
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    var excludedPackages: Set<String> = emptySet()

    override suspend fun capture(c: CapturedNotification): CaptureResult {
        captured += c
        calls += "capture:${c.notifKey}"
        return CaptureResult.Inserted(listOf(captured.size.toLong()))
    }

    override suspend fun markReadByKey(notifKey: String) {
        markedReadKeys += notifKey
        calls += "markRead:$notifKey"
    }

    override suspend fun isAppIncluded(packageName: String): Boolean = packageName !in excludedPackages

    override fun pagedItems(filter: HubFilter): Flow<PagingData<NotificationItem>> = TODO()
    override fun unreadCount(packageName: String?): Flow<Int> = TODO()
    override fun appCounts(): Flow<List<AppCount>> = TODO()
    override fun thread(item: NotificationItem, limit: Int): Flow<List<ThreadEntry>> = TODO()
    override suspend fun item(id: Long): NotificationItem? = TODO()
    override fun itemFlow(id: Long): Flow<NotificationItem?> = TODO()
    override suspend fun markRead(ids: List<Long>) = TODO()
    override suspend fun markUnread(ids: List<Long>) = TODO()
    override suspend fun markAllRead(packageName: String?) = TODO()
    override suspend fun notifKeys(ids: List<Long>): List<String> = TODO()
    override suspend fun unreadNotifKeys(packageName: String?): List<String> = TODO()
    override suspend fun delete(ids: List<Long>) = TODO()
    override suspend fun restore(ids: List<Long>) = TODO()
    override suspend fun purge(ids: List<Long>) = TODO()
    override suspend fun emptyDeleted() = TODO()
    override suspend fun purgeDeletedOlderThan(cutoffMs: Long) = TODO()
    override suspend fun latestUnread(limit: Int): List<NotificationItem> = TODO()
    override suspend fun snooze(ids: List<Long>, untilMs: Long) = TODO()
    override suspend fun unsnooze(ids: List<Long>) = TODO()
    override suspend fun wakeDue(nowMs: Long): List<NotificationItem> = TODO()
    override suspend fun nextSnoozeAt(): Long? = TODO()
    override fun search(query: String): Flow<List<NotificationItem>> = TODO()
    override suspend fun recordSentReply(itemId: Long, text: String) = TODO()
    override fun appPrefs(): Flow<List<AppPref>> = TODO()
    override suspend fun setAppIncluded(packageName: String, included: Boolean) = TODO()
}
