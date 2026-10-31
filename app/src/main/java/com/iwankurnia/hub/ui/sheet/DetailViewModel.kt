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
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

/** State and actions behind one item's detail sheet (spec 4.3). Opening it marks the item read. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = DetailViewModel.Factory::class)
class DetailViewModel internal constructor(
    private val itemId: Long,
    private val context: Context,
    private val repository: HubRepository,
    private val liveActions: LiveActions,
    private val scheduler: SnoozeScheduler,
    private val iconCache: AppIconCache,
    private val clock: () -> Long,
) : ViewModel() {

    @AssistedInject
    constructor(
        @Assisted itemId: Long,
        @ApplicationContext context: Context,
        repository: HubRepository,
        liveActions: LiveActions,
        scheduler: SnoozeScheduler,
        iconCache: AppIconCache,
    ) : this(itemId, context, repository, liveActions, scheduler, iconCache, System::currentTimeMillis)

    @AssistedFactory
    interface Factory {
        fun create(itemId: Long): DetailViewModel
    }

    val item: StateFlow<NotificationItem?> = repository.itemFlow(itemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val thread: StateFlow<List<ThreadEntry>> = item.filterNotNull()
        .distinctUntilChangedBy { it.id }
        .flatMapLatest { repository.thread(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Stored notifications in this thread, live or deleted, posted in the last seven days. */
    val weekCount: StateFlow<Int> = thread
        .map { entries ->
            val since = clock() - WEEK_MS
            entries.count { it is ThreadEntry.Incoming && it.item.postedAt >= since }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** True while the system notification is still in the shade with a `RemoteInput` reply action. */
    val canReply: StateFlow<Boolean> = item
        .map { it != null && liveActions.hasReply(it.notifKey) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _snoozeOptions = MutableStateFlow(
        SnoozeTimes.options(clock(), ZoneId.systemDefault(), SnoozeTimes.Labels.from(context)),
    )
    val snoozeOptions: StateFlow<List<SnoozeTimes.Option>> = _snoozeOptions.asStateFlow()

    init {
        viewModelScope.launch { repository.markRead(listOf(itemId)) }
    }

    fun now(): Long = clock()

    fun appLabel(packageName: String): String = iconCache.label(packageName)

    fun appIcon(packageName: String): Drawable? = iconCache.icon(packageName)

    /** Marks the item read and clears its system notification. */
    fun markRead() {
        val key = item.value?.notifKey
        viewModelScope.launch {
            repository.markRead(listOf(itemId))
            key?.let(liveActions::cancel)
        }
    }

    /** Moves the item to Deleted, clears its system notification and drops any snooze alarm. */
    fun delete() {
        val key = item.value?.notifKey
        viewModelScope.launch {
            repository.delete(listOf(itemId))
            key?.let(liveActions::cancel)
            scheduler.reschedule()
        }
    }

    fun snooze(untilMs: Long) {
        val key = item.value?.notifKey
        viewModelScope.launch {
            repository.snooze(listOf(itemId), untilMs)
            key?.let(liveActions::cancel)
            scheduler.reschedule()
        }
    }

    /** Sends [text] through the notification's own reply action; records it only when that worked. */
    fun reply(text: String): Boolean {
        val current = item.value ?: return false
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !liveActions.reply(current.notifKey, trimmed)) return false
        viewModelScope.launch { repository.recordSentReply(current.id, trimmed) }
        return true
    }

    /** Fires the notification's own content intent, else launches the app. */
    fun openInApp() {
        val current = item.value ?: return
        if (liveActions.openContent(current.notifKey)) return
        context.packageManager.getLaunchIntentForPackage(current.packageName)?.let { intent ->
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun liveActionTitles(): List<String> = item.value?.let { liveActions.actions(it.notifKey) }.orEmpty().map { it.title }

    fun runLiveAction(index: Int) {
        val current = item.value ?: return
        liveActions.actions(current.notifKey).getOrNull(index)?.send?.invoke()
    }

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}
