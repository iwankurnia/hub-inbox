package com.iwankurnia.hub.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iwankurnia.hub.PendingRequest
import com.iwankurnia.hub.PendingRequests
import com.iwankurnia.hub.ui.hub.HubScreen
import com.iwankurnia.hub.ui.onboarding.OnboardingScreen
import com.iwankurnia.hub.ui.search.SearchScreen
import com.iwankurnia.hub.ui.settings.SettingsScreen

/** Navigation routes. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HUB = "hub"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
}

@Composable
fun HubNavGraph(startDestination: String) {
    val navController = rememberNavController()
    val request by PendingRequests.pending.collectAsStateWithLifecycle()
    // Opaque under every screen, so the window never shows through during navigation transitions.
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        HubNavHost(navController, startDestination)
    }
    ReturnToHubOnRequest(navController, request)
}

@Composable
private fun HubNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Routes.HUB) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HUB) {
            val request by PendingRequests.pending.collectAsStateWithLifecycle()
            HubScreen(
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                request = request,
                onRequestConsumed = PendingRequests::consume,
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

/**
 * A widget or notification [request] is applied by the Hub screen, so when one arrives while Search or
 * Settings is showing, go back to Hub first; otherwise it would wait unseen until the user went back.
 */
@Composable
internal fun ReturnToHubOnRequest(navController: NavHostController, request: PendingRequest?) {
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        if (navController.currentDestination?.route != Routes.HUB) {
            // No-op from onboarding, where Hub is not yet on the back stack.
            navController.popBackStack(Routes.HUB, inclusive = false)
        }
    }
}
