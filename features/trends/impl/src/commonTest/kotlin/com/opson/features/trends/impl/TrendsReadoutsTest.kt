package com.opson.features.trends.impl

import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ui.testTag
import com.opson.libraries.ui.components.ChartReadout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What an inspected chart point may say, per mode: a day in every mode that draws the chart, an
 * amount only where the card itself shows that amount. The leak tests scan the same readouts as
 * the charts' accessibility nodes; this pins them at the builder, one branch at a time.
 */
class TrendsReadoutsTest {

    private fun trends(preset: PresetId = PresetId.Full, settings: Settings = Settings()) = TrendsStateBuilder.build(
        TrendsFixtures.trendsInputs(settings, expended = TrendsFixtures.expended()),
        TrendsFixtures.policy(preset),
    )

    private fun weight(preset: PresetId = PresetId.Full) =
        WeightStateBuilder.build(TrendsFixtures.weightInputs(), WeightUi(), TrendsFixtures.policy(preset))

    private val List<ChartReadout?>.values get() = filterNotNull().flatMap { point -> point.readings.mapNotNull { it.value } }

    @Test
    fun eaten_aPointPerBar_gapsWhereTheBarIsAGap() {
        val card = assertNotNull(trends().intake)
        assertEquals(card.bars.size, card.readouts.size)
        card.bars.zip(card.readouts).forEach { (bar, readout) -> assertEquals(bar.fraction == null, readout == null) }
        // Thursday the 22nd is untracked in the fixture week.
        assertNull(card.readouts[3])
    }

    @Test
    fun eaten_full_saysTheDayAndItsCalories_tagged() {
        val card = assertNotNull(trends().intake)
        val wednesday = assertNotNull(card.readouts[2])
        assertEquals("Wed, Oct 21", wednesday.title)
        val reading = wednesday.readings.single()
        assertTrue(reading.value!!.endsWith("kcal"))
        assertEquals(Metric.Calories.testTag(), reading.tag)
        assertEquals("Today", card.readouts.last()?.title)
    }

    @Test
    fun eaten_weekly_namesTheWeek_andCallsItADailyAverage() {
        val card = assertNotNull(trends(settings = Settings(trendsRange = TrendsRange.FourWeeks)).intake)
        val last = assertNotNull(card.readouts.last())
        // The fixture's today is Sunday Oct 25, the last day of the week that started on the 19th.
        assertEquals("Oct 19 to 25", last.title)
        assertNotNull(last.readings.single().label)
    }

    @Test
    fun noAmountMode_noEatenOrExpendedPoints_atAll() {
        listOf(PresetId.MacrosWithGrams, PresetId.MacrosOnly, PresetId.NoNumbers).forEach { preset ->
            val state = trends(preset)
            assertNull(state.intake, "$preset")
            assertNull(state.expended, "$preset")
        }
    }

    @Test
    fun expended_full_aPointPerDayOfTheLine_withEnergyOut() {
        val card = assertNotNull(trends().expended)
        assertEquals(card.trend.size, card.readouts.size)
        card.readouts.filterNotNull().forEach { point ->
            assertEquals(Metric.EnergyOut.testTag(), point.readings.single().tag)
            assertTrue(point.readings.single().value!!.endsWith("kcal"))
        }
    }

    @Test
    fun weightCard_full_theTrendAtEachWeighIn() {
        val card = assertNotNull(trends().weight)
        assertEquals(card.trend.size, card.readouts.size)
        assertTrue(card.readouts.values.all { it.endsWith("kg") })
        assertTrue(card.readouts.values.isNotEmpty())
    }

    @Test
    fun weightCard_trendOnly_saysTheDayAndNothingElse() {
        val card = assertNotNull(trends(PresetId.MacrosOnly).weight)
        assertTrue(card.readouts.isNotEmpty())
        assertTrue(card.readouts.all { it != null && it.readings.isEmpty() })
    }

    @Test
    fun weightScreen_full_theTrendAndTheWeighIn() {
        val chart = assertNotNull(weight().chart)
        assertEquals(chart.trend.size, chart.readouts.size)
        chart.readouts.filterNotNull().forEach { point ->
            assertEquals(2, point.readings.size)
            assertTrue(point.readings.all { it.tag == Metric.Weight.testTag() && it.value!!.endsWith("kg") })
        }
    }

    @Test
    fun weightScreen_trendOnly_noAmount_inAnyPoint() {
        val chart = assertNotNull(weight(PresetId.MacrosOnly).chart)
        assertTrue(chart.readouts.isNotEmpty())
        assertTrue(chart.readouts.values.isEmpty())
    }

    @Test
    fun weightScreen_noNumbers_noChartSoNoPoints() {
        assertNull(weight(PresetId.NoNumbers).chart)
    }

    @Test
    fun weightScreen_oneReading_justTheWeighIn() {
        val state = WeightStateBuilder.build(
            TrendsFixtures.weightInputs(weighIns = TrendsFixtures.weighIns().takeLast(1)),
            WeightUi(),
            TrendsFixtures.policy(),
        )
        val point = assertNotNull(assertNotNull(state.chart).readouts.single())
        assertEquals("Today", point.title)
        assertEquals(1, point.readings.size)
    }

    @Test
    fun hunger_aWordInEveryMode_neverAnAmount() {
        PresetId.entries.forEach { preset ->
            val chart = assertIs<HungerTrend.Chart>(trends(preset).hunger, "$preset")
            assertEquals(chart.bars.size, chart.readouts.size)
            chart.bars.zip(chart.readouts).forEach { (bar, readout) -> assertEquals(bar.fraction == null, readout == null) }
            chart.readouts.filterNotNull().forEach { point ->
                val reading = point.readings.single()
                assertNull(reading.value)
                assertNotNull(reading.word)
            }
        }
    }

    @Test
    fun hungerWord_isTheNearestAnswer() {
        assertEquals(Hunger.Stuffed, TrendsReadouts.hungerWord(listOf(0)))
        assertEquals(Hunger.Starving, TrendsReadouts.hungerWord(listOf(4, 4, 3)))
        assertEquals(Hunger.Neutral, TrendsReadouts.hungerWord(listOf(1, 3)))
    }

    @Test
    fun patternsHunger_aWordPerWeekdayAnswered() {
        val card = assertNotNull(PatternsStateBuilder.build(TrendsFixtures.patternsInputs(checkInOn = true), TrendsFixtures.policy(PresetId.NoNumbers)).hunger)
        assertEquals(7, card.readouts.size)
        card.days.zip(card.readouts).forEach { (day, readout) -> assertEquals(day.level == null, readout == null) }
        card.readouts.filterNotNull().forEach { point ->
            assertNull(point.readings.single().value)
            assertNotNull(point.readings.single().word)
        }
    }

    @Test
    fun weekTitle_aWeekThatBeganToday_isOneDay() {
        val day = TrendsFixtures.today
        assertEquals("Oct 25", TrendsReadouts.week(day, day))
    }
}
