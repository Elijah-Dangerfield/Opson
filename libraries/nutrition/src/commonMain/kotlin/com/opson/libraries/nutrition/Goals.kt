package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDate

/*
 * Goals (docs/plans/goals-v2.md). A goal is a type with its own parameters, a start date and a
 * status. At most one goal sets the calorie target ([GoalType.setsCalories]); the others sit
 * beside it. Everything here is a plain value: Settings stores the list (`goals`), and the engine
 * (GoalTargets, WeeklyBudgets, GoalDays, GoalProjections) reads it.
 */

/** What kind of goal, as the wire and the goal UI name it. */
enum class GoalKind(val key: String) {
    Weight("weight"),
    EnergyBalance("energy_balance"),
    Burn("burn"),
    Steps("steps"),
    TrackEveryDay("track_every_day"),
    Meals("meals"),

    /** A kind a newer build wrote: kept as it came and never acted on. */
    Unknown("unknown"),
    ;

    companion object {
        fun fromKey(key: String): GoalKind? = entries.firstOrNull { it.key == key && it != Unknown }
    }
}

/** Active goals count; a paused goal is kept with its settings; done is reached and kept for the record. */
enum class GoalStatus(val key: String) {
    Active("active"),
    Paused("paused"),
    Done("done"),
    ;

    companion object {
        fun fromKey(key: String): GoalStatus? = entries.firstOrNull { it.key == key }
    }
}

/**
 * How a calorie goal's days are judged. [Daily] is the default: every day stands alone. [Weekly]
 * spreads what is left of the week over the days still to come, capped ([WeeklyBudgets]).
 */
enum class GoalAveraging(val key: String) {
    Daily("daily"),
    Weekly("weekly"),
    ;

    companion object {
        fun fromKey(key: String): GoalAveraging? = entries.firstOrNull { it.key == key }
    }
}

/** What a burn goal counts: the whole day's Burned (TDEE), or only what is above resting energy. */
enum class BurnMeasure(val key: String) {
    Total("total"),
    Active("active"),
    ;

    companion object {
        fun fromKey(key: String): BurnMeasure? = entries.firstOrNull { it.key == key }
    }
}

/** A goal's type and its own parameters. */
sealed interface GoalType {
    val kind: GoalKind

    /**
     * Lose, keep or gain weight. [direction] is [GoalDirection.Lose], [GoalDirection.Maintain] or
     * [GoalDirection.Gain]; [kgPerWeek] is always positive, and 0 while keeping.
     */
    data class Weight(
        val direction: GoalDirection,
        val targetKg: Double? = null,
        val kgPerWeek: Double = 0.0,
        val startKg: Double? = null,
    ) : GoalType {
        override val kind: GoalKind get() = GoalKind.Weight
    }

    /** A set amount a day under (negative) or over (positive) a typical day's burn. */
    data class EnergyBalance(val kcalPerDay: Int) : GoalType {
        override val kind: GoalKind get() = GoalKind.EnergyBalance
    }

    /** Burn at least [kcal] a day, the whole day or active energy only. */
    data class Burn(val kcal: Int, val measure: BurnMeasure = BurnMeasure.Active) : GoalType {
        override val kind: GoalKind get() = GoalKind.Burn
    }

    /** At least [count] steps a day. Nothing reads steps yet ([GoalDayFacts.steps] is the hook). */
    data class Steps(val count: Int) : GoalType {
        override val kind: GoalKind get() = GoalKind.Steps
    }

    /** Show up every day: log something, wrap the day up or check in. */
    data object TrackEveryDay : GoalType {
        override val kind: GoalKind get() = GoalKind.TrackEveryDay
    }

    /** [count] meals a day with food in them. */
    data class Meals(val count: Int = DEFAULT_MEALS) : GoalType {
        override val kind: GoalKind get() = GoalKind.Meals
    }

    /** A type a newer build wrote, [raw] as it came, so saving here doesn't delete it. */
    data class Unknown(val wireKind: String, val raw: String) : GoalType {
        override val kind: GoalKind get() = GoalKind.Unknown
    }

    /**
     * Energy a day against a typical day's burn: negative for a loss, 0 to keep, positive to gain.
     * Null for a type that doesn't set a calorie target.
     */
    val deltaKcal: Int?
        get() = when (this) {
            is Weight -> when (direction) {
                GoalDirection.Lose -> -WeightGoals.kcalPerDay(kgPerWeek)
                GoalDirection.Gain -> WeightGoals.kcalPerDay(kgPerWeek)
                else -> 0
            }
            is EnergyBalance -> kcalPerDay
            else -> null
        }

    /** Whether the goal sets the day's calorie target. */
    val setsCalories: Boolean get() = deltaKcal != null

    companion object {
        const val DEFAULT_MEALS = 3
    }
}

/**
 * One goal. [id] is stable for the goal's life. [setsTarget] is false once the person chose "Set my
 * own target": the goal is kept, and Targets is theirs. [baseline] is the typical day the target
 * is worked out from, and only changes when the person accepts a recalculation; [declined] the
 * inputs of the last recalculation they kept their target through, which is never offered again.
 * [targetsKcal] is the aim last written to Targets, how an older build's hand edit is spotted.
 * [history] is every recalculation the person answered, oldest first, at most [MAX_HISTORY]: what
 * the goal screen lists, so a target that moved says when and why.
 */
data class Goal(
    val id: String,
    val type: GoalType,
    val startDate: LocalDate,
    val status: GoalStatus = GoalStatus.Active,
    val averaging: GoalAveraging = GoalAveraging.Daily,
    val toleranceKcal: Int = DEFAULT_TOLERANCE_KCAL,
    val setsTarget: Boolean = type.setsCalories,
    val baseline: GoalBaseline? = null,
    val declined: BaselineInputs? = null,
    val targetsKcal: Int? = null,
    val history: List<GoalRecalculated> = emptyList(),
) {
    val isActive: Boolean get() = status == GoalStatus.Active

    /** Active, of a type that sets the calorie target, and not overridden by the person. */
    val drivesTarget: Boolean get() = isActive && setsTarget && type.setsCalories

    /** The weight goal's own fields, when it is one. */
    val weight: GoalType.Weight? get() = type as? GoalType.Weight

    companion object {
        // TODO(clinician): how far either side of a goal's target still counts as on it. An
        // engineer picked it: the same leeway one number has on Today (ONE_NUMBER_LEEWAY_KCAL).
        const val DEFAULT_TOLERANCE_KCAL: Int = ONE_NUMBER_LEEWAY_KCAL

        /** The bands the goal UI may offer. */
        val TOLERANCES_KCAL: List<Int> = listOf(50, 100, 150, 200)

        /** The id of the one weight goal, so a migration and a later write agree on it. */
        const val WEIGHT_ID = "weight"

        /** How many answered recalculations a goal keeps: about a year of monthly weigh-in drift. */
        const val MAX_HISTORY = 12
    }

    /** This goal with [entry] added to its history, the oldest dropped past [MAX_HISTORY]. */
    fun recorded(entry: GoalRecalculated): Goal = copy(history = (history + entry).takeLast(MAX_HISTORY))
}

/**
 * A recalculation the person answered on [date]: what changed, the daily target before
 * ([oldAimKcal], null for a goal set before baselines) and the one offered ([newAimKcal]), and
 * whether they took it ([accepted]) or kept their target.
 */
data class GoalRecalculated(
    val date: LocalDate,
    val changes: Set<BaselineChange>,
    val oldAimKcal: Int?,
    val newAimKcal: Int,
    val accepted: Boolean,
)
