package com.opson.libraries.nutrition

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Macro goals in grams or percents of the calorie goal, at 4, 4 and 9 kcal a gram. The grams are
 * what is stored and what every screen reads; percents are worked out from them. The properties
 * run over many seeded random goals, so a rounding rule that only breaks at an odd calorie goal
 * still fails here.
 */
class MacroGoalsTest {

    private val base = Targets.Default
    private val random = Random(20261027)

    /** Calorie goals a person can have: the equation's lowest sensible to well above any athlete's. */
    private fun goals(count: Int = 300, highest: Int = 5000): List<Int> = List(count) { random.nextInt(1000, highest + 1) }

    @Test
    fun theMacrosEnergy_isFourFourNine() {
        assertEquals(120 * 4 + 250 * 4 + 70 * 9, base.macroKcal)
        assertEquals(MacroFit(2110, 2100), base.macroFit(2100))
        assertTrue(base.macroFit(2100).fits, "10 kcal out is rounding, not a choice")
        assertFalse(base.macroFit(2000).fits)
        assertEquals(110, base.macroFit(2000).gapKcal)
    }

    @Test
    fun everyPreset_addsTo100() {
        MacroPreset.entries.forEach { preset ->
            assertEquals(100, SplitMacros.sumOf(preset::percent), "$preset")
        }
    }

    @Test
    fun aPreset_fitsTheGoal_andReadsBackAsItself_atAnyGoal() {
        goals().forEach { goal ->
            MacroPreset.entries.forEach { preset ->
                val split = base.withPreset(preset, goal)
                assertTrue(split.macroFit(goal).fits, "$preset at $goal: ${split.macroFit(goal)}")
                assertEquals(preset, split.preset(goal), "$preset at $goal")
                SplitMacros.forEach { assertEquals(preset.percent(it), split.macroPercent(it, goal), "$preset $it at $goal") }
            }
        }
    }

    @Test
    fun aPercent_roundTrips_throughWholeGrams() {
        goals().forEach { goal ->
            SplitMacros.forEach { metric ->
                val percent = random.nextInt(101)
                // Past MAX_MACRO_G the grams are held, which is the guard working, not a rounding slip.
                if (gramsFor(metric, percent, goal) > MAX_MACRO_G) return@forEach
                assertEquals(percent, base.withMacroPercent(metric, percent, goal).macroPercent(metric, goal), "$metric $percent% of $goal")
            }
        }
    }

    @Test
    fun fittingTheMacros_toTheGoal_keepsTheSplit_andAddsUp() {
        // Up to 3,500 kcal, so even a split that is nearly all carbs stays under MAX_MACRO_G.
        goals(highest = 3500).forEach { goal ->
            val start = base.copy(proteinG = random.nextInt(1, 300), carbsG = random.nextInt(1, 400), fatG = random.nextInt(1, 200))
            val fitted = start.fitMacrosTo(goal)
            assertTrue(fitted.macroFit(goal).fits, "$start fitted to $goal: ${fitted.macroFit(goal)}")
            // Each macro keeps its share of the whole, to within a gram's rounding.
            SplitMacros.forEach { metric ->
                val before = start.grams(metric) * metric.kcalPerGram / start.macroKcal
                val after = fitted.grams(metric) * metric.kcalPerGram / fitted.macroKcal
                assertTrue(abs(before - after) < 0.01, "$metric's share moved from $before to $after at $goal")
            }
        }
    }

    @Test
    fun fittingNothing_takesTheBalancedSplit() {
        val empty = base.copy(proteinG = 0, carbsG = 0, fatG = 0)
        assertEquals(MacroPreset.Balanced, empty.fitMacrosTo(2100).preset(2100))
    }

    @Test
    fun anyMacroEdit_leavesTheCalorieGoalAlone() {
        goals(100).forEach { goal ->
            val edited = base.withMacroGrams(Metric.Protein, random.nextInt(0, 400))
                .withMacroPercent(Metric.Fat, random.nextInt(0, 60), goal)
                .withPreset(MacroPreset.entries.random(random), goal)
                .fitMacrosTo(goal)
            assertEquals(base.energyKcal to (base.minKcal to base.maxKcal), edited.energyKcal to (edited.minKcal to edited.maxKcal))
            assertEquals(base.style, edited.style)
        }
    }

    @Test
    fun grams_areHeldToZeroAndTheMost() {
        assertEquals(0, base.withMacroGrams(Metric.Carbs, -20).carbsG)
        assertEquals(MAX_MACRO_G, base.withMacroGrams(Metric.Carbs, 12_000).carbsG)
        assertEquals(0, base.withMacroPercent(Metric.Fat, -5, 2100).fatG)
        assertEquals(gramsFor(Metric.Fat, 100, 2100), base.withMacroPercent(Metric.Fat, 140, 2100).fatG)
    }

    @Test
    fun aMacroEdit_givesRatioModeANewStartingPoint() {
        val scaled = base.raise(scaleMacros = true)
        val edited = scaled.withMacroGrams(Metric.Protein, 150)
        assertNull(edited.ratioBasis, "the next ratio step scales from the grams just set")
        assertEquals(150 * 2200 / 2150.0, edited.raise(scaleMacros = true).proteinG.toDouble(), 0.5)
    }

    @Test
    fun steps_snapToFiveGrams_orFivePercent() {
        val odd = base.copy(proteinG = 123)
        assertEquals(125, odd.stepMacro(Metric.Protein, up = true, percent = false, goalKcal = 2100).proteinG)
        assertEquals(120, odd.stepMacro(Metric.Protein, up = false, percent = false, goalKcal = 2100).proteinG)
        assertEquals(0, base.copy(proteinG = 3).stepMacro(Metric.Protein, up = false, percent = false, goalKcal = 2100).proteinG)
        // 120 g protein is 23% of 2,100: up goes to 25%, down to 20%.
        assertEquals(25, base.stepMacro(Metric.Protein, up = true, percent = true, goalKcal = 2100).macroPercent(Metric.Protein, 2100))
        assertEquals(20, base.stepMacro(Metric.Protein, up = false, percent = true, goalKcal = 2100).macroPercent(Metric.Protein, 2100))
    }

    @Test
    fun theMacrosCanSetTheGoal_onlyWhereTheGoalIsYourOwnNumber() {
        val more = base.withMacroGrams(Metric.Carbs, 300)
        val goal = requireNotNull(more.useMacroEnergy())
        assertEquals(2310, goal.energyKcal, "2,110 plus 200 from 50 g more carbs, rounded to 10")
        assertEquals(2110 to 2510, goal.minKcal to goal.maxKcal, "the range moves with it")
        assertTrue(goal.macroFit(goal.energyKcal).fits)
        assertEquals(2310, requireNotNull(more.useExact().useMacroEnergy()).energyKcal)
        assertNull(more.useDeficit(2600).useMacroEnergy(), "a deficit follows Burned")
        assertNull(more.useSurplus(null).useMacroEnergy(), "so does a surplus")
        assertNull(more.useByDay().useMacroEnergy(), "each day group has its own")
    }

    @Test
    fun theMacrosNeverSetTheGoal_pastTheLimits() {
        val less = base.withMacroGrams(Metric.Carbs, 150)
        assertEquals(1710, less.useMacroEnergy()?.energyKcal, "under where this person started is fine")
        val tiny = base.withMacroGrams(Metric.Carbs, 0).withMacroGrams(Metric.Fat, 20).withMacroGrams(Metric.Protein, 50)
        assertNull(tiny.useMacroEnergy(), "380 would take the low end under the minimum")
        val huge = base.withMacroGrams(Metric.Carbs, 999).withMacroGrams(Metric.Fat, 400)
        assertNull(huge.useMacroEnergy(), "about 8,000 would take the high end past the maximum")
    }
}
