package com.opson.libraries.review.impl

import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.LogContext
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogLevel
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.opson.AppData
import com.opson.libraries.review.ReviewLaunchOutcome
import com.opson.libraries.review.ReviewMoment
import com.opson.libraries.review.ReviewVariant
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class RealReviewPromptCoordinatorTest : CoroutineTest() {

    private val clock = MutableClock()
    private val config = FakeConfig(
        "release.reviewAsk" to true,
        "review.weightCelebration" to 1,
        "review.weightDayFeltGood" to 0,
        "review.weightShowingUp" to 0,
    )
    private val cache = FakeAppCache()
    private val launcher = FakeLauncher()
    private val appState = FakeAppState()
    private val telemetry = RecordingReviewTelemetry()
    private val scope by lazy { AppCoroutineScope(dispatchers) }
    private val trouble by lazy { RecentTrouble(clock, appState, scope) }
    private val coordinator by lazy {
        RealReviewPromptCoordinator(
            appCache = cache,
            launcher = launcher,
            experiment = experiment(config, cache, telemetry),
            appState = appState,
            trouble = trouble,
            telemetry = telemetry,
            clock = clock,
            appScope = scope,
        )
    }

    private val celebration = ReviewVariant(ReviewMoment.Celebration, "moments-1")

    @AfterTest
    fun stop() = scope.cancel()

    /** A week and a day after the first launch. */
    private fun settledIn() {
        coordinator
        clock.advance(RealReviewPromptCoordinator.MIN_INSTALL_AGE + 1.days)
    }

    @Test
    fun stampsTheInstallAtBoot() = runUnitTest {
        coordinator

        assertEquals(T0.toEpochMilliseconds(), cache.get().reviewInstallAt)
    }

    @Test
    fun asksOnlyAtThisInstallsMoment() = runUnitTest {
        assertTrue(coordinator.asksAt(ReviewMoment.Celebration))
        assertFalse(coordinator.asksAt(ReviewMoment.DayFeltGood))
        assertFalse(coordinator.clearToAsk(ReviewMoment.DayFeltGood))
        assertEquals(emptyList(), telemetry.skipped, "another install's moment is not a skip")
    }

    @Test
    fun theFirstWeekIsQuiet() = runUnitTest {
        coordinator
        clock.advance(6.days)

        assertFalse(coordinator.clearToAsk(ReviewMoment.Celebration))
        assertEquals(listOf(celebration to ReviewHold.InstallTooNew), telemetry.skipped)
    }

    @Test
    fun clearAfterTheFirstWeek() = runUnitTest {
        settledIn()

        assertTrue(coordinator.clearToAsk(ReviewMoment.Celebration))
        assertTrue(coordinator.couldAsk())
    }

    @Test
    fun anAsk_isWrittenDown_andHoldsTheNextFor122Days() = runUnitTest {
        settledIn()

        assertEquals(ReviewLaunchOutcome.LikelyShown, coordinator.ask(ReviewMoment.Celebration))
        assertEquals(clock.now().toEpochMilliseconds(), cache.get().lastReviewPromptAt)
        assertEquals(listOf(celebration to ReviewLaunchOutcome.LikelyShown), telemetry.asked)

        clock.advance(121.days)
        assertFalse(coordinator.clearToAsk(ReviewMoment.Celebration))
        assertEquals(ReviewHold.AskedRecently, telemetry.skipped.last().second)

        clock.advance(2.days)
        assertTrue(coordinator.clearToAsk(ReviewMoment.Celebration))
    }

    @Test
    fun noWindow_givesTheAskBack() = runUnitTest {
        cache.state.value = AppData(lastReviewPromptAt = 1L)
        settledIn()
        launcher.outcome = ReviewLaunchOutcome.NoForegroundUi

        assertEquals(ReviewLaunchOutcome.NoForegroundUi, coordinator.ask(ReviewMoment.Celebration))

        assertEquals(1L, cache.get().lastReviewPromptAt, "the store was never asked")
        assertEquals(ReviewLaunchOutcome.NoForegroundUi, telemetry.asked.single().second)
    }

    @Test
    fun neverOffline() = runUnitTest {
        settledIn()
        appState.isOffline.value = true

        assertFalse(coordinator.clearToAsk(ReviewMoment.Celebration))
        assertEquals(ReviewHold.Offline, telemetry.skipped.last().second)
    }

    @Test
    fun neverWithinHalfAnHourOfTrouble() = runUnitTest {
        settledIn()
        trouble.note()
        clock.advance(29.minutes)

        assertFalse(coordinator.clearToAsk(ReviewMoment.Celebration))
        assertEquals(ReviewHold.Trouble, telemetry.skipped.last().second)

        clock.advance(2.minutes)
        assertTrue(coordinator.clearToAsk(ReviewMoment.Celebration))
    }

    @Test
    fun troubleIs_anErrorLine_orAFailedStep() {
        fun entry(level: LogLevel, event: String? = null) = LogEntry(
            level = level,
            tag = null,
            message = null,
            throwable = null,
            context = LogContext(emptyMap(), event?.let { mapOf(EXTRA_APP_EVENT to it) } ?: emptyMap()),
        )

        assertTrue(RecentTrouble.isTrouble(entry(LogLevel.Error)))
        assertTrue(RecentTrouble.isTrouble(entry(LogLevel.Info, "net.backend_unreachable")))
        assertTrue(RecentTrouble.isTrouble(entry(LogLevel.Info, "barcode.miss")))
        assertFalse(RecentTrouble.isTrouble(entry(LogLevel.Warn)))
        assertFalse(RecentTrouble.isTrouble(entry(LogLevel.Info, "prompt.shown")))
    }

    @Test
    fun goingOffline_countsAsTrouble() = runUnitTest {
        settledIn()
        trouble
        appState.isOffline.value = true
        appState.isOffline.value = false

        assertFalse(coordinator.clearToAsk(ReviewMoment.Celebration))
        assertEquals(ReviewHold.Trouble, telemetry.skipped.last().second)
    }
}
