package com.opson.features.today.impl.burned

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.ExerciseEntry
import com.opson.libraries.diary.ExerciseRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.exerciseOffered
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.ExerciseRelease
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject

/*
 * Today's Burned section (`release.exercise`, docs/research/energy-expenditure.md): what the day
 * has burned and where it came from, and the exercise logged on it, each one tap from its sheet.
 * Its own view model, so Today's state and builder stay as they are; Today only places it.
 */

/** Null [card] draws nothing: the release is off, or energy out is hidden in this mode. */
data class TodayBurnedState(val card: TodayBurnedCard? = null)

/**
 * [total] is Burned, Hidden until resting energy is known ([known]) or while energy out does not
 * show. [parts] are where it came from; [entries] the day's exercise, whatever the visibility, since
 * an activity and its length are not amounts of energy.
 */
data class TodayBurnedCard(
    val day: LocalDate,
    val known: Boolean,
    val total: Display,
    val parts: List<BurnedPart>,
    val entries: List<ExerciseRow>,
)

enum class BurnedPartKind { Resting, Everyday, Health, Exercise }

data class BurnedPart(val kind: BurnedPartKind, val amount: Display)

/**
 * One entry: Other's [name] or its [activity] key's label, [minutes] long from [startedAt], and what
 * it counts. [watchHasIt] when a health app's activity stands in for it, so it adds nothing here.
 */
data class ExerciseRow(
    val id: String,
    val activity: String,
    val name: String?,
    val minutes: Int,
    val startedAt: LocalTime,
    val amount: Display,
    val watchHasIt: Boolean,
)

sealed interface TodayBurnedAction {
    data class InputsArrived(val inputs: TodayBurnedInputs) : TodayBurnedAction
    data object AddTapped : TodayBurnedAction
    data class EntryTapped(val id: String) : TodayBurnedAction
    data object RestingEnergyTapped : TodayBurnedAction
}

sealed interface TodayBurnedEvent {
    data class OpenExercise(val day: LocalDate, val entryId: String? = null) : TodayBurnedEvent
    data object OpenRestingEnergy : TodayBurnedEvent
}

data class TodayBurnedInputs(
    val day: LocalDate,
    val settings: Settings,
    val burned: Burned?,
    val entries: List<ExerciseEntry>,
    val policy: VisibilityPolicy,
    val released: Boolean,
    val timeZone: TimeZone,
)

object TodayBurnedStateBuilder {
    fun build(inputs: TodayBurnedInputs): TodayBurnedState {
        val policy = inputs.policy
        if (!inputs.settings.exerciseOffered(inputs.released, policy.level(Metric.EnergyOut))) return TodayBurnedState()
        val context = FormatContext(day = inputs.day)
        fun energy(kcal: Int?): Display = kcal?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.Today, context) } ?: Display.Hidden
        val burned = inputs.burned
        // The health app read activity, so it stands in for everything a watch would have seen.
        val healthRead = burned != null && burned.everydayKcal == null && (burned.movementKcal != null || burned.workoutKcal != null)
        val counted = burned?.activityCounted == true
        return TodayBurnedState(
            TodayBurnedCard(
                day = inputs.day,
                known = burned != null,
                total = energy(burned?.totalKcal),
                parts = if (burned == null) emptyList() else buildList {
                    add(BurnedPart(BurnedPartKind.Resting, energy(burned.resting.kcal)))
                    if (counted) {
                        burned.everydayKcal?.let { add(BurnedPart(BurnedPartKind.Everyday, energy(it))) }
                        if (healthRead) add(BurnedPart(BurnedPartKind.Health, energy((burned.movementKcal ?: 0) + (burned.workoutKcal ?: 0))))
                        burned.exerciseKcal?.takeIf { it > 0 }?.let { add(BurnedPart(BurnedPartKind.Exercise, energy(it))) }
                    }
                },
                entries = inputs.entries.map { entry ->
                    val watchHasIt = healthRead && !entry.notOnWatch
                    ExerciseRow(
                        id = entry.id,
                        activity = entry.activity,
                        name = entry.name,
                        minutes = entry.minutes,
                        startedAt = entry.startedAt.toLocalDateTime(inputs.timeZone).time,
                        amount = if (watchHasIt) Display.Hidden else energy(entry.kcal),
                        watchHasIt = watchHasIt,
                    )
                },
            ),
        )
    }
}

/** Follows the day Today shows. Reads nothing while the release is off. */
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
@Suppress("LongParameterList")
class TodayBurnedViewModel(
    selectedDay: SelectedDay,
    settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    burnedRepository: BurnedRepository,
    exercise: ExerciseRepository,
    release: ExerciseRelease,
    calendar: LocalCalendar,
) : SEAViewModel<TodayBurnedState, TodayBurnedEvent, TodayBurnedAction>(initialStateArg = TodayBurnedState()) {

    private var day: LocalDate = selectedDay.date.value

    init {
        if (release.isOn()) {
            viewModelScope.launch {
                selectedDay.date.flatMapLatest { date ->
                    combine(
                        burnedRepository.burned(date),
                        exercise.observeDay(date),
                        settingsRepository.settings,
                        policySource.policy,
                    ) { burned, entries, settings, policy ->
                        TodayBurnedInputs(date, settings, burned, entries, policy, released = true, timeZone = calendar.timeZone())
                    }
                }.collect { takeAction(TodayBurnedAction.InputsArrived(it)) }
            }
        }
    }

    override suspend fun handleAction(action: TodayBurnedAction) {
        when (action) {
            is TodayBurnedAction.InputsArrived -> {
                day = action.inputs.day
                action.updateState { TodayBurnedStateBuilder.build(action.inputs) }
            }
            TodayBurnedAction.AddTapped -> sendEvent(TodayBurnedEvent.OpenExercise(day))
            is TodayBurnedAction.EntryTapped -> sendEvent(TodayBurnedEvent.OpenExercise(day, action.id))
            TodayBurnedAction.RestingEnergyTapped -> sendEvent(TodayBurnedEvent.OpenRestingEnergy)
        }
    }
}
