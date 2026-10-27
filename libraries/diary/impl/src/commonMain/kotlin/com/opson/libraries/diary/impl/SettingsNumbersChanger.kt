package com.opson.libraries.diary.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.ModeTimerEnded
import com.opson.libraries.diary.NumbersChanger
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.nutrition.ModeTiming
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock

/**
 * [NumbersChanger] over [SettingsRepository.update].
 *
 * Whether a change needs the pause is decided inside the update transform,
 * against the settings it is about to replace, so a second write landing
 * between the check and the write (a pull, another screen) cannot let a raise
 * through unpaused.
 *
 * Telemetry carries preset keys, the kind of change and booleans. Never a
 * level, an amount, or the pause sheet's answer.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class SettingsNumbersChanger(
    private val settings: SettingsRepository,
    private val calendar: LocalCalendar,
    private val clock: Clock,
) : NumbersChanger {

    private val logger = KLog.withTag("Numbers")

    override suspend fun request(change: NumbersChange): NumbersOutcome {
        var before: NumbersSettings? = null
        val after = settings.update { current ->
            if (change.needsPause(current.numbers)) {
                current
            } else {
                before = current.numbers
                current.applying(change)
            }
        }
        val from = before ?: return NumbersOutcome.NeedsPause
        logModeChanged(from, after.numbers, viaPause = false)
        return NumbersOutcome.Applied(fewer = from.isLower(after.numbers))
    }

    override suspend fun confirm(change: NumbersChange) {
        var before: NumbersSettings? = null
        val after = settings.update { current ->
            before = current.numbers
            current.applying(change)
        }
        logger.logEvent("friction.completed", ATTR_SUBJECT to change.subject(), ATTR_APPLIED to true)
        before?.let { logModeChanged(it, after.numbers, viaPause = true) }
    }

    override suspend fun turnOnPause() {
        settings.update { it.withNumbers(it.numbers.copy(pauseBeforeMore = true, changedAt = clock.now())) }
    }

    override suspend fun setEndAfter(weeks: Int, then: PresetId) {
        settings.update { current ->
            // Changing the length or the next mode of a running timer keeps its start.
            val start = current.numbers.endAfter?.startedOn ?: calendar.today()
            val timing = ModeTiming(startedOn = start, weeks = weeks, then = then)
            current.withNumbers(current.numbers.copy(endAfter = timing, changedAt = clock.now()))
        }
        logger.logEvent("mode.timer_set", "weeks" to weeks, "then" to then.key)
    }

    override suspend fun clearEndAfter() {
        settings.update { it.withNumbers(it.numbers.copy(endAfter = null, changedAt = clock.now())) }
        logger.logEvent("mode.timer_cleared")
    }

    override suspend fun endTimedMode() {
        val today = calendar.today()
        var before: NumbersSettings? = null
        val after = settings.update { current ->
            val next = current.numbers.timedOut(today, clock.now()) ?: return@update current
            before = current.numbers
            current.withNumbers(next).copy(modeTimerEnded = ModeTimerEnded(on = today, from = current.numbers.preset))
        }
        val from = before ?: return
        logger.logEvent("mode.timer_ended", "from" to from.preset.key, "to" to after.numbers.preset.key, "fewer" to from.isLower(after.numbers))
    }

    override fun pauseShown(change: NumbersChange) {
        logger.logEvent("friction.shown", ATTR_SUBJECT to change.subject())
    }

    override fun pauseKept(change: NumbersChange) {
        logger.logEvent("friction.completed", ATTR_SUBJECT to change.subject(), ATTR_APPLIED to false)
    }

    private fun Settings.applying(change: NumbersChange): Settings = withNumbers(change.applyTo(numbers, clock.now()))

    /** Only when the mode, an override or the photos row moved: the pause switch alone is not a mode change. */
    private fun logModeChanged(before: NumbersSettings, after: NumbersSettings, viaPause: Boolean) {
        val moved = before.preset != after.preset || before.overrides != after.overrides || before.progressPhotos != after.progressPhotos
        if (!moved) return
        logger.logEvent(
            "mode.changed",
            "from" to before.preset.key,
            "to" to after.preset.key,
            "adjusted" to after.isAdjusted,
            "fewer" to before.isLower(after),
            "via_pause" to viaPause,
        )
    }

    private fun NumbersChange.subject(): String = when (this) {
        is NumbersChange.PickPreset -> "preset"
        is NumbersChange.Override, is NumbersChange.ClearOverride -> "metric"
        NumbersChange.TurnOffPause -> "pause"
        is NumbersChange.SetRevealAt -> "reveal_time"
        is NumbersChange.SetRevealWeekly -> "reveal_on"
        is NumbersChange.SetProgressPhotos -> "progress_photos"
    }

    internal companion object {
        const val ATTR_SUBJECT = "subject"
        const val ATTR_APPLIED = "applied"
    }
}
