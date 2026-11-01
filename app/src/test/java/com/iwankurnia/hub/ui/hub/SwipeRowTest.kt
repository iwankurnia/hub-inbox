package com.iwankurnia.hub.ui.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.swipeLeft
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
    /**
     * The Undo bug Iwan hit (2026-11-01): LazyColumn keeps each keyed row's saved state while the row is
     * gone, so a deleted row restored by Undo came back with its swipe state still dismissed and deleted
     * itself again. [SaveableStateHolder] is what LazyColumn wraps rows in; the row must delete once.
     */
    @Test
    fun swipeLeft_thenRowRemovedAndRestoredUnderSameKey_deletesOnce() {
        val visible = mutableStateOf(true)
        compose.setContent {
            HubTheme(dynamicColor = false) {
                val holder = rememberSaveableStateHolder()
                if (visible.value) {
                    holder.SaveableStateProvider("row-1") {
                        SwipeRow(
                            readEnabled = true,
                            stayAfterRead = true,
                            gesturesEnabled = true,
                            onRead = { reads++ },
                            onDelete = {
                                deletes++
                                visible.value = false
                            },
                        ) {
                            Box(Modifier.fillMaxWidth().height(56.dp).testTag("row"))
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("row").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(1, deletes)

        // Undo: the same row comes back under the same key.
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals("the restored row deleted itself again", 1, deletes)
        compose.onNodeWithTag("row").assertExists()
    }
}
