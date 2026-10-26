package com.opson.libraries.navigation.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.ui.components.dialog.bottomsheet.BottomSheetState
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.shouldNotBeCaught
import com.opson.libraries.core.throwIfDebug
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.observeWithLifecycle
import com.opson.libraries.navigation.BlockingErrorRoute
import com.opson.libraries.navigation.NavigationOptions
import com.opson.libraries.navigation.NavigationTracker
import com.opson.libraries.navigation.Route
import com.opson.libraries.navigation.NavigationRecovery
import com.opson.libraries.navigation.Router
import com.opson.libraries.navigation.RouterBatch
import com.opson.libraries.navigation.TabRoute
import com.opson.libraries.navigation.enterTab
import com.opson.libraries.navigation.switchTab
import com.opson.libraries.navigation.WebLinkLauncher
import com.opson.libraries.navigation.FileShareLauncher
import com.opson.libraries.navigation.NavigableWhileBlocked
import com.opson.libraries.navigation.TrackableRoute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
    private val navigationTracker: NavigationTracker,
) : Router, NavigationRecovery {

    private val logger = KLog.withTag("DelegatingRouter")
    private val navigationRequests = Channel<NavHostController.() -> Unit>(Channel.UNLIMITED)

    private var navController: NavHostController? = null
    private var processingJob: Job? = null

    private var viewScope: CompletableDeferred<CoroutineScope> = CompletableDeferred()

    fun clearNavController(controller: NavHostController? = null) {
        if (navController === controller || controller == null) {
            logger.d { "Clearing nav controller" }
            processingJob?.cancel()
            processingJob = null
            navController = null
            viewScope = CompletableDeferred()
        }
    }

    fun setNavController(
        controller: NavHostController,
        lifecycle: Lifecycle,
        scope: CoroutineScope = lifecycle.coroutineScope,
    ) {
        logger.d { "Setting nav controller" }
        viewScope.complete(scope)
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
        // Track the navigation if the route is trackable
        if (route is TrackableRoute) {
            appScope.launch { navigationTracker.onNavigate(route) }
        }
        
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
                    if (route is TrackableRoute) {
                        appScope.launch { navigationTracker.onNavigate(route) }
                    }
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
        navigate(route) {
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

    override fun openWebLink(url: String) {
        webLinkLauncher
            .open(url)
            .logOnFailure("Failed to open web link: $url")
            .throwIfDebug()
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
        logger.i { "Binding nav controller" }
        val lifecycleOwner = LocalLifecycleOwner.current
        val coroutineScope = rememberCoroutineScope()
        val controllerKey = remember(navController) { navController }

        DisposableEffect(controllerKey, lifecycleOwner) {
            setNavController(controllerKey, lifecycleOwner.lifecycle, coroutineScope)
            onDispose { clearNavController(controllerKey) }
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
        KLog.i { "Blocking Navigation for route $route" }
        return true
    }

    private fun NavHostController.isBlockingErrorActive(): Boolean {
        val destination = currentBackStackEntry?.destination ?: return false
        return destination.hasRoute<BlockingErrorRoute>()
    }

    private fun Route.nameForLogs(): String =
        this::class.simpleName ?: this::class.qualifiedName ?: toString()

    private suspend fun NavHostController.awaitGraphAttachment() {
        withContext(Dispatchers.Main.immediate) {
            currentBackStackEntryFlow.first()
            delay(100)
        }
    }
}
