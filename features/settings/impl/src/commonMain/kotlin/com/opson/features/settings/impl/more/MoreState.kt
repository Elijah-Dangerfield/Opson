package com.opson.features.settings.impl.more

import com.opson.libraries.diary.FastingAccess
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.PresetId

/** The More list as cards of rows, top to bottom. */
data class MoreState(val groups: List<List<MoreRow>> = emptyList())

/**
 * One row. No row carries an amount (design v3: "Row subtitles never show amounts"), so More
 * resolves nothing through the policy.
 *
 * Not built yet, so not here: Reminders and celebrations, Widgets and watch face. Watch and
 * health apps (0.2, `release.health`) sits in the first card; Fasting (0.2, `release.fasting`)
 * sits in the extras card, only where [FastingAccess] offers it.
 */
sealed interface MoreRow {
    /** "Fuel", or "Fuel, 1 change" when levels differ from the preset's. */
    data class Modes(val preset: PresetId, val changes: Int) : MoreRow

    /** "Energy range and macro split", never the range itself. */
    data object Targets : MoreRow

    data object LookAndFeel : MoreRow

    /** Watch and health apps (0.2): "Connected" or "Not connected", never an amount. */
    data class Health(val connected: Boolean) : MoreRow

    /** The daily goal in glasses, a setting rather than intake. */
    data class Water(val glasses: Int) : MoreRow

    /** "Off", or "On, 14:10". Only where fasting is offered: never in other modes, never after the statement. */
    data class Fasting(val on: Boolean, val plan: FastingPlan) : MoreRow

    data object Support : MoreRow

    /** "Free". There is no purchase flow in 0.1, so it opens nothing. */
    data object Plan : MoreRow
}

object MoreStateBuilder {

    /**
     * How the app treats you, optional extras, then help and plan. [healthConnected] is null
     * while `release.health` is off, and then there is no health row at all. [fastingReleased]
     * is `release.fasting`.
     */
    fun build(settings: Settings, healthConnected: Boolean? = null, fastingReleased: Boolean = false): MoreState = MoreState(
        groups = listOf(
            listOfNotNull(
                MoreRow.Modes(preset = settings.numbers.preset, changes = settings.numbers.changes.size),
                MoreRow.Targets,
                MoreRow.LookAndFeel,
                healthConnected?.let { MoreRow.Health(connected = it) },
            ),
            listOfNotNull(
                MoreRow.Water(glasses = settings.waterGlasses),
                MoreRow.Fasting(on = settings.fasting, plan = settings.fastPlan)
                    .takeIf { FastingAccess.of(settings, fastingReleased) == FastingAccess.Offered },
            ),
            listOf(MoreRow.Support, MoreRow.Plan),
        ),
    )
}
