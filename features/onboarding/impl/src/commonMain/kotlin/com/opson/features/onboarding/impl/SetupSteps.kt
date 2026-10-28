package com.opson.features.onboarding.impl

import com.opson.libraries.diary.MealTimes
import com.opson.libraries.nutrition.BodyProfile
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.HeightInput
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.RegionUnits
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
 * is while no dietitian has set one ([Targets.floorKcal]), or at an ordinary day's burn above it
 * ([startingFrom]). The steps raise and lower it through [Targets.raise] and [Targets.lower], so
 * onboarding can never start anyone below the floor. Macros follow the energy, as ratio mode
 * (the default) does in Targets.
 */
data class GoalAnswer(
    val style: GoalChoice = GoalChoice.Range,
    val targets: Targets = Targets.Default,
    /** The person moved the amount themselves, so a new estimate no longer moves it for them. */
    val edited: Boolean = false,
    /** The burn [targets] was started from, when it was. Null at the app's starting range. */
    val basedOnKcal: Int? = null,
) {
    fun stepped(up: Boolean): GoalAnswer = when {
        style.needsBurn -> this
        up -> copy(targets = targets.raise(scaleMacros = true), edited = true)
        else -> targets.lower(scaleMacros = true)?.let { copy(targets = it, edited = true) } ?: this
    }

    /**
     * The starting goal from an ordinary day's burn, [burnKcal]: the range around it, to the
     * nearest step. Never under the floor: [Targets.withEnergy] holds it there, so a low estimate
     * (or a typo) leaves the app's starting range. Once the person has moved the amount, or with
     * no burn to start from, the goal is left as it is.
     */
    fun startingFrom(burnKcal: Int?): GoalAnswer {
        if (edited) return this
        val aim = burnKcal?.let { (it.toDouble() / Targets.STEP_KCAL).roundToInt() * Targets.STEP_KCAL }
        val start = aim?.let { Targets.Default.withEnergy(it, scaleMacros = true) } ?: Targets.Default
        return copy(targets = start, basedOnKcal = burnKcal?.takeIf { start.energyKcal == aim })
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
 * year is not lost. Height is in centimetres or feet and inches, weight in kilograms or pounds,
 * each with its own switch and both starting in the phone region's units. Weight is the first
 * weigh-in rather than a setting, as everywhere else in the app.
 *
 * [height] and [weight] are what the fields show. After a switch they are a rounded conversion,
 * and [heightAsTyped] and [weightAsTyped] keep what the person typed in the unit they typed it in,
 * so switching back gives it back unchanged and what is saved is what they typed.
 */
data class BodyAnswer(
    val birthYear: String = "",
    val height: HeightInput = HeightInput(HeightUnit.Cm),
    val weight: String = "",
    val sex: BodySex? = null,
    val weightUnit: WeightUnit = WeightUnit.Kg,
    val activity: EverydayActivity = EverydayActivity.Default,
    val heightAsTyped: HeightInput? = null,
    val weightAsTyped: WeightAsTyped? = null,
) {
    /** A weight's text in the unit it was typed in. */
    data class WeightAsTyped(val text: String, val unit: WeightUnit)

    fun withBirthYear(text: String) = copy(birthYear = NumberInput.sanitize(text, allowDecimal = false).take(YEAR_DIGITS))

    // Typing into a field that shows a conversion makes what it now holds the typed height.
    fun withHeightCm(text: String) = copy(height = height.copy(cm = NumberInput.sanitize(text, allowDecimal = true)), heightAsTyped = null)

    fun withHeightFeet(text: String) = copy(height = height.copy(feet = NumberInput.sanitize(text, allowDecimal = false)), heightAsTyped = null)

    fun withHeightInches(text: String) = copy(height = height.copy(inches = NumberInput.sanitize(text, allowDecimal = true)), heightAsTyped = null)

    /**
     * Height in [unit], carrying over a height already typed. Each conversion starts from what was
     * typed, never from an earlier conversion, so switching back to its unit restores it exactly:
     * 172 cm shows as 5 ft 7.5 in and comes back as 172, not 171.
     */
    fun withHeightUnit(unit: HeightUnit): BodyAnswer {
        if (unit == height.unit) return this
        val typed = typedHeight()
        return copy(height = typed.inUnit(unit), heightAsTyped = typed.takeIf { it.unit != unit && !it.isBlank })
    }

    fun withWeight(text: String) = copy(weight = NumberInput.sanitize(text, allowDecimal = true), weightAsTyped = null)

    /**
     * Weight in [unit]. A weight already typed is converted, to a tenth, so switching never loses
     * it or relabels 70 kg as 70 lb. As with height, each conversion starts from what was typed and
     * switching back restores it: 160 lb shows as 72.6 kg and comes back as 160, not 160.1.
     */
    fun withWeightUnit(unit: WeightUnit): BodyAnswer {
        if (unit == weightUnit) return this
        val typed = typedWeight()
        val shown = if (typed.unit == unit) {
            typed.text
        } else {
            typed.text.toDoubleOrNull()?.let { tenths(if (unit == WeightUnit.Lb) Units.kgToLb(it) else Units.lbToKg(it)) } ?: typed.text
        }
        return copy(weightUnit = unit, weight = shown, weightAsTyped = typed.takeIf { it.unit != unit && it.text.isNotEmpty() })
    }

    private fun typedHeight(): HeightInput = heightAsTyped ?: height

    private fun typedWeight(): WeightAsTyped = weightAsTyped ?: WeightAsTyped(weight, weightUnit)

    /** The year, when [currentYear] makes it someone 13 to 120. */
    fun birthYearOrNull(currentYear: Int): Int? =
        birthYear.toIntOrNull()?.takeIf { it in (currentYear - MAX_AGE)..(currentYear - MIN_AGE) }

    fun heightCmOrNull(): Double? = typedHeight().cmOrNull()

    fun weightKgOrNull(): Double? = typedWeight().let { typed ->
        typed.text.toDoubleOrNull()
            ?.let { if (typed.unit == WeightUnit.Lb) Units.lbToKg(it) else it }
            ?.takeIf { it in WEIGHT_KG }
    }

    /** Something is typed in the year field, but not a year that makes someone 13 to 120. */
    fun birthYearInvalid(currentYear: Int): Boolean = birthYear.isNotEmpty() && birthYearOrNull(currentYear) == null

    /** Something is typed for height that isn't a height: centimetres typed as feet, or 13 inches. */
    val heightInvalid: Boolean get() = !height.isBlank && heightCmOrNull() == null

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

    companion object {
        private const val YEAR_DIGITS = 4
        private const val MIN_AGE = 13
        private const val MAX_AGE = 120

        /** Weights the equation is given, 25 to 400 kg: about 55 to 882 lb. */
        private val WEIGHT_KG = 25.0..400.0

        /** Empty fields in the phone region's units ([RegionUnits]). */
        fun inRegion(countryCode: String?): BodyAnswer =
            BodyAnswer(height = HeightInput(RegionUnits.height(countryCode)), weightUnit = RegionUnits.weight(countryCode))

        /** 141.1 as "141.1", 64.0 as "64". */
        private fun tenths(value: Double): String {
            val tenths = (value * 10).roundToInt()
            return if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
        }
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
    /**
     * The ordinary day's burn the amount started from, energy out, for "Based on about 2,350
     * burned on an ordinary day". Hidden at the app's starting range, for a deficit or surplus
     * (which say they follow the burn), and where energy out is not shown.
     */
    val basedOn: Display = Display.Hidden,
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
    val heightUnit: HeightUnit? = null,
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
        val basedOn = answer.basedOnKcal
            ?.takeIf { amount is Display.Shown }
            ?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.Settings, FormatContext()) }
            ?: Display.Hidden
        return GoalView(
            styles = styles.filter { !it.needsBurn || TargetStyle.deficitAllowed(policy.preset.id) },
            style = answer.style,
            amount = amount,
            canLower = targets.canLower,
            basedOn = basedOn,
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
