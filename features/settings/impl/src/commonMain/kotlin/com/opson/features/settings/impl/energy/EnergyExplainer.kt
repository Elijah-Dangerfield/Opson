package com.opson.features.settings.impl.energy

import com.opson.features.settings.EnergyExplainerRoute
import com.opson.libraries.diary.Settings
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.DayGoal
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.RestingEnergies
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.digestingShown
import com.opson.libraries.nutrition.shownOn
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/**
 * The (i) sheets beside Resting energy, Burned and a set deficit or surplus: what the word means
 * in plain language, how Opson works the number out, and the person's own working wherever their
 * mode shows the numbers it is made of. [wire] is [EnergyExplainerRoute.topic].
 */
enum class EnergyTopic(val wire: String) {
    /** Resting energy (BMR) and the Mifflin–St Jeor working. */
    Resting(EnergyExplainerRoute.TOPIC_RESTING),

    /** Total burned (TDEE) and the parts it adds up. */
    Burned(EnergyExplainerRoute.TOPIC_BURNED),

    /** A deficit or surplus, the rough 3,500 rule, and how today's goal follows from Burned. */
    Balance(EnergyExplainerRoute.TOPIC_BALANCE),
    ;

    companion object {
        /** An unknown topic opens Resting energy's, the one every other one leans on. */
        fun fromWire(wire: String): EnergyTopic = entries.firstOrNull { it.wire == wire } ?: Resting
    }
}

/**
 * One sheet. The words always show; every amount went through the policy, so a mode that hides
 * energy out or calories leaves the working out (null), label and all.
 */
data class EnergyExplainerState(
    val loaded: Boolean = false,
    val topic: EnergyTopic = EnergyTopic.Resting,
    /** Resting: the equation term by term; null while it can't be worked out or the mode hides it. */
    val working: RestingWorkingView? = null,
    /** Resting: the number Burned uses when it is not the calculation (a health app's, or the person's own). */
    val inUse: RestingInUse? = null,
    /** Burned: today's parts and their total; null while Burned is unknown or energy out is hidden. */
    val burned: BurnedWorking? = null,
    /** Burned: whether "Digesting food" counts in this mode, so its part is described at all. */
    val digesting: Boolean = false,
    /** Balance: today's burn, the set amount and the goal it comes to; null for any other goal style. */
    val balance: BalanceWorking? = null,
    /** Balance: the rough rule's numbers ("about 3,500 calories"), only where calories show. */
    val ruleNumbers: Boolean = false,
    /** Which unit the rough rule speaks in: a pound or a kilogram of fat. */
    val weightUnit: WeightUnit = WeightUnit.Kg,
)

/**
 * Mifflin–St Jeor for this person: 10 × [weight] + 6.25 × [heightCm] − 5 × [age] + the [sex]
 * constant. [terms] are the four amounts, bare; [total] is the rounded result. [weight] is
 * kilograms, because the equation is; the profile's own units are on the Resting energy page.
 */
data class RestingWorkingView(
    val weight: Display,
    val heightCm: Int,
    val age: Int,
    val sex: BodySex,
    val weightTerm: Display,
    val heightTerm: Display,
    val ageTerm: Display,
    /** The sex constant, without its sign: 5, 161, or 78 halfway. [sexAdds] says which way it goes. */
    val sexTerm: Display,
    val sexAdds: Boolean,
    val total: Display,
)

/** The resting energy in use when the person picked another source. [source] names the health app. */
data class RestingInUse(val from: RestingFrom, val source: HealthSource?, val amount: Display)

/**
 * Burned's parts as Today adds them. [everyday] is the estimate with no health app, [movement] and
 * [workouts] a health app's reading; a day has one side or the other. Hidden parts are left out.
 */
data class BurnedWorking(
    val resting: Display,
    val everyday: Display = Display.Hidden,
    val movement: Display = Display.Hidden,
    val workouts: Display = Display.Hidden,
    val exercise: Display = Display.Hidden,
    val food: Display = Display.Hidden,
    val total: Display,
)

/**
 * Today's goal from Burned: [burned] less the deficit (or plus the surplus) [amount] is the [goal].
 * [held] when the floor held the goal above that, so the sum doesn't seem to be wrong.
 */
data class BalanceWorking(val surplus: Boolean, val burned: Display, val amount: Display, val goal: Display, val held: Boolean)

/** What one sheet reads. [today] is the day Burned and the age are for. */
data class EnergyExplainerInputs(
    val settings: Settings,
    val choices: RestingChoices,
    val burned: Burned?,
    val today: LocalDate,
)

object EnergyExplainerStateBuilder {

    fun build(topic: EnergyTopic, inputs: EnergyExplainerInputs, policy: VisibilityPolicy): EnergyExplainerState {
        val settings = inputs.settings
        return when (topic) {
            EnergyTopic.Resting -> EnergyExplainerState(
                loaded = true,
                topic = topic,
                working = working(inputs, policy),
                inUse = inUse(inputs, policy),
            )
            EnergyTopic.Burned -> EnergyExplainerState(
                loaded = true,
                topic = topic,
                burned = burned(inputs, policy),
                digesting = digestingShown(policy, Surface.Today, FormatContext(day = inputs.today)),
            )
            EnergyTopic.Balance -> EnergyExplainerState(
                loaded = true,
                topic = topic,
                balance = balance(inputs, policy),
                ruleNumbers = policy.effectiveLevel(Metric.Calories, Surface.Settings) == Visibility.Show,
                weightUnit = settings.weightUnit,
            )
        }
    }

    /**
     * Only where energy out and weight both show: the first term is ten times the weight, so with
     * weight hidden the working would give it away.
     */
    private fun working(inputs: EnergyExplainerInputs, policy: VisibilityPolicy): RestingWorkingView? {
        if (!policy.shows(Metric.EnergyOut) || !policy.shows(Metric.Weight)) return null
        val settings = inputs.settings
        val profile = settings.bodyProfile(inputs.choices.weightKg, inputs.today)
        val working = RestingEnergies.working(profile) ?: return null
        val kg = profile.weightKg ?: return null
        val cm = profile.heightCm ?: return null
        val age = profile.ageYears ?: return null
        val sex = profile.sex ?: return null
        fun energy(kcal: Double, bare: Boolean = true) = policy.resolve(Metric.EnergyOut, abs(kcal), Surface.Settings, FormatContext(bare = bare))
        return RestingWorkingView(
            weight = policy.resolve(Metric.Weight, kg, Surface.Settings, FormatContext(weightUnit = WeightUnit.Kg)),
            heightCm = kotlin.math.round(cm).toInt(),
            age = age,
            sex = sex,
            weightTerm = energy(working.weightTerm),
            heightTerm = energy(working.heightTerm),
            ageTerm = energy(working.ageTerm),
            sexTerm = energy(working.sexTerm),
            sexAdds = working.sexTerm >= 0,
            total = energy(working.kcal.toDouble(), bare = false),
        )
    }

    private fun inUse(inputs: EnergyExplainerInputs, policy: VisibilityPolicy): RestingInUse? {
        if (!policy.shows(Metric.EnergyOut)) return null
        val settings = inputs.settings
        val choices = inputs.choices
        val resolved = RestingEnergies.resolve(settings.restingSource, choices.calculatedKcal, choices.healthKcal, settings.restingOwnKcal)
        if (resolved == null || resolved.from == RestingFrom.Calculated || settings.restingSource == RestingSource.Calculate) return null
        val amount = policy.resolve(Metric.EnergyOut, resolved.kcal.toDouble(), Surface.Settings, FormatContext())
        return RestingInUse(resolved.from, choices.healthSource.takeIf { resolved.from == RestingFrom.Health }, amount)
    }

    /** As Energy today adds it, on Today's surface: digesting food only where calories show. */
    private fun burned(inputs: EnergyExplainerInputs, policy: VisibilityPolicy): BurnedWorking? {
        val context = FormatContext(day = inputs.today)
        if (policy.effectiveLevel(Metric.EnergyOut, Surface.Today, context) != Visibility.Show) return null
        val burned = inputs.burned?.shownOn(policy, Surface.Today, context) ?: return null
        val bare = context.copy(bare = true)
        fun energy(kcal: Int?) = kcal?.let { policy.resolve(Metric.EnergyOut, it.toDouble(), Surface.Today, bare) } ?: Display.Hidden
        val counted = burned.activityCounted
        return BurnedWorking(
            resting = energy(burned.resting.kcal),
            everyday = energy(burned.everydayKcal),
            movement = energy(burned.movementKcal.takeIf { counted }),
            workouts = energy(burned.workoutKcal.takeIf { counted }),
            exercise = energy(burned.exerciseKcal?.takeIf { counted && it > 0 }),
            food = energy(burned.foodKcal),
            total = policy.resolve(Metric.EnergyOut, burned.totalKcal.toDouble(), Surface.Today, context),
        )
    }

    /** A set deficit or surplus with Burned known, and calories and energy out both on show. */
    private fun balance(inputs: EnergyExplainerInputs, policy: VisibilityPolicy): BalanceWorking? {
        val settings = inputs.settings
        val burned = inputs.burned ?: return null
        if (!policy.shows(Metric.Calories) || !policy.shows(Metric.EnergyOut)) return null
        val (surplus, amount) = when (val style = settings.targets.style) {
            is TargetStyle.Deficit -> false to style.kcal
            is TargetStyle.Surplus -> true to style.kcal
            else -> return null
        }
        val goal = settings.targets.rangeFor(inputs.today, settings.numbers.preset, burned.totalKcal) as? DayGoal.About ?: return null
        val sum = if (surplus) burned.totalKcal + amount else burned.totalKcal - amount
        val bare = FormatContext(bare = true)
        return BalanceWorking(
            surplus = surplus,
            burned = policy.resolve(Metric.EnergyOut, burned.totalKcal.toDouble(), Surface.Settings, bare),
            amount = policy.resolve(Metric.Calories, amount.toDouble(), Surface.Settings, bare),
            goal = policy.resolve(Metric.Calories, goal.kcal.toDouble(), Surface.Settings, FormatContext()),
            // Goals are rounded to 10, so only a gap bigger than the rounding is the floor's doing.
            held = abs(goal.kcal - sum) > ROUNDING_KCAL,
        )
    }

    private fun VisibilityPolicy.shows(metric: Metric): Boolean = effectiveLevel(metric, Surface.Settings) == Visibility.Show

    private const val ROUNDING_KCAL = 5
}

/**
 * The Resting energy page's "Male, 28, 5 ft 11 in, 170 lb": what the calculation is given, so a
 * wrong one is easy to spot. [weight] is Hidden where the mode hides weight, and the line then
 * names the latest weigh-in instead. Null until every detail is in.
 */
data class ProfileInputs(val sex: BodySex, val age: Int, val heightCm: Double, val heightUnit: HeightUnit, val weight: Display)

internal object ProfileInputsBuilder {
    fun build(settings: Settings, weightKg: Double?, today: LocalDate, policy: VisibilityPolicy, regionHeight: HeightUnit): ProfileInputs? {
        val profile = settings.bodyProfile(weightKg, today)
        if (profile.missing.isNotEmpty()) return null
        val kg = profile.weightKg ?: return null
        return ProfileInputs(
            sex = profile.sex ?: return null,
            age = profile.ageYears ?: return null,
            heightCm = profile.heightCm ?: return null,
            heightUnit = settings.heightUnitOr(regionHeight),
            weight = if (policy.effectiveLevel(Metric.Weight, Surface.Settings) == Visibility.Show) {
                policy.resolve(Metric.Weight, kg, Surface.Settings, FormatContext(weightUnit = settings.weightUnit))
            } else {
                Display.Hidden
            },
        )
    }
}
