package com.iwankurnia.hub.capture

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.iwankurnia.hub.badge.UnreadBadge
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.data.HubRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Turns every posted notification into Hub items and keeps their live actions (spec 4.1). */
@AndroidEntryPoint
class HubListenerService : NotificationListenerService() {

    @Inject lateinit var repository: HubRepository

    @Inject lateinit var prefs: HubPrefs

    @Inject lateinit var registry: LiveActionsRegistry

    @Inject lateinit var unreadBadge: UnreadBadge

    /** Seam for tests; production always uses [NotificationMapper]. */
    internal var mapNotification: (StatusBarNotification, Context) -> CapturedNotification = NotificationMapper::map

    private val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "Listener work failed", e) }

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + crashGuard)

    /** Every listener event, consumed one at a time in arrival order so a removal never overtakes its post. */
    private val events = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private val filter by lazy { NotificationFilter(ownPackage = packageName) }

    /** Keys Hub cancelled for "Keep the status bar clear"; their removal must not drop the content intent. */
    private val clearedForShade: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        scope.launch {
            for (event in events) {
                try {
                    event()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One bad notification must not stop the ones behind it.
                    Log.e(TAG, "Listener event failed", e)
                }
            }
        }
    }

    /** Seam for tests; production reads the live shade. */
    internal var shadeContents: () -> List<StatusBarNotification> =
        { runCatching { activeNotifications }.getOrNull().orEmpty().toList() }

    override fun onCreate() {
        super.onCreate()
        // The system may start the listener without the app's activity; the badge follows the count from here too.
        unreadBadge.start()
        watchClearShade()
    }

    /** Sweeps the shade the moment "Keep the status bar clear" is switched on, not only for later posts. */
    internal fun watchClearShade() {
        scope.launch {
            prefs.clearShade.distinctUntilChanged().collect { on -> if (on) sweepShade() }
        }
    }

    private fun sweepShade() {
        val ranking = runCatching { currentRanking }.getOrNull()
        shadeContents().forEach { handle(it, ranking) }
    }

    override fun onListenerConnected() {
        registry.canceller = { key -> runCatching { cancelNotification(key) } }
        // Import whatever is already in the shade; the repository's de-dup rules stop duplicates.
        sweepShade()
    }

    override fun onListenerDisconnected() {
        registry.canceller = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        sbn?.let { handle(it, rankingMap) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        val key = sbn?.key ?: return
        // Only the app cancelling it means it was read there; a user swipe leaves the item unread.
        val readInApp = reason == REASON_APP_CANCEL || reason == REASON_APP_CANCEL_ALL
        val clearedByHub = reason == REASON_LISTENER_CANCEL
        enqueue {
            // Hub's own shade-clearing cancel keeps the intent so a tap still opens the notification's target.
            if (!(clearedByHub && clearedForShade.remove(key))) registry.unregister(key)
            if (readInApp) repository.markReadByKey(key)
        }
    }

    override fun onDestroy() {
        registry.canceller = null
        events.close()
        scope.cancel()
        super.onDestroy()
    }

    /** Suspends until every event queued before this call has been processed. */
    internal suspend fun awaitIdle() {
        val done = CompletableDeferred<Unit>()
        enqueue { done.complete(Unit) }
        done.await()
    }

    private fun enqueue(event: suspend () -> Unit) {
        events.trySend(event)
    }

    private fun handle(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val importance = channelImportance(sbn, rankingMap)
        enqueue {
            if (!repository.isAppIncluded(sbn.packageName)) return@enqueue
            val clearShade = prefs.clearShade.first()
            val drop = filter.dropReason(sbn, importance, prefs.hideSilent.first())
            if (drop != NotificationFilter.Drop.NONE) {
                // Group summaries and silent posts never become items, but with the option on they must
                // not linger in the shade either (WhatsApp's "3 new messages" summary, for one).
                if (clearShade && drop.clearable) clearFromShade(sbn.key)
                return@enqueue
            }
            val captured = mapNotification(sbn, this)
            registry.register(sbn)
            repository.capture(captured)
            // Every accepted post is cleared, including an identical re-post that changed nothing in Hub:
            // chat apps re-post the same conversation on every message.
            if (clearShade) clearFromShade(sbn.key)
        }
    }

    private fun clearFromShade(key: String) {
        clearedForShade += key
        registry.cancel(key)
    }

    /** The channel's importance from the ranking, or null when the system did not supply one. */
    private fun channelImportance(sbn: StatusBarNotification, rankingMap: RankingMap?): Int? {
        val ranking = Ranking()
        if (rankingMap == null || !rankingMap.getRanking(sbn.key, ranking)) return null
        return ranking.channel?.importance
    }

    private companion object {
        const val TAG = "HubListener"
    }
}
