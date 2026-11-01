package com.iwankurnia.hub.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iwankurnia.hub.data.AppCount
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.data.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings of spec 4.8, plus the onboarding's "done" flag. Setters return the write's [Job]. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: HubRepository,
    private val prefs: HubPrefs,
    private val widgets: WidgetRefresher,
) : ViewModel() {

    /**
     * Every app Hub has seen, most recently seen first, with whether it is included. Apps with nothing
     * stored any more still appear (from their preference row) so a hidden app can be switched back on.
     */
    val apps: StateFlow<List<Pair<AppCount, Boolean>>> =
        combine(repository.appCounts(), repository.appPrefs()) { counts, appPrefs ->
            val countsByPackage = counts.associateBy { it.packageName }
            val prefsByPackage = appPrefs.associateBy { it.packageName }
            (countsByPackage.keys + prefsByPackage.keys).map { packageName ->
                val pref = prefsByPackage[packageName]
                val count = countsByPackage[packageName]
                    ?: AppCount(packageName, unread = 0, total = 0, lastSeenAt = pref?.lastSeenAt ?: 0L)
                val lastSeen = maxOf(count.lastSeenAt, pref?.lastSeenAt ?: 0L)
                Triple(count, pref?.includeInHub ?: true, lastSeen)
            }
                .sortedByDescending { it.third }
                .map { (count, included, _) -> count to included }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hideSilent: StateFlow<Boolean> = prefs.hideSilent.state(true)
    val keepDeletedDays: StateFlow<Int> = prefs.keepDeletedDays.state(30)
    val dynamicColor: StateFlow<Boolean> = prefs.dynamicColor.state(false)
    val clearShade: StateFlow<Boolean> = prefs.clearShade.state(false)
    val unreadBadge: StateFlow<Boolean> = prefs.unreadBadge.state(true)

    fun setAppIncluded(packageName: String, included: Boolean): Job = viewModelScope.launch { repository.setAppIncluded(packageName, included) }

    /** Days to keep deleted items; [KEEP_FOREVER] keeps them until emptied. */
    fun setKeepDeletedDays(days: Int): Job = viewModelScope.launch { prefs.setKeepDeletedDays(days) }

    fun setClearShade(value: Boolean): Job = viewModelScope.launch { prefs.setClearShade(value) }

    fun setUnreadBadge(value: Boolean): Job = viewModelScope.launch { prefs.setUnreadBadge(value) }

    fun setHideSilent(value: Boolean): Job = viewModelScope.launch { prefs.setHideSilent(value) }

    /** The widgets follow this setting too, so they are redrawn even when no widget session is running. */
    fun setDynamicColor(value: Boolean): Job = viewModelScope.launch {
        prefs.setDynamicColor(value)
        widgets.refresh()
    }

    /** Suspends until written, so the caller can leave the screen safely afterwards. */
    suspend fun completeOnboarding() {
        prefs.setOnboardingDone(true)
    }

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    companion object {
        /** `keepDeletedDays == 0` means deleted items are never purged (see the purge worker). */
        const val KEEP_FOREVER = 0
        val KEEP_OPTIONS = listOf(7, 30, 90, KEEP_FOREVER)
    }
}
