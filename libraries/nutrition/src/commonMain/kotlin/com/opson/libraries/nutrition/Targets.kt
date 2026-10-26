package com.opson.libraries.nutrition

import kotlin.math.max
import kotlin.math.roundToInt

// TODO(dietitian): GOAL_FLOOR_KCAL must be set by an ED-informed dietitian
// before launch. Do not invent a value, not even a "safe-looking" one: a floor
// picked by an engineer is exactly the unreviewed number this exists to
// prevent. While it is null, nothing can lower the energy target below where
// the user started, so the target can only go up.
val GOAL_FLOOR_KCAL: Int? = null

/**
 * The user's energy range and macro goals. [startingKcal] is the target the
 * user began with, and is the floor until [GOAL_FLOOR_KCAL] is set.
 *
 * [ratioBasis] is bookkeeping for ratio mode, not something a screen reads:
 * the split the current grams were scaled from. Scaling each 50 kcal step from
 * the previous step's rounded grams drifts (ten raises put fat 3 g off the
 * prototype's `GOAL[k] * baseT / GOAL.target`), so ratio steps always scale from
 * this fixed point instead. Any change that does not scale clears it.
 */
data class Targets(
    val energyKcal: Int,
    val minKcal: Int,
    val maxKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int,
    val startingKcal: Int = energyKcal,
    val ratioBasis: MacroSplit? = null,
) {
    val floorKcal: Int get() = GOAL_FLOOR_KCAL ?: startingKcal

    val canLower: Boolean get() = energyKcal - STEP_KCAL >= floorKcal

    /**
     * Raising is always allowed. With [scaleMacros] (ratio mode) protein, carbs
     * and fat move with the energy target so the split stays the same, the
     * prototype's `GOAL[k] * baseT / GOAL.target`. Gram mode leaves them. Fiber
     * is a floor for gut health, not a share of energy, so it never scales.
     */
    fun raise(scaleMacros: Boolean = false): Targets = shiftedBy(STEP_KCAL, scaleMacros)

    /** `null` when one more step down would cross the floor. [scaleMacros] as in [raise]. */
    fun lower(scaleMacros: Boolean = false): Targets? = if (canLower) shiftedBy(-STEP_KCAL, scaleMacros) else null

    /** Sets the target directly, never below the floor. The range moves with it. */
    fun withEnergy(kcal: Int): Targets = shiftedBy(max(kcal, floorKcal) - energyKcal)

    /** Today's range: the base plus whatever the user lets watch activity add. */
    fun today(adjustment: ExerciseAdjustment, movementKcal: Int, workoutKcal: Int): EnergyRange {
        val activity = adjustment.activityKcal(movementKcal, workoutKcal)
        return EnergyRange(energyKcal + activity, minKcal + activity, maxKcal + activity, activity)
    }

    /**
     * Tomorrow always starts from the base. It takes no inputs on purpose: a
     * big day, a quiet day or a long workout must never move it, and least of
     * all down.
     */
    fun tomorrow(): EnergyRange = EnergyRange(energyKcal, minKcal, maxKcal, activityKcal = 0)

    private fun shiftedBy(kcal: Int, scaleMacros: Boolean = false): Targets {
        val energy = energyKcal + kcal
        val shifted = copy(energyKcal = energy, minKcal = minKcal + kcal, maxKcal = maxKcal + kcal, ratioBasis = null)
        if (!scaleMacros) return shifted
        val basis = ratioBasis ?: MacroSplit(energyKcal, proteinG, carbsG, fatG)
        if (basis.kcal <= 0) return shifted
        fun scaled(grams: Int) = (grams * energy.toDouble() / basis.kcal).roundToInt()
        return shifted.copy(
            proteinG = scaled(basis.proteinG),
            carbsG = scaled(basis.carbsG),
            fatG = scaled(basis.fatG),
            ratioBasis = basis,
        )
    }

    companion object {
        const val STEP_KCAL = 50

        /** The handoff's starting goals, until onboarding sets real ones. */
        val Default = Targets(
            energyKcal = 2100,
            minKcal = 1900,
            maxKcal = 2300,
            proteinG = 120,
            carbsG = 250,
            fatG = 70,
            fiberG = 30,
        )
    }
}

/** Protein, carbs and fat grams at an energy target: the fixed point ratio mode scales from. */
data class MacroSplit(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

data class EnergyRange(
    val targetKcal: Int,
    val minKcal: Int,
    val maxKcal: Int,
    val activityKcal: Int,
)

/** How much watch activity (everyday movement plus workouts) adds to today's target. */
enum class ExerciseAdjustment {
    All,
    Some,
    None,
    ;

    /** Never negative, so activity can only ever add to a target. */
    fun activityKcal(movementKcal: Int, workoutKcal: Int): Int {
        val activity = max(0, movementKcal) + max(0, workoutKcal)
        return when (this) {
            All -> activity
            Some -> (activity / 2.0).roundToInt()
            None -> 0
        }
    }

    companion object {
        val Default = Some
    }
}
