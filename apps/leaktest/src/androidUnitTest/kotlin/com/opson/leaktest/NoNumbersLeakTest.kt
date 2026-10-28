package com.opson.leaktest

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
 * No numbers (design sync 4, 13.5d): "No amounts anywhere". [BlindModeLeakTest] holds every mode
 * to no calorie amount; this holds No numbers to no nutrition amount of any kind. On every
 * surface, no node tagged with any [Metric] may show a digit: not protein, not weight, not water,
 * not a share. Water's glasses are a bar with no text, which is all No numbers keeps.
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
                // The spoken words too: a chart point is a node that only speaks, and a screen
                // reader must hear no more than the screen shows.
                .map { node ->
                    (node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ")
                }
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
