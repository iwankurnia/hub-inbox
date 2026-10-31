package com.iwankurnia.hub.ui.hub

import com.iwankurnia.hub.data.HubFilter
import com.iwankurnia.hub.data.HubTab
import com.iwankurnia.hub.data.ItemState
import com.iwankurnia.hub.testing.RecordingLiveActions
import com.iwankurnia.hub.ui.hub.FakeHubRepository.Companion.item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HubViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeHubRepository
    private lateinit var vm: HubViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeHubRepository(
            listOf(
                item(1, title = "Teguh", sortAt = 3_000L),
                item(2, title = "Okky", sortAt = 2_000L, snoozedUntil = 9_000L, isRead = true),
                item(3, title = "Bitbucket", sortAt = 1_000L, pkg = "com.google.android.gm"),
            ),
        )
        vm = HubViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectTab_changesFilter() = runTest(dispatcher) {
        backgroundScope.launch { vm.items.collect {} }
        assertEquals(HubFilter(HubTab.ALL, null), repo.requestedFilters.last())

        vm.selectTab(HubTab.UNREAD)
        assertEquals(HubTab.UNREAD, vm.tab.value)
        assertEquals(HubFilter(HubTab.UNREAD, null), repo.requestedFilters.last())

        vm.filterApp("com.whatsapp")
        assertEquals(HubFilter(HubTab.UNREAD, "com.whatsapp"), repo.requestedFilters.last())

        // The app filter persists across tabs.
        vm.selectTab(HubTab.DELETED)
        assertEquals(HubFilter(HubTab.DELETED, "com.whatsapp"), repo.requestedFilters.last())
    }

    @Test
    fun swipeDelete_thenUndo_restoresItem() = runTest(dispatcher) {
        vm.swipeDelete(1)
        assertEquals(ItemState.DELETED, repo.get(1).state)
        val undo = vm.undo.value
        assertNotNull(undo)
        assertEquals("Teguh", undo!!.label)

        vm.undo()

        assertEquals(listOf(1L), repo.restored.single())
        assertEquals(ItemState.ACTIVE, repo.get(1).state)
        assertEquals(3_000L, repo.get(1).sortAt)
        assertNull(vm.undo.value)
    }

    @Test
    fun swipeRead_marksRead() = runTest(dispatcher) {
        vm.swipeRead(1)
        assertTrue(repo.get(1).isRead)
        assertEquals(UndoKind.READ, vm.undo.value?.kind)

        vm.undo()
        assertFalse(repo.get(1).isRead)
    }

    @Test
    fun swipeRead_onReadItem_marksUnread() = runTest(dispatcher) {
        vm.swipeRead(1)
        assertTrue(repo.get(1).isRead)

        vm.swipeRead(1)
        assertFalse(repo.get(1).isRead)
        assertEquals(UndoKind.UNREAD, vm.undo.value?.kind)

        vm.undo()
        assertTrue(repo.get(1).isRead)
    }

    @Test
    fun open_marksReadCancelsAndOpensContent() = runTest(dispatcher) {
        val live = RecordingLiveActions(opens = true)
        vm = HubViewModel(repo, live, flowOf(30))

        vm.open(1)

        assertTrue(repo.get(1).isRead)
        assertEquals(listOf("cancel:k1", "open:k1"), live.calls)
        // The content intent opened the app, so there is nothing to fall back to.
        assertNull(vm.openApp.value)
    }

    @Test
    fun open_fallsBackToLaunchIntentWhenNoContentIntent() = runTest(dispatcher) {
        val live = RecordingLiveActions(opens = false)
        vm = HubViewModel(repo, live, flowOf(30))

        vm.open(3)

        assertTrue(repo.get(3).isRead)
        assertEquals(listOf("cancel:k3", "open:k3"), live.calls)
        assertEquals("com.google.android.gm", vm.openApp.value)

        vm.appOpened()
        assertNull(vm.openApp.value)
    }

    @Test
    fun selection_toggleAndClear() = runTest(dispatcher) {
        vm.toggleSelect(1)
        vm.toggleSelect(3)
        assertEquals(setOf(1L, 3L), vm.selection.value)

        vm.toggleSelect(1)
        assertEquals(setOf(3L), vm.selection.value)

        vm.clearSelection()
        assertTrue(vm.selection.value.isEmpty())
    }

    @Test
    fun deleteSelected_deletesAllAndClears() = runTest(dispatcher) {
        vm.toggleSelect(1)
        vm.toggleSelect(2)

        vm.deleteSelected()

        assertEquals(ItemState.DELETED, repo.get(1).state)
        assertEquals(ItemState.DELETED, repo.get(2).state)
        assertEquals(ItemState.ACTIVE, repo.get(3).state)
        assertTrue(vm.selection.value.isEmpty())
        assertEquals(2, vm.undo.value?.count)

        // Undo brings both back, and the snoozed one goes back to sleep.
        vm.undo()
        assertEquals(ItemState.ACTIVE, repo.get(2).state)
        assertEquals(9_000L, repo.get(2).snoozedUntil)
    }

    @Test
    fun purge_inDeleted_waitsForUndoToExpire() = runTest(dispatcher) {
        vm.selectTab(HubTab.DELETED)
        repo.store.value = repo.store.value.map { if (it.id == 3L) it.copy(state = ItemState.DELETED) else it }

        vm.purge(3)
        assertTrue(3L in vm.hiddenIds.value)
        assertTrue(repo.purged.isEmpty())

        vm.undo()
        assertFalse(3L in vm.hiddenIds.value)
        assertTrue(repo.purged.isEmpty())

        vm.purge(3)
        vm.dismissUndo(vm.undo.value!!)
        assertEquals(listOf(3L), repo.purged.single())
    }

    @Test
    fun markAllRead_onlyForTheFilteredApp() = runTest(dispatcher) {
        vm.filterApp("com.google.android.gm")
        vm.markAllRead()
        assertEquals("com.google.android.gm", repo.markAllReadFor)
    }

    @Test
    fun markAllRead_cancelsNotifications() = runTest(dispatcher) {
        val live = RecordingLiveActions()
        vm = HubViewModel(repo, live, flowOf(30))

        vm.filterApp("com.whatsapp")
        vm.markAllRead()
        // Item 2 is already read (snoozed), so only item 1 of WhatsApp had a notification to clear.
        assertEquals(listOf("k1"), live.cancelled)

        vm.filterApp(null)
        vm.markAllRead()
        assertEquals(listOf("k1", "k3"), live.cancelled)
    }
}
