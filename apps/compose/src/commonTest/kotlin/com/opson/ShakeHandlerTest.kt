package com.opson

import com.opson.libraries.core.ShakeDetector
import com.opson.libraries.core.ShakeEvent
import com.opson.libraries.core.ShakeMessage
import com.opson.libraries.core.ShakeMessageContext
import com.opson.libraries.core.ShakeMessageProvider
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.UpdateProfileOutcome
import com.opson.libraries.navigation.NavigationOptions
import com.opson.libraries.navigation.Route
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.RouterBatch
import com.opson.libraries.navigation.ShakeDialogRoute
import com.opson.libraries.navigation.TabRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/** Shake-to-report has to work exactly when things are broken, profile included. */
class ShakeHandlerTest : CoroutineTest() {

    private val detector = FakeShakeDetector()
    private val router = RecordingRouter()

    @Test
    fun aShakeWhileTheProfileIsStillResolving_stillOpensTheDialog() = runUnitTest {
        val handler = handler(UnresolvedProfileRepository)
        handler.start()

        detector.shake()
        runCurrent()
        assertTrue(router.dialogs.isEmpty(), "waits briefly for the name")

        advanceTimeBy(ShakeNameWaitMillis + 1)

        assertEquals(listOf(ShakeDialogRoute(headline = "Hi there", subtext = null)), router.dialogs)
        handler.stop()
    }

    private fun handler(profiles: ProfileRepository) = ShakeHandler(
        shakeDetector = detector,
        shakeMessageProvider = GreetingProvider,
        profileRepository = profiles,
        router = router,
        appScope = AppCoroutineScope(dispatchers),
    )
}

private class FakeShakeDetector : ShakeDetector {
    private val events = MutableSharedFlow<ShakeEvent>(extraBufferCapacity = 1)
    override val shakeEvents: Flow<ShakeEvent> = events
    override fun start() = Unit
    override fun stop() = Unit
    fun shake() {
        events.tryEmit(ShakeEvent(timestampMs = 0))
    }
}

private object GreetingProvider : ShakeMessageProvider {
    override fun getMessage(context: ShakeMessageContext) = ShakeMessage(headline = "Hi ${context.userName ?: "there"}")
}

/** The first profile not in yet: `/v1/me` still on the wire, or a resolve that threw. */
private object UnresolvedProfileRepository : ProfileRepository {
    override suspend fun current(): Profile = awaitCancellation()
    override fun observe(): Flow<Profile> = flow { awaitCancellation() }
    override suspend fun update(displayName: String?): UpdateProfileOutcome = error("unused")
}

private class RecordingRouter : Router {
    val dialogs = mutableListOf<ShakeDialogRoute>()
    override fun navigate(route: Route, options: NavigationOptions) {
        if (route is ShakeDialogRoute) dialogs += route
    }
    override fun goBack() = Unit
    override fun popBackTo(route: Route, inclusive: Boolean) = Unit
    override fun switchTab(route: TabRoute) = Unit
    override fun reselectTab(route: TabRoute) = Unit
    override val tabReselects: SharedFlow<TabRoute> = MutableSharedFlow()
    override fun enterTab(route: TabRoute) = Unit
    override fun batch(block: RouterBatch.() -> Unit) = Unit
    override fun openWebLink(url: String) = Unit
    override fun shareFile(name: String, mimeType: String, bytes: ByteArray) = Unit
}
