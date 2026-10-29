package com.opson.features.trends.impl

import com.opson.libraries.diary.WeighIn
import com.opson.libraries.medication.DoseMarks
import com.opson.libraries.medication.DoseUnit
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dose ticks on the weight chart (`release.medication`): where they sit, and where they never show. */
class WeightDoseTicksTest {

    private val today = TrendsFixtures.today
    private val marks = TrendsFixtures.doseMarks()

    private fun chart(preset: PresetId, weighIns: List<WeighIn> = TrendsFixtures.weighIns()) =
        WeightStateBuilder.build(TrendsFixtures.weightInputs(weighIns = weighIns, doseMarks = marks), WeightUi(), TrendsFixtures.policy(preset)).chart

    @Test
    fun withWeightShownTheStartAndTheStepUpAreTicked() {
        val ticks = chart(PresetId.Full)?.doseTicks.orEmpty()
        assertEquals(marks, ticks.map { it.mark })
        assertTrue(ticks.all { it.x in 0f..1f })
        assertTrue(ticks[0].x < ticks[1].x, "in date order along the line")
    }

    @Test
    fun atTrendOnlyTheTicksStayWithTheLine() {
        assertEquals(marks, chart(PresetId.MacrosOnly)?.doseTicks?.map { it.mark })
    }

    @Test
    fun inAModeThatHidesWeightThereIsNothing() {
        assertNull(chart(PresetId.NoNumbers), "no chart, so no ticks")
    }

    @Test
    fun aTickBetweenWeighInsSitsBetweenThemByTheDays() {
        // Weigh-ins ten days apart and one in the middle of the window: 0, 0.5, 1 along the line.
        val days = listOf(20, 10, 0).map { today.minus(it, DateTimeUnit.DAY) }
        val points = days.map { WeightPoint(it, 70.0, 70.0) }
        val ticks = DoseTicks.place(
            listOf(
                DoseMarks.Mark.Started(days[0]),
                DoseMarks.Mark.Changed(today.minus(15, DateTimeUnit.DAY), 0.5, DoseUnit.Mg),
                DoseMarks.Mark.Changed(days[2], 1.0, DoseUnit.Mg),
            ),
            points,
        )
        assertEquals(listOf(0f, 0.25f, 1f), ticks.map { it.x })
    }

    @Test
    fun aDoseOutsideTheWeighInsOrUnderOneReadingIsLeftOff() {
        val points = listOf(WeightPoint(LocalDate(2026, 10, 1), 70.0, 70.0), WeightPoint(LocalDate(2026, 10, 8), 70.0, 70.0))
        assertEquals(emptyList(), DoseTicks.place(listOf(DoseMarks.Mark.Started(LocalDate(2026, 9, 1))), points))
        assertEquals(emptyList(), DoseTicks.place(listOf(DoseMarks.Mark.Started(LocalDate(2026, 10, 1))), points.take(1)))
    }
}
