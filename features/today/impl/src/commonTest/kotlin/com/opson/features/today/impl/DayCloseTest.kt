package com.opson.features.today.impl

import com.opson.libraries.diary.Sleep
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.DayHunger
import com.opson.libraries.diary.CheckInMood
import com.opson.libraries.diary.CheckIn
import kotlinx.datetime.LocalDate
import com.opson.features.today.impl.dayclose.Commitment
import com.opson.features.today.impl.dayclose.DayCloseAction
import com.opson.features.today.impl.dayclose.DayCloseEvent
import com.opson.features.today.impl.dayclose.DayCloseState
import com.opson.features.today.impl.dayclose.DayCloseViewModel
import com.opson.features.today.impl.dayclose.FeelingChoice
import com.opson.features.today.impl.dayclose.MealsLine
import com.opson.features.today.impl.dayclose.RangeLine
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.Tone
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Targets
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_dayclose_title_warm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import com.opson.libraries.nutrition.DayGroup
import com.opson.libraries.nutrition.TargetStyle
import kotlinx.datetime.DayOfWeek

/**
 * Day close (design v3, 4.1 to 4.5): the builder for every visibility branch and the view
 * model's actions. The big-day card only ever appears with calories fully shown, and nothing on
 * this screen can move tomorrow.
 */
class DayCloseTest : CoroutineTest() {

    private fun build(
        preset: PresetId,
        bigDay: Boolean = false,
        settings: Settings = Settings(),
        at: Instant = TodayFixtures.noon,
        committed: Boolean = false,
    ): DayCloseState = TodayFixtures.dayCloseState(policy(preset, at), bigDay = bigDay, settings = settings, committed = committed)

    @Test
    fun fullInsideTheRangeShowsTotalsAndMacrosWithNoCard() {
        val state = build(PresetId.Full)

        val totals = assertNotNull(state.totals)
        assertEquals("2,058", totals.eaten.text())
        assertEquals("1,900 to 2,300", assertIs<RangeLine.Inside>(totals.rangeLine).range.text())
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), totals.macros.map { it.metric })
        assertEquals("88 g", totals.macros.first().display.text())
        assertNull(state.bigDay)
        assertNull(state.loggedMeals)
    }

    @Test
    fun aBigDayShowsTheResetWithTomorrowsUnchangedRange() {
        val state = build(PresetId.Full, bigDay = true)

        val totals = assertNotNull(state.totals)
        assertEquals("3,095", totals.eaten.text())
        assertEquals(RangeLine.Above, totals.rangeLine)
        assertTrue(totals.macros.isEmpty(), "the reset card is the message")
        val bigDay = assertNotNull(state.bigDay)
        assertEquals(Commitment.Offered, bigDay.commitment)
        assertEquals("1,900 to 2,300 kcal", bigDay.tomorrow.text())
        assertEquals(Commitment.Committed, build(PresetId.Full, bigDay = true, committed = true).bigDay?.commitment)
    }

    /** "Different by day": the big-day card names tomorrow's own range, a training day's here. */
    @Test
    fun tomorrowIsTomorrowsOwnRangeUnderDifferentByDay() {
        val tomorrow = TodayFixtures.today.plus(DatePeriod(days = 1)).dayOfWeek
        val training = DayGroup(setOf(tomorrow), minKcal = 2300, maxKcal = 2700)
        val rest = DayGroup(DayOfWeek.entries.toSet() - tomorrow, minKcal = 1900, maxKcal = 2300)
        val byDay = Targets.Default.copy(style = TargetStyle.ByDay(listOf(rest, training)))

        val bigDay = assertNotNull(build(PresetId.Full, bigDay = true, settings = Settings(targets = byDay)).bigDay)

        assertEquals("2,300 to 2,700 kcal", bigDay.tomorrow.text())
    }

    @Test
    fun tomorrowIgnoresHowBigTodayWas() {
        val raised = Targets.Default.withEnergy(2400)
        val bigDay = assertNotNull(build(PresetId.Full, bigDay = true, settings = Settings(targets = raised)).bigDay)

        assertEquals("2,200 to 2,600 kcal", bigDay.tomorrow.text(), "the user's own range, untouched by a 3,095 day")
    }

    /** One number is judged with leeway either side, so a few bites past it are not a big day. */
    @Test
    fun closeToOneNumber_isOnIt_andNamedAsAGoal() {
        val totals = assertNotNull(build(PresetId.Full, settings = Settings(targets = Targets.Default.useExact())).totals)

        // 2,058 eaten against one number, 2,100.
        assertEquals("2,100", assertIs<RangeLine.Inside>(totals.rangeLine).range.text())
        assertTrue(totals.singleGoal)
        assertEquals(false, assertNotNull(build(PresetId.Full).totals).singleGoal)
    }

    @Test
    fun wellPastOneNumber_isABigDay_andTomorrowIsTheSameNumber() {
        val low = Targets.Default.copy(energyKcal = 1900, minKcal = 1700, maxKcal = 2100, startingKcal = 1900).useExact()
        val state = build(PresetId.Full, settings = Settings(targets = low))

        // 2,058 is more than the leeway past 1,900.
        assertEquals(RangeLine.Above, assertNotNull(state.totals).rangeLine)
        val bigDay = assertNotNull(state.bigDay)
        assertEquals("1,900 kcal", bigDay.tomorrow.text())
        assertTrue(bigDay.singleGoal)
    }

    /** Design sync 4: a set deficit's goal is Burned less the deficit, and tomorrow's moves with it too. */
    @Test
    fun aSetDeficit_isJudgedAgainstBurned() {
        val state = TodayFixtures.dayCloseState(policy(PresetId.Full), bigDay = true, settings = TodayFixtures.deficit500, burned = TodayFixtures.burned)

        // 3,095 against 1,680 (2,180 less 500).
        assertEquals(RangeLine.Above, assertNotNull(state.totals).rangeLine)
        assertEquals("1,680 kcal", assertNotNull(state.bigDay).tomorrow.text())
        val withoutBurned = TodayFixtures.dayCloseState(policy(PresetId.Full), bigDay = true, settings = TodayFixtures.deficit500)
        assertEquals("1,900 to 2,300 kcal", assertNotNull(withoutBurned.bigDay).tomorrow.text(), "no Burned, the range")
    }

    @Test
    fun subtleHidesTheButtonsAndOffHidesTheCard() {
        assertEquals(Commitment.Hidden, build(PresetId.Full, bigDay = true, settings = Settings(bigDayEvent = EventLevel.Subtle)).bigDay?.commitment)
        assertNull(build(PresetId.Full, bigDay = true, settings = Settings(bigDayEvent = EventLevel.Off)).bigDay)
    }

    @Test
    fun fuelIsASoftLineAndNeverABigDay() {
        val state = build(PresetId.Fuel, bigDay = true)

        assertIs<Display.Soft>(state.soft)
        assertNull(state.totals)
        assertNull(state.bigDay)
    }

    @Test
    fun delayedOpensHereTodayWithANoteThatItLocksAgain() {
        val state = build(PresetId.Delayed)

        assertNotNull(state.totals)
        assertTrue(state.revealNote)
        assertEquals(MealsLine(4, 4), state.mealsLine)
    }

    @Test
    fun delayedOnAPastDayHasNoRelockNote() {
        val past = TodayFixtures.today.plus(DatePeriod(days = -2))
        val state = TodayFixtures.dayCloseState(policy(PresetId.Delayed), date = past)

        assertNotNull(state.totals)
        assertEquals(false, state.revealNote)
    }

    @Test
    fun coastAndBlindShowTheLoggedMealsAndNoBigDay() {
        listOf(PresetId.Coast, PresetId.Blind).forEach { preset ->
            val state = build(preset, bigDay = true)
            assertEquals(MealSlot.meals.toSet(), state.loggedMeals, "$preset")
            assertNull(state.totals)
            assertNull(state.bigDay, "a big day can't be inferred in $preset")
        }
    }

    @Test
    fun warmToneRetitles() {
        assertEquals(Res.string.today_dayclose_title_warm, build(PresetId.Full, settings = Settings(tone = Tone.Warm)).title)
    }

    private val diary = FakeDiaryRepository(listOf(TodayFixtures.day(dinner = true).copy(note = "Dinner at Lola's")))

    private fun vm(
        day: kotlinx.datetime.LocalDate = TodayFixtures.today,
        policySource: FakePolicySource = FakePolicySource(),
        checkInOn: Boolean = false,
        settings: Settings = Settings(),
    ) = DayCloseViewModel(
        day = day,
        diary = diary,
        settingsRepository = FakeSettingsRepository(settings),
        policySource = policySource,
        calendar = FakeCalendar(),
        health = FakeHealthRepository(),
        healthFlag = healthFlag(false),
        checkIns = diary,
        checkInEnabled = checkInFlag(checkInOn),
        goalsAndEnergy = goalsAndEnergyFlag(),
        burnedRepository = FakeBurnedRepository(),
    )

    @Test
    fun theNotePrefillsOnceAndIsNotOverwrittenByLaterEmissions() = runUnitTest {
        val vm = vm()
        assertEquals("Dinner at Lola's", vm.state.note)

        vm.takeAction(DayCloseAction.NoteTyped("Lola's birthday"))
        diary.days.value = diary.days.value.mapValues { (_, d) -> d.copy(note = "Something else") }

        assertEquals("Lola's birthday", vm.state.note)
    }

    @Test
    fun commitmentChangesTheCardAndWritesNothing() = runUnitTest {
        diary.days.value = mapOf(TodayFixtures.today to TodayFixtures.day(bigDay = true))
        val vm = vm()

        vm.takeAction(DayCloseAction.CommitmentTapped)

        assertEquals(Commitment.Committed, vm.state.bigDay?.commitment)
        assertEquals(0, diary.writes)
    }

    @Test
    fun doneSavesTheTrimmedNoteClosesTheDayAndLeaves() = runUnitTest {
        val vm = vm()
        val events = mutableListOf<DayCloseEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(DayCloseAction.NoteTyped("  Lola's birthday  "))
        vm.takeAction(DayCloseAction.DoneTapped)

        assertEquals(listOf<Pair<kotlinx.datetime.LocalDate, String?>>(TodayFixtures.today to "Lola's birthday"), diary.notes)
        assertEquals(listOf(TodayFixtures.today), diary.closed)
        assertEquals(listOf<DayCloseEvent>(DayCloseEvent.Close), events)
    }

    @Test
    fun backWritesNothing() = runUnitTest {
        val vm = vm()
        val events = mutableListOf<DayCloseEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(DayCloseAction.BackTapped)

        assertEquals(0, diary.writes)
        assertEquals(listOf<DayCloseEvent>(DayCloseEvent.Close), events)
    }

    @Test
    fun howTodayFeltIsOfferedOnlyBesideTheLoggedMeals() {
        val blind = TodayFixtures.dayCloseState(policy(PresetId.Blind), feeling = DayFeeling.Tired)
        assertEquals(FeelingChoice(DayFeeling.entries, DayFeeling.Tired), blind.feeling)
        assertEquals(listOf("steady", "busy", "tired", "good", "hard"), DayFeeling.entries.map { it.wire }, "the design's order")
        assertNull(build(PresetId.Full).feeling, "with totals on show the screen already has its say")
        assertNull(build(PresetId.Fuel).feeling)
    }

    @Test
    fun aFeelingTapPicksItAndTappingItAgainClearsIt() = runUnitTest {
        policySourceForFeeling.policy.value = policy(PresetId.Blind)
        val vm = vm(policySource = policySourceForFeeling)

        vm.takeAction(DayCloseAction.FeelingTapped(DayFeeling.Steady))
        assertEquals(DayFeeling.Steady, vm.state.feeling?.picked)
        vm.takeAction(DayCloseAction.FeelingTapped(DayFeeling.Busy))
        assertEquals(DayFeeling.Busy, vm.state.feeling?.picked)
        vm.takeAction(DayCloseAction.FeelingTapped(DayFeeling.Busy))
        assertNull(vm.state.feeling?.picked)
        assertEquals(0, diary.writes, "nothing is saved until Close the day")
    }

    @Test
    fun theFeelingPrefillsAndIsSavedOnDoneButNeverOnBack() = runUnitTest {
        diary.days.value = mapOf(TodayFixtures.today to TodayFixtures.day(dinner = true).copy(feeling = DayFeeling.Good))
        policySourceForFeeling.policy.value = policy(PresetId.Blind)
        val vm = vm(policySource = policySourceForFeeling)
        assertEquals(DayFeeling.Good, vm.state.feeling?.picked)

        vm.takeAction(DayCloseAction.FeelingTapped(DayFeeling.Hard))
        vm.takeAction(DayCloseAction.BackTapped)
        assertTrue(diary.dayFeelings.isEmpty())

        vm.takeAction(DayCloseAction.DoneTapped)
        assertEquals(listOf<Pair<kotlinx.datetime.LocalDate, DayFeeling?>>(TodayFixtures.today to DayFeeling.Hard), diary.dayFeelings)
    }

    @Test
    fun anUnchangedFeelingIsNotWrittenAgain() = runUnitTest {
        diary.days.value = mapOf(TodayFixtures.today to TodayFixtures.day(dinner = true).copy(feeling = DayFeeling.Good))
        policySourceForFeeling.policy.value = policy(PresetId.Blind)
        val vm = vm(policySource = policySourceForFeeling)

        vm.takeAction(DayCloseAction.DoneTapped)

        assertTrue(diary.dayFeelings.isEmpty())
    }

    private val policySourceForFeeling = FakePolicySource()

    @Test
    fun aFutureDayClosesItself() = runUnitTest {
        val events = mutableListOf<DayCloseEvent>()
        val vm = vm(TodayFixtures.today.plus(DatePeriod(days = 1)))
        backgroundScope.launch { vm.eventFlow.toList(events) }
        diary.days.value = diary.days.value + (TodayFixtures.today.plus(DatePeriod(days = 1)) to TodayFixtures.plannedDay())

        assertEquals<DayCloseEvent?>(DayCloseEvent.Close, events.lastOrNull())
    }

    // Design sync 2: the check-in (release.checkIn), the expended row, and the big-day links

    @Test
    fun theCheckInIsOfferedInEveryMode_onlyWithTheFlag_andTakesTheFeelingsPlace() {
        listOf(PresetId.Full, PresetId.Blind, PresetId.Fuel, PresetId.Delayed).forEach { preset ->
            val on = TodayFixtures.dayCloseState(policy(preset), checkInOn = true)
            assertNotNull(on.checkIn, "$preset")
            assertNull(on.feeling, "$preset")
            assertNull(TodayFixtures.dayCloseState(policy(preset)).checkIn, "$preset with the flag off")
        }
        assertNotNull(TodayFixtures.dayCloseState(policy(PresetId.Blind)).feeling, "Blind keeps its feeling with the flag off")
    }

    @Test
    fun expendedFromTheWatch_showsAtShow_andOnlyToday() {
        assertEquals("1,990 kcal", TodayFixtures.dayCloseState(policy(PresetId.Full), expended = TodayFixtures.expended).expended.text())
        assertIs<Display.Hidden>(TodayFixtures.dayCloseState(policy(PresetId.Blind), expended = TodayFixtures.expended).expended)
        assertIs<Display.Hidden>(
            TodayFixtures.dayCloseState(policy(PresetId.Full), expended = TodayFixtures.expended, date = TodayFixtures.pastDay().date).expended,
        )
    }

    @Test
    fun theCheckInPrefillsOnce_andIsSavedOnCloseTheDay() = runUnitTest {
        diary.days.value = mapOf(TodayFixtures.today to TodayFixtures.day(dinner = true).copy(checkIn = CheckIn(hunger = DayHunger.Some)))
        val vm = vm(checkInOn = true)
        assertEquals(CheckIn(hunger = DayHunger.Some), vm.state.checkIn)

        vm.takeAction(DayCloseAction.CheckInHungerTapped(DayHunger.Steady))
        vm.takeAction(DayCloseAction.CheckInEnergyTapped(Energy.Good))
        vm.takeAction(DayCloseAction.CheckInSleepTapped(Sleep.Ok))
        vm.takeAction(DayCloseAction.CheckInMoodTapped(CheckInMood.Calm))
        vm.takeAction(DayCloseAction.CheckInMoodTapped(CheckInMood.Social))
        vm.takeAction(DayCloseAction.CheckInSleepTapped(Sleep.Ok))
        diary.days.value = diary.days.value.mapValues { (_, d) -> d.copy(checkIn = CheckIn(hunger = DayHunger.Very)) }
        assertEquals(DayHunger.Steady, vm.state.checkIn?.hunger, "a later emission does not overwrite what is being answered")

        vm.takeAction(DayCloseAction.DoneTapped)

        val expected = CheckIn(hunger = DayHunger.Steady, energy = Energy.Good, moods = setOf(CheckInMood.Calm, CheckInMood.Social))
        assertEquals(listOf<Pair<LocalDate, CheckIn?>>(TodayFixtures.today to expected), diary.checkIns.toList(), "sleep tapped twice is left blank")
    }

    @Test
    fun backSavesNoCheckIn_andTheFlagOffWritesNone() = runUnitTest {
        val on = vm(checkInOn = true)
        on.takeAction(DayCloseAction.CheckInEnergyTapped(Energy.Low))
        on.takeAction(DayCloseAction.BackTapped)
        assertTrue(diary.checkIns.isEmpty())

        val off = vm(checkInOn = false)
        off.takeAction(DayCloseAction.CheckInEnergyTapped(Energy.Low))
        off.takeAction(DayCloseAction.DoneTapped)
        assertTrue(diary.checkIns.isEmpty())
    }

    @Test
    fun checkInSwitchedOffInReminders_hidesTheCard_evenWithTheFlagOn() = runUnitTest {
        assertNotNull(vm(checkInOn = true).state.checkIn)

        val off = vm(checkInOn = true, settings = Settings(checkIn = false))
        assertNull(off.state.checkIn)
        off.takeAction(DayCloseAction.CheckInEnergyTapped(Energy.Low))
        off.takeAction(DayCloseAction.DoneTapped)
        assertTrue(diary.checkIns.isEmpty())
    }

    @Test
    fun whyNotCutBackAndSupportOpenTheirPages() = runUnitTest {
        val events = mutableListOf<DayCloseEvent>()
        val vm = vm()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(DayCloseAction.WhyNotCutBackTapped)
        vm.takeAction(DayCloseAction.SupportTapped)

        assertEquals(listOf<DayCloseEvent>(DayCloseEvent.OpenWhyNotCutBack, DayCloseEvent.OpenSupport), events)
        assertEquals(0, diary.writes, "opening support is never recorded")
    }
}
