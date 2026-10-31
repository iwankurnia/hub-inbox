package com.iwankurnia.hub.ui.hub

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iwankurnia.hub.data.HubTab
import com.iwankurnia.hub.data.ItemState
import com.iwankurnia.hub.ui.hub.FakeHubRepository.Companion.item
import com.iwankurnia.hub.ui.theme.HubTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HubScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val todayNoon = LocalDate.now(zone).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val threeDaysAgo = LocalDate.now(zone).minusDays(3).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun show(vararg items: com.iwankurnia.hub.data.NotificationItem): HubViewModel {
        val vm = HubViewModel(FakeHubRepository(items.toList()))
        compose.setContent {
            HubTheme(dynamicColor = false) {
                HubScreen(onOpenSearch = {}, onOpenSettings = {}, viewModel = vm)
            }
        }
        return vm
    }

    private fun fontWeightOf(text: String): FontWeight? {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first().layoutInput.style.fontWeight
    }

    @Test
    fun showsDateBandsAndRows() {
        show(
            item(1, title = "Teguh", sortAt = todayNoon),
            item(2, title = "Okky", sortAt = threeDaysAgo),
        )

        compose.onNodeWithText("Today").assertExists()
        val weekday = DateBands.forInstant(threeDaysAgo, System.currentTimeMillis(), zone).label
        compose.onNodeWithText(weekday).assertExists()
        compose.onNodeWithText("Teguh").assertExists()
        compose.onNodeWithText("Okky").assertExists()
    }

    @Test
    fun unreadRow_hasBoldTitle() {
        show(
            item(1, title = "Teguh", sortAt = todayNoon, isRead = false),
            item(2, title = "Okky", sortAt = todayNoon - 60_000, isRead = true),
        )

        assertEquals(FontWeight.Bold, fontWeightOf("Teguh"))
        assertEquals(FontWeight.Normal, fontWeightOf("Okky"))
    }

    @Test
    fun longPress_entersSelectMode() {
        show(item(1, title = "Teguh", sortAt = todayNoon))

        compose.onNodeWithText("Teguh").performTouchInput { longClick() }

        compose.onNodeWithText("1 selected").assertExists()
    }

    @Test
    fun hiddenRow_keepsBandHeader() {
        val vm = show(
            item(1, title = "Shopee", sortAt = todayNoon, state = ItemState.DELETED, deletedAt = todayNoon),
            item(2, title = "Tokopedia", sortAt = todayNoon - 60_000, state = ItemState.DELETED, deletedAt = todayNoon - 60_000),
        )
        compose.runOnIdle { vm.selectTab(HubTab.DELETED) }
        compose.onNodeWithText("Shopee").assertExists()

        // Swiping the first row of the band away hides it until the purge lands.
        compose.runOnIdle { vm.purge(1) }

        compose.onNodeWithText("Shopee").assertDoesNotExist()
        compose.onNodeWithText("Tokopedia").assertExists()
        compose.onNodeWithText("Today").assertExists()
    }
}
