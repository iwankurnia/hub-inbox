package com.iwankurnia.hub.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.iwankurnia.hub.capture.LiveActions
import com.iwankurnia.hub.capture.NoLiveActions
import com.iwankurnia.hub.data.AppCount
import com.iwankurnia.hub.data.HubFilter
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.data.HubTab
import com.iwankurnia.hub.data.NotificationItem
import com.iwankurnia.hub.ui.openItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the snackbar reports; the screen turns it into text. */
enum class UndoKind { READ, UNREAD, DELETE, PURGE, RESTORE }

/**
 * A reversible action offered in the snackbar. [label] is the item's title when one item was
 * affected, else empty; [count] is how many items.
 */
data class UndoAction(
    val label: String,
    val revert: suspend () -> Unit,
    val kind: UndoKind = UndoKind.READ,
    val count: Int = 1,
)

/**
 * State and actions for the Hub list: tab, app filter, paging, selection, undo and opening an item.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HubViewModel internal constructor(
    private val repository: HubRepository,
    private val liveActions: LiveActions,
    keepDeletedDaysSource: Flow<Int>,
) : ViewModel() {

    @Inject
    constructor(repository: HubRepository, liveActions: LiveActions, prefs: HubPrefs) :
        this(repository, liveActions, prefs.keepDeletedDays)

    /** For tests and previews: no live notifications and the default 30-day bin. */
    constructor(repository: HubRepository) : this(repository, NoLiveActions, flowOf(DEFAULT_KEEP_DELETED_DAYS))

    private val _tab = MutableStateFlow(HubTab.ALL)
    val tab: StateFlow<HubTab> = _tab.asStateFlow()

    private val _packageFilter = MutableStateFlow<String?>(null)
    val packageFilter: StateFlow<String?> = _packageFilter.asStateFlow()

    val items: Flow<PagingData<NotificationItem>> =
        combine(_tab, _packageFilter) { tab, pkg -> HubFilter(tab, pkg) }
            .distinctUntilChanged()
            .flatMapLatest { repository.pagedItems(it) }
            .cachedIn(viewModelScope)

    val unreadCount: StateFlow<Int> = _packageFilter
        .flatMapLatest { repository.unreadCount(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    val apps: StateFlow<List<AppCount>> = repository.appCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val keepDeletedDays: StateFlow<Int> = keepDeletedDaysSource
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DEFAULT_KEEP_DELETED_DAYS)

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _openApp = MutableStateFlow<String?>(null)

    /** One-shot: the package whose launcher activity the screen should start; cleared by [appOpened]. */
    val openApp: StateFlow<String?> = _openApp.asStateFlow()

    private val _undo = MutableStateFlow<UndoAction?>(null)
    val undo: StateFlow<UndoAction?> = _undo.asStateFlow()

    /** Rows swiped away in Deleted whose purge waits for the Undo snackbar to go. */
    private val _hiddenIds = MutableStateFlow<Set<Long>>(emptySet())
    val hiddenIds: StateFlow<Set<Long>> = _hiddenIds.asStateFlow()
    private var pendingPurge: Set<Long> = emptySet()

    fun selectTab(tab: HubTab) {
        _tab.value = tab
        _selection.value = emptySet()
    }

    fun filterApp(packageName: String?) {
        _packageFilter.value = packageName
        _selection.value = emptySet()
    }

    /** Swipe from the left toggles: an unread row is marked read, a read row goes back to unread. */
    fun swipeRead(id: Long) {
        viewModelScope.launch {
            val item = repository.item(id) ?: return@launch
            if (item.isRead) unreadItems(listOf(id)) else readItems(listOf(id))
        }
    }

    fun swipeDelete(id: Long) = deleteItems(listOf(id))

    /** Puts a Deleted item back in All at its original time. */
    fun restore(id: Long) {
        viewModelScope.launch {
            val item = repository.item(id) ?: return@launch
            repository.restore(listOf(id))
            offerUndo(UndoAction(item.title, { repository.delete(listOf(id)) }, UndoKind.RESTORE))
        }
    }

    /** Removes a Deleted item for good once the Undo snackbar has gone without Undo. */
    fun purge(id: Long) = purgeItems(listOf(id))

    fun emptyDeleted() {
        viewModelScope.launch { repository.emptyDeleted() }
    }

    /** Marks everything read (for the filtered app, when one is chosen) and clears those system notifications. */
    fun markAllRead() {
        val packageName = _packageFilter.value
        viewModelScope.launch {
            val keys = repository.unreadNotifKeys(packageName)
            repository.markAllRead(packageName)
            keys.forEach(liveActions::cancel)
        }
    }

    fun undo() {
        val action = _undo.value ?: return
        _undo.value = null
        viewModelScope.launch { action.revert() }
    }

    /** The snackbar for [action] went away without Undo: commit whatever was waiting on it. */
    fun dismissUndo(action: UndoAction) {
        if (_undo.value !== action) return
        _undo.value = null
        commitPendingPurge()
    }

    fun toggleSelect(id: Long) {
        _selection.update { if (id in it) it - id else it + id }
    }

    fun selectAll(ids: Collection<Long>) {
        _selection.update { it + ids }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun readSelected() = readItems(takeSelection())

    /** Moves the selection to Deleted, or in the Deleted tab removes it for good. */
    fun deleteSelected() {
        val ids = takeSelection()
        if (_tab.value == HubTab.DELETED) purgeItems(ids) else deleteItems(ids)
    }

    /**
     * Marks [id] read, clears its system notification and opens it in its app: the notification's content
     * intent while it is still in the shade, else the app's launcher activity through [openApp].
     */
    fun open(id: Long) {
        viewModelScope.launch {
            openItem(repository, liveActions, id)?.let { _openApp.value = it }
        }
    }

    /** The screen has started the app [openApp] asked for. */
    fun appOpened() {
        _openApp.value = null
    }

    override fun onCleared() {
        // viewModelScope is already cancelled; a purge still waiting on Undo must not be lost.
        val ids = pendingPurge
        if (ids.isNotEmpty()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repository.purge(ids.toList()) }
        }
        super.onCleared()
    }

    private fun takeSelection(): List<Long> {
        val ids = _selection.value.toList()
        _selection.value = emptySet()
        return ids
    }

    private fun readItems(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val before = ids.mapNotNull { repository.item(it) }
            repository.markRead(ids)
            before.forEach { liveActions.cancel(it.notifKey) }
            val wasUnread = before.filterNot { it.isRead }.map { it.id }
            offerUndo(
                UndoAction(
                    label = before.singleOrNull()?.title.orEmpty(),
                    revert = { if (wasUnread.isNotEmpty()) repository.markUnread(wasUnread) },
                    kind = UndoKind.READ,
                    count = ids.size,
                ),
            )
        }
    }

    private fun unreadItems(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val before = ids.mapNotNull { repository.item(it) }
            repository.markUnread(ids)
            // The system notification was cancelled when the item was read; Hub cannot bring it back.
            val wasRead = before.filter { it.isRead }.map { it.id }
            offerUndo(
                UndoAction(
                    label = before.singleOrNull()?.title.orEmpty(),
                    revert = { if (wasRead.isNotEmpty()) repository.markRead(wasRead) },
                    kind = UndoKind.UNREAD,
                    count = ids.size,
                ),
            )
        }
    }

    private fun deleteItems(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val before = ids.mapNotNull { repository.item(it) }
            repository.delete(ids)
            before.forEach { liveActions.cancel(it.notifKey) }
            offerUndo(
                UndoAction(
                    label = before.singleOrNull()?.title.orEmpty(),
                    revert = { repository.restore(ids) },
                    kind = UndoKind.DELETE,
                    count = ids.size,
                ),
            )
        }
    }

    private fun purgeItems(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val before = ids.mapNotNull { repository.item(it) }
            commitPendingPurge()
            pendingPurge = ids.toSet()
            _hiddenIds.update { it + ids }
            _undo.value = UndoAction(
                label = before.singleOrNull()?.title.orEmpty(),
                revert = {
                    pendingPurge = pendingPurge - ids.toSet()
                    _hiddenIds.update { it - ids.toSet() }
                },
                kind = UndoKind.PURGE,
                count = ids.size,
            )
        }
    }

    private fun offerUndo(action: UndoAction) {
        commitPendingPurge()
        _undo.value = action
    }

    private fun commitPendingPurge() {
        val ids = pendingPurge
        if (ids.isEmpty()) return
        pendingPurge = emptySet()
        // Purged ids never come back, so they stay hidden until the paged list catches up.
        viewModelScope.launch { repository.purge(ids.toList()) }
    }

    private companion object {
        const val DEFAULT_KEEP_DELETED_DAYS = 30
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
