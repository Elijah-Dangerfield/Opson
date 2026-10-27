package com.opson.libraries.diary

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/** [LogItem.withQuantity]: Today's "Change amount" scales everything the entry carries. */
class LogItemQuantityTest {
    private val peanutButter = LogItem(
        id = "pb",
        day = LocalDate(2026, 10, 25),
        slot = MealSlot.Snack,
        loggedAt = Instant.fromEpochSeconds(0),
        name = "Peanut butter",
        precision = Precision.Weighed,
        method = LogMethod.Search,
        foodId = "peanut-butter",
        amountText = "2 tbsp",
        quantity = 2.0,
        unit = "tbsp",
        grams = 32.0,
        kcal = 190.0,
        protein = 8.0,
        carbs = 7.0,
        fat = 16.0,
        fiber = 2.0,
        micros = mapOf("sodium_mg" to 140.0),
    )

    @Test
    fun scalesNutrientsGramsAndMicros_andRewritesTheAmount() {
        val three = assertNotNull(peanutButter.withQuantity(3.0))

        assertEquals(3.0, three.quantity)
        assertEquals("3 tbsp", three.amountText)
        assertEquals(48.0, three.grams)
        assertEquals(285.0, three.kcal)
        assertEquals(12.0, three.protein)
        assertEquals(24.0, three.fat)
        assertEquals(210.0, three.micros?.get("sodium_mg"))
        assertEquals(Precision.Weighed, three.precision, "the precision stays as logged")
        assertEquals(peanutButter.loggedAt, three.loggedAt)
    }

    @Test
    fun aPortionLabelCountsAsItsOwnAmount() {
        val rice = peanutButter.copy(amountText = "1 cup", quantity = 1.0, unit = "1 cup", grams = 158.0, kcal = 205.0)

        assertEquals("1½ cups", rice.withQuantity(1.5)?.amountText)
    }

    @Test
    fun nothingToScaleFrom_orNoAmount_isNull() {
        assertNull(peanutButter.copy(quantity = null).withQuantity(3.0))
        assertNull(peanutButter.copy(unit = null).withQuantity(3.0))
        assertNull(peanutButter.withQuantity(0.0))
    }
}
