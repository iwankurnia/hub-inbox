package com.iwankurnia.hub.ui.nav

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.PendingRequest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReturnToHubTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController
    private var request by mutableStateOf<PendingRequest?>(null)

    private fun setContent() = compose.setContent {
        navController = rememberNavController()
        NavHost(navController = navController, startDestination = Routes.HUB) {
            composable(Routes.HUB) { Text("Hub screen") }
            composable(Routes.SEARCH) { Text("Search screen") }
            composable(Routes.SETTINGS) { Text("Settings screen") }
        }
        ReturnToHubOnRequest(navController, request)
    }

    @Test
    fun request_whileOnSearch_popsBackToHub() {
        setContent()
        compose.runOnIdle { navController.navigate(Routes.SEARCH) }
        compose.onNodeWithText("Search screen").assertExists()

        request = PendingRequest(tab = null, packageName = null, itemId = 7L, openSearch = false)

        compose.onNodeWithText("Hub screen").assertExists()
        compose.runOnIdle { assertEquals(Routes.HUB, navController.currentDestination?.route) }
    }

    @Test
    fun request_whileOnSettings_popsBackToHub() {
        setContent()
        compose.runOnIdle {
            navController.navigate(Routes.SEARCH)
            navController.navigate(Routes.SETTINGS)
        }

        request = PendingRequest(tab = null, packageName = "com.whatsapp", itemId = null, openSearch = false)

        compose.onNodeWithText("Hub screen").assertExists()
        compose.runOnIdle { assertEquals(null, navController.previousBackStackEntry) }
    }
}
