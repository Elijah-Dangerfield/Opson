package com.opson.libraries.goals

import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.ActivityFrom
import com.opson.libraries.nutrition.BaselineInputs
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.BurnMeasure
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalBaselines
import com.opson.libraries.nutrition.GoalDay
import com.opson.libraries.nutrition.GoalDayFacts
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.Recalculation
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.WeightProjection
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Goals in every state a screen draws, for previews and view-model tests (the goal UI, onboarding's
 * goal question). Built through the real engine, so a preview shows what the app would.
 *
 * The person: 30, 180 cm, a man, latest weigh-in 88 kg, resting 1,800 from the equation and some
 * walking. Losing 0.5 kg a week toward 80 kg since Monday 26 October 2026; today is Thursday.
 */
object GoalFixtures {

    val monday: LocalDate = LocalDate(2026, 10, 26)
    val today: LocalDate = monday.plus(DatePeriod(days = 3))
    const val WEIGHT_KG: Double = 88.0

    val inputs: BaselineInputs = BaselineInputs(
        restingFrom = RestingFrom.Calculated,
        restingKcal = 1800,
        activityFrom = ActivityFrom.Everyday,
        everyday = EverydayActivity.SomeWalking,
        activityKcal = 680,
        digesting = false,
        weightKg = WEIGHT_KG,
        heightCm = 180.0,
        ageYears = 30,
        sex = BodySex.Male,
    )

    /** Losing 0.5 kg a week, judged day by day: a 1,930 target, 1,830 to 2,030. */
    val lose: Goal = Goal(
        id = Goal.WEIGHT_ID,
        type = GoalType.Weight(GoalDirection.Lose, targetKg = 80.0, kgPerWeek = 0.5, startKg = 90.0),
        startDate = monday,
        baseline = GoalBaselines.of(inputs),
    )

    /** The same goal averaged over the week. */
    val loseWeekly: Goal = lose.copy(averaging = GoalAveraging.Weekly)

    /** A pace past a lean body's fat-loss limit: 1 kg a week. */
    val loseFast: Goal = lose.copy(type = GoalType.Weight(GoalDirection.Lose, targetKg = 80.0, kgPerWeek = 1.0, startKg = 90.0))

    val gain: Goal = lose.copy(type = GoalType.Weight(GoalDirection.Gain, targetKg = 95.0, kgPerWeek = 0.25, startKg = 88.0))

    val balance: Goal = Goal("balance", GoalType.EnergyBalance(-500), monday, baseline = GoalBaselines.of(inputs))

    val burn: Goal = Goal("burn", GoalType.Burn(500, BurnMeasure.Active), monday)

    val steps: Goal = Goal("steps", GoalType.Steps(8000), monday)

    val trackEveryDay: Goal = Goal("track", GoalType.TrackEveryDay, monday)

    val meals: Goal = Goal("meals", GoalType.Meals(), monday)

    /** Settings with [goal] setting the target, plus [also] beside it. */
    fun settings(goal: Goal = lose, vararg also: Goal, base: Settings = Settings(heightCm = 180.0, birthYear = 1996, bodySex = BodySex.Male)): Settings {
        val applied = GoalBook.applyTarget(GoalBook.withGoals(base, listOf(goal)), goal)
        return also.fold(applied) { settings, other -> GoalBook.withGoal(settings, other) }
    }

    /** Only the gentle goals, as after "numbers have been hard". */
    fun gentleSettings(): Settings = GoalBook.withGoals(Settings(), listOf(trackEveryDay, meals))

    /** Monday well under, Tuesday a big day, Wednesday skipped; today two meals in and still going. */
    val week: Map<LocalDate, GoalDayFacts> = listOf(
        GoalDayFacts(monday, logged = true, eatenKcal = 1500.0, burnedKcal = 2480, activeKcal = 680, mealsWithFood = 3, showedUp = true),
        GoalDayFacts(monday.plus(DatePeriod(days = 1)), logged = true, eatenKcal = 2900.0, burnedKcal = 2600, activeKcal = 800, mealsWithFood = 4, showedUp = true),
        GoalDayFacts(monday.plus(DatePeriod(days = 2)), untracked = true),
        GoalDayFacts(today, logged = true, eatenKcal = 900.0, burnedKcal = 2100, activeKcal = 300, mealsWithFood = 2, showedUp = true),
    ).associateBy { it.date }

    fun today(goal: Goal = lose, vararg also: Goal): List<GoalToday> = GoalStatuses.today(settings(goal, *also), week, today, WEIGHT_KG)

    fun days(goal: Goal = lose): List<GoalDay> =
        GoalStatuses.days(settings(goal), week, monday, monday.plus(DatePeriod(days = 6)), today, WEIGHT_KG).getValue(goal.id)

    /** Today wrapped up for [goal] and [also], with goal streaks on when [streaks]. [eatenKcal] replaces today's 900 when given. */
    fun wrapUp(goal: Goal = lose, vararg also: Goal, streaks: Boolean = false, eatenKcal: Double? = null): List<GoalWrapUp> {
        val days = if (eatenKcal == null) week else week + (today to week.getValue(today).copy(eatenKcal = eatenKcal))
        return GoalStatuses.wrapUp(settings(goal, *also).copy(goalStreakEnabled = streaks), days, today, today, WEIGHT_KG)
    }

    fun projection(goal: Goal = lose): WeightProjection? = GoalStatuses.projection(settings(goal), WEIGHT_KG, today)

    /** Connecting Apple Health: resting from the watch and a week of its activity. 1,930 becomes 2,150. */
    val connectedHealth: Recalculation = GoalBaselines.recalculation(
        lose,
        inputs.copy(restingFrom = RestingFrom.Health, restingKcal = 1900, activityFrom = ActivityFrom.Health, activityKcal = 800),
    )!!

    /** Nine kilograms lighter: the target moves down past the drift threshold. */
    val lighter: Recalculation = GoalBaselines.recalculation(lose, inputs.copy(weightKg = 79.0, restingKcal = 1710, activityKcal = 640))!!
}
