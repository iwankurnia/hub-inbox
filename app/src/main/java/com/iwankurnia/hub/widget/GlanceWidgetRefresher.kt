package com.iwankurnia.hub.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.iwankurnia.hub.data.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** Redraws both home-screen widgets after the repository changes; a widget failure never fails the write. */
@Singleton
class GlanceWidgetRefresher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WidgetRefresher {

    override suspend fun refresh() {
        try {
            HubListWidget().updateAll(context)
            AppCountWidget().updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Widget refresh failed", e)
        }
    }

    private companion object {
        const val TAG = "GlanceWidgetRefresher"
    }
}
