package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How a shown amount spells its goal in each [GoalStyle], and that the style
 * only changes the spelling: soften words and bar fractions read `of` the same
 * way in every style.
 */
class GoalStyleTest {

    private val full = showAll()

    private fun text(metric: Metric, value: Double, of: Double?, goal: GoalStyle, bare: Boolean = false) =
        full.text(metric, value, FormatContext(of = of, bare = bare, goal = goal))

    @Test
    fun default_macrosSayOf_energyDoesNot() {
        assertEquals("47 g of 120 g", text(Metric.Protein, 47.0, 120.0, GoalStyle.Default))
        assertEquals("1,245 kcal", text(Metric.Calories, 1245.0, 2305.0, GoalStyle.Default))
        assertEquals("1,245", text(Metric.Calories, 1245.0, 2305.0, GoalStyle.Default, bare = true))
    }

    @Test
    fun none_dropsTheGoal() {
        assertEquals("47 g", text(Metric.Protein, 47.0, 120.0, GoalStyle.None))
        assertEquals("1,245 kcal", text(Metric.Calories, 1245.0, 2305.0, GoalStyle.None))
    }

    @Test
    fun of_spellsTheGoalForEnergyToo() {
        assertEquals("47 g of 120 g", text(Metric.Protein, 47.0, 120.0, GoalStyle.Of))
        assertEquals("1,245 kcal of 2,305", text(Metric.Calories, 1245.0, 2305.0, GoalStyle.Of))
        assertEquals("1,245 of 2,305", text(Metric.Calories, 1245.0, 2305.0, GoalStyle.Of, bare = true))
    }

    @Test
    fun slash() {
        assertEquals("47 / 120 g", text(Metric.Protein, 47.0, 120.0, GoalStyle.Slash))
        assertEquals("1,245 / 2,305", text(Metric.Calories, 1245.0, 2305.0, GoalStyle.Slash, bare = true))
        assertEquals("1,245 / 2,305 kcal", text(Metric.Calories, 1245.0, 2305.0, GoalStyle.Slash))
    }

    @Test
    fun everyStyle_withoutAGoal_isThePlainAmount() {
        GoalStyle.entries.forEach { goal ->
            assertEquals("47 g", text(Metric.Fiber, 47.0, null, goal), "$goal")
            assertEquals("1,245", text(Metric.Calories, 1245.0, null, goal, bare = true), "$goal")
            assertEquals("47 g", text(Metric.Fat, 47.0, 0.0, goal), "$goal with a zero goal")
        }
    }

    @Test
    fun style_neverChangesTheFraction() {
        GoalStyle.entries.forEach { goal ->
            val display = full.resolve(Metric.Protein, 60.0, Surface.Today, FormatContext(of = 120.0, goal = goal))
            assertEquals(0.5f, (display as Display.Shown).fraction, "$goal")
        }
    }

    @Test
    fun style_neverChangesSoftWords() {
        val fuel = policy(PresetId.Fuel)
        GoalStyle.entries.forEach { goal ->
            assertEquals(
                "Partway through",
                fuel.text(Metric.Calories, 1000.0, FormatContext(of = 2100.0, goal = goal)),
                "$goal",
            )
        }
    }

    @Test
    fun style_doesNotTouchMetricsWithoutAnAmountGoal() {
        GoalStyle.entries.forEach { goal ->
            assertEquals("750 of 2,000 ml", text(Metric.Water, 750.0, 2000.0, goal), "$goal")
            assertEquals("62%", text(Metric.Micros, 62.0, 100.0, goal), "$goal")
        }
    }
}
