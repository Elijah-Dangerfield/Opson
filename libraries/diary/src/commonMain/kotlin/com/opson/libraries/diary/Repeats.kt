package com.opson.libraries.diary

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.uuid.Uuid

/**
 * What a repeat logs and when: [items] in [slot] on each of [days], from [startDay] to [endDay]
 * (open when null). [items] are snapshots, as a saved meal's are. With [savedMealId] the meal's
 * current foods are logged while it exists, and the snapshot only once it is gone.
 *
 * [name] is what the Repeating list shows: the meal's name, or the foods'.
 */
data class RepeatPlan(
    val name: String,
    val items: List<LogDraft>,
    val slot: MealSlot,
    val days: Set<DayOfWeek>,
    val startDay: LocalDate,
    val endDay: LocalDate? = null,
    val savedMealId: String? = null,
) {
    /** Whether [day] is one this plan logs on, paused or not. */
    fun fallsOn(day: LocalDate): Boolean =
        day >= startDay && (endDay == null || day <= endDay) && day.dayOfWeek in days

    /** The first day from [from] on that this plan logs, or null when it never will again. */
    fun nextFrom(from: LocalDate): LocalDate? {
        if (days.isEmpty()) return null
        var day = maxOf(from, startDay)
        repeat(DAYS_IN_WEEK) {
            if (endDay != null && day > endDay) return null
            if (day.dayOfWeek in days) return day
            day = day.plus(1, DateTimeUnit.DAY)
        }
        return null
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
    }
}

/**
 * A food or meal that logs itself on the days the person picked ("Repeating" on the Foods tab).
 * A paused one logs nothing until it is resumed.
 */
data class RepeatRule(
    val id: String,
    val plan: RepeatPlan,
    val paused: Boolean = false,
) {
    fun isDue(day: LocalDate): Boolean = !paused && plan.fallsOn(day)
}

/** The schedules the editor offers as one tap. Anything else is a custom set of days. */
object RepeatDays {
    val EveryDay: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    val Weekdays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )
    val Weekends: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
}

/** A repeat saved, and whether saving it logged today's foods as well. */
data class RepeatSaved(val rule: RepeatRule, val loggedToday: Boolean)

/**
 * Repeating foods. Stored on this phone only for now: the rules do not sync yet, but every rule
 * has a UUID, an update stamp and a tombstone, so it can join the diary outbox once the server
 * takes it. The foods a rule logs are ordinary diary entries and sync like any other.
 *
 * Today's foods are logged when the app opens and at midnight while it is open, never for a day
 * that has gone by. Each rule logs a day once: an entry removed stays removed that day.
 */
interface RepeatRepository {
    /** Every rule, paused ones included, oldest first. */
    fun observeAll(): Flow<List<RepeatRule>>

    suspend fun get(id: String): RepeatRule?

    /**
     * Whether rule [id] has had [day]: it logged its foods there, or "Repeat this" counted a food
     * as that day's. Still true once those foods are removed, since the rule logs nothing more
     * that day, so this rather than the day's entries says whether saving will log it.
     */
    fun observeHasRun(id: String, day: LocalDate): Flow<Boolean>

    /**
     * Saves a new rule and logs today's foods if today is one of its days. [loggedToday] names
     * entries already logged today that count as today's ("Repeat this" on Today), so the food the
     * person just logged is not logged twice.
     */
    suspend fun create(plan: RepeatPlan, loggedToday: List<String> = emptyList()): RepeatSaved

    /** Changes take effect from the next day the rule logs; what it logged already stays. */
    suspend fun update(id: String, plan: RepeatPlan, paused: Boolean): RepeatSaved?

    suspend fun setPaused(id: String, paused: Boolean): RepeatSaved?

    /** The foods it logged stay in the log. */
    suspend fun delete(id: String)
}

/**
 * The id of the [index]th food rule [ruleId] logs on [day]. Deterministic, so the same rule on
 * two phones writes the same rows rather than two of each: the server keeps one row per id, and
 * the second phone's write of the same row changes nothing. A version 8 UUID (RFC 9562's custom
 * layout), lowercase, which is the only spelling the server accepts.
 */
fun repeatEntryId(ruleId: String, day: LocalDate, index: Int): String {
    val key = "$ruleId|$day|$index".encodeToByteArray()
    val high = mix(fnv1a(key, FNV_OFFSET))
    val low = mix(fnv1a(key, FNV_OFFSET xor SECOND_SEED))
    val versioned = (high and VERSION_MASK.inv()) or VERSION_8
    val variant = (low and VARIANT_MASK.inv()) or VARIANT_RFC
    return Uuid.fromLongs(versioned, variant).toString()
}

private fun fnv1a(bytes: ByteArray, seed: Long): Long {
    var hash = seed
    for (byte in bytes) {
        hash = hash xor (byte.toLong() and BYTE_MASK)
        hash *= FNV_PRIME
    }
    return hash
}

/** SplitMix64's finalizer: spreads FNV's low-entropy high bits across the word. */
private fun mix(value: Long): Long {
    var z = value
    z = (z xor (z ushr 30)) * MIX_1
    z = (z xor (z ushr 27)) * MIX_2
    return z xor (z ushr 31)
}

private const val FNV_OFFSET = -0x340d631b7bdddcdbL
private const val FNV_PRIME = 0x100000001b3L
private const val SECOND_SEED = 0x5bd1e9955bd1e995L
private const val MIX_1 = -0x40a7b892e31b1a47L
private const val MIX_2 = -0x6b2fb644ecceee15L
private const val BYTE_MASK = 0xffL
private const val VERSION_MASK = 0xf000L
private const val VERSION_8 = 0x8000L
private const val VARIANT_MASK = -0x4000000000000000L
private const val VARIANT_RFC = Long.MIN_VALUE
