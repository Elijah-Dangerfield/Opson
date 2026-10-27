package com.opson.features.settings.impl.pause

import com.opson.features.settings.impl.FakeNumbersChanger
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.findSupportFlag
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.datetime.LocalTime
import kotlinx.coroutines.test.runCurrent
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_pause_saved
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The pause sheet: the ten-second wait on virtual time, that nothing applies before it ends,
 * that "Show them" applies exactly the held change once, and that every other way out applies
 * nothing. The telemetry these calls emit, and that it never carries the reason, is
 * `SettingsNumbersChangerTest`'s.
 */
class PauseViewModelTest : CoroutineTest() {

    private val change = NumbersChange.Override(Metric.Water, Visibility.Show)

    @Test
    fun openingTheSheetIsReported() = runUnitTest {
        val changer = FakeNumbersChanger()
        PauseViewModel(change, changer, findSupportFlag())

        assertEquals(listOf<NumbersChange>(change), changer.shown)
    }

    @Test
    fun theSubjectNamesWhatComesBack() = runUnitTest {
        val changer = FakeNumbersChanger()

        assertEquals(PauseSubject.Number(Metric.Water), PauseViewModel(change, changer, findSupportFlag()).state.subject)
        assertEquals(
            PauseSubject.Preset(PresetId.Full),
            PauseViewModel(NumbersChange.PickPreset(PresetId.Full), changer, findSupportFlag()).state.subject,
        )
        assertEquals(PauseSubject.ThePause, PauseViewModel(NumbersChange.TurnOffPause, changer, findSupportFlag()).state.subject)
        assertEquals(
            PauseSubject.RevealTime(LocalTime(19, 30)),
            PauseViewModel(NumbersChange.SetRevealAt(LocalTime(19, 30)), changer, findSupportFlag()).state.subject,
        )
        assertEquals(
            PauseSubject.ProgressPhotos,
            PauseViewModel(NumbersChange.SetProgressPhotos(shown = true), changer, findSupportFlag()).state.subject,
        )
    }

    @Test
    fun theCountdownReadsAsAClockAndFillsInAsItRuns() {
        val start = PauseState(PauseSubject.ThePause)
        val sevenLeft = start.copy(secondsLeft = 7)

        assertEquals("0:10", start.countdown)
        assertEquals("0:07", sevenLeft.countdown)
        assertEquals(0f, start.waited)
        assertEquals(0.3f, sevenLeft.waited, 0.0001f)
        assertEquals(1f, start.copy(secondsLeft = 0).waited)
    }

    @Test
    fun theCountdownReachesZeroAfterTenSeconds() = runUnitTest {
        val vm = PauseViewModel(change, FakeNumbersChanger(), findSupportFlag())
        assertEquals(10, vm.state.secondsLeft)

        advanceTimeBy(3.seconds)
        runCurrent()
        assertEquals(7, vm.state.secondsLeft)

        advanceTimeBy(6.seconds + 999.milliseconds)
        runCurrent()
        assertEquals(1, vm.state.secondsLeft)

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(0, vm.state.secondsLeft)
    }

    @Test
    fun showThemBeforeTheWaitIsOverDoesNothing() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = PauseViewModel(change, changer, findSupportFlag())
        val events = eventsOf(vm)
        advanceTimeBy(9.seconds)
        runCurrent()

        vm.takeAction(PauseAction.ShowThemTapped)

        assertTrue(changer.confirmed.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun showThemAfterTheWaitAppliesTheExactChangeOnce() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = PauseViewModel(change, changer, findSupportFlag())
        val events = eventsOf(vm)
        advanceTimeBy(10.seconds)
        runCurrent()

        vm.takeAction(PauseAction.ShowThemTapped)
        vm.takeAction(PauseAction.ShowThemTapped)
        vm.takeAction(PauseAction.SheetDismissed)

        assertEquals(listOf<NumbersChange>(change), changer.confirmed)
        assertTrue(changer.kept.isEmpty())
        assertEquals(
            listOf(PauseEvent.ShowToast(Toast(Res.string.settings_pause_saved)), PauseEvent.Dismiss),
            events,
        )
    }

    @Test
    fun keepingThingsAppliesNothing() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = PauseViewModel(change, changer, findSupportFlag())
        val events = eventsOf(vm)

        vm.takeAction(PauseAction.KeepAsTheyAreTapped)
        vm.takeAction(PauseAction.SheetDismissed)
        advanceTimeBy(10.seconds)
        runCurrent()
        vm.takeAction(PauseAction.ShowThemTapped)

        assertTrue(changer.confirmed.isEmpty())
        assertEquals(listOf<NumbersChange>(change), changer.kept)
        assertEquals(listOf<PauseEvent>(PauseEvent.Dismiss), events)
    }

    @Test
    fun aSwipeDownIsAKeep() = runUnitTest {
        val changer = FakeNumbersChanger()
        val vm = PauseViewModel(change, changer, findSupportFlag())

        vm.takeAction(PauseAction.SheetDismissed)

        assertEquals(listOf<NumbersChange>(change), changer.kept)
        assertTrue(changer.confirmed.isEmpty())
    }

    @Test
    fun aReasonCanBePickedAndCleared() = runUnitTest {
        val vm = PauseViewModel(change, FakeNumbersChanger(), findSupportFlag())

        vm.takeAction(PauseAction.ReasonPicked(PauseReason.Curious))
        assertEquals(PauseReason.Curious, vm.state.reason)

        vm.takeAction(PauseAction.ReasonPicked(PauseReason.ProviderAsked))
        assertEquals(PauseReason.ProviderAsked, vm.state.reason)

        vm.takeAction(PauseAction.ReasonPicked(PauseReason.ProviderAsked))
        assertNull(vm.state.reason)
    }

    @Test
    fun weightBackToNumbersIsHeldLikeAnyOtherRaise() = runUnitTest {
        // The Weight screen's old Numbers / Trend only / Hidden switch lives in Modes now, so
        // Trend only back to Numbers waits here, named as weight.
        val weight = NumbersChange.Override(Metric.Weight, Visibility.Show)
        val changer = FakeNumbersChanger()
        val vm = PauseViewModel(weight, changer, findSupportFlag())

        assertEquals(PauseSubject.Number(Metric.Weight), vm.state.subject)
        vm.takeAction(PauseAction.ShowThemTapped)
        assertTrue(changer.confirmed.isEmpty(), "nothing applies before the wait is over")

        advanceTimeBy(10.seconds)
        runCurrent()
        vm.takeAction(PauseAction.ShowThemTapped)
        assertEquals(listOf<NumbersChange>(weight), changer.confirmed)
    }

    @Test
    fun talkToSomeoneOpensFindSupport_onlyWithTheFlag_andChangesNothing() = runUnitTest {
        val changer = FakeNumbersChanger()
        val off = PauseViewModel(change, changer, findSupportFlag(on = false))
        val offEvents = eventsOf(off)
        assertEquals(false, off.state.talkToSomeone)
        off.takeAction(PauseAction.TalkToSomeoneTapped)
        assertTrue(offEvents.isEmpty())

        val on = PauseViewModel(change, changer, findSupportFlag(on = true))
        val onEvents = eventsOf(on)
        assertTrue(on.state.talkToSomeone)
        on.takeAction(PauseAction.TalkToSomeoneTapped)

        assertEquals(listOf<PauseEvent>(PauseEvent.OpenFindSupport), onEvents)
        assertTrue(changer.confirmed.isEmpty())
        assertTrue(changer.kept.isEmpty())
        assertEquals(false, on.state.finished)
    }
}
