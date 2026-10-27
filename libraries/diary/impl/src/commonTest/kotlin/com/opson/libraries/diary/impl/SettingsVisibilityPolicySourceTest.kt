package com.opson.libraries.diary.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.ModeTiming
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Surface
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The live policy fails closed: No numbers until the user's own settings are known,
 * and a fresh instance at 9 pm so delayed numbers open without a settings
 * change. The boundary loop never ends, so it runs in `backgroundScope`, which
 * the test cancels for us. Virtual time, so a standard dispatcher
 * (docs/practices/testing.md).
 */
class SettingsVisibilityPolicySourceTest : CoroutineTest() {
    override val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private val clock = FakeClock(Instant.parse("2026-10-25T20:58:00Z"))
    private val settings = KnowableSettings(Settings(numbers = NumbersSettings(preset = PresetId.Full)))

    private fun TestScope.live(calendar: FakeCalendar) =
        LivePolicy(settings, calendar, clock, backgroundScope, SettingsNumbersChanger(settings, calendar, clock))

    @Test
    fun noNumbersUntilTheUsersSettingsAreKnown() = runUnitTest {
        val source = live(FakeCalendar())
        settings.known.value = false
        runCurrent()
        assertEquals(PresetId.NoNumbers, source.policy.value.settings.preset)

        settings.known.value = true
        runCurrent()
        assertEquals(PresetId.Full, source.policy.value.settings.preset)
    }

    @Test
    fun followsAPresetChange() = runUnitTest {
        val source = live(FakeCalendar())
        runCurrent()
        settings.state.value = Settings(numbers = NumbersSettings(preset = PresetId.NutrientsFirst))
        runCurrent()
        assertEquals(PresetId.NutrientsFirst, source.policy.value.settings.preset)
    }

    @Test
    fun aNewPolicyArrivesAtNinePm() = runUnitTest {
        val source = live(FakeCalendar())
        runCurrent()
        val before = source.policy.value

        advanceTimeBy(1.minutes)
        runCurrent()
        assertSame(before, source.policy.value, "nothing changes before the boundary")

        clock.now = Instant.parse("2026-10-25T21:00:00Z")
        advanceTimeBy(1.minutes)
        runCurrent()
        assertNotSame(before, source.policy.value)
    }

    @Test
    fun aNewPolicyArrivesAtTheUsersOwnRevealTime() = runUnitTest {
        settings.state.value = Settings(numbers = NumbersSettings(preset = PresetId.Later, revealAt = LocalTime(19, 30)))
        clock.now = Instant.parse("2026-10-25T19:28:00Z")
        val source = live(FakeCalendar())
        runCurrent()
        val before = source.policy.value

        clock.now = Instant.parse("2026-10-25T19:30:00Z")
        advanceTimeBy(2.minutes)
        runCurrent()
        assertNotSame(before, source.policy.value)
        assertTrue(source.policy.value.isRevealed(Surface.Today))
    }

    @Test
    fun changingTheRevealTimeRestartsTheWait() = runUnitTest {
        val source = live(FakeCalendar())
        runCurrent()
        // 8:58 pm, waiting for 9. The user moves the reveal to 11 pm.
        settings.state.value = Settings(numbers = NumbersSettings(preset = PresetId.Later, revealAt = LocalTime(23, 0)))
        runCurrent()
        val afterChange = source.policy.value

        clock.now = Instant.parse("2026-10-25T21:00:00Z")
        advanceTimeBy(2.minutes)
        runCurrent()
        assertSame(afterChange, source.policy.value, "nine o'clock is no longer a boundary")
        assertFalse(source.policy.value.isRevealed(Surface.Today))
    }

    @Test
    fun aTimerThatRanOut_switchesThePolicy_andIsWrittenDown() = runUnitTest {
        val timing = ModeTiming(startedOn = LocalDate(2026, 10, 11), weeks = 2, then = PresetId.NoNumbers)
        settings.state.value = Settings(numbers = NumbersSettings(preset = PresetId.Full, endAfter = timing))
        val calendar = FakeCalendar(LocalDate(2026, 10, 24))
        val source = live(calendar)
        runCurrent()
        assertEquals(PresetId.Full, source.policy.value.settings.preset, "a day to go")

        calendar.today = LocalDate(2026, 10, 25)
        clock.now = Instant.parse("2026-10-25T23:59:00Z")
        advanceTimeBy(30.minutes)
        runCurrent()

        assertEquals(PresetId.NoNumbers, source.policy.value.settings.preset)
        assertEquals(PresetId.NoNumbers, settings.state.value.numbers.preset)
        assertEquals(LocalDate(2026, 10, 25), settings.state.value.modeTimerEnded?.on)
    }

    private class KnowableSettings(initial: Settings) : SettingsRepository {
        val state = MutableStateFlow(initial)
        val known = MutableStateFlow(true)
        override val settings: StateFlow<Settings> = state
        override val isKnown: StateFlow<Boolean> = known

        override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings =
            transform(state.value).also { state.value = it }
    }
}
