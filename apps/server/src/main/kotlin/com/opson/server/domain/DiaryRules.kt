package com.opson.server.domain

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * What a synced row must look like before the server stores it. The server's
 * copy of these rules is the authoritative backstop; the client should never
 * produce a row that breaks them.
 *
 * [clampClock] runs first and [validate] after it. [validate] returns null for
 * a valid row, otherwise a message naming the field. The route turns a message into a `rejected` entry with code
 * `invalid_row`. Messages name fields, never echo values: they can end up in
 * a log line, and the values are health data.
 */
object DiaryRules {
    /** Mutations accepted per `/v1/sync` request. */
    const val MAX_MUTATIONS = 500

    /** Rows returned per entity per `/v1/sync` response. */
    const val PAGE_SIZE = 500

    val MIN_DAY: LocalDate = LocalDate.of(2000, 1, 1)

    /**
     * Days past today (UTC) a row may be dated. Today lets someone plan six
     * days ahead in their own time zone; UTC+14 adds one and the rest is slack.
     * The client's `SERVER_MAX_DAYS_AHEAD` must match, or planned days sit in
     * the outbox until they come into range.
     */
    const val MAX_DAYS_AHEAD = 8L

    /** How far ahead of the server clock a device clock may be before its times are replaced (see [clampClock]). */
    val MAX_CLOCK_SKEW = 24.hours

    const val MAX_NAME = 200
    const val MAX_AMOUNT_TEXT = 100
    const val MAX_UNIT = 32
    const val MAX_NOTE = 2_000
    const val MAX_FEELING = 40

    /** The check-in's JSON: four short wire values and six moods fit well inside this. */
    const val MAX_CHECK_IN = 400
    const val MAX_MOODS = 20
    const val MAX_MICROS = 100
    const val MAX_MICRO_KEY = 64
    const val MAX_JSON_CHARS = 64 * 1024

    /** Generous ceilings that only catch garbage (a unit mix-up, a stray digit). */
    const val MAX_NUTRIENT = 100_000.0
    const val MAX_GRAMS = 100_000.0
    const val MAX_QUANTITY = 10_000.0
    const val MAX_WATER_ML = 20_000
    const val MAX_KG = 700.0

    /** The longest a fast may be, and its largest goal. TODO(clinician): confirm the cap. */
    const val MAX_FAST_HOURS = 16

    /**
     * [row] with every time more than [MAX_CLOCK_SKEW] past [now] replaced by
     * [now]. Such a time comes from a phone whose clock is wrong, not from the
     * future. Refusing the row, as the server once did, made that phone roll the
     * change back, so a clock a couple of days fast lost everything the person
     * logged. Stored as [now], the change is kept, and it cannot outrank a later
     * edit from a phone that keeps good time.
     */
    fun clampClock(row: DiaryRow, now: Instant): DiaryRow {
        fun Instant.clamped(): Instant = if (this > now + MAX_CLOCK_SKEW) now else this
        val updatedAt = row.clientUpdatedAt.clamped()
        return when (row) {
            is LogEntry -> row.copy(loggedAt = row.loggedAt.clamped(), clientUpdatedAt = updatedAt)
            is DayRecord -> row.copy(closedAt = row.closedAt?.clamped(), clientUpdatedAt = updatedAt)
            is MealFeeling -> row.copy(clientUpdatedAt = updatedAt)
            is WeighIn -> row.copy(measuredAt = row.measuredAt.clamped(), clientUpdatedAt = updatedAt)
            is CustomFood -> row.copy(clientUpdatedAt = updatedAt)
            is SavedMeal -> row.copy(clientUpdatedAt = updatedAt)
            is UserSettings -> row.copy(changedAt = row.changedAt?.clamped(), clientUpdatedAt = updatedAt)
            is Fast -> row.copy(startedAt = row.startedAt.clamped(), endedAt = row.endedAt?.clamped(), clientUpdatedAt = updatedAt)
        }
    }

    fun validate(row: DiaryRow, now: Instant): String? {
        return when (row) {
            is LogEntry -> validateEntry(row, now)
            is DayRecord -> validateDay(row, now)
            is MealFeeling -> validateFeeling(row, now)
            is WeighIn -> validateWeighIn(row)
            is CustomFood -> validateCustomFood(row)
            is SavedMeal -> validateSavedMeal(row)
            is UserSettings -> jsonSize("settings", row.settingsJson)
            is Fast -> validateFast(row, now)
        }
    }

    private fun validateEntry(row: LogEntry, now: Instant): String? =
        dayError(row.day, now)
            ?: text("name", row.name, MAX_NAME, required = true)
            ?: text("amountText", row.amountText, MAX_AMOUNT_TEXT)
            ?: text("unit", row.unit, MAX_UNIT)
            ?: amount("quantity", row.quantity, MAX_QUANTITY)
            ?: amount("grams", row.grams, MAX_GRAMS)
            ?: nutrients(row.nutrients)
            ?: micros(row.micros)

    private fun validateDay(row: DayRecord, now: Instant): String? =
        dayError(row.day, now)
            ?: text("note", row.note, MAX_NOTE)
            ?: text("feeling", row.feeling, MAX_FEELING)
            ?: row.feeling?.let { if (it.isBlank()) "feeling is blank" else null }
            ?: text("checkIn", row.checkIn, MAX_CHECK_IN)
            ?: row.waterMl?.let { if (it < 0 || it > MAX_WATER_ML) "waterMl is out of range" else null }

    private fun validateFeeling(row: MealFeeling, now: Instant): String? =
        dayError(row.day, now)
            ?: text("hunger", row.hunger, MAX_FEELING)
            ?: text("fullness", row.fullness, MAX_FEELING)
            ?: text("note", row.note, MAX_NOTE)
            ?: when {
                row.moods.size > MAX_MOODS -> "moods has too many items"
                row.moods.any { it.isBlank() || it.length > MAX_FEELING } -> "moods has an invalid item"
                else -> null
            }

    private fun validateFast(row: Fast, now: Instant): String? {
        val goal = row.goalHours
        val ended = row.endedAt
        return dayError(row.day, now)
            ?: when {
                row.startedAt < MIN_INSTANT -> "startedAtEpochMs is too old"
                goal != null && goal !in 1..MAX_FAST_HOURS -> "goalHours is out of range"
                ended != null && ended < row.startedAt -> "endedAtEpochMs is before startedAtEpochMs"
                ended != null && ended - row.startedAt > MAX_FAST_HOURS.hours -> "endedAtEpochMs is more than $MAX_FAST_HOURS hours after startedAtEpochMs"
                else -> null
            }
    }

    private fun validateWeighIn(row: WeighIn): String? = when {
        !row.kg.isFinite() || row.kg <= 0.0 || row.kg > MAX_KG -> "kg is out of range"
        row.measuredAt < MIN_INSTANT -> "measuredAtEpochMs is too old"
        else -> null
    }

    private val MIN_INSTANT: Instant =
        Instant.fromEpochSeconds(MIN_DAY.atStartOfDay(ZoneOffset.UTC).toEpochSecond())

    private fun validateCustomFood(row: CustomFood): String? =
        text("name", row.name, MAX_NAME, required = true)
            ?: text("brand", row.brand, MAX_NAME)
            ?: text("servingUnit", row.servingUnit, MAX_UNIT, required = true)
            ?: amount("servingAmount", row.servingAmount, MAX_GRAMS)
            ?: positive("servingAmount", row.servingAmount)
            ?: nutrients(row.nutrients)

    private fun positive(field: String, value: Double): String? =
        if (value <= 0.0) "$field must be positive" else null

    private fun validateSavedMeal(row: SavedMeal): String? =
        text("name", row.name, MAX_NAME, required = true)
            ?: jsonSize("items", row.itemsJson)

    private fun jsonSize(field: String, json: String): String? =
        if (json.length > MAX_JSON_CHARS) "$field is too large" else null

    private fun dayError(day: LocalDate, now: Instant): String? {
        val today = java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atZone(ZoneOffset.UTC).toLocalDate()
        return when {
            day < MIN_DAY -> "day is too far in the past"
            day > today.plusDays(MAX_DAYS_AHEAD) -> "day is in the future"
            else -> null
        }
    }

    private fun text(field: String, value: String?, max: Int, required: Boolean = false): String? = when {
        value == null -> if (required) "$field is required" else null
        required && value.isBlank() -> "$field is required"
        value.length > max -> "$field is longer than $max characters"
        else -> null
    }

    private fun amount(field: String, value: Double?, max: Double): String? = when {
        value == null -> null
        !value.isFinite() || value < 0.0 -> "$field must be a non-negative number"
        value > max -> "$field is out of range"
        else -> null
    }

    private fun nutrients(n: Nutrients): String? {
        val names = listOf("kcal", "protein", "carbs", "fat", "fiber")
        return names.zip(n.all()).firstNotNullOfOrNull { (name, value) -> amount(name, value, MAX_NUTRIENT) }
    }

    private fun micros(micros: Map<String, Double>?): String? {
        if (micros == null) return null
        if (micros.size > MAX_MICROS) return "micros has too many keys"
        if (micros.keys.any { it.isBlank() || it.length > MAX_MICRO_KEY }) return "micros has an invalid key"
        return micros.values.firstNotNullOfOrNull { amount("micros", it, MAX_NUTRIENT) }
    }
}
