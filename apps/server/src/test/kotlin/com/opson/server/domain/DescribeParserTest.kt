package com.opson.server.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DescribeParserTest {

    private fun one(text: String): ParsedPhrase = DescribeParser.parse(text).single()

    private fun foods(text: String): List<String> = DescribeParser.parse(text).map { it.foodText }

    private fun assertPhrase(
        phrase: ParsedPhrase,
        quantity: Double?,
        food: String,
        unit: DescribeUnit? = null,
        size: PortionSize? = null,
    ) {
        assertEquals(quantity, phrase.quantity, "quantity of '${phrase.text}'")
        assertEquals(food, phrase.foodText, "food of '${phrase.text}'")
        assertEquals(unit, phrase.unit, "unit of '${phrase.text}'")
        assertEquals(size, phrase.size, "size of '${phrase.text}'")
    }

    @Test
    fun splitsOnAndCommasWithAndNewlines() {
        assertEquals(
            listOf("eggs", "toast", "coffee", "milk", "apple", "pear", "crackers", "jam"),
            foods("2 eggs and toast, coffee with milk\nan apple; a pear + crackers plus jam"),
        )
    }

    @Test
    fun splitsSentencesButNotDecimals() {
        assertEquals(listOf("coffee", "banana"), foods("So I had a coffee. And then a banana"))
        assertPhrase(one("1.5 cups rice"), 1.5, "rice", DescribeUnit.Cup)
    }

    @Test
    fun noQuantityOrSomeMeansATypicalServing() {
        assertPhrase(one("toast"), null, "toast")
        assertPhrase(one("some toast"), null, "toast")
        assertPhrase(one("a bit of cheese"), null, "cheese")
        assertPhrase(one("a little ice cream"), null, "ice cream")
        assertPhrase(one("lots of rice"), null, "rice")
    }

    @Test
    fun digitsAndDecimals() {
        assertPhrase(one("2 eggs"), 2.0, "eggs")
        assertPhrase(one("1.5 cups rice"), 1.5, "rice", DescribeUnit.Cup)
    }

    @Test
    fun fractions() {
        assertPhrase(one("1/2 cup rice"), 0.5, "rice", DescribeUnit.Cup)
        assertPhrase(one("1 1/2 cups of oatmeal"), 1.5, "oatmeal", DescribeUnit.Cup)
        assertPhrase(one("3/4 cup blueberries"), 0.75, "blueberries", DescribeUnit.Cup)
    }

    @Test
    fun unicodeFractions() {
        assertPhrase(one("½ cup yogurt"), 0.5, "yogurt", DescribeUnit.Cup)
        assertPhrase(one("1½ cups milk"), 1.5, "milk", DescribeUnit.Cup)
    }

    @Test
    fun numberWordsUpToTwentyAndADozen() {
        assertPhrase(one("one banana"), 1.0, "banana")
        assertPhrase(one("three pancakes"), 3.0, "pancakes")
        assertPhrase(one("twelve almonds"), 12.0, "almonds")
        assertPhrase(one("fifteen grapes"), 15.0, "grapes")
        assertPhrase(one("a dozen wings"), 12.0, "wings")
        assertPhrase(one("half a dozen eggs"), 6.0, "eggs")
        assertPhrase(one("two slices of toast"), 2.0, "toast", DescribeUnit.Slice)
    }

    @Test
    fun aRangeIsItsFirstNumber() {
        assertPhrase(one("2-3 cookies"), 2.0, "cookies")
        assertPhrase(one("two or three cookies"), 2.0, "cookies")
    }

    @Test
    fun articlesHalfAndCounts() {
        assertPhrase(one("a banana"), 1.0, "banana")
        assertPhrase(one("an orange"), 1.0, "orange")
        assertPhrase(one("half an avocado"), 0.5, "avocado")
        assertPhrase(one("half a bagel"), 0.5, "bagel")
        assertPhrase(one("a half cup of beans"), 0.5, "beans", DescribeUnit.Cup)
        assertPhrase(one("half sandwich"), 0.5, "sandwich")
        assertPhrase(one("a couple of cookies"), 2.0, "cookies")
        assertPhrase(one("a couple eggs"), 2.0, "eggs")
        assertPhrase(one("a few almonds"), 3.0, "almonds")
        assertPhrase(one("an 8 piece nuggets"), 8.0, "nuggets", DescribeUnit.Piece)
    }

    @Test
    fun andAHalfIsNotASplit() {
        assertPhrase(one("1 and a half cups milk"), 1.5, "milk", DescribeUnit.Cup)
        assertPhrase(one("two and a half slices pizza"), 2.5, "pizza", DescribeUnit.Slice)
    }

    @Test
    fun massUnits() {
        assertPhrase(one("200g chicken breast"), 200.0, "chicken breast", DescribeUnit.Gram)
        assertPhrase(one("150 grams of pasta"), 150.0, "pasta", DescribeUnit.Gram)
        assertPhrase(one("3 oz salmon"), 3.0, "salmon", DescribeUnit.Ounce)
        assertPhrase(one("8oz steak"), 8.0, "steak", DescribeUnit.Ounce)
        assertPhrase(one("1 ounce cheese"), 1.0, "cheese", DescribeUnit.Ounce)
        assertPhrase(one("12 fl oz cola"), 12.0, "cola", DescribeUnit.FluidOunce)
    }

    @Test
    fun householdUnitsSaidWithoutANumberAreOne() {
        assertPhrase(one("1 tbsp peanut butter"), 1.0, "peanut butter", DescribeUnit.Tablespoon)
        assertPhrase(one("2 tablespoons olive oil"), 2.0, "olive oil", DescribeUnit.Tablespoon)
        assertPhrase(one("a slice of pizza"), 1.0, "pizza", DescribeUnit.Slice)
        assertPhrase(one("3 pieces of sushi"), 3.0, "sushi", DescribeUnit.Piece)
        assertPhrase(one("a plate of pasta"), 1.0, "pasta", DescribeUnit.Plate)
        assertPhrase(one("bowl of oatmeal"), 1.0, "oatmeal", DescribeUnit.Bowl)
        assertPhrase(one("2 servings of rice"), 2.0, "rice", DescribeUnit.Serving)
        assertPhrase(one("a glass of milk"), 1.0, "milk", DescribeUnit.Glass)
        assertPhrase(one("a can of coke"), 1.0, "cola", DescribeUnit.Can)
        assertPhrase(one("a handful of almonds"), 1.0, "almonds", DescribeUnit.Handful)
        assertPhrase(one("3 strips of bacon"), 3.0, "bacon", DescribeUnit.Strip)
        assertPhrase(one("an order of fries"), 1.0, "fries", DescribeUnit.Serving)
    }

    @Test
    fun sizes() {
        assertPhrase(one("a medium apple"), 1.0, "apple", size = PortionSize.Medium)
        assertPhrase(one("2 large eggs"), 2.0, "eggs", size = PortionSize.Large)
        assertPhrase(one("one small banana"), 1.0, "banana", size = PortionSize.Small)
        assertPhrase(one("a large bowl of soup"), 1.0, "soup", DescribeUnit.Bowl, PortionSize.Large)
        assertPhrase(one("a big bowl of oatmeal"), 1.0, "oatmeal", DescribeUnit.Bowl, PortionSize.Large)
        assertPhrase(one("a tall glass of milk"), 1.0, "milk", DescribeUnit.Glass, PortionSize.Large)
    }

    @Test
    fun aStarbucksTallIsTheirSmall() {
        val phrase = one("a tall latte from starbucks")
        assertEquals(PortionSize.Small, phrase.size)
        assertEquals(Brands.Starbucks, phrase.brand)
    }

    @Test
    fun whatFollowsWithIsAnAddition() {
        val phrases = DescribeParser.parse("coffee with milk and sugar, toast")
        assertEquals(listOf(false, true, true, false), phrases.map { it.addition })
    }

    @Test
    fun foodsNamedWithAndStayWhole() {
        assertEquals(listOf("macaroni and cheese", "salad"), foods("mac and cheese and a salad"))
        assertEquals(listOf("peanut butter and jelly sandwich"), foods("a pb and j"))
        assertEquals(listOf("fish and chips"), foods("fish and chips"))
    }

    @Test
    fun aCorrectionTakesBackWhatCameBefore() {
        assertEquals(listOf("latte"), foods("a coffee no wait a latte"))
        assertEquals(listOf("latte"), foods("a coffee, no wait, a latte"))
        assertEquals(listOf("toast", "tea"), foods("toast and coffee, actually tea"))
        assertPhrase(one("two eggs actually three eggs"), 3.0, "eggs")
        assertEquals(listOf("cheeseburger"), foods("a burger I mean a cheeseburger"))
    }

    @Test
    fun whatWasLeftOutIsDropped() {
        assertEquals(listOf("toast"), foods("toast no butter"))
        assertEquals(listOf("bagel", "cream cheese"), foods("a bagel with cream cheese, without the butter"))
        assertEquals(listOf("burger"), foods("a burger, hold the fries"))
    }

    @Test
    fun fillerIsNotFood() {
        assertEquals(
            listOf("eggs", "toast", "orange juice"),
            foods("Um, I had like two eggs and uh some toast with orange juice for breakfast this morning"),
        )
        assertEquals(listOf("spaghetti"), foods("leftover spaghetti"))
        assertPhrase(one("I just had about 3 oz salmon"), 3.0, "salmon", DescribeUnit.Ounce)
    }

    @Test
    fun misheardWordsAreFixed() {
        assertEquals(listOf("big mac", "fries"), foods("a big mack and frys"))
        assertEquals(listOf("grilled nuggets"), foods("8 piece grilled nuggest"))
        assertEquals(listOf("orange juice"), foods("oj"))
        assertEquals(listOf("yogurt"), foods("yoghurt"))
    }

    @Test
    fun aRestaurantIsReadHoweverItIsSpelled() {
        listOf(
            "8 piece grilled nuggets from Chick-fil-A",
            "8 piece grilled nuggets from chick fil a",
            "8 piece grilled nuggets from chickfila",
            "8 piece grilled nuggets at chic filet",
            "chick fil a 8 piece grilled nuggets",
        ).forEach { text ->
            val phrase = one(text)
            assertEquals(Brands.ChickFilA, phrase.brand, text)
            assertPhrase(phrase, 8.0, "grilled nuggets", DescribeUnit.Piece)
        }
        assertEquals(Brands.McDonalds, one("mcdonalds big mac").brand)
        assertEquals(Brands.McDonalds, one("a big mac from mickey d's").brand)
        assertEquals(Brands.Starbucks, one("uh a latte from star bucks").brand)
    }

    @Test
    fun aRestaurantAppliesToItsWholeSentence() {
        val phrases = DescribeParser.parse("a big mac and medium fries from mcdonalds. Then a banana")
        assertEquals(listOf(Brands.McDonalds, Brands.McDonalds, null), phrases.map { it.brand })
    }

    @Test
    fun chipotleIsAPepperUnlessItIsTheRestaurant() {
        assertNull(one("chipotle mayo").brand)
        assertEquals(Brands.Chipotle, one("chipotle chicken burrito bowl").brand)
        assertEquals(Brands.Chipotle, one("tacos from chipotle").brand)
    }

    @Test
    fun ignoresCaseAndStrayPunctuation() {
        assertPhrase(one("  Two EGGS!  "), 2.0, "eggs")
        assertPhrase(one("Café au lait."), null, "café au lait")
    }

    @Test
    fun dropsPhrasesWithNoFood() {
        assertTrue(DescribeParser.parse("").isEmpty())
        assertTrue(DescribeParser.parse(" , and ,").isEmpty())
        assertTrue(DescribeParser.parse("200 g").isEmpty())
        assertTrue(DescribeParser.parse("um I had uh").isEmpty())
        assertEquals(listOf("rice"), foods("2 large, rice"))
    }

    @Test
    fun capsPhraseCount() {
        val text = (1..40).joinToString(", ") { "apple" }
        assertEquals(DescribeParser.MAX_PHRASES, DescribeParser.parse(text).size)
    }

    @Test
    fun keepsTheOriginalPhraseText() {
        assertEquals("1/2 cup rice", one("1/2 cup rice").text)
    }

    @Test
    fun labelAmountReadsTheLeadingNumber() {
        assertEquals(1.0, DescribeParser.labelAmount("1 cup, sliced"))
        assertEquals(2.0, DescribeParser.labelAmount("2 tablespoon"))
        assertEquals(0.5, DescribeParser.labelAmount("1/2 cup"))
        assertEquals(1.5, DescribeParser.labelAmount("1 1/2 cups"))
        assertEquals(1.0, DescribeParser.labelAmount("NLEA serving"))
    }

    @Test
    fun formatNumberPrefersCommonFractions() {
        assertEquals("1/2", DescribeParser.formatNumber(0.5))
        assertEquals("1 1/2", DescribeParser.formatNumber(1.5))
        assertEquals("2", DescribeParser.formatNumber(2.0))
        assertEquals("1/3", DescribeParser.formatNumber(1.0 / 3))
        assertEquals("0.2", DescribeParser.formatNumber(0.2))
    }

    @Test
    fun unitWordAloneIsNotAFood() {
        assertNull(DescribeParser.parse("a cup").firstOrNull())
    }
}
