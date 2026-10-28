package com.opson.libraries.ui.components.dialog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.opson.libraries.ui.PreviewContent
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PreviewContextConfigurationEffect
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Dismiss closes the shake dialog the way a tap outside does: it plays its exit, then reports
 * the dismissal. Before, Dismiss reported at once and the caller's back navigation cut the
 * dialog off mid-frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShakeDialogTest {

    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalResourceApi::class)
    @Test
    fun dismissPlaysTheExitBeforeReportingIt() {
        var dismissed = 0
        compose.setContent {
            // Compose resources only take the Android context from this effect, and only under
            // inspection mode, as the leak test sets them up. The dialog's labels need it.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PreviewContextConfigurationEffect()
                PreviewContent {
                    ShakeDialog(headline = "I felt that.", subtext = null, onDismiss = { dismissed++ }, onReportBug = {})
                }
            }
        }
        compose.onNodeWithText("I felt that.").assertExists()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Dismiss").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(MID_EXIT_MILLIS)
        assertEquals(0, dismissed)
        compose.onAllNodesWithText("I felt that.").assertCountEquals(1)

        compose.mainClock.advanceTimeBy(PAST_EXIT_MILLIS)
        assertEquals(1, dismissed)
    }

    private companion object {
        const val MID_EXIT_MILLIS = 60L
        const val PAST_EXIT_MILLIS = 1_000L
    }
}
