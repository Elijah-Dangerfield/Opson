package com.opson.features.settings.impl.integrations

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.Toast
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthRecords
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.health.HealthWeighInImport
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.integrations_toast_connected
import opson.libraries.resources.generated.resources.integrations_toast_disconnected
import opson.libraries.resources.generated.resources.integrations_toast_weigh_ins_added
import opson.libraries.resources.generated.resources.integrations_toast_weigh_ins_failed
import opson.libraries.resources.generated.resources.integrations_toast_weigh_ins_none

/** Where "Get Health Connect" goes on Android 9 to 13, where it is an app from Play. */
const val HEALTH_CONNECT_PLAY_URL = "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"

/**
 * Watch and health apps. Reads the connection and today's energy from [HealthRepository], what
 * more there is to allow and where the data came from from [HealthRecords], the weigh-in switch
 * from [HealthWeighInImport], the exercise answer from settings, the days left out from the diary
 * and the live policy, rebuilds through [IntegrationsStateBuilder], and asks for permission only
 * from [IntegrationsAction.ConnectTapped] and [IntegrationsAction.AllowMoreTapped].
 */
@Inject
@Suppress("LongParameterList")
class IntegrationsViewModel(
    private val health: HealthRepository,
    private val records: HealthRecords,
    private val weighIns: HealthWeighInImport,
    private val settingsRepository: SettingsRepository,
    diary: DiaryRepository,
    policySource: VisibilityPolicySource,
    calendar: LocalCalendar,
) : SEAViewModel<IntegrationsState, IntegrationsEvent, IntegrationsAction>(
    initialStateArg = IntegrationsState(source = health.source),
) {
    private var model = Model(policy = policySource.policy.value)

    /** Read again each time the connection is (re)checked: a watch app may have started sharing since. */
    private val sources = MutableStateFlow<List<String>?>(null)

    private data class Model(
        val policy: VisibilityPolicy,
        val inputs: IntegrationsInputs? = null,
        val connecting: Boolean = false,
        val denied: Boolean = false,
        val failed: Boolean = false,
    )

    init {
        val today = calendar.today()
        viewModelScope.launch {
            // energy() refreshes the status first, so the page opens on the real connection.
            val store = combine(health.energy(today, calendar.timeZone()), health.status, records.canAskForMore, weighIns.on, sources) {
                    energy, status, more, weighInsOn, names ->
                StoreInputs(energy, status, more, weighInsOn, names)
            }
            combine(store, settingsRepository.settings, diary.observeDays(LeaveOutWindow.start(today), today)) { read, settings, days ->
                IntegrationsInputs(
                    source = health.source,
                    status = read.status,
                    energy = read.energy,
                    today = today,
                    settings = settings,
                    days = days,
                    canAskForMore = read.canAskForMore,
                    sources = read.sources,
                    weighInsOn = read.weighInsOn,
                )
            }.collect { takeAction(IntegrationsAction.InputsArrived(it)) }
        }
        viewModelScope.launch {
            health.status.map { it == HealthStatus.Connected }.distinctUntilChanged().collect { connected ->
                sources.value = if (connected) records.sources() else null
            }
        }
        viewModelScope.launch {
            policySource.policy.collect { takeAction(IntegrationsAction.PolicyArrived(it)) }
        }
    }

    private data class StoreInputs(
        val energy: EnergyExpended?,
        val status: HealthStatus,
        val canAskForMore: Boolean,
        val weighInsOn: Boolean,
        val sources: List<String>?,
    )

    override suspend fun handleAction(action: IntegrationsAction) {
        when (action) {
            is IntegrationsAction.InputsArrived -> action.rebuild { copy(inputs = action.inputs) }
            is IntegrationsAction.PolicyArrived -> action.rebuild { copy(policy = action.policy) }
            IntegrationsAction.ScreenResumed -> {
                health.refresh()
                if (health.status.value == HealthStatus.Connected) sources.value = records.sources()
            }
            IntegrationsAction.ConnectTapped, IntegrationsAction.AllowMoreTapped -> connect(action)
            IntegrationsAction.DisconnectTapped -> {
                action.rebuild { copy(denied = false, failed = false) }
                health.disconnect()
                sendEvent(IntegrationsEvent.ShowToast(Toast(Res.string.integrations_toast_disconnected)))
            }
            is IntegrationsAction.WeighInsToggled -> if (action.on) {
                val added = weighIns.turnOn()
                val toast = when {
                    added == null -> Res.string.integrations_toast_weigh_ins_failed
                    added > 0 -> Res.string.integrations_toast_weigh_ins_added
                    else -> Res.string.integrations_toast_weigh_ins_none
                }
                sendEvent(IntegrationsEvent.ShowToast(Toast(toast)))
            } else {
                weighIns.turnOff()
            }
            IntegrationsAction.GetHealthConnectTapped -> sendEvent(IntegrationsEvent.OpenLink(HEALTH_CONNECT_PLAY_URL))
            IntegrationsAction.OpenSourceTapped -> health.openSourceSettings()
            IntegrationsAction.BackTapped -> sendEvent(IntegrationsEvent.Close)
            is IntegrationsAction.ExercisePicked -> settingsRepository.update { current ->
                // Never asked of someone who said numbers have been hard, so never written for them either.
                if (current.onboarding.hard) current else current.copy(exerciseRange = action.choice)
            }
            IntegrationsAction.LeaveOutDayTapped -> sendEvent(IntegrationsEvent.OpenLeaveOut)
        }
    }

    private suspend fun connect(action: IntegrationsAction) {
        if (model.connecting) return
        val wasConnected = health.status.value == HealthStatus.Connected
        action.rebuild { copy(connecting = !wasConnected, denied = false, failed = false) }
        val outcome = health.connect()
        action.rebuild { copy(connecting = false, denied = outcome == ConnectOutcome.Denied, failed = outcome == ConnectOutcome.Failed) }
        when (outcome) {
            ConnectOutcome.Connected -> {
                sources.value = records.sources()
                if (!wasConnected) sendEvent(IntegrationsEvent.ShowToast(Toast(Res.string.integrations_toast_connected)))
            }
            ConnectOutcome.NeedsInstall -> sendEvent(IntegrationsEvent.OpenLink(HEALTH_CONNECT_PLAY_URL))
            ConnectOutcome.Denied, ConnectOutcome.Unsupported, ConnectOutcome.Failed -> Unit
        }
    }

    private suspend fun IntegrationsAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val inputs = current.inputs ?: return
        updateState { IntegrationsStateBuilder.build(inputs, current.policy, current.connecting, current.denied, current.failed) }
    }
}
