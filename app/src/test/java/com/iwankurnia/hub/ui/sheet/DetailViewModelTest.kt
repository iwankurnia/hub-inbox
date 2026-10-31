package com.iwankurnia.hub.ui.sheet

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.data.ItemState
import com.iwankurnia.hub.icon.AppIconCache
import com.iwankurnia.hub.snooze.SnoozeScheduler
import com.iwankurnia.hub.testing.InMemoryHubRepository
import com.iwankurnia.hub.testing.InMemoryHubRepository.Companion.item
import com.iwankurnia.hub.testing.RecordingLiveActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DetailViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 30L * DAY
    private val repository = InMemoryHubRepository { now }
    private val liveActions = RecordingLiveActions()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        repository.add(
            item(1, text = "Lunch?", postedAt = now - DAY),
            item(2, text = "Old news", postedAt = now - 8 * DAY),
            item(3, text = "Deleted one", postedAt = now - 2 * DAY, state = ItemState.DELETED),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = DetailViewModel(
        itemId = 1L,
        context = context,
        repository = repository,
        liveActions = liveActions,
        scheduler = SnoozeScheduler(context, repository),
        iconCache = AppIconCache(context),
        clock = { now },
    )

    @Test
    fun open_marksReadAndCountsThisWeek() = runTest {
        val vm = viewModel()

        assertTrue(repository.item(1)!!.isRead)
        assertEquals(3, vm.thread.value.size)
        assertEquals(2, vm.weekCount.value) // item 1 and the deleted item 3; item 2 is older than a week
        assertTrue(vm.canReply.value)
        assertEquals(4, vm.snoozeOptions.value.size)
    }

    @Test
    fun reply_recordsSentReplyOnSuccess() = runTest {
        val vm = viewModel()

        assertTrue(vm.reply("  On my way "))

        assertEquals(listOf("key-1" to "On my way"), liveActions.replies)
        val sent = repository.replies.value.single()
        assertEquals(1L, sent.itemId)
        assertEquals("On my way", sent.text)
    }

    @Test
    fun reply_doesNothingOnFailure() = runTest {
        liveActions.replySucceeds = false
        val vm = viewModel()

        assertFalse(vm.reply("On my way"))

        assertTrue(repository.replies.value.isEmpty())
    }

    @Test
    fun snooze_callsRepositoryAndReschedules() = runTest {
        val vm = viewModel()
        val until = now + 3_600_000L

        vm.snooze(until)

        assertEquals(until, repository.item(1)!!.snoozedUntil)
        assertEquals(listOf("key-1"), liveActions.cancelled)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assertEquals(until, shadowOf(alarmManager).scheduledAlarms.single().triggerAtTime)
    }

    @Test
    fun delete_marksDeleted() = runTest {
        repository.snooze(listOf(1L), now + DAY)
        SnoozeScheduler(context, repository).reschedule()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
        val vm = viewModel()

        vm.delete()

        assertEquals(ItemState.DELETED, repository.item(1)!!.state)
        assertEquals(listOf("key-1"), liveActions.cancelled)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun liveActions_listedAndRun() = runTest {
        liveActions.actionTitles = listOf("Mark as read", "Like")
        val vm = viewModel()

        assertEquals(listOf("Mark as read", "Like"), vm.liveActionTitles())
        vm.runLiveAction(1)

        assertEquals(listOf("Like"), liveActions.fired)
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
