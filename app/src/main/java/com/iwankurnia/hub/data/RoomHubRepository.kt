package com.iwankurnia.hub.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.iwankurnia.hub.capture.CaptureResult
import com.iwankurnia.hub.capture.CapturedNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** [HubRepository] over Room; applies the capture rules of spec 4.1. */
@Singleton
class RoomHubRepository(
    private val db: HubDatabase,
    private val widgetRefresher: WidgetRefresher,
    private val clock: () -> Long,
) : HubRepository {

    @Inject
    constructor(db: HubDatabase, widgetRefresher: WidgetRefresher) :
        this(db, widgetRefresher, System::currentTimeMillis)

    private val dao = db.hubDao()

    override fun pagedItems(filter: HubFilter): Flow<PagingData<NotificationItem>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE)) {
            when (filter.tab) {
                HubTab.ALL -> dao.pagingAll(filter.packageNames.isEmpty(), filter.packageNames.toList())
                HubTab.UNREAD -> dao.pagingUnread(filter.packageNames.isEmpty(), filter.packageNames.toList())
                HubTab.DELETED -> dao.pagingDeleted(filter.packageNames.isEmpty(), filter.packageNames.toList())
            }
        }.flow

    override fun unreadCount(packageNames: Set<String>): Flow<Int> =
        dao.unreadCount(packageNames.isEmpty(), packageNames.toList())

    override fun appCounts(): Flow<List<AppCount>> = dao.appCounts()

    override suspend fun item(id: Long): NotificationItem? = dao.item(id)

    override suspend fun markRead(ids: List<Long>) = mutate { dao.markRead(ids) }

    override suspend fun markUnread(ids: List<Long>) = mutate { dao.markUnread(ids) }

    override suspend fun markAllRead(packageNames: Set<String>) =
        mutate { dao.markAllRead(packageNames.isEmpty(), packageNames.toList()) }

    override suspend fun markReadByKey(notifKey: String) = mutate { dao.markReadByKey(notifKey) }

    override suspend fun notifKeys(ids: List<Long>): List<String> =
        if (ids.isEmpty()) emptyList() else dao.notifKeys(ids)

    override suspend fun unreadNotifKeys(packageNames: Set<String>): List<String> =
        dao.unreadNotifKeys(packageNames.isEmpty(), packageNames.toList())

    override suspend fun delete(ids: List<Long>) = mutate { dao.delete(ids, clock()) }

    override suspend fun restore(ids: List<Long>) = mutate { dao.restore(ids) }

    override suspend fun purge(ids: List<Long>) = mutate { dao.purge(ids) }

    override suspend fun emptyDeleted() = mutate { dao.emptyDeleted() }

    override suspend fun purgeDeletedOlderThan(cutoffMs: Long) = mutate { dao.purgeDeletedOlderThan(cutoffMs) }

    override fun search(query: String): Flow<List<NotificationItem>> {
        val tokens = query.split(Regex("\\s+"))
            .map { token -> token.filter(Char::isLetterOrDigit) }
            .filter(String::isNotEmpty)
        if (tokens.isEmpty()) return flowOf(emptyList())
        return dao.search(tokens.joinToString(" ") { "$it*" })
    }

    override suspend fun capture(c: CapturedNotification): CaptureResult {
        val result = db.withTransaction {
            dao.insertAppPrefIfAbsent(AppPref(packageName = c.packageName, lastSeenAt = c.postedAt))
            dao.touchAppPref(c.packageName, c.postedAt)
            if (c.messages.isNotEmpty()) captureMessages(c) else captureSingle(c)
        }
        if (result != CaptureResult.Ignored) widgetRefresher.refresh()
        return result
    }

    override fun appPrefs(): Flow<List<AppPref>> = dao.appPrefs()

    override suspend fun setAppIncluded(packageName: String, included: Boolean) = mutate {
        db.withTransaction {
            dao.insertAppPrefIfAbsent(AppPref(packageName = packageName, includeInHub = included, lastSeenAt = 0L))
            dao.setAppIncluded(packageName, included)
        }
    }

    override suspend fun isAppIncluded(packageName: String): Boolean = dao.appPref(packageName)?.includeInHub ?: true

    /** One item per chat message, skipping messages already stored and the user's own messages. */
    private suspend fun captureMessages(c: CapturedNotification): CaptureResult {
        val ids = c.messages.mapNotNull { message ->
            val at = if (message.timestamp > 0) message.timestamp else c.postedAt
            when {
                message.fromUser -> null
                dao.messageExists(c.notifKey, message.sender, message.text, message.timestamp) -> null
                else -> dao.insert(
                    NotificationItem(
                        packageName = c.packageName,
                        notifKey = c.notifKey,
                        conversationId = c.conversationId,
                        title = c.title,
                        text = message.text,
                        bigText = null,
                        subText = c.subText,
                        sender = message.sender,
                        messageTimestamp = message.timestamp,
                        postedAt = at,
                        sortAt = at,
                    ),
                )
            }
        }
        return if (ids.isEmpty()) CaptureResult.Ignored else CaptureResult.Inserted(ids)
    }

    /** Identical re-posts are ignored; a still-unread item is updated in place; anything else is a new item. */
    private suspend fun captureSingle(c: CapturedNotification): CaptureResult {
        val existing = dao.newestForKey(c.notifKey)
        if (existing != null && existing.title == c.title && existing.text == c.text && existing.bigText == c.bigText) {
            return CaptureResult.Ignored
        }
        if (existing != null && existing.state == ItemState.ACTIVE && !existing.isRead) {
            dao.update(
                existing.copy(
                    title = c.title,
                    text = c.text,
                    bigText = c.bigText,
                    subText = c.subText,
                    postedAt = c.postedAt,
                    sortAt = c.postedAt,
                ),
            )
            return CaptureResult.Updated(existing.id)
        }
        val id = dao.insert(
            NotificationItem(
                packageName = c.packageName,
                notifKey = c.notifKey,
                conversationId = c.conversationId,
                title = c.title,
                text = c.text,
                bigText = c.bigText,
                subText = c.subText,
                sender = null,
                messageTimestamp = null,
                postedAt = c.postedAt,
                sortAt = c.postedAt,
            ),
        )
        return CaptureResult.Inserted(listOf(id))
    }

    private suspend fun mutate(block: suspend () -> Unit) {
        block()
        widgetRefresher.refresh()
    }

    private companion object {
        const val PAGE_SIZE = 40
    }

    override suspend fun latestUnread(limit: Int): List<NotificationItem> = dao.latestUnread(limit)
}
