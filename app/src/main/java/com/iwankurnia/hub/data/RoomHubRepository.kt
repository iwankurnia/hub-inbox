package com.iwankurnia.hub.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.iwankurnia.hub.capture.CaptureResult
import com.iwankurnia.hub.capture.CapturedMessage
import com.iwankurnia.hub.capture.CapturedNotification
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

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
                HubTab.ALL -> dao.pagingAll(filter.packageName)
                HubTab.UNREAD -> dao.pagingUnread(filter.packageName)
                HubTab.DELETED -> dao.pagingDeleted(filter.packageName)
            }
        }.flow

    override fun unreadCount(packageName: String?): Flow<Int> = dao.unreadCount(packageName)

    override fun appCounts(): Flow<List<AppCount>> = dao.appCounts()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun thread(item: NotificationItem, limit: Int): Flow<List<ThreadEntry>> {
        val conversationId = item.conversationId
        return if (conversationId != null) {
            combine(
                dao.threadByConversation(item.packageName, conversationId, limit),
                dao.repliesForConversation(conversationId),
            ) { items, replies -> interleave(items, replies, limit) }
        } else {
            dao.threadByTitle(item.packageName, item.title, limit).flatMapLatest { items ->
                dao.repliesForItems(items.map { it.id }).map { replies -> interleave(items, replies, limit) }
            }
        }
    }

    override suspend fun item(id: Long): NotificationItem? = dao.item(id)

    override fun itemFlow(id: Long): Flow<NotificationItem?> = dao.itemFlow(id)

    override suspend fun markRead(ids: List<Long>) = mutate { dao.markRead(ids) }

    override suspend fun markUnread(ids: List<Long>) = mutate { dao.markUnread(ids) }

    override suspend fun markAllRead(packageName: String?) = mutate { dao.markAllRead(packageName) }

    override suspend fun markReadByKey(notifKey: String) = mutate { dao.markReadByKey(notifKey) }

    override suspend fun delete(ids: List<Long>) = mutate { dao.delete(ids, clock()) }

    override suspend fun restore(ids: List<Long>) = mutate { dao.restore(ids) }

    override suspend fun purge(ids: List<Long>) = mutate { dao.purge(ids) }

    override suspend fun emptyDeleted() = mutate { dao.emptyDeleted() }

    override suspend fun purgeDeletedOlderThan(cutoffMs: Long) = mutate { dao.purgeDeletedOlderThan(cutoffMs) }

    override suspend fun snooze(ids: List<Long>, untilMs: Long) = mutate { dao.snooze(ids, untilMs) }

    override suspend fun unsnooze(ids: List<Long>) = mutate { dao.unsnooze(ids) }

    override suspend fun wakeDue(nowMs: Long): List<NotificationItem> {
        val woken = db.withTransaction {
            val ids = dao.dueSnoozeIds(nowMs)
            if (ids.isEmpty()) return@withTransaction emptyList()
            dao.wake(ids, nowMs)
            dao.items(ids)
        }
        if (woken.isNotEmpty()) widgetRefresher.refresh()
        return woken
    }

    override suspend fun nextSnoozeAt(): Long? = dao.nextSnoozeAt()

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

    override suspend fun recordSentReply(itemId: Long, text: String) = mutate {
        val conversationId = dao.item(itemId)?.conversationId
        dao.insertReply(SentReply(itemId = itemId, conversationId = conversationId, text = text, sentAt = clock()))
    }

    override fun appPrefs(): Flow<List<AppPref>> = dao.appPrefs()

    override suspend fun setAppIncluded(packageName: String, included: Boolean) = mutate {
        db.withTransaction {
            dao.insertAppPrefIfAbsent(AppPref(packageName = packageName, includeInHub = included, lastSeenAt = 0L))
            dao.setAppIncluded(packageName, included)
        }
    }

    override suspend fun isAppIncluded(packageName: String): Boolean = dao.appPref(packageName)?.includeInHub ?: true

    /** One item per chat message, skipping messages already stored and echoes of replies sent from Hub. */
    private suspend fun captureMessages(c: CapturedNotification): CaptureResult {
        val ids = c.messages.mapNotNull { message ->
            val at = if (message.timestamp > 0) message.timestamp else c.postedAt
            when {
                dao.messageExists(c.notifKey, message.sender, message.text, message.timestamp) -> null
                isEchoOfSentReply(c.conversationId, message, at) -> null
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
                        hasReply = c.hasReply,
                    ),
                )
            }
        }
        return if (ids.isEmpty()) CaptureResult.Ignored else CaptureResult.Inserted(ids)
    }

    /** A message matching a reply sent from Hub in the same conversation within the echo window. */
    private suspend fun isEchoOfSentReply(conversationId: String?, message: CapturedMessage, at: Long): Boolean =
        dao.sentReplyExists(conversationId, message.text, at - ECHO_WINDOW_MS, at + ECHO_WINDOW_MS)

    /** Identical re-posts are ignored; a still-unread item is updated in place; anything else is a new item. */
    private suspend fun captureSingle(c: CapturedNotification): CaptureResult {
        val existing = dao.newestForKey(c.notifKey)
        if (existing != null && existing.title == c.title && existing.text == c.text && existing.bigText == c.bigText) {
            return CaptureResult.Ignored
        }
        if (existing != null && existing.state == ItemState.ACTIVE && !existing.isRead && existing.snoozedUntil == null) {
            dao.update(
                existing.copy(
                    title = c.title,
                    text = c.text,
                    bigText = c.bigText,
                    subText = c.subText,
                    postedAt = c.postedAt,
                    sortAt = c.postedAt,
                    hasReply = c.hasReply,
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
                hasReply = c.hasReply,
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
        const val ECHO_WINDOW_MS = 60_000L

        /** Merges messages and replies by time; when the thread is cut at [limit], replies older than it are dropped. */
        fun interleave(items: List<NotificationItem>, replies: List<SentReply>, limit: Int): List<ThreadEntry> {
            val from = if (items.size >= limit) items.firstOrNull()?.postedAt ?: Long.MIN_VALUE else Long.MIN_VALUE
            val incoming = items.map { ThreadEntry.Incoming(it) to it.postedAt }
            val outgoing = replies.filter { it.sentAt >= from }.map { ThreadEntry.Outgoing(it) to it.sentAt }
            return (incoming + outgoing).sortedBy { it.second }.map { it.first }
        }
    }

    override suspend fun latestUnread(limit: Int): List<NotificationItem> = dao.latestUnread(limit)
}
