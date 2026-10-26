package com.opson.features.settings.impl.integrations

import com.opson.features.settings.impl.Toast
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Settings
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.integrations_movement
import opson.libraries.resources.generated.resources.integrations_resting
import opson.libraries.resources.generated.resources.integrations_workouts
import org.jetbrains.compose.resources.StringResource

/**
 * Watch and health apps (design v3 `integrations.png`, `integrations--full.png`, 0.2): the
 * platform's health store, the connection to it, today's energy from it, whether exercise moves
 * the range, and the days left out of the math. `docs/plans/screens/integrations.md` says what
 * v3 draws that is not built (the watch rows, the Health card).
 */
data class IntegrationsState(
    val loading: Boolean = true,
    val source: HealthSource = HealthSource.HealthConnect,
    val connection: Connection = Connection.NotConnected,
    /** The last Connect was refused: say where to change it. */
    val denied: Boolean = false,
    /** The store has its own permission screen to send people to (Health Connect does, Health does not). */
    val canOpenSource: Boolean = false,
    /** Only while connected. */
    val expended: ExpendedCard? = null,
    /** "Should exercise change your range?": only while connected, and never for someone who said numbers have been hard. */
    val exercise: ExerciseQuestion? = null,
    val leftOut: LeftOutCard = LeftOutCard(emptyList()),
)

/** The three answers, with [selected] the one that applies: No until the person picks. */
data class ExerciseQuestion(val selected: ExerciseAdjustment) {
    val options: List<ExerciseAdjustment> get() = listOf(ExerciseAdjustment.None, ExerciseAdjustment.Some, ExerciseAdjustment.All)
}

/** The days marked untracked in the last [LeaveOutWindow.DAYS], newest first. */
data class LeftOutCard(val days: List<LeftOutDay>)

/** "Sun, Oct 18", and the day's own note ("Family visit") when it has one. */
data class LeftOutDay(val date: LocalDate, val label: String, val note: String?)

enum class Connection { Unsupported, NeedsInstall, NotConnected, Connecting, Connected }

sealed interface ExpendedCard {
    /** Resting, Everyday movement and Workouts (each only when the store had it), then the total. */
    data class Shown(val rows: List<ExpendedLine>, val total: Display) : ExpendedCard

    /** Connected, and nothing read yet today: "Nothing from Health Connect yet today." */
    data object Empty : ExpendedCard

    /** The mode keeps watch energy out of sight right now: one line, no label, no amount. */
    data object Hidden : ExpendedCard
}

data class ExpendedLine(val label: StringResource, val display: Display)

/** Everything the page reads. [days] are the window's days; only the untracked ones are listed. */
data class IntegrationsInputs(
    val source: HealthSource,
    val status: HealthStatus,
    val energy: EnergyExpended?,
    val today: LocalDate,
    val settings: Settings = Settings(),
    val days: List<Day> = emptyList(),
)

object IntegrationsStateBuilder {

    fun build(
        inputs: IntegrationsInputs,
        policy: VisibilityPolicy,
        connecting: Boolean = false,
        denied: Boolean = false,
    ): IntegrationsState {
        val connection = when {
            connecting -> Connection.Connecting
            else -> when (inputs.status) {
                HealthStatus.Unsupported -> Connection.Unsupported
                HealthStatus.NeedsInstall -> Connection.NeedsInstall
                HealthStatus.NotConnected -> Connection.NotConnected
                HealthStatus.Connected -> Connection.Connected
            }
        }
        return IntegrationsState(
            loading = false,
            source = inputs.source,
            connection = connection,
            denied = denied && connection == Connection.NotConnected,
            canOpenSource = inputs.source == HealthSource.HealthConnect,
            expended = if (connection == Connection.Connected) expended(inputs, policy) else null,
            exercise = if (connection == Connection.Connected && !inputs.settings.onboarding.hard) {
                ExerciseQuestion(inputs.settings.exerciseInRange)
            } else {
                null
            },
            leftOut = leftOut(inputs),
        )
    }

    private fun leftOut(inputs: IntegrationsInputs): LeftOutCard = LeftOutCard(
        inputs.days
            .filter { it.untracked && LeaveOutWindow.contains(it.date, inputs.today) }
            .sortedByDescending { it.date }
            .map { LeftOutDay(it.date, DateCopy.weekdayMonthDay(it.date), it.note?.trim()?.takeIf(String::isNotEmpty)) },
    )

    /**
     * Energy out has no soften word, so anything short of Show hides it, and the card says so in
     * words that carry no amount. A store with nothing yet says that, rather than "0 kcal".
     */
    private fun expended(inputs: IntegrationsInputs, policy: VisibilityPolicy): ExpendedCard {
        val context = FormatContext(day = inputs.today)
        if (policy.effectiveLevel(Metric.EnergyOut, Surface.Settings, context) != Visibility.Show) return ExpendedCard.Hidden
        val energy = inputs.energy
        val total = energy?.totalKcal ?: return ExpendedCard.Empty
        fun resolve(kcal: Double?) = policy.resolve(Metric.EnergyOut, kcal, Surface.Settings, context)
        return ExpendedCard.Shown(
            rows = listOf(
                ExpendedLine(Res.string.integrations_resting, resolve(energy.restingKcal)),
                ExpendedLine(Res.string.integrations_movement, resolve(energy.movementKcal)),
                ExpendedLine(Res.string.integrations_workouts, resolve(energy.workoutKcal)),
            ).filter { it.display !is Display.Hidden },
            total = resolve(total),
        )
    }
}

sealed interface IntegrationsAction {
    data class InputsArrived(val inputs: IntegrationsInputs) : IntegrationsAction
    data class PolicyArrived(val policy: VisibilityPolicy) : IntegrationsAction

    /** Back from the store's own screen, or the app came forward: the access may have changed. */
    data object ScreenResumed : IntegrationsAction
    data object ConnectTapped : IntegrationsAction
    data object DisconnectTapped : IntegrationsAction
    data object GetHealthConnectTapped : IntegrationsAction
    data object OpenSourceTapped : IntegrationsAction
    data class ExercisePicked(val choice: ExerciseAdjustment) : IntegrationsAction
    data object LeaveOutDayTapped : IntegrationsAction
    data object BackTapped : IntegrationsAction
}

sealed interface IntegrationsEvent {
    data class OpenLink(val url: String) : IntegrationsEvent
    data class ShowToast(val toast: Toast) : IntegrationsEvent
    data object OpenLeaveOut : IntegrationsEvent
    data object Close : IntegrationsEvent
}
