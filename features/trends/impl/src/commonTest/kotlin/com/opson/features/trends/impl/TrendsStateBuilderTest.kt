package com.opson.features.trends.impl

import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.TrendsCards
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Trends mapping for every visibility branch in the plan's surface table,
 * over the fixture week (Oct 19 to 25 seen on Sunday the 25th at noon,
 * Thursday untracked). The window rules themselves are `TrendsCalendarTest`;
 * that nothing hidden reaches the screen is `BlindModeLeakTest`.
 */
class TrendsStateBuilderTest {

    private fun build(
        preset: PresetId = PresetId.Full,
        settings: Settings = Settings(),
        evening: Boolean = false,
        revealOn: RevealOn = RevealOn.DayClose,
    ) = TrendsStateBuilder.build(
        TrendsFixtures.trendsInputs(settings),
        TrendsFixtures.policy(preset, clock = if (evening) TrendsFixtures.evening else TrendsFixtures.clock, revealOn = revealOn),
    )

    private fun withWatch(
        preset: PresetId = PresetId.Full,
        settings: Settings = Settings(),
        overrides: Map<Metric, Visibility> = emptyMap(),
        expended: Map<LocalDate, EnergyExpended>? = TrendsFixtures.expended(),
        revealOn: RevealOn = RevealOn.DayClose,
    ) = TrendsStateBuilder.build(
        TrendsFixtures.trendsInputs(settings, expended = expended),
        TrendsFixtures.policy(preset, overrides, revealOn = revealOn),
    )

    @Test
    fun expended_averagesTheWeeksPastDays_todayLeftOut() {
        val card = assertNotNull(withWatch().expended)
        val week = (19..24).map { TrendsFixtures.expended().getValue(LocalDate(2026, 10, it)).totalKcal!! }
        assertEquals(TrendsFixtures.policy().resolve(Metric.EnergyOut, week.average(), Surface.Week, FormatContext(day = LocalDate(2026, 10, 25))), card.average)
        assertIs<Display.Shown>(card.average)
        assertEquals(6, card.trend.size)
        assertTrue(card.trend.all { it in 0f..1f })
    }

    @Test
    fun expended_aDayTheWatchMissedIsAGap_notAZero() {
        val missing = TrendsFixtures.expended() - LocalDate(2026, 10, 22)
        val card = assertNotNull(withWatch(expended = missing).expended)
        val week = listOf(19, 20, 21, 23, 24).map { missing.getValue(LocalDate(2026, 10, it)).totalKcal!! }
        assertEquals(TrendsFixtures.policy().resolve(Metric.EnergyOut, week.average(), Surface.Week, FormatContext(day = LocalDate(2026, 10, 25))), card.average)
        assertEquals(5, card.trend.size)
    }

    @Test
    fun expended_noWatch_noCard_andNoReadingsAtAll_noCard() {
        assertNull(withWatch(expended = null).expended)
        assertNull(withWatch(expended = emptyMap()).expended)
    }

    @Test
    fun expended_removedWhereEnergyOutIsHiddenOrSoftened_orTheCardIsOff() {
        assertNull(withWatch(overrides = mapOf(Metric.EnergyOut to Visibility.Hide)).expended)
        assertNull(withWatch(overrides = mapOf(Metric.EnergyOut to Visibility.Soften)).expended, "energy out has no soft word")
        assertNull(withWatch(PresetId.NoNumbers).expended)
        assertNull(withWatch(PresetId.NutrientsFirst).expended)
        assertNull(withWatch(settings = Settings(trendsCards = TrendsCards(expended = false))).expended)
    }

    @Test
    fun expended_delayed_showsOnTheWeekView_withTodayStillOut() {
        assertNotNull(withWatch(PresetId.Later).expended, "Delayed opens the week view all day")
    }

    @Test
    fun expended_weeklyWithEnergyOutLater_waitsForTheWeeklyCheck() {
        assertNull(withWatch(PresetId.Later, revealOn = RevealOn.Week).expended)
    }

    @Test
    fun full_week_everyBarTheSameKind_todayStriped_thursdayAGap() {
        val intake = assertNotNull(build().intake)
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), intake.bars.map { it.label })
        assertNull(intake.bars[3].fraction, "an untracked day is a gap, not a zero")
        assertEquals(1920f / 2800f, intake.bars[0].fraction)
        assertEquals(listOf(false, false, false, false, false, false, true), intake.bars.map { it.isCurrent })
        assertEquals(1900f / 2800f..2300f / 2800f, intake.band)
        assertEquals(IntakeCaption.TodayInProgress, intake.caption)
    }

    @Test
    fun full_week_averageLeavesOutTodayAndUntrackedDays() {
        // (1920 + 1690 + 1680 + 2015 + 1785) / 5: Thursday untracked, Sunday still in progress.
        assertEquals("1,818 kcal", assertIs<Display.Shown>(build().intake?.average).text)
    }

    @Test
    fun theRangeControlCoversThePage_andShowsWhenACardFollowsIt() {
        assertEquals(TrendsRange.Week, build().range)
        assertEquals(TrendsRange.FourWeeks, build(settings = Settings(trendsRange = TrendsRange.FourWeeks)).range)
    }

    @Test
    fun rangeLabel_followsTheRange() {
        assertEquals(RangeLabel.Week("Oct 19 to 25"), build().rangeLabel)
        assertEquals(RangeLabel.LastFourWeeks, build(settings = Settings(trendsRange = TrendsRange.FourWeeks)).rangeLabel)
        assertEquals(RangeLabel.LastThreeMonths, build(settings = Settings(trendsRange = TrendsRange.ThreeMonths)).rangeLabel)
    }

    @Test
    fun month_weeklyBars_currentWeekStripedAndOutOfTheAverage() {
        val state = build(settings = Settings(trendsRange = TrendsRange.FourWeeks))
        val intake = assertNotNull(state.intake)
        assertEquals(listOf("9/28", "10/5", "10/12", "10/19"), intake.bars.map { it.label })
        assertEquals(listOf(false, false, false, true), intake.bars.map { it.isCurrent })
        assertEquals(IntakeCaption.WeekInProgress, intake.caption)
        assertIs<Display.Shown>(intake.average)
    }

    @Test
    fun month_theRecapAndWeightCoverTheSameFourWeeks() {
        val state = build(settings = Settings(trendsRange = TrendsRange.FourWeeks))
        val days = assertNotNull(state.recap).rows.filterIsInstance<RecapRow.DaysLogged>().single()
        assertEquals(28, days.of)
        assertEquals(28, assertNotNull(state.weight).trend.size, "one weigh-in a day for four weeks")
    }

    @Test
    fun threeMonths_twelveBars_everyThirdLabelledFromTheCurrentWeek() {
        val bars = assertNotNull(build(settings = Settings(trendsRange = TrendsRange.ThreeMonths)).intake).bars
        assertEquals(12, bars.size)
        assertEquals(listOf(2, 5, 8, 11), bars.indices.filter { bars[it].label.isNotEmpty() })
        assertEquals("10/19", bars.last().label)
    }

    @Test
    fun weekStartsSaturday_windowEndsBeforeToday_nothingInProgress() {
        val state = build(settings = Settings(weekStart = DayOfWeek.SATURDAY))
        assertEquals(RangeLabel.Week("Oct 17 to 23"), state.rangeLabel)
        val intake = assertNotNull(state.intake)
        assertTrue(intake.bars.none { it.isCurrent })
        assertEquals(IntakeCaption.Plain, intake.caption)
        assertEquals(listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri"), intake.bars.map { it.label })
    }

    @Test
    fun full_recapSitsUnderTheChart_titled_withCountsOnly() {
        val state = build()
        assertNull(state.week, "the dot grid only leads when there is no chart")
        val recap = assertNotNull(state.recap)
        assertTrue(recap.titled)
        assertEquals(
            listOf(RecapRow.DaysLogged(6, 7), RecapRow.Breakfast(6, 7), RecapRow.NewFoods(3, names = null)),
            recap.rows,
        )
    }

    @Test
    fun aDayMarkedUntracked_isAGapEverywhere_evenWithFoodLogged() {
        val tuesday = LocalDate(2026, 10, 20)
        val days = TrendsFixtures.days().map { if (it.date == tuesday) it.copy(untracked = true) else it }
        val state = TrendsStateBuilder.build(TrendsFixtures.trendsInputs(days = days), TrendsFixtures.policy())

        val intake = assertNotNull(state.intake)
        assertNull(intake.bars[1].fraction, "a holiday is a gap, never a low day")
        // (1920 + 1680 + 2015 + 1785) / 4: Tuesday's 1,690 is left out, not counted as zero.
        assertEquals("1,850 kcal", assertIs<Display.Shown>(intake.average).text)
        assertEquals(RecapRow.DaysLogged(5, 6), assertNotNull(state.recap).rows.first())
        assertEquals(RecapRow.Breakfast(5, 6), assertNotNull(state.recap).rows[1])
        assertNull(WeekAverages.of(days.filter { it.date == tuesday }))

        val blind = TrendsStateBuilder.build(TrendsFixtures.trendsInputs(days = days), TrendsFixtures.policy(PresetId.NoNumbers))
        assertEquals(List(4) { null }, assertNotNull(blind.week).days[1].meals, "no dots for an untracked day")
    }

    @Test
    fun nameOnlyDay_isAGapInTheChart_butADayLogged() {
        val nameOnly = dayWith(LocalDate(2026, 10, 22), item(null, "Something at a friend's", null))
        val days = TrendsFixtures.days().filter { it.date != nameOnly.date } + nameOnly
        val state = TrendsStateBuilder.build(TrendsFixtures.trendsInputs(days = days), TrendsFixtures.policy())
        assertNull(state.intake?.bars?.get(3)?.fraction)
        assertEquals(RecapRow.DaysLogged(7, 7), state.recap?.rows?.first())
    }

    @Test
    fun noTrackedDays_bandAndEmptyBars_averageHidden() {
        val state = TrendsStateBuilder.build(TrendsFixtures.trendsInputs(days = emptyList()), TrendsFixtures.policy())
        val intake = assertNotNull(state.intake)
        assertTrue(intake.bars.all { it.fraction == null })
        assertEquals(Display.Hidden, intake.average)
        assertEquals(RecapRow.DaysLogged(0, 7), state.recap?.rows?.first())
    }

    @Test
    fun blind_theWeekLeads_andNutrientsAndWeightAreRemoved() {
        val state = build(PresetId.NoNumbers)
        assertNull(state.intake)
        assertNull(state.nutrients)
        assertNull(state.weight, "removed, not greyed out")
        assertNull(state.range, "no card on the page follows a range")
        val week = assertNotNull(state.week)
        assertEquals(List(4) { null }, week.days[3].meals)
        assertEquals(listOf(MealSlot.Breakfast, MealSlot.Lunch, null, null), week.days[6].meals)
        assertTrue(week.days[6].isToday)
        val recap = assertNotNull(state.recap)
        assertEquals(false, recap.titled)
        assertEquals(
            RecapRow.NewFoods(3, listOf("Chicken kabsa", "Rice pudding", "Poutine")),
            recap.rows.last(),
        )
    }

    @Test
    fun weekly_beforeTheCheck_theCheckCountsAndLastWeekOnly() {
        val state = build(PresetId.Later, revealOn = RevealOn.Week)
        assertEquals(WeeklyCheck(DayOfWeek.SUNDAY, LocalTime(21, 0), tonight = true), state.weeklyCheck)
        assertNull(state.intake)
        assertNull(state.week)
        assertNull(state.nutrients)
        assertNull(state.weight, "the check holds the weight trend")
        assertNull(state.range)
        val recap = assertNotNull(state.recap)
        assertEquals(RecapRow.DaysLogged(6, 7), recap.rows[0])
        assertIs<RecapRow.MealsLogged>(recap.rows[1])
        assertEquals(2, recap.rows.size)
        val last = assertNotNull(state.lastCheck)
        assertEquals("Oct 18", last.weekEnd)
        assertTrue(assertIs<Display.Shown>(last.average).text.endsWith("kcal"), "last week's check is open")
        assertIs<Display.Soft>(last.weight, "Later's weight is a slope word")
    }

    @Test
    fun weekly_afterTheCheckOpens_theWeekIsOnTheChart() {
        val state = build(PresetId.Later, evening = true, revealOn = RevealOn.Week)
        assertNull(state.weeklyCheck)
        assertNull(state.lastCheck)
        assertIs<Display.Shown>(assertNotNull(state.intake).average)
        assertIs<Display.Soft>(assertNotNull(state.weight).value)
    }

    @Test
    fun delayed_beforeTheReveal_isOpenOnTheWeekView() {
        val state = build(PresetId.Later)
        assertNull(state.weeklyCheck, "Delayed opens at day close, not once a week")
        assertIs<Display.Shown>(assertNotNull(state.intake).average)
    }

    @Test
    fun delayed_beforeTheReveal_todaysBarIsAGap_untilTheRevealOpensIt() {
        assertNotNull(assertNotNull(build().intake).bars[6].fraction, "Full: today's bar so far")
        assertNull(assertNotNull(build(PresetId.Later).intake).bars[6].fraction, "its height would say roughly what today came to")
        assertNotNull(assertNotNull(build(PresetId.Later, evening = true).intake).bars[6].fraction)
    }

    @Test
    fun weekly_aMonthReachingIntoAWeekBeforeItsCheck_leavesThatWeekOut() {
        // A Saturday start: the week window (Oct 17 to 23) has had its check, but the month's last
        // bar holds Saturday the 24th and today, whose week checks on Friday the 30th.
        val settings = Settings(weekStart = DayOfWeek.SATURDAY, trendsRange = TrendsRange.FourWeeks)
        val weekly = assertNotNull(build(PresetId.Later, settings = settings, revealOn = RevealOn.Week).intake)
        assertNull(weekly.bars.last().fraction)
        assertNotNull(weekly.bars[weekly.bars.lastIndex - 1].fraction, "a week past its check shows")
        assertNotNull(assertNotNull(build(settings = settings).intake).bars.last().fraction, "Full: the week so far")
    }

    @Test
    fun fuel_noChart_theWeekLeads_proteinAndFiberTiles_weightAsAWord() {
        val state = build(PresetId.NutrientsFirst)
        assertNull(state.intake, "a softened calorie chart would still read as amounts")
        assertNotNull(state.week)
        assertEquals(listOf(Metric.Protein, Metric.Fiber), assertNotNull(state.nutrients).macros.map { it.metric })
        assertIs<Display.Soft>(assertNotNull(state.weight).value)
        assertEquals(TrendsRange.Week, state.range, "nutrients and weight follow the range, so the control shows")
    }

    @Test
    fun full_nutrientsCard_slashStyleWithBars_todayLeftOut() {
        val nutrients = assertNotNull(build().nutrients)
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), nutrients.macros.map { it.metric })
        val protein = assertIs<Display.Shown>(nutrients.macros.first().display)
        assertTrue(protein.text.endsWith(" / 120 g"), protein.text)
        assertNotNull(protein.fraction)
    }

    @Test
    fun nutrientFocus_softenedTileIsAWordWithABar() {
        val protein = assertIs<Display.Soft>(assertNotNull(build(PresetId.NutrientsFirst).nutrients).macros.single { it.metric == Metric.Protein }.display)
        assertNotNull(protein.fraction)
    }

    @Test
    fun full_weightCard_theSmoothedWeightAndItsLine() {
        val weight = assertNotNull(build().weight)
        assertEquals(WeightSubtitle.Trend, weight.subtitle)
        assertTrue(assertIs<Display.Shown>(weight.value).text.endsWith("kg"))
        assertEquals(7, weight.trend.size)
    }

    @Test
    fun weightCard_noWeighIns_saysSoWithoutAValue() {
        val state = TrendsStateBuilder.build(TrendsFixtures.trendsInputs(weighIns = emptyList()), TrendsFixtures.policy())
        assertEquals(WeightSubtitle.NoWeighIns, state.weight?.subtitle)
        assertNull(state.weight?.value)
        assertEquals(emptyList(), state.weight?.trend)
    }

    @Test
    fun cardsSwitchedOff_areNotBuilt_andTheRangeGoesWithThem() {
        val off = Settings(trendsCards = TrendsCards(intake = false, recap = false, nutrients = false, weight = false))
        val state = build(settings = off)
        assertNull(state.intake)
        assertNull(state.recap)
        assertNull(state.week)
        assertNull(state.nutrients)
        assertNull(state.weight)
        assertNull(state.range)
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
