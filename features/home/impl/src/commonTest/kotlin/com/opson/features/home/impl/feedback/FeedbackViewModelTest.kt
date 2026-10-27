package com.opson.features.home.impl.feedback

import com.opson.features.home.impl.bugreport.BugReportAction
import com.opson.features.home.impl.bugreport.BugReportEvent
import com.opson.features.home.impl.bugreport.BugReportViewModel
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.feedback_failed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Feedback and bug reports: only a message that actually went out counts, thanks the person and
 * closes the page. One that failed stays on the page with its text and a line saying so.
 */
class FeedbackViewModelTest : CoroutineTest() {

    @Test
    fun aSentMessage_countsThanksAndCloses() = runUnitTest {
        val repository = FakeFeedbackRepository()
        val cache = FakeAppCache()
        val vm = FeedbackViewModel(repository, cache)
        val events = eventsOf(vm)

        vm.takeAction(FeedbackAction.MessageChanged("  Love the water card  "))
        vm.takeAction(FeedbackAction.Submit)

        assertEquals(listOf("Love the water card"), repository.sent)
        assertEquals(1, cache.get().feedbacksGiven)
        assertEquals(listOf<FeedbackEvent>(FeedbackEvent.Sent), events)
    }

    @Test
    fun aFailedSend_keepsTheMessage_saysSo_andCountsNothing() = runUnitTest {
        val cache = FakeAppCache()
        val vm = FeedbackViewModel(FakeFeedbackRepository(fails = true), cache)
        val events = eventsOf(vm)

        vm.takeAction(FeedbackAction.MessageChanged("Love the water card"))
        vm.takeAction(FeedbackAction.Submit)

        assertEquals("Love the water card", vm.state.message)
        assertEquals(Res.string.feedback_failed, vm.state.errorMessage)
        assertFalse(vm.state.isSubmitting)
        assertEquals(0, cache.get().feedbacksGiven)
        assertEquals(emptyList(), events)
    }

    @Test
    fun typingAgain_clearsTheFailure() = runUnitTest {
        val vm = FeedbackViewModel(FakeFeedbackRepository(fails = true), FakeAppCache())
        vm.takeAction(FeedbackAction.MessageChanged("Hi"))
        vm.takeAction(FeedbackAction.Submit)

        vm.takeAction(FeedbackAction.MessageChanged("Hi there"))

        assertNull(vm.state.errorMessage)
    }

    @Test
    fun aSentBugReport_carriesItsIds_countsThanksAndCloses() = runUnitTest {
        val repository = FakeFeedbackRepository()
        val cache = FakeAppCache()
        val vm = BugReportViewModel(repository, cache, logId = "log-1", errorCode = 1200)
        val events = eventsOf(vm)

        vm.takeAction(BugReportAction.MessageChanged("The sheet would not open"))
        vm.takeAction(BugReportAction.Submit)

        assertEquals(listOf<Pair<String?, Int?>>("log-1" to 1200), repository.ids)
        assertEquals(1, cache.get().bugsReported)
        assertEquals(listOf<BugReportEvent>(BugReportEvent.Sent), events)
    }

    @Test
    fun aFailedBugReport_staysOnThePage() = runUnitTest {
        val cache = FakeAppCache()
        val vm = BugReportViewModel(FakeFeedbackRepository(fails = true), cache)
        val events = eventsOf(vm)

        vm.takeAction(BugReportAction.MessageChanged("The sheet would not open"))
        vm.takeAction(BugReportAction.Submit)

        assertEquals(Res.string.feedback_failed, vm.state.errorMessage)
        assertEquals(0, cache.get().bugsReported)
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

    override suspend fun submitFeedback(message: String, isBugReport: Boolean, logId: String?, errorCode: Int?): Result<Unit> {
        if (fails) return Result.failure(IllegalStateException("telemetry is down"))
        sent += message
        ids += logId to errorCode
        return Result.success(Unit)
    }
}

private class FakeAppCache : AppCache {
    private val state = MutableStateFlow(AppData())
    override val updates: Flow<AppData> = state
    override suspend fun get(): AppData = state.value
    override suspend fun set(value: AppData) {
        state.value = value
    }
    override suspend fun clear() {
        state.value = AppData()
    }
}
