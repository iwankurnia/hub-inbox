package com.iwankurnia.hub.capture

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.data.HubRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Daily removal of items that have sat in Deleted longer than the "keep deleted" setting (spec 4.5). */
class PurgeWorker internal constructor(
    context: Context,
    params: WorkerParameters,
    private val dependencies: Dependencies,
    private val clock: () -> Long,
) : CoroutineWorker(context, params) {

    /** Created by WorkManager's default factory. */
    constructor(context: Context, params: WorkerParameters) : this(
        context,
        params,
        EntryPointAccessors.fromApplication(context, Dependencies::class.java),
        System::currentTimeMillis,
    )

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun repository(): HubRepository
        fun prefs(): HubPrefs
    }

    override suspend fun doWork(): Result {
        val keepDays = dependencies.prefs().keepDeletedDays.first()
        // Zero means keep deleted items forever.
        if (keepDays > 0) {
            dependencies.repository().purgeDeletedOlderThan(clock() - TimeUnit.DAYS.toMillis(keepDays.toLong()))
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "purge-deleted"

        /** Enqueues the daily purge once; an already scheduled one is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
