package com.opson.features.home.impl.report

import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.DeviceDescription
import com.opson.libraries.core.versionString
import com.opson.libraries.flowroutines.DispatcherProvider
import com.opson.libraries.opson.Telemetry
import com.opson.libraries.ui.system.ScreenCapture
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** A picture on a report. Equal only to itself, like `MealPhoto`: comparing JPEGs on every state change would be waste. */
class Screenshot(val jpeg: ByteArray)

/**
 * Everything a bug report or feedback sends besides the words, shown on the page before it goes,
 * so the person can see each thing and take off what they would rather keep.
 *
 * @property log the recent app log, read once as the page opens. The page shows this text and
 *   sends this text, so what was read is what goes out.
 * @property device the version and phone line. Crash reporting records the same on every event,
 *   so it is shown as always sent rather than offered as a choice it could not keep.
 */
data class ReportAttachments(
    val screenshots: List<Screenshot> = emptyList(),
    val log: String = "",
    val includeLog: Boolean = true,
    val device: Device? = null,
    val showingLog: Boolean = false,
    val pickFailed: Boolean = false,
) {
    data class Device(val version: String, val phone: String)

    val logLines: List<String> get() = if (log.isBlank()) emptyList() else log.lines()
    val canAddScreenshot: Boolean get() = screenshots.size < MAX_SCREENSHOTS

    /** What [captureUserFeedback][com.opson.libraries.opson.Telemetry.captureUserFeedback] gets: null when taken off. */
    val sentLog: String? get() = log.takeIf { includeLog && it.isNotBlank() }

    /** These, the first attachments in, over what the page already had: pictures picked and a log taken off before they arrived. */
    fun mergedWith(onPage: ReportAttachments): ReportAttachments = copy(
        screenshots = (screenshots + onPage.screenshots).take(MAX_SCREENSHOTS),
        includeLog = onPage.includeLog,
    )

    fun reduce(action: AttachmentAction): ReportAttachments = when (action) {
        // The page asks the picker; nothing changes until a picture comes back.
        AttachmentAction.AddScreenshotTapped -> copy(pickFailed = false)
        is AttachmentAction.ScreenshotPicked ->
            if (canAddScreenshot) copy(screenshots = screenshots + Screenshot(action.jpeg), pickFailed = false) else this
        AttachmentAction.ScreenshotPickFailed -> copy(pickFailed = true)
        is AttachmentAction.ScreenshotRemoved -> copy(screenshots = screenshots.filterIndexed { i, _ -> i != action.index })
        is AttachmentAction.LogIncluded -> copy(includeLog = action.include)
        AttachmentAction.ViewLogTapped -> copy(showingLog = log.isNotBlank())
        AttachmentAction.LogClosed -> copy(showingLog = false)
    }

    companion object {
        /** Crash reporting takes three at most (`AppTelemetry`), so the page offers no more. */
        const val MAX_SCREENSHOTS = 3
    }
}

sealed interface AttachmentAction {
    data object AddScreenshotTapped : AttachmentAction
    class ScreenshotPicked(val jpeg: ByteArray) : AttachmentAction
    data object ScreenshotPickFailed : AttachmentAction
    data class ScreenshotRemoved(val index: Int) : AttachmentAction
    data class LogIncluded(val include: Boolean) : AttachmentAction
    data object ViewLogTapped : AttachmentAction
    data object LogClosed : AttachmentAction
}

/** What a report page starts with. */
interface ReportAttachmentsSource {
    /** [shakeScreenshot]: opened from the shake dialog, so it starts with the picture the shake took. */
    suspend fun initial(shakeScreenshot: Boolean = false): ReportAttachments
}

@Inject
@ContributesBinding(AppScope::class)
class ReportAttachmentsSourceImpl(
    private val telemetry: Telemetry,
    private val device: DeviceDescription,
    private val screenCapture: ScreenCapture,
    private val dispatchers: DispatcherProvider,
) : ReportAttachmentsSource {
    override suspend fun initial(shakeScreenshot: Boolean): ReportAttachments {
        // Taken on any open, so a report from More can't pick up a picture an abandoned shake left.
        val held = screenCapture.takeHeld()
        // Copies up to 500 lines under a lock; off the main thread like the send.
        val log = withContext(dispatchers.io) { telemetry.recentLog() }
        return ReportAttachments(
            screenshots = listOfNotNull(held?.takeIf { shakeScreenshot }?.let(::Screenshot)),
            log = log,
            device = ReportAttachments.Device(version = BuildInfo.versionString(), phone = device.summary()),
        )
    }
}
