package com.opson.features.trends.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsCards
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_intake_daily
import opson.libraries.resources.generated.resources.trends_intake_weekly
import opson.libraries.resources.generated.resources.trends_nutrients_sub
import opson.libraries.resources.generated.resources.trends_nutrients_sub_micros
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Trends mapping for every visibility branch in the plan's surface table,
 * over the fixture week (Oct 19 to 25, Thursday untracked). The window rules
 * themselves are `TrendsCalendarTest`; that nothing hidden reaches the screen is
 * `BlindModeLeakTest`.
 */
class TrendsStateBuilderTest {

    private fun build(preset: PresetId = PresetId.Full, settings: Settings = Settings()) =
        TrendsStateBuilder.build(TrendsFixtures.trendsInputs(settings), TrendsFixtures.policy(preset))

    @Test
    fun full_week_drawsDailyBarsWithTodayCurrentAndThursdayAGap() {
        val intake = assertNotNull(build().intake)
        assertEquals(Res.string.trends_intake_daily, intake.title)
        assertEquals(listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"), intake.bars.map { it.label })
        assertNull(intake.bars[3].fraction, "an untracked day is a gap, not a zero")
        assertEquals(1920f / 2800f, intake.bars[0].fraction)
        assertEquals(listOf(false, false, false, false, false, false, true), intake.bars.map { it.isCurrent })
        assertEquals(1900f / 2800f..2300f / 2800f, intake.band)
    }

    @Test
    fun full_week_averagesTrackedDaysOnly() {
        // (1920 + 1690 + 1680 + 2015 + 1785 + 1130) / 6, Thursday left out.
        assertEquals("1,703 kcal", assertIs<Display.Shown>(build().intake?.average).text)
    }

    @Test
    fun rangeLabel_week_isTheWindow() {
        assertEquals(RangeLabel.Week("Oct 19 to 25"), build().rangeLabel)
        assertEquals(RangeLabel.LastFourWeeks, build(settings = Settings(trendsRange = TrendsRange.FourWeeks)).rangeLabel)
        assertEquals(RangeLabel.LastThreeMonths, build(settings = Settings(trendsRange = TrendsRange.ThreeMonths)).rangeLabel)
    }

    @Test
    fun fourWeeks_oneLabelledBarPerWeek_currentWeekLast() {
        val intake = assertNotNull(build(settings = Settings(trendsRange = TrendsRange.FourWeeks)).intake)
        assertEquals(Res.string.trends_intake_weekly, intake.title)
        assertEquals(listOf("9/28", "10/5", "10/12", "10/19"), intake.bars.map { it.label })
        assertEquals(listOf(false, false, false, true), intake.bars.map { it.isCurrent })
        // The current week is its own average so far: the same six tracked days as the week view.
        assertEquals((10220.0 / 6 / 2800).toFloat(), intake.bars.last().fraction)
    }

    @Test
    fun threeMonths_twelveBars_everyThirdLabelledFromTheCurrentWeek() {
        val bars = assertNotNull(build(settings = Settings(trendsRange = TrendsRange.ThreeMonths)).intake).bars
        assertEquals(12, bars.size)
        val labelled = bars.indices.filter { bars[it].label.isNotEmpty() }
        assertEquals(listOf(2, 5, 8, 11), labelled)
        assertEquals("10/19", bars.last().label)
    }

    @Test
    fun weekStartsSaturday_windowEndsBeforeToday() {
        val state = build(settings = Settings(weekStart = DayOfWeek.SATURDAY))
        assertEquals(RangeLabel.Week("Oct 17 to 23"), state.rangeLabel)
        assertTrue(assertNotNull(state.intake).bars.none { it.isCurrent })
        assertEquals(listOf("Sa", "Su", "Mo", "Tu", "We", "Th", "Fr"), state.intake?.bars?.map { it.label })
    }

    @Test
    fun recap_countsTheWeekAndItsNewFoods() {
        val recap = assertNotNull(build().recap)
        assertEquals(6, recap.breakfastDays)
        assertEquals(6, recap.daysLogged)
        assertEquals(3, recap.newFoods, "kabsa, poutine and rice pudding are new; the rest were eaten before")
        assertEquals(List(4) { null }, recap.days[3].meals)
        assertEquals(listOf(MealSlot.Breakfast, MealSlot.Lunch, null, null), recap.days[6].meals)
    }

    @Test
    fun newFoods_aFoodSeenInTheLookbackIsNotNew() {
        val window = TrendsCalendar.weekWindow(TrendsFixtures.today, DayOfWeek.MONDAY)
        val earlierPoutine = dayWith(LocalDate(2026, 9, 1), item("poutine", "Poutine", 740.0))
        assertEquals(2, TrendsStateBuilder.newFoods(listOf(earlierPoutine) + TrendsFixtures.days(), window))
    }

    @Test
    fun nameOnlyDay_isAGapInTheChart() {
        val nameOnly = dayWith(LocalDate(2026, 10, 22), item(null, "Something at a friend's", null))
        val days = TrendsFixtures.days().filter { it.date != nameOnly.date } + nameOnly
        val state = TrendsStateBuilder.build(TrendsFixtures.trendsInputs(days = days), TrendsFixtures.policy())
        assertNull(state.intake?.bars?.get(3)?.fraction)
        assertEquals(7, state.recap?.daysLogged, "it still counts as a day logged")
    }

    @Test
    fun noTrackedDays_bandAndEmptyBars_averageHidden() {
        val state = TrendsStateBuilder.build(TrendsFixtures.trendsInputs(days = emptyList()), TrendsFixtures.policy())
        val intake = assertNotNull(state.intake)
        assertTrue(intake.bars.all { it.fraction == null })
        assertEquals(Display.Hidden, intake.average)
        assertEquals(0, state.recap?.daysLogged)
    }

    @Test
    fun blind_onlyRecapAndAHiddenWeightCard() {
        val state = build(PresetId.Blind)
        assertNull(state.intake)
        assertNull(state.nutrients)
        assertNotNull(state.recap)
        val weight = assertNotNull(state.weight)
        assertEquals(WeightSubtitle.Hidden, weight.subtitle)
        assertNull(weight.now)
        assertNull(weight.trend)
    }

    @Test
    fun coast_intakeOpensOnTheWeekView() {
        val state = build(PresetId.Coast)
        assertIs<Display.Shown>(assertNotNull(state.intake).average)
        assertNull(state.nutrients, "Coast hides every macro and the micros")
        assertEquals(WeightSubtitle.Trend, state.weight?.subtitle)
        assertNull(state.weight?.now)
        assertTrue(state.weight?.trend.orEmpty().isNotEmpty())
    }

    @Test
    fun delayed_beforeNinePm_isRevealedOnTheWeekView() {
        assertIs<Display.Shown>(assertNotNull(build(PresetId.Delayed).intake).average)
    }

    @Test
    fun fuel_noIntakeChart_butProteinAndFiberTiles() {
        val state = build(PresetId.Fuel)
        assertNull(state.intake, "a softened calorie chart would still read as amounts")
        assertNotNull(state.recap)
        val nutrients = assertNotNull(state.nutrients)
        assertEquals(listOf(Metric.Protein, Metric.Fiber), nutrients.macros.map { it.metric })
    }

    @Test
    fun full_nutrientsCard_gramsWithoutTheGoal() {
        val nutrients = assertNotNull(build().nutrients)
        assertEquals(Res.string.trends_nutrients_sub, nutrients.subtitle)
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), nutrients.macros.map { it.metric })
        val protein = assertIs<Display.Shown>(nutrients.macros.first().display)
        assertTrue(protein.text.endsWith(" g") && " of " !in protein.text, protein.text)
        assertNotNull(protein.fraction)
    }

    @Test
    fun microsShown_nutrientsSubtitleMentionsThem() {
        val state = TrendsStateBuilder.build(
            TrendsFixtures.trendsInputs(),
            TrendsFixtures.policy(overrides = mapOf(Metric.Micros to Visibility.Show)),
        )
        assertEquals(Res.string.trends_nutrients_sub_micros, state.nutrients?.subtitle)
    }

    @Test
    fun nutrient_softened_isAWordWithABar() {
        val state = build(PresetId.Nutrient)
        val protein = assertIs<Display.Soft>(assertNotNull(state.nutrients).macros.single { it.metric == Metric.Protein }.display)
        assertNotNull(protein.fraction)
    }

    @Test
    fun full_weightCard_latestWeighInWithTrend() {
        val weight = assertNotNull(build().weight)
        assertEquals(WeightSubtitle.Latest(dayText = null), weight.subtitle)
        assertTrue(assertIs<Display.Shown>(weight.now).text.endsWith("kg"))
        assertEquals(28, weight.trend?.size)
    }

    @Test
    fun weightCard_noWeighIns_saysSoWithoutAValue() {
        val state = TrendsStateBuilder.build(TrendsFixtures.trendsInputs(weighIns = emptyList()), TrendsFixtures.policy())
        assertEquals(WeightSubtitle.NoWeighIns, state.weight?.subtitle)
        assertNull(state.weight?.now)
        assertEquals(emptyList(), state.weight?.trend)
    }

    @Test
    fun weightCard_latestFromYesterday_saysYesterday() {
        val weighIns = TrendsFixtures.weighIns().dropLast(1)
        val state = TrendsStateBuilder.build(TrendsFixtures.trendsInputs(weighIns = weighIns), TrendsFixtures.policy())
        assertEquals(WeightSubtitle.Latest("Yesterday"), state.weight?.subtitle)
    }

    @Test
    fun cardsSwitchedOff_areNotBuilt() {
        val off = Settings(trendsCards = TrendsCards(intake = false, recap = false, nutrients = false, weight = false))
        val state = build(settings = off)
        assertNull(state.intake)
        assertNull(state.recap)
        assertNull(state.nutrients)
        assertNull(state.weight)
        assertEquals(false, state.loading)
    }

    @Test
    fun chartScale_isTheTopOfTheRangePlusAFifth_roundedUp() {
        assertEquals(2800.0, TrendsStateBuilder.chartScale(Targets.Default))
        assertEquals(3000.0, TrendsStateBuilder.chartScale(Targets.Default.copy(maxKcal = 2450)))
    }

    private fun dayWith(date: LocalDate, item: LogItem) =
        Day(date = date, itemsBySlot = MealSlot.entries.associateWith { if (it == MealSlot.Lunch) listOf(item.copy(day = date)) else emptyList() })

    private fun item(foodId: String?, name: String, kcal: Double?) = LogItem(
        id = "test-$name",
        day = TrendsFixtures.today,
        slot = MealSlot.Lunch,
        loggedAt = TrendsFixtures.clock.now(),
        name = name,
        precision = if (kcal == null) Precision.Named else Precision.Estimate,
        method = LogMethod.Search,
        foodId = foodId,
        kcal = kcal,
    )
}
