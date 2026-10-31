package com.iwankurnia.hub.capture

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.iwankurnia.hub.data.HubDatabase
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.data.ItemState
import com.iwankurnia.hub.data.RoomHubRepository
import com.iwankurnia.hub.data.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PurgeWorkerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val day = TimeUnit.DAYS.toMillis(1)
    private var now = 0L
    private lateinit var db: HubDatabase
    private lateinit var repository: RoomHubRepository
    private lateinit var prefs: HubPrefs

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java).allowMainThreadQueries().build()
        val refresher = object : WidgetRefresher {
            override suspend fun refresh() = Unit
        }
        repository = RoomHubRepository(db, refresher) { now }
        prefs = HubPrefs(PreferenceDataStoreFactory.create(scope = prefsScope) { folder.root.resolve("hub_prefs.preferences_pb") })
    }

    @After
    fun tearDown() {
        db.close()
        prefsScope.cancel()
    }

    private fun worker(): PurgeWorker {
        val dependencies = object : PurgeWorker.Dependencies {
            override fun repository(): HubRepository = repository
            override fun prefs(): HubPrefs = prefs
        }
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                PurgeWorker(appContext, workerParameters, dependencies) { now }
        }
        return TestListenableWorkerBuilder<PurgeWorker>(context).setWorkerFactory(factory).build()
    }

    /** Captures one item and returns its id. */
    private suspend fun item(key: String): Long {
        val result = repository.capture(
            CapturedNotification(
                packageName = "com.example.mail",
                notifKey = key,
                conversationId = key,
                title = "Matthew",
                text = "Message $key",
                bigText = null,
                subText = null,
                postedAt = now,
                messages = emptyList(),
                hasReply = false,
            ),
        )
        return (result as CaptureResult.Inserted).ids.single()
    }

    @Test
    fun purge_respectsKeepDays() = runTest {
        prefs.setKeepDeletedDays(7)
        val old = item("old")
        val recent = item("recent")
        val active = item("active")
        repository.delete(listOf(old))
        now = 5 * day
        repository.delete(listOf(recent))
        now = 8 * day

        assertEquals(ListenableWorker.Result.success(), worker().doWork())

        assertNull("deleted eight days ago", repository.item(old))
        assertEquals(ItemState.DELETED, repository.item(recent)?.state)
        assertEquals(ItemState.ACTIVE, repository.item(active)?.state)
    }

    @Test
    fun purge_zeroDaysKeepsEverything() = runTest {
        prefs.setKeepDeletedDays(0)
        val deleted = item("deleted")
        repository.delete(listOf(deleted))
        now = 3_650 * day

        assertEquals(ListenableWorker.Result.success(), worker().doWork())

        assertNotNull(repository.item(deleted))
    }
}
