@file:OptIn(ExperimentalObjCName::class)

package com.opson.libraries.review

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Thin wrapper around the platform's in-app review API: Play's `ReviewManager.launchReviewFlow()`
 * and StoreKit's `AppStore.requestReview(in:)` on the foreground window scene. Behind an interface
 * so the pacing in [ReviewPromptCoordinator] is testable without the platform.
 *
 * **Android binding** is `AndroidReviewLauncher` in `:libraries:review:impl/androidMain`. **iOS
 * binding** is `IOSReviewLauncher` (Swift), passed into the DI graph via
 * `IosAppComponent.provideReviewLauncher`.
 *
 * Implementations must not throw: the platform APIs report failure via callbacks or no-ops, and a
 * thrown launcher would punch through a UI-thread coroutine. Swallow and log instead.
 */
@ObjCName("ReviewLauncher", exact = true)
interface ReviewLauncher {

    /**
     * Ask the platform to show its rating sheet. Neither store says whether it showed or whether
     * the person rated, so the answer is a best guess at the first, never the second.
     */
    suspend fun requestReview(): ReviewLaunchOutcome
}

/**
 * How far a [ReviewLauncher.requestReview] call got, as well as the platform lets us tell. [wire]
 * is the `outcome` attribute of `review.asked`.
 */
@ObjCName("ReviewLaunchOutcome", exact = true)
enum class ReviewLaunchOutcome(val wire: String) {

    /**
     * Something appeared over the app after the ask: on iOS a new window in the scene, on Android
     * a review flow that stayed up for a while. A guess, and the closest either store comes to
     * saying the sheet was seen.
     */
    LikelyShown("likely_shown"),

    /** Handed to the store and back at once with nothing drawn: its own quota, most likely. */
    LikelyNotShown("likely_not_shown"),

    /** Handed to the store; this launcher can't tell what happened next. */
    Requested("requested"),

    /**
     * There was no Activity or window scene to host a sheet, so nothing was asked of the store.
     * Expected when the ask outlives the screen. The coordinator does not count it as an ask.
     */
    NoForegroundUi("no_window"),
}
