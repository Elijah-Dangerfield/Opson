package com.opson.leaktest

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.opson.libraries.core.fixed
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.ui.leak.LeakSurface
import com.opson.libraries.nutrition.ui.testTag
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Vitamins and minerals where they are hidden. Full and Sprint show them; every other preset
 * hides them, and so does Full with micros turned off by hand. In each of those, the surfaces
 * that draw micros must compose no node tagged `metric:micros`.
 *
 * The control case renders the same surfaces in plain Full and needs the tag there, so a surface
 * that stopped drawing micros at all cannot pass the hidden cases for the wrong reason.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp")
class MicrosHiddenLeakTest(private val surface: LeakSurface, private val case: MicrosCase) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun microsShowOnlyWhereTheModeShowsThem() {
        compose.render(surface, case.policy)
        val shown = compose.microTexts()
        if (case.hidden) {
            assertEquals(emptyList(), shown, "${surface.id} shows vitamins or minerals in $case")
        } else {
            assertTrue(shown.isNotEmpty(), "${surface.id} shows no vitamins or minerals in $case, so the hidden cases prove nothing")
        }
    }

    companion object {
        /** Every surface that draws micros. [LeakTestControlTest] fails on one missing from here. */
        val MICROS_SURFACES = setOf("nutrients", "nutrients-week", "logger-amount-nutrients")

        /** Derived, so a preset added later is covered the day it hides micros. */
        private val PRESETS_HIDING_MICROS: List<PresetId> = Presets.all
            .filter { it.level(Metric.Micros) == Visibility.Hide }
            .map { it.id }

        private val CASES: List<MicrosCase> = PRESETS_HIDING_MICROS.map { MicrosCase("$it", policyFor(it), hidden = true) } +
            MicrosCase("Full, micros off", fullWith(Metric.Micros to Visibility.Hide), hidden = true) +
            MicrosCase("Full (control)", policyFor(PresetId.Full), hidden = false)

        private fun fullWith(override: Pair<Metric, Visibility>): VisibilityPolicy =
            VisibilityPolicy(NumbersSettings(preset = PresetId.Full, overrides = mapOf(override)), Clock.fixed(NOW), TimeZone.UTC)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} in {1}")
        fun cases(): List<Array<Any>> = SURFACES.filter { it.id in MICROS_SURFACES }.flatMap { surface ->
            CASES.map { arrayOf<Any>(surface, it) }
        }
    }
}

/** A mode to render in, and whether micros must be absent from it. */
class MicrosCase(private val name: String, val policy: VisibilityPolicy, val hidden: Boolean) {
    override fun toString() = name
}

/** What every node tagged `metric:micros` shows, every window included, on the unmerged tree. */
internal fun ComposeContentTestRule.microTexts(): List<String> =
    onAllNodes(hasTestTag(Metric.Micros.testTag()), useUnmergedTree = true).fetchSemanticsNodes()
        .map { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() }
