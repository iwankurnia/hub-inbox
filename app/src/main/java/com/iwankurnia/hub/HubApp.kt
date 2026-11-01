package com.iwankurnia.hub

import android.app.Application
import androidx.work.WorkManager
import com.iwankurnia.hub.badge.UnreadBadge
import com.iwankurnia.hub.capture.PurgeWorker
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HubApp : Application() {

    @Inject lateinit var unreadBadge: Lazy<UnreadBadge>

    override fun onCreate() {
        super.onCreate()
        // WorkManager's start-up initialiser has always run by now on a device; Robolectric skips it,
        // and app-wide background work is not started in JVM tests either.
        if (!WorkManager.isInitialized()) return
        PurgeWorker.schedule(this)
        unreadBadge.get().start()
    }
}
