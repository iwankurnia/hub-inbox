package com.iwankurnia.hub.ui.search

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iwankurnia.hub.capture.LiveActions
import com.iwankurnia.hub.data.AppCount
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.data.NotificationItem
import com.iwankurnia.hub.icon.AppIconCache
import com.iwankurnia.hub.ui.openItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Full-text search over everything stored (spec 4.7), narrowed to one app when a chip is picked. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: HubRepository,
    private val prefs: HubPrefs,
    private val iconCache: AppIconCache,
    private val liveActions: LiveActions,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _packageFilter = MutableStateFlow<String?>(null)
    val packageFilter: StateFlow<String?> = _packageFilter.asStateFlow()

    val recents: StateFlow<List<String>> = prefs.recentSearches
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _openApp = MutableStateFlow<String?>(null)

    /** One-shot: the package whose launcher activity the screen should start; cleared by [appOpened]. */
    val openApp: StateFlow<String?> = _openApp.asStateFlow()

    val apps: StateFlow<List<AppCount>> = repository.appCounts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Newest first; empty until the query has [MIN_CHARS] characters, and [DEBOUNCE_MS] after the last keystroke. */
    val results: StateFlow<List<NotificationItem>> = combine(
        _query.debounce(DEBOUNCE_MS)
            .map { it.trim() }
            .distinctUntilChanged()
            .flatMapLatest { q -> if (q.length < MIN_CHARS) flowOf(emptyList()) else repository.search(q) },
        _packageFilter,
    ) { items, packageName ->
        if (packageName == null) items else items.filter { it.packageName == packageName }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Remembers the current query in the recent list. */
    fun submit() {
        val value = _query.value.trim()
        if (value.length < MIN_CHARS) return
        viewModelScope.launch { prefs.addRecentSearch(value) }
    }

    fun filterApp(packageName: String?) {
        _packageFilter.value = packageName
    }

    /** Opens a result the way a tap in the list does (spec 4.3): read, notification cleared, app opened. */
    fun open(id: Long) {
        viewModelScope.launch {
            openItem(repository, liveActions, id)?.let { _openApp.value = it }
        }
    }

    /** The screen has started the app [openApp] asked for. */
    fun appOpened() {
        _openApp.value = null
    }

    fun appLabel(packageName: String): String = iconCache.label(packageName)

    fun appIcon(packageName: String): Drawable? = iconCache.icon(packageName)

    companion object {
        const val MIN_CHARS = 2
        const val DEBOUNCE_MS = 200L
    }
}
