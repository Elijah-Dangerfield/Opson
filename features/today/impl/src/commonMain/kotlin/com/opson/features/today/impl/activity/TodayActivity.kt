package com.opson.features.today.impl.activity

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.DiaryRepository
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
import com.opson.libraries.health.HealthRecords
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthWorkout
import com.opson.libraries.health.WorkoutKind
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.nutrition.shownOn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject
import kotlin.math.roundToInt

/*
 * Today's Activity card (`release.exercise`, docs/research/energy-expenditure.md), at the bottom
 * of Today: what the day has burned as lines that add up to the total (resting energy, the day's
 * activity, logged exercise, digesting food when it counts), where the activity came from, and
 * the exercise logged on it, each one tap from its sheet. Its own view model, so Today's state and
 * builder stay as they are; Today only places it.
 */

/** Null [card] draws nothing: the release is off, or energy out is hidden in this mode. */
data class TodayActivityState(val card: TodayActivityCard? = null)

/**
 * [total] is Burned, Hidden until resting energy is known ([known]) or while energy out does not
 * show. [lines] add up to it, in order; [entries] are the day's exercise, whatever the
 * visibility, since an activity and its length are not amounts of energy.
 *
 * [healthSource] is the health app counting the day's activity, null while the manual side
 * (everyday activity and logged exercise) counts. With one, a watch's workouts are already in its
 * active energy, so adding exercise is the quiet "your watch missed it" path rather than the +.
 */
data class TodayActivityCard(
    val day: LocalDate,
    val known: Boolean,
    val total: Display,
    val lines: List<ActivityLine>,
    val entries: List<ExerciseRow>,
    /** Closed with Close the day: nothing to add. */
    val closed: Boolean = false,
    val healthSource: HealthSource? = null,
    /** The health app's workouts that day ("Morning run · Garmin Connect · 320 kcal"), oldest first. */
    val workouts: List<WorkoutRow> = emptyList(),
)

/**
 * One workout from the health app. [title] is the app's own name when it keeps one, else the row
 * names the [kind]; [source] is the app or watch that recorded it. [amount] is its active energy,
 * resolved like every other amount here, so it hides with energy out.
 */
data class WorkoutRow(
    val id: String,
    val kind: WorkoutKind,
    val title: String?,
    val source: String?,
    val startedAt: LocalTime,
    val minutes: Int,
    val amount: Display,
)

/** One line of the sum. Every [amount] is resolved through energy out's level. */
sealed interface ActivityLine {
    val amount: Display

    /** Resting energy, and where it came from; [source] names the health app when it is [RestingFrom.Health]. */
    data class Resting(override val amount: Display, val from: RestingFrom, val source: HealthSource?) : ActivityLine

    /** The health app's active energy, movement and workouts together; [workouts] is the part inside a workout, Hidden at none. */
    data class Active(override val amount: Display, val workouts: Display) : ActivityLine

    /** No health app reading activity: the everyday baseline for [level]. */
    data class Everyday(override val amount: Display, val level: EverydayActivity) : ActivityLine

    /** Exercise logged by hand. [missedByWatch]: with a health app, only the entries its watch did not see count. */
    data class Exercise(override val amount: Display, val missedByWatch: Boolean) : ActivityLine

    /** The thermic effect of food, with "Count digesting food" on. */
    data class Digesting(override val amount: Display) : ActivityLine
}

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

sealed interface TodayActivityAction {
    data class InputsArrived(val inputs: TodayActivityInputs) : TodayActivityAction

    /** The + with no health app: the usual way to log exercise. */
    data object AddTapped : TodayActivityAction

    /** "Add exercise your watch missed", with a health app counting activity. */
    data object AddMissedTapped : TodayActivityAction
    data class EntryTapped(val id: String) : TodayActivityAction
    data object RestingEnergyTapped : TodayActivityAction
}

sealed interface TodayActivityEvent {
    /** [notOnWatch] starts a new entry with "My watch didn't record this" already on. */
    data class OpenExercise(val day: LocalDate, val entryId: String? = null, val notOnWatch: Boolean = false) : TodayActivityEvent
    data object OpenRestingEnergy : TodayActivityEvent
}

data class TodayActivityInputs(
    val day: LocalDate,
    val settings: Settings,
    val burned: Burned?,
    val entries: List<ExerciseEntry>,
    val policy: VisibilityPolicy,
    val released: Boolean,
    val timeZone: TimeZone,
    val closed: Boolean = false,
    /** This platform's health store, named on the card when it is the one counting. */
    val healthSource: HealthSource? = null,
    /** The day's workouts from the health store; empty without one, or with `release.health` off. */
    val workouts: List<HealthWorkout> = emptyList(),
)

object TodayActivityStateBuilder {
    fun build(inputs: TodayActivityInputs): TodayActivityState {
        val policy = inputs.policy
        if (!inputs.settings.exerciseOffered(inputs.released, policy.level(Metric.EnergyOut))) return TodayActivityState()
        val context = FormatContext(day = inputs.day)
        fun energy(kcal: Int?): Display = kcal?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.Today, context) } ?: Display.Hidden
        // Digesting food is a tenth of what was eaten: with calories short of Show it is not on the card (Burned.shownOn).
        val burned = inputs.burned?.shownOn(policy, Surface.Today, context)
        // The health app read activity, so it stands in for everything a watch would have seen.
        val healthRead = burned != null && burned.everydayKcal == null && (burned.movementKcal != null || burned.workoutKcal != null)
        val counted = burned?.activityCounted == true
        val healthSource = inputs.healthSource.takeIf { healthRead && counted }
        return TodayActivityState(
            TodayActivityCard(
                day = inputs.day,
                known = burned != null,
                total = energy(burned?.totalKcal),
                lines = if (burned == null) emptyList() else buildList {
                    val from = burned.resting.from
                    add(ActivityLine.Resting(energy(burned.resting.kcal), from, inputs.healthSource.takeIf { from == RestingFrom.Health }))
                    // The everyday baseline counts even with activity switched off (burnedFor).
                    burned.everydayKcal?.let { add(ActivityLine.Everyday(energy(it), inputs.settings.everydayActivity)) }
                    if (counted) {
                        if (healthRead) {
                            val workouts = burned.workoutKcal?.takeIf { it > 0 }
                            add(ActivityLine.Active(energy((burned.movementKcal ?: 0) + (burned.workoutKcal ?: 0)), energy(workouts)))
                        }
                        burned.exerciseKcal?.takeIf { it > 0 }?.let { add(ActivityLine.Exercise(energy(it), missedByWatch = healthRead)) }
                    }
                    burned.foodKcal?.let { add(ActivityLine.Digesting(energy(it))) }
                },
                entries = inputs.entries.map { entry ->
                    val watchHasIt = healthRead && counted && !entry.notOnWatch
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
                closed = inputs.closed,
                healthSource = healthSource,
                workouts = inputs.workouts.map { workout ->
                    WorkoutRow(
                        id = workout.id,
                        kind = workout.kind,
                        title = workout.title,
                        source = workout.sourceName,
                        startedAt = workout.start.toLocalDateTime(inputs.timeZone).time,
                        minutes = (workout.end - workout.start).inWholeMinutes.toInt().coerceAtLeast(0),
                        amount = workout.activeKcal?.takeIf { it > 0 }?.let { energy(it.roundToInt()) } ?: Display.Hidden,
                    )
                },
            ),
        )
    }
}

/**
 * Follows the day Today shows. Reads nothing while the release is off, and the health app's
 * workouts only with `release.health` on too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
@Suppress("LongParameterList")
class TodayActivityViewModel(
    selectedDay: SelectedDay,
    settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    burnedRepository: BurnedRepository,
    exercise: ExerciseRepository,
    diary: DiaryRepository,
    release: ExerciseRelease,
    calendar: LocalCalendar,
    health: HealthRepository,
    records: HealthRecords,
    healthFlag: HealthReleaseFlag,
) : SEAViewModel<TodayActivityState, TodayActivityEvent, TodayActivityAction>(initialStateArg = TodayActivityState()) {

    private var day: LocalDate = selectedDay.date.value

    init {
        if (release.isOn()) {
            val healthOn = healthFlag()
            viewModelScope.launch {
                selectedDay.date.flatMapLatest { date ->
                    val workouts = if (healthOn) records.workouts(date, calendar.timeZone()).map { it.orEmpty() } else flowOf(emptyList())
                    combine(
                        combine(burnedRepository.burned(date), workouts, ::Pair),
                        exercise.observeDay(date),
                        settingsRepository.settings,
                        policySource.policy,
                        diary.observeDay(date).map { it.closedByUser }.distinctUntilChanged(),
                    ) { (burned, fromHealth), entries, settings, policy, closed ->
                        TodayActivityInputs(
                            day = date,
                            settings = settings,
                            burned = burned,
                            entries = entries,
                            policy = policy,
                            released = true,
                            timeZone = calendar.timeZone(),
                            closed = closed,
                            // Only ever named when Burned says the store's activity is what counts.
                            healthSource = health.source,
                            workouts = fromHealth,
                        )
                    }
                }.collect { takeAction(TodayActivityAction.InputsArrived(it)) }
            }
        }
    }

    override suspend fun handleAction(action: TodayActivityAction) {
        when (action) {
            is TodayActivityAction.InputsArrived -> {
                day = action.inputs.day
                action.updateState { TodayActivityStateBuilder.build(action.inputs) }
            }
            TodayActivityAction.AddTapped -> sendEvent(TodayActivityEvent.OpenExercise(day))
            TodayActivityAction.AddMissedTapped -> sendEvent(TodayActivityEvent.OpenExercise(day, notOnWatch = true))
            is TodayActivityAction.EntryTapped -> sendEvent(TodayActivityEvent.OpenExercise(day, action.id))
            TodayActivityAction.RestingEnergyTapped -> sendEvent(TodayActivityEvent.OpenRestingEnergy)
        }
    }
}
