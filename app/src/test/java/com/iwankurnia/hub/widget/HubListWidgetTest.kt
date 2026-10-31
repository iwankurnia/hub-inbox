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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = WidgetTestApp::class)
class HubListWidgetTest {

    @Test
    fun rowsForSize_2_4_6() {
        // 16dp padding + 28dp header + 66dp newest row with chips = 110dp, then 40dp per extra row.
        assertEquals(110.dp, listHeightFor(1))
        assertEquals(1, rowsFor(DpSize(250.dp, 110.dp)))
        assertEquals(2, rowsFor(DpSize(250.dp, 150.dp)))
        assertEquals(2, rowsFor(DpSize(250.dp, 180.dp)))
        assertEquals(4, rowsFor(DpSize(250.dp, 230.dp)))
        assertEquals(5, rowsFor(DpSize(250.dp, 300.dp)))
        assertEquals(6, rowsFor(DpSize(250.dp, 310.dp)))
        assertEquals(6, rowsFor(DpSize(250.dp, 600.dp)))
        assertEquals(1, rowsFor(DpSize(250.dp, 40.dp)))

        (110..600).forEach { height ->
            val rows = rowsFor(DpSize(250.dp, height.dp))
            assertTrue("$rows rows overflow ${height}dp", listHeightFor(rows) <= height.dp)
        }
        HubListWidget().sizeMode.sizes.forEach { size ->
            assertEquals(size.height, listHeightFor(rowsFor(size)))
        }
        assertEquals(listOf(1, 2, 4, 6), HubListWidget().sizeMode.sizes.map(::rowsFor).sorted())
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
