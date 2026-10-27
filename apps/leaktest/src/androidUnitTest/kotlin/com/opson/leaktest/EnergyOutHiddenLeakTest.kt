package com.opson.leaktest

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import com.opson.libraries.core.fixed
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.ui.leak.LeakSurface
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
 * Energy out where the person has hidden it themselves. [BlindModeLeakTest] runs the presets that
 * hide calories; this runs Full, where calories show, with only energy out turned down. The
 * surfaces that carry it must then show no `metric:energyOut` node and no "Expended" label: a
 * hidden number takes its label with it. Softened counts too, because energy out has no soft word.
 *
 * The control runs the same surfaces in plain Full and needs the tag there, so a surface that
 * stopped rendering its energy at all cannot pass this for the wrong reason.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp")
class EnergyOutHiddenLeakTest(private val surface: LeakSurface) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun hiddenInFull_showsNoEnergyOut() = assertNothingShown(Visibility.Hide)

    @Test
    fun softenedInFull_showsNoEnergyOut() = assertNothingShown(Visibility.Soften)

    @Test
    fun control_plainFull_showsIt() {
        compose.render(surface, fullWith(emptyMap()))
        assertTrue(
            compose.scan().tagged.any { it.metric == Metric.EnergyOut },
            "${surface.id} shows no energy out even in Full, so the hidden case proves nothing",
        )
    }

    private fun assertNothingShown(level: Visibility) {
        compose.render(surface, fullWith(mapOf(Metric.EnergyOut to level)))
        val energyOut = compose.scan().tagged.filter { it.metric == Metric.EnergyOut }
        assertEquals(emptyList(), energyOut, "${surface.id} shows energy out set to $level")
        val labels = compose.onAllNodes(hasText("Expended", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(0, labels.size, "${surface.id} keeps an Expended label with energy out set to $level")
    }

    private fun fullWith(overrides: Map<Metric, Visibility>): VisibilityPolicy =
        VisibilityPolicy(NumbersSettings(preset = PresetId.Full, overrides = overrides), Clock.fixed(NOW), TimeZone.UTC)

    companion object {
        /** Every surface that draws energy out. [LeakTestControlTest] fails on one missing from here. */
        val ENERGY_OUT_SURFACES = setOf(
            "integrations", "today-expended", "today-exercise", "today-summary", "day-close-check-in",
            "today-macros", "today-weight", "today-select", "today-select-remove", "today-swipe-delete",
            "today-delete-confirm", "targets-deficit", "targets-surplus", "trends",
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = SURFACES.filter { it.id in ENERGY_OUT_SURFACES }.map { arrayOf<Any>(it) }
    }
}
