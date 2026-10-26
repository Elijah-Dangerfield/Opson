package com.opson.features.settings.impl.water

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.Toast
import com.opson.libraries.diary.NumbersChanger
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.WaterUnit
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.reminders.NotificationPermissions
import com.opson.libraries.reminders.ReminderEvents
import com.opson.libraries.reminders.ReminderKind
import com.opson.libraries.reminders.RemindersEnabled
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_saved
import opson.libraries.resources.generated.resources.settings_saved_fewer

/**
 * Water goal, glass size and units, plus "Show on Today", and with `release.reminders` on the
 * water reminders. The goal, size, unit and reminders are plain writes; "Show on Today" changes
 * what the user sees, so it goes through [NumbersChanger] and can be held by the pause like any
 * other number.
 */
@Inject
class WaterSettingsViewModel(
    @Assisted private val backToMore: Boolean,
    private val settingsRepository: SettingsRepository,
    private val numbersChanger: NumbersChanger,
    private val remindersEnabled: RemindersEnabled,
    private val permissions: NotificationPermissions,
) : SEAViewModel<WaterSettingsState, WaterSettingsEvent, WaterSettingsAction>(
    initialStateArg = WaterSettingsState(backToMore = backToMore),
) {

    init {
        viewModelScope.launch {
            settingsRepository.observe().collect { takeAction(WaterSettingsAction.InputsArrived(it)) }
        }
    }

    override suspend fun handleAction(action: WaterSettingsAction) {
        when (action) {
            is WaterSettingsAction.InputsArrived -> action.updateState {
                WaterSettingsStateBuilder.build(action.settings, backToMore, remindersEnabled())
            }
            WaterSettingsAction.ShowOnTodayToggled -> {
                val change = WaterSettingsStateBuilder.toggleChange(settingsRepository.settings.value.numbers)
                request(change)
            }
            is WaterSettingsAction.GoalPicked -> settingsRepository.update { it.copy(waterGlasses = action.glasses) }
            is WaterSettingsAction.GlassSizePicked -> settingsRepository.update { it.copy(glassMl = action.ml) }
            is WaterSettingsAction.UnitPicked -> settingsRepository.update { it.copy(waterUnit = action.unit) }
            WaterSettingsAction.RemindersToggled -> remindersToggled()
            WaterSettingsAction.BackTapped -> sendEvent(WaterSettingsEvent.Close)
        }
    }

    /** Turning water nudges on is the moment to ask for notifications, and the only one. */
    private suspend fun remindersToggled() {
        val next = settingsRepository.update { it.copy(waterReminders = !it.waterReminders) }
        var permission = permissions.status()
        if (next.waterReminders && permission == NotificationPermission.NotAsked) {
            permission = permissions.request()
            ReminderEvents.permission(granted = permission == NotificationPermission.Granted)
        }
        ReminderEvents.set(ReminderKind.Water, next, permission)
    }

    private suspend fun request(change: NumbersChange) {
        when (val outcome = numbersChanger.request(change)) {
            NumbersOutcome.NeedsPause -> sendEvent(WaterSettingsEvent.OpenPause(change))
            is NumbersOutcome.Applied -> sendEvent(
                WaterSettingsEvent.ShowToast(
                    Toast(if (outcome.fewer) Res.string.settings_saved_fewer else Res.string.settings_saved),
                ),
            )
        }
    }
}

sealed interface WaterSettingsEvent {
    data class ShowToast(val toast: Toast) : WaterSettingsEvent
    data class OpenPause(val change: NumbersChange) : WaterSettingsEvent
    data object Close : WaterSettingsEvent
}

sealed interface WaterSettingsAction {
    data class InputsArrived(val settings: Settings) : WaterSettingsAction
    data object ShowOnTodayToggled : WaterSettingsAction
    data class GoalPicked(val glasses: Int) : WaterSettingsAction
    data class GlassSizePicked(val ml: Int) : WaterSettingsAction
    data class UnitPicked(val unit: WaterUnit) : WaterSettingsAction
    data object RemindersToggled : WaterSettingsAction
    data object BackTapped : WaterSettingsAction
}
