package com.iwankurnia.hub.ui

import android.content.Context
import com.iwankurnia.hub.capture.LiveActions
import com.iwankurnia.hub.data.HubRepository

/**
 * Tapping an item (spec 4.3): marks it read, clears its system notification and fires its content
 * intent. Returns the item's package when there was no content intent to fire, so the caller starts
 * the app's launcher activity instead; null once the content intent has gone or when the item is gone.
 */
suspend fun openItem(repository: HubRepository, liveActions: LiveActions, id: Long): String? {
    val item = repository.item(id) ?: return null
    repository.markRead(listOf(id))
    // Cancel and open run back to back on the main thread, where the listener's removal callback (which
    // drops the content intent) is also delivered, so that callback cannot land in between.
    liveActions.cancel(item.notifKey)
    return if (liveActions.openContent(item.notifKey)) null else item.packageName
}

/** Starts [packageName]'s launcher activity; does nothing when the app has none. */
fun launchApp(context: Context, packageName: String) {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let(context::startActivity)
}
