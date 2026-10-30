package com.opson.libraries.review.impl

import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.opson.ActivityProvider
import com.opson.libraries.review.ReviewLaunchOutcome
import com.opson.libraries.review.ReviewLauncher
import kotlinx.coroutines.suspendCancellableCoroutine
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Play's in-app review. The sheet will not show if the device has no Play Services, Play decides
 * this person was asked recently, or the signing key isn't the one Play knows (any build not from
 * Play, including internal app sharing's own rules). In each case the flow completes as if it had,
 * which is Play's contract: it never says whether anything showed or whether they rated.
 *
 * What it does give away is time. A flow that shows the sheet stays open until the person answers
 * or closes it; one that shows nothing completes at once. So a flow that took longer than
 * [SEEN_AFTER] is reported as [ReviewLaunchOutcome.LikelyShown]. A guess, labelled as one.
 *
 * No foreground Activity is [ReviewLaunchOutcome.NoForegroundUi], quietly: it is expected when the
 * ask outlives the screen (Cards' CARDS-D0 logged it as an error and spent the ask).
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AndroidReviewLauncher(
    private val context: Context,
    private val activityProvider: ActivityProvider,
) : ReviewLauncher {

    override suspend fun requestReview(): ReviewLaunchOutcome {
        val activity = activityProvider.currentActivity() ?: return ReviewLaunchOutcome.NoForegroundUi
        return Catching {
            val manager = ReviewManagerFactory.create(context)
            val info = manager.requestReviewFlow().awaitOrNull() ?: return@Catching ReviewLaunchOutcome.LikelyNotShown
            val started = TimeSource.Monotonic.markNow()
            suspendCancellableCoroutine { cont ->
                manager.launchReviewFlow(activity, info).addOnCompleteListener {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            if (started.elapsedNow() >= SEEN_AFTER) ReviewLaunchOutcome.LikelyShown else ReviewLaunchOutcome.LikelyNotShown
        }.logOnFailure { "In-app review prompt failed (non-fatal)" }
            .getOrNull() ?: ReviewLaunchOutcome.Requested
    }

    private companion object {
        /** Nobody reads, decides and taps in under a second; a flow back that fast showed nothing. */
        val SEEN_AFTER = 1.seconds
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitOrNull(): T? =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
        addOnFailureListener { _ -> if (cont.isActive) cont.resume(null) }
        addOnCanceledListener { if (cont.isActive) cont.resume(null) }
    }
