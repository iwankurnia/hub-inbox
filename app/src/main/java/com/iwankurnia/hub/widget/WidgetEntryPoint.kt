package com.iwankurnia.hub.widget

import android.content.Context
import com.iwankurnia.hub.capture.LiveActions
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.icon.AppIconCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** How Glance code, which Hilt cannot inject, reaches the app's singletons. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): HubRepository
    fun appIconCache(): AppIconCache
    fun liveActions(): LiveActions
    fun prefs(): HubPrefs
}

internal fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
