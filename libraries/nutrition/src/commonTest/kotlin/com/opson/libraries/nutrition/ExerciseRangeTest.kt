package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercise in today's range, as properties over a grid of bases, readings and choices: the range
 * only ever goes up for movement, by at most [EXERCISE_CAP_KCAL], and a quiet day, a missing
 * reading or a nonsense one never lowers it.
 */
class ExerciseRangeTest {

    private val bases = listOf(
        Targets.Default,
        Targets.Default.raise().raise(),
        Targets(energyKcal = 1600, minKcal = 1400, maxKcal = 1800, proteinG = 90, carbsG = 180, fatG = 55, fiberG = 25),
        Targets(energyKcal = 3200, minKcal = 3000, maxKcal = 3400, proteinG = 160, carbsG = 400, fatG = 110, fiberG = 38),
    )

    private val readings = listOf(0, 1, 49, 150, 260, 999, 1_000, 2_500, 5_000, 100_000, Int.MAX_VALUE, -1, -500, Int.MIN_VALUE)

    private fun everyCase(check: (Targets, ExerciseAdjustment, Int, Int) -> Unit) {
        bases.forEach { base ->
            ExerciseAdjustment.entries.forEach { choice ->
                readings.forEach { movement -> readings.forEach { workout -> check(base, choice, movement, workout) } }
            }
        }
    }

    @Test
    fun neverBelowTheBase() = everyCase { base, choice, movement, workout ->
        val today = base.today(choice, movement, workout)
        assertTrue(today.minKcal >= base.minKcal, "$choice $movement $workout low end")
        assertTrue(today.maxKcal >= base.maxKcal, "$choice $movement $workout high end")
        assertTrue(today.targetKcal >= base.energyKcal, "$choice $movement $workout target")
    }

    @Test
    fun neverMoreThanTheCapAbove() = everyCase { base, choice, movement, workout ->
        val today = base.today(choice, movement, workout)
        assertTrue(today.activityKcal in 0..EXERCISE_CAP_KCAL, "$choice $movement $workout added ${today.activityKcal}")
        assertEquals(base.minKcal + today.activityKcal, today.minKcal)
        assertEquals(base.maxKcal + today.activityKcal, today.maxKcal)
        assertEquals(base.energyKcal + today.activityKcal, today.targetKcal)
    }

    @Test
    fun no_addsNothing() = everyCase { base, _, movement, workout ->
        assertEquals(base.tomorrow(), base.today(ExerciseAdjustment.None, movement, workout))
    }

    @Test
    fun aQuietDay_addsNothing() = everyCase { base, choice, _, _ ->
        assertEquals(base.tomorrow(), base.today(choice, 0, 0))
    }

    @Test
    fun some_isTheShareOfAll_untilTheCap() {
        readings.filter { it in 0..2_000 }.forEach { movement ->
            readings.filter { it in 0..2_000 }.forEach { workout ->
                val all = ExerciseAdjustment.All.activityKcal(movement, workout)
                val some = ExerciseAdjustment.Some.activityKcal(movement, workout)
                assertTrue(some <= all, "$movement $workout")
                val expected = minOf(EXERCISE_CAP_KCAL.toDouble(), (movement + workout) * EXERCISE_SOME_SHARE)
                assertTrue(kotlin.math.abs(some - expected) <= 0.5, "$movement $workout: $some vs $expected")
            }
        }
    }

    @Test
    fun moreMovement_neverLowersIt() = everyCase { base, choice, movement, workout ->
        if (movement < 0 || movement > 1_000_000) return@everyCase
        val less = base.today(choice, movement, workout).targetKcal
        val more = base.today(choice, movement + 100, workout).targetKcal
        assertTrue(more >= less, "$choice $movement $workout")
    }

    @Test
    fun aHugeReading_stopsAtTheCap() {
        assertEquals(EXERCISE_CAP_KCAL, ExerciseAdjustment.All.activityKcal(5_000, 0))
        assertEquals(EXERCISE_CAP_KCAL, ExerciseAdjustment.Some.activityKcal(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun tomorrow_neverMoves() = everyCase { base, choice, movement, workout ->
        base.today(choice, movement, workout)
        assertEquals(EnergyRange(base.energyKcal, base.minKcal, base.maxKcal, 0), base.tomorrow())
    }
}
