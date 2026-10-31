package com.iwankurnia.hub.ui.hub

import androidx.paging.PagingData
import com.iwankurnia.hub.capture.CaptureResult
import com.iwankurnia.hub.capture.CapturedNotification
import com.iwankurnia.hub.data.AppCount
import com.iwankurnia.hub.data.AppPref
import com.iwankurnia.hub.data.HubFilter
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.data.HubTab
import com.iwankurnia.hub.data.ItemState
import com.iwankurnia.hub.data.NotificationItem
import com.iwankurnia.hub.data.ThreadEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** In-memory [HubRepository] for list tests; records the filters asked for and the calls made. */
class FakeHubRepository(initial: List<NotificationItem> = emptyList(), private val now: Long = 5_000L) : HubRepository {

    val store = MutableStateFlow(initial)
    val requestedFilters = mutableListOf<HubFilter>()
    val restored = mutableListOf<List<Long>>()
    val purged = mutableListOf<List<Long>>()
    var markAllReadFor: String? = "none"

    fun get(id: Long): NotificationItem = store.value.single { it.id == id }

    private fun update(ids: List<Long>, change: (NotificationItem) -> NotificationItem) {
        store.value = store.value.map { if (it.id in ids) change(it) else it }
    }

    private fun matches(item: NotificationItem, filter: HubFilter): Boolean {
        if (filter.packageName != null && item.packageName != filter.packageName) return false
        return when (filter.tab) {
            HubTab.ALL -> item.state == ItemState.ACTIVE
            HubTab.UNREAD -> item.state == ItemState.ACTIVE && !item.isRead && item.snoozedUntil == null
            HubTab.DELETED -> item.state == ItemState.DELETED
        }
    }

    override fun pagedItems(filter: HubFilter): Flow<PagingData<NotificationItem>> {
        requestedFilters += filter
        return flowOf(PagingData.from(store.value.filter { matches(it, filter) }.sortedByDescending { it.sortAt }))
    }

    override fun unreadCount(packageName: String?): Flow<Int> =
        store.map { items -> items.count { matches(it, HubFilter(HubTab.UNREAD, packageName)) } }

    override fun appCounts(): Flow<List<AppCount>> = store.map { items ->
        items.filter { it.state == ItemState.ACTIVE }.groupBy { it.packageName }.map { (pkg, list) ->
            AppCount(pkg, list.count { !it.isRead }, list.size, list.maxOf { it.sortAt })
        }.sortedByDescending { it.lastSeenAt }
    }

    override fun thread(item: NotificationItem, limit: Int): Flow<List<ThreadEntry>> = flowOf(emptyList())
    override suspend fun item(id: Long): NotificationItem? = store.value.firstOrNull { it.id == id }
    override fun itemFlow(id: Long): Flow<NotificationItem?> = store.map { items -> items.firstOrNull { it.id == id } }
    override suspend fun markRead(ids: List<Long>) = update(ids) { it.copy(isRead = true) }
    override suspend fun markUnread(ids: List<Long>) = update(ids) { it.copy(isRead = false) }
    override suspend fun markAllRead(packageName: String?) {
        markAllReadFor = packageName
        store.value = store.value.map { if (packageName == null || it.packageName == packageName) it.copy(isRead = true) else it }
    }

    override suspend fun markReadByKey(notifKey: String) = Unit
    override suspend fun delete(ids: List<Long>) =
        update(ids) { it.copy(state = ItemState.DELETED, deletedAt = now, snoozedUntil = null) }

    override suspend fun restore(ids: List<Long>) {
        restored += ids
        update(ids) { it.copy(state = ItemState.ACTIVE, deletedAt = null) }
    }

    override suspend fun purge(ids: List<Long>) {
        purged += ids
        store.value = store.value.filterNot { it.id in ids }
    }

    override suspend fun emptyDeleted() {
        store.value = store.value.filterNot { it.state == ItemState.DELETED }
    }

    override suspend fun purgeDeletedOlderThan(cutoffMs: Long) = Unit
    override suspend fun snooze(ids: List<Long>, untilMs: Long) = update(ids) { it.copy(snoozedUntil = untilMs, isRead = true) }
    override suspend fun unsnooze(ids: List<Long>) = update(ids) { it.copy(snoozedUntil = null) }
    override suspend fun wakeDue(nowMs: Long): List<NotificationItem> = emptyList()
    override suspend fun nextSnoozeAt(): Long? = null
    override fun search(query: String): Flow<List<NotificationItem>> = flowOf(emptyList())
    override suspend fun capture(c: CapturedNotification): CaptureResult = error("not used")
    override suspend fun recordSentReply(itemId: Long, text: String) = Unit
    override fun appPrefs(): Flow<List<AppPref>> = flowOf(emptyList())
    override suspend fun setAppIncluded(packageName: String, included: Boolean) = Unit
    override suspend fun isAppIncluded(packageName: String): Boolean = true

    companion object {
        fun item(
            id: Long,
            title: String = "Teguh",
            text: String = "PR #1616 sudah saya rebase",
            pkg: String = "com.whatsapp",
            sortAt: Long = 1_000L,
            isRead: Boolean = false,
            state: ItemState = ItemState.ACTIVE,
            snoozedUntil: Long? = null,
            deletedAt: Long? = null,
        ) = NotificationItem(
            id = id,
            packageName = pkg,
            notifKey = "k$id",
            conversationId = null,
            title = title,
            text = text,
            bigText = null,
            subText = null,
            sender = null,
            messageTimestamp = null,
            postedAt = sortAt,
            sortAt = sortAt,
            state = state,
            isRead = isRead,
            snoozedUntil = snoozedUntil,
            deletedAt = deletedAt,
        )
    }
}
