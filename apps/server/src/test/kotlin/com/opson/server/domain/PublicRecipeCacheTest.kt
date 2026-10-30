package com.opson.server.domain

import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * What keeps a served recipe page and for how long. Whether the recipe is still shared is the
 * route's to check on every read (RecipeRoutesTest); this only covers the cache's own rules.
 */
@OptIn(ExperimentalTime::class)
class PublicRecipeCacheTest {

    private var now = Instant.parse("2026-10-30T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private val stored = StoredRecipe(
        id = UUID.randomUUID(),
        ownerId = UserId(UUID.randomUUID()),
        name = "Chili",
        servings = 4.0,
        itemsJson = "[]",
        notes = null,
        code = "abcd2345",
        state = PublicState.Live,
        showName = false,
        ownerName = "Sam",
        deleted = false,
        publishedAt = now,
        updatedAt = now,
        saveCount = 0,
    )

    private var serves = 0

    private suspend fun PublicRecipeCache.read(recipe: StoredRecipe = stored): PublicRecipe =
        serve(recipe) {
            serves++
            PublicRecipes.serve(recipe, emptyMap())
        }

    @Test
    fun theSameRecipeIsServedOnce_untilItIsEditedOrItsNameShowsOrTimeRunsOut() = runTest {
        val cache = PublicRecipeCache(clock, maxEntries = 10, ttl = 5.minutes)
        cache.read()
        cache.read()
        assertEquals(1, serves)

        assertEquals("Sam", cache.read(stored.copy(showName = true)).by)
        assertEquals(2, serves)
        cache.read(stored.copy(updatedAt = now + 1.seconds))
        assertEquals(3, serves)

        now += 5.minutes
        cache.read()
        assertEquals(4, serves)
    }

    @Test
    fun theLeastRecentlyReadGoesFirst() = runTest {
        val cache = PublicRecipeCache(clock, maxEntries = 1)
        cache.read()
        cache.read(stored.copy(code = "zzzz2345"))
        cache.read()
        assertEquals(3, serves)
    }
}
