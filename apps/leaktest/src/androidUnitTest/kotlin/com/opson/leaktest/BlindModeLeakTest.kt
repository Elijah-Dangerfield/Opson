package com.opson.leaktest

import androidx.compose.ui.test.junit4.createComposeRule
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.ui.leak.LeakSurface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.fail

/**
 * The handoff's acceptance test: with calories hidden, no surface shows a calorie amount or an
 * "N left". Every surface in [SURFACES], in every preset in [PRESETS_UNDER_TEST] (Blind first),
 * must compose no node tagged `metric:calories`, `metric:remaining` or `metric:energyOut`, and no
 * text matching [LEAK]. Softened presets may tag a word, never a digit. Delayed presets run at
 * [NOW], before the reveal, and may show only what [REVEALED_BY_DESIGN] lists.
 *
 * A failure here is a real leak until proven otherwise: the policy is the only formatter, so a hit
 * means a composable formatted a number itself or a fixture built a `Display` around the policy.
 * Fix the screen, not this test. [LeakTestControlTest] is what proves the scan sees anything.
 *
 * The window is 4000dp tall so lazy lists compose every fixture row: a leak below the fold is
 * still a leak.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp")
class BlindModeLeakTest(private val surface: LeakSurface, private val preset: PresetId) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsNoCalorieAmount() {
        val policy = policyFor(preset)
        compose.render(surface, policy)
        val scan = compose.scan()

        val allowed = (REVEALED_BY_DESIGN[preset to surface.id] as? Reveal.Only)?.values.orEmpty()
        val tagged = scan.tagged.filterNot { tag ->
            tag.text in allowed || (policy.level(tag.metric) == Visibility.Soften && tag.text.none(Char::isDigit))
        }
        val hits = scan.hits.filterNot { it.value in allowed }
        if (tagged.isEmpty() && hits.isEmpty()) return

        fail(
            buildString {
                appendLine("${surface.id} (${surface.label}) shows calories in $preset")
                tagged.forEach { appendLine("  tagged: $it") }
                hits.forEach { appendLine("  text: $it") }
            },
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} in {1}")
        fun cases(): List<Array<Any>> = PRESETS_UNDER_TEST.flatMap { preset ->
            SURFACES
                .filterNot { REVEALED_BY_DESIGN[preset to it.id] is Reveal.WholeSurface }
                .map { arrayOf<Any>(it, preset) }
        }
    }
}
