package com.opson.features.logger.impl

import com.opson.features.logger.ExerciseRoute
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.core.fixed
import com.opson.libraries.diary.ExerciseDraft
import com.opson.libraries.diary.ExerciseEntry
import com.opson.libraries.diary.ExerciseRepository
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.ExerciseActivities
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [ExerciseSheetViewModel]: a new entry starts on the activity list, an estimate needs a weigh-in,
 * a typed number wins, an edit keeps its saved number unless the activity or length changed, the
 * watch switch only with a health app, delete, and one write however many taps.
 */
class ExerciseSheetViewModelTest : CoroutineTest() {
    private val repo = FakeExercise()
    private val policySource = FakePolicySource()
    private val today = LoggerFixtures.today

    private fun vm(
        route: ExerciseRoute = ExerciseRoute(),
        kg: Double? = 70.0,
        connected: Boolean = false,
    ) = ExerciseSheetViewModel(
        route = route,
        exercise = repo,
        weights = FakeWeights(kg),
        health = FakeHealth(connected),
        healthFlag = HealthReleaseFlag(
            object : AppConfigMap() {
                override val map: Map<String, *> = mapOf("release" to mapOf("health" to true))
            },
        ),
        policySource = policySource,
        calendar = FakeCalendar(),
        selectedDay = FakeSelectedDay(),
        clock = Clock.fixed(Instant.parse("2026-10-25T18:20:00Z")),
    )

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private fun TestScope.events(vm: ExerciseSheetViewModel): List<ExerciseSheetEvent> {
        val events = mutableListOf<ExerciseSheetEvent>()
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return events
    }

    @Test
    fun aNewEntry_opensOnTheList_thenEstimatesFromTheLatestWeighIn() = runUnitTest {
        val vm = vm()
        settle()
        assertEquals(ExercisePane.Pick, vm.state.pane)
        assertFalse(vm.state.canSave)

        vm.takeAction(ExerciseSheetAction.ActivityPicked("walking_brisk"))
        settle()

        assertEquals(ExercisePane.Form, vm.state.pane)
        assertEquals("30", vm.state.minutes, "half an hour until changed")
        assertEquals(LocalTime(18, 20), vm.state.startedAt, "now, on today")
        // (4.8 - 1) x 70 x 0.5 = 133
        assertEquals("133 kcal", (vm.state.estimate as Display.Shown).text)
        assertTrue(vm.state.canSave)
        assertNull(vm.state.notOnWatch, "no health app, no switch")
    }

    @Test
    fun save_writesTheEstimate_once() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()
        vm.takeAction(ExerciseSheetAction.ActivityPicked("running_10"))
        vm.takeAction(ExerciseSheetAction.MinutesPicked(45))
        vm.takeAction(ExerciseSheetAction.TimePicked(LocalTime(7, 15)))
        settle()

        vm.takeAction(ExerciseSheetAction.SaveTapped)
        vm.takeAction(ExerciseSheetAction.SaveTapped)
        settle()

        val saved = repo.added.single()
        assertEquals(today, saved.day)
        assertEquals(Instant.parse("2026-10-25T07:15:00Z"), saved.startedAt)
        // (9.3 - 1) x 70 x 0.75 = 435.75
        assertEquals(436, saved.kcal)
        assertFalse(saved.kcalTyped)
        assertIs<ExerciseSheetEvent.ShowToast>(events.first())
        assertEquals(ExerciseSheetEvent.Dismiss, events.last())
    }

    @Test
    fun withNoWeighIn_theSheetAsksForTheNumber_andATypedNumberIsSaved() = runUnitTest {
        val vm = vm(kg = null)
        settle()
        vm.takeAction(ExerciseSheetAction.ActivityPicked("yoga"))
        settle()

        assertTrue(vm.state.noWeight)
        assertTrue(vm.state.numberNeeded)
        assertEquals(Display.Hidden, vm.state.estimate)
        assertFalse(vm.state.canSave)

        vm.takeAction(ExerciseSheetAction.NumberTyped("1 2 0 kcal"))
        settle()
        assertEquals("120", vm.state.typed)
        vm.takeAction(ExerciseSheetAction.SaveTapped)
        settle()

        assertEquals(120, repo.added.single().kcal)
        assertTrue(repo.added.single().kcalTyped)
    }

    @Test
    fun other_needsANameAndANumber() = runUnitTest {
        val vm = vm()
        settle()
        vm.takeAction(ExerciseSheetAction.ActivityPicked(ExerciseActivities.OTHER))
        vm.takeAction(ExerciseSheetAction.NumberTyped("250"))
        settle()
        assertFalse(vm.state.canSave, "no name yet")
        assertEquals("", vm.state.name)

        vm.takeAction(ExerciseSheetAction.NameTyped("Canoe polo"))
        settle()
        vm.takeAction(ExerciseSheetAction.SaveTapped)
        settle()

        val saved = repo.added.single()
        assertEquals("Canoe polo", saved.name)
        assertEquals(250, saved.kcal)
    }

    /** Later's energy out is held back until the reveal, so there is no number field: Other could never be saved. */
    @Test
    fun withEnergyOutOnLater_otherIsNotOffered_andCannotBePicked() = runUnitTest {
        policySource.policy.value = laterEnergyOut()
        val vm = vm()
        settle()
        assertFalse(vm.state.canNumber)
        assertFalse(vm.state.offersOther)

        vm.takeAction(ExerciseSheetAction.ActivityPicked(ExerciseActivities.OTHER))
        settle()

        assertNull(vm.state.activity)
        assertEquals(ExercisePane.Pick, vm.state.pane)
    }

    @Test
    fun withEnergyOutOnLater_aListedActivityStillSavesItsEstimate() = runUnitTest {
        policySource.policy.value = laterEnergyOut()
        val vm = vm()
        settle()
        vm.takeAction(ExerciseSheetAction.ActivityPicked("walking"))
        settle()
        assertTrue(vm.state.canSave)

        vm.takeAction(ExerciseSheetAction.SaveTapped)
        settle()

        assertFalse(repo.added.single().kcalTyped)
    }

    /** An Other entry made before energy out went to Later still lists, and re-picks, the activity it is. */
    @Test
    fun withEnergyOutOnLater_anOtherEntryBeingEdited_keepsOtherInTheList() = runUnitTest {
        policySource.policy.value = laterEnergyOut()
        repo.entries += ExerciseEntry(
            "e1", today, Instant.parse("2026-10-25T07:00:00Z"), ExerciseActivities.OTHER,
            name = "Climbing", minutes = 60, kcal = 500, kcalTyped = true,
        )
        val vm = vm(route = ExerciseRoute(entryId = "e1"))
        settle()
        assertTrue(vm.state.offersOther)
        assertTrue(vm.state.canSave)

        vm.takeAction(ExerciseSheetAction.ActivityTapped)
        vm.takeAction(ExerciseSheetAction.ActivityPicked(ExerciseActivities.OTHER))
        settle()

        assertEquals(ExerciseActivities.OTHER, vm.state.activity)
        assertEquals(ExercisePane.Form, vm.state.pane)
        vm.takeAction(ExerciseSheetAction.SaveTapped)
        settle()
        assertEquals(500, repo.updated.single().second.kcal)
    }

    @Test
    fun withEnergyOutShowing_otherIsOffered() = runUnitTest {
        val vm = vm()
        settle()

        assertTrue(vm.state.offersOther)
    }

    private fun laterEnergyOut() =
        VisibilityPolicy(NumbersSettings(overrides = mapOf(Metric.EnergyOut to Visibility.Delay)), Clock.fixed(NOON), TimeZone.UTC)

    @Test
    fun anEdit_keepsItsSavedNumber_untilTheActivityOrLengthChanges() = runUnitTest {
        repo.entries += ExerciseEntry("e1", today, Instant.parse("2026-10-25T07:00:00Z"), "cycling", minutes = 60, kcal = 400)
        val vm = vm(route = ExerciseRoute(entryId = "e1"), kg = 90.0)
        settle()
        assertTrue(vm.state.editing)
        assertEquals(ExercisePane.Form, vm.state.pane)
        assertEquals(LocalTime(7, 0), vm.state.startedAt)

        vm.takeAction(ExerciseSheetAction.TimePicked(LocalTime(8, 0)))
        vm.takeAction(ExerciseSheetAction.SaveTapped)
        settle()
        assertEquals(400, repo.updated.single().second.kcal, "a newer weigh-in does not rewrite it")
    }

    @Test
    fun anEdit_thatChangesTheLength_isWorkedOutAgain() = runUnitTest {
        repo.entries += ExerciseEntry("e1", today, Instant.parse("2026-10-25T07:00:00Z"), "cycling", minutes = 60, kcal = 400)
        val vm = vm(route = ExerciseRoute(entryId = "e1"), kg = 70.0)
        settle()

        vm.takeAction(ExerciseSheetAction.MinutesTyped("30"))
        vm.takeAction(ExerciseSheetAction.SaveTapped)
        settle()

        // (8 - 1) x 70 x 0.5 = 245
        assertEquals("e1" to 245, repo.updated.single().let { it.first to it.second.kcal })
    }

    @Test
    fun delete_removesTheEntry() = runUnitTest {
        repo.entries += ExerciseEntry("e1", today, Instant.parse("2026-10-25T07:00:00Z"), "cycling", minutes = 60, kcal = 400)
        val vm = vm(route = ExerciseRoute(entryId = "e1"))
        val events = events(vm)
        settle()

        vm.takeAction(ExerciseSheetAction.DeleteTapped)
        settle()

        assertEquals(listOf("e1"), repo.deleted)
        assertEquals(ExerciseSheetEvent.Dismiss, events.last())
    }

    @Test
    fun theWatchSwitch_showsWithAHealthAppConnected_andIsSaved() = runUnitTest {
        val vm = vm(connected = true)
        settle()
        vm.takeAction(ExerciseSheetAction.ActivityPicked("swimming"))
        settle()
        assertEquals(false, vm.state.notOnWatch)

        vm.takeAction(ExerciseSheetAction.NotOnWatchToggled(true))
        vm.takeAction(ExerciseSheetAction.SaveTapped)
        settle()

        assertTrue(repo.added.single().notOnWatch)
    }

    @Test
    fun withEnergyOutHidden_noAmountShows_andNoNumberCanBeTyped() = runUnitTest {
        policySource.policy.value = policy(PresetId.NoNumbers)
        val vm = vm()
        settle()
        vm.takeAction(ExerciseSheetAction.ActivityPicked("walking"))
        vm.takeAction(ExerciseSheetAction.NumberTyped("300"))
        settle()

        assertEquals(Display.Hidden, vm.state.estimate)
        assertFalse(vm.state.canNumber)
        assertEquals("", vm.state.typed)
    }

    @Test
    fun backFromTheListWithNothingPicked_closesTheSheet() = runUnitTest {
        val vm = vm()
        val events = events(vm)
        settle()

        vm.takeAction(ExerciseSheetAction.PickBackTapped)
        settle()

        assertEquals(listOf<ExerciseSheetEvent>(ExerciseSheetEvent.Dismiss), events)
    }

    private class FakeExercise : ExerciseRepository {
        val entries = mutableListOf<ExerciseEntry>()
        val added = mutableListOf<ExerciseDraft>()
        val updated = mutableListOf<Pair<String, ExerciseDraft>>()
        val deleted = mutableListOf<String>()

        override fun observeDay(day: LocalDate): Flow<List<ExerciseEntry>> = flowOf(entries.filter { it.day == day })
        override suspend fun get(id: String): ExerciseEntry? = entries.firstOrNull { it.id == id }
        override suspend fun add(draft: ExerciseDraft): ExerciseEntry {
            added += draft
            return ExerciseEntry("new", draft.day, draft.startedAt, draft.activity, draft.name, draft.minutes, draft.kcal, draft.kcalTyped, draft.notOnWatch)
        }

        override suspend fun update(id: String, draft: ExerciseDraft) {
            updated += id to draft
        }

        override suspend fun delete(id: String) {
            deleted += id
        }
    }

    private class FakeWeights(kg: Double?) : WeightRepository {
        private val weighIns = listOfNotNull(kg?.let { WeighIn("w", Instant.parse("2026-10-20T08:00:00Z"), it) })
        override fun observeWeighIns(since: Instant?): Flow<List<WeighIn>> = flowOf(weighIns)
        override suspend fun addWeighIn(kg: Double, measuredAt: Instant?): WeighIn = error("not used")
        override suspend fun removeWeighIn(id: String) = Unit
    }

    private class FakeHealth(connected: Boolean) : HealthRepository {
        override val source = HealthSource.AppleHealth
        override val status: StateFlow<HealthStatus> = MutableStateFlow(if (connected) HealthStatus.Connected else HealthStatus.NotConnected)
        override suspend fun refresh() = Unit
        override suspend fun connect(): ConnectOutcome = ConnectOutcome.Connected
        override suspend fun disconnect() = Unit
        override fun energy(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> = flowOf(null)
        override suspend fun energyBetween(from: LocalDate, to: LocalDate, timeZone: TimeZone): Map<LocalDate, EnergyExpended>? = null
        override fun openSourceSettings(): Boolean = false
    }
}
