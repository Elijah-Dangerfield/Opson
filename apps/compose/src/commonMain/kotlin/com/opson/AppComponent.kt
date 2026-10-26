package com.opson

import com.opson.libraries.core.AppState
import com.opson.libraries.core.AutoInit
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.navigation.DeepLinkBridge
import com.opson.libraries.navigation.impl.DelegatingRouter
import com.opson.libraries.telemetry.impl.JankMonitor
import com.opson.libraries.telemetry.impl.StartupReporter
import com.opson.libraries.opson.Telemetry
import com.opson.libraries.navigation.FeatureEntryPoint
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlin.time.Clock

@ContributesTo(AppScope::class)
@SingleIn(AppScope::class)
interface AppComponent {
    val featureEntryPoints: Set<FeatureEntryPoint>
    val appViewModel: AppViewModel  // Singleton, shared between MainActivity and App
    val delegatingRouter: DelegatingRouter
    val telemetry: Telemetry

    /**
     * Real-user frame timing, reported per screen. Android binds JankStats; iOS
     * binds a no-op (see [JankMonitor]).
     */
    val jankMonitor: JankMonitor

    /**
     * Cold-start duration, reported once per process when the first usable
     * frame is on screen. Android measures from OS process creation; iOS
     * reports nothing on purpose (see [ProcessStartTimeProvider]).
     */
    val startupReporter: StartupReporter
    val shakeHandler: ShakeHandler
    val deepLinkBridge: DeepLinkBridge

    /**
     * Production app-wide state (offline banner etc.). Backed by
     * AppStateImpl — platform connectivity combined with witnessed
     * request reachability.
     */
    val appState: AppState

    /** Auth surface for App.kt's deep-link OAuth completion + routing collectors. */
    val authRepository: AuthRepository

    /** The live numbers policy App.kt provides to every screen as `LocalVisibilityPolicy`. */
    val visibilityPolicySource: VisibilityPolicySource

    /** The user's palette, theme and text size, which App.kt applies to `AppTheme`. */
    val settingsRepository: SettingsRepository
    
    /**
     * Singletons that need to construct at app boot rather than lazily
     * on first injection. Anvil populates this set via the
     * `@ContributesBinding(... AutoInit::class, multibinding = true)`
     * annotation on each implementer — see [AutoInit] for the
     * contract and when to opt in.
     *
     * The set is resolved once on first composition in `App.kt` (and in
     * `Application.onCreate` on Android); the act of resolving forces
     * every contributor to construct, which runs each implementer's
     * `init {}` block. That's how
     * [com.opson.libraries.opson.impl.AppEventDispatcher]
     * registers its lifecycle listener at boot.
     */
    val autoInits: Set<AutoInit>

    @Provides
    fun provideClock(): Clock = Clock.System

}
