package com.opson.features.home.impl.report

import com.opson.features.home.impl.bugreport.BugReportAction
import com.opson.features.home.impl.bugreport.BugReportEvent
import com.opson.features.home.impl.bugreport.BugReportViewModel
import com.opson.features.home.impl.feedback.FakeAttachmentsSource
import com.opson.features.home.impl.feedback.FakeFeedbackRepository
import com.opson.features.home.impl.feedback.FeedbackAction
import com.opson.features.home.impl.feedback.FeedbackDelivery
import com.opson.features.home.impl.feedback.FeedbackEvent
import com.opson.features.home.impl.feedback.FeedbackViewModel
import com.opson.libraries.core.DeviceDescription
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.opson.Telemetry
import com.opson.libraries.ui.system.ScreenCapture
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What a report carries besides the words: each thing is on the page before it goes, what the
 * page shows is what is sent, and anything taken off stays home.
 */
class ReportAttachmentsTest : CoroutineTest() {

    @Test
    fun aReportFromAShake_opensWithItsScreenshot_andSendsIt() = runUnitTest {
        val repository = FakeFeedbackRepository()
        val vm = bugReport(repository, FakeAttachmentsSource(held = SHOT), shakeScreenshot = true)
        runCurrent()

        assertSame(SHOT, vm.state.attachments.screenshots.single().jpeg)
        send(vm)

        assertSame(SHOT, repository.screenshots.single().single())
    }

    @Test
    fun aRemovedScreenshot_isNotSent() = runUnitTest {
        val repository = FakeFeedbackRepository()
        val vm = bugReport(repository, FakeAttachmentsSource(held = SHOT), shakeScreenshot = true)
        runCurrent()

        vm.takeAction(BugReportAction.Attachment(AttachmentAction.ScreenshotRemoved(0)))
        send(vm)

        assertEquals(listOf(emptyList()), repository.screenshots)
    }

    @Test
    fun theLogShown_isTheLogSent() = runUnitTest {
        val repository = FakeFeedbackRepository()
        val vm = bugReport(repository, FakeAttachmentsSource(ReportAttachments(log = LOG)))
        runCurrent()

        send(vm)

        assertEquals(listOf<String?>(LOG), repository.logs)
    }

    @Test
    fun aLogSwitchedOff_isNotSent() = runUnitTest {
        val repository = FakeFeedbackRepository()
        val vm = FeedbackViewModel(repository, FakeAttachmentsSource(ReportAttachments(log = LOG)))
        runCurrent()

        vm.takeAction(FeedbackAction.Attachment(AttachmentAction.LogIncluded(false)))
        vm.takeAction(FeedbackAction.MessageChanged("Love the water card"))
        vm.takeAction(FeedbackAction.Submit)

        assertEquals(listOf<String?>(null), repository.logs)
    }

    @Test
    fun addingAScreenshot_opensThePicker_untilThereAreThree() = runUnitTest {
        val vm = bugReport(FakeFeedbackRepository(), FakeAttachmentsSource())
        val events = eventsOf(vm)
        runCurrent()

        repeat(ReportAttachments.MAX_SCREENSHOTS + 1) {
            vm.takeAction(BugReportAction.Attachment(AttachmentAction.AddScreenshotTapped))
            vm.takeAction(BugReportAction.Attachment(AttachmentAction.ScreenshotPicked(SHOT)))
        }

        assertEquals(ReportAttachments.MAX_SCREENSHOTS, vm.state.attachments.screenshots.size)
        assertEquals(List(ReportAttachments.MAX_SCREENSHOTS) { BugReportEvent.PickScreenshot }, events)
    }

    @Test
    fun aPictureThatWontLoad_saysSo_untilTheNextTry() = runUnitTest {
        val vm = bugReport(FakeFeedbackRepository(), FakeAttachmentsSource())
        runCurrent()

        vm.takeAction(BugReportAction.Attachment(AttachmentAction.ScreenshotPickFailed))
        assertTrue(vm.state.attachments.pickFailed)

        vm.takeAction(BugReportAction.Attachment(AttachmentAction.AddScreenshotTapped))
        assertTrue(!vm.state.attachments.pickFailed)
    }

    @Test
    fun theLogSheet_onlyOpensWithALogToShow() {
        assertTrue(!ReportAttachments().reduce(AttachmentAction.ViewLogTapped).showingLog)
        assertTrue(ReportAttachments(log = LOG).reduce(AttachmentAction.ViewLogTapped).showingLog)
    }

    /** A picture picked before the log arrived is not lost when it does. */
    @Test
    fun attachmentsArrivingLate_keepWhatWasPicked() {
        val onPage = ReportAttachments(screenshots = listOf(Screenshot(SHOT)), includeLog = false)

        val merged = ReportAttachments(log = LOG).mergedWith(onPage)

        assertSame(SHOT, merged.screenshots.single().jpeg)
        assertNull(merged.sentLog)
    }

    @Test
    fun offline_thanksWithItGoingOutLater() = runUnitTest {
        val vm = FeedbackViewModel(FakeFeedbackRepository(delivery = FeedbackDelivery.Queued), FakeAttachmentsSource())
        val events = eventsOf(vm)

        vm.takeAction(FeedbackAction.MessageChanged("Love the water card"))
        vm.takeAction(FeedbackAction.Submit)

        assertEquals(listOf<FeedbackEvent>(FeedbackEvent.Sent(queued = true)), events)
    }

    @Test
    fun theSource_givesTheShakeItsPicture_andTheLogAndPhone() = runUnitTest {
        val capture = FakeScreenCapture().apply { hold(SHOT) }

        val attachments = source(capture).initial(shakeScreenshot = true)

        assertSame(SHOT, attachments.screenshots.single().jpeg)
        assertEquals(LOG, attachments.log)
        assertEquals("Pixel 8, Android 15", attachments.device?.phone)
        assertNull(capture.takeHeld(), "taken, so a later report can't pick it up")
    }

    /** A report opened any other way drops a picture an abandoned shake left behind. */
    @Test
    fun theSource_dropsAStaleShakePicture() = runUnitTest {
        val capture = FakeScreenCapture().apply { hold(SHOT) }

        val attachments = source(capture).initial(shakeScreenshot = false)

        assertEquals(emptyList(), attachments.screenshots)
        assertNull(capture.takeHeld())
    }

    private fun source(capture: ScreenCapture) = ReportAttachmentsSourceImpl(
        telemetry = LogTelemetry,
        device = object : DeviceDescription {
            override fun summary() = "Pixel 8, Android 15"
        },
        screenCapture = capture,
        dispatchers = dispatchers,
    )

    private fun bugReport(repository: FakeFeedbackRepository, source: ReportAttachmentsSource, shakeScreenshot: Boolean = false) =
        BugReportViewModel(repository, source, shakeScreenshot = shakeScreenshot)

    private fun send(vm: BugReportViewModel) {
        vm.takeAction(BugReportAction.MessageChanged("The water card stopped counting"))
        vm.takeAction(BugReportAction.Submit)
    }

    private fun <E : Any> TestScope.eventsOf(vm: SEAViewModel<*, E, *>): List<E> {
        val events = mutableListOf<E>()
        backgroundScope.launch { vm.eventFlow.collect { events += it } }
        return events
    }

    private class FakeScreenCapture : ScreenCapture {
        private var held: ByteArray? = null
        override suspend fun capture(): ByteArray? = null
        override fun hold(jpeg: ByteArray?) {
            held = jpeg
        }
        override fun takeHeld(): ByteArray? = held.also { held = null }
    }

    private object LogTelemetry : Telemetry {
        override fun initialize() = Unit
        override fun setUser(id: String?) = Unit
        override fun setCurrentRoute(route: String) = Unit
        override fun setSession(sessionId: String) = Unit
        override fun setInstallId(installId: String) = Unit
        override fun setContext(key: String, value: String?) = Unit
        override fun recentLog(): String = LOG
        override fun captureUserFeedback(
            message: String,
            isBugReport: Boolean,
            eventId: String?,
            errorCode: Int?,
            email: String?,
            screenshots: List<ByteArray>,
            sessionLog: String?,
        ) = true
    }

    private companion object {
        val SHOT = byteArrayOf(1, 2, 3)
        const val LOG = "2026-10-29T10:01:02Z INFO Today: day loaded"
    }
}
