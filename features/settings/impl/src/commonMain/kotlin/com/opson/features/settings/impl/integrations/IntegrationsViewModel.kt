package com.opson.features.settings.impl.integrations

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.Toast
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.integrations_toast_connected
import opson.libraries.resources.generated.resources.integrations_toast_disconnected

/** Where "Get Health Connect" goes on Android 9 to 13, where it is an app from Play. */
const val HEALTH_CONNECT_PLAY_URL = "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"

/**
 * Watch and health apps. Reads the connection and today's energy from [HealthRepository] and
 * the live policy, rebuilds through [IntegrationsStateBuilder], and asks for permission only
 * from [IntegrationsAction.ConnectTapped].
 */
@Inject
class IntegrationsViewModel(
    private val health: HealthRepository,
    policySource: VisibilityPolicySource,
    calendar: LocalCalendar,
) : SEAViewModel<IntegrationsState, IntegrationsEvent, IntegrationsAction>(
    initialStateArg = IntegrationsState(source = health.source),
) {
    private var model = Model(policy = policySource.policy.value)

    private data class Model(
        val policy: VisibilityPolicy,
        val inputs: IntegrationsInputs? = null,
        val connecting: Boolean = false,
        val denied: Boolean = false,
    )

    init {
        val today = calendar.today()
        viewModelScope.launch {
            // energy() refreshes the status first, so the page opens on the real connection.
            combine(health.energy(today, calendar.timeZone()), health.status) { energy, status ->
                IntegrationsInputs(source = health.source, status = status, energy = energy, today = today)
            }.collect { takeAction(IntegrationsAction.InputsArrived(it)) }
        }
        viewModelScope.launch {
            policySource.policy.collect { takeAction(IntegrationsAction.PolicyArrived(it)) }
        }
    }

    override suspend fun handleAction(action: IntegrationsAction) {
        when (action) {
            is IntegrationsAction.InputsArrived -> action.rebuild { copy(inputs = action.inputs) }
            is IntegrationsAction.PolicyArrived -> action.rebuild { copy(policy = action.policy) }
            IntegrationsAction.ScreenResumed -> health.refresh()
            IntegrationsAction.ConnectTapped -> connect(action)
            IntegrationsAction.DisconnectTapped -> {
                action.rebuild { copy(denied = false) }
                health.disconnect()
                sendEvent(IntegrationsEvent.ShowToast(Toast(Res.string.integrations_toast_disconnected)))
            }
            IntegrationsAction.GetHealthConnectTapped -> sendEvent(IntegrationsEvent.OpenLink(HEALTH_CONNECT_PLAY_URL))
            IntegrationsAction.OpenSourceTapped -> health.openSourceSettings()
            IntegrationsAction.BackTapped -> sendEvent(IntegrationsEvent.Close)
        }
    }

    private suspend fun connect(action: IntegrationsAction) {
        if (model.connecting) return
        action.rebuild { copy(connecting = true, denied = false) }
        val outcome = health.connect()
        action.rebuild { copy(connecting = false, denied = outcome == ConnectOutcome.Denied) }
        when (outcome) {
            ConnectOutcome.Connected -> sendEvent(IntegrationsEvent.ShowToast(Toast(Res.string.integrations_toast_connected)))
            ConnectOutcome.NeedsInstall -> sendEvent(IntegrationsEvent.OpenLink(HEALTH_CONNECT_PLAY_URL))
            ConnectOutcome.Denied, ConnectOutcome.Unsupported -> Unit
        }
    }

    private suspend fun IntegrationsAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val inputs = current.inputs ?: return
        updateState { IntegrationsStateBuilder.build(inputs, current.policy, current.connecting, current.denied) }
    }
}
