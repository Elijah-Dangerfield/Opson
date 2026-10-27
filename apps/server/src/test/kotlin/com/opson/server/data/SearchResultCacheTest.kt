package com.opson.server.data

import com.opson.server.domain.CatalogSource
import com.opson.server.domain.Food
import com.opson.server.domain.FoodMatch
import com.opson.server.domain.MatchTier
import com.opson.server.domain.Nutrients
import com.opson.server.domain.SearchFilter
import com.opson.server.domain.SearchSource
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SearchResultCacheTest {

    private val clock = object : Clock {
        var now = Instant.parse("2026-10-27T12:00:00Z")
        override fun now(): Instant = now
    }

    private fun key(query: String, filter: SearchFilter = SearchFilter.None) =
        SearchResultCache.Key(query, limit = 25, filter = filter, openFoodFacts = false, countryTag = null)

    private fun page(name: String) = listOf(
        FoodMatch(
            Food(
                id = UUID.randomUUID(),
                source = CatalogSource.UsdaSr,
                sourceId = name,
                name = name,
                brand = null,
                barcode = null,
                per100g = Nutrients(kcal = 100.0),
                micros = emptyMap(),
                portions = emptyList(),
            ),
            MatchTier.Exact,
            1.0,
        ),
    )

    @Test
    fun anAnswerIsKeptForTheTtlAndNoLonger() {
        val cache = SearchResultCache(maxEntries = 10, ttl = 5.minutes, clock = clock)
        val eggs = page("Egg, whole, raw")
        cache.put(key("egg"), eggs)

        clock.now += 4.minutes
        assertEquals(eggs, cache.get(key("egg")))

        clock.now += 1.minutes + 1.seconds
        assertNull(cache.get(key("egg")), "searched again after the TTL")
        assertEquals(0, cache.size, "and the stale answer is let go")
    }

    @Test
    fun theLeastRecentlyUsedAnswerGoesFirst() {
        val cache = SearchResultCache(maxEntries = 2, ttl = 5.minutes, clock = clock)
        cache.put(key("egg"), page("Egg"))
        cache.put(key("milk"), page("Milk"))
        cache.get(key("egg"))
        cache.put(key("rice"), page("Rice"))

        assertNull(cache.get(key("milk")))
        assertEquals("Egg", cache.get(key("egg"))?.single()?.food?.name)
        assertEquals("Rice", cache.get(key("rice"))?.single()?.food?.name)
    }

    @Test
    fun aFilterIsPartOfTheQuestion() {
        val cache = SearchResultCache(maxEntries = 10, ttl = 5.minutes, clock = clock)
        cache.put(key("egg"), page("Egg"))
        assertNull(cache.get(key("egg", SearchFilter(source = SearchSource.Usda))))
    }

    @Test
    fun zeroEntriesHoldsNothing() {
        val cache = SearchResultCache(maxEntries = 0, ttl = 5.minutes, clock = clock)
        cache.put(key("egg"), page("Egg"))
        assertNull(cache.get(key("egg")))
    }

    @Test
    fun clearDropsEverything() {
        val cache = SearchResultCache(maxEntries = 10, ttl = 5.minutes, clock = clock)
        cache.put(key("egg"), page("Egg"))
        cache.clear()
        assertNull(cache.get(key("egg")))
    }
}
