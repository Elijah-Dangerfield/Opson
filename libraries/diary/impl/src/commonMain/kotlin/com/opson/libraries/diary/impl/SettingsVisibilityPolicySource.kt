package com.opson.libraries.diary.impl

import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.NumbersChanger
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The live policy, built from the user's own numbers settings.
 *
 * Two things here are safety rules, not conveniences:
 *
 * - **No numbers until the settings are known.** On a phone signed into an
 *   existing account, the local settings are defaults (Full numbers) until the
 *   first pull lands. Serving those would flash every number at someone who
 *   chose to see none. No numbers is the only safe guess.
 * - **A new policy at the reveal time and at midnight.** Later numbers open
 *   at the user's reveal time ([NumbersSettings.revealAt], 9 pm unless they
 *   picked another). The policy reads the clock when it resolves, but screens
 *   only re-resolve when the policy they hold changes, so without a fresh
 *   instance at the boundary the numbers would stay shut until something else
 *   happened to recompose.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class SettingsVisibilityPolicySource(
    settingsRepository: SettingsRepository,
    calendar: LocalCalendar,
    clock: Clock,
    appScope: AppCoroutineScope,
    numbersChanger: NumbersChanger,
) : VisibilityPolicySource by LivePolicy(settingsRepository, calendar, clock, appScope, numbersChanger)

/**
 * The work behind [SettingsVisibilityPolicySource], on a plain [CoroutineScope]
 * so a test can run it in `backgroundScope`. Its boundary loop never ends by
 * design, and a test that runs it anywhere else waits on it forever.
 */
internal class LivePolicy(
    settingsRepository: SettingsRepository,
    private val calendar: LocalCalendar,
    private val clock: Clock,
    scope: CoroutineScope,
    private val numbersChanger: NumbersChanger,
) : VisibilityPolicySource {

    private val tick = MutableStateFlow(0)

    override val policy: StateFlow<VisibilityPolicy> =
        combine(settingsRepository.settings, settingsRepository.isKnown, tick) { settings, known, _ ->
            build(if (known) settings.numbers.afterTimer() else NO_NUMBERS)
        }.stateIn(scope, SharingStarted.Eagerly, build(NO_NUMBERS))

    init {
        // End after a set time: the policy above switches on the day itself; this writes the
        // switch down, so Modes shows it, other phones get it, and Today says so that day.
        scope.launch {
            combine(settingsRepository.settings, settingsRepository.isKnown, tick) { settings, known, _ ->
                known && settings.numbers.timedOut(calendar.today(), clock.now()) != null
            }.distinctUntilChanged().collect { due ->
                if (due) numbersChanger.endTimedMode()
            }
        }
        scope.launch {
            // A new reveal time restarts the wait, so a change never leaves the loop
            // asleep until the old boundary.
            settingsRepository.settings
                .map { it.numbers.revealAt }
                .distinctUntilChanged()
                .collectLatest { revealAt ->
                    while (true) {
                        val boundary = nextBoundary(clock.now(), revealAt)
                        // Steps of at most a minute: a coroutine delay does not count time
                        // the phone spent asleep, so one long delay could wake hours late.
                        while (clock.now() < boundary) {
                            delay((boundary - clock.now()).coerceIn(1.milliseconds, 1.minutes))
                        }
                        tick.value += 1
                    }
                }
        }
    }

    private fun build(numbers: NumbersSettings) = VisibilityPolicy(numbers, clock, calendar.timeZone())

    private fun NumbersSettings.afterTimer(): NumbersSettings = timedOut(calendar.today(), clock.now()) ?: this

    private fun nextBoundary(now: Instant, revealAt: LocalTime): Instant {
        val zone = calendar.timeZone()
        val local = now.toLocalDateTime(zone)
        val reveal = LocalDateTime(local.date, revealAt).toInstant(zone)
        return if (now < reveal) reveal else local.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
    }

    private companion object {
        val NO_NUMBERS = NumbersSettings(preset = PresetId.NoNumbers)
    }
}
