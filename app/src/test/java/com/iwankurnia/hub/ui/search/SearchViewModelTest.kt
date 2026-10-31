package com.iwankurnia.hub.ui.search

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.icon.AppIconCache
import com.iwankurnia.hub.testing.InMemoryHubRepository
import com.iwankurnia.hub.testing.InMemoryHubRepository.Companion.item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SearchViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository = InMemoryHubRepository()
    private lateinit var prefs: HubPrefs
    private lateinit var vm: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        prefs = HubPrefs(PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("p.preferences_pb") })
        repository.add(
            item(1, title = "Teguh", text = "PR 1616 rebased", packageName = "com.whatsapp", postedAt = 3),
            item(2, title = "Bitbucket", text = "PR 1616 approved", packageName = "com.google.android.gm", postedAt = 2),
            item(3, title = "Shopee", text = "Your parcel is on its way", packageName = "com.shopee", postedAt = 1),
        )
        vm = SearchViewModel(repository, prefs, AppIconCache(context))
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun query_underTwoChars_noResults() = runTest {
        vm.setQuery("P")
        advanceUntilIdle()

        assertTrue(vm.results.value.isEmpty())
        assertTrue(repository.searches.isEmpty())

        vm.setQuery("PR")
        advanceTimeBy(100)
        assertTrue("debounced", vm.results.value.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), vm.results.value.map { it.id })
    }

    @Test
    fun results_filteredByPackage() = runTest {
        vm.setQuery("PR")
        vm.filterApp("com.whatsapp")
        advanceUntilIdle()

        assertEquals(listOf(1L), vm.results.value.map { it.id })

        vm.filterApp(null)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L), vm.results.value.map { it.id })
    }

    @Test
    fun submit_addsRecent() = runTest {
        vm.setQuery("  parcel ")
        vm.submit()
        advanceUntilIdle()

        assertEquals(listOf("parcel"), prefs.recentSearches.first { it.isNotEmpty() })
    }
}
