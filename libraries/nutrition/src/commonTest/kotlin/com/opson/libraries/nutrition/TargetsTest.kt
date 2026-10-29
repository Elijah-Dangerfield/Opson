package com.opson.libraries.nutrition

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The safety rules on targets: the goal moves freely between [MIN_GOAL_KCAL]
 * and [MAX_GOAL_KCAL] while [GOAL_FLOOR_KCAL] is null (how low is too low is
 * a note, `GoalSafetyTest`, not a lock), and exercise adjustment only ever adds
 * to today, never to tomorrow (`S.exercise === 'all'` in `renderVals`).
 *
 * Not covered here: a floor set by a dietitian, because there is no such
 * value yet and inventing one for a test would be the first step to shipping it.
 */
class TargetsTest {

    private val base = Targets.Default

    @Test
    fun floor_isUnsetUntilADietitianSetsIt() {
        assertNull(GOAL_FLOOR_KCAL)
        assertEquals(MIN_GOAL_KCAL, base.floorKcal)
    }

    @Test
    fun defaults_matchTheHandoff() {
        assertEquals(Targets(2100, 1900, 2300, 120, 250, 70, 30, startingKcal = 2100), base)
    }

    @Test
    fun raise_movesTheTargetAndRangeUpFifty() {
        val raised = base.raise()
        assertEquals(2150, raised.energyKcal)
        assertEquals(1950, raised.minKcal)
        assertEquals(2350, raised.maxKcal)
        assertEquals(base.startingKcal, raised.startingKcal)
        assertEquals(base.proteinG, raised.proteinG)
    }

    @Test
    fun raise_stopsAtTheMaximum() {
        var targets = base
        repeat(40) { targets = targets.raise() }
        assertEquals(4100, targets.energyKcal)
        repeat(100) { targets = targets.raise() }
        assertEquals(MAX_GOAL_KCAL, targets.maxKcal)
    }

    @Test
    fun lower_goesUnderTheStartingTarget_downToTheMinimum() {
        assertTrue(base.canLower)
        assertEquals(2050, base.lower()?.energyKcal)
        var targets = base
        while (true) targets = targets.lower() ?: break
        assertEquals(MIN_GOAL_KCAL, targets.minKcal)
        assertEquals(base.startingKcal, targets.startingKcal, "where it started is a record, not a limit")
    }

    @Test
    fun lower_undoesARaise() {
        assertEquals(base, base.raise().raise().lower()?.lower())
    }

    @Test
    fun withEnergy_keepsBothEndsBetweenTheMinimumAndTheMaximum() {
        assertEquals(1500, base.withEnergy(1500).energyKcal)
        val lowest = base.withEnergy(100)
        assertEquals(MIN_GOAL_KCAL, lowest.minKcal)
        assertEquals(1000, lowest.energyKcal)
        assertEquals(MAX_GOAL_KCAL, base.withEnergy(9999).maxKcal)
    }

    @Test
    fun withEnergy_raisesFreelyAndMovesTheRange() {
        val set = base.withEnergy(2500)
        assertEquals(Targets(2500, 2300, 2700, 120, 250, 70, 30, startingKcal = 2100, startingMinKcal = 1900), set)
    }

    @Test
    fun exercise_all_addsMovementAndWorkout() {
        assertEquals(410, ExerciseAdjustment.All.activityKcal(movementKcal = 260, workoutKcal = 150))
    }

    @Test
    fun exercise_some_addsHalfRoundedHalfUp() {
        assertEquals(205, ExerciseAdjustment.Some.activityKcal(260, 150))
        assertEquals(206, ExerciseAdjustment.Some.activityKcal(261, 150))
    }

    @Test
    fun exercise_none_addsNothing() {
        assertEquals(0, ExerciseAdjustment.None.activityKcal(260, 150))
    }

    @Test
    fun exercise_defaultIsSome() {
        assertEquals(ExerciseAdjustment.Some, ExerciseAdjustment.Default)
    }

    @Test
    fun exercise_negativeReadingsNeverSubtract() {
        ExerciseAdjustment.entries.forEach { adjustment ->
            assertTrue(adjustment.activityKcal(-500, -500) >= 0, adjustment.name)
        }
        assertEquals(150, ExerciseAdjustment.All.activityKcal(-100, 150))
    }

    @Test
    fun today_addsTheAdjustmentToTargetAndRange() {
        assertEquals(EnergyRange(2510, 2310, 2710, 410), base.today(ExerciseAdjustment.All, 260, 150))
        assertEquals(EnergyRange(2305, 2105, 2505, 205), base.today(ExerciseAdjustment.Some, 260, 150))
        assertEquals(EnergyRange(2100, 1900, 2300, 0), base.today(ExerciseAdjustment.None, 260, 150))
    }

    @Test
    fun today_startsFromTheDaysOwnGoal() {
        val saturday = LocalDate(2026, 10, 24)
        val weekend = DayGroup(setOf(DayOfWeek.SATURDAY), minKcal = 2300, maxKcal = 2700)
        val rest = DayGroup(DayOfWeek.entries.toSet() - weekend.days, minKcal = 1900, maxKcal = 2300)
        val byDay = base.copy(style = TargetStyle.ByDay(listOf(rest, weekend)))

        val goal = byDay.rangeFor(saturday, PresetId.Full)

        assertEquals(EnergyRange(2600, 2400, 2800, 100), byDay.today(ExerciseAdjustment.All, 100, 0, goal))
    }

    @Test
    fun today_isNeverBelowTheBase() {
        ExerciseAdjustment.entries.forEach { adjustment ->
            listOf(0 to 0, 260 to 150, -300 to -300).forEach { (movement, workout) ->
                assertTrue(base.today(adjustment, movement, workout).targetKcal >= base.energyKcal, "$adjustment")
            }
        }
    }

    @Test
    fun tomorrow_isAlwaysTheBase() {
        val expected = EnergyRange(2100, 1900, 2300, 0)
        assertEquals(expected, base.tomorrow())
        // Whatever today looked like, tomorrow starts from the same place.
        base.today(ExerciseAdjustment.All, 2000, 2000)
        assertEquals(expected, base.tomorrow())
        assertEquals(EnergyRange(2150, 1950, 2350, 0), base.raise().tomorrow())
    }
    @Test
    fun raise_ratioMode_scalesProteinCarbsAndFatWithEnergy() {
        // The prototype's Math.round(GOAL[k] * baseT / GOAL.target) at 2150.
        val raised = base.raise(scaleMacros = true)
        assertEquals(2150, raised.energyKcal)
        assertEquals(123, raised.proteinG)
        assertEquals(256, raised.carbsG)
        assertEquals(72, raised.fatG)
    }

    @Test
    fun raise_ratioMode_leavesFiberAlone() {
        assertEquals(base.fiberG, base.raise(scaleMacros = true).fiberG)
    }

    @Test
    fun raise_gramMode_leavesTheGramsWhereTheyAre() {
        val raised = base.raise(scaleMacros = false)
        assertEquals(listOf(120, 250, 70, 30), listOf(raised.proteinG, raised.carbsG, raised.fatG, raised.fiberG))
    }

    @Test
    fun ratioMode_matchesThePrototypeAfterManySteps() {
        var targets = base
        repeat(10) { targets = targets.raise(scaleMacros = true) }
        assertEquals(2600, targets.energyKcal)
        assertEquals(Math.roundHalfUp(120 * 2600 / 2100.0), targets.proteinG)
        assertEquals(Math.roundHalfUp(250 * 2600 / 2100.0), targets.carbsG)
        assertEquals(Math.roundHalfUp(70 * 2600 / 2100.0), targets.fatG)
    }

    @Test
    fun ratioMode_raiseThenLowerBackDoesNotDriftTheSplit() {
        var targets = base
        repeat(25) { targets = targets.raise(scaleMacros = true) }
        repeat(25) { targets = requireNotNull(targets.lower(scaleMacros = true)) }
        assertEquals(base, targets.copy(ratioBasis = null))
    }

    @Test
    fun ratioMode_aGramModeStepStartsANewBasis() {
        // Raise in ratio mode, switch to grams and raise (grams stay), back to
        // ratio: the split now scales from where gram mode left it.
        val inGrams = base.raise(scaleMacros = true).raise(scaleMacros = false)
        assertNull(inGrams.ratioBasis)
        assertEquals(123, inGrams.proteinG)
        val backToRatio = inGrams.raise(scaleMacros = true)
        assertEquals(Math.roundHalfUp(123 * 2250 / 2200.0), backToRatio.proteinG)
    }

    @Test
    fun lower_ratioMode_stillStopsAtTheFloor() {
        assertNull(base.withEnergy(MIN_GOAL_KCAL).lower(scaleMacros = true))
        assertEquals(base, base.raise(scaleMacros = true).lower(scaleMacros = true)?.copy(ratioBasis = null))
    }

    private object Math {
        fun roundHalfUp(value: Double): Int = kotlin.math.floor(value + 0.5).toInt()
    }
}
