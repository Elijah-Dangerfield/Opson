package com.opson.libraries.diary.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The live policy fails closed: Blind until the user's own settings are known,
 * and a fresh instance at 9 pm so delayed numbers open without a settings
 * change. The boundary loop never ends, so it runs in `backgroundScope`, which
 * the test cancels for us. Virtual time, so a standard dispatcher
 * (docs/practices/testing.md).
 */
class SettingsVisibilityPolicySourceTest : CoroutineTest() {
    override val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private val clock = FakeClock(Instant.parse("2026-10-25T20:58:00Z"))
    private val settings = KnowableSettings(Settings(numbers = NumbersSettings(preset = PresetId.Full)))

    @Test
    fun blindUntilTheUsersSettingsAreKnown() = runUnitTest {
        val source = LivePolicy(settings, FakeCalendar(), clock, backgroundScope)
        settings.known.value = false
        runCurrent()
        assertEquals(PresetId.Blind, source.policy.value.settings.preset)

        settings.known.value = true
        runCurrent()
        assertEquals(PresetId.Full, source.policy.value.settings.preset)
    }

    @Test
    fun followsAPresetChange() = runUnitTest {
        val source = LivePolicy(settings, FakeCalendar(), clock, backgroundScope)
        runCurrent()
        settings.state.value = Settings(numbers = NumbersSettings(preset = PresetId.Nutrient))
        runCurrent()
        assertEquals(PresetId.Nutrient, source.policy.value.settings.preset)
    }

    @Test
    fun aNewPolicyArrivesAtNinePm() = runUnitTest {
        val source = LivePolicy(settings, FakeCalendar(), clock, backgroundScope)
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

    private class KnowableSettings(initial: Settings) : SettingsRepository {
        val state = MutableStateFlow(initial)
        val known = MutableStateFlow(true)
        override val settings: StateFlow<Settings> = state
        override val isKnown: StateFlow<Boolean> = known

        override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings =
            transform(state.value).also { state.value = it }
    }
}
