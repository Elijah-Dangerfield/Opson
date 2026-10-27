package com.opson.features.settings.impl.qa

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.Toast
import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.config.AppConfigRefresher
import com.opson.libraries.config.ConfigOverride
import com.opson.libraries.config.ConfigOverrideRepository
import com.opson.libraries.config.ConfiguredValue
import com.opson.libraries.config.QaConfigValue
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.diary.CheckInEnabled
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.db.FoodCacheDao
import com.opson.libraries.identity.IdentityConfig
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.networking.NetworkConfig
import com.opson.libraries.opson.AppCache
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.reminders.ReminderTestFire
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.qa_clear_caches_done
import opson.libraries.resources.generated.resources.qa_copied
import opson.libraries.resources.generated.resources.qa_failed
import opson.libraries.resources.generated.resources.qa_fire_no_permission
import opson.libraries.resources.generated.resources.qa_fired
import opson.libraries.resources.generated.resources.qa_overrides_cleared
import opson.libraries.resources.generated.resources.qa_reset_onboarding_done
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.seconds

/**
 * The QA menu. Every tool here acts on this phone only: overrides are local, the caches are
 * local, and nothing calls the server except the config refetch every foreground already does.
 */
@Inject
class QaViewModel(
    @Assisted build: QaStateBuilder.BuildFacts,
    private val auth: AuthRepository,
    identityConfig: IdentityConfig,
    networkConfig: NetworkConfig,
    configValues: Set<QaConfigValue>,
    private val config: AppConfigFlow,
    private val overrides: ConfigOverrideRepository,
    private val refresher: AppConfigRefresher,
    private val appCache: AppCache,
    private val testFire: ReminderTestFire,
    private val checkInEnabled: CheckInEnabled,
    private val settingsRepository: SettingsRepository,
    private val calendar: LocalCalendar,
    private val foodCache: FoodCacheDao,
) : SEAViewModel<QaState, QaEvent, QaAction>(initialStateArg = QaState(build = QaStateBuilder.build(build))) {

    private val values: List<ConfiguredValue<*>> = configValues.filterIsInstance<ConfiguredValue<*>>()
    private val serverUrl = networkConfig.baseUrl
    private val supabaseUrl = identityConfig.supabaseUrl

    init {
        viewModelScope.launch {
            val authStates = auth.observe().map<AuthState, AuthState?> { it }.onStart { emit(null) }
            val overridden = overrides.getOverridesFlow().map { list -> list.map { it.path }.toSet() }.onStart { emit(emptySet()) }
            combine(authStates, config, overridden, QaAction::InputsArrived).collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: QaAction) {
        when (action) {
            is QaAction.InputsArrived -> action.updateState {
                it.copy(
                    identity = QaStateBuilder.identity(action.auth, serverUrl, supabaseUrl),
                    config = QaStateBuilder.config(values, action.config, action.overriddenPaths),
                )
            }
            is QaAction.Copied -> toast(Res.string.qa_copied, getString(action.label))
            is QaAction.FireTapped -> {
                val permission = testFire.fire(action.reminder.key(calendar.today()))
                toast(if (permission == NotificationPermission.Granted) Res.string.qa_fired else Res.string.qa_fire_no_permission)
            }
            QaAction.CheckInTapped -> work(action) { openCheckIn() }
            QaAction.ResetOnboardingTapped -> work(action) {
                appCache.update { it.copy(hasUserOnboarded = false) }
                toast(Res.string.qa_reset_onboarding_done)
            }
            QaAction.ClearCachesTapped -> work(action) {
                foodCache.deleteAllButFavorites()
                refresher.refresh()
                toast(Res.string.qa_clear_caches_done)
            }
            is QaAction.FlagToggled -> overrides.addOverride(ConfigOverride(action.path, action.on))
            is QaAction.ValueSet -> QaStateBuilder.parse(action.row.kind, action.raw)?.let {
                overrides.addOverride(ConfigOverride(action.row.path, it))
            }
            QaAction.ClearOverridesTapped -> {
                overrides.clearAll()
                toast(Res.string.qa_overrides_cleared)
            }
            QaAction.BackTapped -> sendEvent(QaEvent.Close)
        }
    }

    /**
     * Turns on what the check-in needs, then opens Close the day for today. Waits for the
     * override to reach the config, so Close the day reads it on its first build.
     */
    private suspend fun openCheckIn() {
        if (!checkInEnabled()) {
            overrides.addOverride(ConfigOverride(checkInEnabled.path, true))
            withTimeoutOrNull(OVERRIDE_WAIT) { config.first { CheckInEnabled(it).value } }
        }
        if (!settingsRepository.settings.value.checkIn) settingsRepository.update { it.copy(checkIn = true) }
        sendEvent(QaEvent.OpenDayClose(calendar.today().toString()))
    }

    /** One tool at a time; a failure is logged and said, never thrown at the menu. */
    private suspend fun work(action: QaAction, block: suspend () -> Unit) {
        if (state.busy) return
        action.updateState { it.copy(busy = true) }
        Catching { block() }
            .logOnFailure { "QA tool failed: $action" }
            .onFailure { toast(Res.string.qa_failed) }
        action.updateState { it.copy(busy = false) }
    }

    private suspend fun toast(message: StringResource, vararg args: Any) {
        sendEvent(QaEvent.ShowToast(Toast(message, args.toList())))
    }

    private companion object {
        val OVERRIDE_WAIT = 2.seconds
    }
}
