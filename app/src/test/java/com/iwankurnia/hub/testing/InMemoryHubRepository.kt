package com.iwankurnia.hub.testing

import androidx.paging.PagingData
import com.iwankurnia.hub.capture.CaptureResult
import com.iwankurnia.hub.capture.CapturedNotification
import com.iwankurnia.hub.data.AppCount
import com.iwankurnia.hub.data.AppPref
import com.iwankurnia.hub.data.HubFilter
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.data.ItemState
import com.iwankurnia.hub.data.NotificationItem
import com.iwankurnia.hub.data.SentReply
import com.iwankurnia.hub.data.ThreadEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A small in-memory [HubRepository] for Track C's view-model and receiver tests. */
class InMemoryHubRepository(private val clock: () -> Long = { 0L }) : HubRepository {

    val items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val replies = MutableStateFlow<List<SentReply>>(emptyList())
    val prefs = MutableStateFlow<List<AppPref>>(emptyList())
    val counts = MutableStateFlow<List<AppCount>>(emptyList())

    /** Every id list passed to [markRead], in order. */
    val markReadCalls = mutableListOf<List<Long>>()

    /** Every query passed to [search], in order. */
    val searches = mutableListOf<String>()

    fun add(vararg newItems: NotificationItem) = items.update { it + newItems }

    private fun change(ids: List<Long>, f: (NotificationItem) -> NotificationItem) =
        items.update { list -> list.map { if (it.id in ids) f(it) else it } }

    override fun pagedItems(filter: HubFilter): Flow<PagingData<NotificationItem>> = emptyFlow()

    override fun unreadCount(packageName: String?): Flow<Int> =
        items.map { list -> list.count { !it.isRead && it.state == ItemState.ACTIVE } }

    override fun appCounts(): Flow<List<AppCount>> = counts

    override fun thread(item: NotificationItem, limit: Int): Flow<List<ThreadEntry>> =
        combine(items, replies) { list, sent ->
            val incoming = list.filter {
                it.packageName == item.packageName &&
                    (if (item.conversationId != null) it.conversationId == item.conversationId else it.title == item.title)
            }.map { ThreadEntry.Incoming(it) to it.postedAt }
            val outgoing = sent.filter { it.itemId in list.map(NotificationItem::id) }
                .map { ThreadEntry.Outgoing(it) to it.sentAt }
            (incoming + outgoing).sortedBy { it.second }.map { it.first }.takeLast(limit)
        }

    override suspend fun item(id: Long): NotificationItem? = items.value.firstOrNull { it.id == id }

    override fun itemFlow(id: Long): Flow<NotificationItem?> = items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun markRead(ids: List<Long>) {
        markReadCalls += ids
        change(ids) { it.copy(isRead = true) }
    }

    override suspend fun markUnread(ids: List<Long>) = change(ids) { it.copy(isRead = false) }

    override suspend fun markAllRead(packageName: String?) =
        items.update { list -> list.map { if (packageName == null || it.packageName == packageName) it.copy(isRead = true) else it } }

    override suspend fun markReadByKey(notifKey: String) =
        items.update { list -> list.map { if (it.notifKey == notifKey) it.copy(isRead = true) else it } }

    override suspend fun delete(ids: List<Long>) =
        change(ids) { it.copy(state = ItemState.DELETED, deletedAt = clock(), snoozedUntil = null) }

    override suspend fun restore(ids: List<Long>) = change(ids) { it.copy(state = ItemState.ACTIVE, deletedAt = null) }

    override suspend fun purge(ids: List<Long>) = items.update { list -> list.filterNot { it.id in ids } }

    override suspend fun emptyDeleted() = items.update { list -> list.filterNot { it.state == ItemState.DELETED } }

    override suspend fun purgeDeletedOlderThan(cutoffMs: Long) =
        items.update { list -> list.filterNot { it.state == ItemState.DELETED && (it.deletedAt ?: 0) < cutoffMs } }

    override suspend fun snooze(ids: List<Long>, untilMs: Long) =
        change(ids) { it.copy(snoozedUntil = untilMs, isRead = true) }

    override suspend fun unsnooze(ids: List<Long>) = change(ids) { it.copy(snoozedUntil = null) }

    override suspend fun wakeDue(nowMs: Long): List<NotificationItem> {
        val due = items.value.filter { it.state == ItemState.ACTIVE && (it.snoozedUntil ?: Long.MAX_VALUE) <= nowMs }
        change(due.map { it.id }) { it.copy(snoozedUntil = null, isRead = false, sortAt = nowMs) }
        return due.map { it.copy(snoozedUntil = null, isRead = false, sortAt = nowMs) }
    }

    override suspend fun nextSnoozeAt(): Long? =
        items.value.filter { it.state == ItemState.ACTIVE }.mapNotNull { it.snoozedUntil }.minOrNull()

    override fun search(query: String): Flow<List<NotificationItem>> {
        searches += query
        return items.map { list ->
            list.filter { it.title.contains(query, ignoreCase = true) || it.text.contains(query, ignoreCase = true) }
                .sortedByDescending { it.sortAt }
        }
    }

    override suspend fun capture(c: CapturedNotification): CaptureResult = CaptureResult.Ignored

    override suspend fun recordSentReply(itemId: Long, text: String) {
        val item = item(itemId)
        replies.update { it + SentReply(it.size + 1L, itemId, item?.conversationId, text, clock()) }
    }

    override fun appPrefs(): Flow<List<AppPref>> = prefs

    override suspend fun setAppIncluded(packageName: String, included: Boolean) = prefs.update { list ->
        if (list.any { it.packageName == packageName }) {
            list.map { if (it.packageName == packageName) it.copy(includeInHub = included) else it }
        } else {
            list + AppPref(packageName, included, 0L)
        }
    }

    override suspend fun isAppIncluded(packageName: String): Boolean =
        prefs.value.firstOrNull { it.packageName == packageName }?.includeInHub ?: true

    companion object {
        /** A notification item with sensible defaults for tests. */
        fun item(
            id: Long,
            title: String = "Matthew",
            text: String = "Lunch?",
            packageName: String = "com.example.chat",
            notifKey: String = "key-$id",
            conversationId: String? = "conv-1",
            postedAt: Long = 0L,
            state: ItemState = ItemState.ACTIVE,
            isRead: Boolean = false,
            snoozedUntil: Long? = null,
        ) = NotificationItem(
            id = id,
            packageName = packageName,
            notifKey = notifKey,
            conversationId = conversationId,
            title = title,
            text = text,
            bigText = null,
            subText = null,
            sender = null,
            messageTimestamp = null,
            postedAt = postedAt,
            sortAt = postedAt,
            state = state,
            isRead = isRead,
            snoozedUntil = snoozedUntil,
        )
    }

    override suspend fun latestUnread(limit: Int): List<NotificationItem> =
        items.value.filter { it.state == ItemState.ACTIVE && !it.isRead && it.snoozedUntil == null }
            .sortedByDescending { it.sortAt }.take(limit)
}
