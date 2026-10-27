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
     * One number instead of a range: the aim, [Targets.energyKcal]. The range's ends are kept and
     * move with it ([Targets.stepNumber]), so going back to a range lands around the number, and
     * the floor under the range is the floor under the number.
     */
    data object Exact : TargetStyle

    /**
     * A fixed amount over what the user burns, for gaining. [kcal] is the surplus, 0 to
     * [MAX_SURPLUS_KCAL], or to [startingKcal] while that is unset, as the deficit. Until a burn is known it is added to the aim instead
     * ([Targets.surplusGoal]): a surplus can only put the goal above the aim, never under the
     * floor, so starting from the aim is safe where a deficit's fallback has to be the range.
     * Offered where [surplusAllowed], as the deficit is.
     */
    data class Surplus(val kcal: Int, val startingKcal: Int) : TargetStyle

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

        /**
         * A set surplus is offered where a deficit is (design sync 4: both are Full numbers
         * only). Elsewhere a surplus chosen earlier, the style's or a day group's, falls back to
         * the range.
         */
        fun surplusAllowed(preset: PresetId): Boolean = deficitAllowed(preset)
    }
}

/**
 * One group of weekdays in [TargetStyle.ByDay] and its range. [days] may be empty while the user
 * fills it. [shape] says how the group's goal reads; the ends are kept whatever it is, so a group
 * switched back to a range finds its range where it left it.
 */
data class DayGroup(
    val days: Set<DayOfWeek>,
    val minKcal: Int,
    val maxKcal: Int,
    val shape: GroupShape = GroupShape.Range,
) {
    val midKcal: Int get() = (minKcal + maxKcal) / 2
}

/**
 * How one day group's goal is shaped, so "different by day" can mix them: a range on rest days,
 * one number or a surplus on training days. A set deficit is not offered per group.
 */
sealed interface GroupShape {
    /** The group's low and high end. */
    data object Range : GroupShape

    /** One number, the group's middle. Its ends move with it, as [TargetStyle.Exact]'s do. */
    data object Exact : GroupShape

    /**
     * [kcal] over what the user burns, or over the group's middle until that is known, capped as
     * [TargetStyle.Surplus] is.
     */
    data class Surplus(val kcal: Int, val startingKcal: Int) : GroupShape
}

/** Which end of a range a step moves. */
enum class RangeEnd { Low, High }

/** Why a range step is refused: it would cross the floor, or bring the ends closer than one step. */
enum class StepBlock { Floor, Gap }

/**
 * The energy goal for one day, whatever the style. Today draws [Range] as the dashed band, and
 * [Exact] and [About] as a single line: "Goal about 1,870" is a result, never the deficit or
 * surplus itself. [groupIndex] is the day group a goal came from under [TargetStyle.ByDay]
 * ("Training day · 2,300 to 2,700"), null otherwise.
 */
sealed interface DayGoal {
    val minKcal: Int
    val maxKcal: Int
    val targetKcal: Int

    val groupIndex: Int?

    data class Range(
        override val minKcal: Int,
        override val maxKcal: Int,
        override val targetKcal: Int,
        override val groupIndex: Int? = null,
    ) : DayGoal

    /** A single goal worked out from a set deficit or a surplus, rounded to 10. */
    data class About(val kcal: Int, override val groupIndex: Int? = null) : DayGoal {
        override val minKcal: Int get() = kcal
        override val maxKcal: Int get() = kcal
        override val targetKcal: Int get() = kcal
    }

    /** One number the user set, as it is. */
    data class Exact(val kcal: Int, override val groupIndex: Int? = null) : DayGoal {
        override val minKcal: Int get() = kcal
        override val maxKcal: Int get() = kcal
        override val targetKcal: Int get() = kcal
    }
}
