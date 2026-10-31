package com.iwankurnia.hub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iwankurnia.hub.data.HubPrefs
import com.iwankurnia.hub.data.HubTab
import com.iwankurnia.hub.ui.nav.HubNavGraph
import com.iwankurnia.hub.ui.nav.Routes
import com.iwankurnia.hub.ui.theme.HubTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A deep link into the Hub screen, read from [HubIntents] extras. */
data class PendingRequest(
    val tab: HubTab?,
    val packageName: String?,
    val itemId: Long?,
    val openSearch: Boolean,
) {
    companion object {
        /** Null when [intent] carries none of the [HubIntents] extras. */
        fun from(intent: Intent?): PendingRequest? {
            if (intent == null) return null
            val request = PendingRequest(
                tab = intent.getStringExtra(HubIntents.EXTRA_TAB)?.let { name -> HubTab.entries.firstOrNull { it.name == name } },
                packageName = intent.getStringExtra(HubIntents.EXTRA_PACKAGE),
                // A widget drawn before Reply was removed may still send the reply extra: it opens the item too.
                itemId = intent.longExtraOrNull(HubIntents.EXTRA_ITEM_ID)
                    ?: intent.longExtraOrNull(HubIntents.EXTRA_REPLY_ITEM_ID),
                openSearch = intent.getBooleanExtra(HubIntents.EXTRA_OPEN_SEARCH, false),
            )
            return request.takeIf { it != PendingRequest(null, null, null, false) }
        }

        private fun Intent.longExtraOrNull(name: String): Long? =
            if (hasExtra(name)) getLongExtra(name, -1L).takeIf { it >= 0 } else null
    }
}

/** Holds the latest [PendingRequest] until the Hub screen has applied it. */
object PendingRequests {
    private val _pending = MutableStateFlow<PendingRequest?>(null)
    val pending: StateFlow<PendingRequest?> = _pending.asStateFlow()

    fun offer(intent: Intent?) {
        PendingRequest.from(intent)?.let { _pending.value = it }
    }

    fun consume() {
        _pending.value = null
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: HubPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) PendingRequests.offer(intent)
        setContent {
            val dynamicColor by prefs.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
            val onboardingDone by prefs.onboardingDone.collectAsStateWithLifecycle(initialValue = null)
            // Chosen once: later preference changes must not rebuild the navigation graph.
            var start by rememberSaveable { mutableStateOf<String?>(null) }
            if (start == null) {
                onboardingDone?.let { done -> start = if (done && listenerEnabled()) Routes.HUB else Routes.ONBOARDING }
            }
            HubTheme(dynamicColor = dynamicColor) {
                start?.let { HubNavGraph(startDestination = it) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PendingRequests.offer(intent)
    }

    private fun listenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}
