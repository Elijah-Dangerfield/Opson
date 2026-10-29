package com.opson.features.home.impl.feedback

import com.opson.libraries.core.AppState
import com.opson.libraries.flowroutines.DispatcherProvider
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.opson.Telemetry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A report crash reporting dropped is a failed send, not a sent one. */
class FeedbackRepositoryImplTest : CoroutineTest() {

    private val online = FakeAppState(offline = false)

    @Test
    fun aDroppedReport_isAFailure() = runUnitTest {
        val repository = FeedbackRepositoryImpl(FakeTelemetry(delivers = false), dispatchers, online)

        assertTrue(repository.submitFeedback("The water card is great", isBugReport = false).isFailure)
    }

    @Test
    fun aDeliveredReport_isASuccess() = runUnitTest {
        val repository = FeedbackRepositoryImpl(FakeTelemetry(delivers = true), dispatchers, online)

        assertTrue(repository.submitFeedback("The water card is great", isBugReport = false).isSuccess)
    }

    @Test
    fun aBugReport_carriesTheContextThePageShowed() = runUnitTest {
        val telemetry = FakeTelemetry(delivers = true)

        FeedbackRepositoryImpl(telemetry, dispatchers, online).submitFeedback(
            message = "The sheet would not open",
            isBugReport = true,
            contextMessage = "We couldn't load your day.",
        )

        assertEquals(listOf("Context: We couldn't load your day.\n\nThe sheet would not open"), telemetry.messages)
    }

    @Test
    fun noContext_sendsJustWhatTheyTyped() = runUnitTest {
        val telemetry = FakeTelemetry(delivers = true)

        FeedbackRepositoryImpl(telemetry, dispatchers, online).submitFeedback("The water card is great", isBugReport = false)

        assertEquals(listOf("The water card is great"), telemetry.messages)
    }

    /** Crash reporting keeps it on the phone and sends it later, so offline is not a failure. */
    @Test
    fun offline_theReportIsQueued() = runUnitTest {
        val repository = FeedbackRepositoryImpl(FakeTelemetry(delivers = true), dispatchers, FakeAppState(offline = true))

        assertEquals(FeedbackDelivery.Queued, repository.submitFeedback("Hi", isBugReport = false).getOrNull())
    }

    @Test
    fun online_theReportIsSent() = runUnitTest {
        val repository = FeedbackRepositoryImpl(FakeTelemetry(delivers = true), dispatchers, online)

        assertEquals(FeedbackDelivery.Sent, repository.submitFeedback("Hi", isBugReport = false).getOrNull())
    }

    @Test
    fun theScreenshotsAndLogOnThePage_goWithTheReport() = runUnitTest {
        val telemetry = FakeTelemetry(delivers = true)
        val shot = byteArrayOf(1, 2, 3)

        FeedbackRepositoryImpl(telemetry, dispatchers, online)
            .submitFeedback("Hi", isBugReport = true, screenshots = listOf(shot), sessionLog = "a log")

        assertEquals(listOf(listOf(shot)), telemetry.screenshots)
        assertEquals(listOf<String?>("a log"), telemetry.logs)
    }

    /** Capturing copies the session log and builds the event; the view model calls from main. */
    @Test
    fun theReportIsCapturedOnTheIoDispatcher() = runUnitTest {
        val io = MarkingDispatcher(StandardTestDispatcher(testScheduler))
        val telemetry = FakeTelemetry(delivers = true, io = io)
        val onlyIoIsMarked = object : DispatcherProvider by dispatchers {
            override val io: CoroutineDispatcher = io
        }

        FeedbackRepositoryImpl(telemetry, onlyIoIsMarked, online).submitFeedback("Hi", isBugReport = false)

        assertEquals(listOf(true), telemetry.capturedOnIo)
    }

    private class FakeTelemetry(
        private val delivers: Boolean,
        private val io: MarkingDispatcher? = null,
    ) : Telemetry {
        val messages = mutableListOf<String>()
        val screenshots = mutableListOf<List<ByteArray>>()
        val logs = mutableListOf<String?>()
        val capturedOnIo = mutableListOf<Boolean>()

        override fun initialize() = Unit
        override fun setUser(id: String?) = Unit
        override fun setCurrentRoute(route: String) = Unit
        override fun setSession(sessionId: String) = Unit
        override fun setInstallId(installId: String) = Unit
        override fun setContext(key: String, value: String?) = Unit
        override fun captureUserFeedback(
            message: String,
            isBugReport: Boolean,
            eventId: String?,
            errorCode: Int?,
            email: String?,
            screenshots: List<ByteArray>,
            sessionLog: String?,
        ): Boolean {
            messages += message
            this.screenshots += screenshots
            logs += sessionLog
            io?.let { capturedOnIo += it.isRunningABlock }
            return delivers
        }
    }

    private class FakeAppState(offline: Boolean) : AppState {
        override val isOffline: StateFlow<Boolean> = MutableStateFlow(offline)
    }

    /** Runs on [delegate] and says whether a block it dispatched is running right now. */
    private class MarkingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var isRunningABlock = false
            private set

        override fun isDispatchNeeded(context: CoroutineContext) = true

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            delegate.dispatch(context) {
                isRunningABlock = true
                try {
                    block.run()
                } finally {
                    isRunningABlock = false
                }
            }
        }
    }
}
