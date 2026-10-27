package com.opson.libraries.foods.impl

import com.opson.libraries.foods.FoodSearchFilter
import com.opson.libraries.foods.FoodSearchResult
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The server's last [capacity] search answers, each for [ttl], in memory
 * only. Backspacing from "chicken b" to "chicken", or opening the sheet again
 * for the same food, shows the answer at once instead of a spinner and a
 * round trip. Kept short: popularity on the server moves slowly, and a
 * process restart forgets it all.
 *
 * Keyed the way the server normalizes a query (lowercase, single spaces), so
 * "Rice " and "rice" are one answer. Reads and writes come from the view
 * model and from network coroutines, so both are synchronized.
 */
@OptIn(InternalCoroutinesApi::class)
internal class RecentSearches(
    private val clock: Clock,
    private val capacity: Int = CAPACITY,
    private val ttl: Duration = TTL,
) {
    private data class Key(val query: String, val limit: Int, val filter: FoodSearchFilter)

    private class Entry(val result: FoodSearchResult, val at: Instant)

    private val lock = SynchronizedObject()

    /** Least recently used first: a hit moves its entry to the end. */
    private val entries = LinkedHashMap<Key, Entry>()

    fun get(query: String, limit: Int, filter: FoodSearchFilter): FoodSearchResult? {
        val key = Key(normalize(query), limit, filter)
        return synchronized(lock) {
            val entry = entries.remove(key) ?: return@synchronized null
            if (clock.now() - entry.at >= ttl) return@synchronized null
            entries[key] = entry
            entry.result
        }
    }

    fun put(query: String, limit: Int, filter: FoodSearchFilter, result: FoodSearchResult) {
        val key = Key(normalize(query), limit, filter)
        synchronized(lock) {
            entries.remove(key)
            entries[key] = Entry(result, clock.now())
            while (entries.size > capacity) entries.remove(entries.keys.first())
        }
    }

    private fun normalize(query: String): String = query.lowercase().trim().replace(WHITESPACE, " ")

    companion object {
        const val CAPACITY = 32
        val TTL: Duration = 5.minutes
        private val WHITESPACE = Regex("\\s+")
    }
}
