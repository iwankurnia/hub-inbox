package com.iwankurnia.hub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.hubDataStore by preferencesDataStore(name = "hub_prefs")

/** App-wide settings and the recent-search list, kept in DataStore. */
@Singleton
class HubPrefs internal constructor(private val store: DataStore<Preferences>) {

    @Inject
    constructor(@ApplicationContext ctx: Context) : this(ctx.hubDataStore)

    val hideSilent: Flow<Boolean> = store.data.map { it[HIDE_SILENT] ?: true }
    val keepDeletedDays: Flow<Int> = store.data.map { it[KEEP_DELETED_DAYS] ?: 30 }
    val snoozeNotify: Flow<Boolean> = store.data.map { it[SNOOZE_NOTIFY] ?: true }
    val dynamicColor: Flow<Boolean> = store.data.map { it[DYNAMIC_COLOR] ?: true }

    /** At most [MAX_RECENT_SEARCHES], newest first. */
    val recentSearches: Flow<List<String>> = store.data.map { decode(it[RECENT_SEARCHES]) }
    val onboardingDone: Flow<Boolean> = store.data.map { it[ONBOARDING_DONE] ?: false }

    suspend fun setHideSilent(value: Boolean) = set(HIDE_SILENT, value)
    suspend fun setKeepDeletedDays(value: Int) = set(KEEP_DELETED_DAYS, value)
    suspend fun setSnoozeNotify(value: Boolean) = set(SNOOZE_NOTIFY, value)
    suspend fun setDynamicColor(value: Boolean) = set(DYNAMIC_COLOR, value)
    suspend fun setOnboardingDone(value: Boolean) = set(ONBOARDING_DONE, value)

    /** Moves [query] to the front of the recent list; blank queries are ignored. */
    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim().replace(SEPARATOR, " ")
        if (trimmed.isEmpty()) return
        store.edit { prefs ->
            val updated = (listOf(trimmed) + decode(prefs[RECENT_SEARCHES]).filterNot { it == trimmed })
                .take(MAX_RECENT_SEARCHES)
            prefs[RECENT_SEARCHES] = updated.joinToString(SEPARATOR)
        }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    private companion object {
        const val MAX_RECENT_SEARCHES = 10
        const val SEPARATOR = "\u001F"
        val HIDE_SILENT = booleanPreferencesKey("hide_silent")
        val KEEP_DELETED_DAYS = intPreferencesKey("keep_deleted_days")
        val SNOOZE_NOTIFY = booleanPreferencesKey("snooze_notify")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        fun decode(raw: String?): List<String> =
            raw?.split(SEPARATOR)?.filter(String::isNotEmpty).orEmpty()
    }
}
