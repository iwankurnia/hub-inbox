package com.iwankurnia.hub.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
            HubScreen(
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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
