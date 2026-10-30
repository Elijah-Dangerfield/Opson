@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.review.impl

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import com.opson.libraries.opson.AppCache
import com.opson.libraries.review.ReviewExperiment
import com.opson.libraries.review.ReviewMoment
import com.opson.libraries.review.ReviewVariant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.random.Random

/**
 * [ReviewExperiment] over remote config: `release.reviewAsk`, `review.experiment` and one
 * `review.weight*` per moment. The moment is drawn once per install, at random by weight, and
 * kept on the phone (`AppData.reviewVariant`), so one person's asks stay at one moment and the
 * moments can be compared. Changing a weight moves only installs not yet assigned; renaming the
 * experiment reassigns everyone, and a moment set to 0 gives its installs another.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
@HiddenFromObjC
class ConfiguredReviewExperiment(
    private val enabled: ReviewAskEnabled,
    private val name: ReviewExperimentName,
    celebration: ReviewWeightCelebration,
    dayFeltGood: ReviewWeightDayFeltGood,
    showingUp: ReviewWeightShowingUp,
    foodApproved: ReviewWeightFoodApproved,
    private val appCache: AppCache,
    private val telemetry: ReviewTelemetry,
) : ReviewExperiment {

    /** The draw. A field rather than an injected value, so a test can fix it. */
    internal var random: Random = Random.Default

    private val weights: List<ReviewMomentWeight> = listOf(celebration, dayFeltGood, showingUp, foodApproved)
    private val mutex = Mutex()

    override suspend fun variant(): ReviewVariant? = mutex.withLock {
        if (!enabled.value) return@withLock null
        val shares = weights.associate { it.moment to it.value.coerceAtLeast(0) }.filterValues { it > 0 }
        if (shares.isEmpty()) return@withLock null
        val experiment = name.value.trim()

        val stored = appCache.get()
        val kept = ReviewMoment.fromWire(stored.reviewVariant)
            ?.takeIf { stored.reviewExperiment == experiment && it in shares }
        if (kept != null) return@withLock ReviewVariant(kept, experiment)

        val picked = ReviewVariant(pick(shares, random.nextInt(shares.values.sum())), experiment)
        appCache.update { it.copy(reviewVariant = picked.moment.wire, reviewExperiment = experiment) }
        telemetry.assigned(picked)
        picked
    }

    internal companion object {
        /** The moment [roll] (0 until the sum of [shares]) lands on, in [ReviewMoment] order. */
        fun pick(shares: Map<ReviewMoment, Int>, roll: Int): ReviewMoment {
            var left = roll
            for (moment in ReviewMoment.entries) {
                val share = shares[moment] ?: continue
                if (left < share) return moment
                left -= share
            }
            return shares.keys.last()
        }
    }
}
