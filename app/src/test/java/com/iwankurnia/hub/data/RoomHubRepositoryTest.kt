package com.iwankurnia.hub.data

import android.content.Context
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.capture.CaptureResult
import com.iwankurnia.hub.capture.CapturedMessage
import com.iwankurnia.hub.capture.CapturedNotification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomHubRepositoryTest {

    private lateinit var db: HubDatabase
    private lateinit var repo: RoomHubRepository
    private var now = 1_000_000L
    private var refreshes = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val refresher = object : WidgetRefresher {
            override suspend fun refresh() {
                refreshes++
            }
        }
        repo = RoomHubRepository(db, refresher) { now }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun notif(
        key: String = "k1",
        pkg: String = "com.example.mail",
        title: String = "Matthew",
        text: String = "Lunch?",
        bigText: String? = null,
        postedAt: Long = 100L,
        conversationId: String? = null,
        messages: List<CapturedMessage> = emptyList(),
    ) = CapturedNotification(
        packageName = pkg,
        notifKey = key,
        conversationId = conversationId,
        title = title,
        text = text,
        bigText = bigText,
        subText = null,
        postedAt = postedAt,
        messages = messages,
        hasReply = messages.isNotEmpty(),
    )

    private fun chat(messages: List<CapturedMessage>, postedAt: Long = 500L) = notif(
        key = "chat",
        pkg = "com.whatsapp",
        title = "Engineering",
        text = messages.lastOrNull()?.text ?: "",
        postedAt = postedAt,
        conversationId = "conv-1",
        messages = messages,
    )

    private fun ids(result: CaptureResult): List<Long> = (result as CaptureResult.Inserted).ids

    private suspend fun all() = repo.pagedItems(HubFilter(HubTab.ALL)).asSnapshot()

    @Test
    fun capture_nonChat_insertsUnread() = runTest {
        val result = repo.capture(notif())

        val id = ids(result).single()
        val item = repo.item(id)!!
        assertEquals("Matthew", item.title)
        assertEquals("Lunch?", item.text)
        assertFalse(item.isRead)
        assertEquals(ItemState.ACTIVE, item.state)
        assertEquals(100L, item.sortAt)
        assertNull(item.messageTimestamp)
        assertEquals(1, repo.unreadCount().first())
        assertEquals(1, refreshes)
        assertEquals(100L, repo.appPrefs().first().single().lastSeenAt)
    }

    @Test
    fun capture_sameKeyWhileUnread_updatesInPlace() = runTest {
        val id = ids(repo.capture(notif())).single()

        val result = repo.capture(notif(text = "Lunch at 1?", postedAt = 200L))

        assertEquals(CaptureResult.Updated(id), result)
        val item = repo.item(id)!!
        assertEquals("Lunch at 1?", item.text)
        assertEquals(200L, item.postedAt)
        assertEquals(200L, item.sortAt)
        assertEquals(1, all().size)
    }

    @Test
    fun capture_sameKeyAfterRead_insertsNewItem() = runTest {
        val first = ids(repo.capture(notif())).single()
        repo.markRead(listOf(first))

        val second = ids(repo.capture(notif(text = "Lunch at 1?", postedAt = 200L))).single()

        assertNotEquals(first, second)
        assertEquals("Lunch?", repo.item(first)!!.text)
        assertFalse(repo.item(second)!!.isRead)
        assertEquals(2, all().size)
    }

    @Test
    fun capture_identicalRepost_ignored() = runTest {
        val id = ids(repo.capture(notif())).single()

        assertEquals(CaptureResult.Ignored, repo.capture(notif(postedAt = 300L)))
        repo.markRead(listOf(id))
        assertEquals(CaptureResult.Ignored, repo.capture(notif(postedAt = 400L)))

        assertEquals(100L, repo.item(id)!!.sortAt)
        assertEquals(1, all().size)
    }

    @Test
    fun capture_chat_insertsOnePerMessage() = runTest {
        val messages = listOf(
            CapturedMessage("Ann", "Morning", 1_000L),
            CapturedMessage("Bob", "Hi all", 2_000L),
            CapturedMessage("Ann", "Stand-up?", 0L),
        )

        val inserted = ids(repo.capture(chat(messages)))

        assertEquals(3, inserted.size)
        val items = inserted.map { repo.item(it)!! }
        assertEquals(listOf("Morning", "Hi all", "Stand-up?"), items.map { it.text })
        assertEquals(listOf("Ann", "Bob", "Ann"), items.map { it.sender })
        assertTrue(items.all { it.title == "Engineering" && it.conversationId == "conv-1" })
        assertEquals(listOf(1_000L, 2_000L, 500L), items.map { it.postedAt })
        assertEquals(listOf(1_000L, 2_000L, 500L), items.map { it.sortAt })
        assertEquals(1_000L, items[0].messageTimestamp)
    }

    @Test
    fun capture_dedupesExistingMessages() = runTest {
        val three = listOf(
            CapturedMessage("Ann", "Morning", 1_000L),
            CapturedMessage("Bob", "Hi all", 2_000L),
            CapturedMessage("Ann", "Stand-up?", 3_000L),
        )
        repo.capture(chat(three))

        val result = repo.capture(chat(three + CapturedMessage("Bob", "Now", 4_000L), postedAt = 600L))

        assertEquals(1, ids(result).size)
        assertEquals(4, all().size)
        assertEquals(CaptureResult.Ignored, repo.capture(chat(three)))
    }

    @Test
    fun capture_skipsEchoedSentReply() = runTest {
        val incoming = CapturedMessage("Ann", "Coming?", 10_000L)
        val annId = ids(repo.capture(chat(listOf(incoming)))).single()
        now = 20_000L
        repo.recordSentReply(annId, "On my way")

        val echoed = CapturedMessage(null, "On my way", 50_000L)
        val result = repo.capture(chat(listOf(incoming, echoed)))

        assertEquals(CaptureResult.Ignored, result)
        assertEquals(1, all().size)
        val late = CapturedMessage(null, "On my way", 200_000L)
        assertEquals(1, ids(repo.capture(chat(listOf(incoming, late)))).size)
    }

    @Test
    fun capture_skipsEchoedSentReply_withSender() = runTest {
        val incoming = CapturedMessage("Ann", "Coming?", 10_000L)
        val annId = ids(repo.capture(chat(listOf(incoming)))).single()
        now = 20_000L
        repo.recordSentReply(annId, "On my way")

        val echoed = CapturedMessage("Iwan", "On my way", 30_000L)

        assertEquals(CaptureResult.Ignored, repo.capture(chat(listOf(incoming, echoed))))
        assertEquals(1, all().size)
    }

    @Test
    fun capture_userMessage_becomesSentReply_notItem() = runTest {
        val incoming = CapturedMessage("Ann", "Coming?", 10_000L)
        val annId = ids(repo.capture(chat(listOf(incoming)))).single()
        val mine = CapturedMessage(null, "Yes, five minutes", 20_000L, fromUser = true)

        assertEquals(CaptureResult.Ignored, repo.capture(chat(listOf(incoming, mine))))

        assertEquals(1, all().size)
        val reply = db.hubDao().repliesForConversation("conv-1").first().single()
        assertEquals(annId, reply.itemId)
        assertEquals("Yes, five minutes", reply.text)
        assertEquals(20_000L, reply.sentAt)
        val thread = repo.thread(repo.item(annId)!!).first()
        assertTrue(thread.last() is ThreadEntry.Outgoing)

        // Re-capturing the same notification adds nothing.
        repo.capture(chat(listOf(incoming, mine)))
        assertEquals(1, all().size)
        assertEquals(1, db.hubDao().repliesForConversation("conv-1").first().size)
    }

    @Test
    fun capture_userMessage_matchingHubReply_isNotRecordedTwice() = runTest {
        val annId = ids(repo.capture(chat(listOf(CapturedMessage("Ann", "Coming?", 10_000L))))).single()
        now = 20_000L
        repo.recordSentReply(annId, "On my way")

        repo.capture(chat(listOf(CapturedMessage(null, "On my way", 50_000L, fromUser = true))))

        assertEquals(1, db.hubDao().repliesForConversation("conv-1").first().size)
    }

    @Test
    fun capture_userMessage_firstInConversation_usesItemIdZero() = runTest {
        repo.capture(chat(listOf(CapturedMessage(null, "Hello?", 5_000L, fromUser = true))))

        assertEquals(0, all().size)
        assertEquals(0L, db.hubDao().repliesForConversation("conv-1").first().single().itemId)
    }

    @Test
    fun wakeDue_ignoresDeletedItems() = runTest {
        val id = ids(repo.capture(notif())).single()
        repo.snooze(listOf(id), untilMs = 2_000_000L)

        repo.delete(listOf(id))

        assertTrue(repo.wakeDue(3_000_000L).isEmpty())
        val item = repo.item(id)!!
        assertEquals(ItemState.DELETED, item.state)
        assertNull(item.snoozedUntil)
    }

    @Test
    fun nextSnoozeAt_ignoresDeletedItems() = runTest {
        val kept = ids(repo.capture(notif(key = "a"))).single()
        val deleted = ids(repo.capture(notif(key = "b"))).single()
        repo.snooze(listOf(kept), untilMs = 9_000_000L)
        repo.snooze(listOf(deleted), untilMs = 2_000_000L)

        repo.delete(listOf(deleted))

        assertEquals(9_000_000L, repo.nextSnoozeAt())
        repo.restore(listOf(deleted))
        assertNull(repo.item(deleted)!!.snoozedUntil)
        assertEquals(9_000_000L, repo.nextSnoozeAt())
    }

    @Test
    fun pagedItems_unreadExcludesSnoozed() = runTest {
        val a = ids(repo.capture(notif(key = "a", postedAt = 100L))).single()
        val b = ids(repo.capture(notif(key = "b", postedAt = 200L))).single()
        repo.snooze(listOf(a), untilMs = 5_000_000L)

        val unread = repo.pagedItems(HubFilter(HubTab.UNREAD)).asSnapshot()

        assertEquals(listOf(b), unread.map { it.id })
        assertEquals(listOf(b, a), all().map { it.id })
        assertEquals(1, repo.unreadCount().first())
    }

    @Test
    fun delete_restore_roundTrip_keepsSortAt() = runTest {
        val id = ids(repo.capture(notif(postedAt = 150L))).single()
        now = 9_000L

        repo.delete(listOf(id))

        val deleted = repo.item(id)!!
        assertEquals(ItemState.DELETED, deleted.state)
        assertEquals(9_000L, deleted.deletedAt)
        assertTrue(all().isEmpty())
        assertEquals(listOf(id), repo.pagedItems(HubFilter(HubTab.DELETED)).asSnapshot().map { it.id })

        repo.restore(listOf(id))

        val restored = repo.item(id)!!
        assertEquals(ItemState.ACTIVE, restored.state)
        assertNull(restored.deletedAt)
        assertEquals(150L, restored.sortAt)
        assertEquals(listOf(id), all().map { it.id })
    }

    @Test
    fun snooze_marksReadAndSetsUntil() = runTest {
        val id = ids(repo.capture(notif())).single()

        repo.snooze(listOf(id), untilMs = 7_000_000L)

        val item = repo.item(id)!!
        assertTrue(item.isRead)
        assertEquals(7_000_000L, item.snoozedUntil)
        assertEquals(7_000_000L, repo.nextSnoozeAt())
        repo.unsnooze(listOf(id))
        assertNull(repo.item(id)!!.snoozedUntil)
        assertNull(repo.nextSnoozeAt())
    }

    @Test
    fun wakeDue_resetsUnreadAndMovesToTop() = runTest {
        val older = ids(repo.capture(notif(key = "a", postedAt = 100L))).single()
        val newer = ids(repo.capture(notif(key = "b", postedAt = 200L))).single()
        repo.snooze(listOf(older), untilMs = 2_000_000L)
        now = 2_000_001L

        val woken = repo.wakeDue(now)

        assertEquals(listOf(older), woken.map { it.id })
        val item = repo.item(older)!!
        assertFalse(item.isRead)
        assertNull(item.snoozedUntil)
        assertEquals(2_000_001L, item.sortAt)
        assertEquals(100L, item.postedAt)
        assertEquals(listOf(older, newer), all().map { it.id })
        assertTrue(repo.wakeDue(now).isEmpty())
    }

    @Test
    fun search_matchesTitleAndText() = runTest {
        val byTitle = ids(repo.capture(notif(key = "a", title = "Matthew Lee", text = "Hello"))).single()
        val byText = ids(repo.capture(notif(key = "b", title = "Build bot", text = "Deployment finished"))).single()
        repo.capture(notif(key = "c", title = "Lunch", text = "Pizza"))

        assertEquals(listOf(byTitle), repo.search("matt").first().map { it.id })
        assertEquals(listOf(byText), repo.search("deploy").first().map { it.id })
        assertEquals(listOf(byText), repo.search("build fin").first().map { it.id })
        assertTrue(repo.search("  ").first().isEmpty())
        repo.delete(listOf(byTitle))
        assertEquals(listOf(byTitle), repo.search("matthew").first().map { it.id })
    }

    @Test
    fun thread_interleavesRepliesByTime() = runTest {
        val first = ids(repo.capture(chat(listOf(CapturedMessage("Ann", "Coming?", 1_000L))))).single()
        now = 2_000L
        repo.recordSentReply(first, "Yes")
        val second = ids(
            repo.capture(
                chat(listOf(CapturedMessage("Ann", "Coming?", 1_000L), CapturedMessage("Ann", "Great", 3_000L))),
            ),
        ).single()
        repo.capture(notif(key = "other", pkg = "com.whatsapp", title = "Family"))

        val entries = repo.thread(repo.item(second)!!).first()

        assertEquals(3, entries.size)
        assertEquals(first, (entries[0] as ThreadEntry.Incoming).item.id)
        assertEquals("Yes", (entries[1] as ThreadEntry.Outgoing).reply.text)
        assertEquals(second, (entries[2] as ThreadEntry.Incoming).item.id)
    }

    @Test
    fun appCounts_countsUnreadPerPackage() = runTest {
        val mailRead = ids(repo.capture(notif(key = "m1", pkg = "mail", postedAt = 100L))).single()
        repo.capture(notif(key = "m2", pkg = "mail", postedAt = 300L))
        val mailDeleted = ids(repo.capture(notif(key = "m3", pkg = "mail", postedAt = 400L))).single()
        repo.capture(notif(key = "s1", pkg = "slack", postedAt = 200L))
        repo.markRead(listOf(mailRead))
        repo.delete(listOf(mailDeleted))

        val counts = repo.appCounts().first()

        assertEquals(
            listOf(AppCount("mail", unread = 1, total = 2, lastSeenAt = 300L), AppCount("slack", 1, 1, 200L)),
            counts,
        )
        assertEquals(1, repo.unreadCount("mail").first())
        repo.markAllRead(null)
        assertEquals(0, repo.unreadCount().first())
    }

    @Test
    fun isAppIncluded_defaultsTrue() = runTest {
        assertTrue(repo.isAppIncluded("com.example.unknown"))

        repo.setAppIncluded("com.example.unknown", included = false)

        assertFalse(repo.isAppIncluded("com.example.unknown"))
        assertFalse(repo.appPrefs().first().single().includeInHub)
        repo.setAppIncluded("com.example.unknown", included = true)
        assertTrue(repo.isAppIncluded("com.example.unknown"))
    }

    @Test
    fun notifKeys_and_unreadNotifKeys() = runTest {
        val chatIds = ids(
            repo.capture(chat(listOf(CapturedMessage(null, "Hi", 400L), CapturedMessage(null, "There?", 450L)))),
        )
        val mail = ids(repo.capture(notif(key = "m1", pkg = "mail", postedAt = 100L))).single()
        val mailRead = ids(repo.capture(notif(key = "m2", pkg = "mail", postedAt = 200L))).single()
        val mailDeleted = ids(repo.capture(notif(key = "m3", pkg = "mail", postedAt = 300L))).single()
        repo.markRead(listOf(mailRead))
        repo.delete(listOf(mailDeleted))

        // Two messages of one notification share a key; it comes back once.
        assertEquals(listOf("chat", "m1"), repo.notifKeys(chatIds + mail).sorted())
        assertEquals(listOf("m2", "m3"), repo.notifKeys(listOf(mailRead, mailDeleted)).sorted())
        assertTrue(repo.notifKeys(emptyList()).isEmpty())

        // Only ACTIVE unread items, optionally for one app.
        assertEquals(listOf("chat", "m1"), repo.unreadNotifKeys(null).sorted())
        assertEquals(listOf("m1"), repo.unreadNotifKeys("mail"))
    }
}
