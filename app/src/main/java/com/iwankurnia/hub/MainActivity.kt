package com.iwankurnia.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.iwankurnia.hub.ui.nav.HubNavGraph
import com.iwankurnia.hub.ui.nav.Routes
import com.iwankurnia.hub.ui.theme.HubTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HubTheme {
                HubNavGraph(startDestination = Routes.HUB)
            }
        }
    }
}
