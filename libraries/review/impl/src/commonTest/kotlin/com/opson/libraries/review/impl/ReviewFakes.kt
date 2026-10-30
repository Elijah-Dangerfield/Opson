package com.opson.libraries.review.impl

import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.core.AppState
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
import com.opson.libraries.review.ReviewLaunchOutcome
import com.opson.libraries.review.ReviewLauncher
import com.opson.libraries.review.ReviewVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

internal val T0: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)

internal class MutableClock(at: Instant = T0) : Clock {
    var current: Instant = at
    fun advance(by: Duration) {
        current += by
    }
    override fun now(): Instant = current
}

internal class FakeLauncher(var outcome: ReviewLaunchOutcome = ReviewLaunchOutcome.LikelyShown) : ReviewLauncher {
    var asks = 0
        private set
    override suspend fun requestReview(): ReviewLaunchOutcome {
        asks++
        return outcome
    }
}

internal class FakeAppCache(initial: AppData = AppData()) : AppCache {
    val state = MutableStateFlow(initial)
    override val updates: Flow<AppData> = state
    override suspend fun get(): AppData = state.value
    override suspend fun set(value: AppData) {
        state.value = value
    }
    override suspend fun clear() {
        state.value = AppData()
    }
}

internal class FakeAppState(offline: Boolean = false) : AppState {
    override val isOffline: MutableStateFlow<Boolean> = MutableStateFlow(offline)
}

internal class RecordingReviewTelemetry : ReviewTelemetry {
    val assigned = mutableListOf<ReviewVariant>()
    val skipped = mutableListOf<Pair<ReviewVariant, ReviewHold>>()
    val asked = mutableListOf<Pair<ReviewVariant, ReviewLaunchOutcome>>()
    override fun assigned(variant: ReviewVariant) {
        assigned += variant
    }
    override fun skipped(variant: ReviewVariant, reason: ReviewHold) {
        skipped += variant to reason
    }
    override fun asked(variant: ReviewVariant, outcome: ReviewLaunchOutcome) {
        asked += variant to outcome
    }
}

/** Remote config as a flat path map, `"review.weightCelebration" to 2`. */
internal class FakeConfig(vararg values: Pair<String, Any>) : AppConfigMap() {
    private val flat = values.toMap().toMutableMap()

    operator fun set(path: String, value: Any) {
        flat[path] = value
    }

    override val map: Map<String, *>
        get() = flat.entries.groupBy({ it.key.substringBefore('.') }) { it.key.substringAfter('.') to it.value }
            .mapValues { (_, pairs) -> pairs.toMap() }
}

internal fun experiment(config: AppConfigMap, cache: AppCache, telemetry: ReviewTelemetry) = ConfiguredReviewExperiment(
    enabled = ReviewAskEnabled(config),
    name = ReviewExperimentName(config),
    celebration = ReviewWeightCelebration(config),
    dayFeltGood = ReviewWeightDayFeltGood(config),
    showingUp = ReviewWeightShowingUp(config),
    foodApproved = ReviewWeightFoodApproved(config),
    appCache = cache,
    telemetry = telemetry,
)
