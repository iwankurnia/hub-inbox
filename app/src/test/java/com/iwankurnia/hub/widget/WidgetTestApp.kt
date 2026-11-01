package com.iwankurnia.hub.widget

import android.app.Application
import com.iwankurnia.hub.capture.LiveActions
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.data.HubPrefs
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.iwankurnia.hub.icon.AppIconCache
import com.iwankurnia.hub.testing.RecordingLiveActions
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import java.lang.reflect.Proxy

/**
 * Stands in for the Hilt application in widget tests: `EntryPointAccessors.fromApplication` resolves
 * [WidgetEntryPoint] to this app's fake component, so widget code sees [repository] and [liveActions].
 */
class WidgetTestApp : Application(), GeneratedComponentManager<Any> {

    val repository = RecordingHubRepository()
    val liveActions = RecordingLiveActions()
    val prefs by lazy { HubPrefs(PreferenceDataStoreFactory.create { filesDir.resolve("widget_test_prefs.preferences_pb") }) }

    private val component = object : GeneratedComponent, WidgetEntryPoint {
        override fun repository(): HubRepository = this@WidgetTestApp.repository
        override fun appIconCache(): AppIconCache = AppIconCache(this@WidgetTestApp)
        override fun liveActions(): LiveActions = this@WidgetTestApp.liveActions
        override fun prefs(): HubPrefs = this@WidgetTestApp.prefs
    }

    override fun generatedComponent(): Any = component
}

/**
 * Records `markRead` calls and answers `notifKeys` with "key-<id>"; every other member throws, so a
 * test fails loudly if the widget uses it.
 */
class RecordingHubRepository : HubRepository by unsupported() {
    val markedRead = mutableListOf<List<Long>>()

    override suspend fun markRead(ids: List<Long>) {
        markedRead += ids
    }

    override suspend fun notifKeys(ids: List<Long>): List<String> = ids.map { "key-$it" }
}

private fun unsupported(): HubRepository = Proxy.newProxyInstance(
    HubRepository::class.java.classLoader,
    arrayOf(HubRepository::class.java),
) { _, method, _ -> throw UnsupportedOperationException("${method.name} is not faked") } as HubRepository
