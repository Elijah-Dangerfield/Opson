@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson

import com.opson.features.upgrade.AppGuard
import com.opson.features.upgrade.ConnectionBanner
import com.opson.libraries.config.ConfigOverrideRepository
import com.opson.libraries.core.AutoInit
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.navigation.DeepLinkBridge
import com.opson.libraries.navigation.TabBar
import com.opson.libraries.navigation.impl.DelegatingRouter
import com.opson.libraries.telemetry.impl.JankMonitor
import com.opson.libraries.telemetry.impl.StartupReporter
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.Telemetry
import com.opson.libraries.prompts.PromptHost
import com.opson.libraries.ui.components.CoachMarks
import com.opson.libraries.reminders.ReminderActions
import com.opson.libraries.reminders.ReminderOpens
import com.opson.libraries.navigation.FeatureEntryPoint
import com.opson.libraries.widgets.WidgetOpens
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import com.opson.libraries.nutrition.VisibilityPolicySource
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock

@ContributesTo(AppScope::class)
@SingleIn(AppScope::class)
interface AppComponent {
    val featureEntryPoints: Set<FeatureEntryPoint>
    val appViewModel: AppViewModel  // Singleton, shared between MainActivity and App
    val delegatingRouter: DelegatingRouter

    /** Lets a tab screen put the bottom bar away while it needs the room (Today's select mode). */
    val tabBar: TabBar
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

    /** Auth surface for App.kt's deep-link OAuth completion + routing collectors. */
    val authRepository: AuthRepository

    /** The live numbers policy App.kt provides to every screen as `LocalVisibilityPolicy`. */
    val visibilityPolicySource: VisibilityPolicySource

    /** The user's theme and text size, which App.kt applies to `AppTheme`. */
    val settingsRepository: SettingsRepository

    /** Tapped reminders asking for the Logger, which App.kt routes. */
    val reminderOpens: ReminderOpens

    /** Whether this device finished onboarding, which App.kt reads before routing a reminder tap. */
    val appCache: AppCache

    /** What a notification's buttons and taps report: Android's activity and receivers reach it here. */
    val reminderActions: ReminderActions

    /** A home screen widget's +: the URL goes in here (App.kt on iOS, MainActivity on Android), the Logger comes out. */
    val widgetOpens: WidgetOpens

    /** Maintenance, the version floor and the update offer, drawn by App.kt over everything. */
    val appGuard: AppGuard

    /** The offline strip the guard layer draws above the app. */
    val connectionBanner: ConnectionBanner

    /** The guard's debug-only way out when a QA override is what is holding the app. */
    val configOverrideRepository: ConfigOverrideRepository

    /** Today's prompts (docs/practices/prompts.md), drawn by App.kt beside the nav host. */
    val promptHost: PromptHost

    /** First-run tips: which one a screen visit shows, provided by App.kt as `LocalCoachMarks`. */
    val coachMarks: CoachMarks

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

    /** For `App.kt` to reload the words when the locale changes. Swift never uses it. */
    @HiddenFromObjC
    val localizedCopy: LocalizedCopy

    @Provides
    fun provideClock(): Clock = Clock.System

}
