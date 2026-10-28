package com.opson.libraries.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.opson.system.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A popover closes on a tap outside it or on back, and plays its exit before it leaves, whatever
 * closed it. Before, it was removed the frame `expanded` went false, so an outside tap made it
 * vanish rather than animate away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PopoverMenuTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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

    @Test
    fun aTapOutsideClosesItAndTheTapGoesNoFurther() {
        var expanded by mutableStateOf(true)
        var dismissals = 0
        var behind = 0
        compose.setContent {
            AppTheme {
                Box(Modifier.fillMaxSize().clickable { behind++ }) {
                    PopoverMenu(expanded = expanded, onDismissRequest = { dismissals++; expanded = false }) {
                        MenuItem(label = "Grams", onClick = {})
                    }
                }
            }
        }

        // As large as the screen, so a tap anywhere around the menu lands on it.
        val screen = compose.onAllNodes(isRoot())[0].fetchSemanticsNode().size
        assertEquals(screen, compose.onNodeWithTag(POPOVER_OUTSIDE_TAG).fetchSemanticsNode().size)

        compose.onNodeWithTag(POPOVER_OUTSIDE_TAG).performTouchInput { click(bottomRight - Offset(10f, 10f)) }
        compose.waitForIdle()

        assertEquals(1, dismissals)
        assertEquals(0, behind)
        compose.onAllNodesWithText("Grams").assertCountEquals(0)
        // Closed, the catcher is gone with it and the screen takes taps again.
        compose.onAllNodesWithTag(POPOVER_OUTSIDE_TAG).assertCountEquals(0)
    }

    @Test
    fun aRowStillGetsItsTap() {
        var expanded by mutableStateOf(true)
        var picked = 0
        var dismissals = 0
        compose.setContent {
            AppTheme {
                Box {
                    PopoverMenu(expanded = expanded, onDismissRequest = { dismissals++; expanded = false }) {
                        MenuItem(label = "Grams", onClick = { picked++; expanded = false })
                    }
                }
            }
        }

        compose.onNodeWithText("Grams").performClick()
        compose.waitForIdle()

        assertEquals(1, picked)
        assertEquals(0, dismissals)
    }

    @Test
    fun backClosesIt() {
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

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertFalse(expanded)
        compose.onAllNodesWithText("Grams").assertCountEquals(0)
    }

    private companion object {
        const val MID_EXIT_MILLIS = 60L
        const val PAST_EXIT_MILLIS = 1_000L
    }
}
