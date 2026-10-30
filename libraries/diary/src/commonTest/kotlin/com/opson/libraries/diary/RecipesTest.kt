package com.opson.libraries.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A recipe logs a serving as one entry, and its links read back to its code. */
class RecipesTest {

    private val beans = LogDraft(
        name = "Black beans", precision = Precision.Weighed, grams = 800.0,
        kcal = 728.0, protein = 48.0, carbs = 133.0, fat = 2.4, micros = mapOf("sodium_mg" to 1200.0),
    )
    private val beef = LogDraft(name = "Ground beef", precision = Precision.Weighed, grams = 454.0, kcal = 1150.0, protein = 78.0, fat = 90.0)
    private val chili = Recipe(id = "r", name = "Chili", servings = 6.0, ingredients = listOf(beans, beef))

    @Test
    fun aServingIsASixthOfEverything() {
        val serving = chili.servingDraft()
        assertEquals("Chili", serving.name)
        assertEquals("1 serving", serving.amountText)
        assertEquals(1.0, serving.quantity)
        assertEquals(Recipe.SERVING, serving.unit)
        assertEquals((728.0 + 1150.0) / 6, serving.kcal!!, 1e-9)
        assertEquals((800.0 + 454.0) / 6, serving.grams!!, 1e-9)
        assertEquals(200.0, serving.micros!!.getValue("sodium_mg"), 1e-9)
        assertEquals(LogMethod.Saved, serving.method)
        assertEquals(Precision.Estimate, serving.precision)
    }

    @Test
    fun twoServingsReadAsServings_andCarbsCountWhatIsKnown() {
        val two = chili.servingDraft(2.0)
        assertEquals("2 servings", two.amountText)
        // The beef has no carbs: what the beans have is what there is.
        assertEquals(133.0 / 3, two.carbs!!, 1e-9)
    }

    @Test
    fun anUnweighedIngredientLeavesTheWeightUnknown_andNothingKnownStaysUnknown() {
        val pinch = LogDraft(name = "Salt", precision = Precision.Named)
        val serving = chili.copy(ingredients = listOf(beans, pinch)).servingDraft()
        assertNull(serving.grams)
        assertNull(Recipe("r", "Air", 2.0, listOf(pinch)).servingDraft().kcal)
    }

    @Test
    fun linksReadBackToTheirCode() {
        val code = "abcd2345"
        assertEquals("https://www.opsontracker.com/r/abcd2345", RecipeLinks.web(code))
        listOf(
            RecipeLinks.web(code),
            RecipeLinks.app(code),
            "https://opsontracker.com/r/abcd2345/",
            "HTTPS://WWW.OPSONTRACKER.COM/r/ABCD2345?c=print",
            "abcd2345",
            " ABCD2345 ",
        ).forEach { assertEquals(code, RecipeLinks.codeFrom(it), it) }
    }

    @Test
    fun anythingElseIsNotARecipeLink() {
        listOf(
            "https://example.com/r/abcd2345",
            "https://www.opsontracker.com/privacy",
            "https://www.opsontracker.com/r/abcd234",
            "https://www.opsontracker.com/r/abcd0345",
            "opson://widget/log",
            "012345678905",
            "",
        ).forEach { assertNull(RecipeLinks.codeFrom(it), it) }
    }
}
