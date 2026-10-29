package com.opson.features.settings.impl.more

import com.opson.features.settings.MoreRowContribution
import com.opson.libraries.diary.FastingAccess
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.PresetId

/**
 * The More list as cards of rows, top to bottom, then the version line under them.
 *
 * @property logOut where the log out confirmation is; Idle draws no dialog.
 */
data class MoreState(
    val groups: List<List<MoreRow>> = emptyList(),
    val logOut: LogOutStep = LogOutStep.Idle,
)

/** Who is signed in, for the Log out row and its confirmation. */
data class MoreAccount(val email: String?, val guest: Boolean)

sealed interface LogOutStep {
    data object Idle : LogOutStep

    /** The dialog is up. A guest is told their log stays behind with no way back to it. */
    data class Confirming(val guest: Boolean) : LogOutStep

    /** [unsynced]: "Log out anyway" was chosen, so the dialog keeps saying what is lost until it closes. */
    data class Working(val guest: Boolean, val unsynced: Boolean = false) : LogOutStep

    /**
     * The sync before logging out didn't reach the account, so changes made since the last one
     * may be only on this phone, and logging out deletes them. Stay is the safe choice.
     */
    data object Unsynced : LogOutStep
}

/**
 * One row. No row carries an amount (design v3: "Row subtitles never show amounts"), so More
 * resolves nothing through the policy.
 *
 * Reminders and celebrations shows only with `release.reminders` on, Watch and health apps only
 * with `release.health`. Fasting (0.2, `release.fasting`) sits in the extras card, only where
 * [FastingAccess] offers it, and Widgets (0.2, `release.widgets`) after it. v3 calls that row
 * "Widgets and watch face"; it says "Widgets" until the watch face ships.
 */
sealed interface MoreRow {
    /**
     * Display: the mode and the look. "Fuel", or "Fuel, 1 change" when levels differ from the
     * preset's, since the mode is what most people come here to change.
     */
    data class Display(val preset: PresetId, val changes: Int) : MoreRow

    /** "Energy range and macro split", never the range itself. */
    data object Targets : MoreRow

    /**
     * More › Profile: the body details every estimate reads. "Age, sex, height and weight", never
     * the values. With `release.goalsAndEnergy`, which everything that reads them is behind.
     */
    data object Profile : MoreRow

    /** More › Today screen (design sync 4, 13.6), with `release.goalsAndEnergy`. */
    data object TodayScreen : MoreRow

    /** "Quiet mode on" or "Quiet mode off", as v3. Only with `release.reminders` on. */
    data class Reminders(val quiet: Boolean) : MoreRow
    /** Watch and health apps (0.2): "Connected" or "Not connected", never an amount. */
    data class Health(val connected: Boolean) : MoreRow

    /** The daily goal in glasses, a setting rather than intake. */
    data class Water(val glasses: Int) : MoreRow

    /** "Off", or "On, 14:10". Only where fasting is offered: never in other modes, never after the statement. */
    data class Fasting(val on: Boolean, val plan: FastingPlan) : MoreRow

    /** Home screen widgets. No subtitle and no value, as v3. Only with `release.widgets` on. */
    data object Widgets : MoreRow

    /** Help: helplines, export, feedback and deleting the account. */
    data object Support : MoreRow

    /** Straight to the bug report, the same page shaking the phone offers. */
    data object ReportBug : MoreRow

    /** Straight to feedback, also on Support. */
    data object SendFeedback : MoreRow

    /** "Free". There is no purchase flow in 0.1, so it opens nothing. */
    data object Plan : MoreRow

    /** The published legal pages, also on Support's About card; Apple wants privacy one tap from More. */
    data object Privacy : MoreRow
    data object Terms : MoreRow
    data object Credits : MoreRow

    /** "Signed in as …", or "Guest on this phone". Only while there is a session to end. */
    data class LogOut(val account: MoreAccount) : MoreRow

    /** A row another module added ([MoreRowContribution]), in a card of its own after Account. */
    data class Contributed(val row: MoreRowContribution) : MoreRow
}

object MoreStateBuilder {

    /**
     * How the app treats you, optional extras, help and plan, about, the account, then the rows
     * other modules [contributed], a card per section. Reminders shows only with [remindersOn]; [healthConnected] is null while
     * `release.health` is off, and then there is no health row at all. [fastingReleased] is
     * `release.fasting`, [widgetsOn] `release.widgets`, [goalsAndEnergyOn] `release.goalsAndEnergy`, which
     * also brings Profile, first in the first card.
     * [account] is null with no session, and then there is no Account card.
     */
    fun build(
        settings: Settings,
        healthConnected: Boolean? = null,
        remindersOn: Boolean = false,
        fastingReleased: Boolean = false,
        widgetsOn: Boolean = false,
        goalsAndEnergyOn: Boolean = false,
        account: MoreAccount? = null,
        contributed: List<MoreRowContribution> = emptyList(),
    ): MoreState = MoreState(
        groups = listOfNotNull(
            listOfNotNull(
                MoreRow.Profile.takeIf { goalsAndEnergyOn },
                MoreRow.Display(preset = settings.numbers.preset, changes = settings.numbers.changes.size),
                MoreRow.Targets,
                MoreRow.TodayScreen.takeIf { goalsAndEnergyOn },
                MoreRow.Reminders(quiet = settings.quiet).takeIf { remindersOn },
                healthConnected?.let { MoreRow.Health(connected = it) },
            ),
            listOfNotNull(
                MoreRow.Water(glasses = settings.waterGlasses),
                MoreRow.Fasting(on = settings.fasting, plan = settings.fastPlan)
                    .takeIf { FastingAccess.of(settings, fastingReleased) == FastingAccess.Offered },
                MoreRow.Widgets.takeIf { widgetsOn },
            ),
            listOf(MoreRow.Support, MoreRow.ReportBug, MoreRow.SendFeedback, MoreRow.Plan),
            listOf(MoreRow.Privacy, MoreRow.Terms, MoreRow.Credits),
            account?.let { listOf(MoreRow.LogOut(it)) },
        ) + contributed.groupBy { it.section }.values.map { rows -> rows.map(MoreRow::Contributed) },
    )
}

/**
 * The cards that get a label above them; the first three read as the design draws them, bare.
 * A contributed card is labelled with its own [MoreRowContribution.section].
 */
enum class MoreSection { About, Account }

fun List<MoreRow>.section(): MoreSection? = when (firstOrNull()) {
    MoreRow.Privacy -> MoreSection.About
    is MoreRow.LogOut -> MoreSection.Account
    else -> null
}

/**
 * The line under the cards: "Version 0.2.0 (702)". Off the store channel it adds the channel and
 * the commit, so a tester's screenshot names the exact build.
 */
object MoreVersion {
    fun line(versionName: String, buildNumber: Int, channel: String, commitSha: String): String {
        val base = "$versionName ($buildNumber)"
        return if (channel == STORE_CHANNEL) base else "$base · $channel · ${commitSha.take(SHORT_SHA)}"
    }

    private const val SHORT_SHA = 7
    private const val STORE_CHANNEL = "store"
}
