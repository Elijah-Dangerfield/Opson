package com.opson.leaktest

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.leak.LeakSurface
import com.opson.libraries.nutrition.ui.testTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.fail

/**
 * No numbers (design sync 4, 13.5d): "No amounts anywhere". [BlindModeLeakTest] holds every
 * mode to no calorie amount; this holds No numbers to no nutrition amount of any kind. On every
 * surface, no node tagged with any [Metric] may show a digit, in its text or in what a screen
 * reader says for it: not protein, not weight, not water, not a share. Water's glasses are a bar
 * with no text, which is all No numbers keeps.
 *
 * Amounts of food (1 bowl, a plate's 350 g) are not nutrition and carry no metric tag, so they
 * stay, as the design draws them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp")
class NoNumbersLeakTest(private val surface: LeakSurface) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsNoNutritionAmount() {
        compose.render(surface, policyFor(PresetId.NoNumbers))
        val leaks = Metric.entries.flatMap { metric ->
            compose.onAllNodes(hasTestTag(metric.testTag()), useUnmergedTree = true).fetchSemanticsNodes()
                .map { node -> node.texts().joinToString(" ") }
                .filter { text -> text.any(Char::isDigit) }
                .map { "${metric.testTag()} showing \"$it\"" }
        }
        if (leaks.isNotEmpty()) fail("${surface.id} (${surface.label}) shows an amount in No numbers:\n  " + leaks.joinToString("\n  "))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} in no_numbers")
        fun cases(): List<Array<Any>> = SURFACES.map { arrayOf<Any>(it) }
    }
}
