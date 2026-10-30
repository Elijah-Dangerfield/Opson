package com.opson.libraries.nutrition

import kotlin.math.abs
import kotlin.math.roundToInt

/** Where a typical day's activity came from. */
enum class ActivityFrom(val key: String) {
    /** The everyday activity level's share of resting energy. */
    Everyday("everyday"),

    /** The health app's last whole days, averaged. */
    Health("health"),
    ;

    companion object {
        fun fromKey(key: String): ActivityFrom? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Everything a typical day's burn was worked out from, kept so a later change can be named
 * ("You connected Apple Health") and its effect shown. Weight, height and the rest are the
 * profile's, as resting energy read them.
 */
data class BaselineInputs(
    val restingFrom: RestingFrom,
    val restingKcal: Int,
    val activityFrom: ActivityFrom,
    val everyday: EverydayActivity,
    val activityKcal: Int,
    val digesting: Boolean,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val ageYears: Int? = null,
    val sex: BodySex? = null,
    val stage: BodyStage = BodyStage.None,
    val bodyFatPercent: Double? = null,
)

/**
 * The typical day a goal's target is worked out from: [burnKcal] is resting plus activity, before
 * digesting food; [extraKcal] what pregnancy or breastfeeding adds; [digesting] whether digesting
 * food counts; [sex] picks the low-goal line. [inputs] is null for a goal migrated from before
 * baselines, whose burn was recovered from the target it wrote.
 */
data class GoalBaseline(
    val burnKcal: Int,
    val extraKcal: Int = 0,
    val digesting: Boolean = false,
    val sex: BodySex? = null,
    val inputs: BaselineInputs? = null,
)

/** What changed since a goal's baseline, named for the recalculation offer. */
enum class BaselineChange(val discrete: Boolean) {
    /** Resting energy now comes from somewhere else (the equation, the health app, your own number). */
    RestingSource(discrete = true),

    /** A new number of your own on Resting energy. */
    OwnResting(discrete = true),

    /** The health app's resting energy moved. */
    Resting(discrete = false),

    /** Activity now comes from the health app, or no longer does. */
    ActivitySource(discrete = true),

    /** A different everyday activity level. */
    ActivityLevel(discrete = true),

    /** The health app's average activity moved. */
    Activity(discrete = false),

    /** "Count digesting food" was turned on or off. */
    DigestingFood(discrete = true),
    Weight(discrete = false),
    Height(discrete = true),
    Age(discrete = true),
    Sex(discrete = true),

    /** Pregnant, breastfeeding, or neither. */
    Stage(discrete = true),

    /** Body fat changes the fat-loss limit, not the target: listed, never a reason on its own. */
    BodyFat(discrete = false),

    /** No inputs were kept (a goal set before baselines): only the target's size can say. */
    Unknown(discrete = false),
}

/**
 * A recalculation to offer: the goal's target under its baseline ([oldAimKcal]) and under the
 * current inputs ([newAimKcal]), why ([changes]), and the baseline to store on Accept.
 */
data class Recalculation(
    val goalId: String,
    val changes: Set<BaselineChange>,
    val oldAimKcal: Int?,
    val newAimKcal: Int,
    val oldInputs: BaselineInputs?,
    val newBaseline: GoalBaseline,
)

/**
 * A typical day's burn and when it has changed enough to offer a new target (docs/plans/goals-v2.md,
 * "A goal owns its inputs").
 */
object GoalBaselines {

    // TODO(clinician): how far a target has to move before a recalculation is offered. An engineer
    // picked both: any visible change for something the person did, and two Targets steps for drift
    // (a weigh-in, a health app average), so a weekly weigh-in prompts about monthly during a loss.
    const val DISCRETE_THRESHOLD_KCAL: Int = 10
    const val DRIFT_THRESHOLD_KCAL: Int = 100

    /** A health app average needs this many whole days with a reading, or everyday activity stands in. */
    const val MIN_HEALTH_DAYS: Int = 3

    /** Health activity is rounded to this, so a store's daily noise does not move the baseline. */
    const val HEALTH_ROUND_KCAL: Int = 50

    /**
     * The inputs for a typical day. Activity is the mean of [healthActivityKcal] (the last whole
     * days' movement and workouts) when [activityCounted] and at least [MIN_HEALTH_DAYS] have one,
     * rounded to [HEALTH_ROUND_KCAL]; otherwise [everyday]'s share of resting energy.
     */
    fun inputs(
        resting: RestingEnergy,
        everyday: EverydayActivity,
        healthActivityKcal: List<Int>,
        activityCounted: Boolean,
        digesting: Boolean,
        profile: BodyProfile,
        stage: BodyStage,
        bodyFatPercent: Double?,
    ): BaselineInputs {
        val readings = healthActivityKcal.filter { it >= 0 }
        val fromHealth = activityCounted && readings.size >= MIN_HEALTH_DAYS
        val activity = if (fromHealth) {
            (readings.average() / HEALTH_ROUND_KCAL).roundToInt() * HEALTH_ROUND_KCAL
        } else {
            everyday.kcalFor(resting.kcal)
        }
        return BaselineInputs(
            restingFrom = resting.from,
            restingKcal = resting.kcal,
            activityFrom = if (fromHealth) ActivityFrom.Health else ActivityFrom.Everyday,
            everyday = everyday,
            activityKcal = activity,
            digesting = digesting,
            weightKg = profile.weightKg,
            heightCm = profile.heightCm,
            ageYears = profile.ageYears,
            sex = profile.sex,
            stage = stage,
            bodyFatPercent = bodyFatPercent?.let(BodyFat::inRange),
        )
    }

    /** The baseline [inputs] give. */
    fun of(inputs: BaselineInputs): GoalBaseline = GoalBaseline(
        burnKcal = inputs.restingKcal + inputs.activityKcal,
        extraKcal = inputs.stage.extraKcal,
        digesting = inputs.digesting,
        sex = inputs.sex,
        inputs = inputs,
    )

    /** What differs between [old] and [new]; null [old] is [BaselineChange.Unknown]. */
    fun changes(old: BaselineInputs?, new: BaselineInputs): Set<BaselineChange> {
        if (old == null) return setOf(BaselineChange.Unknown)
        return buildSet {
            if (old.restingFrom != new.restingFrom) {
                add(BaselineChange.RestingSource)
            } else if (old.restingKcal != new.restingKcal) {
                add(if (new.restingFrom == RestingFrom.Own) BaselineChange.OwnResting else BaselineChange.Resting)
            }
            if (old.activityFrom != new.activityFrom) {
                add(BaselineChange.ActivitySource)
            } else if (new.activityFrom == ActivityFrom.Everyday && old.everyday != new.everyday) {
                add(BaselineChange.ActivityLevel)
            } else if (new.activityFrom == ActivityFrom.Health && old.activityKcal != new.activityKcal) {
                add(BaselineChange.Activity)
            }
            if (old.digesting != new.digesting) add(BaselineChange.DigestingFood)
            if (!sameKg(old.weightKg, new.weightKg)) add(BaselineChange.Weight)
            if (!sameKg(old.heightCm, new.heightCm)) add(BaselineChange.Height)
            if (old.ageYears != new.ageYears) add(BaselineChange.Age)
            if (old.sex != new.sex) add(BaselineChange.Sex)
            if (old.stage != new.stage) add(BaselineChange.Stage)
            if (!sameKg(old.bodyFatPercent, new.bodyFatPercent)) add(BaselineChange.BodyFat)
        }
    }

    /**
     * The recalculation to offer [goal] under [current] inputs, or null: the goal doesn't set the
     * target, nothing changed, the target would barely move ([DISCRETE_THRESHOLD_KCAL] after
     * something the person did, [DRIFT_THRESHOLD_KCAL] after drift), or they already kept their
     * target through these inputs. A goal with no baseline yet is offered one whenever the inputs
     * can give it.
     */
    fun recalculation(goal: Goal, current: BaselineInputs): Recalculation? {
        if (!goal.drivesTarget) return null
        val newBaseline = of(current)
        val newAim = GoalTargets.aimKcal(goal.type, newBaseline) ?: return null
        val old = goal.baseline ?: return Recalculation(goal.id, emptySet(), null, newAim, null, newBaseline)
        val oldAim = GoalTargets.aimKcal(goal.type, old) ?: return null
        val changes = changes(old.inputs, current)
        if (!worthOffering(changes, oldAim, newAim)) return null
        goal.declined?.let { declined ->
            val sinceDeclined = changes(declined, current)
            val declinedAim = GoalTargets.aimKcal(goal.type, of(declined)) ?: return@let
            if (!worthOffering(sinceDeclined, declinedAim, newAim)) return null
        }
        return Recalculation(goal.id, changes, oldAim, newAim, old.inputs, newBaseline)
    }

    private fun worthOffering(changes: Set<BaselineChange>, oldAim: Int, newAim: Int): Boolean {
        if (changes.isEmpty()) return false
        val threshold = if (changes.any { it.discrete }) DISCRETE_THRESHOLD_KCAL else DRIFT_THRESHOLD_KCAL
        return abs(newAim - oldAim) >= threshold
    }

    // A tenth of a kilogram (or centimetre, or percent) is the profile's own precision.
    private fun sameKg(a: Double?, b: Double?): Boolean =
        if (a == null || b == null) a == b else abs(a - b) < PRECISION

    private const val PRECISION = 0.05
}
