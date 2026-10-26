package com.opson.libraries.diary.impl

import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * [SettingsNumbersChanger]: when a change applies at once and when it waits
 * for the pause, what it writes (the change, the stamp, a sprint's start) and
 * the app events it emits.
 *
 * Not covered here: `NumbersChange.applyTo` and `needsPause` themselves
 * (`NumbersSettingsTest` in `:libraries:nutrition`), and that the store
 * re-stamps `changedAt` and syncs the write (`SettingsStoreTest`).
 */
class SettingsNumbersChangerTest {

    private val clock = FakeClock(Instant.parse("2026-10-25T21:41:00Z"))
    private val calendar = FakeCalendar(LocalDate(2026, 10, 25))
    private val events = EventTree()

    @BeforeTest
    fun plant() = KLog.plant(events)

    @AfterTest
    fun uproot() = KLog.uproot(events)

    private fun changer(numbers: NumbersSettings): Pair<SettingsNumbersChanger, FakeSettingsRepository> {
        val repository = FakeSettingsRepository(Settings(numbers = numbers))
        return SettingsNumbersChanger(repository, calendar, clock) to repository
    }

    // --- pause off ---

    @Test
    fun withThePauseOffARaiseAppliesAndIsStamped() = runTest {
        val (changer, repository) = changer(NumbersSettings(preset = PresetId.Blind))

        val outcome = changer.request(NumbersChange.PickPreset(PresetId.Full))

        assertEquals(NumbersOutcome.Applied(fewer = false), outcome)
        assertEquals(PresetId.Full, repository.state.value.numbers.preset)
        assertEquals(clock.now, repository.state.value.numbers.changedAt)
    }

    @Test
    fun withThePauseOffALowerAppliesAsFewer() = runTest {
        val (changer, repository) = changer(NumbersSettings(preset = PresetId.Full))

        val outcome = changer.request(NumbersChange.Override(Metric.Calories, Visibility.Hide))

        assertEquals(NumbersOutcome.Applied(fewer = true), outcome)
        assertEquals(Visibility.Hide, repository.state.value.numbers.overrides[Metric.Calories])
    }

    @Test
    fun turningOffAPauseThatIsAlreadyOffAppliesWithoutAPause() = runTest {
        val (changer, repository) = changer(NumbersSettings(preset = PresetId.Blind))

        assertEquals(NumbersOutcome.Applied(fewer = false), changer.request(NumbersChange.TurnOffPause))
        assertFalse(repository.state.value.numbers.pauseBeforeMore)
    }

    // --- pause on ---

    @Test
    fun withThePauseOnALowerStillAppliesAtOnce() = runTest {
        val (changer, repository) = changer(NumbersSettings(preset = PresetId.Full, pauseBeforeMore = true))

        val outcome = changer.request(NumbersChange.PickPreset(PresetId.Blind))

        assertEquals(NumbersOutcome.Applied(fewer = true), outcome)
        assertEquals(PresetId.Blind, repository.state.value.numbers.preset)
    }

    @Test
    fun withThePauseOnARaiseNeedsThePauseAndWritesNothing() = runTest {
        val before = NumbersSettings(preset = PresetId.Blind, pauseBeforeMore = true, changedAt = null)
        val (changer, repository) = changer(before)

        val outcome = changer.request(NumbersChange.Override(Metric.Water, Visibility.Show))

        assertEquals(NumbersOutcome.NeedsPause, outcome)
        assertEquals(before, repository.state.value.numbers)
        assertTrue(events.named("mode.changed").isEmpty())
    }

    @Test
    fun withThePauseOnClearingAnOverrideThatShowsMoreNeedsThePause() = runTest {
        val hiddenWater = NumbersSettings(
            preset = PresetId.Blind,
            overrides = mapOf(Metric.Water to Visibility.Hide),
            pauseBeforeMore = true,
        )
        val (changer, repository) = changer(hiddenWater)

        assertEquals(NumbersOutcome.NeedsPause, changer.request(NumbersChange.ClearOverride(Metric.Water)))
        assertEquals(hiddenWater, repository.state.value.numbers)
    }

    @Test
    fun withThePauseOnAnEqualScoreChangeApplies() = runTest {
        // Full and Sprint show the same numbers.
        val (changer, repository) = changer(NumbersSettings(preset = PresetId.Full, pauseBeforeMore = true))

        assertEquals(NumbersOutcome.Applied(fewer = false), changer.request(NumbersChange.PickPreset(PresetId.Sprint)))
        assertEquals(PresetId.Sprint, repository.state.value.numbers.preset)
    }

    @Test
    fun withThePauseOnTurningItOffGoesThroughThePause() = runTest {
        val (changer, repository) = changer(NumbersSettings(preset = PresetId.Blind, pauseBeforeMore = true))

        assertEquals(NumbersOutcome.NeedsPause, changer.request(NumbersChange.TurnOffPause))
        assertTrue(repository.state.value.numbers.pauseBeforeMore)
    }

    // --- confirm, after the pause ---

    @Test
    fun confirmAppliesTheRaiseWithThePauseOn() = runTest {
        val (changer, repository) = changer(NumbersSettings(preset = PresetId.Blind, pauseBeforeMore = true))

        changer.confirm(NumbersChange.Override(Metric.Water, Visibility.Show))

        val numbers = repository.state.value.numbers
        assertEquals(Visibility.Show, numbers.overrides[Metric.Water])
        assertTrue(numbers.pauseBeforeMore)
        assertEquals(clock.now, numbers.changedAt)
    }

    @Test
    fun confirmTurnsThePauseOff() = runTest {
        val (changer, repository) = changer(NumbersSettings(preset = PresetId.Blind, pauseBeforeMore = true))

        changer.confirm(NumbersChange.TurnOffPause)

        assertFalse(repository.state.value.numbers.pauseBeforeMore)
        assertEquals(clock.now, repository.state.value.numbers.changedAt)
    }

    @Test
    fun turnOnPauseIsInstantAndStamped() = runTest {
        val (changer, repository) = changer(NumbersSettings(preset = PresetId.Full))

        changer.turnOnPause()

        assertTrue(repository.state.value.numbers.pauseBeforeMore)
        assertEquals(clock.now, repository.state.value.numbers.changedAt)
    }

    // --- Sprint ---

    @Test
    fun pickingSprintStartsItToday() = runTest {
        val (changer, repository) = changer(NumbersSettings(preset = PresetId.Full))

        changer.request(NumbersChange.PickPreset(PresetId.Sprint))

        assertEquals(calendar.today, repository.state.value.sprintStartedOn)
    }

    @Test
    fun pickingSprintAfterThePauseStartsItToday() = runTest {
        val (changer, repository) = changer(NumbersSettings(preset = PresetId.Blind, pauseBeforeMore = true))

        changer.confirm(NumbersChange.PickPreset(PresetId.Sprint))

        assertEquals(calendar.today, repository.state.value.sprintStartedOn)
    }

    @Test
    fun rePickingSprintToClearOverridesKeepsItsStart() = runTest {
        val started = LocalDate(2026, 10, 20)
        val repository = FakeSettingsRepository(
            Settings(
                numbers = NumbersSettings(PresetId.Sprint, overrides = mapOf(Metric.Fat to Visibility.Hide)),
                sprintStartedOn = started,
            ),
        )
        val changer = SettingsNumbersChanger(repository, calendar, clock)

        changer.request(NumbersChange.PickPreset(PresetId.Sprint))

        assertEquals(started, repository.state.value.sprintStartedOn)
        assertTrue(repository.state.value.numbers.overrides.isEmpty())
    }

    @Test
    fun anotherPresetLeavesTheSprintDateAlone() = runTest {
        val (changer, repository) = changer(NumbersSettings(preset = PresetId.Full))

        changer.request(NumbersChange.PickPreset(PresetId.Blind))

        assertNull(repository.state.value.sprintStartedOn)
    }

    // --- app events ---

    @Test
    fun anAppliedModeChangeIsLoggedWithPresetKeysOnly() = runTest {
        val (changer, _) = changer(NumbersSettings(preset = PresetId.Full))

        changer.request(NumbersChange.Override(Metric.Weight, Visibility.Hide))

        val event = events.named("mode.changed").single()
        assertEquals("full", event["from"])
        assertEquals("full", event["to"])
        assertEquals(true, event["adjusted"])
        assertEquals(true, event["fewer"])
        assertEquals(false, event["via_pause"])
        assertFalse(event.keys.any { it.contains("weight") || it.contains("level") })
    }

    @Test
    fun aConfirmedChangeLogsItCameThroughThePause() = runTest {
        val (changer, _) = changer(NumbersSettings(preset = PresetId.Blind, pauseBeforeMore = true))

        changer.confirm(NumbersChange.PickPreset(PresetId.Full))

        val modeChanged = events.named("mode.changed").single()
        assertEquals("blind", modeChanged["from"])
        assertEquals("full", modeChanged["to"])
        assertEquals(true, modeChanged["via_pause"])
        val completed = events.named("friction.completed").single()
        assertEquals("preset", completed["subject"])
        assertEquals(true, completed["applied"])
    }

    @Test
    fun thePauseSwitchAloneIsNotAModeChange() = runTest {
        val (changer, _) = changer(NumbersSettings(preset = PresetId.Blind, pauseBeforeMore = true))

        changer.confirm(NumbersChange.TurnOffPause)
        changer.turnOnPause()

        assertTrue(events.named("mode.changed").isEmpty())
        assertEquals("pause", events.named("friction.completed").single()["subject"])
    }

    @Test
    fun theSheetHooksLogShownAndKept() = runTest {
        val (changer, repository) = changer(NumbersSettings(preset = PresetId.Blind, pauseBeforeMore = true))
        val before = repository.state.value
        val change = NumbersChange.Override(Metric.Water, Visibility.Show)

        changer.pauseShown(change)
        changer.pauseKept(change)

        assertEquals("metric", events.named("friction.shown").single()["subject"])
        val completed = events.named("friction.completed").single()
        assertEquals(false, completed["applied"])
        assertEquals(before, repository.state.value)
    }
}

/** Collects `logEvent` entries: the event name and its attributes. */
private class EventTree : LogTree() {
    private val entries = mutableListOf<LogEntry>()

    override fun log(entry: LogEntry): LogId? {
        if (entry.context.extras.containsKey(EXTRA_APP_EVENT)) entries += entry
        return null
    }

    fun named(name: String): List<Map<String, Any?>> =
        entries.filter { it.context.extras[EXTRA_APP_EVENT] == name }.map { it.context.extras - EXTRA_APP_EVENT }
}
