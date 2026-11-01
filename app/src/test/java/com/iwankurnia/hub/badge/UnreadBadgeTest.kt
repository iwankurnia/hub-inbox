package com.iwankurnia.hub.badge

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.testing.InMemoryHubRepository
import com.iwankurnia.hub.testing.InMemoryHubRepository.Companion.item
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class UnreadBadgeTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val repository = InMemoryHubRepository()
    private val enabled = MutableStateFlow(true)

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun badge(): Notification? = shadowOf(manager).getNotification(UnreadBadge.NOTIFICATION_ID)

    @Test
    fun postsWhenUnread() = runTest(UnconfinedTestDispatcher()) {
        UnreadBadge(app, repository, enabled, backgroundScope).start()
        assertNull(badge())

        repository.add(item(1), item(2), item(3, isRead = true))

        val posted = badge()!!
        assertEquals(2, posted.number)
        assertEquals("2 unread notifications", posted.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(UnreadBadge.CHANNEL_ID, posted.channelId)
        assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT == 0)
        val channel = manager.getNotificationChannel(UnreadBadge.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertTrue(channel.canShowBadge())
    }

    @Test
    fun cancelsAtZero() = runTest(UnconfinedTestDispatcher()) {
        repository.add(item(1))
        UnreadBadge(app, repository, enabled, backgroundScope).start()
        assertEquals(1, badge()!!.number)

        repository.markRead(listOf(1L))

        assertNull(badge())
    }

    @Test
    fun offWhenSettingDisabled() = runTest(UnconfinedTestDispatcher()) {
        enabled.value = false
        repository.add(item(1))
        UnreadBadge(app, repository, enabled, backgroundScope).start()
        assertNull(badge())

        enabled.value = true
        assertEquals(1, badge()!!.number)

        // Switching it off again takes the badge away.
        enabled.value = false
        assertNull(badge())
    }

    @Test
    fun withoutPermission_postsNothing() = runTest(UnconfinedTestDispatcher()) {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        repository.add(item(1))

        UnreadBadge(app, repository, enabled, backgroundScope).start()

        assertNull(badge())
    }
}
