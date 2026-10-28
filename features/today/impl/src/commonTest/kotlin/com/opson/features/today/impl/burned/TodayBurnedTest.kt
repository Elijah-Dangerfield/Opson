package com.opson.features.today.impl.burned

import com.opson.features.today.impl.FakeBurnedRepository
import com.opson.features.today.impl.FakeCalendar
import com.opson.features.today.impl.FakeDiaryRepository
import com.opson.features.today.impl.FakePolicySource
import com.opson.features.today.impl.FakeSelectedDay
import com.opson.features.today.impl.FakeSettingsRepository
import com.opson.features.today.impl.TodayFixtures
import com.opson.features.today.impl.policy
import com.opson.features.today.impl.text
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.ExerciseDraft
import com.opson.libraries.diary.ExerciseEntry
import com.opson.libraries.diary.ExerciseRepository
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.ExerciseRelease
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.ExerciseTotals
import com.opson.libraries.nutrition.HealthActivity
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.burnedFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Today's Burned section: where Burned came from, the day's entries, an entry the health app
 * already counts, the modes that leave the section out, and the taps that open the sheet.
 */
class TodayBurnedTest : CoroutineTest() {
    private val day = TodayFixtures.today
    private val resting = RestingEnergy(1600, RestingFrom.Calculated)
    private val run = ExerciseEntry("run", day, Instant.parse("${day}T07:10:00Z"), "running_10", minutes = 35, kcal = 340)
    private val polo = ExerciseEntry("polo", day, Instant.parse("${day}T17:30:00Z"), "other", name = "Canoe polo", minutes = 60, kcal = 420, notOnWatch = true)

    private fun build(
        burned: Burned? = burnedFor(resting, null, EverydayActivity.MostlySitting, ExerciseTotals(760, 420), true),
        entries: List<ExerciseEntry> = listOf(run, polo),
        settings: Settings = Settings(),
        preset: PresetId = PresetId.Full,
        numbers: NumbersSettings = NumbersSettings(preset = preset),
        released: Boolean = true,
    ) = TodayBurnedStateBuilder.build(TodayBurnedInputs(day, settings, burned, entries, policy(numbers), released, TimeZone.UTC)).card

    @Test
    fun withoutAHealthApp_partsAreRestingEverydayAndExercise() {
        val card = assertNotNull(build())

        assertEquals("2,680 kcal", card.total.text())
        assertEquals(
            listOf(BurnedPartKind.Resting to "1,600 kcal", BurnedPartKind.Everyday to "320 kcal", BurnedPartKind.Exercise to "760 kcal"),
            card.parts.map { it.kind to it.amount.text() },
        )
        assertEquals(listOf("run", "polo"), card.entries.map { it.id })
        assertEquals(LocalTime(7, 10), card.entries.first().startedAt)
        assertEquals("340 kcal", card.entries.first().amount.text())
    }

    @Test
    fun withAHealthApp_anEntryTheWatchSaw_countsNothingHere() {
        val card = assertNotNull(build(burned = burnedFor(resting, HealthActivity(380, 330), EverydayActivity.MostlySitting, ExerciseTotals(760, 420), true)))

        assertEquals(listOf(BurnedPartKind.Resting, BurnedPartKind.Health, BurnedPartKind.Exercise), card.parts.map { it.kind })
        assertEquals("710 kcal", card.parts[1].amount.text())
        val (watched, missed) = card.entries
        assertTrue(watched.watchHasIt)
        assertEquals(Display.Hidden, watched.amount)
        assertEquals("420 kcal", missed.amount.text())
    }

    @Test
    fun beforeRestingEnergyIsKnown_theEntriesStillShow() {
        val card = assertNotNull(build(burned = null))

        assertEquals(false, card.known)
        assertEquals(Display.Hidden, card.total)
        assertTrue(card.parts.isEmpty())
        assertEquals(2, card.entries.size)
    }

    @Test
    fun activitySwitchedOff_leavesRestingAndTheEverydayBaseline() {
        val card = assertNotNull(build(burned = burnedFor(resting, null, EverydayActivity.MostlySitting, ExerciseTotals(760, 0), activityCounted = false)))

        assertEquals("1,920 kcal", card.total.text(), "logged exercise stops counting; the ordinary day doesn't")
        assertEquals(listOf(BurnedPartKind.Resting, BurnedPartKind.Everyday), card.parts.map { it.kind })
    }

    @Test
    fun theSection_isLeftOut_withTheReleaseOff_inModesThatHideEnergyOut_andForSomeoneNumbersHaveBeenHardFor() {
        assertNull(build(released = false))
        PresetId.entries.filter { it != PresetId.Full }.forEach { preset -> assertNull(build(preset = preset), "$preset hides energy out") }
        assertNull(build(settings = Settings(onboarding = OnboardingAnswers(hard = true))))
    }

    @Test
    fun onLater_beforeTheReveal_theEntriesShowWithoutAmounts() {
        val card = assertNotNull(build(numbers = NumbersSettings.later()))

        assertEquals(Display.Hidden, card.total)
        assertTrue(card.parts.all { it.amount == Display.Hidden })
        assertTrue(card.entries.all { it.amount == Display.Hidden })
        assertEquals(2, card.entries.size)
    }

    // --- view model ---

    private val diary = FakeDiaryRepository()

    private fun TestScope.vm(released: Boolean = true, selected: FakeSelectedDay = FakeSelectedDay()) = TodayBurnedViewModel(
        selectedDay = selected,
        settingsRepository = FakeSettingsRepository(),
        policySource = FakePolicySource(),
        burnedRepository = FakeBurnedRepository(burnedFor(resting, null, EverydayActivity.MostlySitting, ExerciseTotals(340, 0), true)),
        exercise = FakeExercise(listOf(run, run.copy(id = "tomorrow", day = day.plusDays(1)))),
        diary = diary,
        release = ExerciseRelease { released },
        calendar = FakeCalendar(),
    )

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    @Test
    fun theViewModel_followsTheSelectedDay_andOpensTheSheet() = runUnitTest {
        val selected = FakeSelectedDay()
        val vm = vm(selected = selected)
        val events = mutableListOf<TodayBurnedEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        settle()
        assertEquals(listOf("run"), vm.state.card?.entries?.map { it.id })

        selected.select(day.plusDays(1))
        settle()
        assertEquals(listOf("tomorrow"), vm.state.card?.entries?.map { it.id })

        vm.takeAction(TodayBurnedAction.AddTapped)
        vm.takeAction(TodayBurnedAction.EntryTapped("tomorrow"))
        vm.takeAction(TodayBurnedAction.RestingEnergyTapped)
        settle()
        assertEquals(
            listOf(
                TodayBurnedEvent.OpenExercise(day.plusDays(1)),
                TodayBurnedEvent.OpenExercise(day.plusDays(1), "tomorrow"),
                TodayBurnedEvent.OpenRestingEnergy,
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
