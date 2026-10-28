package com.opson.libraries.foods

import kotlinx.coroutines.flow.StateFlow

/**
 * The foods this person added to Opson's food database from photos of a pack, by the id of their
 * own copy (the custom food saved first), with where each review stands. A row of their own food
 * reads "Unverified" while it is [ReviewStatus.Pending].
 *
 * Kept on the device and refreshed from `GET /v1/foods/submissions` whenever the account becomes
 * active, so the mark follows the person to another phone and goes once a reviewer approves the
 * food. A server without that route leaves what the device recorded as it is.
 */
interface SubmittedFoods {
    /** Review status by custom food id. Empty until the stored copy has been read. */
    val reviews: StateFlow<Map<String, ReviewStatus>>

    /**
     * [customFoodId] was just sent for review as [submissionId], which made the pending catalog food
     * [foodId]. Marks it at once, before any sync.
     */
    suspend fun sent(customFoodId: String, submissionId: String, foodId: String)
}
