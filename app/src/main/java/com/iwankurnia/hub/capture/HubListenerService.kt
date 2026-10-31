package com.iwankurnia.hub.capture

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.data.HubRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Turns every posted notification into Hub items and keeps their live actions (spec 4.1). */
@AndroidEntryPoint
class HubListenerService : NotificationListenerService() {

    @Inject lateinit var repository: HubRepository

    @Inject lateinit var prefs: HubPrefs

    @Inject lateinit var registry: LiveActionsRegistry

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val filter by lazy { NotificationFilter(ownPackage = packageName) }

    override fun onListenerConnected() {
        registry.canceller = { key -> runCatching { cancelNotification(key) } }
        // Import whatever is already in the shade; the repository's de-dup rules stop duplicates.
        val ranking = runCatching { currentRanking }.getOrNull()
        runCatching { activeNotifications }.getOrNull().orEmpty().forEach { handle(it, ranking) }
    }

    override fun onListenerDisconnected() {
        registry.canceller = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        sbn?.let { handle(it, rankingMap) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        val key = sbn?.key ?: return
        registry.unregister(key)
        // Only the app cancelling it means it was read there; a user swipe leaves the item unread.
        if (reason == REASON_APP_CANCEL || reason == REASON_APP_CANCEL_ALL) {
            scope.launch { repository.markReadByKey(key) }
        }
    }

    override fun onDestroy() {
        registry.canceller = null
        scope.cancel()
        super.onDestroy()
    }

    private fun handle(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val importance = channelImportance(sbn, rankingMap)
        scope.launch {
            if (!repository.isAppIncluded(sbn.packageName)) return@launch
            if (filter.shouldDrop(sbn, importance, prefs.hideSilent.first())) return@launch
            registry.register(sbn)
            repository.capture(NotificationMapper.map(sbn, this@HubListenerService))
        }
    }

    /** The channel's importance from the ranking, or null when the system did not supply one. */
    private fun channelImportance(sbn: StatusBarNotification, rankingMap: RankingMap?): Int? {
        val ranking = Ranking()
        if (rankingMap == null || !rankingMap.getRanking(sbn.key, ranking)) return null
        return ranking.channel?.importance
    }
}
