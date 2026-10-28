package com.opson.leaktest

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which foods gave a nutrient. All numbers and Macros with grams list each food's amount and
 * share; Macros only ranks the foods with neither, and No numbers shows nothing of the nutrient.
 * In the last two, no node tagged with a metric may show a digit, and no text anywhere may read
 * as an amount or a share ("12 g", "40%"): the sheet's caption and food names carry none.
 *
 * The control renders the same sheets in All numbers and needs an amount there, so a sheet that
 * stopped drawing amounts at all cannot pass the hidden cases for the wrong reason.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp")
class NutrientFoodsLeakTest(private val surface: LeakSurface, private val preset: PresetId) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun amountsShowOnlyWhereTheModeShowsThem() {
        compose.render(surface, policyFor(preset))
        val tagged = compose.taggedDigits()
        val loose = compose.segments().filter { AMOUNT.containsMatchIn(it) }
        if (preset in SHOWING) {
            assertTrue(tagged.isNotEmpty(), "${surface.id} shows no amount in $preset, so the hidden cases prove nothing")
        } else {
            assertEquals(emptyList(), tagged, "${surface.id} shows an amount in $preset")
            assertEquals(emptyList(), loose, "${surface.id} reads as an amount in $preset")
        }
    }

    companion object {
        val FOODS_SURFACES = setOf("nutrient-foods", "nutrient-foods-week", "nutrient-foods-protein")

        /** The two modes that show grams and micros. */
        private val SHOWING = setOf(PresetId.Full, PresetId.MacrosWithGrams)

        /** A number with a nutrient unit or a percent after it. */
        private val AMOUNT = Regex("""\d[\d,.]*\s*(g|mg|µg|%)(\s|$)""")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} in {1}")
        fun cases(): List<Array<Any>> = SURFACES.filter { it.id in FOODS_SURFACES }.flatMap { surface ->
            PresetId.entries.map { arrayOf<Any>(surface, it) }
        }
    }
}

private fun ComposeContentTestRule.taggedDigits(): List<String> = Metric.entries.flatMap { metric ->
    onAllNodes(hasTestTag(metric.testTag()), useUnmergedTree = true).fetchSemanticsNodes()
        .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text } }
        .filter { text -> text.any(Char::isDigit) }
        .map { "${metric.testTag()} showing \"$it\"" }
}
