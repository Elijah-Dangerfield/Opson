package com.opson.features.settings.impl.todayscreen

import com.opson.features.settings.impl.FakeBurnedRepository
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.goalsAndEnergyFlag
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.todaySummaryFlag
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.diary.TodayCardRules
import com.opson.libraries.diary.ui.SummaryStat
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * More › Today screen (design sync 4, 13.6), the one place Today is arranged: the builder per
 * branch, each action, the stats Edit Today's cards used to carry, and every note that says a
 * choice here is overridden somewhere else.
 */
class TodayScreenSettingsTest : CoroutineTest() {
    private val weightChanger = SettingsFixtures.settings().copy(onboarding = OnboardingAnswers(why = listOf("changing_weight")))
    private val noGoalsAndEnergy = TodayScreenFlags(goalsAndEnergy = false)

    private fun vm(
        repository: FakeSettingsRepository = FakeSettingsRepository(),
        burned: FakeBurnedRepository = FakeBurnedRepository(),
        goalsAndEnergy: Boolean = true,
        summary: Boolean = true,
        fasting: Boolean = false,
    ) = TodayScreenSettingsViewModel(
        repository,
        burned,
        FakeCalendar(),
        goalsAndEnergyFlag(goalsAndEnergy),
        todaySummaryFlag(summary),
        FastingRelease { fasting },
    )

    @Test
    fun theDefaults_inAndOut_followMyGoal_bothPages_everyRowOn() {
        val state = TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings())
        assertTrue(state.presetsOn)
        assertNull(state.cardNote)
        assertEquals(SummaryCardPreset.InOut, state.presets.single { it.selected }.preset)
        assertEquals(MainNumberChoice.Follow, state.mainNumber)
        assertEquals(FollowStyle.Range, state.follows)
        assertEquals(true, state.macrosPage)
        assertTrue(state.burnedBreakdown && state.nutrientsLink == true && state.water && state.weight == true && state.closeDay && state.mealGroups)
        assertFalse(state.mealsCount)
        assertNull(state.stats, "design sync 4's card has no stats row")
        listOf(state.presetNote, state.mainNumberNote, state.waterNote, state.weightNote, state.nutrientsNote).forEach(::assertNull)
    }

    @Test
    fun rings_needASingleTarget() {
        val range = TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings().copy(todayCard = SummaryCardPreset.Rings))
        assertFalse(range.presets.single { it.preset == SummaryCardPreset.Rings }.enabled)
        assertEquals(SummaryCardPreset.InOut, range.presets.single { it.selected }.preset, "Rings with a range draws In and out")

        val one = TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings(targets = SettingsFixtures.exactTargets).copy(todayCard = SummaryCardPreset.Rings))
        assertTrue(one.presets.single { it.preset == SummaryCardPreset.Rings }.enabled)
        assertEquals(SummaryCardPreset.Rings, one.presets.single { it.selected }.preset)
        assertEquals(FollowStyle.Exact, one.follows)
    }

    @Test
    fun outsideFullNumbers_theCardComesFromTheMode_andSaysWhereToChangeIt() {
        listOf(PresetId.MacrosWithGrams, PresetId.MacrosOnly, PresetId.NoNumbers).forEach { preset ->
            val state = TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings(NumbersSettings(preset = preset)))
            assertFalse(state.presetsOn, "$preset")
            assertEquals(SettingLink.Display, state.cardNote?.link, "$preset")
            assertNull(state.stats, "$preset")
            assertNull(state.nutrientsLink, "$preset: no energy page to add a nutrients page to")
        }
    }

    /**
     * The owner's report: Equation picked, nothing changed on Today. Today draws In and out when
     * there is no Burned to subtract, and the page said nothing. Now the page and Today read the
     * same rule, and whenever they would differ the page says why.
     */
    @Test
    fun equationWithoutBurned_saysTodayShowsInAndOut_andWhereToFixIt() {
        val equation = SettingsFixtures.settings().copy(todayCard = SummaryCardPreset.Equation)

        val unknown = TodayScreenSettingsStateBuilder.build(equation, burnedKnown = false)
        assertEquals(SummaryCardPreset.Equation, unknown.presets.single { it.selected }.preset, "the pick stays picked")
        assertEquals(SettingLink.RestingEnergy, unknown.presetNote?.link)

        val hidden = SettingsFixtures.settings(NumbersSettings(overrides = mapOf(Metric.EnergyOut to Visibility.Hide))).copy(todayCard = SummaryCardPreset.Equation)
        assertEquals(SettingLink.Display, TodayScreenSettingsStateBuilder.build(hidden).presetNote?.link)

        assertNull(TodayScreenSettingsStateBuilder.build(equation, burnedKnown = true).presetNote, "with Burned, Equation draws")
    }

    /** Whatever the inputs, the page names a note exactly when Today would draw something other than the pick. */
    @Test
    fun aPresetNote_isThereExactlyWhenTodayDrawsSomethingElse() {
        val levels = listOf(NumbersSettings(), NumbersSettings(overrides = mapOf(Metric.EnergyOut to Visibility.Hide)))
        SummaryCardPreset.entries.filter { it != SummaryCardPreset.Rings }.forEach { preset ->
            levels.forEach { numbers ->
                listOf(true, false).forEach { known ->
                    val settings = SettingsFixtures.settings(numbers).copy(todayCard = preset)
                    val drawn = TodayCardRules.drawn(preset, singleTarget = false, burnedOnShow = known && numbers.level(Metric.EnergyOut) == Visibility.Show)
                    val note = TodayScreenSettingsStateBuilder.build(settings, burnedKnown = known).presetNote
                    assertEquals(drawn != preset, note != null, "$preset, $numbers, burned known $known")
                }
            }
        }
    }

    @Test
    fun deficitOrSurplusWithoutBurned_saysTheGapShowsInstead() {
        val balance = SettingsFixtures.settings().copy(todayMainNumber = MainNumberChoice.Balance)
        assertEquals(SettingLink.RestingEnergy, TodayScreenSettingsStateBuilder.build(balance, burnedKnown = false).mainNumberNote?.link)
        assertNull(TodayScreenSettingsStateBuilder.build(balance).mainNumberNote)
    }

    @Test
    fun theBreakdown_saysItIsInAndOutsAlone_onAnotherPreset() {
        assertFalse(TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings()).breakdownNeedsInOut)
        assertTrue(TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings().copy(todayCard = SummaryCardPreset.RangeBar)).breakdownNeedsInOut)
    }

    @Test
    fun aRowTheLevelsHide_saysSo_whileItsSwitchIsOn() {
        val hidden = SettingsFixtures.settings(
            NumbersSettings(overrides = mapOf(Metric.Water to Visibility.Hide, Metric.Weight to Visibility.Hide, Metric.Micros to Visibility.Hide)),
        )
        val state = TodayScreenSettingsStateBuilder.build(hidden)
        listOf(state.waterNote, state.weightNote, state.nutrientsNote).forEach { assertEquals(SettingLink.Display, it?.link) }

        val off = TodayScreenSettingsStateBuilder.build(hidden.copy(todayWater = false, todayWeight = false, todayNutrientsLink = false))
        listOf(off.waterNote, off.weightNote, off.nutrientsNote).forEach(::assertNull)
    }

    @Test
    fun theNutrientsPageRow_namesItsFour_whileThePageIsOnAndMicrosShow() {
        val settings = SettingsFixtures.settings().withTodayNutrient(Micronutrient.Sodium, false)
        assertEquals(listOf(Micronutrient.Potassium, Micronutrient.Calcium, Micronutrient.Iron), TodayScreenSettingsStateBuilder.build(settings).nutrientsOnPage)
        assertEquals(emptyList(), TodayScreenSettingsStateBuilder.build(settings.copy(todayNutrientsLink = false)).nutrientsOnPage)
        val microsOff = SettingsFixtures.settings(NumbersSettings(overrides = mapOf(Metric.Micros to Visibility.Hide)))
        assertEquals(emptyList(), TodayScreenSettingsStateBuilder.build(microsOff).nutrientsOnPage)
    }

    @Test
    fun withoutTheSummaryFlag_thereAreNoPagesOrWeightToArrange() {
        val state = TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings(), TodayScreenFlags(todaySummary = false))
        assertNull(state.macrosPage)
        assertNull(state.weight)
        assertNull(state.stats)
    }

    @Test
    fun theFastingRow_showsOnlyWhileFastingIsOfferedAndOn() {
        val on = SettingsFixtures.settings().copy(fasting = true)
        assertEquals(true, TodayScreenSettingsStateBuilder.build(on, TodayScreenFlags(fasting = true)).fasting)
        assertNull(TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings(), TodayScreenFlags(fasting = true)).fasting, "timer off")
        assertNull(TodayScreenSettingsStateBuilder.build(on, TodayScreenFlags(fasting = false)).fasting, "flag off")
        assertNull(
            TodayScreenSettingsStateBuilder.build(on.copy(onboarding = OnboardingAnswers(hard = true)), TodayScreenFlags(fasting = true)).fasting,
            "never after numbers have been hard",
        )
    }

    // --- the older card's stats, which Edit Today's cards carried ---

    @Test
    fun withoutGoalsAndEnergy_theStatsOpenOnTheGoalsSuggestion_tickedFirst() {
        val section = assertNotNull(TodayScreenSettingsStateBuilder.build(weightChanger, noGoalsAndEnergy).stats)
        assertNotNull(section.goal)
        assertEquals(listOf(SummaryStat.Expended, SummaryStat.DeficitSoFar, SummaryStat.Meals), section.rows.filter { it.ticked }.map { it.stat })
        assertEquals(3, section.ticked)
    }

    @Test
    fun withCaloriesOnLater_thePresetsWait_andTheStatsArePicked() {
        val later = SettingsFixtures.settings(NumbersSettings(overrides = mapOf(Metric.Calories to Visibility.Delay)))
        val state = TodayScreenSettingsStateBuilder.build(later)
        assertFalse(state.presetsOn)
        assertEquals(SettingLink.Display, state.cardNote?.link)
        assertNotNull(state.stats)
    }

    @Test
    fun aStatTheLevelsHideIsLocked() {
        val fiberOff = SettingsFixtures.settings(NumbersSettings(overrides = mapOf(Metric.Fiber to Visibility.Hide)))
        val rows = assertNotNull(TodayScreenSettingsStateBuilder.build(fiberOff, noGoalsAndEnergy).stats).rows
        assertTrue(rows.first { it.stat == SummaryStat.Fiber }.locked)
        assertFalse(rows.first { it.stat == SummaryStat.Meals }.locked, "a count is never locked")
    }

    @Test
    fun tickingAddsUpToThree_andUntickingRemoves() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings())
        val vm = vm(repository, goalsAndEnergy = false)

        vm.takeAction(TodayScreenSettingsAction.StatToggled(SummaryStat.Fiber))
        assertNull(repository.state.value.todaySummaryStats, "three already: a fourth changes nothing")

        vm.takeAction(TodayScreenSettingsAction.StatToggled(SummaryStat.ToRange))
        vm.takeAction(TodayScreenSettingsAction.StatToggled(SummaryStat.Fiber))
        assertEquals(listOf("expended", "meals", "fiber"), repository.state.value.todaySummaryStats)
        assertEquals(3, vm.state.stats?.ticked)
    }

    @Test
    fun aLockedStatNeverTicks_andItsTapOpensModes() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings(NumbersSettings(overrides = mapOf(Metric.Fiber to Visibility.Hide))))
        val vm = vm(repository, goalsAndEnergy = false)
        val events = eventsOf(vm)

        vm.takeAction(TodayScreenSettingsAction.StatToggled(SummaryStat.Meals))
        vm.takeAction(TodayScreenSettingsAction.StatToggled(SummaryStat.Fiber))
        assertEquals(listOf("expended", "to_range"), repository.state.value.todaySummaryStats)

        vm.takeAction(TodayScreenSettingsAction.LinkTapped(SettingLink.Display))
        assertEquals(listOf<TodayScreenSettingsEvent>(TodayScreenSettingsEvent.Open(SettingLink.Display)), events)
    }

    @Test
    fun movingARowReordersWhatIsTicked_andResetGoesBackToTheSuggestion() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings())
        val vm = vm(repository, goalsAndEnergy = false)

        vm.takeAction(TodayScreenSettingsAction.StatMoved(from = 2, to = 0))
        assertEquals(listOf("meals", "expended", "to_range"), repository.state.value.todaySummaryStats)
        assertEquals(SummaryStat.Meals, vm.state.stats?.rows?.first()?.stat)

        vm.takeAction(TodayScreenSettingsAction.StatsReset)
        assertNull(repository.state.value.todaySummaryStats)
    }

    // --- view model ---

    @Test
    fun presetPicked_isSaved_butRingsNotWithARange() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = vm(repository)

        vm.takeAction(TodayScreenSettingsAction.PresetPicked(SummaryCardPreset.Equation))
        assertEquals(SummaryCardPreset.Equation, repository.state.value.todayCard)
        vm.takeAction(TodayScreenSettingsAction.PresetPicked(SummaryCardPreset.Rings))
        assertEquals(SummaryCardPreset.Equation, repository.state.value.todayCard)
    }

    @Test
    fun theViewModel_readsBurned_toSayEquationCantDrawYet() = runUnitTest {
        val burned = FakeBurnedRepository(burned = null)
        val vm = vm(FakeSettingsRepository(SettingsFixtures.settings().copy(todayCard = SummaryCardPreset.Equation)), burned)
        assertEquals(SettingLink.RestingEnergy, vm.state.presetNote?.link)

        burned.burnedFlow.value = SettingsFixtures.burned
        assertNull(vm.state.presetNote)
    }

    @Test
    fun eachSwitch_writesItsSetting() = runUnitTest {
        val repository = FakeSettingsRepository(SettingsFixtures.settings().copy(fasting = true))
        val vm = vm(repository, fasting = true)
        TodaySwitch.entries.forEach { vm.takeAction(TodayScreenSettingsAction.Toggled(it, on = it == TodaySwitch.MealsCount)) }

        val s = repository.state.value
        assertEquals(
            listOf(false, true, false, false, false, false, false, false),
            listOf(s.todayBurnedBreakdown, s.todayMealsCount, s.todayNutrientsLink, s.mealGroups, s.todayWater, s.fastToday, s.todayWeight, s.todayCloseDay),
        )
    }

    @Test
    fun theFastingSwitch_doesNothingWhileFastingIsNotOffered() = runUnitTest {
        val repository = FakeSettingsRepository(Settings(fastToday = true))
        vm(repository, fasting = false).takeAction(TodayScreenSettingsAction.Toggled(TodaySwitch.Fasting, on = false))
        assertTrue(repository.state.value.fastToday)
    }

    @Test
    fun mainNumberAndCardPages_openTheirSheets_aNoteOpensItsSetting_andBackCloses() = runUnitTest {
        val vm = vm()
        val events = eventsOf(vm)
        vm.takeAction(TodayScreenSettingsAction.MainNumberTapped)
        vm.takeAction(TodayScreenSettingsAction.CardPagesTapped)
        vm.takeAction(TodayScreenSettingsAction.LinkTapped(SettingLink.RestingEnergy))
        vm.takeAction(TodayScreenSettingsAction.BackTapped)
        assertEquals(
            listOf(
                TodayScreenSettingsEvent.OpenChoice(TodayScreenChoice.MainNumber),
                TodayScreenSettingsEvent.OpenChoice(TodayScreenChoice.CardPages),
                TodayScreenSettingsEvent.Open(SettingLink.RestingEnergy),
                TodayScreenSettingsEvent.Close,
            ),
            events,
        )
    }

    @Test
    fun aMainNumberPicked_isWhatTheToggleResetsTo() = runUnitTest {
        val repository = FakeSettingsRepository(Settings(todayMainNumberByStyle = mapOf("range" to "balance")))
        val vm = TodayScreenChoiceViewModel(TodayScreenChoice.MainNumber, repository)
        val events = eventsOf(vm)

        vm.takeAction(TodayScreenChoiceAction.MainNumberPicked(MainNumberChoice.Eaten))

        assertEquals(MainNumberChoice.Eaten, repository.state.value.todayMainNumber)
        assertEquals(emptyMap(), repository.state.value.todayMainNumberByStyle)
        assertEquals(listOf<TodayScreenChoiceEvent>(TodayScreenChoiceEvent.Dismiss), events)
    }

    @Test
    fun cardPagesPicked_turnsTheMacrosPageOnOrOff() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = TodayScreenChoiceViewModel(TodayScreenChoice.CardPages, repository)
        vm.takeAction(TodayScreenChoiceAction.PagesPicked(macros = false))
        assertFalse(repository.state.value.todayMacrosPage)
        assertFalse(vm.state.macrosPage)
    }
}
