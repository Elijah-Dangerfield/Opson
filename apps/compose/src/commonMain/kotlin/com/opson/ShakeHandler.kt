package com.opson

import com.opson.libraries.core.ShakeDetector
import com.opson.libraries.core.ShakeEvent
import com.opson.libraries.core.ShakeMessageContext
import com.opson.libraries.core.ShakeMessageProvider
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.displayNameOrNull
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.ShakeDialogRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null
    private var isShowingDialog = false
    // Process-local flavor counter for the shake easter-egg copy.
    private var shakeCount = 0

    fun start() {
        // Every foreground cycle used to launch another collector that nothing
        // ever cancelled. They then competed for the same events.
        collectJob?.cancel()
        shakeDetector.start()
        collectJob = scope.launch {
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

    private suspend fun handleShake(event: ShakeEvent) {
        if (isShowingDialog) return

        val profile = profileRepository.current()

        val context = ShakeMessageContext(
            shakeCount = shakeCount,
            intensity = event.intensity,
            isLateNight = false,
            isFirstSession = false,
            userName = profile.displayNameOrNull,
        )

        val message = shakeMessageProvider.getMessage(context)

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
