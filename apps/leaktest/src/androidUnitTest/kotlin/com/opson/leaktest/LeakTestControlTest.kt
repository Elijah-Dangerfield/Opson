package com.opson.leaktest

import androidx.compose.ui.test.junit4.createComposeRule
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.leak.LeakSurface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse

/**
 * The control for [BlindModeLeakTest]: every surface marked `showsCaloriesInFull`, rendered in
 * Full, must show at least one calorie tag or [LEAK] match. Without it the leak test would pass
 * just as happily on a surface that renders nothing, or a scan that reads nothing. The prototype
 * runs Full beside Blind for the same reason.
 *
 * The same render catches a surface that draws energy out without being in
 * [EnergyOutHiddenLeakTest.ENERGY_OUT_SURFACES], a hand list that otherwise only grows when
 * someone remembers it.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp")
class LeakTestControlTest(private val surface: LeakSurface) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsCaloriesInFull() {
        compose.render(surface, policyFor(PresetId.Full))
        val scan = compose.scan()
        assertFalse(
            scan.isEmpty,
            "${surface.id} (${surface.label}) shows no calories in Full, so the leak test proves nothing about it. " +
                "Either its fixture lost its numbers or it should not be marked showsCaloriesInFull.",
        )
        assertFalse(
            scan.tagged.any { it.metric == Metric.EnergyOut } && surface.id !in EnergyOutHiddenLeakTest.ENERGY_OUT_SURFACES,
            "${surface.id} shows energy out, so EnergyOutHiddenLeakTest.ENERGY_OUT_SURFACES has to list it.",
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} in Full")
        fun cases(): List<Array<Any>> = SURFACES.filter { it.showsCaloriesInFull }.map { arrayOf<Any>(it) }
    }
}
