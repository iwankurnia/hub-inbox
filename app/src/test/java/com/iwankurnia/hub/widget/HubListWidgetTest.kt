package com.iwankurnia.hub.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.iwankurnia.hub.data.HubDatabase
import com.iwankurnia.hub.data.ItemState
import com.iwankurnia.hub.data.NoOpWidgetRefresher
import com.iwankurnia.hub.data.NotificationItem
import com.iwankurnia.hub.data.RoomHubRepository
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = WidgetTestApp::class)
class HubListWidgetTest {

    @Test
    fun rowsForSize_2_4_6() {
        assertEquals(2, rowsFor(DpSize(250.dp, 110.dp)))
        assertEquals(2, rowsFor(DpSize(250.dp, 149.dp)))
        assertEquals(4, rowsFor(DpSize(250.dp, 150.dp)))
        assertEquals(4, rowsFor(DpSize(250.dp, 180.dp)))
        assertEquals(4, rowsFor(DpSize(250.dp, 259.dp)))
        assertEquals(6, rowsFor(DpSize(250.dp, 260.dp)))
        assertEquals(6, rowsFor(DpSize(250.dp, 300.dp)))
    }

    @Test
    fun markReadAction_callsRepository() = runTest {
        val app = ApplicationProvider.getApplicationContext<WidgetTestApp>()
        val glanceId = object : GlanceId {}
        val redrawn = mutableListOf<GlanceId>()
        val action = MarkReadAction(redraw = { _, id -> redrawn += id })

        action.onAction(app, glanceId, actionParametersOf(ItemIdKey to 7L))

        assertEquals(listOf(listOf(7L)), app.repository.markedRead)
        assertEquals(listOf(glanceId), redrawn)
    }

    @Test
    fun latestUnread_newestUnreadActiveOnly() = runTest {
        val app = ApplicationProvider.getApplicationContext<WidgetTestApp>()
        val db = Room.inMemoryDatabaseBuilder(app, HubDatabase::class.java).allowMainThreadQueries().build()
        val repository = RoomHubRepository(db, NoOpWidgetRefresher()) { 0L }
        val dao = db.hubDao()
        fun item(title: String, sortAt: Long) = NotificationItem(
            packageName = "com.example.chat", notifKey = title, conversationId = null, title = title, text = "",
            bigText = null, subText = null, sender = null, messageTimestamp = null, postedAt = sortAt, sortAt = sortAt,
        )
        dao.insert(item("old", 100))
        dao.insert(item("middle", 200))
        dao.insert(item("newest", 300))
        dao.insert(item("read", 400).copy(isRead = true))
        dao.insert(item("snoozed", 500).copy(snoozedUntil = 9_000))
        dao.insert(item("deleted", 600).copy(state = ItemState.DELETED, deletedAt = 600))

        assertEquals(listOf("newest", "middle"), repository.latestUnread(2).map { it.title })
        db.close()
    }
}
