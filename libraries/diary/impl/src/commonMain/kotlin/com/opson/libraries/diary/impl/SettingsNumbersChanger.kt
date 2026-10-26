package com.opson.libraries.diary.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.NumbersChanger
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
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
        return NumbersOutcome.Applied(fewer = after.numbers.score() < from.score())
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

    override fun pauseShown(change: NumbersChange) {
        logger.logEvent("friction.shown", ATTR_SUBJECT to change.subject())
    }

    override fun pauseKept(change: NumbersChange) {
        logger.logEvent("friction.completed", ATTR_SUBJECT to change.subject(), ATTR_APPLIED to false)
    }

    private fun Settings.applying(change: NumbersChange): Settings {
        val next = withNumbers(change.applyTo(numbers, clock.now()))
        // A new sprint starts its 14 days today; re-picking Sprint to clear
        // overrides is the same sprint and keeps its start.
        val startsSprint = change is NumbersChange.PickPreset &&
            change.id == PresetId.Sprint &&
            numbers.preset != PresetId.Sprint
        return if (startsSprint) next.copy(sprintStartedOn = calendar.today()) else next
    }

    /** Only when the mode or an override moved: the pause switch alone is not a mode change. */
    private fun logModeChanged(before: NumbersSettings, after: NumbersSettings, viaPause: Boolean) {
        if (before.preset == after.preset && before.overrides == after.overrides) return
        logger.logEvent(
            "mode.changed",
            "from" to before.preset.key,
            "to" to after.preset.key,
            "adjusted" to after.isAdjusted,
            "fewer" to (after.score() < before.score()),
            "via_pause" to viaPause,
        )
    }

    private fun NumbersChange.subject(): String = when (this) {
        is NumbersChange.PickPreset -> "preset"
        is NumbersChange.Override, is NumbersChange.ClearOverride -> "metric"
        NumbersChange.TurnOffPause -> "pause"
    }

    internal companion object {
        const val ATTR_SUBJECT = "subject"
        const val ATTR_APPLIED = "applied"
    }
}
