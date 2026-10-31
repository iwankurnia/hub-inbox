package com.iwankurnia.hub.snooze

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.HubIntents
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.testing.InMemoryHubRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class SnoozeSchedulerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository = InMemoryHubRepository()
    private val scheduler = SnoozeScheduler(context, repository)
    private lateinit var prefs: HubPrefs

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        prefs = HubPrefs(PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("p.preferences_pb") })
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun reschedule_setsAlarmForNext() = runTest {
        repository.add(
            InMemoryHubRepository.item(1, snoozedUntil = 9_000L),
            InMemoryHubRepository.item(2, snoozedUntil = 5_000L),
        )

        scheduler.reschedule()

        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        assertEquals(5_000L, alarm.triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertTrue(alarm.allowWhileIdle)
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
        assertEquals(
            SnoozeWakeReceiver::class.java.name,
            shadowOf(alarm.operation).savedIntent.component?.className,
        )
    }

    @Test
    fun reschedule_inexactWithoutExactAlarmPermission() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        repository.add(InMemoryHubRepository.item(1, snoozedUntil = 5_000L))

        scheduler.reschedule()

        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        assertEquals(5_000L, alarm.triggerAtTime)
        assertTrue(alarm.allowWhileIdle)
        assertEquals(ShadowAlarmManager.WINDOW_HEURISTIC, alarm.windowLengthMs)
    }

    @Test
    fun reschedule_cancelsWhenNone() = runTest {
        repository.add(InMemoryHubRepository.item(1, snoozedUntil = 5_000L))
        scheduler.reschedule()
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)

        repository.unsnooze(listOf(1L))
        scheduler.reschedule()

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun wake_postsNotificationAndReschedules() = runTest {
        repository.add(
            InMemoryHubRepository.item(1, title = "Matthew", text = "Lunch?", isRead = true, snoozedUntil = 1_000L),
            InMemoryHubRepository.item(2, title = "Priya", snoozedUntil = 5_000L),
        )

        SnoozeWakeReceiver.wake(context, repository, prefs, scheduler, nowMs = 2_000L)

        val posted = shadowOf(notificationManager).allNotifications.single()
        val extras = posted.extras
        assertEquals("Snoozed: Matthew", extras.getCharSequence("android.title").toString())
        assertEquals("Lunch?", extras.getCharSequence("android.text").toString())
        assertEquals("snooze", posted.channelId)
        assertEquals(
            1L,
            shadowOf(posted.contentIntent).savedIntent.getLongExtra(HubIntents.EXTRA_ITEM_ID, -1L),
        )
        val woken = repository.item(1)!!
        assertFalse(woken.isRead)
        assertNull(woken.snoozedUntil)
        assertEquals(5_000L, shadowOf(alarmManager).scheduledAlarms.single().triggerAtTime)
    }

    @Test
    fun wake_withNotifyOff_wakesQuietly() = runTest {
        prefs.setSnoozeNotify(false)
        repository.add(InMemoryHubRepository.item(1, snoozedUntil = 1_000L))

        SnoozeWakeReceiver.wake(context, repository, prefs, scheduler, nowMs = 2_000L)

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
        assertNull(repository.item(1)!!.snoozedUntil)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }
}
