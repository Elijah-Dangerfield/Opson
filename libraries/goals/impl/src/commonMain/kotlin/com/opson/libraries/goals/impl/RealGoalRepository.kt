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
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import me.tatarka.inject.annotations.Inject
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * [GoalRepository] from settings, the log, each day's Burned, the latest weigh-in and the health
 * app's readings. Holds nothing: every value is read while a flow is collected, and the answers
 * come from [GoalStatuses], so a preview built from the same inputs shows the same thing.
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
) : GoalRepository {

    override fun inputs(): Flow<BaselineInputs?> =
        combine(settingsRepository.settings, latestWeightKg(), burned.healthReadings()) { settings, kg, health ->
            settings.baselineInputs(kg, calendar.today(), health)
        }.distinctUntilChanged()

    override fun recalculation(): Flow<Recalculation?> =
        combine(settingsRepository.settings, inputs()) { settings, inputs ->
            val goal = GoalBook.driving(settings) ?: return@combine null
            inputs?.let { GoalBaselines.recalculation(goal, it) }
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
        combine(diary.observeDays(from, to), burned.burnedBetween(from, to)) { days, burnedByDay ->
            days.associate { it.date to GoalStatuses.facts(it, burnedByDay[it.date]) }
        }

    private fun latestWeightKg(): Flow<Double?> =
        weights.observeWeighIns().map { weighIns -> weighIns.maxByOrNull { it.measuredAt }?.kg }.distinctUntilChanged()
}
