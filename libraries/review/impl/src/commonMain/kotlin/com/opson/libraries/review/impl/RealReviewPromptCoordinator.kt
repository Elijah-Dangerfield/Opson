@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.review.impl

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import com.opson.libraries.core.AppState
import com.opson.libraries.core.AutoInit
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.opson.AppCache
import com.opson.libraries.review.ReviewExperiment
import com.opson.libraries.review.ReviewLaunchOutcome
import com.opson.libraries.review.ReviewLauncher
import com.opson.libraries.review.ReviewMoment
import com.opson.libraries.review.ReviewPromptCoordinator
import com.opson.libraries.review.ReviewVariant
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The default [ReviewPromptCoordinator], device-wide: the stores pace asks per device, so this
 * does too, and a sign-out does not reset it.
 *
 * - **A quiet first week** ([MIN_INSTALL_AGE]) from the first launch with this build, stamped at
 *   boot. (Cards stamped it at the first happy moment, which let the clock start late.)
 * - **[MIN_GAP] between asks.** Apple shows the sheet at most three times in 365 days; 122 days
 *   apart is three a year, so Opson never asks when the store would refuse anyway.
 * - **Never offline or right after trouble** ([RecentTrouble]): the sheet needs a connection, and
 *   nobody wants to be asked for stars a minute after something failed.
 *
 * The ask is written down before the launcher runs, so a crash in between costs an ask rather than
 * repeating one. The one exception, from Cards: [ReviewLaunchOutcome.NoForegroundUi] means the
 * store was never asked, so the stamp goes back to what it was.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ReviewPromptCoordinator::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
@HiddenFromObjC
class RealReviewPromptCoordinator(
    private val appCache: AppCache,
    private val launcher: ReviewLauncher,
    private val experiment: ReviewExperiment,
    private val appState: AppState,
    private val trouble: RecentTrouble,
    private val telemetry: ReviewTelemetry,
    private val clock: Clock,
    appScope: AppCoroutineScope,
) : ReviewPromptCoordinator, AutoInit {

    private val mutex = Mutex()

    init {
        appScope.launch { installAt() }
    }

    override suspend fun asksAt(moment: ReviewMoment): Boolean = experiment.variant()?.moment == moment

    override suspend fun clearToAsk(moment: ReviewMoment): Boolean {
        val variant = experiment.variant()?.takeIf { it.moment == moment } ?: return false
        val hold = hold() ?: return true
        telemetry.skipped(variant, hold)
        return false
    }

    override suspend fun couldAsk(): Boolean = experiment.variant() != null && hold() == null

    override suspend fun ask(moment: ReviewMoment, fromQa: Boolean): ReviewLaunchOutcome = mutex.withLock {
        val variant = when {
            fromQa -> ReviewVariant(moment, QA_EXPERIMENT)
            else -> experiment.variant() ?: ReviewVariant(moment, experiment = "")
        }
        val previous = appCache.get().lastReviewPromptAt
        appCache.update { it.copy(lastReviewPromptAt = clock.now().toEpochMilliseconds()) }
        val outcome = launcher.requestReview()
        if (outcome == ReviewLaunchOutcome.NoForegroundUi) {
            appCache.update { it.copy(lastReviewPromptAt = previous) }
        }
        telemetry.asked(variant.copy(moment = moment), outcome)
        outcome
    }

    /** What holds an ask back right now, the phone's pacing first. */
    private suspend fun hold(): ReviewHold? {
        val now = clock.now()
        val last = appCache.get().lastReviewPromptAt
        return when {
            now - installAt() < MIN_INSTALL_AGE -> ReviewHold.InstallTooNew
            last > 0 && now - Instant.fromEpochMilliseconds(last) < MIN_GAP -> ReviewHold.AskedRecently
            appState.isOffline.value -> ReviewHold.Offline
            trouble.recent() -> ReviewHold.Trouble
            else -> null
        }
    }

    /** When this install was first seen, stamped now if it never was. */
    private suspend fun installAt(): Instant {
        val stamped = appCache.get().reviewInstallAt
        if (stamped > 0) return Instant.fromEpochMilliseconds(stamped)
        val now = clock.now()
        appCache.update { if (it.reviewInstallAt > 0) it else it.copy(reviewInstallAt = now.toEpochMilliseconds()) }
        return Instant.fromEpochMilliseconds(appCache.get().reviewInstallAt)
    }

    companion object {
        /** A week of using Opson before anyone is asked what they think of it. */
        val MIN_INSTALL_AGE: Duration = 7.days

        /** Three asks a year at most, inside Apple's three-in-365-days limit. */
        val MIN_GAP: Duration = 122.days

        /** The experiment a QA menu ask is logged under, so it never counts toward a moment. */
        const val QA_EXPERIMENT = "qa"
    }
}
