package com.opson.libraries.goals.ui

import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ProjectionPoint
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightProjection
import com.opson.libraries.nutrition.WeightUnit
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A projection laid out for the chart: heights, the goal line, and the words around it. */
class ProjectionChartsTest {

    private val lose = assertNotNull(GoalFixtures.projection(GoalFixtures.lose))

    @Test
    fun everySeries_sharesOneScale_paddedOffTheEdges() {
        val shape = assertNotNull(ProjectionCharts.shape(lose, goalKg = 80.0))
        val all = shape.low + shape.middle + shape.high + listOfNotNull(shape.goal)
        assertTrue(all.all { it in 0.05f..0.95f }, "nothing touches the top or bottom: $all")
        assertTrue(shape.start > shape.goal!!, "a loss starts above its goal")
        assertEquals(shape.goal!!, shape.middle.last(), 1e-6f)
        assertTrue(shape.low.last() <= shape.middle.last() && shape.high.last() >= shape.middle.last())
    }

    @Test
    fun theTarget_reachesTheGoal_whereTheForecastSays() {
        val shape = assertNotNull(ProjectionCharts.shape(lose, goalKg = 80.0))
        val reached = assertNotNull(shape.reachedAt)
        assertTrue(reached in 0.5f..1f, "the slowest series sets the span, so the middle gets there before it ends: $reached")
    }

    @Test
    fun aSmallChange_isDrawnOnAtLeastTwoKilograms() {
        val flat = WeightProjection(
            points = (0..4).map { week ->
                val kg = 70.0 - week * 0.05
                ProjectionPoint(GoalFixtures.monday.plus(DatePeriod(days = week * 7)), kg, kg, kg)
            },
            lowKcalReached = null,
            targetReached = null,
            highKcalReached = null,
        )
        val shape = assertNotNull(ProjectionCharts.shape(flat, goalKg = null))
        assertTrue(shape.middle.first() - shape.middle.last() < 0.15f, "0.2 kg doesn't fill the chart")
    }

    @Test
    fun onePoint_drawsNothing() {
        val single = WeightProjection(listOf(ProjectionPoint(GoalFixtures.monday, 70.0, 70.0, 70.0)), null, null, null)
        assertNull(ProjectionCharts.shape(single, goalKg = null))
    }

    @Test
    fun theWords_comeThroughThePolicy_andOnlyWhereWeightShows() {
        val ui = assertNotNull(ProjectionCharts.ui(lose, 80.0, GoalDirection.Lose, WeightUnit.Kg, policy(PresetId.Full)))
        assertEquals("88.0 kg", ui.startWeight)
        assertEquals("80.0 kg", ui.endWeight)
        assertTrue(ui.toGoal)
        val forecast = assertNotNull(ui.forecast)
        assertNotNull(forecast.middle)
        assertNotNull(forecast.best)
        assertNotNull(forecast.worst)

        assertNull(ProjectionCharts.ui(lose, 80.0, GoalDirection.Lose, WeightUnit.Kg, policy(PresetId.NoNumbers)))
    }

    private fun policy(preset: PresetId) = VisibilityPolicy(NumbersSettings(preset = preset), Clock.System, TimeZone.UTC)
}
