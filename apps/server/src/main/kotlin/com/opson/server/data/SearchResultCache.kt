package com.opson.server.data

import com.opson.server.domain.FoodMatch
import com.opson.server.domain.SearchFilter
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The last [maxEntries] search answers, each kept for [ttl]. Typing walks
 * through the same prefixes ("c", "ch", "chi") and people log the same
 * foods every day, so the same few hundred queries come back all the time;
 * each one is a statement over 445,000 rows, and over 3.3 million more with
 * Open Food Facts on.
 *
 * Bounded both ways: least recently used goes first past [maxEntries] (a
 * page of 25 foods is tens of kilobytes on the heap), and an answer older
 * than [ttl] is searched again, so a popularity change or a monthly Open Food
 * Facts reload shows within that. [clear] drops everything at once, for a
 * write that adds a food search could find. With [maxEntries] 0 it holds
 * nothing.
 */
internal class SearchResultCache(
    private val maxEntries: Int,
    private val ttl: Duration,
    private val clock: Clock,
) {
    /** Everything that changes the answer. [countryTag] is null unless Open Food Facts is searched, since only it ranks by country. */
    data class Key(
        val normalized: String,
        val limit: Int,
        val filter: SearchFilter,
        val openFoodFacts: Boolean,
        val countryTag: String?,
    )

    private class Entry(val matches: List<FoodMatch>, val at: Instant)

    private val entries = object : LinkedHashMap<Key, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>): Boolean = size > maxEntries
    }

    fun get(key: Key): List<FoodMatch>? {
        if (maxEntries <= 0) return null
        synchronized(entries) {
            val entry = entries[key] ?: return null
            if (clock.now() - entry.at < ttl) return entry.matches
            entries.remove(key)
            return null
        }
    }

    fun put(key: Key, matches: List<FoodMatch>) {
        if (maxEntries <= 0) return
        synchronized(entries) { entries[key] = Entry(matches, clock.now()) }
    }

    fun clear() {
        synchronized(entries) { entries.clear() }
    }

    val size: Int get() = synchronized(entries) { entries.size }
}
