package com.opson.libraries.navigation.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.throwIfDebug
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.observeWithLifecycle
import com.opson.libraries.navigation.BlockingErrorRoute
import com.opson.libraries.navigation.NavigationOptions
import com.opson.libraries.navigation.navigateUnlessShowing
import com.opson.libraries.navigation.Route
import com.opson.libraries.navigation.NavigationRecovery
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.RouterBatch
import com.opson.libraries.navigation.TabRoute
import com.opson.libraries.navigation.enterTab
import com.opson.libraries.navigation.reselectTab
import com.opson.libraries.navigation.switchTab
import com.opson.libraries.navigation.WebLinkLauncher
import com.opson.libraries.navigation.FileShareLauncher
import com.opson.libraries.navigation.NavigableWhileBlocked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = Router::class)
@ContributesBinding(AppScope::class, boundType = NavigationRecovery::class)
@Inject
class DelegatingRouter(
    private val appScope: AppCoroutineScope,
    private val webLinkLauncher: WebLinkLauncher,
    private val fileShareLauncher: FileShareLauncher,
) : Router, NavigationRecovery {

    private val logger = KLog.withTag("DelegatingRouter")
    private val navigationRequests = Channel<NavHostController.() -> Unit>(Channel.UNLIMITED)

    // Buffered, not replayed, so a tab that subscribes later never scrolls for an old tap. If taps
    // outrun the collector, the newest one is the one that counts.
    private val _tabReselects = MutableSharedFlow<TabRoute>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val tabReselects: SharedFlow<TabRoute> = _tabReselects.asSharedFlow()

    private var navController: NavHostController? = null
    private var processingJob: Job? = null

    private fun clearNavController(controller: NavHostController) {
        if (navController === controller) {
            logger.d { "Clearing nav controller" }
            processingJob?.cancel()
            processingJob = null
            navController = null
        }
    }

    private fun setNavController(controller: NavHostController, lifecycle: Lifecycle) {
        logger.d { "Setting nav controller" }
        navController = controller
        processingJob?.cancel()
        processingJob = appScope.launch {
            controller.awaitGraphAttachment()
            navigationRequests
                .receiveAsFlow()
                .observeWithLifecycle(
                    lifecycle = lifecycle,
                    tag = "DelegatingRouter navigation queue",
                ) { command ->
                    command(controller)
                }
        }
    }

    override fun navigate(route: Route, options: NavigationOptions) {
        enqueueNavigation(
            description = "navigate to ${route.nameForLogs()}",
            route = route,
        ) {
            executeNavigate(route, options)
        }
    }

    override fun switchTab(route: TabRoute) {
        enqueueNavigation(
            description = "switchTab to ${route.nameForLogs()}",
            route = route,
        ) {
            // `this.`, the controller: unqualified, it could read as this router's own switchTab.
            this.switchTab(route)
        }
    }

    override fun reselectTab(route: TabRoute) {
        enqueueNavigation(
            description = "reselectTab ${route.nameForLogs()}",
            route = route,
        ) {
            this.reselectTab(route) { _tabReselects.tryEmit(route) }
        }
    }

    override fun enterTab(route: TabRoute) {
        enqueueNavigation(
            description = "enterTab to ${route.nameForLogs()}",
            route = route,
        ) {
            this.enterTab(route)
        }
    }

    override fun batch(block: RouterBatch.() -> Unit) {
        enqueueNavigation("batch") {
            // Every call in the block runs against this controller inside this one queued
            // command, so nothing in the block can be left behind in the queue if the caller's
            // scope dies partway through. Each route still passes the blocking-error gate.
            val controller = this
            val scope = object : RouterBatch {
                override fun navigate(route: Route, options: NavigationOptions) {
                    if (controller.shouldBlockNavigation(route)) return
                    controller.executeNavigate(route, options)
                }

                override fun switchTab(route: TabRoute) {
                    if (controller.shouldBlockNavigation(route)) return
                    controller.switchTab(route)
                }

                override fun goBack() {
                    controller.popBackStack()
                }

                override fun popBackTo(route: Route, inclusive: Boolean) {
                    controller.popBackStack(route, inclusive)
                }
            }
            scope.block()
        }
    }

    private fun NavHostController.executeNavigate(route: Route, options: NavigationOptions) {
        val builder: NavOptionsBuilder.() -> Unit = {
            if (options.clearBackStack) {
                popUpTo(0) { inclusive = true }
            }
            if (options.launchSingleTop) {
                launchSingleTop = true
            }
            if (options.restoreState) {
                restoreState = true
            }
        }
        // Clearing the stack still has work to do when the route is on top.
        if (options.clearBackStack) {
            navigate(route, builder)
        } else if (!navigateUnlessShowing(route, builder)) {
            logger.d { "Dropped a repeat navigate to ${route.nameForLogs()}, already on top" }
        }
    }

    override fun goBack() {
        enqueueNavigation("go back") {
            popBackStack()
        }
    }

    override fun popBackTo(route: Route, inclusive: Boolean) {
        enqueueNavigation("popBackTo ${route.nameForLogs()}") {
            popBackStack(route, inclusive)
        }
    }

    /**
     * Releases the queue past the lifecycle gate. See [NavigationRecovery] for
     * why this exists and why nothing but the watchdog may call it.
     *
     * Dispatched to the main thread rather than run inline: the commands touch
     * `NavHostController`, and the watchdog fires from a pointer callback whose
     * thread is not guaranteed. `immediate` so an already-main call keeps the
     * ordering the queue promised.
     */
    override fun drainQueuedNavigation() {
        val controller = navController ?: run {
            logger.w { "Navigation recovery asked for, but no controller is attached" }
            return
        }
        appScope.launch(Dispatchers.Main.immediate) {
            val drained = navigationRequests.drainInto { command ->
                Catching { command(controller) }
                    .logOnFailure("Navigation failure while recovering the queue")
            }
            if (drained > 0) {
                logger.w { "Navigation queue was wedged; released $drained queued command(s)" }
            } else {
                logger.i { "Navigation recovery ran with an empty queue; the host was not the problem" }
            }
        }
    }

    /**
     * A link that will not open is expected, not a bug (no browser or store app on the device), so
     * it is a warning and never crashes a debug build. It names the scheme and the exception type
     * only. The link, and the platform's message, which quotes it, can be the eating disorder
     * helpline Support opens, and that someone tried to reach it is health data.
     */
    override fun openWebLink(url: String) {
        webLinkLauncher.open(url).onFailure { error ->
            logger.w { "Couldn't open a ${url.substringBefore(":", missingDelimiterValue = "schemeless")} link (${error::class.simpleName})" }
        }
    }

    override fun shareFile(name: String, mimeType: String, bytes: ByteArray) {
        // The name and type only: the bytes are an export of the user's diary.
        fileShareLauncher
            .share(name, mimeType, bytes)
            .logOnFailure("Failed to share file: $name ($mimeType)")
            .throwIfDebug()
    }

    @Composable
    fun Bind(navController: NavHostController) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(navController, lifecycleOwner) {
            setNavController(navController, lifecycleOwner.lifecycle)
            onDispose { clearNavController(navController) }
        }
    }

    private fun enqueueNavigation(
        description: String,
        route: Route? = null,
        block: NavHostController.() -> Unit,
    ) {
        logger.d { "Enqueuing navigation: $description" }
        navigationRequests.trySend {
            if (route != null && shouldBlockNavigation(route)) {
                logger.w { "Blocked navigation '$description' because a blocking error is active" }
                return@trySend
            }
            Catching { block() }
                .logOnFailure("Navigation failure: $description")
                .throwIfDebug()
        }
    }

    private fun NavHostController.shouldBlockNavigation(route: Route): Boolean {
        if (!isBlockingErrorActive()) return false
        if (route is NavigableWhileBlocked) return false
        return true
    }

    // TODO(review): nothing navigates to BlockingErrorRoute, so this gate never closes, while the
    // screens that are meant to block (SessionExpiredRoute, AccessDeniedRoute) do not close it:
    // a reminder tap still pushes the Logger over either. Covering them needs OnboardingRoute,
    // SessionExpired's own way out, let through (docs/review/findings/core-libraries.md).
    private fun NavHostController.isBlockingErrorActive(): Boolean {
        val destination = currentBackStackEntry?.destination ?: return false
        return destination.hasRoute<BlockingErrorRoute>()
    }

    private fun Route.nameForLogs(): String =
        this::class.simpleName ?: this::class.qualifiedName ?: toString()

    private suspend fun NavHostController.awaitGraphAttachment() {
        withContext(Dispatchers.Main.immediate) {
            currentBackStackEntryFlow.first()
            // TODO(review): an unexplained settle time from the initial commit. Nobody knows what
            // it waits for, so it stays until a launch-and-deep-link test can show it is unneeded.
            delay(100)
        }
    }
}
