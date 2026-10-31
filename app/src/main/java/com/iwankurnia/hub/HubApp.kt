package com.iwankurnia.hub

import android.app.Application
import androidx.work.WorkManager
import com.iwankurnia.hub.capture.PurgeWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HubApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // WorkManager's start-up initialiser has always run by now on a device; Robolectric skips it.
        if (WorkManager.isInitialized()) PurgeWorker.schedule(this)
    }
}
