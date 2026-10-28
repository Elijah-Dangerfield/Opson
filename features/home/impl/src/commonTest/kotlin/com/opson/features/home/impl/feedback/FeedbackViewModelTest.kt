package com.opson.features.home.impl.feedback

import com.opson.features.home.impl.bugreport.BugReportAction
import com.opson.features.home.impl.bugreport.BugReportEvent
import com.opson.features.home.impl.bugreport.BugReportViewModel
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.common_sent
import opson.libraries.resources.generated.resources.feedback_empty
import opson.libraries.resources.generated.resources.feedback_failed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Feedback and bug reports: only a message that actually went out thanks the person and
 * closes the page. One that failed stays on the page with its text and a line saying so.
 */
class FeedbackViewModelTest : CoroutineTest() {

    @Test
    fun aSentMessage_thanksAndCloses() = runUnitTest {
        val repository = FakeFeedbackRepository()
        val vm = FeedbackViewModel(repository)
        val events = eventsOf(vm)

        vm.takeAction(FeedbackAction.MessageChanged("  Love the water card  "))
        vm.takeAction(FeedbackAction.Submit)

        assertEquals(listOf("Love the water card"), repository.sent)
        assertEquals(listOf<FeedbackEvent>(FeedbackEvent.Sent), events)
    }

    /**
     * Two taps inside one frame both reach the view model before the button
     * redraws as disabled. Actions run one after another, so the second must
     * not send again or close a second screen.
     */
    @Test
    fun aDoubleTapOnSend_sendsOnceAndClosesOnce() = runUnitTest {
        val repository = FakeFeedbackRepository()
        val vm = FeedbackViewModel(repository)
        val events = eventsOf(vm)

        vm.takeAction(FeedbackAction.MessageChanged("Love the water card"))
        vm.takeAction(FeedbackAction.Submit)
        vm.takeAction(FeedbackAction.Submit)

        assertEquals(listOf("Love the water card"), repository.sent)
        assertEquals(listOf<FeedbackEvent>(FeedbackEvent.Sent), events)
    }

    /**
     * A page whose Sent event expired (the app was in the background when the send finished)
     * stays open with Send disabled. The button says it went, so nobody retypes it.
     */
    @Test
    fun aSentPage_saysSentOnItsDisabledButton() = runUnitTest {
        val vm = FeedbackViewModel(FakeFeedbackRepository())

        vm.takeAction(FeedbackAction.MessageChanged("Love the water card"))
        vm.takeAction(FeedbackAction.Submit)

        assertFalse(vm.state.canSubmit)
        assertEquals(Res.string.common_sent, vm.state.sendLabel)
    }

    @Test
    fun aBlankMessage_isNotSent_andSaysWhy() = runUnitTest {
        val repository = FakeFeedbackRepository()
        val vm = FeedbackViewModel(repository)

        vm.takeAction(FeedbackAction.MessageChanged("   "))
        vm.takeAction(FeedbackAction.Submit)

        assertEquals(emptyList(), repository.sent)
        assertEquals(Res.string.feedback_empty, vm.state.errorMessage)
    }

    @Test
    fun aFailedSend_keepsTheMessage_andSaysSo() = runUnitTest {
        val vm = FeedbackViewModel(FakeFeedbackRepository(fails = true))
        val events = eventsOf(vm)

        vm.takeAction(FeedbackAction.MessageChanged("Love the water card"))
        vm.takeAction(FeedbackAction.Submit)

        assertEquals("Love the water card", vm.state.message)
        assertEquals(Res.string.feedback_failed, vm.state.errorMessage)
        assertFalse(vm.state.isSubmitting)
        assertEquals(emptyList(), events)
    }

    @Test
    fun typingAgain_clearsTheFailure() = runUnitTest {
        val vm = FeedbackViewModel(FakeFeedbackRepository(fails = true))
        vm.takeAction(FeedbackAction.MessageChanged("Hi"))
        vm.takeAction(FeedbackAction.Submit)

        vm.takeAction(FeedbackAction.MessageChanged("Hi there"))

        assertNull(vm.state.errorMessage)
    }

    @Test
    fun aSentBugReport_carriesItsIdsAndContext_thanksAndCloses() = runUnitTest {
        val repository = FakeFeedbackRepository()
        val vm = BugReportViewModel(
            repository,
            logId = "log-1",
            errorCode = 1200,
            contextMessage = "We couldn't load your day.",
        )
        val events = eventsOf(vm)

        vm.takeAction(BugReportAction.MessageChanged("The sheet would not open"))
        vm.takeAction(BugReportAction.Submit)

        assertEquals(listOf<Pair<String?, Int?>>("log-1" to 1200), repository.ids)
        assertEquals(listOf<String?>("We couldn't load your day."), repository.contexts)
        assertEquals(listOf<BugReportEvent>(BugReportEvent.Sent), events)
    }

    @Test
    fun aDoubleTapOnSend_sendsTheBugReportOnceAndClosesOnce() = runUnitTest {
        val repository = FakeFeedbackRepository()
        val vm = BugReportViewModel(repository)
        val events = eventsOf(vm)

        vm.takeAction(BugReportAction.MessageChanged("The sheet would not open"))
        vm.takeAction(BugReportAction.Submit)
        vm.takeAction(BugReportAction.Submit)

        assertEquals(listOf("The sheet would not open"), repository.sent)
        assertEquals(listOf<BugReportEvent>(BugReportEvent.Sent), events)
    }

    @Test
    fun aSentBugReport_saysSentOnItsDisabledButton() = runUnitTest {
        val vm = BugReportViewModel(FakeFeedbackRepository())

        vm.takeAction(BugReportAction.MessageChanged("The sheet would not open"))
        vm.takeAction(BugReportAction.Submit)

        assertFalse(vm.state.canSubmit)
        assertEquals(Res.string.common_sent, vm.state.sendLabel)
    }

    @Test
    fun aFailedBugReport_staysOnThePage() = runUnitTest {
        val vm = BugReportViewModel(FakeFeedbackRepository(fails = true))
        val events = eventsOf(vm)

        vm.takeAction(BugReportAction.MessageChanged("The sheet would not open"))
        vm.takeAction(BugReportAction.Submit)

        assertEquals(Res.string.feedback_failed, vm.state.errorMessage)
        assertEquals(emptyList(), events)
    }

    private fun <E : Any> TestScope.eventsOf(vm: SEAViewModel<*, E, *>): List<E> {
        val events = mutableListOf<E>()
        backgroundScope.launch { vm.eventFlow.collect { events += it } }
        return events
    }
}

private class FakeFeedbackRepository(private val fails: Boolean = false) : FeedbackRepository {
    val sent = mutableListOf<String>()
    val ids = mutableListOf<Pair<String?, Int?>>()
    val contexts = mutableListOf<String?>()

    override suspend fun submitFeedback(
        message: String,
        isBugReport: Boolean,
        logId: String?,
        errorCode: Int?,
        contextMessage: String?,
    ): Result<Unit> {
        if (fails) return Result.failure(IllegalStateException("telemetry is down"))
        sent += message
        ids += logId to errorCode
        contexts += contextMessage
        return Result.success(Unit)
    }
}
