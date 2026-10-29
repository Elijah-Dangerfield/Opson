package com.opson.features.today.impl

import com.opson.libraries.diary.ui.SummaryStat
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.FlipFigure
import com.opson.libraries.diary.Reading
import com.opson.libraries.diary.withReading
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ui.SummaryStats
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_flip_reached
import opson.libraries.resources.generated.resources.today_stat_burned_so_far
import opson.libraries.resources.generated.resources.today_stat_to_goal
import opson.libraries.resources.generated.resources.today_stat_to_range
import opson.libraries.resources.generated.resources.trends_micro_calcium
import opson.libraries.resources.generated.resources.trends_micro_iron
import opson.libraries.resources.generated.resources.trends_micro_potassium
import opson.libraries.resources.generated.resources.trends_micro_sodium
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
 * Also how the macro row and the stats read once flipped; which readings a mode allows at all is
 * `TodayFlipsTest`.
 */
class TodaySummaryBuilderTest {
    private val weightChanger = Settings(onboarding = OnboardingAnswers(why = listOf("changing_weight")))

    private fun build(
        policy: VisibilityPolicy = policy(PresetId.Full),
        day: Day = TodayFixtures.day(),
        settings: Settings = Settings(),
        on: Boolean = true,
        ui: TodayUi = TodayUi(),
        foodActionsOn: Boolean = false,
    ): TodayState = TodayStateBuilder.build(
        TodayFixtures.inputs(
            day = day,
            settings = settings,
            expended = TodayFixtures.expended,
            summaryOn = on,
            selectOn = on,
            weighIns = TodayFixtures.weighIns,
            foodActionsOn = foodActionsOn,
        ),
        policy,
        ui = ui,
    )

    private fun TodayState.energy(): TodaySummary.Energy = assertIs<TodaySummary.Energy>(summary)

    private fun StatTile.text(): String? = when (val v = value) {
        is StatValue.Amount -> v.display.text()
        is StatValue.Count -> "${v.done} of ${v.total}"
        is StatValue.ToGo -> "${v.left} to go"
    }

    @Test
    fun flagsOff_theCardKeepsItsColumns_andNothingNewIsBuilt() {
        val state = build(on = false)
        val energy = state.energy()

        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), energy.macros.map { it.metric })
        assertTrue(energy.stats.isEmpty())
        assertNull(energy.macrosPage)
        assertNull(state.compact)
        assertNull(state.weight)
        assertFalse(state.summaryTools)
        assertFalse(state.selectTools)
        assertNull(build(on = false, ui = TodayUi(selected = TodayFixtures.picked)).selection)
    }

    /**
     * The owner's phone, All numbers with every Today flag on: page 1 showed Protein alone under
     * the eaten bar, because the stats row (a goal's suggestion of Protein, Meals, To your range)
     * or design sync 4's card took the macro row's place. Every macro, in grams, in every case.
     */
    @Test
    fun allNumbers_showsEveryMacroInGrams_underTheBar_whateverTheFlags() {
        val performance = Settings(onboarding = OnboardingAnswers(why = listOf("performance")))
        listOf(false to false, true to false, true to true).forEach { (summaryOn, goalsOn) ->
            val energy = assertIs<TodaySummary.Energy>(
                TodayFixtures.todayState(
                    policy(PresetId.Full),
                    settings = performance,
                    summaryOn = summaryOn,
                    goalsAndEnergyOn = goalsOn,
                    burned = TodayFixtures.burned,
                ).summary,
            )
            val flags = "summary $summaryOn, goals and energy $goalsOn"
            assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), energy.macros.map { it.metric }, flags)
            assertEquals(listOf("51 g", "162 g", "44 g", "18 g"), energy.macros.map { it.display.text() }, flags)
            assertTrue(energy.stats.none { it.stat.metric in energy.macros.map { m -> m.metric } }, "no macro twice: $flags")
        }
    }

    /** What each mode puts at the top of Today with every flag on, as the owner runs it. */
    @Test
    fun everyMode_showsItsOwnNumbersAtTheTop() {
        fun top(preset: PresetId) = TodayFixtures.todayState(
            policy(preset),
            summaryOn = true,
            goalsAndEnergyOn = true,
            burned = TodayFixtures.burned,
        ).summary
        val macros = listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber)

        val grams = assertIs<TodaySummary.Focus>(top(PresetId.MacrosWithGrams))
        assertEquals(macros, grams.rows.map { it.metric })
        assertEquals("51 of 120 g", grams.rows.first().display.text())

        val words = assertIs<TodaySummary.Focus>(top(PresetId.MacrosOnly))
        assertEquals(macros, words.rows.map { it.metric })
        assertTrue(words.rows.all { it.display is Display.Soft }, "words and bars, never a gram")

        assertNull(top(PresetId.NoNumbers), "No numbers has its meal timeline instead")
    }

    @Test
    fun theLastPage_isKeyMinerals_andTheStickyBarHasADotForIt() {
        val state = build()
        val energy = state.energy()

        assertEquals(listOf(SummaryPage.Energy, SummaryPage.Macros, SummaryPage.Nutrients), energy.pages)
        val page = assertNotNull(energy.nutrientsPage)
        assertEquals(
            listOf(Res.string.trends_micro_sodium, Res.string.trends_micro_potassium, Res.string.trends_micro_calcium, Res.string.trends_micro_iron),
            page.rows.map { it.label },
        )
        assertTrue(page.rows.first().isLimit, "sodium is a limit")
        assertEquals(3, assertNotNull(state.compact).pages.size)
    }

    @Test
    fun theLastPage_listsTheirFour_againstTheirOwnReferences() {
        val settings = Settings(birthYear = 1990, bodySex = BodySex.Female)
            .withTodayNutrient(Micronutrient.Sodium, false)
            .withTodayNutrient(Micronutrient.VitaminD, true)
            .withMicroTarget(Micronutrient.Calcium, 1200.0)
        val page = assertNotNull(build(settings = settings).energy().nutrientsPage)
        val rows = page.rows.associateBy { it.micro }
        assertEquals(listOf(Micronutrient.Potassium, Micronutrient.Calcium, Micronutrient.Iron, Micronutrient.VitaminD), page.rows.map { it.micro })
        assertEquals("1,200 mg", rows.getValue(Micronutrient.Calcium).reference)
        assertEquals("18 mg", rows.getValue(Micronutrient.Iron).reference)
        // Iron: 3.4 + 1.8 mg of 18.
        assertEquals("29%", assertIs<Display.Shown>(rows.getValue(Micronutrient.Iron).display).text)
    }

    /** Nothing logged today lists vitamin D: the row says Not listed, never 0%. */
    @Test
    fun aNutrientNoFoodLists_isNotListed() {
        val page = assertNotNull(build(settings = Settings().withTodayNutrient(Micronutrient.VitaminD, false)).energy().nutrientsPage)
        assertEquals(4, page.rows.size)
        val withD = assertNotNull(
            build(settings = Settings().withTodayNutrient(Micronutrient.Iron, false).withTodayNutrient(Micronutrient.VitaminD, true)).energy().nutrientsPage,
        )
        val vitaminD = withD.rows.single { it.micro == Micronutrient.VitaminD }
        assertTrue(vitaminD.notListed)
        assertEquals(Display.Hidden, vitaminD.display)
    }

    @Test
    fun withMicrosHidden_thereIsNoNutrientsPage() {
        val energy = build(policy(PresetId.Full, overrides = mapOf(Metric.Micros to Visibility.Hide))).energy()

        assertNull(energy.nutrientsPage)
        assertEquals(listOf(SummaryPage.Energy, SummaryPage.Macros), energy.pages)
    }

    @Test
    fun theNutrientsPage_needsNoFlag_soTheCardStillReachesNutrients() {
        val energy = build(on = false).energy()

        assertEquals(listOf(SummaryPage.Energy, SummaryPage.Nutrients), energy.pages)
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
        val stats = build(settings = Settings(todaySummaryStats = listOf("water", "sparkles", "meals"))).energy().stats

        assertEquals(listOf(SummaryStat.Water, SummaryStat.Meals), stats.map { it.stat })
        assertEquals(listOf("3 of 8", "3 of 4"), stats.map { it.text() })
    }

    @Test
    fun aMacroStatTheMacroRowShows_isNotRepeated() {
        val stats = build(settings = Settings(todaySummaryStats = listOf("fiber", "meals", "protein"))).energy().stats

        assertEquals(listOf(SummaryStat.Meals), stats.map { it.stat })
    }

    @Test
    fun aStatTheLevelsHideDropsOut_labelAndAll() {
        val stats = build(policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide)), settings = weightChanger).energy().stats

        assertEquals(listOf(SummaryStat.Meals), stats.map { it.stat }, "expended and the deficit both need energy out")
    }

    @Test
    fun deficitSoFarIsOfferedOnlyWhereDeficitsAre() {
        assertTrue(SummaryStat.DeficitSoFar in SummaryStats.offered(policy(PresetId.Full).settings))
        assertTrue(SummaryStat.DeficitSoFar in SummaryStats.offered(policy(PresetId.Full).settings))
        assertFalse(SummaryStat.DeficitSoFar in SummaryStats.offered(policy(PresetId.MacrosOnly).settings))
        assertFalse(SummaryStat.DeficitSoFar in SummaryStats.offered(policy(PresetId.MacrosWithGrams).settings))
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
        assertEquals(2, build(settings = Settings(todaySummaryPage = 2)).summaryPage)
        assertEquals(1, build(settings = Settings(todaySummaryPage = 2, todayMacrosPage = false)).summaryPage)
        val noMicros = policy(PresetId.Full, overrides = mapOf(Metric.Micros to Visibility.Hide))
        assertEquals(0, build(noMicros, settings = Settings(todaySummaryPage = 1, todayMacrosPage = false)).summaryPage)
    }

    @Test
    fun fullCompact_readsTheFigureAndRange_thenTheMacros() {
        val compact = assertNotNull(build().compact)

        val energy = compact.pages[0].parts
        assertEquals("1,245 kcal", assertIs<CompactPart.Amount>(energy[0]).display.text())
        assertEquals("1,900 to 2,300", assertIs<CompactPart.Amount>(energy[1]).display.text())
        assertNotNull(compact.pages[0].bar)
        assertEquals(listOf("51P", "162C", "44F"), assertIs<CompactPart.Macros>(compact.pages[1].parts.single()).parts.map { it.text })
    }

    @Test
    fun blindRhythmAndMindfulCompactToTheMealCountAlone() {
        listOf(PresetId.NoNumbers, PresetId.NoNumbers, PresetId.NoNumbers, PresetId.NoNumbers).forEach { preset ->
            val compact = assertNotNull(build(policy(preset)).compact, "$preset")
            assertEquals(listOf<CompactPart>(CompactPart.Meals(3, 4)), compact.pages.single().parts, "$preset")
            assertNull(compact.pages.single().bar, "$preset")
        }
    }

    @Test
    fun softCaloriesCompactToMealsAndProtein_macrosOnlyToTheWords_gramsToTheAmounts_laterToTheCount() {
        val softCalories = policy(PresetId.MacrosOnly, overrides = mapOf(Metric.Calories to Visibility.Soften))
        val first = assertNotNull(build(softCalories).compact).pages.single().parts
        assertEquals(CompactPart.Meals(3, 4), first[0])
        assertEquals(Metric.Protein, assertIs<CompactPart.Amount>(first[1]).metric)

        val words = assertNotNull(build(policy(PresetId.MacrosOnly)).compact).pages.single().parts
        assertTrue(words.isNotEmpty() && words.all { it is CompactPart.Amount && it.display is Display.Soft })

        val grams = assertNotNull(build(policy(PresetId.MacrosWithGrams)).compact).pages.single().parts
        assertTrue(grams.isNotEmpty() && grams.all { it is CompactPart.Amount && it.display is Display.Shown })
        assertTrue(grams.none { (it as CompactPart.Amount).metric == Metric.Calories })

        val delayed = assertNotNull(build(policy(NumbersSettings.later())).compact).pages.single().parts
        assertEquals(listOf<CompactPart>(CompactPart.Logged(3)), delayed)
    }

    @Test
    fun theWeightRowFollowsTheWeightLevel() {
        val full = assertNotNull(build().weight)
        assertEquals("73.9 kg", full.display.text())
        assertTrue(full.morning)

        assertEquals("Steady", build(policy(NumbersSettings.later())).weight?.display?.text(), "Delayed softens weight to its trend word")
        assertNull(build(policy(PresetId.NoNumbers)).weight, "hidden weight, no row")
        assertNull(build(day = TodayFixtures.day(TodayFixtures.today.minus(DatePeriod(days = 20)))).weight, "no weigh-in that day")
    }

    @Test
    fun selecting_countsThePicks_namesTheFirst_andKnowsTheirMeals() {
        val selection = assertNotNull(build(ui = TodayUi(selected = TodayFixtures.picked)).selection)

        assertEquals(3, selection.count)
        assertEquals("Oat porridge with milk", selection.lead, "the first on the day, for the card a drag carries")
        assertEquals(setOf(MealSlot.Breakfast, MealSlot.Lunch), selection.slots)
    }

    @Test
    fun selectingInNoNumbers_isTheSame_sinceSelectModeShowsNoTotals() {
        val full = assertNotNull(build(ui = TodayUi(selected = TodayFixtures.picked)).selection)
        val blind = assertNotNull(build(policy(PresetId.NoNumbers), ui = TodayUi(selected = TodayFixtures.picked)).selection)

        assertEquals(full, blind)
    }

    @Test
    fun aMove_showsItsUndo_onlyWithTheSelectFlag() {
        val moved = MovedNotice(2, null, MealSlot.Dinner)

        assertEquals(moved, build(ui = TodayUi(moved = moved)).moved)
        assertNull(build(ui = TodayUi(moved = moved), on = false).moved)
    }

    @Test
    fun foodMenus_followTheirFlag() {
        assertTrue(build(foodActionsOn = true).foodMenus)
        assertFalse(build().foodMenus)
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

    // --- figures that flip (owner feedback, 2026-10-29) ---

    @Test
    fun theMacroRow_readsGramsLeft_andAPercentOfTheGoal_withItsBarsStillOnWhatWasEaten() {
        val eaten = build().energy()
        assertEquals(FlipState(FlipFigure.Macros, Reading.Eaten, Reading.Left), eaten.macroFlip)
        assertTrue(eaten.macros.all { it.word == null })

        val left = build(settings = Settings().withReading(FlipFigure.Macros, Reading.Left)).energy()
        assertEquals(listOf("69 g", "88 g", "26 g", "12 g"), left.macros.map { it.display.text() }, "120, 250, 70 and 30 g goals")
        assertTrue(left.macros.all { it.word == null }, "the row says Left once, above the columns")
        assertEquals(eaten.macros.map { it.display }, left.macros.map { it.bar })
        assertEquals(Reading.Percent, left.macroFlip?.next)

        val percent = build(settings = Settings().withReading(FlipFigure.Macros, Reading.Percent)).energy()
        assertEquals(listOf("43%", "65%", "63%", "60%"), percent.macros.map { it.display.text() })
        assertTrue(percent.macros.all { it.word == null })
        assertEquals(Reading.Eaten, percent.macroFlip?.next, "round again")
    }

    @Test
    fun aMacroPastItsGoal_readsReached_neverZeroOrNegativeLeft() {
        val settings = Settings(targets = Targets.Default.copy(proteinG = 40)).withReading(FlipFigure.Macros, Reading.Left)
        val protein = build(settings = settings).energy().macros.first { it.metric == Metric.Protein }
        assertEquals(Res.string.today_flip_reached, protein.word)
        assertEquals("51 g", protein.bar.text())
    }

    @Test
    fun afterNumbersHaveBeenHard_theMacroRowNeverReadsLeft() {
        val hard = Settings(onboarding = OnboardingAnswers(hard = true))
        assertEquals(Reading.Percent, build(settings = hard).energy().macroFlip?.next)
        val stored = build(settings = hard.withReading(FlipFigure.Macros, Reading.Left)).energy()
        assertEquals(Reading.Eaten, stored.macroFlip?.current, "a stored Left falls back")
        assertEquals(build().energy().macros, stored.macros)
    }

    @Test
    fun aSoftenedMacro_keepsItsWord_whicheverWayTheRowReads() {
        val softProtein = policy(PresetId.Full, overrides = mapOf(Metric.Protein to Visibility.Soften))
        val macros = build(softProtein, settings = Settings().withReading(FlipFigure.Macros, Reading.Left)).energy().macros
        val protein = macros.first { it.metric == Metric.Protein }
        assertIs<Display.Soft>(protein.display)
        assertNull(protein.word)
        assertEquals("88 g", macros.first { it.metric == Metric.Carbs }.display.text())
    }

    @Test
    fun withEveryMacroSoftened_theRowDoesNotFlip() {
        val soft = policy(PresetId.Full, overrides = Metric.entries.filter { it.isMacro }.associateWith { Visibility.Soften })
        assertNull(build(soft).energy().macroFlip)
    }

    @Test
    fun deficitSoFar_flipsToBurnedSoFar_withItsOwnLabel() {
        val deficit = build(settings = weightChanger).energy().stats.first { it.stat == SummaryStat.DeficitSoFar }
        assertEquals(FlipState(FlipFigure.DeficitSoFar, Reading.Deficit, Reading.Burned), deficit.flip)
        val burned = build(settings = weightChanger.withReading(FlipFigure.DeficitSoFar, Reading.Burned)).energy().stats
            .first { it.stat == SummaryStat.DeficitSoFar }
        assertEquals("1,990 kcal", burned.text())
        assertEquals(Res.string.today_stat_burned_so_far, burned.label)
        assertEquals(Reading.Deficit, burned.flip?.next)
    }

    @Test
    fun water_flipsToGlassesToGo() {
        val settings = Settings(todaySummaryStats = listOf("water", "meals"))
        val drunk = build(settings = settings).energy().stats.first { it.stat == SummaryStat.Water }
        assertEquals("3 of 8", drunk.text())
        assertEquals(Reading.Left, drunk.flip?.next)
        val toGo = build(settings = settings.withReading(FlipFigure.Water, Reading.Left)).energy().stats.first { it.stat == SummaryStat.Water }
        assertEquals("5 to go", toGo.text())
        assertNull(build(settings = settings).energy().stats.first { it.stat == SummaryStat.Meals }.flip, "a count of meals reads one way")
    }
}
