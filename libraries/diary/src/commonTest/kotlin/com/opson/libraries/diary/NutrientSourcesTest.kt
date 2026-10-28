package com.opson.libraries.diary

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.Nutrient
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Which foods gave a nutrient, ranked, and what an item says about one it does not list. */
class NutrientSourcesTest {

    private val day = LocalDate(2026, 10, 25)
    private val iron = Nutrient.Micro(Micronutrient.Iron)

    private fun item(
        name: String,
        foodId: String? = null,
        protein: Double? = null,
        micros: Map<String, Double>? = null,
    ) = LogItem(
        id = name + foodId,
        day = day,
        slot = MealSlot.Lunch,
        loggedAt = Instant.fromEpochSeconds(0),
        name = name,
        precision = Precision.Estimate,
        method = LogMethod.Search,
        foodId = foodId,
        protein = protein,
        micros = micros,
    )

    @Test
    fun ranksByAmount_withSharesOfTheListedTotal() {
        val items = listOf(
            item("Spinach", micros = mapOf("iron_mg" to 3.0)),
            item("Lentils", micros = mapOf("iron_mg" to 6.0)),
            item("Toast", micros = mapOf("iron_mg" to 1.0)),
        )
        val ranked = items.contributions(iron)
        assertEquals(listOf("Lentils", "Spinach", "Toast"), ranked.map { it.name })
        assertEquals(listOf(60.0, 30.0, 10.0), ranked.map { it.percent })
        assertEquals(6.0, ranked.first().amount)
    }

    @Test
    fun theSameFood_twice_isOneRow() {
        val items = listOf(
            item("Oats", foodId = "f1", micros = mapOf("iron_mg" to 2.0)),
            item("Oats, again", foodId = "f1", micros = mapOf("iron_mg" to 2.0)),
            item("Egg", micros = mapOf("iron_mg" to 1.0)),
            item("egg ", micros = mapOf("iron_mg" to 1.0)),
        )
        assertEquals(listOf("Oats" to 4.0, "Egg" to 2.0), items.contributions(iron).map { it.name to it.amount })
    }

    @Test
    fun foodsThatListNoneOrZero_areNotContributors() {
        val items = listOf(
            item("Water", micros = mapOf("iron_mg" to 0.0)),
            item("Named only"),
            item("Beans", micros = mapOf("iron_mg" to 2.0)),
        )
        assertEquals(listOf("Beans"), items.contributions(iron).map { it.name })
        assertEquals(emptyList(), listOf(item("Water", micros = mapOf("iron_mg" to 0.0))).contributions(iron))
    }

    @Test
    fun macros_rankTheSameWay() {
        val items = listOf(item("Yogurt", protein = 15.0), item("Apple", protein = 0.5), item("Tea"))
        assertEquals(listOf("Yogurt", "Apple"), items.contributions(Nutrient.Macro(Metric.Protein)).map { it.name })
    }

    /** Null is "not listed" and 0 is "none": the difference survives all the way to here. */
    @Test
    fun amountOf_keepsNullAndZeroApart() {
        assertNull(item("Tea").amountOf(iron))
        assertNull(item("Tea", micros = mapOf("calcium_mg" to 5.0)).amountOf(iron))
        assertEquals(0.0, item("Water", micros = mapOf("iron_mg" to 0.0)).amountOf(iron))
        assertNull(item("Tea").amountOf(Nutrient.Macro(Metric.Protein)))
    }
}
