package com.opson.libraries.foods

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoodCheckTest {

    private val bar = Food(
        id = "f1",
        source = FoodSource.UsdaBranded,
        name = "Granola bar",
        per100g = NutrientProfile(kcal = 470.0),
        tier = FoodTier.Label,
        verified = true,
    )

    @Test
    fun aPackagedFoodNobodyCheckedIsUnverifiedAndABuilderCanVerifyIt() {
        assertEquals(FoodCheck.Unverified, bar.check)
        assertTrue(bar.canBeVerified)
    }

    @Test
    fun aCheckedFoodIsVerifiedAndNotOfferedAgain() {
        val checked = bar.copy(checked = true)
        assertEquals(FoodCheck.Verified, checked.check)
        assertFalse(checked.canBeVerified)
    }

    @Test
    fun genericReferenceDataHasNoPackageToCheck() {
        val apple = bar.copy(source = FoodSource.UsdaFoundation, tier = FoodTier.Reference)
        assertEquals(FoodCheck.Reference, apple.check)
        assertFalse(apple.canBeVerified)
        // A reference food a reviewer corrected is still Verified.
        assertEquals(FoodCheck.Verified, apple.copy(checked = true).check)
    }

    @Test
    fun theirOwnSubmissionWaitingForReviewIsInReview() {
        val mine = bar.copy(source = FoodSource.UserSubmitted, tier = FoodTier.Community, reviewStatus = ReviewStatus.Pending)
        assertEquals(FoodCheck.InReview, mine.check)
        assertFalse(mine.canBeVerified)
    }
}
