package com.iwankurnia.hub.widget

import android.app.Application
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.icon.AppIconCache
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import java.lang.reflect.Proxy

/**
 * Stands in for the Hilt application in widget tests: `EntryPointAccessors.fromApplication` resolves
 * [WidgetEntryPoint] to this app's fake component, so widget code sees [repository].
 */
class WidgetTestApp : Application(), GeneratedComponentManager<Any> {

    val repository = RecordingHubRepository()

    private val component = object : GeneratedComponent, WidgetEntryPoint {
        override fun repository(): HubRepository = this@WidgetTestApp.repository
        override fun appIconCache(): AppIconCache = AppIconCache(this@WidgetTestApp)
    }

    override fun generatedComponent(): Any = component
}

/** Records `markRead` calls; every other member throws, so a test fails loudly if the widget uses it. */
class RecordingHubRepository : HubRepository by unsupported() {
    val markedRead = mutableListOf<List<Long>>()

    override suspend fun markRead(ids: List<Long>) {
        markedRead += ids
    }
}

private fun unsupported(): HubRepository = Proxy.newProxyInstance(
    HubRepository::class.java.classLoader,
    arrayOf(HubRepository::class.java),
) { _, method, _ -> throw UnsupportedOperationException("${method.name} is not faked") } as HubRepository
