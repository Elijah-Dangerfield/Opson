package com.opson.libraries.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.opson.system.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A closing popover plays its exit before it leaves, whatever closed it. Before, it was removed
 * the frame `expanded` went false, so an outside tap made it vanish rather than animate away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PopoverMenuTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun aClosedMenuStaysOnScreenUntilItsExitHasPlayed() {
        var expanded by mutableStateOf(true)
        compose.setContent {
            AppTheme {
                Box {
                    PopoverMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        MenuItem(label = "Grams", onClick = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Grams").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        expanded = false
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(MID_EXIT_MILLIS)
        compose.onAllNodesWithText("Grams").assertCountEquals(1)

        compose.mainClock.advanceTimeBy(PAST_EXIT_MILLIS)
        compose.onAllNodesWithText("Grams").assertCountEquals(0)
    }

    @Test
    fun aMenuDrawnFromStateKeepsItsRowsWhileItCloses() {
        var menu by mutableStateOf<String?>("Copy yesterday")
        compose.setContent {
            AppTheme {
                Box {
                    PopoverMenu(menu = menu, onDismissRequest = { menu = null }) { label ->
                        MenuItem(label = label, onClick = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Copy yesterday").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        menu = null
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(MID_EXIT_MILLIS)
        compose.onAllNodesWithText("Copy yesterday").assertCountEquals(1)

        compose.mainClock.advanceTimeBy(PAST_EXIT_MILLIS)
        compose.onAllNodesWithText("Copy yesterday").assertCountEquals(0)
    }

    private companion object {
        const val MID_EXIT_MILLIS = 60L
        const val PAST_EXIT_MILLIS = 1_000L
    }
}
