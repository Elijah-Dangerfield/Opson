package com.opson.libraries.ui.components.button

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * What a screen reader hears from a button. A disabled one (onboarding's Continue before an
 * answer is picked) has to say it is dimmed; before, it was announced as a working button that
 * did nothing when tapped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ButtonSemanticsTest {

    @get:Rule val compose = createComposeRule()

    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and hasText(LABEL)

    @Test
    fun aDisabledButtonIsOneDimmedButton() {
        compose.setContent {
            AppTheme(darkTheme = false) {
                ButtonPrimary(onClick = {}, enabled = false) { Text(LABEL) }
            }
        }

        compose.onNode(button).assertIsNotEnabled()
    }

    @Test
    fun anEnabledButtonIsOneButtonThatTaps() {
        var taps = 0
        compose.setContent {
            AppTheme(darkTheme = false) {
                ButtonPrimary(onClick = { taps++ }) { Text(LABEL) }
            }
        }

        compose.onNode(button).assertIsEnabled().performClick()
        assertEquals(1, taps)
    }

    @Test
    fun aDeepButtonIsOneButtonThatTaps() {
        var taps = 0
        compose.setContent {
            AppTheme(darkTheme = false) {
                ButtonPrimary(onClick = { taps++ }, deep = true) { Text(LABEL) }
            }
        }

        compose.onNode(button).assertIsEnabled().performClick()
        assertEquals(1, taps)
    }

    private companion object {
        // A constant rather than a literal in Text(): test copy, not a string for VerifyStrings.
        const val LABEL = "Continue"
    }
}
