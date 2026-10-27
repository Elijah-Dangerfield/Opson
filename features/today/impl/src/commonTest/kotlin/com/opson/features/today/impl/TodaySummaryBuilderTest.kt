package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_stat_to_goal
import opson.libraries.resources.generated.resources.today_stat_to_range
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Design sync 2 in [TodayStateBuilder]: the split eaten bar, the stats row, the macros page, the
 * one-line summary for the sticky bar and the collapsed card, the weight row, and selecting, each
 * through the real policy in the modes that change them. With the flags off none of it exists.
 */
class TodaySummaryBuilderTest {
    private val weightChanger = Settings(onboarding = OnboardingAnswers(why = listOf("changing_weight")))

    private fun build(
        policy: VisibilityPolicy = policy(PresetId.Full),
        day: Day = TodayFixtures.day(),
        settings: Settings = Settings(),
        on: Boolean = true,
        ui: TodayUi = TodayUi(),
    ): TodayState = TodayStateBuilder.build(
        TodayFixtures.inputs(
            day = day,
            settings = settings,
            expended = TodayFixtures.expended,
            summaryOn = on,
            selectOn = on,
            weighIns = TodayFixtures.weighIns,
        ),
        policy,
        ui = ui,
    )

    private fun TodayState.energy(): TodaySummary.Energy = assertIs<TodaySummary.Energy>(summary)

    private fun StatTile.text(): String? = when (val v = value) {
        is StatValue.Amount -> v.display.text()
        is StatValue.Count -> "${v.done} of ${v.total}"
    }

    @Test
    fun flagsOff_theCardKeepsItsColumns_andNothingNewIsBuilt() {
        val state = build(on = false)
        val energy = state.energy()

        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat), energy.macros.map { it.metric })
        assertTrue(energy.stats.isEmpty())
        assertNull(energy.macrosPage)
        assertNull(state.compact)
        assertNull(state.weight)
        assertFalse(state.summaryTools)
        assertFalse(state.selectTools)
        assertNull(build(on = false, ui = TodayUi(selected = TodayFixtures.picked)).selection)
    }

    @Test
    fun theEatenBarSplitsByMacroEnergy() {
        val bar = build(on = false).energy().bar

        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat), bar.map { it.metric })
        // 51 g protein, 162 g carbs and 44 g fat: 204, 648 and 396 of 1,248 kcal.
        assertEquals(0.163f, bar[0].weight, 0.01f)
        assertEquals(0.519f, bar[1].weight, 0.01f)
        assertEquals(1f, bar.sumOf { it.weight.toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun aHiddenMacroMergesIntoOneNeutralPiece() {
        val bar = build(policy(PresetId.Full, overrides = mapOf(Metric.Carbs to Visibility.Hide))).energy().bar

        assertEquals(listOf(Metric.Protein, Metric.Fat, null), bar.map { it.metric })
    }

    @Test
    fun withNoGoalTheStatsAreExpendedToYourRangeAndMeals() {
        val stats = build().energy().stats

        assertEquals(listOf(SummaryStat.Expended, SummaryStat.ToRange, SummaryStat.Meals), stats.map { it.stat })
        assertEquals(listOf("1,990 kcal", "655 kcal", "3 of 4"), stats.map { it.text() })
    }

    @Test
    fun oneNumber_isYourGoal_markedOnTheBar_andTheStatIsToYourGoal() {
        val energy = build(settings = Settings(targets = Targets.Default.useExact())).energy()

        assertTrue(energy.singleGoal)
        assertEquals("2,100", energy.range.text())
        assertEquals(energy.rangeStart, energy.rangeEnd, "a mark, not a box")
        val toGoal = energy.stats.first { it.stat == SummaryStat.ToRange }
        assertEquals(Res.string.today_stat_to_goal, toGoal.label)
        assertEquals("855 kcal", toGoal.text())
        assertEquals(Res.string.today_stat_to_range, build().energy().stats.first { it.stat == SummaryStat.ToRange }.label)
    }

    @Test
    fun aSurplus_isOneNumber_onTopOfTheUsualTarget() {
        val energy = build(settings = Settings(targets = Targets.Default.copy(style = TargetStyle.Surplus(300, 300)))).energy()

        assertTrue(energy.singleGoal)
        assertEquals("2,400", energy.range.text())
    }

    @Test
    fun changingWeightSuggestsExpendedDeficitSoFarAndMeals() {
        val stats = build(settings = weightChanger).energy().stats

        assertEquals(listOf(SummaryStat.Expended, SummaryStat.DeficitSoFar, SummaryStat.Meals), stats.map { it.stat })
        assertEquals("745 kcal", stats[1].text(), "1,990 out less 1,245 in")
    }

    @Test
    fun eatingRegularlySuggestsMealsToYourRangeAndWater() {
        val stats = build(settings = Settings(onboarding = OnboardingAnswers(why = listOf("curious", "eating_regularly")))).energy().stats

        assertEquals(listOf(SummaryStat.Meals, SummaryStat.ToRange, SummaryStat.Water), stats.map { it.stat })
        assertEquals("3 of 8", stats.last().text())
    }

    @Test
    fun thePersonsOwnOrderWins_andUnknownKeysAreSkipped() {
        val stats = build(settings = Settings(todaySummaryStats = listOf("fiber", "sparkles", "protein"))).energy().stats

        assertEquals(listOf(SummaryStat.Fiber, SummaryStat.Protein), stats.map { it.stat })
        assertEquals(listOf("18 g", "51 g"), stats.map { it.text() })
    }

    @Test
    fun aStatTheLevelsHideDropsOut_labelAndAll() {
        val stats = build(policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide)), settings = weightChanger).energy().stats

        assertEquals(listOf(SummaryStat.Meals), stats.map { it.stat }, "expended and the deficit both need energy out")
    }

    @Test
    fun deficitSoFarIsOfferedOnlyWhereDeficitsAre() {
        assertTrue(SummaryStat.DeficitSoFar in SummaryStats.offered(policy(PresetId.Full)))
        assertTrue(SummaryStat.DeficitSoFar in SummaryStats.offered(policy(PresetId.Sprint)))
        assertFalse(SummaryStat.DeficitSoFar in SummaryStats.offered(policy(PresetId.Nutrient)))
    }

    @Test
    fun insideTheRange_toYourRangeSaysSo_withoutAnAmount() {
        val stats = build(day = TodayFixtures.day(dinner = true)).energy().stats

        assertEquals("On target", stats.first { it.stat == SummaryStat.ToRange }.text())
    }

    @Test
    fun anEmptyMorningHasNoStatsAndOnePage() {
        val energy = build(day = Day.empty(TodayFixtures.today)).energy()

        assertTrue(energy.stats.isEmpty())
        assertNull(energy.macrosPage)
        assertTrue(energy.bar.isEmpty())
    }

    @Test
    fun theMacrosPageHasEveryMacroAndTheShownShares() {
        val page = assertNotNull(build().energy().macrosPage)

        assertEquals("1,245 kcal", page.eaten.text())
        assertEquals(listOf("51 / 120 g", "162 / 250 g", "44 / 70 g", "18 / 30 g"), page.rows.map { it.display.text() })
        assertEquals(listOf("16%", "52%", "32%"), page.shares.map { it.share.text() })
    }

    @Test
    fun aHiddenMacroLeavesTheMacrosPage_andTheSplitRecalculates() {
        val page = assertNotNull(build(policy(PresetId.Full, overrides = mapOf(Metric.Fat to Visibility.Hide))).energy().macrosPage)

        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fiber), page.rows.map { it.metric })
        assertEquals(listOf(Metric.Protein, Metric.Carbs), page.shares.map { it.metric })
        assertEquals(100f, page.shares.sumOf { it.weight.toDouble() }.toFloat(), 0.5f)
    }

    @Test
    fun theMacrosPageCanBeTurnedOff() {
        assertNull(build(settings = Settings(todayMacrosPage = false)).energy().macrosPage)
    }

    @Test
    fun theSavedPageIsKept_andClampedWhenThereIsOnlyOne() {
        assertEquals(1, build(settings = Settings(todaySummaryPage = 1)).summaryPage)
        assertEquals(0, build(settings = Settings(todaySummaryPage = 1, todayMacrosPage = false)).summaryPage)
    }

    @Test
    fun fullCompact_readsTheFigureAndRange_thenTheMacros() {
        val compact = assertNotNull(build().compact)

        val energy = compact.pages[0].parts
        assertEquals("1,245 kcal", assertIs<CompactPart.Amount>(energy[0]).display.text())
        assertEquals("1,900 to 2,300", assertIs<CompactPart.Amount>(energy[1]).display.text())
        assertNotNull(compact.pages[0].bar)
        assertEquals(listOf("51P", "162C", "44F"), assertIs<CompactPart.Macros>(compact.pages[1].parts.single()).parts.map { it.text })
        assertEquals(2, compact.collapsed.parts.size, "page 1's lead, then page 2's")
    }

    @Test
    fun blindRhythmAndMindfulCompactToTheMealCountAlone() {
        listOf(PresetId.Blind, PresetId.Rhythm, PresetId.Mindful, PresetId.Explorer).forEach { preset ->
            val compact = assertNotNull(build(policy(preset)).compact, "$preset")
            assertEquals(listOf<CompactPart>(CompactPart.Meals(3, 4)), compact.pages.single().parts, "$preset")
            assertNull(compact.pages.single().bar, "$preset")
        }
    }

    @Test
    fun fuelCompactsToMealsAndProtein_nutrientToTheWords_delayedToTheCount() {
        val fuel = assertNotNull(build(policy(PresetId.Fuel)).compact).pages.single().parts
        assertEquals(CompactPart.Meals(3, 4), fuel[0])
        assertEquals(Metric.Protein, assertIs<CompactPart.Amount>(fuel[1]).metric)

        val nutrient = assertNotNull(build(policy(PresetId.Nutrient)).compact).pages.single().parts
        assertTrue(nutrient.all { it is CompactPart.Amount && it.display is Display.Soft })

        val delayed = assertNotNull(build(policy(PresetId.Delayed)).compact).pages.single().parts
        assertEquals(listOf<CompactPart>(CompactPart.Logged(3)), delayed)
    }

    @Test
    fun collapsedComesFromSettings_onlyWithTheFlag() {
        assertTrue(build(settings = Settings(todaySummaryCollapsed = true)).collapsed)
        assertFalse(build(settings = Settings(todaySummaryCollapsed = true), on = false).collapsed)
    }

    @Test
    fun theWeightRowFollowsTheWeightLevel() {
        val full = assertNotNull(build().weight)
        assertEquals("73.9 kg", full.display.text())
        assertTrue(full.morning)

        assertEquals("Steady", build(policy(PresetId.Delayed)).weight?.display?.text(), "Delayed softens weight to its trend word")
        assertNull(build(policy(PresetId.Blind)).weight, "hidden weight, no row")
        assertNull(build(day = TodayFixtures.day(TodayFixtures.today.minus(DatePeriod(days = 20)))).weight, "no weigh-in that day")
    }

    @Test
    fun selectingTotalsThePicks_inFull() {
        val selection = assertNotNull(build(ui = TodayUi(selected = TodayFixtures.picked)).selection)

        assertEquals(3, selection.count)
        assertEquals("715 kcal", selection.calories.text())
        assertEquals(listOf("37 g", "82 g", "26 g", "11 g"), selection.macros.map { it.display.text() })
    }

    @Test
    fun selectingInBlind_keepsOnlyTheCount() {
        val selection = assertNotNull(build(policy(PresetId.Blind), ui = TodayUi(selected = TodayFixtures.picked)).selection)

        assertEquals(3, selection.count)
        assertIs<Display.Hidden>(selection.calories)
        assertTrue(selection.macros.isEmpty())
    }

    @Test
    fun whileSelecting_noRowIsSwipedOpen() {
        val state = build(ui = TodayUi(selected = TodayFixtures.picked, swipe = SwipeState(open = TodayFixtures.banana)))

        assertNull(state.swipe.open)
        assertEquals(TodayFixtures.banana, build(ui = TodayUi(swipe = SwipeState(open = TodayFixtures.banana))).swipe.open)
    }

    @Test
    fun uncategorizedFoodsOfferMoveTo_withTheSelectFlag() {
        fun loose(on: Boolean) = build(day = TodayFixtures.day(loose = true), on = on).meals
            .filterIsInstance<MealEntry.Full>()
            .single { it.slot == MealSlot.Uncategorized }.card

        assertTrue(loose(on = true).fileable)
        assertFalse(loose(on = false).fileable)
    }
}
