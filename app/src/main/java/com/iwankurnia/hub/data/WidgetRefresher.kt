package com.iwankurnia.hub.data

import javax.inject.Inject

/** Asks the home-screen widgets to redraw after the data changed. */
interface WidgetRefresher {
    suspend fun refresh()
}

/** Used until the widgets track supplies a real refresher. */
class NoOpWidgetRefresher @Inject constructor() : WidgetRefresher {
    override suspend fun refresh() = Unit
}
