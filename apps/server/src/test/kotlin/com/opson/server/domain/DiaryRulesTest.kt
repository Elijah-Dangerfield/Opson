package com.opson.server.domain

import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class DiaryRulesTest {

    private val now = Instant.parse("2026-10-25T12:00:00Z")
    private val today = LocalDate.parse("2026-10-25")

    private fun entry(
        day: LocalDate = today,
        name: String = "Banana",
        grams: Double? = 118.0,
        nutrients: Nutrients = Nutrients(kcal = 105.0, protein = 1.3),
        micros: Map<String, Double>? = null,
        clientUpdatedAt: Instant = now,
    ) = LogEntry(
        id = UUID.randomUUID(), day = day, slot = MealSlot.Breakfast, loggedAt = now,
        foodId = null, customFoodId = null, name = name, amountText = "1 medium", quantity = 1.0,
        unit = null, grams = grams, precision = Precision.Estimate, nutrients = nutrients, micros = micros,
        method = LogMethod.Search, clientUpdatedAt = clientUpdatedAt, deleted = false,
    )

    @Test
    fun validEntryPasses() {
        assertNull(DiaryRules.validate(entry(), now))
    }

    @Test
    fun dayMustBeInASaneRange() {
        assertNull(DiaryRules.validate(entry(day = today.plusDays(2)), now))
        assertEquals("day is in the future", DiaryRules.validate(entry(day = today.plusDays(3)), now))
        assertEquals("day is too far in the past", DiaryRules.validate(entry(day = LocalDate.of(1999, 12, 31)), now))
        assertNull(DiaryRules.validate(entry(day = DiaryRules.MIN_DAY), now))
    }

    @Test
    fun nutrientsMustBeNonNegativeAndFinite() {
        assertNotNull(DiaryRules.validate(entry(nutrients = Nutrients(kcal = -1.0)), now))
        assertNotNull(DiaryRules.validate(entry(nutrients = Nutrients(fat = Double.NaN)), now))
        assertNotNull(DiaryRules.validate(entry(nutrients = Nutrients(fiber = Double.POSITIVE_INFINITY)), now))
        assertNotNull(DiaryRules.validate(entry(grams = -5.0), now))
        assertNull(DiaryRules.validate(entry(nutrients = Nutrients(kcal = 0.0)), now))
    }

    @Test
    fun microsAreCheckedToo() {
        assertNull(DiaryRules.validate(entry(micros = mapOf("sodium_mg" to 1.0)), now))
        assertNotNull(DiaryRules.validate(entry(micros = mapOf("sodium_mg" to -1.0)), now))
        assertNotNull(DiaryRules.validate(entry(micros = mapOf("" to 1.0)), now))
    }

    @Test
    fun namesAreRequiredAndCapped() {
        assertEquals("name is required", DiaryRules.validate(entry(name = " "), now))
        assertNotNull(DiaryRules.validate(entry(name = "x".repeat(DiaryRules.MAX_NAME + 1)), now))
    }

    @Test
    fun clientClockMayNotBeFarAhead() {
        assertNull(DiaryRules.validate(entry(clientUpdatedAt = now + 23.hours), now))
        assertEquals(
            "clientUpdatedAtEpochMs is in the future",
            DiaryRules.validate(entry(clientUpdatedAt = now + 25.hours), now),
        )
    }

    @Test
    fun messagesNeverEchoValues() {
        val message = DiaryRules.validate(entry(nutrients = Nutrients(kcal = -123.0)), now).orEmpty()
        assertEquals(false, message.contains("123"))
    }

    @Test
    fun dayRecordWaterRange() {
        val record = DayRecord(today, waterMl = 2_000, closedAt = null, note = null, ateNoDetails = emptyMap(), mealGroups = null, clientUpdatedAt = now, deleted = false)
        assertNull(DiaryRules.validate(record, now))
        assertNotNull(DiaryRules.validate(record.copy(waterMl = -1), now))
        assertNotNull(DiaryRules.validate(record.copy(waterMl = DiaryRules.MAX_WATER_ML + 1), now))
    }

    @Test
    fun feelingsAreCapped() {
        val feeling = MealFeeling(today, MealSlot.Lunch, "hungry", "full", listOf("calm"), null, now, false)
        assertNull(DiaryRules.validate(feeling, now))
        assertNotNull(DiaryRules.validate(feeling.copy(moods = List(DiaryRules.MAX_MOODS + 1) { "ok" }), now))
        assertNotNull(DiaryRules.validate(feeling.copy(hunger = "x".repeat(DiaryRules.MAX_FEELING + 1)), now))
    }

    @Test
    fun weighInMustBePlausible() {
        val weighIn = WeighIn(UUID.randomUUID(), now, 70.0, now, false)
        assertNull(DiaryRules.validate(weighIn, now))
        assertNotNull(DiaryRules.validate(weighIn.copy(kg = 0.0), now))
        assertNotNull(DiaryRules.validate(weighIn.copy(kg = 900.0), now))
        assertNotNull(DiaryRules.validate(weighIn.copy(measuredAt = Instant.parse("1990-01-01T00:00:00Z")), now))
    }

    @Test
    fun customFoodNeedsAPositiveServing() {
        val food = CustomFood(UUID.randomUUID(), "Granola", null, 40.0, "g", Nutrients(kcal = 180.0), now, false)
        assertNull(DiaryRules.validate(food, now))
        assertEquals("servingAmount must be positive", DiaryRules.validate(food.copy(servingAmount = 0.0), now))
        assertEquals("servingUnit is required", DiaryRules.validate(food.copy(servingUnit = ""), now))
    }

    @Test
    fun jsonDocumentsAreSizeCapped() {
        val big = "[" + "1,".repeat(DiaryRules.MAX_JSON_CHARS) + "1]"
        assertNotNull(DiaryRules.validate(SavedMeal(UUID.randomUUID(), "Lunch", null, big, now, false), now))
        assertNotNull(DiaryRules.validate(UserSettings("{\"a\":\"" + "x".repeat(DiaryRules.MAX_JSON_CHARS) + "\"}", null, now, false), now))
        assertNull(DiaryRules.validate(UserSettings("{\"preset\":\"full\"}", null, now, false), now))
    }
}
