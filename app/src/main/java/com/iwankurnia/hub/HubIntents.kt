package com.iwankurnia.hub

import android.content.Context
import android.content.Intent

/** Extras and helpers for deep links into [MainActivity] from the widgets. */
object HubIntents {
    /** Tab to show: "ALL", "UNREAD" or "DELETED". */
    const val EXTRA_TAB = "com.iwankurnia.hub.extra.TAB"

    /** Package name of the app to filter the stream to. */
    const val EXTRA_PACKAGE = "com.iwankurnia.hub.extra.PACKAGE"

    /** Item to open in its app, as a tap on its row does. */
    const val EXTRA_ITEM_ID = "com.iwankurnia.hub.extra.ITEM_ID"

    /** Sent by the widget's former Reply chip; read as [EXTRA_ITEM_ID] so a widget not yet redrawn still works. */
    const val EXTRA_REPLY_ITEM_ID = "com.iwankurnia.hub.extra.REPLY_ITEM_ID"

    /** Boolean: open the search screen. */
    const val EXTRA_OPEN_SEARCH = "com.iwankurnia.hub.extra.OPEN_SEARCH"

    /** Builds an intent that brings [MainActivity] to the front with whichever extras are given. */
    fun open(
        context: Context,
        tab: String? = null,
        packageName: String? = null,
        itemId: Long? = null,
    ): Intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        tab?.let { putExtra(EXTRA_TAB, it) }
        packageName?.let { putExtra(EXTRA_PACKAGE, it) }
        itemId?.let { putExtra(EXTRA_ITEM_ID, it) }
    }
}
