package com.opson.features.today.impl.activity

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.ExerciseEntry
import com.opson.libraries.diary.ExerciseRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MinuteTicker
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.exerciseOffered
import com.opson.features.today.impl.BurnedFix
import com.opson.features.today.impl.BurnedGap
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.ExerciseRelease
import com.opson.libraries.health.HealthRecords
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.StepsRepository
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
    /** While not [known]: what resting energy is waiting on, said in place of the sum, with its Fix. */
    val gap: BurnedGap? = null,
    /**
     * The day's steps from the health app, with "Steps on Today" on and `release.health`; null
     * without, or with none recorded. Never part of the sum: walking is already in active energy.
     */
    val steps: Int? = null,
    /** The steps chip's (i) is open: why steps don't add to Burned. */
    val stepsExplained: Boolean = false,
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

    /** `release.exercise` is off (or went off): no card. */
    data object Unreleased : TodayActivityAction

    /** The + with no health app: the usual way to log exercise. */
    data object AddTapped : TodayActivityAction

    /** "Add exercise your watch missed", with a health app counting activity. */
    data object AddMissedTapped : TodayActivityAction
    data class EntryTapped(val id: String) : TodayActivityAction
    data object RestingEnergyTapped : TodayActivityAction

    /** The button under "Burned unavailable": where the missing detail is entered. */
    data object BurnedFixTapped : TodayActivityAction

    /** The (i) beside Total burned (TDEE): what it is and how today's adds up. */
    data object BurnedInfoTapped : TodayActivityAction

    /** The (i) beside the steps chip: steps don't add to Burned. Opens and closes a line on the card. */
    data object StepsInfoTapped : TodayActivityAction
}

sealed interface TodayActivityEvent {
    /** [notOnWatch] starts a new entry with "My watch didn't record this" already on. */
    data class OpenExercise(val day: LocalDate, val entryId: String? = null, val notOnWatch: Boolean = false) : TodayActivityEvent
    data object OpenRestingEnergy : TodayActivityEvent
    data class OpenBurnedFix(val fix: BurnedFix) : TodayActivityEvent
    data object OpenBurnedExplainer : TodayActivityEvent
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
    /** With [burned] null, what resting energy is waiting on. */
    val gap: BurnedGap? = null,
    /** The day's steps, read only with `release.health` on and "Steps on Today" on. */
    val steps: Int? = null,
)

object TodayActivityStateBuilder {
    fun build(inputs: TodayActivityInputs, stepsExplained: Boolean = false): TodayActivityState {
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
                steps = inputs.steps?.takeIf { inputs.settings.todaySteps && it > 0 },
                stepsExplained = stepsExplained,
                gap = if (burned == null) inputs.gap ?: BurnedGap() else null,
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
 * workouts only with `release.health` on too. Both flags are read again each minute ([ticker]),
 * so one the config turns on after Today was built shows the card without a restart.
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
    ticker: MinuteTicker,
    steps: StepsRepository = StepsRepository.None,
) : SEAViewModel<TodayActivityState, TodayActivityEvent, TodayActivityAction>(initialStateArg = TodayActivityState()) {

    private var day: LocalDate = selectedDay.date.value

    /** The last inputs and whether the steps (i) is open, so a tap on it rebuilds without new data. */
    private var latest: TodayActivityInputs? = null
    private var stepsExplained = false

    init {
        val flags = ticker.ticks().map { release.isOn() to healthFlag() }.distinctUntilChanged()
        viewModelScope.launch {
            combine(selectedDay.date, flags) { date, flags -> date to flags }.distinctUntilChanged().flatMapLatest { (date, flags) ->
                val (released, healthOn) = flags
                if (!released) return@flatMapLatest flowOf(null)
                val workouts = if (healthOn) records.workouts(date, calendar.timeZone()).map { it.orEmpty() } else flowOf(emptyList())
                // "Steps on Today" off leaves the store unasked for steps.
                val daySteps = if (healthOn) {
                    settingsRepository.settings.map { it.todaySteps }.distinctUntilChanged().flatMapLatest { on ->
                        if (on) steps.steps(date, calendar.timeZone()) else flowOf(null)
                    }
                } else {
                    flowOf(null)
                }
                // When Burned is null, the Resting energy page's answer for what it is waiting on comes with it.
                val burned = burnedRepository.burned(date).flatMapLatest { known ->
                    if (known != null) flowOf(known to null) else burnedRepository.restingChoices().map { null to BurnedGap(it.unusable) }
                }
                combine(
                    combine(burned, workouts, daySteps, ::Triple),
                    exercise.observeDay(date),
                    settingsRepository.settings,
                    policySource.policy,
                    diary.observeDay(date).map { it.closedByUser }.distinctUntilChanged(),
                ) { (out, fromHealth, stepCount), entries, settings, policy, closed ->
                    TodayActivityInputs(
                        day = date,
                        settings = settings,
                        burned = out.first,
                        gap = out.second,
                        entries = entries,
                        policy = policy,
                        released = true,
                        timeZone = calendar.timeZone(),
                        closed = closed,
                        // Only ever named when Burned says the store's activity is what counts.
                        healthSource = health.source,
                        workouts = fromHealth,
                        steps = stepCount,
                    )
                }
            }.collect { inputs ->
                if (inputs == null) takeAction(TodayActivityAction.Unreleased) else takeAction(TodayActivityAction.InputsArrived(inputs))
            }
        }
    }

    override suspend fun handleAction(action: TodayActivityAction) {
        when (action) {
            is TodayActivityAction.InputsArrived -> {
                day = action.inputs.day
                latest = action.inputs
                action.updateState { TodayActivityStateBuilder.build(action.inputs, stepsExplained) }
            }
            TodayActivityAction.StepsInfoTapped -> {
                stepsExplained = !stepsExplained
                latest?.let { inputs -> action.updateState { TodayActivityStateBuilder.build(inputs, stepsExplained) } }
            }
            TodayActivityAction.AddTapped -> sendEvent(TodayActivityEvent.OpenExercise(day))
            TodayActivityAction.AddMissedTapped -> sendEvent(TodayActivityEvent.OpenExercise(day, notOnWatch = true))
            is TodayActivityAction.EntryTapped -> sendEvent(TodayActivityEvent.OpenExercise(day, action.id))
            TodayActivityAction.RestingEnergyTapped -> sendEvent(TodayActivityEvent.OpenRestingEnergy)
            TodayActivityAction.BurnedFixTapped -> state.card?.gap?.let { sendEvent(TodayActivityEvent.OpenBurnedFix(it.fix)) }
            TodayActivityAction.Unreleased -> {
                latest = null
                action.updateState { TodayActivityState() }
            }
            TodayActivityAction.BurnedInfoTapped -> sendEvent(TodayActivityEvent.OpenBurnedExplainer)
        }
    }
}
