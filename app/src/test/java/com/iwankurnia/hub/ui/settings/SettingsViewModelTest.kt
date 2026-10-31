package com.iwankurnia.hub.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.data.AppCount
import com.iwankurnia.hub.data.AppPref
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.testing.InMemoryHubRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository = InMemoryHubRepository()
    private lateinit var prefs: HubPrefs
    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        prefs = HubPrefs(PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("p.preferences_pb") })
        repository.counts.value = listOf(
            AppCount("com.whatsapp", unread = 2, total = 4, lastSeenAt = 300),
            AppCount("com.google.android.gm", unread = 0, total = 6, lastSeenAt = 200),
        )
        vm = SettingsViewModel(repository, prefs)
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun toggleApp_writesAppPref() = runTest {
        vm.setAppIncluded("com.whatsapp", false).join()

        assertFalse(repository.isAppIncluded("com.whatsapp"))
        assertEquals(
            listOf("com.whatsapp" to false, "com.google.android.gm" to true),
            vm.apps.value.map { (count, included) -> count.packageName to included },
        )
    }

    @Test
    fun apps_includeHiddenAppsWithNothingStored() = runTest {
        repository.prefs.value = listOf(AppPref("com.instagram.android", includeInHub = false, lastSeenAt = 250))

        assertEquals(
            listOf("com.whatsapp", "com.instagram.android", "com.google.android.gm"),
            vm.apps.value.map { it.first.packageName },
        )
        assertEquals(AppCount("com.instagram.android", 0, 0, 250) to false, vm.apps.value[1])
    }

    @Test
    fun setKeepDays_persists() = runTest {
        assertEquals(30, prefs.keepDeletedDays.first())

        vm.setKeepDeletedDays(SettingsViewModel.KEEP_FOREVER).join()

        assertEquals(0, prefs.keepDeletedDays.first())
    }

    @Test
    fun hideSilent_defaultTrue() = runTest {
        assertTrue(vm.hideSilent.value)
        assertTrue(prefs.hideSilent.first())

        vm.setHideSilent(false).join()

        assertFalse(prefs.hideSilent.first())
    }
}
