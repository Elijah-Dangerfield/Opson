package com.opson.libraries.nutrition

import kotlinx.datetime.DayOfWeek

/**
 * How the user's energy goal is shaped (design v3, the Targets "Goal style" picker). Every style
 * answers the same question through [Targets.rangeFor], so Today and Trends never branch on it.
 */
sealed interface TargetStyle {

    /** A low and a high end, the default for everyone. */
    data object Range : TargetStyle

    /**
     * A fixed amount under what the user burns. [kcal] is the deficit; [startingKcal] is where it
     * began, and the most it can grow to while [MAX_DEFICIT_KCAL] is unset. The goal is never
     * below the floor ([Targets.floorKcal]), whatever the burn. Offered only where
     * [deficitAllowed].
     */
    data class Deficit(val kcal: Int, val startingKcal: Int) : TargetStyle

    /**
     * Up to [MAX_GROUPS] groups of weekdays, each with its own range. Every weekday is in exactly
     * one group; build and change it through [Targets], which keeps that true.
     */
    data class ByDay(val groups: List<DayGroup>) : TargetStyle

    companion object {
        const val MAX_GROUPS = 4

        /**
         * A set deficit is offered in Full numbers and Sprint only (design v3); in every other
         * mode the option is hidden and a deficit chosen earlier falls back to the range.
         */
        fun deficitAllowed(preset: PresetId): Boolean = preset == PresetId.Full || preset == PresetId.Sprint
    }
}

/** One group of weekdays in [TargetStyle.ByDay] and its range. [days] may be empty while the user fills it. */
data class DayGroup(
    val days: Set<DayOfWeek>,
    val minKcal: Int,
    val maxKcal: Int,
) {
    val midKcal: Int get() = (minKcal + maxKcal) / 2
}

/** Which end of a range a step moves. */
enum class RangeEnd { Low, High }

/** Why a range step is refused: it would cross the floor, or bring the ends closer than one step. */
enum class StepBlock { Floor, Gap }

/**
 * The energy goal for one day, whatever the style. Today draws [Range] as the dashed band and
 * [About] as "Goal about 1,870": a result, never the deficit itself.
 */
sealed interface DayGoal {
    val minKcal: Int
    val maxKcal: Int
    val targetKcal: Int

    /**
     * A range. [groupIndex] is the day group it came from under [TargetStyle.ByDay] ("Training
     * day · 2,300 to 2,700"), null for a plain range.
     */
    data class Range(
        override val minKcal: Int,
        override val maxKcal: Int,
        override val targetKcal: Int,
        val groupIndex: Int? = null,
    ) : DayGoal

    /** A single goal from a set deficit, rounded to 10. */
    data class About(val kcal: Int) : DayGoal {
        override val minKcal: Int get() = kcal
        override val maxKcal: Int get() = kcal
        override val targetKcal: Int get() = kcal
    }
}
