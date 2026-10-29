package com.opson.server.domain

import java.util.UUID
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The Monster bug, 2026-10-29: scanning Monster Energy Zero Ultra (UPC 070847012474) showed 0 kcal
 * where the can says 10. The barcode was answered by USDA Branded (tier 2 beats Open Food Facts),
 * and FoodData Central's record for it (fdcId 2680587) has the label's "Calories 0" for an 8 fl oz
 * (240 ml) serving, which a US label may print for anything under 5 kcal, scaled to 0 kcal per
 * 100 ml beside 0.83 g of carbs. Its serving was stated in ml, so it had no grams and the app could
 * only offer 100 g. Both numbers below are that record as the importer stores it.
 */
class FoodEnergyTest {

    private fun food(
        source: CatalogSource,
        kcal: Double?,
        protein: Double? = 0.0,
        carbs: Double? = 0.0,
        fat: Double? = 0.0,
        portions: List<Portion> = emptyList(),
    ) = Food(
        id = UUID.randomUUID(),
        source = source,
        sourceId = "x",
        name = "x",
        brand = null,
        barcode = null,
        per100g = Nutrients(kcal = kcal, protein = protein, carbs = carbs, fat = fat),
        micros = emptyMap(),
        portions = portions,
    )

    private val brandedMonster = food(
        CatalogSource.UsdaBranded,
        kcal = 0.0,
        carbs = 0.83,
        portions = listOf(Portion("8.0 fl oz (240 ml)", null)),
    )

    /** Open Food Facts' copy (0070847012474): 2 kcal per 100 ml, a 473 ml can. */
    private val offMonster = food(
        CatalogSource.OpenFoodFacts,
        kcal = 2.0,
        carbs = 1.268,
        portions = listOf(Portion("1 Can (16 fl oz)", 473.176)),
    )

    private fun kcalFor(food: Food, amount: Double) = (food.per100g.kcal!! * amount / 100).roundToInt()

    @Test
    fun theMonsterRowFromUsdaBrandedIsNoLongerServedAsZero() {
        assertEquals(setOf(EnergyFlag.ZeroWithMacros), FoodEnergy.flags(brandedMonster))

        val served = ServedFood.of(brandedMonster, correction = null)

        assertEquals(setOf(EnergyFlag.ZeroWithMacros), served.energyFlags)
        // The macros say 3.3 kcal per 100 ml; the label's 0 says under 5 per 240 ml, so 2.1.
        assertEquals(2.1, served.per100g.kcal)
        // The serving is in ml, and the numbers are per 100 ml, so 240 ml is what they scale by.
        assertEquals(Portion("8.0 fl oz (240 ml)", 240.0), served.portions.single())
        assertEquals(5, kcalFor(served, 240.0))
        assertEquals(10, kcalFor(served, 473.0), "a 16 fl oz can, as printed on it")
    }

    @Test
    fun theOpenFoodFactsCopyOfTheSameCanPasses() {
        assertTrue(FoodEnergy.flags(offMonster).isEmpty())
        assertSame(offMonster, ServedFood.of(offMonster, correction = null))
        assertEquals(9, kcalFor(offMonster, 473.176))
    }

    @Test
    fun aScanPrefersARowWhoseEnergyHoldsUpOverABetterTierCaughtOut() {
        val branded = ServedFood.of(brandedMonster, null)
        val off = ServedFood.of(offMonster, null)
        assertSame(off, BarcodeChoice.best(listOf(branded, off)))
        // With nothing better the Branded row still answers, with its estimate.
        assertSame(branded, BarcodeChoice.best(listOf(branded)))
        // A Branded row that is fine wins as it always did.
        val cola = food(CatalogSource.UsdaBranded, kcal = 42.0, carbs = 10.6)
        assertSame(cola, BarcodeChoice.best(listOf(cola, off)))
    }

    @Test
    fun aZeroTheMacrosAgreeWithStaysZero() {
        val dietCola = food(CatalogSource.UsdaBranded, kcal = 0.0, carbs = 0.0, portions = listOf(Portion("1 can (355 ml)", null)))
        assertTrue(FoodEnergy.flags(dietCola).isEmpty())
        assertEquals(0.0, ServedFood.of(dietCola, null).per100g.kcal)
        // Tea with a trace of carbs: 0.3 g per 100 ml is under 5 kcal in its serving, so 0 is honest.
        val tea = food(CatalogSource.UsdaBranded, kcal = 0.0, carbs = 0.3, portions = listOf(Portion("8 fl oz (240 ml)", null)))
        assertTrue(FoodEnergy.flags(tea).isEmpty())
    }

    @Test
    fun withoutAServingTheCheckJudgesPer100() {
        val bare = food(CatalogSource.OpenFoodFacts, kcal = 0.0, carbs = 2.0)
        assertEquals(setOf(EnergyFlag.ZeroWithMacros), FoodEnergy.flags(bare))
        // 8 kcal from the macros, held under the 5 a printed 0 allows in the amount it was for.
        assertEquals(5.0, ServedFood.of(bare, null).per100g.kcal)
    }

    /**
     * Sweetened with erythritol: FoodData Central has Reed's Zero Sugar Cola at 0 kcal and 5.07 g
     * of carbs per 100 ml, a 355 ml can. 4/4/9 makes that 72 kcal a can, which the label rules out.
     */
    @Test
    fun sugarAlcoholCarbsCannotTurnALabelledZeroIntoMuchMore() {
        val cola = food(CatalogSource.UsdaBranded, kcal = 0.0, carbs = 5.07, portions = listOf(Portion("1 can (355 ml)", null)))
        val served = ServedFood.of(cola, null)
        assertEquals(setOf(EnergyFlag.ZeroWithMacros), served.energyFlags)
        assertEquals(5, kcalFor(served, 355.0))
    }

    @Test
    fun missingEnergyIsWorkedOutFromTheMacrosOrLeftUnknown() {
        val noEnergy = food(CatalogSource.OpenFoodFacts, kcal = null, protein = 10.0, carbs = 20.0, fat = 5.0)
        val served = ServedFood.of(noEnergy, null)
        assertEquals(165.0, served.per100g.kcal)
        assertEquals(setOf(EnergyFlag.FromMacros), served.energyFlags)

        val nothing = food(CatalogSource.OpenFoodFacts, kcal = null, protein = 10.0, carbs = null, fat = 5.0)
        assertNull(ServedFood.of(nothing, null).per100g.kcal, "unknown stays unknown, never 0")
    }

    @Test
    fun energyWithNoMacrosIsFlaggedButLeftAlone() {
        val vodka = food(CatalogSource.UsdaSr, kcal = 231.0)
        val served = ServedFood.of(vodka, null)
        assertEquals(setOf(EnergyFlag.EnergyWithoutMacros), served.energyFlags)
        assertEquals(231.0, served.per100g.kcal)
        // Never preferred over a row that holds up, but still served when it is all there is.
        assertSame(served, BarcodeChoice.best(listOf(served)))
    }

    @Test
    fun aReviewersCaloriesAreNotSecondGuessed() {
        val correction = FoodCorrection(
            id = UUID.randomUUID(),
            foodId = brandedMonster.id,
            createdAt = kotlin.time.Instant.parse("2026-10-29T09:00:00Z"),
            createdBy = "elijah",
            values = CorrectionValues(kcal = 0.0, servingLabel = "1 can (473 ml)", servingGrams = 473.0),
            base = FoodSnapshot.of(brandedMonster),
        )
        val served = ServedFood.of(brandedMonster, correction)
        assertEquals(0.0, served.per100g.kcal)
        assertTrue(served.energyFlags.isEmpty())
        assertEquals(Portion("1 can (473 ml)", 473.0), served.portions.first())
        assertEquals(correction.id, served.correction?.id)
    }

    @Test
    fun portionsReadMillilitresButOnlyFillGramsWhereTheNumbersArePer100Ml() {
        assertEquals(355.0, Portions.millilitres("1 can (355 ml)"))
        assertEquals(240.0, Portions.millilitres("8.0 fl oz (240 ml)"))
        assertEquals(2.5, Portions.millilitres("1 shot (2,5 ml)"))
        assertNull(Portions.millilitres("1 bottle"))

        val off = food(CatalogSource.OpenFoodFacts, kcal = 40.0, portions = listOf(Portion("1 glass (200 ml)", null)))
        assertEquals(listOf(Portion("1 glass (200 ml)", 200.0)), Portions.served(off).portions)
        val generic = food(CatalogSource.UsdaSr, kcal = 40.0, portions = listOf(Portion("1 cup (240 ml)", null)))
        assertSame(generic, Portions.served(generic), "a generic food's numbers are per 100 g, so ml is not its amount")
        val branded = food(CatalogSource.UsdaBranded, kcal = 40.0, portions = listOf(Portion("1 bottle", null)))
        assertEquals(listOf(Portion("1 bottle", null)), Portions.served(branded).portions)
    }
}
