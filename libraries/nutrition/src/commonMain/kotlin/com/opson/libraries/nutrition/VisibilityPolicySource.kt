package com.opson.libraries.nutrition

import kotlinx.coroutines.flow.StateFlow

/**
 * The live [VisibilityPolicy] for the signed-in user. The app root provides it to every screen
 * as `LocalVisibilityPolicy`, and anything outside composition that needs a number's visibility
 * reads it here, so there is one policy and every surface agrees on it.
 *
 * A `StateFlow` so there is always a current policy: a screen must never get a frame where it
 * has to guess.
 */
interface VisibilityPolicySource {
    val policy: StateFlow<VisibilityPolicy>
}
