package com.iwankurnia.hub.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HubPrefsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: HubPrefs

    @Before
    fun setUp() {
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            folder.root.resolve("hub_prefs.preferences_pb")
        }
        prefs = HubPrefs(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun defaults() = runTest {
        assertTrue(prefs.hideSilent.first())
        assertEquals(30, prefs.keepDeletedDays.first())
        assertTrue(prefs.dynamicColor.first())
        assertTrue(prefs.recentSearches.first().isEmpty())
        assertFalse(prefs.onboardingDone.first())
    }

    @Test
    fun setters_persistValues() = runTest {
        prefs.setHideSilent(false)
        prefs.setKeepDeletedDays(7)
        prefs.setDynamicColor(false)
        prefs.setOnboardingDone(true)

        assertFalse(prefs.hideSilent.first())
        assertEquals(7, prefs.keepDeletedDays.first())
        assertFalse(prefs.dynamicColor.first())
        assertTrue(prefs.onboardingDone.first())
    }

    @Test
    fun recentSearches_newestFirstDedupedCappedAtTen() = runTest {
        (1..12).forEach { prefs.addRecentSearch("q$it") }
        prefs.addRecentSearch("  q5 ")
        prefs.addRecentSearch("   ")

        val recent = prefs.recentSearches.first()

        assertEquals(10, recent.size)
        assertEquals(listOf("q5", "q12", "q11", "q10", "q9", "q8", "q7", "q6", "q4", "q3"), recent)
    }
}
