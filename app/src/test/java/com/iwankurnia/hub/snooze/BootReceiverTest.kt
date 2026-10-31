package com.iwankurnia.hub.snooze

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.testing.InMemoryHubRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class BootReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val repository = InMemoryHubRepository()
    private val receiver = BootReceiver().also { it.scheduler = SnoozeScheduler(context, repository) }

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun bootReceiver_reschedulesNextSnooze() = runTest {
        repository.add(InMemoryHubRepository.item(1, snoozedUntil = 7_000L))

        receiver.handle(Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(7_000L, shadowOf(alarmManager).scheduledAlarms.single().triggerAtMs)
    }

    @Test
    fun bootReceiver_ignoresOtherActions() = runTest {
        repository.add(InMemoryHubRepository.item(1, snoozedUntil = 7_000L))

        receiver.handle(Intent("com.example.SOMETHING_ELSE"))

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }
}
