package com.opson.features.settings.impl.integrations

import com.opson.features.settings.impl.Toast
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.integrations_movement
import opson.libraries.resources.generated.resources.integrations_resting
import opson.libraries.resources.generated.resources.integrations_workouts
import org.jetbrains.compose.resources.StringResource

/**
 * Watch and health apps (design v3 `integrations.png`, 0.2): the platform's health store, the
 * connection to it, and today's energy from it. Only the store's own row and Expended today are
 * built; `docs/plans/screens/integrations.md` says why the rest waits.
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
)

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

/** Everything the page reads. */
data class IntegrationsInputs(
    val source: HealthSource,
    val status: HealthStatus,
    val energy: EnergyExpended?,
    val today: LocalDate,
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
        )
    }

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
    data object BackTapped : IntegrationsAction
}

sealed interface IntegrationsEvent {
    data class OpenLink(val url: String) : IntegrationsEvent
    data class ShowToast(val toast: Toast) : IntegrationsEvent
    data object Close : IntegrationsEvent
}
