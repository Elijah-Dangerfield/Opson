package com.opson.features.settings.impl.more

import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.PresetId

/** The More list as cards of rows, top to bottom. */
data class MoreState(val groups: List<List<MoreRow>> = emptyList())

/**
 * One row. No row carries an amount (design v3: "Row subtitles never show amounts"), so More
 * resolves nothing through the policy.
 *
 * Reminders and celebrations shows only with `release.reminders` on. Not built yet, so not here:
 * Watch and health apps, Fasting, Widgets and watch face. Fasting, when it lands, belongs in the extras card and only where
 * `Fasting.allowed(preset)`.
 */
sealed interface MoreRow {
    /** "Fuel", or "Fuel, 1 change" when levels differ from the preset's. */
    data class Modes(val preset: PresetId, val changes: Int) : MoreRow

    /** "Energy range and macro split", never the range itself. */
    data object Targets : MoreRow

    data object LookAndFeel : MoreRow

    /** "Quiet mode on" or "Quiet mode off", as v3. Only with `release.reminders` on. */
    data class Reminders(val quiet: Boolean) : MoreRow

    /** The daily goal in glasses, a setting rather than intake. */
    data class Water(val glasses: Int) : MoreRow

    data object Support : MoreRow

    /** "Free". There is no purchase flow in 0.1, so it opens nothing. */
    data object Plan : MoreRow
}

object MoreStateBuilder {

    /** How the app treats you, optional extras, then help and plan. */
    fun build(settings: Settings, remindersOn: Boolean = false): MoreState = MoreState(
        groups = listOf(
            listOfNotNull(
                MoreRow.Modes(preset = settings.numbers.preset, changes = settings.numbers.changes.size),
                MoreRow.Targets,
                MoreRow.Reminders(quiet = settings.quiet).takeIf { remindersOn },
                MoreRow.LookAndFeel,
            ),
            listOf(MoreRow.Water(glasses = settings.waterGlasses)),
            listOf(MoreRow.Support, MoreRow.Plan),
        ),
    )
}
