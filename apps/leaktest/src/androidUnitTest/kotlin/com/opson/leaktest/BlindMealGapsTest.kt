package com.opson.leaktest

import androidx.compose.ui.test.junit4.createComposeRule
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.leak.LeakSurface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Patterns in No numbers shows when meals happen, never how long between them.
 * "Longest gap" and "about 45 min later" can feed restriction for exactly the people who pick it
 * (design v3, 5.7), so no Patterns surface may show a gap or a duration there. The Full case is the control:
 * it proves the fixture has a gap to hide and that the scan reads it.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp")
class BlindMealGapsTest(private val surface: LeakSurface) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun noNumbersShowsNoGapOrDuration() {
        compose.render(surface, policyFor(PresetId.NoNumbers))
        val hits = GAP.findAll(compose.segments().joinToString(" ")).map { it.value }.toList()
        if (hits.isNotEmpty()) fail("${surface.id} shows meal gaps in No numbers: $hits")
    }

    @Test
    fun fullShowsThem_soTheScanSeesThem() {
        compose.render(surface, policyFor(PresetId.Full))
        val text = compose.segments().joinToString(" ")
        assertTrue(GAP.containsMatchIn(text), "${surface.id} in Full shows no gap, so the No numbers case proves nothing")
    }

    companion object {
        /** A gap line, a shift line, or any duration in hours or minutes. */
        private val GAP = Regex("""longest gap|\blater\b|\d+\s*(h|hr|hours?|min|minutes?)\b""", RegexOption.IGNORE_CASE)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = SURFACES.filter { it.id.startsWith("patterns") }.map { arrayOf<Any>(it) }
    }
}
