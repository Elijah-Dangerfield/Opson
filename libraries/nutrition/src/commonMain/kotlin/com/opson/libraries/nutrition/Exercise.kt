package com.opson.libraries.nutrition

import kotlin.math.roundToInt

/**
 * An activity someone can log, with its MET value and the 2024 Adult Compendium of Physical
 * Activities code it came from (docs/research/energy-expenditure.md). [key] is the wire value an
 * entry stores; its label is a string resource keyed by it, so renaming one is a migration.
 */
data class ExerciseActivity(val key: String, val met: Double, val compendiumCode: String)

/** The list the exercise sheet searches, most common first within each kind. */
object ExerciseActivities {
    /** An activity the list does not have: a name and a typed number, no MET. */
    const val OTHER = "other"

    val all: List<ExerciseActivity> = listOf(
        ExerciseActivity("walking", 3.8, "17190"),
        ExerciseActivity("walking_brisk", 4.8, "17200"),
        ExerciseActivity("walking_slow", 2.8, "17152"),
        ExerciseActivity("walking_dog", 3.0, "17165"),
        ExerciseActivity("hiking", 6.0, "17080"),
        ExerciseActivity("backpacking", 7.0, "17010"),
        ExerciseActivity("jogging", 7.5, "12020"),
        ExerciseActivity("running_12", 8.5, "12030"),
        ExerciseActivity("running_10", 9.3, "12050"),
        ExerciseActivity("running_8_5", 11.0, "12070"),
        ExerciseActivity("running_7_5", 12.0, "12090"),
        ExerciseActivity("running_6", 14.8, "12120"),
        ExerciseActivity("cycling_easy", 4.0, "01010"),
        ExerciseActivity("cycling", 8.0, "01030"),
        ExerciseActivity("cycling_fast", 10.0, "01040"),
        ExerciseActivity("mountain_biking", 8.5, "01009"),
        ExerciseActivity("exercise_bike", 6.8, "01200"),
        ExerciseActivity("spin_class", 9.0, "01270"),
        ExerciseActivity("elliptical", 5.0, "02048"),
        ExerciseActivity("rowing_machine", 5.0, "02071"),
        ExerciseActivity("rowing_machine_hard", 7.3, "02070"),
        ExerciseActivity("stair_machine", 9.3, "02065"),
        ExerciseActivity("stairs", 6.8, "17131"),
        ExerciseActivity("weights", 3.5, "02054"),
        ExerciseActivity("weights_heavy", 6.0, "02050"),
        ExerciseActivity("circuit", 5.0, "02035"),
        ExerciseActivity("hiit", 7.0, "02210"),
        ExerciseActivity("bodyweight", 3.8, "02022"),
        ExerciseActivity("yoga", 2.3, "02150"),
        ExerciseActivity("yoga_power", 4.0, "02160"),
        ExerciseActivity("pilates", 2.8, "02105"),
        ExerciseActivity("stretching", 2.3, "02101"),
        ExerciseActivity("tai_chi", 3.3, "15670"),
        ExerciseActivity("aerobics", 7.3, "02000"),
        ExerciseActivity("zumba", 6.5, "02310"),
        ExerciseActivity("dancing", 4.5, "03025"),
        ExerciseActivity("swimming", 8.0, "18290"),
        ExerciseActivity("swimming_easy", 6.0, "18310"),
        ExerciseActivity("water_aerobics", 5.5, "18355"),
        ExerciseActivity("tennis", 6.8, "15675"),
        ExerciseActivity("badminton", 5.5, "15030"),
        ExerciseActivity("basketball", 7.5, "15055"),
        ExerciseActivity("soccer", 7.0, "15610"),
        ExerciseActivity("volleyball", 3.0, "15720"),
        ExerciseActivity("golf", 4.3, "15265"),
        ExerciseActivity("boxing_bag", 5.8, "15110"),
        ExerciseActivity("martial_arts", 10.3, "15430"),
        ExerciseActivity("climbing", 8.0, "15533"),
        ExerciseActivity("jump_rope", 11.8, "15551"),
        ExerciseActivity("skiing", 6.3, "19160"),
        ExerciseActivity("cross_country_skiing", 8.5, "19090"),
        ExerciseActivity("skating", 7.0, "19030"),
        ExerciseActivity("inline_skating", 7.5, "15591"),
        ExerciseActivity("kayaking", 5.0, "18100"),
        ExerciseActivity("paddleboarding", 6.5, "18224"),
        ExerciseActivity("gardening", 3.8, "08245"),
        ExerciseActivity("mowing", 5.0, "08120"),
        ExerciseActivity("housework", 3.3, "05030"),
        ExerciseActivity("snow_shoveling", 5.3, "19252"),
    )

    private val byKey = all.associateBy { it.key }

    operator fun get(key: String): ExerciseActivity? = byKey[key]
}

/** How much energy an exercise entry counts, before anything is stored. */
object ExerciseEnergy {
    const val MAX_MINUTES = 24 * 60

    /** The most one entry may count; a stray extra digit, not a workout. The server checks the same. */
    const val MAX_KCAL = 5000

    private val WEIGHT_KG = 25.0..400.0
    private const val MINUTES_PER_HOUR = 60.0

    /**
     * (MET − 1) × kg × hours: what the activity burns on top of resting energy, which Burned
     * already counts for the whole day. Null without a usable weight or duration, in which case
     * the person types the number.
     */
    fun estimateKcal(met: Double, weightKg: Double?, minutes: Int): Int? {
        val kg = weightKg?.takeIf { it in WEIGHT_KG } ?: return null
        if (minutes !in 1..MAX_MINUTES || !met.isFinite()) return null
        return ((met - 1).coerceAtLeast(0.0) * kg * minutes / MINUTES_PER_HOUR).roundToInt().coerceAtMost(MAX_KCAL)
    }
}

/**
 * Activity outside workouts, as a share of resting energy on top of it (the usual 1.2 to 1.725
 * activity factors less the resting energy itself). [key] is the settings wire value.
 */
enum class EverydayActivity(val key: String, val shareOfResting: Double) {
    MostlySitting("mostly_sitting", 0.2),
    SomeWalking("some_walking", 0.375),
    OnMyFeet("on_my_feet", 0.55),
    PhysicalWork("physical_work", 0.725),
    ;

    /** For [restingKcal], rounded to 10 like resting energy. */
    fun kcalFor(restingKcal: Int): Int = (restingKcal.coerceAtLeast(0) * shareOfResting / 10).roundToInt() * 10

    companion object {
        val Default = MostlySitting

        fun fromKey(key: String): EverydayActivity? = entries.firstOrNull { it.key == key }
    }
}

/** Logged exercise on a day: everything, and the part a watch could not have seen. */
data class ExerciseTotals(val allKcal: Int, val notOnWatchKcal: Int) {
    companion object {
        val None = ExerciseTotals(0, 0)
    }
}

/**
 * What the health app read as activity for the day, in whole kcal. Only made when it read one of
 * the two: a store that answered with neither has not read activity, and the manual side counts.
 */
data class HealthActivity(val movementKcal: Int?, val workoutKcal: Int?)

/**
 * The one rule for where a day's activity comes from (docs/research/energy-expenditure.md):
 *
 * - [health] read (a store connected with an active energy reading): its movement and workouts.
 *   Everyday activity is left out, and so is logged exercise, except entries marked as not
 *   recorded by the watch.
 * - otherwise: [everyday] activity and all logged [exercise].
 *
 * [everyday] counts whenever no health app reads activity, so a deficit or surplus is aimed at a
 * whole day without one. [exercise] is null with `release.exercise` off: nothing logged by hand.
 */
fun burnedFor(
    resting: RestingEnergy,
    health: HealthActivity?,
    everyday: EverydayActivity?,
    exercise: ExerciseTotals?,
    activityCounted: Boolean,
): Burned = if (health != null) {
    Burned(
        resting = resting,
        movementKcal = health.movementKcal,
        workoutKcal = health.workoutKcal,
        activityCounted = activityCounted,
        exerciseKcal = exercise?.notOnWatchKcal,
    )
} else {
    Burned(
        resting = resting,
        activityCounted = activityCounted,
        everydayKcal = everyday?.kcalFor(resting.kcal),
        exerciseKcal = exercise?.allKcal,
    )
}
