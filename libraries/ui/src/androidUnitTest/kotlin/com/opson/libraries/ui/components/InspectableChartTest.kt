package com.opson.libraries.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.opson.system.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The chart gesture in a real composition: a tap or a sideways drag picks, a drag that starts
 * upward scrolls the page and picks nothing, every new point ticks once, and the points reach the
 * semantics tree whether or not anything is picked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InspectableChartTest {

    @get:Rule val compose = createComposeRule()

    private val week = listOf("Mon", "Tue", "Wed", null, "Fri", "Sat", "Sun")
    private val readouts = week.map { day -> day?.let { ChartReadout(it, listOf(ChartReading(value = "$it amount"))) } }
    private val ticks = mutableListOf<HapticFeedbackType>()
    private val scroll = ScrollState(0)

    /** The clock is held so the label's linger only passes when a test says so. */
    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides RecordingHaptics(ticks)) {
                AppTheme {
                    Column(Modifier.height(400.dp).verticalScroll(scroll)) {
                        Spacer(Modifier.height(80.dp))
                        IntakeBarChart(
                            bars = week.map { IntakeBar(it ?: "Thu", if (it == null) null else 0.5f) },
                            band = null,
                            readouts = readouts,
                            modifier = Modifier.width(350.dp).padding(horizontal = 0.dp).testTag(CHART),
                        )
                        Spacer(Modifier.height(1_000.dp))
                    }
                }
            }
        }
    }

    @Test
    fun everyPointWithData_isANodeForScreenReaders_andAGapIsNot() {
        show()
        week.filterNotNull().forEach { day ->
            compose.onAllNodesWithContentDescription("$day, $day amount").assertCountEquals(1)
        }
        compose.onAllNodesWithContentDescription("Thu", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Wed amount").assertCountEquals(0)
    }

    @Test
    fun aTap_picksThePointUnderIt_andTappingItAgainPutsItAway() {
        show()
        tap(0.36f)
        compose.onAllNodesWithText("Wed amount").assertCountEquals(1)
        assertEquals(1, ticks.size)

        tap(0.36f)
        compose.onAllNodesWithText("Wed amount").assertCountEquals(0)
    }

    @Test
    fun aSidewaysDrag_scrubs_tickingOncePerNewPoint_andSkipsTheGap() {
        show()
        compose.onNodeWithTag(CHART).performTouchInput {
            swipe(percentOffset(0.05f, 0.3f), percentOffset(0.95f, 0.3f), durationMillis = 600)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithText("Sun amount").assertCountEquals(1)
        // Mon, Tue, Wed, Fri, Sat, Sun: six points, no tick for the untracked Thursday.
        assertEquals(6, ticks.size)
        assertEquals(0, scroll.value)
    }

    @Test
    fun aDragThatStartsUpward_scrollsThePage_andPicksNothing() {
        show()
        compose.onNodeWithTag(CHART).performTouchInput {
            // From low in the bars, inside the chart, not on the weekday labels under it.
            swipe(percentOffset(0.5f, 0.7f), percentOffset(0.52f, 0.0f), durationMillis = 150)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithText("Fri amount").assertCountEquals(0)
        compose.onAllNodesWithText("Wed amount").assertCountEquals(0)
        assertTrue(ticks.isEmpty())
        assertTrue(scroll.value > 0)
    }

    @Test
    fun lettingGo_keepsTheLabelUpBriefly_thenItGoes() {
        show()
        tap(0.36f)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onAllNodesWithText("Wed amount").assertCountEquals(1)
        compose.mainClock.advanceTimeBy(2_000)
        compose.onAllNodesWithText("Wed amount").assertCountEquals(0)
    }

    private fun tap(x: Float) {
        compose.onNodeWithTag(CHART).performTouchInput { click(percentOffset(x, 0.3f)) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private class RecordingHaptics(val played: MutableList<HapticFeedbackType>) : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            played += hapticFeedbackType
        }
    }

    private companion object {
        const val CHART = "chart"
    }
}
