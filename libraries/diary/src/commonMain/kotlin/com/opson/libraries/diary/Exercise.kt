package com.opson.libraries.diary

import com.opson.libraries.nutrition.ExerciseActivities
import com.opson.libraries.nutrition.ExerciseEnergy
import com.opson.libraries.nutrition.ExerciseTotals
import com.opson.libraries.nutrition.Visibility
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/*
 * Exercise logged by hand (`release.exercise`, docs/research/energy-expenditure.md). Each entry
 * stores the energy it counts, worked out when it was saved, so a later weigh-in never rewrites
 * a past day. Burned adds these up through `BurnedRepository`; nothing else reads them as energy.
 */

/**
 * One logged activity. [activity] is an `ExerciseActivities` key, or [ExerciseActivities.OTHER]
 * with its [name]. [kcal] is what counts in Burned: the estimate, or [kcalTyped] when the person
 * typed it. [notOnWatch] keeps it in Burned even when a health app reads activity.
 */
data class ExerciseEntry(
    val id: String,
    val day: LocalDate,
    val startedAt: Instant,
    val activity: String,
    val name: String? = null,
    val minutes: Int,
    val kcal: Int,
    val kcalTyped: Boolean = false,
    val notOnWatch: Boolean = false,
) {
    val isOther: Boolean get() = activity == ExerciseActivities.OTHER
}

/** What the exercise sheet saves. The repository checks it the way the server will. */
data class ExerciseDraft(
    val day: LocalDate,
    val startedAt: Instant,
    val activity: String,
    val name: String? = null,
    val minutes: Int,
    val kcal: Int,
    val kcalTyped: Boolean = false,
    val notOnWatch: Boolean = false,
) {
    /** Null when it can be saved, otherwise what is wrong. */
    fun problem(): String? = when {
        activity.isBlank() || activity.length > ExerciseRules.MAX_ACTIVITY -> "activity"
        activity != ExerciseActivities.OTHER && ExerciseActivities[activity] == null -> "activity"
        activity == ExerciseActivities.OTHER && name.isNullOrBlank() -> "name"
        (name?.length ?: 0) > ExerciseRules.MAX_NAME -> "name"
        minutes !in 1..ExerciseEnergy.MAX_MINUTES -> "minutes"
        kcal !in 0..ExerciseEnergy.MAX_KCAL -> "kcal"
        else -> null
    }
}

object ExerciseRules {
    const val MAX_ACTIVITY = 40
    const val MAX_NAME = 80
}

/**
 * Logged exercise, stored on the device and synced like the rest of the diary. A day closed with
 * Close the day takes no new exercise and keeps what it has: each write answers
 * [DayWrite.DayClosed] for it.
 */
interface ExerciseRepository {
    /** [day]'s entries by start time. */
    fun observeDay(day: LocalDate): Flow<List<ExerciseEntry>>

    suspend fun get(id: String): ExerciseEntry?

    suspend fun add(draft: ExerciseDraft): DayWrite<ExerciseEntry>

    /** Replaces [id] with [draft]; nothing happens when it is gone. */
    suspend fun update(id: String, draft: ExerciseDraft): DayWrite<Unit>

    suspend fun delete(id: String): DayWrite<Unit>
}

/** A day's entries as Burned counts them. */
fun List<ExerciseEntry>.totals(): ExerciseTotals =
    ExerciseTotals(allKcal = sumOf { it.kcal }, notOnWatchKcal = filter { it.notOnWatch }.sumOf { it.kcal })

/**
 * Whether the exercise sheet and Today's Burned section are offered: released, energy out not
 * hidden (exercise only exists to count energy, so No numbers and the macro modes go without),
 * and never for someone who said numbers have been hard, as with exercise in the range.
 * TODO(clinician): confirm that last rule, as for fasting.
 */
fun Settings.exerciseOffered(released: Boolean, energyOut: Visibility): Boolean =
    released && energyOut != Visibility.Hide && !onboarding.hard
