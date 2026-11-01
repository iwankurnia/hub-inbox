package com.iwankurnia.hub.ui.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.ui.theme.HubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwipeRowTest {

    @get:Rule
    val compose = createComposeRule()

    private var reads = 0
    private var deletes = 0

    private fun show(readEnabled: Boolean) {
        compose.setContent {
            HubTheme(dynamicColor = false) {
                SwipeRow(
                    readEnabled = readEnabled,
                    stayAfterRead = true,
                    gesturesEnabled = true,
                    onRead = { reads++ },
                    onDelete = { deletes++ },
                ) {
                    Box(Modifier.fillMaxWidth().height(56.dp).testTag("row"))
                }
            }
        }
    }

    @Test
    fun swipeRight_inAll_reads() {
        show(readEnabled = true)

        compose.onNodeWithTag("row").performTouchInput { swipeRight() }
        compose.waitForIdle()
        // The toggle is applied a moment after the swipe settles.
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()

        assertEquals(1, reads)
        assertEquals(0, deletes)
    }

    @Test
    fun swipeRight_inDeleted_doesNothing() {
        show(readEnabled = false)

        compose.onNodeWithTag("row").performTouchInput { swipeRight() }
        compose.waitForIdle()
        // The toggle is applied a moment after the swipe settles.
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()

        assertEquals(0, reads)
        assertEquals(0, deletes)
    }
}
