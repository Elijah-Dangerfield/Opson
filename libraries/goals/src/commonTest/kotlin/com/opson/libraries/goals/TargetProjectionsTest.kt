package com.opson.libraries.goals

import com.opson.libraries.nutrition.GoalBaseline
import com.opson.libraries.nutrition.KCAL_PER_KG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A target with no goal weight, drawn against a typical day's burn. */
class TargetProjectionsTest {

    private val baseline = GoalBaseline(burnKcal = 2480)

    @Test
    fun aDeficit_fallsWeekByWeek_withTheBandEitherSide() {
        val projection = assertNotNull(TargetProjections.weight(baseline, 88.0, GoalFixtures.monday, 1830, 1930, 2030))
        assertEquals(TargetProjections.WEEKS + 1, projection.points.size)
        val last = projection.points.last()
        // 550 a day under for 84 days.
        assertEquals(88.0 - 550.0 * 84 / KCAL_PER_KG, last.targetKg, 1e-9)
        assertTrue(last.lowKcalKg < last.targetKg && last.targetKg < last.highKcalKg, "eating at the low end goes furthest")
        assertNull(projection.targetReached, "nothing to reach without a goal weight")
    }

    @Test
    fun digestingFood_burnsATenthOfWhatIsEaten() {
        val digesting = assertNotNull(TargetProjections.weight(baseline.copy(digesting = true), 88.0, GoalFixtures.monday, 1930, 1930, 1930))
        // 2,480 + 193 burned against 1,930 eaten: 743 a day under.
        assertEquals(88.0 - 743.0 * 7 / KCAL_PER_KG, digesting.points[1].targetKg, 1e-9)
    }

    @Test
    fun aTargetAtTheBurn_drawsNothing() {
        assertNull(TargetProjections.weight(baseline, 88.0, GoalFixtures.monday, 2470, 2480, 2490))
    }

    @Test
    fun anEnergyBalanceGoal_projectsFromItsOwnBand() {
        val projection = assertNotNull(TargetProjections.of(GoalFixtures.balance, GoalFixtures.WEIGHT_KG, GoalFixtures.today))
        assertTrue(projection.points.last().targetKg < GoalFixtures.WEIGHT_KG)
        assertNull(TargetProjections.of(GoalFixtures.steps, GoalFixtures.WEIGHT_KG, GoalFixtures.today))
    }
}
