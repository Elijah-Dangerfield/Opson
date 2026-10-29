package com.opson.features.settings.impl.energy

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.UsualMealSize
import com.opson.libraries.diary.eatenUncertainty
import com.opson.libraries.health.HealthSource
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.DayGoal
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.macroEnergy
import com.opson.libraries.nutrition.resolveRange
import kotlinx.datetime.LocalDate

/**
 * Energy today (design sync 4, 13.2c). Eaten is calories and Burned is energy out, each at its
 * own level on [Surface.Today]; the deficit so far needs both on show, and "To your range" needs
 * Remaining. A section whose number is hidden is left out, label and all.
 */
data class EnergyTodayState(
    val loaded: Boolean = false,
    val eaten: EatenSection? = null,
    /** Null when energy out is not on show. */
    val burned: BurnedSection? = null,
    /** "Deficit so far 935 kcal", or "Surplus so far" once eaten passes Burned. */
    val balance: Balance? = null,
    /** "To your range 655 kcal", or "To your goal" for one number. */
    val toGoal: ToGoal? = null,
)

/**
 * [amount] is "1,245" with its fill fraction; [plusMinus] "100" ("± 100") and [likely]
 * "1,145 to 1,345", both Hidden on a day with nothing logged. [parts] split the fill by macro
 * energy, the macros on show only. [rangeStart]..[rangeEnd] is the day's goal on the same scale.
 */
data class EatenSection(
    val amount: Display,
    val plusMinus: Display,
    val likely: Display,
    val estimates: Int,
    val foods: Int,
    val parts: List<EatenPart>,
    val rangeStart: Float,
    val rangeEnd: Float,
)

data class EatenPart(val metric: Metric, val weight: Float)

/**
 * Burned and where each part comes from. [known] is false while no resting energy source can
 * answer, and then only the prompt to add details shows. [restingShare] and [activeShare] place
 * the two parts on the bar.
 */
data class BurnedSection(
    val known: Boolean,
    val total: Display = Display.Hidden,
    val restingShare: Float = 0f,
    val activeShare: Float = 0f,
    val resting: Display = Display.Hidden,
    val restingFrom: RestingFrom? = null,
    /** The health app activity came from, null without one. */
    val activitySource: HealthSource? = null,
    val movement: Display = Display.Hidden,
    val workouts: Display = Display.Hidden,
    /** Everyday activity, and logged exercise (`release.exercise`); Hidden when the day does not count them. */
    val everyday: Display = Display.Hidden,
    val exercise: Display = Display.Hidden,
    /** Digesting the day's food, with "Count digesting food" on; Hidden with it off. */
    val food: Display = Display.Hidden,
    /** "Add activity to Burned" is on. */
    val activityCounted: Boolean = true,
)

data class Balance(val surplus: Boolean, val amount: Display)

data class ToGoal(val single: Boolean, val metric: Metric, val amount: Display)

data class EnergyTodayInputs(
    val today: LocalDate,
    val day: Day,
    val settings: Settings,
    val burned: Burned?,
    /** The last four weeks, for how big a meal eaten with no details usually is. */
    val history: List<Day> = emptyList(),
    val activitySource: HealthSource? = null,
)

object EnergyTodayStateBuilder {
    /** Both bars run to the larger of the goal and Burned plus a fifth, so they compare at a glance. */
    private const val HEADROOM = 1.2

    fun build(inputs: EnergyTodayInputs, policy: VisibilityPolicy): EnergyTodayState {
        val day = inputs.day
        val settings = inputs.settings
        val context = day.formatContext(inputs.today)
        val totals = day.totals()
        val eaten = totals.kcal.known
        val burned = inputs.burned
        val dayGoal = settings.targets.rangeFor(day.date, settings.numbers.preset, burned?.totalKcal)
        // A goal worked out from Burned already counts the day's activity, as on Today.
        val goal = settings.targets.today(
            adjustment = if (burned != null && dayGoal is DayGoal.About) ExerciseAdjustment.None else settings.exerciseInRange,
            movementKcal = burned?.movementKcal ?: 0,
            workoutKcal = burned?.workoutKcal ?: 0,
            goal = dayGoal,
        )
        val scale = maxOf(goal.maxKcal, burned?.totalKcal ?: 0) * HEADROOM
        val caloriesShown = policy.effectiveLevel(Metric.Calories, Surface.Today, context) == Visibility.Show
        val energyOutShown = policy.effectiveLevel(Metric.EnergyOut, Surface.Today, context) == Visibility.Show
        return EnergyTodayState(
            loaded = true,
            eaten = if (caloriesShown) eatenSection(inputs, policy, context, eaten, scale, goal.minKcal, goal.maxKcal) else null,
            burned = if (energyOutShown) burnedSection(inputs, policy, context, scale) else null,
            balance = if (caloriesShown && energyOutShown && burned != null) {
                val difference = burned.totalKcal - eaten
                Balance(
                    surplus = difference < 0,
                    amount = policy.resolve(Metric.EnergyOut, kotlin.math.abs(difference), Surface.Today, context.copy(bare = true)),
                )
            } else {
                null
            },
            toGoal = toGoal(policy, context, eaten, goal.minKcal, goal.maxKcal, goal.single),
        )
    }

    private fun eatenSection(
        inputs: EnergyTodayInputs,
        policy: VisibilityPolicy,
        context: FormatContext,
        eaten: Double,
        scale: Double,
        min: Int,
        max: Int,
    ): EatenSection {
        val day = inputs.day
        val usual = UsualMealSize.from(inputs.history, inputs.settings.targets.energyKcal)
        val uncertainty = day.eatenUncertainty(usual)
        val totals = day.totals()
        val bare = context.copy(bare = true)
        val spread = uncertainty.plusMinusKcal.toDouble()
        val logged = day.hasLogged && spread > 0
        return EatenSection(
            amount = policy.resolve(Metric.Calories, eaten, Surface.Today, bare.copy(of = scale)),
            plusMinus = if (logged) policy.resolve(Metric.Calories, spread, Surface.Today, bare) else Display.Hidden,
            likely = if (logged) {
                policy.resolveRange(Metric.Calories, (eaten - spread).coerceAtLeast(0.0), eaten + spread, Surface.Today, bare)
            } else {
                Display.Hidden
            },
            estimates = uncertainty.estimates,
            foods = uncertainty.foods,
            parts = macroEnergy(totals.protein.known, totals.carbs.known, totals.fat.known)
                .filter { (metric, kcal) -> kcal > 0 && policy.effectiveLevel(metric, Surface.Today, context) == Visibility.Show }
                .map { (metric, kcal) -> EatenPart(metric, kcal.toFloat()) },
            rangeStart = (min / scale).toFloat(),
            rangeEnd = (max / scale).toFloat(),
        )
    }

    private fun burnedSection(inputs: EnergyTodayInputs, policy: VisibilityPolicy, context: FormatContext, scale: Double): BurnedSection {
        val burned = inputs.burned ?: return BurnedSection(known = false)
        val bare = context.copy(bare = true)
        fun energy(kcal: Int?) = kcal?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.Today, bare) } ?: Display.Hidden
        return BurnedSection(
            known = true,
            total = energy(burned.totalKcal),
            restingShare = (burned.resting.kcal / scale).toFloat().coerceIn(0f, 1f),
            // Digesting food rides on the active part, so the bar still ends at the total.
            activeShare = (burned.aboveRestingKcal / scale).toFloat().coerceIn(0f, 1f),
            resting = energy(burned.resting.kcal),
            restingFrom = burned.resting.from,
            activitySource = inputs.activitySource,
            movement = energy(burned.movementKcal),
            workouts = energy(burned.workoutKcal),
            everyday = energy(burned.everydayKcal),
            exercise = energy(burned.exerciseKcal?.takeIf { it > 0 }),
            food = energy(burned.foodKcal),
            activityCounted = burned.activityCounted,
        )
    }

    /** As Today's "To your range": the gap to the low end, or where eaten sits once it is reached. */
    private fun toGoal(policy: VisibilityPolicy, context: FormatContext, eaten: Double, min: Int, max: Int, single: Boolean): ToGoal? {
        if (policy.effectiveLevel(Metric.Remaining, Surface.Today, context) != Visibility.Show) return null
        val gap = min - eaten
        val bare = context.copy(bare = true)
        val toGoal = if (gap >= 1.0) {
            ToGoal(single, Metric.Calories, policy.resolve(Metric.Calories, gap, Surface.Today, bare))
        } else {
            val bounds = bare.copy(eaten = eaten, min = min.toDouble(), max = max.toDouble())
            ToGoal(single, Metric.Remaining, policy.resolve(Metric.Remaining, gap, Surface.Today, bounds))
        }
        return toGoal.takeUnless { it.amount is Display.Hidden }
    }
}
