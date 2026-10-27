package com.opson.server.data

import com.opson.server.domain.DescribeConfidence
import com.opson.server.domain.DescribeUnit
import com.opson.server.domain.Food
import com.opson.server.domain.Portion
import com.opson.server.testing.FakeFoodCatalog
import com.opson.server.testing.FakeFoodCatalog.Companion.food
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The matching and amount rules against a small in-memory catalog. What real
 * phrases become against the real catalog is `DescribeFixturesTest`.
 */
class CatalogFoodDescriberTest {

    private val egg = food(
        "egg",
        kcal = 143.0,
        portions = listOf(Portion("1 large", 50.0), Portion("1 medium", 44.0), Portion("1 cup", 243.0)),
    )
    private val rice = food("rice", kcal = 130.0, portions = listOf(Portion("1 cup", 158.0), Portion("2 tablespoon", 20.0)))
    private val salt = food("salt", kcal = 0.0)
    private val frenchToast = food("French toast, NFS", kcal = 230.0, portions = listOf(Portion("1 slice, any size", 65.0)))
    private val toast = food(
        "Bread, white, toasted",
        kcal = 290.0,
        portions = listOf(Portion("1 small or thin/very thin slice", 22.0), Portion("1 medium or regular slice", 25.0)),
    )
    private val wheatToast = food("Bread, whole wheat, toasted", kcal = 270.0, portions = listOf(Portion("1 medium or regular slice", 33.0)))
    private val cookedEgg = food("Egg, whole, cooked, NS as to cooking method", kcal = 176.0, portions = listOf(Portion("1 egg", 50.0)))
    private val scrambled = food("Egg omelet or scrambled egg, NS as to fat", kcal = 190.0, portions = listOf(Portion("1 egg", 55.0)))
    private val bacon = food("Bacon, NS as to type of meat, cooked", kcal = 480.0, portions = listOf(Portion("1 medium slice (yield after cooking)", 8.0)))
    private val coffee = food("Coffee, brewed", kcal = 1.0, portions = listOf(Portion("1 cup (8 fl oz)", 240.0), Portion("1 large", 600.0)))
    private val milk = food("Milk, NFS", kcal = 52.0, portions = listOf(Portion("1 cup", 244.0), Portion("1 fl oz", 30.5)))
    private val driedCorn = food("Corn, dried", kcal = 350.0, portions = listOf(Portion("1 cup", 100.0)))
    private val corn = food("Corn, raw", kcal = 86.0, portions = listOf(Portion("1 ear", 90.0)))
    private val chickFilA = food("CHICK-FIL-A, chicken sandwich", kcal = 250.0, portions = listOf(Portion("1 sandwich", 187.0)))
    private val chickenSandwich = food("Chicken fillet sandwich, NFS", kcal = 240.0, portions = listOf(Portion("1 sandwich", 180.0)))
    private val whopper = food("Whopper (Burger King)", kcal = 225.0, portions = listOf(Portion("1 hamburger", 270.0)))
    private val whopperCheese = food("Whopper with cheese (Burger King)", kcal = 268.0, portions = listOf(Portion("1 cheeseburger", 290.0)))
    private val cheese = food("Cheese, NFS", kcal = 380.0, portions = listOf(Portion("1 slice", 21.0)))
    private val nuggets = food("Chicken nuggets, NFS", kcal = 307.0, portions = listOf(Portion("1 nugget", 16.0)))
    private val grilledShrimp = food("Shrimp, grilled", kcal = 120.0, portions = listOf(Portion("1 shrimp", 8.0)))
    private val school = food("Juice, apple", kcal = 46.0, portions = listOf(Portion("1 individual school container", 124.0), Portion("1 cup", 248.0)))

    private val catalog = FakeFoodCatalog(
        listOf(
            egg, rice, salt, frenchToast, toast, wheatToast, cookedEgg, scrambled, bacon, coffee, milk, driedCorn, corn,
            chickFilA, chickenSandwich, whopper, whopperCheese, cheese, nuggets, grilledShrimp, school,
        ),
    )
    private val describer = CatalogFoodDescriber(catalog)

    @Test
    fun bareToastIsPlainToastNotFrenchToast() = runTest {
        val item = describer.describe("some toast").single()
        assertEquals(toast.id, item.foodId)
        assertEquals("1 medium or regular slice", item.portionLabel)
        assertEquals(25.0, item.grams)
        assertEquals(1.0, item.quantity)
        assertEquals("1 medium or regular slice", item.amountText)
        // The plain food carries the full food and what to swap to.
        assertEquals(toast, item.food)
        assertTrue(wheatToast in item.alternatives, "alternatives ${item.alternatives.map(Food::name)}")
    }

    @Test
    fun frenchToastIsHonouredWhenSaid() = runTest {
        assertEquals(frenchToast.id, describer.describe("french toast").single().foodId)
    }

    @Test
    fun eggsWithNoNumberAreOneCookedEgg() = runTest {
        val item = describer.describe("eggs").single()
        assertEquals(cookedEgg.id, item.foodId)
        assertEquals(1.0, item.quantity)
        assertEquals(50.0, item.grams)
        assertEquals(listOf(scrambled), item.alternatives.filter { it == scrambled })
    }

    @Test
    fun aCountCountsTheFoodsOwnUnit() = runTest {
        val item = describer.describe("two eggs").single()
        assertEquals(cookedEgg.id, item.foodId)
        assertEquals(2.0, item.quantity)
        assertEquals(100.0, item.grams)
        assertEquals("2 eggs", item.amountText)
    }

    @Test
    fun aServingOfSeveralIsTheEverydayDefault() = runTest {
        val item = describer.describe("bacon").single()
        assertEquals(2.0, item.quantity)
        assertEquals(16.0, item.grams)
        assertEquals("2 medium slices", item.amountText)
    }

    @Test
    fun anAdditionGetsTheSmallerAmount() = runTest {
        val items = describer.describe("a coffee with milk")
        assertEquals(listOf(coffee.id, milk.id), items.map { it.foodId })
        assertEquals(240.0, items[0].grams)
        assertEquals("1 fl oz", items[1].portionLabel)
        assertEquals(30.5, items[1].grams)
        // Milk said on its own is a glass of it.
        assertEquals(244.0, describer.describe("milk").single().grams)
    }

    @Test
    fun aSizeWordPicksTheMatchingPortion() = runTest {
        val item = describer.describe("a large coffee").single()
        assertEquals("1 large", item.portionLabel)
        assertEquals(600.0, item.grams)
    }

    @Test
    fun aNameAddingAPreparationNobodySaidLoses() = runTest {
        assertEquals(corn.id, describer.describe("corn").single().foodId)
        assertEquals(driedCorn.id, describer.describe("dried corn").single().foodId)
    }

    @Test
    fun aNamedRestaurantsOwnFoodWins() = runTest {
        val item = describer.describe("a chicken sandwich from chick fil a").single()
        assertEquals(chickFilA.id, item.foodId)
        assertTrue(chickenSandwich in item.alternatives)
    }

    @Test
    fun aRestaurantTheCatalogLacksFallsBackToTheGenericFood() = runTest {
        assertEquals(chickenSandwich.id, describer.describe("a chicken sandwich from wendys").single().foodId)
    }

    @Test
    fun aFoodNamedWithWithStaysOneLine() = runTest {
        val item = describer.describe("a whopper with cheese").single()
        assertEquals(whopperCheese.id, item.foodId)
        assertEquals("a whopper with cheese", item.text)
    }

    @Test
    fun anEverydayFoodAfterWithStaysItsOwnLine() = runTest {
        assertEquals(listOf(rice.id, cheese.id), describer.describe("rice with cheese").map { it.foodId })
    }

    @Test
    fun aRunOfEverydayFoodsWithNoAndIsSplit() = runTest {
        val items = describer.describe("two eggs bacon toast")
        assertEquals(listOf(cookedEgg.id, bacon.id, toast.id), items.map { it.foodId })
        assertEquals(listOf(2.0, 2.0, 1.0), items.map { it.quantity })
    }

    @Test
    fun wordsNothingCarriesFallBackToTheEverydayFoodTheyEndIn() = runTest {
        val item = describer.describe("8 piece grilled nuggets").single()
        assertEquals(nuggets.id, item.foodId)
        assertEquals(8.0, item.quantity)
        assertEquals(128.0, item.grams)
        assertEquals(DescribeConfidence.Low, item.confidence)
    }

    @Test
    fun bookkeepingPortionsAreNeverTheServing() = runTest {
        assertEquals("1 cup", describer.describe("apple juice").single().portionLabel)
    }

    @Test
    fun unitMatchingAPortionLabelUsesIt() = runTest {
        val item = describer.describe("1/2 cup rice").single()
        assertEquals("1 cup", item.portionLabel)
        assertEquals(79.0, item.grams)
        assertEquals(DescribeUnit.Cup, item.unit)
        assertEquals("1/2 cup", item.amountText)
    }

    @Test
    fun portionLabelsWithAnAmountAreDividedPerUnit() = runTest {
        val item = describer.describe("1 tbsp rice").single()
        assertEquals("2 tablespoon", item.portionLabel)
        assertEquals(10.0, item.grams)
    }

    @Test
    fun gramsAreUsedAsGiven() = runTest {
        val item = describer.describe("150 g rice").single()
        assertEquals(150.0, item.grams)
        assertEquals(195.0, item.nutrients?.kcal)
        assertNull(item.portionLabel)
        assertEquals(DescribeConfidence.High, item.confidence)
    }

    @Test
    fun ouncesConvertToGrams() = runTest {
        assertEquals(56.7, describer.describe("2 oz rice").single().grams)
    }

    @Test
    fun aBowlIsACupAndAHalfWhenTheFoodHasNoBowl() = runTest {
        val item = describer.describe("a bowl of rice").single()
        assertEquals("1 cup", item.portionLabel)
        assertEquals(237.0, item.grams)
        assertEquals(1.5, item.quantity)
    }

    @Test
    fun foodWithNoPortionsIsAHundredGramGuess() = runTest {
        val item = describer.describe("salt").single()
        assertEquals(100.0, item.grams)
        assertEquals(DescribeConfidence.Low, item.confidence)
    }

    @Test
    fun noMatchStillReturnsTheItem() = runTest {
        val item = describer.describe("dragonfruit smoothie").single()
        assertNull(item.foodId)
        assertNull(item.nutrients)
        assertNull(item.food)
        assertEquals("dragonfruit smoothie", item.name)
        assertEquals(DescribeConfidence.Low, item.confidence)
    }

    @Test
    fun describesEveryPhraseWithoutRecordingSearches() = runTest {
        val items = describer.describe("2 eggs and toast")
        assertEquals(listOf(cookedEgg.id, toast.id), items.map { it.foodId })
        assertTrue(catalog.searches.none { (_, recorded) -> recorded })
    }
}
