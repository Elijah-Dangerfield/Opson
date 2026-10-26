package com.opson.server.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DescribeParserTest {

    private fun one(text: String): ParsedPhrase = DescribeParser.parse(text).single()

    private fun assertPhrase(
        phrase: ParsedPhrase,
        quantity: Double,
        food: String,
        unit: DescribeUnit? = null,
        size: PortionSize? = null,
    ) {
        assertEquals(quantity, phrase.quantity, 1e-9, "quantity of '${phrase.text}'")
        assertEquals(food, phrase.foodText, "food of '${phrase.text}'")
        assertEquals(unit, phrase.unit, "unit of '${phrase.text}'")
        assertEquals(size, phrase.size, "size of '${phrase.text}'")
    }

    @Test
    fun splitsOnAndCommasWithAndNewlines() {
        val phrases = DescribeParser.parse("2 eggs and toast, coffee with milk\nan apple; a pear + crackers plus jam")
        assertEquals(listOf("eggs", "toast", "coffee", "milk", "apple", "pear", "crackers", "jam"), phrases.map { it.foodText })
    }

    @Test
    fun noQuantityMeansOne() {
        assertPhrase(one("toast"), 1.0, "toast")
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
    fun numberWordsOneToTwelve() {
        assertPhrase(one("one banana"), 1.0, "banana")
        assertPhrase(one("three pancakes"), 3.0, "pancakes")
        assertPhrase(one("twelve almonds"), 12.0, "almonds")
        assertPhrase(one("two slices of toast"), 2.0, "toast", DescribeUnit.Slice)
    }

    @Test
    fun articlesAndHalf() {
        assertPhrase(one("a banana"), 1.0, "banana")
        assertPhrase(one("an orange"), 1.0, "orange")
        assertPhrase(one("half an avocado"), 0.5, "avocado")
        assertPhrase(one("half a bagel"), 0.5, "bagel")
        assertPhrase(one("a half cup of beans"), 0.5, "beans", DescribeUnit.Cup)
        assertPhrase(one("half sandwich"), 0.5, "sandwich")
    }

    @Test
    fun andAHalfIsNotASplit() {
        assertPhrase(one("1 and a half cups milk"), 1.5, "milk", DescribeUnit.Cup)
        assertPhrase(one("two and a half slices pizza"), 2.5, "pizza", DescribeUnit.Slice)
    }

    @Test
    fun aCoupleOf() {
        assertPhrase(one("a couple of cookies"), 2.0, "cookies")
    }

    @Test
    fun massUnits() {
        assertPhrase(one("200g chicken breast"), 200.0, "chicken breast", DescribeUnit.Gram)
        assertPhrase(one("150 grams of pasta"), 150.0, "pasta", DescribeUnit.Gram)
        assertPhrase(one("3 oz salmon"), 3.0, "salmon", DescribeUnit.Ounce)
        assertPhrase(one("8oz steak"), 8.0, "steak", DescribeUnit.Ounce)
        assertPhrase(one("1 ounce cheese"), 1.0, "cheese", DescribeUnit.Ounce)
    }

    @Test
    fun householdUnits() {
        assertPhrase(one("1 tbsp peanut butter"), 1.0, "peanut butter", DescribeUnit.Tablespoon)
        assertPhrase(one("2 tablespoons olive oil"), 2.0, "olive oil", DescribeUnit.Tablespoon)
        assertPhrase(one("1 tsp honey"), 1.0, "honey", DescribeUnit.Teaspoon)
        assertPhrase(one("a slice of pizza"), 1.0, "pizza", DescribeUnit.Slice)
        assertPhrase(one("3 pieces of sushi"), 3.0, "sushi", DescribeUnit.Piece)
        assertPhrase(one("a plate of pasta"), 1.0, "pasta", DescribeUnit.Plate)
        assertPhrase(one("a bowl of oatmeal"), 1.0, "oatmeal", DescribeUnit.Bowl)
        assertPhrase(one("2 servings of rice"), 2.0, "rice", DescribeUnit.Serving)
    }

    @Test
    fun sizes() {
        assertPhrase(one("a medium apple"), 1.0, "apple", size = PortionSize.Medium)
        assertPhrase(one("2 large eggs"), 2.0, "eggs", size = PortionSize.Large)
        assertPhrase(one("one small banana"), 1.0, "banana", size = PortionSize.Small)
        assertPhrase(one("a large bowl of soup"), 1.0, "soup", DescribeUnit.Bowl, PortionSize.Large)
    }

    @Test
    fun ignoresCaseAndStrayPunctuation() {
        assertPhrase(one("  Two EGGS!  "), 2.0, "eggs")
        assertPhrase(one("Café au lait."), 1.0, "café au lait")
    }

    @Test
    fun dropsPhrasesWithNoFood() {
        assertTrue(DescribeParser.parse("").isEmpty())
        assertTrue(DescribeParser.parse(" , and ,").isEmpty())
        assertTrue(DescribeParser.parse("200 g").isEmpty())
        assertEquals(listOf("rice"), DescribeParser.parse("2 large, rice").map { it.foodText })
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
