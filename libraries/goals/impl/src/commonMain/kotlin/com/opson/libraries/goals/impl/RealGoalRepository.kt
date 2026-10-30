@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.goals.impl

import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.diary.baselineInputs
import com.opson.libraries.goals.GoalRepository
import com.opson.libraries.goals.GoalStatuses
import com.opson.libraries.goals.GoalStreaks
import com.opson.libraries.goals.GoalToday
import com.opson.libraries.goals.GoalWrapUp
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.StepsRepository
import com.opson.libraries.nutrition.BaselineInputs
import com.opson.libraries.nutrition.GoalBaselines
import com.opson.libraries.nutrition.GoalDay
import com.opson.libraries.nutrition.GoalDayFacts
import com.opson.libraries.nutrition.Recalculation
import com.opson.libraries.nutrition.WeeklyBudget
import com.opson.libraries.nutrition.WeeklyBudgets
import com.opson.libraries.nutrition.WeightProjection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Inject
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * [GoalRepository] from settings, the log, each day's Burned, the latest weigh-in and the health
 * app's readings. Holds nothing: every value is read while a flow is collected, and the answers
 * come from [GoalStatuses], so a preview built from the same inputs shows the same thing.
 *
 * Steps are read only while an active steps goal exists and `release.health` is on, so a goal
 * screen never touches the health store for a person without one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiddenFromObjC
@ContributesBinding(AppScope::class)
@Inject
class RealGoalRepository(
    private val settingsRepository: SettingsRepository,
    private val diary: DiaryRepository,
    private val burned: BurnedRepository,
    private val weights: WeightRepository,
    private val calendar: LocalCalendar,
    private val steps: StepsRepository,
    private val healthFlag: HealthReleaseFlag,
) : GoalRepository {

    override fun inputs(): Flow<BaselineInputs?> =
        combine(settingsRepository.settings, latestWeightKg(), burned.healthReadings()) { settings, kg, health ->
            settings.baselineInputs(kg, calendar.today(), health)
        }.distinctUntilChanged()

    /**
     * The recalculation the goal setting the target has to offer. A goal whose typical day came
     * from the health app gets none while the store can't be read: falling back to the equation
     * then would look like the person changed their source.
     */
    override fun recalculation(): Flow<Recalculation?> =
        combine(settingsRepository.settings, latestWeightKg(), burned.healthReadings()) { settings, kg, health ->
            val goal = GoalBook.driving(settings) ?: return@combine null
            if (health.unavailable && goal.baseline?.inputs?.readsHealth == true) return@combine null
            settings.baselineInputs(kg, calendar.today(), health)?.let { GoalBaselines.recalculation(goal, it) }
        }.distinctUntilChanged()

    override suspend fun accept(recalculation: Recalculation) {
        settingsRepository.update { GoalBook.accept(it, recalculation, calendar.today()) }
    }

    override suspend fun keep(recalculation: Recalculation) {
        settingsRepository.update { GoalBook.keep(it, recalculation, calendar.today()) }
    }

    override fun today(): Flow<List<GoalToday>> {
        val today = calendar.today()
        return combine(settingsRepository.settings, weekFacts(today, today), latestWeightKg()) { settings, facts, kg ->
            GoalStatuses.today(settings, facts, today, kg)
        }.distinctUntilChanged()
    }

    override fun days(from: LocalDate, to: LocalDate): Flow<Map<String, List<GoalDay>>> {
        val today = calendar.today()
        // A weekly goal replays each week from its start, so the span reaches back to the first one.
        return combine(settingsRepository.settings, weekFacts(from, to), latestWeightKg()) { settings, facts, kg ->
            GoalStatuses.days(settings, facts, from, to, today, kg)
        }.distinctUntilChanged()
    }

    override fun weekDays(): Flow<Map<String, List<GoalDay>>> {
        val today = calendar.today()
        return settingsRepository.settings.map { WeeklyBudgets.weekStartOf(today, it.weekStart) }.distinctUntilChanged()
            .flatMapLatest { start -> days(start, start.plus(DatePeriod(days = DAYS_A_WEEK - 1))) }
    }

    override fun week(): Flow<WeeklyBudget?> = today().map { goals -> goals.firstOrNull { it.goal.drivesTarget }?.week }.distinctUntilChanged()

    override fun projection(): Flow<WeightProjection?> =
        combine(settingsRepository.settings, latestWeightKg()) { settings, kg -> GoalStatuses.projection(settings, kg, calendar.today()) }
            .distinctUntilChanged()

    override fun wrapUp(date: LocalDate): Flow<List<GoalWrapUp>> {
        val today = calendar.today()
        // Back to the week's start for a weekly goal, and as far as a streak reads when one is on.
        val streaks = settingsRepository.settings.map { settings ->
            settings.goalStreakEnabled && GoalBook.goals(settings).any { it.isActive && GoalStreaks.eligible(it.type) }
        }.distinctUntilChanged()
        val facts = streaks.flatMapLatest { on ->
            weekFacts(if (on) date.minus(DatePeriod(days = GoalStreaks.WINDOW_DAYS - 1)) else date, date)
        }
        return combine(settingsRepository.settings, facts, latestWeightKg()) { settings, days, kg ->
            GoalStatuses.wrapUp(settings, days, date, today, kg)
        }.distinctUntilChanged()
    }

    /** From the start of [from]'s week (the person's week start) to [to], read again if the week start changes. */
    private fun weekFacts(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, GoalDayFacts>> =
        settingsRepository.settings.map { WeeklyBudgets.weekStartOf(from, it.weekStart) }.distinctUntilChanged()
            .flatMapLatest { start -> facts(start, to) }

    private fun facts(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, GoalDayFacts>> =
        combine(diary.observeDays(from, to), burned.burnedBetween(from, to), stepsBetween(from, to)) { days, burnedByDay, stepsByDay ->
            // A day the log has nothing for still has steps: a steps goal reads it like any other.
            val dates = days.map { it.date }.toSet()
            val logged = days.associate { it.date to GoalStatuses.facts(it, burnedByDay[it.date], stepsByDay?.get(it.date)) }
            val stepsOnly = stepsByDay.orEmpty().filterKeys { it !in dates }.mapValues { (date, count) -> GoalDayFacts(date, steps = count) }
            logged + stepsOnly
        }

    /** Each day's steps while an active steps goal exists; null otherwise, and the store untouched. */
    private fun stepsBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>?> {
        if (!healthFlag()) return flowOf(null)
        return settingsRepository.settings.map { GoalStatuses.stepsGoal(it) != null }.distinctUntilChanged().flatMapLatest { wanted ->
            if (wanted) steps.stepsBetween(from, to, calendar.timeZone()) else flowOf(null)
        }
    }

    private fun latestWeightKg(): Flow<Double?> =
        weights.observeWeighIns().map { weighIns -> weighIns.maxByOrNull { it.measuredAt }?.kg }.distinctUntilChanged()

    private companion object {
        const val DAYS_A_WEEK = 7
    }
}
