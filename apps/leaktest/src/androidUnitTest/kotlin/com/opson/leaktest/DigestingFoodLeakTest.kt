package com.opson.leaktest

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.ui.leak.LeakSurface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Count digesting food" where calories are hidden but energy out is turned up by hand. Digesting
 * food is a tenth of what was eaten, so its line under Burned is the day's calories with one
 * multiplication. Every mode in [MODES_UNDER_TEST] runs here with energy out set to Show, a mix
 * no preset has, and the surfaces that list digesting food must not draw its line.
 *
 * The line's step in Burned's total is not visible to a scan; the state builders' tests own that
 * (`TodayActivityStateBuilderTest`, `EnergyTodayTest`). The control needs the line in plain Full,
 * so a fixture that stopped counting digesting food cannot pass this for the wrong reason.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp")
class DigestingFoodLeakTest(private val surface: LeakSurface, private val mode: LeakMode) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun energyOutShown_caloriesNot_noDigestingLine() {
        val settings = mode.settings.copy(overrides = mode.settings.overrides + (Metric.EnergyOut to Visibility.Show))
        compose.render(surface, mode.copy(settings = settings).policy())
        assertEquals(0, compose.digestingLines(), "${surface.id} shows digesting food in $mode with energy out on show")
    }

    companion object {
        /** Every surface with a "Digesting food" line. [LeakTestControlTest] fails on one missing from here. */
        val DIGESTING_SURFACES = setOf("today-activity", "today-activity-health", "energy-today", "energy-explainer-burned", "energy-explainer-digesting")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} in {1}")
        fun cases(): List<Array<Any>> = MODES_UNDER_TEST.flatMap { mode ->
            SURFACES.filter { it.id in DIGESTING_SURFACES }.map { arrayOf<Any>(it, mode) }
        }
    }
}

/** The digesting food line in plain Full, for [DigestingFoodLeakTest]'s surfaces. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp")
class DigestingFoodControlTest(private val surface: LeakSurface) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun control_plainFull_showsIt() {
        compose.render(surface, policyFor(PresetId.Full))
        assertTrue(compose.digestingLines() > 0, "${surface.id} shows no digesting food even in Full, so the hidden case proves nothing")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = SURFACES.filter { it.id in DigestingFoodLeakTest.DIGESTING_SURFACES }.map { arrayOf<Any>(it) }
    }
}

/**
 * The digesting food line's label, whole: "Digesting food" (Today's Activity card) or "Digesting
 * food (TEF)" (Energy today and the (i) sheets). A sentence or a title that only names the term,
 * like the TEF sheet's "Digesting food (thermic effect of food, TEF)", is words, not the line.
 */
internal fun ComposeContentTestRule.digestingLines(): Int =
    onAllNodes(hasText("Digesting food") or hasText("Digesting food (TEF)"), useUnmergedTree = true).fetchSemanticsNodes().size
