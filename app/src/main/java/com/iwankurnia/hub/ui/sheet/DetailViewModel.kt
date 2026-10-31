package com.iwankurnia.hub.ui.sheet

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iwankurnia.hub.capture.LiveActions
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.data.NotificationItem
import com.iwankurnia.hub.data.ThreadEntry
import com.iwankurnia.hub.icon.AppIconCache
import com.iwankurnia.hub.snooze.SnoozeScheduler
import com.iwankurnia.hub.snooze.SnoozeTimes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/**
 * State and actions behind the detail sheet (spec 4.3). One instance serves every item: the sheet calls
 * [setItem] and then [onOpened] each time it opens, and the Room flows run only while the sheet watches them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel internal constructor(
    private val context: Context,
    private val repository: HubRepository,
    private val liveActions: LiveActions,
    private val scheduler: SnoozeScheduler,
    private val iconCache: AppIconCache,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        repository: HubRepository,
        liveActions: LiveActions,
        scheduler: SnoozeScheduler,
        iconCache: AppIconCache,
    ) : this(context, repository, liveActions, scheduler, iconCache, System::currentTimeMillis)

    private val itemId = MutableStateFlow<Long?>(null)

    /** The item on show; null until it loads, and again for a moment after [setItem] switches items. */
    val item: StateFlow<NotificationItem?> = itemId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.itemFlow(id).onStart { emit(null) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val thread: StateFlow<List<ThreadEntry>> = item
        .distinctUntilChangedBy { it?.id }
        .flatMapLatest { current -> if (current == null) flowOf(emptyList()) else repository.thread(current) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Stored notifications in this thread, live or deleted, posted in the last seven days. */
    val weekCount: StateFlow<Int> = thread
        .map { entries ->
            val since = clock() - WEEK_MS
            entries.count { it is ThreadEntry.Incoming && it.item.postedAt >= since }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    /** True while the system notification is still in the shade with a `RemoteInput` reply action. */
    val canReply: StateFlow<Boolean> = item
        .map { it != null && liveActions.hasReply(it.notifKey) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Points the sheet's flows and actions at [id]. */
    fun setItem(id: Long) {
        itemId.value = id
    }

    private val _snoozeOptions = MutableStateFlow(computeSnoozeOptions())
    val snoozeOptions: StateFlow<List<SnoozeTimes.Option>> = _snoozeOptions.asStateFlow()

    /**
     * Call each time the sheet opens, after [setItem]: marks the item read and clears its system
     * notification (spec 4.1, 4.3). Not done in [setItem] because reopening the same item must do it again.
     */
    fun onOpened() {
        val id = itemId.value ?: return
        viewModelScope.launch {
            repository.markRead(listOf(id))
            cancelNotification(id)
        }
    }

    /** Recomputes the snooze times from now; call each time the picker opens (spec 4.6). */
    fun refreshSnoozeOptions() {
        _snoozeOptions.value = computeSnoozeOptions()
    }

    private fun computeSnoozeOptions(): List<SnoozeTimes.Option> =
        SnoozeTimes.options(clock(), ZoneId.systemDefault(), SnoozeTimes.Labels.from(context))

    fun now(): Long = clock()

    fun appLabel(packageName: String): String = iconCache.label(packageName)

    fun appIcon(packageName: String): Drawable? = iconCache.icon(packageName)

    /** Marks the item read and clears its system notification. */
    fun markRead() {
        val id = itemId.value ?: return
        viewModelScope.launch {
            repository.markRead(listOf(id))
            cancelNotification(id)
        }
    }

    /** Moves the item to Deleted, clears its system notification and drops any snooze alarm. */
    fun delete() {
        val id = itemId.value ?: return
        viewModelScope.launch {
            repository.delete(listOf(id))
            cancelNotification(id)
            scheduler.reschedule()
        }
    }

    fun snooze(untilMs: Long) {
        val id = itemId.value ?: return
        viewModelScope.launch {
            repository.snooze(listOf(id), untilMs)
            cancelNotification(id)
            scheduler.reschedule()
        }
    }

    /** Sends [text] through the notification's own reply action; records it only when that worked. */
    fun reply(text: String): Boolean {
        val current = currentItem() ?: return false
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !liveActions.reply(current.notifKey, trimmed)) return false
        viewModelScope.launch { repository.recordSentReply(current.id, trimmed) }
        return true
    }

    /** Fires the notification's own content intent, else launches the app. */
    fun openInApp() {
        val current = currentItem() ?: return
        if (liveActions.openContent(current.notifKey)) return
        context.packageManager.getLaunchIntentForPackage(current.packageName)?.let { intent ->
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun liveActionTitles(): List<String> = currentItem()?.let { liveActions.actions(it.notifKey) }.orEmpty().map { it.title }

    fun runLiveAction(index: Int) {
        val current = currentItem() ?: return
        liveActions.actions(current.notifKey).getOrNull(index)?.send?.invoke()
    }

    /** The loaded item, unless it still belongs to the previous [setItem]. */
    private fun currentItem(): NotificationItem? = item.value?.takeIf { it.id == itemId.value }

    private suspend fun cancelNotification(id: Long) {
        repository.notifKeys(listOf(id)).forEach(liveActions::cancel)
    }

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
