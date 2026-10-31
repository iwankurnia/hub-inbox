package com.iwankurnia.hub.capture

import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.capture.TestNotifications.builder
import com.iwankurnia.hub.capture.TestNotifications.sbn
import com.iwankurnia.hub.data.HubPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class HubListenerServiceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository = FakeHubRepository()
    private val registry = LiveActionsRegistry(context)
    private lateinit var service: HubListenerService

    @Before
    fun setUp() {
        val store = PreferenceDataStoreFactory.create(scope = prefsScope) {
            folder.root.resolve("hub_prefs.preferences_pb")
        }
        // Built but not created, so Hilt does not inject; the fakes go in by hand.
        service = Robolectric.buildService(HubListenerService::class.java).get().also {
            it.repository = repository
            it.prefs = HubPrefs(store)
            it.registry = registry
        }
    }

    @After
    fun tearDown() {
        service.scope.cancel()
        prefsScope.cancel()
    }

    /** Waits until the service has processed every event handed to it so far. */
    private fun settle() = runBlocking { service.awaitIdle() }

    private fun chat(id: Int, pkg: String = TestNotifications.PKG) = sbn(
        builder(context).setContentTitle("Anna").setContentText("Lunch?")
            .setContentIntent(TestNotifications.pendingIntent(context, "open"))
            .build(),
        pkg = pkg,
        id = id,
    )

    @Test
    fun removal_appCancelMarksRead_userCancelDoesNot() {
        val appCancelled = chat(1)
        val appCancelledAll = chat(2)
        val userSwiped = chat(3)
        val userClearedAll = chat(4)
        listOf(appCancelled, appCancelledAll, userSwiped, userClearedAll).forEach(registry::register)

        service.onNotificationRemoved(appCancelled, null, NotificationListenerService.REASON_APP_CANCEL)
        service.onNotificationRemoved(appCancelledAll, null, NotificationListenerService.REASON_APP_CANCEL_ALL)
        service.onNotificationRemoved(userSwiped, null, NotificationListenerService.REASON_CANCEL)
        service.onNotificationRemoved(userClearedAll, null, NotificationListenerService.REASON_CANCEL_ALL)
        settle()

        assertEquals(listOf(appCancelled.key, appCancelledAll.key), repository.markedReadKeys.sorted())
        listOf(appCancelled, appCancelledAll, userSwiped, userClearedAll).forEach {
            assertFalse("content intent kept for ${it.key}", registry.openContent(it.key))
        }
    }

    @Test
    fun posted_excludedApp_isNotCaptured() {
        repository.excludedPackages = setOf("com.example.muted")
        val muted = chat(1, pkg = "com.example.muted")

        service.onNotificationPosted(muted, null)
        settle()

        assertTrue(repository.captured.isEmpty())
        assertFalse(registry.openContent(muted.key))
    }

    @Test
    fun posted_capturesAndRegisters() {
        val posted = chat(1)
        val ongoing = sbn(builder(context).setContentTitle("Uploading").setOngoing(true).build(), id = 2)

        service.onNotificationPosted(posted, null)
        service.onNotificationPosted(ongoing, null)
        settle()

        assertEquals(listOf(posted.key), repository.captured.map { it.notifKey })
        assertEquals("Anna", repository.captured.single().title)
        assertTrue(registry.openContent(posted.key))
        assertFalse(registry.openContent(ongoing.key))
    }

    @Test
    fun posted_mapperThrows_doesNotCrashService() {
        val bad = chat(1)
        val good = chat(2)
        service.mapNotification = { sbn, context ->
            if (sbn.key == bad.key) throw IllegalArgumentException("malformed extras") else NotificationMapper.map(sbn, context)
        }

        service.onNotificationPosted(bad, null)
        service.onNotificationPosted(good, null)
        settle()

        assertEquals(listOf(good.key), repository.captured.map { it.notifKey })
        assertFalse(registry.openContent(bad.key))
        assertTrue(registry.openContent(good.key))
    }

    @Test
    fun removal_afterPost_isProcessedInOrder() {
        val posted = chat(1)

        service.onNotificationPosted(posted, null)
        service.onNotificationRemoved(posted, null, NotificationListenerService.REASON_APP_CANCEL)
        settle()

        assertEquals(listOf("capture:${posted.key}", "markRead:${posted.key}"), repository.calls)
        assertFalse(registry.openContent(posted.key))
    }
}
