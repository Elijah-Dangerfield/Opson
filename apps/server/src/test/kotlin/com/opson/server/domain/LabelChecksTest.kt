package com.opson.server.domain

import kotlin.math.abs
import kotlin.math.round
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The label checks, case by case and as properties over random labels. The
 * app's `LabelChecksTest` in `:libraries:foods` holds the same cases, so the
 * two copies of the rules cannot drift apart without one of them going red.
 */
class LabelChecksTest {

    private fun facts(
        perServing: Map<NutrientKey, Double> = emptyMap(),
        per100: Map<NutrientKey, Double> = emptyMap(),
        size: Double? = 100.0,
        unit: ServingUnit? = ServingUnit.Gram,
        basis: CarbBasis = CarbBasis.Us,
        name: String? = "Granola bar",
    ) = LabelFacts(name = name, servingSize = size, servingUnit = unit, basis = basis, perServing = perServing, per100 = per100)

    private fun macros(kcal: Double, protein: Double, carbs: Double, fat: Double, vararg more: Pair<NutrientKey, Double>) = mapOf(
        NutrientKey.Energy to kcal,
        NutrientKey.Protein to protein,
        NutrientKey.Carbs to carbs,
        NutrientKey.Fat to fat,
        *more,
    )

    private fun codes(facts: LabelFacts) = LabelChecks.check(facts).map { it.code }

    @Test
    fun aConsistentLabelPassesEveryCheck() {
        // The granola bar fixture: 4*4 + 9*7 + 4*29 = 195 against 190 on the label.
        val bar = facts(perServing = macros(190.0, 4.0, 29.0, 7.0, NutrientKey.Fiber to 3.0, NutrientKey.Sugars to 12.0, NutrientKey.Sodium to 150.0), size = 40.0)
        assertEquals(emptyList(), LabelChecks.check(bar))
    }

    @Test
    fun energyFortyKcalOverTheMacrosIsAMismatchWithTheMacrosFigure() {
        val check = LabelChecks.check(facts(perServing = macros(270.0, 10.0, 35.0, 6.0))).single()
        assertEquals(LabelCheckCode.EnergyMacrosMismatch, check.code)
        assertEquals(234.0, check.expected)
        assertEquals(270.0, check.actual)
        assertEquals(listOf("energy_kcal", "protein_g", "carbs_g", "fat_g"), check.fields)
    }

    @Test
    fun theToleranceIsFifteenKcalOrTwelvePercent() {
        assertEquals(15.0, LabelChecks.energyTolerance(50.0))
        assertEquals(30.0, LabelChecks.energyTolerance(250.0))
        assertEquals(emptyList(), codes(facts(perServing = macros(250.0, 10.0, 35.0, 6.0))))
        assertEquals(emptyList(), codes(facts(perServing = macros(15.0, 1.0, 0.0, 0.0))))
        assertEquals(listOf(LabelCheckCode.EnergyMacrosMismatch), codes(facts(perServing = macros(20.0, 1.0, 0.0, 0.0))))
    }

    @Test
    fun kilojoulesReadAsKcalAreCalledOutWithTheConvertedFigure() {
        val check = LabelChecks.check(facts(per100 = macros(1590.0, 8.0, 80.0, 3.0), basis = CarbBasis.Eu)).single()
        assertEquals(LabelCheckCode.EnergyLooksLikeKj, check.code)
        assertEquals(380.0, round(check.expected!!))
    }

    @Test
    fun fiberCountsAtTwoKcalOnAUsPanelWhereCarbsIncludeIt() {
        val label = macros(120.0, 5.0, 30.0, 2.0, NutrientKey.Fiber to 20.0)
        assertEquals(emptyList(), codes(facts(perServing = label, basis = CarbBasis.Us)))
        // On an EU panel the same carbs exclude the fiber, so the label cannot be right.
        assertEquals(listOf(LabelCheckCode.EnergyMacrosMismatch), codes(facts(perServing = label, basis = CarbBasis.Eu)))
        assertEquals(emptyList(), codes(facts(perServing = label, basis = CarbBasis.Unknown)))
    }

    @Test
    fun anEuPanelAddsFiberOnTopOfCarbs() {
        val yogurt = macros(146.0, 10.7, 17.0, 3.8, NutrientKey.Fiber to 0.3)
        assertEquals(emptyList(), codes(facts(perServing = yogurt, size = 150.0, basis = CarbBasis.Eu)))
    }

    @Test
    fun sugarAlcoholsCountAtTwoPointFour() {
        val bar = macros(115.0, 2.0, 20.0, 5.0, NutrientKey.SugarAlcohols to 10.0)
        assertEquals(emptyList(), codes(facts(perServing = bar)))
    }

    @Test
    fun impossibleAmountsAreNamed() {
        val tooFat = LabelChecks.check(facts(perServing = macros(450.0, 0.0, 0.0, 50.0), size = 40.0)).first()
        assertEquals(LabelCheckCode.ImpossibleValue, tooFat.code)
        assertEquals(listOf("fat_g"), tooFat.fields)

        val sugars = LabelChecks.check(facts(perServing = macros(190.0, 4.0, 29.0, 7.0, NutrientKey.Sugars to 35.0))).single()
        assertEquals(listOf("sugars_g"), sugars.fields)

        val negative = LabelChecks.check(facts(perServing = macros(190.0, -4.0, 29.0, 7.0))).first()
        assertTrue("protein_g" in negative.fields)

        val sum = LabelChecks.check(facts(per100 = macros(830.0, 40.0, 50.0, 20.0), size = null, unit = null)).first { it.code == LabelCheckCode.ImpossibleValue }
        assertEquals(setOf("protein_g", "fat_g", "carbs_g"), sum.fields.toSet())
    }

    @Test
    fun aDrinkMayWeighMoreThanItsMillilitres() {
        val syrup = facts(perServing = macros(260.0, 0.0, 65.0, 0.0, NutrientKey.Sugars to 65.0), size = 50.0, unit = ServingUnit.Milliliter)
        assertEquals(emptyList(), codes(syrup))
    }

    @Test
    fun perServingAndPer100DisagreeingIsNamedByNutrient() {
        val check = LabelChecks.check(
            facts(perServing = mapOf(NutrientKey.Energy to 150.0), per100 = mapOf(NutrientKey.Energy to 400.0), size = 30.0, name = "x"),
        ).first { it.code == LabelCheckCode.ServingMismatch }
        assertEquals(listOf("energy_kcal"), check.fields)
        assertEquals(120.0, check.expected)
    }

    @Test
    fun everythingOffByTheSameFactorMeansTheServingSizeIsWrong() {
        val per100 = macros(400.0, 10.0, 60.0, 12.0)
        val perServing = per100.mapValues { it.value * 0.45 }
        val check = LabelChecks.check(facts(perServing = perServing, per100 = per100, size = 30.0)).single { it.code == LabelCheckCode.ServingMismatch }
        assertEquals(listOf("serving_size"), check.fields)
        assertEquals(45.0, round(check.expected!!))
    }

    @Test
    fun sodiumAThousandTimesOffIsCaught() {
        val grams = LabelChecks.check(facts(perServing = macros(190.0, 4.0, 29.0, 7.0, NutrientKey.Sodium to 0.15))).single()
        assertEquals(LabelCheckCode.SodiumUnit, grams.code)
        assertEquals(150.0, grams.expected)

        val milligrams = LabelChecks.check(facts(per100 = mapOf(NutrientKey.Sodium to 50_000.0), size = null, unit = null, name = "x"))
        assertTrue(milligrams.any { it.code == LabelCheckCode.SodiumUnit && it.expected == 50.0 })
    }

    @Test
    fun missingRequiredFieldsAreListed() {
        val check = LabelChecks.check(facts(perServing = mapOf(NutrientKey.Protein to 4.0), name = " ", size = null)).first()
        assertEquals(LabelCheckCode.MissingRequired, check.code)
        assertEquals(listOf("name", "serving_size", "energy_kcal", "carbs_g", "fat_g"), check.fields)
        assertTrue(check.code.blocking)
    }

    @Test
    fun aNutrientGivenEitherWayCountsAsPresent() {
        val per100Only = facts(per100 = macros(380.0, 8.0, 80.0, 3.0), size = 30.0, basis = CarbBasis.Eu)
        assertEquals(emptyList(), codes(per100Only))
        assertEquals(114.0, per100Only.perServing(NutrientKey.Energy))
        assertEquals(380.0, per100Only.completed().per100[NutrientKey.Energy])
        assertEquals(2.4, per100Only.completed().perServing[NutrientKey.Protein]!!, 1e-9)
    }

    @Test
    fun kilojoulesAndKcalPrintedTogetherAgreeWithinRounding() {
        assertEquals(null, LabelChecks.unitsDisagree(kcal = 146.0, kj = 615.0))
        assertEquals(LabelCheckCode.EnergyUnitsDisagree, LabelChecks.unitsDisagree(kcal = 200.0, kj = 615.0)?.code)
    }

    // ---- properties, over random labels with a fixed seed ----

    @Test
    fun anyLabelWithinToleranceOfTheMacrosPasses() {
        val random = Random(SEED)
        repeat(RUNS) {
            val protein = random.nextDouble(0.0, 30.0)
            val carbs = random.nextDouble(0.0, 40.0)
            val fat = random.nextDouble(0.0, 25.0)
            val atwater = 4 * protein + 4 * carbs + 9 * fat
            val declared = atwater + random.nextDouble(-0.9, 0.9) * LabelChecks.energyTolerance(atwater) * 0.8
            val label = facts(perServing = macros(declared.coerceAtLeast(0.0), protein, carbs, fat), size = 100.0)
            assertTrue(LabelChecks.check(label).none { it.code == LabelCheckCode.EnergyMacrosMismatch }, "flagged $label")
        }
    }

    @Test
    fun anyLabelWellOutsideToleranceFailsTheEnergyCheck() {
        val random = Random(SEED + 1)
        repeat(RUNS) {
            val protein = random.nextDouble(0.0, 30.0)
            val carbs = random.nextDouble(0.0, 40.0)
            val fat = random.nextDouble(0.0, 25.0)
            val atwater = 4 * protein + 4 * carbs + 9 * fat
            val declared = if (random.nextBoolean()) atwater * 1.2 + 20 else (atwater * 0.8 - 20).coerceAtLeast(0.0)
            if (abs(declared - atwater) <= LabelChecks.energyTolerance(declared)) return@repeat
            val label = facts(perServing = macros(declared, protein, carbs, fat), size = 100.0)
            val energy = LabelChecks.check(label).filter {
                it.code == LabelCheckCode.EnergyMacrosMismatch || it.code == LabelCheckCode.EnergyLooksLikeKj
            }
            assertEquals(1, energy.size, "not flagged: $label")
        }
    }

    @Test
    fun anyKcalFigureThatIsTheMacrosInKilojoulesIsCalledKilojoules() {
        val random = Random(SEED + 2)
        repeat(RUNS) {
            val protein = random.nextDouble(2.0, 20.0)
            val carbs = random.nextDouble(10.0, 70.0)
            val fat = random.nextDouble(0.0, 9.0)
            val atwater = 4 * protein + 4 * carbs + 9 * fat
            val label = facts(per100 = macros(round(atwater * LabelChecks.KJ_PER_KCAL), protein, carbs, fat))
            val check = LabelChecks.check(label).single()
            assertEquals(LabelCheckCode.EnergyLooksLikeKj, check.code, "$label")
            assertTrue(abs(check.expected!! - atwater) <= LabelChecks.energyTolerance(atwater))
        }
    }

    @Test
    fun anyPerServingColumnWorkedOutFromPer100AsALabelRoundsItPasses() {
        val random = Random(SEED + 3)
        repeat(RUNS) {
            val size = round(random.nextDouble(10.0, 250.0))
            val per100 = mapOf(
                NutrientKey.Energy to round(random.nextDouble(0.0, 600.0)),
                NutrientKey.Sugars to round(random.nextDouble(0.0, 50.0) * 10) / 10,
                NutrientKey.Sodium to round(random.nextDouble(0.0, 2000.0)),
            )
            // Rounded as a label prints them: energy and sodium to whole numbers, grams to the gram.
            val perServing = per100.mapValues { (_, value) -> round(value * size / 100) }
            val label = facts(perServing = perServing, per100 = per100, size = size, name = "x")
            assertTrue(LabelChecks.check(label).none { it.code == LabelCheckCode.ServingMismatch }, "flagged $label")
        }
    }

    private companion object {
        const val SEED = 20261027
        const val RUNS = 500
    }
}
