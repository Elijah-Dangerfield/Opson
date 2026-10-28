package com.opson.features.onboarding.impl

import com.opson.libraries.diary.MealTimes
import com.opson.libraries.nutrition.BodyProfile
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.RestingEnergies
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Units
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.resolveRange
import com.opson.libraries.ui.components.text.NumberInput
import kotlin.math.roundToInt

/**
 * The goal styles the goal step offers. Different by day is left to Targets: it means building
 * day groups, which is not a first-day job, and until it is built it is the same as a range.
 */
enum class GoalChoice {
    Range,
    OneNumber,
    Deficit,
    Surplus,
    ;

    /** A set deficit or surplus is aimed at what the person burns, so it needs resting energy. */
    val needsBurn: Boolean get() = this == Deficit || this == Surplus

    companion object {
        /**
         * What the step offers in Full numbers. Without `release.goalsAndEnergy` only the range:
         * nothing reads Burned, so a deficit would quietly fall back to the range, and one number
         * and a surplus are not released.
         */
        fun offered(goalsAndEnergy: Boolean): List<GoalChoice> = if (goalsAndEnergy) entries else listOf(Range)
    }
}

/**
 * The goal as the person leaves the step. [targets] starts at [Targets.Default], where the floor
 * is while no dietitian has set one ([Targets.floorKcal]): the steps raise and lower it through
 * [Targets.raise] and [Targets.lower], so onboarding can never start anyone below it.
 */
data class GoalAnswer(
    val style: GoalChoice = GoalChoice.Range,
    val targets: Targets = Targets.Default,
) {
    fun stepped(up: Boolean): GoalAnswer = when {
        style.needsBurn -> this
        up -> copy(targets = targets.raise())
        else -> targets.lower()?.let { copy(targets = it) } ?: this
    }

    /**
     * The targets saved: the style applied to [targets]. A deficit or surplus starts from what
     * the aim already implies against [burnKcal], as Targets does, so it never asks for more
     * than the person already had while the dietitian's caps are unset.
     */
    fun resolve(burnKcal: Int?): Targets = when (style) {
        GoalChoice.Range -> targets.useRange()
        GoalChoice.OneNumber -> targets.useExact()
        GoalChoice.Deficit -> targets.useDeficit(burnKcal)
        GoalChoice.Surplus -> targets.useSurplus(burnKcal)
    }
}

/**
 * Resting energy's details, as typed, and how active an ordinary day is. Text, so a half-typed
 * year is not lost. Weight is the first weigh-in rather than a setting, as everywhere else in the
 * app.
 *
 * [height] and [weight] are what the fields show. After a unit switch they are a rounded
 * conversion, and [heightAsTyped] and [weightAsTyped] keep what the person typed and its unit, so
 * switching back gives it back unchanged and what is saved is what they typed.
 */
data class BodyAnswer(
    val birthYear: String = "",
    val height: String = "",
    val weight: String = "",
    val sex: BodySex? = null,
    val unit: WeightUnit = WeightUnit.Kg,
    val activity: EverydayActivity = EverydayActivity.Default,
    val heightAsTyped: AsTyped? = null,
    val weightAsTyped: AsTyped? = null,
) {
    /** A field's text in the unit it was typed in. */
    data class AsTyped(val text: String, val unit: WeightUnit)

    fun withBirthYear(text: String) = copy(birthYear = NumberInput.sanitize(text, allowDecimal = false).take(YEAR_DIGITS))

    fun withHeight(text: String) = copy(height = NumberInput.sanitize(text, allowDecimal = true), heightAsTyped = null)

    fun withWeight(text: String) = copy(weight = NumberInput.sanitize(text, allowDecimal = true), weightAsTyped = null)

    /**
     * Switches to [next], converting what is typed so it still means the same height and weight:
     * 175 cm becomes 68.9 in, never 175 in. Each conversion starts from what was typed, and
     * switching back to its unit restores it exactly. A field that is not a number yet is left as
     * it is.
     */
    fun withUnit(next: WeightUnit): BodyAnswer {
        if (next == unit) return this
        val heightTyped = typedHeight()
        val weightTyped = typedWeight()
        return copy(
            unit = next,
            height = heightTyped.shownIn(next) { if (next == WeightUnit.Lb) it / CM_PER_INCH else it * CM_PER_INCH },
            weight = weightTyped.shownIn(next) { if (next == WeightUnit.Lb) Units.kgToLb(it) else Units.lbToKg(it) },
            heightAsTyped = heightTyped.takeIf { it.unit != next },
            weightAsTyped = weightTyped.takeIf { it.unit != next },
        )
    }

    private fun typedHeight() = heightAsTyped ?: AsTyped(height, unit)

    private fun typedWeight() = weightAsTyped ?: AsTyped(weight, unit)

    /** The year, when [currentYear] makes it someone 13 to 120. */
    fun birthYearOrNull(currentYear: Int): Int? =
        birthYear.toIntOrNull()?.takeIf { it in (currentYear - MAX_AGE)..(currentYear - MIN_AGE) }

    fun heightCmOrNull(): Double? = typedHeight().let { typed ->
        typed.text.toDoubleOrNull()
            ?.let { if (typed.unit == WeightUnit.Lb) it * CM_PER_INCH else it }
            ?.takeIf { it in HEIGHT_CM }
    }

    fun weightKgOrNull(): Double? = typedWeight().let { typed ->
        typed.text.toDoubleOrNull()
            ?.let { if (typed.unit == WeightUnit.Lb) Units.lbToKg(it) else it }
            ?.takeIf { it in WEIGHT_KG }
    }

    /** Something is typed in the year field, but not a year that makes someone 13 to 120. */
    fun birthYearInvalid(currentYear: Int): Boolean = birthYear.isNotEmpty() && birthYearOrNull(currentYear) == null

    /** Something is typed for height that isn't a height in [unit]: 5.10 for 5 ft 10 in, or centimetres as inches. */
    val heightInvalid: Boolean get() = height.isNotEmpty() && heightCmOrNull() == null

    val weightInvalid: Boolean get() = weight.isNotEmpty() && weightKgOrNull() == null

    fun profile(currentYear: Int): BodyProfile = BodyProfile(
        weightKg = weightKgOrNull(),
        heightCm = heightCmOrNull(),
        ageYears = birthYearOrNull(currentYear)?.let { currentYear - it },
        sex = sex,
    )

    /** Resting energy from what is typed so far; null until every detail is in. */
    fun restingKcal(currentYear: Int): Int? = RestingEnergies.calculate(profile(currentYear))

    /**
     * An ordinary day's burn: resting energy plus the [activity] level's everyday movement, as
     * Burned works it out with no health app. What a deficit or surplus starts from.
     */
    fun burnKcal(currentYear: Int): Int? = restingKcal(currentYear)?.let { it + activity.kcalFor(it) }

    private companion object {
        /** The text as it shows in [unit]: as typed in its own unit, else converted and rounded. */
        fun AsTyped.shownIn(unit: WeightUnit, convert: (Double) -> Double): String =
            if (unit == this.unit) text else text.toDoubleOrNull()?.let { typed(convert(it)) } ?: text

        /** A number as it would be typed, to one decimal: "68.9", "70". */
        fun typed(value: Double): String {
            val tenths = (value * TENTHS).roundToInt()
            return if (tenths % TENTHS == 0) "${tenths / TENTHS}" else "${tenths / TENTHS}.${tenths % TENTHS}"
        }

        const val TENTHS = 10
        const val YEAR_DIGITS = 4
        const val MIN_AGE = 13
        const val MAX_AGE = 120
        const val CM_PER_INCH = 2.54
        val HEIGHT_CM = 100.0..250.0
        val WEIGHT_KG = 25.0..400.0
    }
}

/** The goal step as drawn. Every amount went through the policy for the mode being started. */
data class GoalView(
    /** The styles to pick from; one or none means the list is not drawn. */
    val styles: List<GoalChoice>,
    val style: GoalChoice,
    /**
     * The stepper's amount: the range, "1,900 to 2,300 kcal", or the one number. Hidden for a
     * deficit or surplus, and where calories are not shown.
     */
    val amount: Display,
    val canLower: Boolean,
)

/**
 * The resting energy step as drawn. [resting] and [burned] (an ordinary day, with the activity
 * level) are energy out, through the policy.
 */
data class BodyView(
    val answer: BodyAnswer,
    val resting: Display,
    val burned: Display = Display.Hidden,
    /** Whether the weight field shows: weight is a number like any other. */
    val weightShown: Boolean,
    /**
     * A field holding something that can't be used, which gets a line saying what it wants.
     * Without one, a height in the wrong unit just leaves resting energy blank with no reason given.
     */
    val birthYearInvalid: Boolean = false,
    val heightInvalid: Boolean = false,
    val weightInvalid: Boolean = false,
)

/** What onboarding writes past the mode and the answers, for the steps the person saw. */
data class StartingSetup(
    val targets: Targets? = null,
    val birthYear: Int? = null,
    val heightCm: Double? = null,
    val sex: BodySex? = null,
    val weightUnit: WeightUnit? = null,
    val mealTimes: MealTimes? = null,
    val everydayActivity: EverydayActivity? = null,
)

object SetupViews {

    fun goal(answer: GoalAnswer, styles: List<GoalChoice>, policy: VisibilityPolicy): GoalView {
        val targets = answer.targets
        val amount = when (answer.style) {
            GoalChoice.Range ->
                policy.resolveRange(Metric.Calories, targets.minKcal.toDouble(), targets.maxKcal.toDouble(), Surface.Settings)
            GoalChoice.OneNumber -> policy.resolve(Metric.Calories, targets.energyKcal.toDouble(), Surface.Settings)
            GoalChoice.Deficit, GoalChoice.Surplus -> Display.Hidden
        }
        return GoalView(
            styles = styles.filter { !it.needsBurn || TargetStyle.deficitAllowed(policy.preset.id) },
            style = answer.style,
            amount = amount,
            canLower = targets.canLower,
        )
    }

    fun body(answer: BodyAnswer, currentYear: Int, policy: VisibilityPolicy): BodyView = BodyView(
        answer = answer,
        resting = answer.restingKcal(currentYear)
            ?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.Settings, FormatContext()) }
            ?: Display.Hidden,
        burned = answer.burnKcal(currentYear)
            ?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.Settings, FormatContext()) }
            ?: Display.Hidden,
        weightShown = policy.effectiveLevel(Metric.Weight, Surface.Settings) == Visibility.Show,
        birthYearInvalid = answer.birthYearInvalid(currentYear),
        heightInvalid = answer.heightInvalid,
        weightInvalid = answer.weightInvalid,
    )
}
