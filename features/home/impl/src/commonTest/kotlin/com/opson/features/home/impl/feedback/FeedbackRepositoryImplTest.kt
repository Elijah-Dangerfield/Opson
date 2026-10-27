package com.opson.features.home.impl.feedback

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.opson.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A report crash reporting dropped is a failed send, not a sent one. */
class FeedbackRepositoryImplTest : CoroutineTest() {

    @Test
    fun aDroppedReport_isAFailure() = runUnitTest {
        val repository = FeedbackRepositoryImpl(FakeTelemetry(delivers = false))

        assertTrue(repository.submitFeedback("The water card is great", isBugReport = false).isFailure)
    }

    @Test
    fun aDeliveredReport_isASuccess() = runUnitTest {
        val repository = FeedbackRepositoryImpl(FakeTelemetry(delivers = true))

        assertTrue(repository.submitFeedback("The water card is great", isBugReport = false).isSuccess)
    }

    @Test
    fun aBugReport_carriesTheContextThePageShowed() = runUnitTest {
        val telemetry = FakeTelemetry(delivers = true)

        FeedbackRepositoryImpl(telemetry).submitFeedback(
            message = "The sheet would not open",
            isBugReport = true,
            contextMessage = "We couldn't load your day.",
        )

        assertEquals(listOf("Context: We couldn't load your day.\n\nThe sheet would not open"), telemetry.messages)
    }

    @Test
    fun noContext_sendsJustWhatTheyTyped() = runUnitTest {
        val telemetry = FakeTelemetry(delivers = true)

        FeedbackRepositoryImpl(telemetry).submitFeedback("The water card is great", isBugReport = false)

        assertEquals(listOf("The water card is great"), telemetry.messages)
    }

    private class FakeTelemetry(private val delivers: Boolean) : Telemetry {
        val messages = mutableListOf<String>()

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
        ): Boolean {
            messages += message
            return delivers
        }
    }
}
