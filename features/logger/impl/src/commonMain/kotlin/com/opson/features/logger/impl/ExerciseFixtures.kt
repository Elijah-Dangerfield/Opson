package com.opson.features.logger.impl

import com.opson.libraries.diary.ExerciseEntry
import com.opson.libraries.nutrition.ExerciseActivities
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** The exercise sheet's states for previews and the leak test, built through [ExerciseSheetStateBuilder]. */
object ExerciseFixtures {
    private val now = LocalDateTime(LoggerFixtures.today, LocalTime(18, 20))

    /** Resting energy for a 34-year-old woman, 168 cm, at the 70 kg weigh-in: Mifflin–St Jeor, rounded to 10. */
    private const val RESTING_KCAL = 1420

    private fun inputs(
        weightKg: Double? = 70.0,
        healthConnected: Boolean = false,
        entry: ExerciseEntry? = null,
        restingKcal: Int? = RESTING_KCAL,
    ) = ExerciseInputs(
        day = LoggerFixtures.today,
        now = now,
        timeZone = TimeZone.UTC,
        weightKg = weightKg,
        healthConnected = healthConnected,
        entry = entry,
        restingKcal = restingKcal.takeIf { weightKg != null },
    )

    /** Brisk walking for 45 minutes this morning, worked out from Profile and a 70 kg weigh-in. */
    fun formState(policy: VisibilityPolicy, healthConnected: Boolean = false): ExerciseSheetState = ExerciseSheetStateBuilder.build(
        ExerciseForm(activity = "walking_brisk", minutes = "45", startedAt = LocalTime(7, 30)),
        inputs(healthConnected = healthConnected),
        policy,
    )

    /** The same walk with Profile missing an age, height or sex: worked out from the weight alone, and it asks for the rest. */
    fun weightOnlyState(policy: VisibilityPolicy): ExerciseSheetState = ExerciseSheetStateBuilder.build(
        ExerciseForm(activity = "walking_brisk", minutes = "45", startedAt = LocalTime(7, 30)),
        inputs(restingKcal = null),
        policy,
    )

    /** No weigh-in yet: no estimate, so the number is asked for. */
    fun noWeightState(policy: VisibilityPolicy): ExerciseSheetState = ExerciseSheetStateBuilder.build(
        ExerciseForm(activity = "yoga", minutes = "60", startedAt = LocalTime(9, 0)),
        inputs(weightKg = null),
        policy,
    )

    fun otherState(policy: VisibilityPolicy): ExerciseSheetState = ExerciseSheetStateBuilder.build(
        ExerciseForm(activity = ExerciseActivities.OTHER, name = "Canoe polo", minutes = "90", typed = "520", startedAt = LocalTime(10, 0)),
        inputs(),
        policy,
    )

    /** Editing this morning's cycle, with a health app connected. */
    fun editState(policy: VisibilityPolicy): ExerciseSheetState {
        val entry = ExerciseEntry("e1", LoggerFixtures.today, Instant.parse("2026-10-25T06:45:00Z"), "cycling", minutes = 40, kcal = 327)
        return ExerciseSheetStateBuilder.build(ExerciseForm.from(entry, TimeZone.UTC), inputs(healthConnected = true, entry = entry), policy)
    }

    fun pickState(policy: VisibilityPolicy, query: String = ""): ExerciseSheetState = ExerciseSheetStateBuilder.build(
        ExerciseForm(pane = ExercisePane.Pick, query = query),
        inputs(),
        policy,
    )
}
