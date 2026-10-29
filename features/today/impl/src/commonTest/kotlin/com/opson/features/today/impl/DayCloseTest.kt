package com.opson.features.today.impl

import com.opson.libraries.diary.DayReflection
import kotlinx.datetime.LocalDate
import com.opson.features.today.impl.dayclose.BalanceDirection
import com.opson.features.today.impl.dayclose.BalancePlan
import com.opson.features.today.impl.dayclose.Commitment
import com.opson.features.today.impl.dayclose.DayCloseInputs
import com.opson.features.today.impl.dayclose.DayCloseStateBuilder
import com.opson.features.today.impl.dayclose.PlanKind
import com.opson.features.today.impl.dayclose.PlanResult
import com.opson.features.today.impl.dayclose.RecapLine
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import com.opson.features.today.impl.dayclose.DayCloseAction
import com.opson.features.today.impl.dayclose.DayCloseEvent
import com.opson.features.today.impl.dayclose.DayCloseState
import com.opson.features.today.impl.dayclose.DayCloseViewModel
import com.opson.features.today.impl.dayclose.FeelingChoice
import com.opson.features.today.impl.dayclose.MealsLine
import com.opson.features.today.impl.dayclose.RangeLine
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.Tone
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.nutrition.Targets
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** Calories as a word, as Nutrients first had them: Macros only with calories set to Soft. */
    private val softCalories = policy(PresetId.MacrosOnly, overrides = mapOf(Metric.Calories to Visibility.Soften))

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
        val macros = assertNotNull(state.macros)
        assertEquals(listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber), macros.map { it.metric })
        assertEquals("88 / 120 g", macros.first().display.text(), "each against its target")
        assertNull(state.bigDay)
        assertNull(state.loggedMeals)
    }

    @Test
    fun aBigDayShowsTheResetWithTomorrowsUnchangedRange() {
        val state = build(PresetId.Full, bigDay = true)

        val totals = assertNotNull(state.totals)
        assertEquals("3,095", totals.eaten.text())
        assertEquals(RangeLine.Above, totals.rangeLine)
        assertNull(state.macros, "the reset card is the message")
        assertNull(state.micros)
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
        val state = TodayFixtures.dayCloseState(softCalories, bigDay = true)

        assertIs<Display.Soft>(state.soft)
        assertNull(state.totals)
        assertNull(state.bigDay)
    }

    @Test
    fun delayedOpensHereTodayWithANoteThatItLocksAgain() {
        val state = TodayFixtures.dayCloseState(policy(NumbersSettings.later()))

        assertNotNull(state.totals)
        assertTrue(state.revealNote)
        assertEquals(MealsLine(4, 4), state.mealsLine)
    }

    @Test
    fun delayedOnAPastDayHasNoRelockNote() {
        val past = TodayFixtures.today.plus(DatePeriod(days = -2))
        val state = TodayFixtures.dayCloseState(policy(NumbersSettings.later()), date = past)

        assertNotNull(state.totals)
        assertEquals(false, state.revealNote)
    }

    @Test
    fun weeklyLaterAndNoNumbersShowTheLoggedMealsAndNoBigDay() {
        listOf(policy(NumbersSettings.later(RevealOn.Week)), policy(PresetId.NoNumbers)).forEach { policy ->
            val state = TodayFixtures.dayCloseState(policy, bigDay = true)
            val preset = policy.settings.preset
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

        assertEquals(listOf(TodayFixtures.today to DayReflection(note = "Lola's birthday")), diary.closed, "the note goes with the close, in one write")
        assertTrue(diary.notes.isEmpty())
        assertEquals(listOf<DayCloseEvent>(DayCloseEvent.Close), events)
    }

    /**
     * Done shows while the day loads (with a health store, until it answers). Tapped then, it once
     * saved the empty field over the day's note.
     */
    @Test
    fun doneBeforeTheDayIsRead_keepsTheNoteAndStays() = runUnitTest {
        val open = MutableStateFlow(false)
        val vm = DayCloseViewModel(
            day = TodayFixtures.today,
            diary = diary.heldUntil(open),
            settingsRepository = FakeSettingsRepository(),
            policySource = FakePolicySource(),
            calendar = FakeCalendar(),
            health = FakeHealthRepository(),
            healthFlag = healthFlag(false),
            checkInEnabled = checkInFlag(false),
            goalsAndEnergy = goalsAndEnergyFlag(),
            burnedRepository = FakeBurnedRepository(),
        )
        val events = mutableListOf<DayCloseEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(DayCloseAction.DoneTapped)

        assertEquals(0, diary.writes)
        assertTrue(events.isEmpty())
        open.value = true
        assertEquals("Dinner at Lola's", vm.state.note)
    }

    @Test
    fun aSecondDoneWhileLeavingWritesAndLeavesOnce() = runUnitTest {
        val vm = vm()
        val events = mutableListOf<DayCloseEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(DayCloseAction.DoneTapped)
        vm.takeAction(DayCloseAction.DoneTapped)

        assertEquals(listOf(TodayFixtures.today), diary.closed.map { it.first })
        assertEquals(listOf<DayCloseEvent>(DayCloseEvent.Close), events)
    }

    /** Reopen and Save sit side by side on a closed day; a tap on either while leaving undoes nothing. */
    @Test
    fun onAClosedDay_aTapWhileLeavingAfterReopenNeitherClosesItAgainNorLeavesTwice() = runUnitTest {
        diary.days.value = mapOf(TodayFixtures.today to TodayFixtures.day(dinner = true).copy(closedAt = TodayFixtures.noon))
        val vm = vm()
        val events = mutableListOf<DayCloseEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }

        vm.takeAction(DayCloseAction.ReopenTapped)
        vm.takeAction(DayCloseAction.DoneTapped)
        vm.takeAction(DayCloseAction.ReopenTapped)

        assertEquals(listOf(TodayFixtures.today), diary.reopened)
        assertTrue(diary.closed.isEmpty())
        assertEquals(listOf<DayCloseEvent>(DayCloseEvent.Close), events)
    }

    @Test
    fun onAClosedDay_saveKeepsItClosed_andReopenOpensItAndLeaves() = runUnitTest {
        diary.days.value = mapOf(TodayFixtures.today to TodayFixtures.day(dinner = true).copy(closedAt = TodayFixtures.noon, note = "Lola's"))
        val vm = vm()
        val events = mutableListOf<DayCloseEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        assertTrue(vm.state.closed)

        vm.takeAction(DayCloseAction.NoteTyped("Lola's birthday"))
        vm.takeAction(DayCloseAction.DoneTapped)
        assertEquals(listOf(TodayFixtures.today to DayReflection(note = "Lola's birthday")), diary.closed)
        assertTrue(diary.reopened.isEmpty())
        assertEquals(listOf<DayCloseEvent>(DayCloseEvent.Close), events)

        // Opened again; Save left the first one.
        val again = vm()
        val againEvents = mutableListOf<DayCloseEvent>()
        backgroundScope.launch { again.eventFlow.toList(againEvents) }
        assertTrue(again.state.closed)
        again.takeAction(DayCloseAction.ReopenTapped)
        assertEquals(listOf(TodayFixtures.today), diary.reopened)
        assertEquals(false, again.state.closed)
        assertEquals(listOf<DayCloseEvent>(DayCloseEvent.Close), againEvents)
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
        val blind = TodayFixtures.dayCloseState(policy(PresetId.NoNumbers), feeling = DayFeeling.Tired)
        assertEquals(FeelingChoice(DayFeeling.entries, DayFeeling.Tired), blind.feeling)
        assertEquals(listOf("steady", "busy", "tired", "good", "hard"), DayFeeling.entries.map { it.wire }, "the design's order")
        assertNull(build(PresetId.Full).feeling, "with totals on show the screen already has its say")
        assertNull(TodayFixtures.dayCloseState(softCalories).feeling)
    }

    @Test
    fun aFeelingTapPicksItAndTappingItAgainClearsIt() = runUnitTest {
        policySourceForFeeling.policy.value = policy(PresetId.NoNumbers)
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
        policySourceForFeeling.policy.value = policy(PresetId.NoNumbers)
        val vm = vm(policySource = policySourceForFeeling)
        assertEquals(DayFeeling.Good, vm.state.feeling?.picked)

        vm.takeAction(DayCloseAction.FeelingTapped(DayFeeling.Hard))
        vm.takeAction(DayCloseAction.BackTapped)
        assertTrue(diary.closed.isEmpty())

        vm.takeAction(DayCloseAction.DoneTapped)
        assertEquals(DayFeeling.Hard, diary.closed.single().second.feeling)
    }

    @Test
    fun aFeelingNotAskedHereIsKeptAsItWas() = runUnitTest {
        diary.days.value = mapOf(TodayFixtures.today to TodayFixtures.day(dinner = true).copy(feeling = DayFeeling.Good))
        val vm = vm(policySource = FakePolicySource())
        assertNull(vm.state.feeling, "full totals on show: the feeling is not asked")

        vm.takeAction(DayCloseAction.DoneTapped)

        assertEquals(DayFeeling.Good, diary.closed.single().second.feeling)
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
        val modes = PresetId.entries.map { policy(it) } + policy(NumbersSettings.later())
        modes.forEach { policy ->
            val preset = policy.settings
            val on = TodayFixtures.dayCloseState(policy, checkInOn = true)
            assertNotNull(on.checkIn, "$preset")
            assertNull(on.feeling, "$preset")
            assertNull(TodayFixtures.dayCloseState(policy).checkIn, "$preset with the flag off")
        }
        assertNotNull(TodayFixtures.dayCloseState(policy(PresetId.NoNumbers)).feeling, "No numbers keeps its feeling with the flag off")
    }

    @Test
    fun expendedFromTheWatch_showsAtShow_andOnlyToday() {
        // Calories hidden, so no eaten and burned card says it instead.
        val energyOnly = policy(PresetId.Full, overrides = mapOf(Metric.Calories to Visibility.Hide))
        assertEquals("1,990 kcal", TodayFixtures.dayCloseState(energyOnly, expended = TodayFixtures.expended).expended.text())
        assertIs<Display.Hidden>(TodayFixtures.dayCloseState(policy(PresetId.NoNumbers), expended = TodayFixtures.expended).expended)
        assertIs<Display.Hidden>(
            TodayFixtures.dayCloseState(energyOnly, expended = TodayFixtures.expended, date = TodayFixtures.pastDay().date).expended,
        )
    }

    @Test
    fun theCheckInRowShowsTheDaysCheckIn_andOpensTheSheet() = runUnitTest {
        diary.days.value = mapOf(TodayFixtures.today to TodayFixtures.day(dinner = true).copy(checkIn = TodayFixtures.checkIn))
        val events = mutableListOf<DayCloseEvent>()
        val vm = vm(checkInOn = true)
        backgroundScope.launch { vm.eventFlow.toList(events) }
        assertEquals(TodayFixtures.checkIn, vm.state.checkIn)

        vm.takeAction(DayCloseAction.CheckInTapped)

        assertEquals<DayCloseEvent>(DayCloseEvent.OpenCheckIn(TodayFixtures.today), events.single())
    }

    @Test
    fun closingTheDayNeverWritesTheCheckIn() = runUnitTest {
        diary.days.value = mapOf(TodayFixtures.today to TodayFixtures.day(dinner = true).copy(checkIn = TodayFixtures.checkIn))
        val vm = vm(checkInOn = true)

        vm.takeAction(DayCloseAction.DoneTapped)

        assertEquals(1, diary.closed.size)
        assertTrue(diary.checkIns.isEmpty(), "the check-in has its own sheet and its own write")
    }

    @Test
    fun theCheckInNeedsNoSwitch_theFlagAlone() = runUnitTest {
        assertNotNull(vm(checkInOn = true, settings = Settings(checkIn = false)).state.checkIn, "Reminders no longer turns it off")
        val events = mutableListOf<DayCloseEvent>()
        val off = vm(checkInOn = false)
        backgroundScope.launch { off.eventFlow.toList(events) }
        assertNull(off.state.checkIn)
        off.takeAction(DayCloseAction.CheckInTapped)
        assertTrue(events.isEmpty(), "with the flag off there is nothing to open")
    }

    // The recap: eaten against burned, the macros and the vitamins and minerals.

    @Test
    fun aSetDeficitOnABusyDay_isAbout500Under_rightAroundThePlan() {
        val state = TodayFixtures.dayCloseState(policy(PresetId.Full), settings = TodayFixtures.deficit500, burned = TodayFixtures.activeBurned)

        val balance = assertNotNull(state.balance)
        assertEquals("2,058 kcal", balance.eaten.text())
        assertEquals("2,560 kcal", balance.burned.text())
        assertEquals("502 kcal", balance.difference.text())
        assertEquals(BalanceDirection.Under, balance.direction)
        assertTrue(balance.soFar, "today's activity is still syncing")
        val plan = assertNotNull(balance.plan)
        assertEquals(PlanKind.Deficit, plan.kind)
        assertEquals("500 kcal", plan.planned.text())
        assertEquals(PlanResult.OnPlan, plan.result)
        assertIs<Display.Hidden>(state.expended, "the card says what the watch row would")
    }

    @Test
    fun aDeficitGoalOnAQuietDay_isOver_andSaysSoKindly_besideTheBigDayCard() {
        val state = TodayFixtures.dayCloseState(policy(PresetId.Full), settings = TodayFixtures.deficit500, burned = TodayFixtures.quietBurned)

        val balance = assertNotNull(state.balance)
        assertEquals(BalanceDirection.Over, balance.direction)
        assertEquals("308 kcal", balance.difference.text())
        // 1,750 less 500 is under the floor of 1,500, so the goal is 1,500: a 250 deficit.
        val plan = assertNotNull(balance.plan)
        assertEquals(PlanKind.Deficit, plan.kind)
        assertEquals(PlanResult.Opposite, plan.result)
        assertEquals("250 kcal", plan.planned.text())
        assertNotNull(state.bigDay)
    }

    @Test
    fun aSetSurplus_isOverWhatWasBurned_aroundThePlan() {
        val state = TodayFixtures.dayCloseState(policy(PresetId.Full), settings = TodayFixtures.surplus300, burned = TodayFixtures.quietBurned)

        val balance = assertNotNull(state.balance)
        assertEquals(BalanceDirection.Over, balance.direction)
        val plan = assertNotNull(balance.plan)
        assertEquals(PlanKind.Surplus, plan.kind)
        assertEquals(PlanResult.OnPlan, plan.result)
    }

    @Test
    fun aRangeCloseToWhatWasBurned_hasNoPlanLine_andEvenSaysEven() {
        // The range's middle, 2,100, is 80 under the 2,180 burned: no deficit worth naming.
        val state = TodayFixtures.dayCloseState(policy(PresetId.Full), burned = TodayFixtures.burned)
        val balance = assertNotNull(state.balance)
        assertEquals(BalanceDirection.Under, balance.direction)
        assertEquals("122 kcal", balance.difference.text())
        assertNull(balance.plan)

        val even = Burned(RestingEnergy(1610, RestingFrom.Calculated), movementKcal = 450)
        assertEquals(BalanceDirection.Even, TodayFixtures.dayCloseState(policy(PresetId.Full), burned = even).balance?.direction)
    }

    @Test
    fun aDeficitPlanCanBeBeyondOrShort() {
        val busier = Burned(RestingEnergy(1610, RestingFrom.Calculated), movementKcal = 900, workoutKcal = 400)
        val small = Settings(targets = Targets.Default.copy(style = TargetStyle.Deficit(300, 300)))
        // 2,910 burned and a 300 deficit: the goal is 2,610, and 2,058 eaten is 852 under.
        assertEquals(PlanResult.Beyond, TodayFixtures.dayCloseState(policy(PresetId.Full), settings = small, burned = busier).balance?.plan?.result)

        val big = Settings(targets = Targets.Default.copy(startingKcal = 1500, style = TargetStyle.Deficit(800, 800)))
        // 2,560 burned and an 800 deficit: 502 under is the same way, and less.
        assertEquals(PlanResult.Short, TodayFixtures.dayCloseState(policy(PresetId.Full), settings = big, burned = TodayFixtures.activeBurned).balance?.plan?.result)
    }

    @Test
    fun theBalanceIsLeftOutWheneverCaloriesOrEnergyOutIsLessThanShown() {
        val hiding = listOf(
            policy(PresetId.MacrosWithGrams),
            policy(PresetId.MacrosOnly),
            policy(PresetId.NoNumbers),
            softCalories,
            policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide)),
            policy(PresetId.Full, overrides = mapOf(Metric.Calories to Visibility.Hide)),
            policy(NumbersSettings.later(RevealOn.Week)),
        )
        hiding.forEach { policy ->
            val state = TodayFixtures.dayCloseState(policy, settings = TodayFixtures.deficit500, burned = TodayFixtures.activeBurned, expended = TodayFixtures.expended)
            assertNull(state.balance, "${policy.settings}")
        }
        val later = TodayFixtures.dayCloseState(policy(NumbersSettings.later()), settings = TodayFixtures.deficit500, burned = TodayFixtures.activeBurned)
        assertNotNull(later.balance, "Later opens on Close the day, the balance with it")
    }

    @Test
    fun noBurnedKnown_noBalance() {
        assertNull(TodayFixtures.dayCloseState(policy(PresetId.Full)).balance)
        assertNotNull(TodayFixtures.dayCloseState(policy(PresetId.Full), expended = TodayFixtures.expended).balance, "the watch's total stands in today")
        assertNull(
            TodayFixtures.dayCloseState(policy(PresetId.Full), expended = TodayFixtures.expended, date = TodayFixtures.pastDay().date).balance,
            "the watch reads today only",
        )
    }

    @Test
    fun theMacrosShowWhereverTheModeShowsThem_evenWithoutCalories() {
        assertEquals("88 / 120 g", TodayFixtures.dayCloseState(policy(PresetId.MacrosWithGrams)).macros?.first()?.display?.text())
        assertIs<Display.Soft>(assertNotNull(TodayFixtures.dayCloseState(policy(PresetId.MacrosOnly)).macros).first().display)
        assertNull(TodayFixtures.dayCloseState(policy(PresetId.NoNumbers)).macros)
    }

    @Test
    fun theVitaminsAndMineralsAreTodaysFour_asPercents_neverAScore() {
        val micros = assertNotNull(TodayFixtures.dayCloseState(policy(PresetId.Full)).micros)
        assertEquals(Settings().todayMicronutrients, micros.map { it.micro })
        assertTrue(micros.all { it.display is Display.Shown || it.notListed })
        assertNull(TodayFixtures.dayCloseState(policy(PresetId.MacrosOnly)).micros, "a mode that hides micros has no card")
        assertNull(TodayFixtures.dayCloseState(policy(PresetId.NoNumbers)).micros)
    }

    @Test
    fun theRecapLineSaysHowMuchWasLogged_inEveryMode() {
        PresetId.entries.forEach { preset ->
            assertEquals(RecapLine.AllMeals, TodayFixtures.dayCloseState(policy(preset)).recap, "$preset")
        }
        fun recap(day: Day) =
            DayCloseStateBuilder.build(DayCloseInputs(TodayFixtures.today, day, Settings()), policy(PresetId.Full), note = "", committed = false).recap
        assertEquals(RecapLine.SomeLogged, recap(TodayFixtures.day(dinner = false)), "no dinner yet")
        assertEquals(RecapLine.NothingLogged, recap(Day.empty(TodayFixtures.today)))
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
