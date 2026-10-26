package com.opson

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.BuildInfo
import com.opson.libraries.core.Platform
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.navigation.AccessDeniedRoute
import com.opson.libraries.navigation.AnimationType
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.navigation.NavigationOptions
import com.opson.libraries.navigation.Route
import com.opson.libraries.navigation.SessionExpiredRoute
import com.opson.libraries.navigation.floatingwindow.FloatingWindowHost
import com.opson.libraries.navigation.floatingwindow.FloatingWindowNavigator
import com.opson.libraries.navigation.impl.DelegatingRouter
import com.opson.libraries.navigation.serializableType
import com.opson.libraries.navigation.toEnterTransition
import com.opson.libraries.navigation.toExitTransition
import com.opson.libraries.navigation.toRouteOrNull
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.nutrition.ui.LocalVisibilityPolicy
import com.opson.libraries.opson.Telemetry
import com.opson.libraries.telemetry.impl.JankMonitor
import com.opson.libraries.ui.components.Screen
import com.opson.libraries.ui.components.SnackbarDuration
import com.opson.libraries.ui.components.dialog.DialogHost
import com.opson.libraries.ui.components.dialog.LocalDialogHostState
import com.opson.libraries.ui.components.dialog.rememberDialogHostState
import com.opson.libraries.ui.debug.RecompositionCounter
import com.opson.libraries.ui.snackbar.PresenterSnackbarHost
import com.opson.libraries.ui.snackbar.showDebugSnackBar
import com.opson.libraries.ui.system.LocalAppState
import com.opson.libraries.ui.system.LocalBuildInfo
import com.opson.libraries.ui.system.LocalClock
import com.opson.system.AppTheme
import kotlin.reflect.typeOf
import kotlin.time.Duration.Companion.seconds

@Composable
fun App(appComponent: AppComponent) {
    val appViewModel = appComponent.appViewModel
    val floatingWindowNavigator = remember { FloatingWindowNavigator() }
    val navController = rememberNavController(floatingWindowNavigator)
    val appRecomposeLogger = remember { KLog.withTag("AppRecompose") }
    val router = remember { appComponent.delegatingRouter }
    val dialogHostState = rememberDialogHostState()

    val shakeHandler = remember { appComponent.shakeHandler }
    val deepLinkBridge = remember { appComponent.deepLinkBridge }

    // Boot-warm every @AutoInit singleton. Resolving the Set forces each
    // contributor to construct, running their `init {}` blocks —
    // AppEventDispatcher attaches its lifecycle observer, connectivity
    // watchers arm, etc. Wrapped in `remember` so this resolves exactly once
    // per composition lifetime, even though App() recomposes. See [AutoInit]
    // for the contract. (Android also resolves this in Application.onCreate,
    // before the first Activity; resolving twice is a no-op.)
    remember { appComponent.autoInits }

    // Lifecycle-scoped, not composition-scoped. A DisposableEffect here only
    // tears down when the composition goes away, which backgrounding does not
    // do — so the accelerometer kept sampling a phone in a pocket, and
    // DelegatingRouter holds navigations under repeatOnLifecycle(STARTED).
    // A jostle queued a navigate that was released on resume, which is how a
    // shake dialog appeared by itself seconds after the user came back.
    LifecycleStartEffect(shakeHandler) {
        shakeHandler.start()
        onStopOrDispose {
            shakeHandler.stop()
        }
    }

    val authRepository = remember { appComponent.authRepository }

    LaunchedEffect(navController, deepLinkBridge) {
        deepLinkBridge.urls.collect { url ->
            // The browser OAuth return trip (`…://login-callback#...`) carries
            // a Supabase session in its fragment, not a navigable destination —
            // hand it to supabase-kt to import instead of the nav graph. Every
            // other link routes as before. The repo emits the new AuthState on
            // success, which the app's auth collectors react to.
            if (authRepository.isOAuthRedirect(url)) {
                Catching { authRepository.completeOAuthRedirect(url) }
                    .logOnFailure { "Failed to complete OAuth redirect" }
            } else {
                Catching {
                    val request = NavDeepLinkRequest.Builder.fromUri(NavUri(url)).build()
                    navController.handleDeepLink(request)
                }.logOnFailure { "Failed to handle deep link: $url" }
            }
        }
    }

    RecompositionCounter(
        tag = "App",
        logEvery = 1,
        rapidRecompositionThreshold = 6,
        rapidRecompositionWindow = 60.seconds,
        onRecompose = { count ->
            val message = if (count == 1L) {
                "App recomposed (this should be rare)"
            } else {
                "App recomposed $count times"
            }
            appRecomposeLogger.w { message }
        },
        onRapidRecomposition = { info ->
            appRecomposeLogger.e {
                "Rapid recompositions: ${info.countInWindow} in ${info.windowMillis}ms (total=${info.totalCount})"
            }
            showDebugSnackBar(
                title = "Performance hiccup",
                message = "App recomposed ${info.countInWindow}× in ${info.windowMillis}ms.",
                duration = SnackbarDuration.Long,
                withDismissAction = true,
            )
        }
    )

    val appState = remember { appComponent.appState }

    CompositionLocalProvider(
        LocalAppState provides appState,
        LocalClock provides appComponent.provideClock(),
        LocalBuildInfo provides BuildInfo,
        LocalDialogHostState provides dialogHostState
    ) {
        UserAppTheme(appComponent.settingsRepository) {
            ProvideVisibilityPolicy(appComponent.visibilityPolicySource) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Detection only, at the root so it sees every press. See
                        // NavigationQueueWatchdog for why a touch is the one signal
                        // that a host below STARTED is lying about its visibility.
                        .navigationQueueWatchdog(onWedgeDetected = router::drainQueuedNavigation)
                ) {
                    // Stage 1: null until the async AppData read resolves — the
                    // platform splash (keyed on appViewModel.isReady) covers the
                    // gap. Stage 2: the Compose boot gate holds a loading screen
                    // until the remaining boot work (config + profile resolve)
                    // lands, so the first real frame renders authoritative data.
                    val bootComplete by appViewModel.isBootComplete.collectAsState()
                    val startDestination by appViewModel.startDestination.collectAsState()
                    val route = startDestination
                    if (bootComplete && route != null) {
                        AppNavigation(
                            navController = navController,
                            floatingWindowNavigator = floatingWindowNavigator,
                            featureEntryPoints = appComponent.featureEntryPoints,
                            startDestination = route,
                            router = router,
                            telemetry = appComponent.telemetry,
                            jankMonitor = appComponent.jankMonitor,
                        )
                    } else {
                        BootLoadingScreen()
                    }

                    SplashGate()

                    // The auth server rejected our session mid-run: push a blocking
                    // SessionExpired screen (kept on top, stack intact) that owns
                    // "sign in again" (claimed) vs "start fresh" (guest). An ambient
                    // network event can't present this from a feature screen, so it
                    // routes here. The screen picks its copy off wasAnonymous.
                    LaunchedEffect(Unit) {
                        appViewModel.sessionExpired.collect { event ->
                            router.navigate(
                                SessionExpiredRoute(wasAnonymous = event.wasAnonymous),
                                NavigationOptions(launchSingleTop = true),
                            )
                        }
                    }

                    // Server returned the locked `403` access-denied envelope: push
                    // the blocking AccessDenied screen. Same launchSingleTop pattern
                    // as SessionExpired — a burst of denied calls collapses to one
                    // screen on top. The screen keys title/body off `reason` and
                    // surfaces the optional lift date + appeal link.
                    LaunchedEffect(Unit) {
                        appViewModel.accessDenied.collect { denial ->
                            router.navigate(
                                AccessDeniedRoute(
                                    reason = denial.reason,
                                    until = denial.until,
                                    appealUrl = denial.appealUrl,
                                ),
                                NavigationOptions(launchSingleTop = true),
                            )
                        }
                    }

                    DialogHost(
                        modifier = Modifier.matchParentSize(),
                        hostState = dialogHostState
                    )
                }
            }
        }
    }
}

/**
 * Provides the live numbers policy to everything below. Its own composable so a policy change
 * recomposes from here down rather than recomposing [App] (and the boot gate with it).
 *
 * Provided once, at the root, so no screen can render outside it. A screen that somehow did would
 * get `LocalVisibilityPolicy`'s default, which hides every number.
 */
@Composable
private fun ProvideVisibilityPolicy(
    source: VisibilityPolicySource,
    content: @Composable () -> Unit,
) {
    val policy by source.policy.collectAsState()
    CompositionLocalProvider(LocalVisibilityPolicy provides policy, content = content)
}

/**
 * The simple class name of a destination's route, for telemetry tagging.
 *
 * Type-safe nav stores the route as its serializer name — the fully-qualified
 * class name followed by argument placeholders, e.g.
 * `com.opson.features.logger.LoggerRoute?slot={slot}`. We strip the args and
 * the package to get just `LoggerRoute`, keeping the tag low-cardinality and
 * readable. Returns null for unnamed/graph destinations.
 */
private fun NavDestination.routeClassNameOrNull(): String? =
    route
        ?.substringBefore('/')
        ?.substringBefore('?')
        ?.substringAfterLast('.')
        ?.takeIf { it.isNotBlank() }

@Composable
private fun AppNavigation(
    navController: NavHostController,
    floatingWindowNavigator: FloatingWindowNavigator,
    featureEntryPoints: Set<FeatureEntryPoint>,
    startDestination: Route,
    router: DelegatingRouter,
    telemetry: Telemetry,
    jankMonitor: JankMonitor,
) {
    // Tag every crash/error with the route the user is currently on. Sheets
    // and dialogs are real destinations on this same back stack (see
    // `bottomSheet`/`dialog` nav builders), so an open sheet wins over the
    // screen beneath it — exactly the granularity we want for triage. Pushed
    // from a LaunchedEffect keyed on the name so we only touch the Sentry
    // scope when the route actually changes, not on every recomposition.
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRouteName = currentBackStackEntry?.destination?.routeClassNameOrNull()
    LaunchedEffect(currentRouteName) {
        currentRouteName?.let {
            telemetry.setCurrentRoute(it)
            // Same hook, so jank attribution and crash attribution always name
            // the same screen. This closes out the previous screen's frame tally
            // and emits it as one app.jank event — never one per frame.
            jankMonitor.onRouteChanged(it)
        }
    }

    // Remember the graph-builder lambda so recompositions of AppNavigation
    // hand NavHost the SAME builder instance. A fresh lambda each pass makes
    // NavHost treat the graph as changed and re-push the start destination
    // onto the back stack.
    val graph: NavGraphBuilder.() -> Unit = remember(featureEntryPoints, router) {
        {
            featureEntryPoints.forEach { entryPoint ->
                with(entryPoint) {
                    buildNavGraph(router)
                }
            }
        }
    }

    Screen(
        containerColor = AppTheme.colors.bg.color,
        // Each screen owns its own insets. The only padding the host adds is the bottom bar's,
        // and it marks that consumed so a tab screen does not pad for the navigation bar twice.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            ShellBottomBar(navController = navController, router = router)
        },
        snackbarHost = {
            PresenterSnackbarHost()
        },
        content = { shellPadding ->
            NavHost(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(shellPadding)
                    .consumeWindowInsets(shellPadding),
                navController = navController,
                startDestination = startDestination,
                //To make this more readable consider Screens A and B
                enterTransition = {
                    // A -> B
                    // How should we animate the B screen?
                    // Enter animation should match B's Enter
                    val targetRoute = targetState.toRouteOrNull<Route>()
                    val (animationType, reason) = when {
                        isSwitchingTabs() -> AnimationType.None to "Switching tabs"
                        targetRoute != null -> targetRoute.enter to "Using target route enter animation"
                        else -> AnimationType.None to "Target destination is not a Route; default to none"
                    }

                    animationType.toEnterTransition()
                },
                popEnterTransition = {
                    // Popping from B back to A
                    // How should we animate the A screen?
                    // Enter animation should match initials pop EXIT transition
                    // AKA if B slides out, A should slide IN
                    val initialRoute = initialState.toRouteOrNull<Route>()
                    val targetRoute = targetState.toRouteOrNull<Route>()
                    val (animationType, reason) = when {
                        isSwitchingTabs() -> AnimationType.None to "Switching tabs"
                        initialRoute != null -> initialRoute.popExit.opposite() to "Mirroring initial popExit animation"
                        targetRoute != null -> targetRoute.enter to "Fallback to target route enter animation"
                        else -> AnimationType.None to "No route metadata; default to none"
                    }

                    animationType.toEnterTransition()
                },
                exitTransition = {
                    // A -> B
                    // Initial: A | Target B
                    // How should we animate the A screen
                    // Exit animation should match A's Exit
                    val initialRoute = initialState.toRouteOrNull<Route>()
                    val (animationType, reason) = when {
                        isSwitchingTabs() -> AnimationType.None to "Switching tabs"
                        initialRoute != null -> initialRoute.exit to "Using initial route exit animation"
                        else -> AnimationType.None to "Initial destination is not a Route; default to none"
                    }

                    animationType.toExitTransition()
                },
                popExitTransition = {
                    // Popping from B back to A
                    // Initial: B | Target A
                    // How should we animate the B screen
                    // Exit animation should match B's pope Exit
                    val initialRoute = initialState.toRouteOrNull<Route>()

                    val (animationType, reason) = when {
                        isSwitchingTabs() -> AnimationType.None to "Switching tabs"
                        initialRoute != null -> initialRoute.popExit to "Using initial route popExit animation"
                        else -> AnimationType.None to "Initial destination is not a Route; default to none"
                    }

                    animationType.toExitTransition()
                },
                typeMap = mapOf(
                    typeOf<AnimationType>() to serializableType<AnimationType>()
                ),
                builder = graph
            )

            FloatingWindowHost(floatingWindowNavigator)

            router.Bind(navController)
        },
    )
}

/**
 * Isolates the splash-overlay's `hasShownSplash` state read into its own composable
 * so that flipping it cannot recompose `App` and, in particular, cannot cause
 * `AppNavigation` / `NavHost` to rebuild its graph — which was previously pushing
 * a second copy of the start destination onto the back stack.
 *
 * Only renders on iOS. Android uses the native splash API instead.
 */
@Composable
private fun SplashGate() {
    if (BuildInfo.platform != Platform.iOS) return
    var hasShownSplash by rememberSaveable { mutableStateOf(false) }
    if (!hasShownSplash) {
        SplashOverlay(onComplete = { hasShownSplash = true })
    }
}
