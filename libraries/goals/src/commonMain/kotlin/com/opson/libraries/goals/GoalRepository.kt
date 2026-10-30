@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.goals

import com.opson.libraries.nutrition.BaselineInputs
import com.opson.libraries.nutrition.GoalDay
import com.opson.libraries.nutrition.Recalculation
import com.opson.libraries.nutrition.WeeklyBudget
import com.opson.libraries.nutrition.WeightProjection
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Goals as every screen reads them (docs/plans/goals-v2.md, "The engine API"). Settings hold the
 * goals; this puts them together with the log, Burned, weigh-ins and the health app. The math is
 * pure and lives in `:libraries:nutrition` ([GoalStatuses] puts it together), so a screen that
 * already has the inputs can call that instead.
 *
 * Nothing here formats a number or checks a mode: every surface resolves amounts through its
 * policy, and offers only [com.opson.libraries.nutrition.GoalCatalog.offered] types.
 */
@HiddenFromObjC
interface GoalRepository {
    /** A typical day's inputs right now, from settings, the latest weigh-in and the health app. Null while resting energy can't be worked out. */
    fun inputs(): Flow<BaselineInputs?>

    /** The recalculation to offer for the goal setting the target, or null. */
    fun recalculation(): Flow<Recalculation?>

    /** Use the new target. */
    suspend fun accept(recalculation: Recalculation)

    /** Keep the current target; these inputs aren't offered again. */
    suspend fun keep(recalculation: Recalculation)

    /** Today for every active goal, the one setting the target first. */
    fun today(): Flow<List<GoalToday>>

    /**
     * Each active goal's day from [from] to [to], by goal id: the Trends calendar, the recap and
     * goal-met days.
     */
    fun days(from: LocalDate, to: LocalDate): Flow<Map<String, List<GoalDay>>>

    /** This week for the goal setting the target, or null without one. */
    fun week(): Flow<WeeklyBudget?>

    /** The weight goal's projection from the latest weigh-in, or null without a weight goal that can draw one. */
    fun projection(): Flow<WeightProjection?>
}
