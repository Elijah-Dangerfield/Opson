package com.opson.libraries.diary.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The food tile's tint is a pure hash, so it is pinned here: the same food must
 * be the same colour on Android, iOS and every launch. The composables
 * themselves are covered by their previews and the leak test.
 */
class FoodTintTest {

    @Test
    fun theSameSeedAlwaysLandsOnTheSameTint() {
        assertEquals(FoodTint.indexOf("f:banana"), FoodTint.indexOf("f:banana"))
    }

    @Test
    fun theHashIsPinnedSoItCannotDriftBetweenPlatforms() {
        // 31-based polynomial hash of "ab" is 97 * 31 + 98 = 3105, and 3105 mod 4 is 1.
        assertEquals(1, FoodTint.indexOf("ab"))
        assertEquals(0, FoodTint.indexOf(""))
    }

    @Test
    fun longSeedsThatOverflowStillGiveAValidTint() {
        val index = FoodTint.indexOf("f:" + "x".repeat(500))
        assertTrue(index in 0..3)
    }

    @Test
    fun differentFoodsSpreadOverAllFourTints() {
        val tints = (0 until 40).map { FoodTint.indexOf("f:food$it") }.toSet()
        assertEquals(setOf(0, 1, 2, 3), tints)
    }
}
