package com.opson.features.settings.impl.reminders

import androidx.lifecycle.viewModelScope
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.CelebrationStyle
import com.opson.libraries.diary.CelebrationsRelease
import com.opson.libraries.diary.EventLevel
import com.opson.libraries.diary.HabitsSource
import com.opson.libraries.diary.LoggingHabits
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.reminders.NotificationPermission
import com.opson.libraries.reminders.NotificationPermissions
import com.opson.libraries.reminders.ReminderEvents
import com.opson.libraries.reminders.ReminderKind
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import me.tatarka.inject.annotations.Inject

/**
 * Reminders and celebrations. Every change is a plain settings write: none of them shows a
 * number, so nothing goes through `NumbersChanger`. The coordinator picks the writes up and
 * reschedules.
 *
 * Permission is asked at the moment someone turns a reminder on, and only then: a level other
 * than Off, or a meal switch on. A denial keeps the choice and shows the note with Open settings.
 */
@Inject
class RemindersViewModel(
    private val settingsRepository: SettingsRepository,
    private val permissions: NotificationPermissions,
    celebrationsRelease: CelebrationsRelease = CelebrationsRelease.Off,
    private val habits: HabitsSource = HabitsSource.None,
) : SEAViewModel<RemindersState, RemindersEvent, RemindersAction>(initialStateArg = RemindersState()) {

    private var model = Model()
    private val logger = KLog.withTag("Celebrations")

    // Read once, as the page opens, like every release flag.
    private val celebrationsOn = celebrationsRelease.isOn()

    private data class Model(
        val settings: Settings? = null,
        val permission: NotificationPermission = NotificationPermission.Granted,
        val pickingTime: MealSlot? = null,
        val habits: LoggingHabits = LoggingHabits(),
    )

    init {
        viewModelScope.launch {
            settingsRepository.observe().collect { takeAction(RemindersAction.InputsArrived(it)) }
        }
        viewModelScope.launch { takeAction(RemindersAction.PermissionChecked(permissions.status())) }
        viewModelScope.launch { takeAction(RemindersAction.HabitsArrived(habits.current())) }
    }

    override suspend fun handleAction(action: RemindersAction) {
        when (action) {
            is RemindersAction.InputsArrived -> action.rebuild { it.copy(settings = action.settings) }
            is RemindersAction.PermissionChecked -> action.rebuild { it.copy(permission = action.permission) }
            is RemindersAction.HabitsArrived -> action.rebuild { it.copy(habits = action.habits) }
            RemindersAction.QuietToggled -> settingsRepository.update { it.copy(quiet = !it.quiet) }
            RemindersAction.CelebrationsToggled -> celebrationsChanged { it.copy(celebrationsEnabled = !it.celebrationsEnabled) }
            RemindersAction.CelebrationCountsToggled -> celebrationsChanged { it.copy(celebrationCounts = !(it.celebrationCounts ?: !it.onboarding.hard)) }
            is RemindersAction.BigDayPicked -> settingsRepository.update { it.copy(bigDayEvent = action.level) }
            is RemindersAction.MealLevelPicked -> mealLevelPicked(action)
            is RemindersAction.MealToggled -> mealToggled(action)
            is RemindersAction.MealTimeTapped -> action.rebuild { it.copy(pickingTime = action.slot) }
            RemindersAction.MealTimeDismissed -> action.rebuild { it.copy(pickingTime = null) }
            is RemindersAction.MealTimePicked -> mealTimePicked(action)
            RemindersAction.OpenSettingsTapped -> permissions.openSettings()
            RemindersAction.ScreenResumed -> takeAction(RemindersAction.PermissionChecked(permissions.status()))
            RemindersAction.BackTapped -> sendEvent(RemindersEvent.Close)
        }
    }

    /** A plain synced write, then `celebration.setting` with what it now is. */
    private suspend fun celebrationsChanged(change: (Settings) -> Settings) {
        val next = settingsRepository.update(transform = change)
        logger.logEvent("celebration.setting", "on" to next.celebrationsEnabled, "counts" to (next.celebrationStyle == CelebrationStyle.Counts))
    }

    private suspend fun mealLevelPicked(action: RemindersAction.MealLevelPicked) {
        val current = settingsRepository.settings.value
        if (current.remindersChosen && current.remindersEvent == action.level) return
        val next = settingsRepository.update { it.firstChoice().copy(remindersEvent = action.level, remindersChosen = true) }
        val permission = if (action.level != EventLevel.Off) askIfNotAsked(action) else model.permission
        ReminderEvents.set(ReminderKind.Meal, next, permission)
    }

    /** Turning a meal on while reminders are Off turns them on, at Subtle: the quieter choice. */
    private suspend fun mealToggled(action: RemindersAction.MealToggled) {
        val current = settingsRepository.settings.value
        val turningOn = !(current.mealReminderLevel != EventLevel.Off && current.mealReminders.isOn(action.slot))
        val next = settingsRepository.update {
            val level = if (turningOn && it.mealReminderLevel == EventLevel.Off) EventLevel.Subtle else it.remindersEvent
            it.firstChoice().copy(
                mealReminders = it.mealReminders.with(action.slot, turningOn),
                remindersEvent = level,
                remindersChosen = true,
            )
        }
        val permission = if (turningOn) askIfNotAsked(action) else model.permission
        ReminderEvents.set(ReminderKind.Meal, next, permission)
    }

    private suspend fun mealTimePicked(action: RemindersAction.MealTimePicked) {
        val slot = model.pickingTime
        action.rebuild { it.copy(pickingTime = null) }
        if (slot == null || settingsRepository.settings.value.mealTimes.at(slot) == action.time) return
        val next = settingsRepository.update { it.copy(mealTimes = it.mealTimes.with(slot, action.time)) }
        ReminderEvents.set(ReminderKind.Meal, next, model.permission)
    }

    /** On the first choice ever, the meal times this person usually logs at, where they never set their own. */
    private fun Settings.firstChoice(): Settings =
        RemindersStateBuilder.suggestedTimes(this, model.habits)?.let { copy(mealTimes = it) } ?: this

    /** The system prompt, only if it has never been shown. Granted and Denied answer as they are. */
    private suspend fun askIfNotAsked(action: RemindersAction): NotificationPermission {
        val status = permissions.status()
        val result = if (status == NotificationPermission.NotAsked) {
            permissions.request().also { ReminderEvents.permission(granted = it == NotificationPermission.Granted) }
        } else {
            status
        }
        action.rebuild { it.copy(permission = result) }
        return result
    }

    private suspend fun RemindersAction.rebuild(change: (Model) -> Model) {
        model = change(model)
        val settings = model.settings ?: return
        updateState { RemindersStateBuilder.build(settings, model.permission, model.pickingTime, celebrationsOn, model.habits) }
    }
}

sealed interface RemindersEvent {
    data object Close : RemindersEvent
}

sealed interface RemindersAction {
    data class InputsArrived(val settings: Settings) : RemindersAction
    data class PermissionChecked(val permission: NotificationPermission) : RemindersAction

    /** How this person usually logs, learned on the phone: where meal reminder times start. */
    data class HabitsArrived(val habits: LoggingHabits) : RemindersAction
    data object QuietToggled : RemindersAction

    /** Celebrations on or off; synced, like every setting. */
    data object CelebrationsToggled : RemindersAction

    /** Day counts on or off. */
    data object CelebrationCountsToggled : RemindersAction
    data class BigDayPicked(val level: EventLevel) : RemindersAction
    data class MealLevelPicked(val level: EventLevel) : RemindersAction
    data class MealToggled(val slot: MealSlot) : RemindersAction
    data class MealTimeTapped(val slot: MealSlot) : RemindersAction
    data object MealTimeDismissed : RemindersAction
    data class MealTimePicked(val time: LocalTime) : RemindersAction
    data object OpenSettingsTapped : RemindersAction

    /** Back on screen, from the system settings page or anywhere: permission may have changed. */
    data object ScreenResumed : RemindersAction
    data object BackTapped : RemindersAction
}
