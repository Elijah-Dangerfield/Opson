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
        assertNull(DiaryRules.validate(entry(day = today.plusDays(8)), now))
        assertEquals("day is in the future", DiaryRules.validate(entry(day = today.plusDays(9)), now))
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
    fun textPostgresCannotStoreIsRefused() {
        assertEquals("name contains a NUL character", DiaryRules.validate(entry(name = "Ban\u0000ana"), now))
        assertNotNull(DiaryRules.validate(entry(micros = mapOf("sodium\u0000" to 1.0)), now))
        val settings = UserSettings("""{"name":"a\u0000b"}""", null, now, false)
        assertEquals("settings contains a NUL character", DiaryRules.validate(settings, now))
        assertNull(DiaryRules.validate(UserSettings("""{"name":"ab"}""", null, now, false), now))
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
    fun aClockWithinTheSkewIsTrusted() {
        val row = entry(clientUpdatedAt = now + 23.hours)
        assertEquals(row, DiaryRules.clampClock(row, now))
        assertNull(DiaryRules.validate(row, now))
    }

    @Test
    fun aClockDaysAhead_isBroughtBackToNow_ratherThanRefused() {
        val row = entry(clientUpdatedAt = now + 49.hours).copy(loggedAt = now + 48.hours)

        val clamped = DiaryRules.clampClock(row, now) as LogEntry

        assertEquals(now, clamped.clientUpdatedAt)
        assertEquals(now, clamped.loggedAt)
        assertNull(DiaryRules.validate(clamped, now))
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
    fun dayFeelingIsShortAndNotBlank() {
        val record = DayRecord(today, null, null, null, emptyMap(), null, now, false, untracked = true, feeling = "steady")
        assertNull(DiaryRules.validate(record, now))
        assertNull(DiaryRules.validate(record.copy(feeling = null), now))
        assertNotNull(DiaryRules.validate(record.copy(feeling = " "), now))
        assertNotNull(DiaryRules.validate(record.copy(feeling = "x".repeat(DiaryRules.MAX_FEELING + 1)), now))
    }

    @Test
    fun theCheckInIsOpaqueButCapped() {
        val record = DayRecord(today, null, null, null, emptyMap(), null, now, false, checkIn = """{"hunger":"steady","moods":["calm"]}""")
        assertNull(DiaryRules.validate(record, now))
        assertNull(DiaryRules.validate(record.copy(checkIn = null), now))
        assertNotNull(DiaryRules.validate(record.copy(checkIn = "x".repeat(DiaryRules.MAX_CHECK_IN + 1)), now))
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
    fun customFoodSizesNeedANameAndAWeight() {
        val food = CustomFood(UUID.randomUUID(), "Creamer", null, 1.0, "tbsp", Nutrients(kcal = 35.0), now, false)
        fun with(details: CustomFoodDetails) = DiaryRules.validate(food.copy(details = details), now)

        assertNull(with(CustomFoodDetails(servingGrams = 15.0, sizes = listOf(FoodSize("cup", 240.0)), micros = mapOf("sugars_g" to 5.0))))
        assertEquals("sizes.label is required", with(CustomFoodDetails(sizes = listOf(FoodSize(" ", 15.0)))))
        assertEquals("sizes.grams must be positive", with(CustomFoodDetails(sizes = listOf(FoodSize("tbsp", 0.0)))))
        assertEquals("servingGrams must be a non-negative number", with(CustomFoodDetails(servingGrams = -1.0)))
        assertEquals("sizes has too many entries", with(CustomFoodDetails(sizes = List(DiaryRules.MAX_SIZES + 1) { FoodSize("s$it", 1.0) })))
        assertEquals("micros has an invalid key", with(CustomFoodDetails(micros = mapOf("" to 1.0))))
    }

    @Test
    fun jsonDocumentsAreSizeCapped() {
        val big = "[" + "1,".repeat(DiaryRules.MAX_JSON_CHARS) + "1]"
        assertNotNull(DiaryRules.validate(SavedMeal(UUID.randomUUID(), "Lunch", null, big, now, false), now))
        assertNotNull(DiaryRules.validate(UserSettings("{\"a\":\"" + "x".repeat(DiaryRules.MAX_JSON_CHARS) + "\"}", null, now, false), now))
        assertNull(DiaryRules.validate(UserSettings("{\"preset\":\"full\"}", null, now, false), now))
    }

    private fun fast(
        startedAt: Instant = now - 3.hours,
        endedAt: Instant? = null,
        goalHours: Int? = 14,
        day: LocalDate = today,
    ) = Fast(day = day, startedAt = startedAt, endedAt = endedAt, goalHours = goalHours, clientUpdatedAt = now, deleted = false)

    @Test
    fun validFastPasses() {
        assertNull(DiaryRules.validate(fast(), now))
        assertNull(DiaryRules.validate(fast(endedAt = now - 1.hours), now))
        assertNull(DiaryRules.validate(fast(goalHours = null), now))
        assertNull(DiaryRules.validate(fast(startedAt = now - 16.hours, endedAt = now), now), "exactly 16 hours is allowed")
    }

    @Test
    fun aFastNeverGoesPast16Hours() {
        assertEquals(
            "endedAtEpochMs is more than 16 hours after startedAtEpochMs",
            DiaryRules.validate(fast(startedAt = now - 17.hours, endedAt = now), now),
        )
        assertEquals("goalHours is out of range", DiaryRules.validate(fast(goalHours = 18), now))
        assertEquals("goalHours is out of range", DiaryRules.validate(fast(goalHours = 0), now))
    }

    @Test
    fun aFastCannotEndBeforeItStarts_andAFutureStartIsBroughtBack() {
        assertEquals("endedAtEpochMs is before startedAtEpochMs", DiaryRules.validate(fast(endedAt = now - 4.hours), now))
        assertEquals(now, (DiaryRules.clampClock(fast(startedAt = now + 25.hours), now) as Fast).startedAt)
        assertEquals("day is in the future", DiaryRules.validate(fast(day = today.plusDays(9)), now))
    }

    private fun recipe(servings: Double = 4.0, name: String = "Chili", notes: String? = null, items: Int = 3) = Recipe(
        UUID.randomUUID(), name, servings, "[" + List(items) { "{}" }.joinToString(",") + "]", items, notes, clientUpdatedAt = now, deleted = false,
    )

    @Test
    fun aRecipeMakesAQuarterServingToAThousand_withSixtyIngredientsAtMost() {
        assertNull(DiaryRules.validate(recipe(), now))
        assertNull(DiaryRules.validate(recipe(servings = 0.25), now))
        assertEquals("servings is out of range", DiaryRules.validate(recipe(servings = 0.0), now))
        assertEquals("servings is out of range", DiaryRules.validate(recipe(servings = 1001.0), now))
        assertEquals("servings is out of range", DiaryRules.validate(recipe(servings = Double.NaN), now))
        assertEquals("items has too many entries", DiaryRules.validate(recipe(items = 61), now))
        assertEquals("name is required", DiaryRules.validate(recipe(name = " "), now))
        assertEquals("notes is longer than 4000 characters", DiaryRules.validate(recipe(notes = "x".repeat(4001)), now))
    }

    private fun exercise(minutes: Int = 30, kcal: Int = 200, activity: String = "walking", name: String? = null, startedAt: Instant = now) =
        Exercise(UUID.randomUUID(), today, startedAt, activity, name, minutes, kcal, kcalTyped = false, notOnWatch = false, clientUpdatedAt = now, deleted = false)

    @Test
    fun anExerciseIsADayLongAtMost_andCountsAtMost5000() {
        assertNull(DiaryRules.validate(exercise(), now))
        assertNull(DiaryRules.validate(exercise(minutes = 1440, kcal = 5000), now))
        assertNull(DiaryRules.validate(exercise(kcal = 0), now), "yoga can count nothing")
        assertEquals("minutes is out of range", DiaryRules.validate(exercise(minutes = 0), now))
        assertEquals("minutes is out of range", DiaryRules.validate(exercise(minutes = 1441), now))
        assertEquals("kcal is out of range", DiaryRules.validate(exercise(kcal = 5001), now))
        assertEquals("kcal is out of range", DiaryRules.validate(exercise(kcal = -1), now))
        assertEquals("activity is required", DiaryRules.validate(exercise(activity = " "), now))
        assertEquals("name is longer than 80 characters", DiaryRules.validate(exercise(activity = "other", name = "x".repeat(81)), now))
        assertEquals(now, (DiaryRules.clampClock(exercise(startedAt = now + 25.hours), now) as Exercise).startedAt)
    }
}
