package com.opson.features.today.impl.activity

import com.opson.features.today.impl.FakeBurnedRepository
import com.opson.features.today.impl.FakeCalendar
import com.opson.features.today.impl.FakeDiaryRepository
import com.opson.features.today.impl.FakeHealthRepository
import com.opson.features.today.impl.FakePolicySource
import com.opson.features.today.impl.FakeSelectedDay
import com.opson.features.today.impl.FakeSettingsRepository
import com.opson.features.today.impl.TodayFixtures
import com.opson.features.today.impl.healthFlag
import com.opson.features.today.impl.policy
import com.opson.features.today.impl.text
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.ExerciseDraft
import com.opson.libraries.diary.ExerciseEntry
import com.opson.libraries.diary.ExerciseRepository
import com.opson.libraries.diary.MinuteTicker
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.ExerciseRelease
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthWorkout
import com.opson.libraries.health.WorkoutKind
import com.opson.features.today.impl.BurnedFix
import com.opson.features.today.impl.BurnedGap
import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.ExerciseTotals
import com.opson.libraries.nutrition.HealthActivity
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.burnedFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Today's Activity card: the lines that add up to Burned, an entry the health app already
 * counts, which health app is counting, digesting food as its own line, the modes that leave the
 * card out, and the taps that open the sheet.
 */
class TodayActivityTest : CoroutineTest() {
    private val day = TodayFixtures.today
    private val resting = RestingEnergy(1600, RestingFrom.Calculated)
    private val run = ExerciseEntry("run", day, Instant.parse("${day}T07:10:00Z"), "running_10", minutes = 35, kcal = 340)
    private val polo = ExerciseEntry("polo", day, Instant.parse("${day}T17:30:00Z"), "other", name = "Canoe polo", minutes = 60, kcal = 420, notOnWatch = true)
    private val watch = HealthActivity(380, 330)

    private fun build(
        burned: Burned? = burnedFor(resting, null, EverydayActivity.MostlySitting, ExerciseTotals(760, 420), true),
        entries: List<ExerciseEntry> = listOf(run, polo),
        settings: Settings = Settings(),
        preset: PresetId = PresetId.Full,
        numbers: NumbersSettings = NumbersSettings(preset = preset),
        released: Boolean = true,
        health: HealthSource? = HealthSource.AppleHealth,
    ) = TodayActivityStateBuilder.build(TodayActivityInputs(day, settings, burned, entries, policy(numbers), released, TimeZone.UTC, healthSource = health)).card

    private fun TodayActivityCard.sum(): List<Pair<String, String?>> = lines.map { it::class.simpleName.orEmpty() to it.amount.text() }

    @Test
    fun withoutAHealthApp_theLinesAreRestingEverydayAndExercise_andAddUpToTheTotal() {
        val card = assertNotNull(build())

        assertEquals("2,680 kcal", card.total.text())
        assertEquals(listOf("Resting" to "1,600 kcal", "Everyday" to "320 kcal", "Exercise" to "760 kcal"), card.sum())
        assertNull(card.healthSource, "the manual side counts, so no health app is named")
        assertEquals(EverydayActivity.MostlySitting, assertIs<ActivityLine.Everyday>(card.lines[1]).level)
        assertEquals(listOf("run", "polo"), card.entries.map { it.id })
        assertEquals(LocalTime(7, 10), card.entries.first().startedAt)
        assertEquals("340 kcal", card.entries.first().amount.text())
    }

    @Test
    fun withAHealthApp_itIsNamed_itsWorkoutsAreInActiveEnergy_andAnEntryTheWatchSawCountsNothing() {
        val card = assertNotNull(build(burned = burnedFor(resting, watch, EverydayActivity.MostlySitting, ExerciseTotals(760, 420), true)))

        assertEquals(HealthSource.AppleHealth, card.healthSource)
        assertEquals(listOf("Resting" to "1,600 kcal", "Active" to "710 kcal", "Exercise" to "420 kcal"), card.sum())
        assertEquals("330 kcal", assertIs<ActivityLine.Active>(card.lines[1]).workouts.text())
        assertTrue(assertIs<ActivityLine.Exercise>(card.lines[2]).missedByWatch)
        assertEquals("2,730 kcal", card.total.text())
        val (watched, missed) = card.entries
        assertTrue(watched.watchHasIt)
        assertEquals(Display.Hidden, watched.amount)
        assertEquals("420 kcal", missed.amount.text())
    }

    @Test
    fun aHealthAppConnected_butWithActivitySwitchedOff_isNotNamedAsTheSource() {
        val card = assertNotNull(build(burned = burnedFor(resting, watch, EverydayActivity.MostlySitting, ExerciseTotals(760, 0), activityCounted = false)))
        assertNull(card.healthSource)
    }

    @Test
    fun restingEnergyFromTheHealthApp_namesIt() {
        val card = assertNotNull(build(burned = burnedFor(RestingEnergy(1560, RestingFrom.Health), watch, null, null, true)))
        assertEquals(HealthSource.AppleHealth, assertIs<ActivityLine.Resting>(card.lines.first()).source)
    }

    @Test
    fun digestingFood_isItsOwnLine_andInTheTotal() {
        val card = assertNotNull(build(burned = burnedFor(resting, null, EverydayActivity.MostlySitting, ExerciseTotals(760, 0), true, foodKcal = 125)))

        assertEquals(listOf("Resting" to "1,600 kcal", "Everyday" to "320 kcal", "Exercise" to "760 kcal", "Digesting" to "125 kcal"), card.sum())
        assertEquals("2,805 kcal", card.total.text())
    }

    /** Digesting food is a tenth of eaten, so energy out turned up over hidden calories must not carry it. */
    @Test
    fun digestingFood_withCaloriesHidden_isNeitherALineNorInTheTotal() {
        val numbers = NumbersSettings(preset = PresetId.MacrosOnly, overrides = mapOf(Metric.EnergyOut to Visibility.Show))
        val card = assertNotNull(build(burned = burnedFor(resting, null, EverydayActivity.MostlySitting, ExerciseTotals(760, 0), true, foodKcal = 125), numbers = numbers))

        assertEquals(listOf("Resting" to "1,600 kcal", "Everyday" to "320 kcal", "Exercise" to "760 kcal"), card.sum())
        assertEquals("2,680 kcal", card.total.text())
    }

    @Test
    fun beforeRestingEnergyIsKnown_theEntriesStillShow() {
        val card = assertNotNull(build(burned = null))

        assertEquals(false, card.known)
        assertEquals(Display.Hidden, card.total)
        assertTrue(card.lines.isEmpty())
        assertEquals(2, card.entries.size)
    }

    @Test
    fun activitySwitchedOff_leavesRestingAndTheEverydayBaseline() {
        val card = assertNotNull(build(burned = burnedFor(resting, null, EverydayActivity.MostlySitting, ExerciseTotals(760, 0), activityCounted = false)))

        assertEquals("1,920 kcal", card.total.text(), "logged exercise stops counting; the ordinary day doesn't")
        assertEquals(listOf("Resting", "Everyday"), card.sum().map { it.first })
    }

    @Test
    fun theCard_isLeftOut_withTheReleaseOff_inModesThatHideEnergyOut_andForSomeoneNumbersHaveBeenHardFor() {
        assertNull(build(released = false))
        PresetId.entries.filter { it != PresetId.Full }.forEach { preset -> assertNull(build(preset = preset), "$preset hides energy out") }
        assertNull(build(settings = Settings(onboarding = OnboardingAnswers(hard = true))))
    }

    @Test
    fun onLater_beforeTheReveal_theEntriesShowWithoutAmounts() {
        val card = assertNotNull(build(numbers = NumbersSettings.later()))

        assertEquals(Display.Hidden, card.total)
        assertTrue(card.lines.all { it.amount == Display.Hidden })
        assertTrue(card.entries.all { it.amount == Display.Hidden })
        assertEquals(2, card.entries.size)
    }

    // --- view model ---

    private val diary = FakeDiaryRepository()

    private val noon = Instant.parse("2026-10-25T12:00:00Z")

    private fun TestScope.vm(
        released: Boolean = true,
        selected: FakeSelectedDay = FakeSelectedDay(),
        health: FakeHealthRepository = FakeHealthRepository(),
        healthOn: Boolean = false,
        burned: FakeBurnedRepository = FakeBurnedRepository(burnedFor(resting, null, EverydayActivity.MostlySitting, ExerciseTotals(340, 0), true)),
        release: ExerciseRelease = ExerciseRelease { released },
        ticker: MinuteTicker = MinuteTicker { flowOf(noon) },
    ) = TodayActivityViewModel(
        selectedDay = selected,
        settingsRepository = FakeSettingsRepository(),
        policySource = FakePolicySource(),
        burnedRepository = burned,
        exercise = FakeExercise(listOf(run, run.copy(id = "tomorrow", day = day.plusDays(1)))),
        diary = diary,
        release = release,
        calendar = FakeCalendar(),
        health = health,
        records = health,
        healthFlag = healthFlag(healthOn),
        ticker = ticker,
    )

    @Test
    fun burnedUnknown_saysWhatItNeeds_andFixOpensThatPlace() = runUnitTest {
        val vm = vm(burned = FakeBurnedRepository(null, unusable = setOf(BodyDetail.Weight)))
        val events = mutableListOf<TodayActivityEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        settle()
        val card = assertNotNull(vm.state.card)
        assertEquals(false, card.known)
        assertEquals(BurnedGap(setOf(BodyDetail.Weight)), card.gap)

        vm.takeAction(TodayActivityAction.BurnedFixTapped)
        settle()
        assertEquals(listOf<TodayActivityEvent>(TodayActivityEvent.OpenBurnedFix(BurnedFix.Weight)), events)
    }

    @Test
    fun burnedKnown_hasNoGap() = runUnitTest {
        val vm = vm()
        settle()
        assertNull(vm.state.card?.gap)
    }

    /** Read once when the card was built, a release the config turned on later never showed it until a restart. */
    @Test
    fun aReleaseTurnedOnAfterTheCardWasBuilt_showsIt_onTheNextMinute() = runUnitTest {
        var on = false
        val ticks = MutableStateFlow(noon)
        val vm = vm(release = ExerciseRelease { on }, ticker = MinuteTicker { ticks })
        settle()
        assertNull(vm.state.card)

        on = true
        ticks.value = noon.plus(kotlin.time.Duration.parse("1m"))
        settle()
        assertNotNull(vm.state.card)
    }

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    @Test
    fun theViewModel_followsTheSelectedDay_andOpensTheSheet() = runUnitTest {
        val selected = FakeSelectedDay()
        val vm = vm(selected = selected)
        val events = mutableListOf<TodayActivityEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        settle()
        assertEquals(listOf("run"), vm.state.card?.entries?.map { it.id })

        selected.select(day.plusDays(1))
        settle()
        assertEquals(listOf("tomorrow"), vm.state.card?.entries?.map { it.id })

        vm.takeAction(TodayActivityAction.AddTapped)
        vm.takeAction(TodayActivityAction.AddMissedTapped)
        vm.takeAction(TodayActivityAction.EntryTapped("tomorrow"))
        vm.takeAction(TodayActivityAction.RestingEnergyTapped)
        vm.takeAction(TodayActivityAction.BurnedInfoTapped)
        settle()
        assertEquals(
            listOf(
                TodayActivityEvent.OpenExercise(day.plusDays(1)),
                TodayActivityEvent.OpenExercise(day.plusDays(1), notOnWatch = true),
                TodayActivityEvent.OpenExercise(day.plusDays(1), "tomorrow"),
                TodayActivityEvent.OpenRestingEnergy,
                TodayActivityEvent.OpenBurnedExplainer,
            ),
            events,
        )
    }

    @Test
    fun aClosedDayHasNoPlus_andReopeningBringsItBack() = runUnitTest {
        val vm = vm()
        settle()
        assertEquals(false, vm.state.card?.closed)

        diary.days.value = mapOf(day to Day.empty(day).copy(closedAt = TodayFixtures.noon))
        settle()
        assertEquals(true, vm.state.card?.closed)

        diary.reopenDay(day)
        settle()
        assertEquals(false, vm.state.card?.closed)
    }

    @Test
    fun withTheReleaseOff_theViewModelReadsNothing() = runUnitTest {
        val vm = vm(released = false)
        settle()
        assertNull(vm.state.card)
    }

    // --- the health app's workouts ---

    private val garminRun = HealthWorkout(
        id = "garmin",
        kind = WorkoutKind.Running,
        title = "Morning run",
        start = Instant.parse("${day}T07:10:00Z"),
        end = Instant.parse("${day}T07:45:00Z"),
        activeKcal = 320.4,
        sourceName = "Garmin Connect",
    )

    private fun withWorkouts(preset: PresetId) = TodayActivityStateBuilder.build(
        TodayActivityInputs(day, Settings(), null, emptyList(), policy(preset), released = true, timeZone = TimeZone.UTC, workouts = listOf(garminRun)),
    ).card

    @Test
    fun workouts_areRows_withTheirAppTimeLengthAndEnergy() {
        val row = assertNotNull(withWorkouts(PresetId.Full)).workouts.single()
        assertEquals("Morning run", row.title)
        assertEquals("Garmin Connect", row.source)
        assertEquals(35, row.minutes)
        assertEquals(LocalTime(7, 10), row.startedAt)
        assertEquals("320", row.amount.text()?.filter(Char::isDigit))
    }

    @Test
    fun workouts_hideTheirEnergy_whereEnergyOutIsHidden() {
        withWorkouts(PresetId.NoNumbers)?.workouts?.forEach { assertEquals(Display.Hidden, it.amount) }
    }

    @Test
    fun theViewModel_readsWorkouts_onlyWithReleaseHealthOn() = runUnitTest {
        val health = FakeHealthRepository(connected = true, workouts = listOf(garminRun))
        val off = vm(health = health)
        settle()
        assertTrue(health.workoutDays.isEmpty())
        assertEquals(emptyList(), off.state.card?.workouts.orEmpty())

        val on = vm(health = health, healthOn = true)
        settle()
        assertEquals(listOf("garmin"), on.state.card?.workouts?.map { it.id })
    }

    private class FakeExercise(entries: List<ExerciseEntry>) : ExerciseRepository {
        private val all = MutableStateFlow(entries)
        override fun observeDay(day: LocalDate): Flow<List<ExerciseEntry>> = all.map { list -> list.filter { it.day == day } }
        override suspend fun get(id: String): ExerciseEntry? = all.value.firstOrNull { it.id == id }
        override suspend fun add(draft: ExerciseDraft): DayWrite<ExerciseEntry> = error("not used")
        override suspend fun update(id: String, draft: ExerciseDraft): DayWrite<Unit> = error("not used")
        override suspend fun delete(id: String): DayWrite<Unit> = error("not used")
    }
}

private fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))
