package com.opson

import com.opson.libraries.core.ShakeDetector
import com.opson.libraries.core.ShakeEvent
import com.opson.libraries.core.ShakeMessageContext
import com.opson.libraries.core.ShakeMessageProvider
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.displayNameOrNull
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.ShakeDialogRoute
import com.opson.libraries.core.Catching
import com.opson.libraries.ui.system.ScreenCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** How long a shake waits for the person's name before showing the dialog without it. */
internal const val ShakeNameWaitMillis = 500L

/**
 * Owns the shake-to-report gesture: listens while the app is in the
 * foreground, and pushes the shake dialog at most once at a time.
 *
 * [start] and [stop] must be driven by the app's *lifecycle*, not by the
 * composition — see [ShakeDetector].
 */
@Inject
@SingleIn(AppScope::class)
class ShakeHandler(
    private val shakeDetector: ShakeDetector,
    private val shakeMessageProvider: ShakeMessageProvider,
    private val profileRepository: ProfileRepository,
    private val router: Router,
    private val appScope: AppCoroutineScope,
    /** Internal so App.kt can install its source at the root without the iOS header seeing it. */
    internal val screenCapture: ScreenCapture,
) {
    private var collectJob: Job? = null
    private var isShowingDialog = false
    // Process-local flavor counter for the shake easter-egg copy.
    private var shakeCount = 0

    fun start() {
        // Every foreground cycle used to launch another collector that nothing
        // ever cancelled. They then competed for the same events.
        collectJob?.cancel()
        shakeDetector.start()
        // The app scope, so a failed shake is logged instead of ending the process; Main for the router.
        collectJob = appScope.launch(Dispatchers.Main) {
            shakeDetector.shakeEvents.collect { event ->
                handleShake(event)
            }
        }
    }

    fun stop() {
        shakeDetector.stop()
        collectJob?.cancel()
        collectJob = null
    }

    /**
     * Clears the one-dialog-at-a-time latch. The dialog destination calls this
     * when it leaves the back stack, however it left — dismiss button, scrim,
     * system back, or navigating on to the bug report. Miss any of those and
     * the latch stays set and shaking does nothing for the rest of the process.
     */
    fun onDialogDismissed() {
        isShowingDialog = false
    }

    private suspend fun handleShake(event: ShakeEvent) = coroutineScope {
        if (isShowingDialog) return@coroutineScope

        // The picture first, before the dialog covers what the person was looking at, alongside
        // the name lookup so neither waits on the other. Held for the bug report Report a bug opens.
        // A capture that fails or never answers costs the report its picture, never the dialog.
        val screenshot = async { Catching { screenCapture.capture() }.getOrNull() }
        // The name only flavours the copy. The first profile is a /v1/me round trip, and a resolve
        // that throws never emits, so an unbounded wait here left shake-to-report dead for the
        // session, the moment someone most wants to report something.
        val userName = withTimeoutOrNull(ShakeNameWaitMillis) { profileRepository.current() }?.displayNameOrNull

        val context = ShakeMessageContext(
            shakeCount = shakeCount,
            intensity = event.intensity,
            userName = userName,
        )

        val message = shakeMessageProvider.getMessage(context)
        screenCapture.hold(screenshot.await())

        isShowingDialog = true
        router.navigate(
            ShakeDialogRoute(
                headline = message.headline,
                subtext = message.subtext,
            )
        )

        shakeCount++
    }
}
