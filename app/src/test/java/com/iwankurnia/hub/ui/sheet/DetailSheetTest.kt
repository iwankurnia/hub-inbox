package com.iwankurnia.hub.ui.sheet

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.data.SentReply
import com.iwankurnia.hub.data.ThreadEntry
import com.iwankurnia.hub.snooze.SnoozeTimes
import com.iwankurnia.hub.testing.InMemoryHubRepository.Companion.item
import com.iwankurnia.hub.ui.theme.HubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val first = item(1, title = "Teguh", text = "PR rebased, can you check?", postedAt = 1_000L)
    private val second = item(2, title = "Teguh", text = "Thanks", postedAt = 3_000L)

    private fun state(canReply: Boolean) = DetailState(
        item = second,
        appLabel = "WhatsApp",
        icon = null,
        thread = listOf(
            ThreadEntry.Incoming(first),
            ThreadEntry.Outgoing(SentReply(1, 1, "conv-1", "Will do", 2_000L)),
            ThreadEntry.Incoming(second),
        ),
        weekCount = 2,
        canReply = canReply,
        liveActionTitles = listOf("Like"),
        snoozeOptions = listOf(SnoozeTimes.Option("In 1 hour", "10:41", 5_000L)),
        nowMs = 4_000L,
    )

    private fun setContent(canReply: Boolean) = compose.setContent {
        HubTheme(dynamicColor = false) {
            DetailSheetBody(state = state(canReply), actions = DetailSheetActions.None)
        }
    }

    @Test
    fun sheet_showsThreadAndActions() {
        setContent(canReply = true)

        compose.onNodeWithText("2 notifications this week").assertIsDisplayed()
        compose.onNodeWithText("PR rebased, can you check?").assertIsDisplayed()
        compose.onNodeWithText("Will do").assertIsDisplayed()
        compose.onNodeWithText("Reply…").assertIsDisplayed()
        compose.onNodeWithText("Mark read").assertExists()
        compose.onNodeWithText("Delete").assertExists()
        compose.onNodeWithText("Open in WhatsApp").assertExists()
        compose.onNodeWithText("Like").assertExists()
        compose.onNodeWithText("In 1 hour").assertDoesNotExist()

        // The chip row sits below the small test window, so click through semantics.
        compose.onNodeWithText("Snooze").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("In 1 hour").assertExists()
        compose.onNodeWithText("Pick date & time…").assertExists()
    }

    @Test
    fun sheet_hidesReplyWhenNoRemoteInput() {
        setContent(canReply = false)

        compose.onNodeWithText("Thanks").assertIsDisplayed()
        compose.onNodeWithText("Reply…").assertDoesNotExist()
    }
}
