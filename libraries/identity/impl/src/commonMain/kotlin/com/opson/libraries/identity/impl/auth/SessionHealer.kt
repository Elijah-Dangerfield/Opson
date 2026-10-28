package com.opson.libraries.identity.impl.auth

import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.AppEventListener
import com.opson.libraries.core.AppState
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.identity.profile.ProfileRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The *decider* for identity self-heal. Listens for the data-freshness triggers
 * — cold boot, warm boot, foreground (non-cold-boot), and connectivity-regained
 * — and, on each, asks "is this device stranded?": onboarded + online + genuinely
 * session-less (not a deliberate sign-out, not a server-killed session).
 *
 * First it tries [AuthRepository.retry], which revives a dormant session that
 * only needed a refresh. If that fails, the device is stranded, and since
 * accounts are required and nothing mints guests any more, the only way back is
 * signing in: it declares the session unrecoverable
 * ([AuthRepository.markSessionUnrecoverable]), which routes the person to the
 * session-expired screen and from there to sign-in. A cached server-backed
 * profile (AUTH-19: storage lost the token across an upgrade) takes the same
 * route, carrying whether that account was a guest.
 *
 * Each trigger emits one structured decision line (see [HealAction]).
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AppEventListener::class, multibinding = true)
@Inject
class SessionHealer(
    // Lazy providers break the DI construction cycle: this is an AppEventListener
    // (in the dispatcher's set), and AuthRepository → AppEventBus → the dispatcher
    // → this set. ProfileRepository reaches AuthRepository transitively, so it's
    // deferred too. Mirrors InAppMessageManagerImpl.
    authRepositoryProvider: () -> AuthRepository,
    profileRepositoryProvider: () -> ProfileRepository,
    private val expiredAccounts: ExpiredAccountStore,
    private val appCache: AppCache,
    private val appState: AppState,
    private val appScope: AppCoroutineScope,
) : AppEventListener {

    private val logger = KLog.withTag("SessionHealer")
    private val inFlight = Mutex()
    private val authRepository: AuthRepository by lazy { authRepositoryProvider() }
    private val profileRepository: ProfileRepository by lazy { profileRepositoryProvider() }

    override fun onColdBoot(event: AppEvent.ColdBoot) = heal("coldBoot")
    override fun onWarmBoot(event: AppEvent.WarmBoot) = heal("warmBoot")

    override fun onForeground(event: AppEvent.OnForeground) {
        // Cold-boot foreground is already covered by onColdBoot; skip the
        // duplicate so we don't run the decision twice on launch.
        if (event.isColdBoot) return
        heal("foreground")
    }

    override fun onConnectivityRegained(event: AppEvent.ConnectivityRegained) = heal("connectivityRegained")

    /**
     * Single-flight: overlapping triggers skip rather than queue — every source
     * re-fires on a later edge anyway. This guards the decision run (duplicate
     * [AuthRepository.retry] hits).
     */
    fun heal(source: String) {
        if (!inFlight.tryLock()) {
            log(source, HealAction.SKIP_ALREADY_RUNNING)
            return
        }
        appScope.launch {
            try {
                Catching { healIfStranded(source) }
                    .logOnFailure { "Identity self-heal failed (source=$source)" }
            } finally {
                inFlight.unlock()
            }
        }
    }

    private suspend fun healIfStranded(source: String) {
        val current = authRepository.current()
        // Already have a session — nothing to do.
        if (current is AuthState.Authenticated) {
            log(source, HealAction.SKIP_HAS_SESSION)
            return
        }
        // Checked before retry(): reviving a session the person just logged out
        // of would sign them back in. Onboarding's sign-in step is next.
        if ((current as AuthState.Unauthenticated).reason == AuthState.Unauthenticated.Reason.SignedOut) {
            log(source, HealAction.SKIP_SIGNED_OUT)
            return
        }

        // Cheapest recovery: re-resolve a dormant/refreshable session. Recovers a
        // claimed user whose token just needed a nudge — without minting anything.
        val afterRetry = authRepository.retry()
        if (afterRetry is AuthState.Authenticated) {
            log(source, HealAction.RETRY_RECOVERED)
            return
        }
        val unauth = afterRetry as AuthState.Unauthenticated

        when {
            unauth.cause is SessionUnconfirmed -> {
                // An account is stored on the device and only its refresh is
                // failing; supabase-kt keeps retrying. Declaring it
                // unrecoverable would sign out someone who is merely offline.
                log(source, HealAction.SKIP_SESSION_UNCONFIRMED)
            }
            unauth.reason == AuthState.Unauthenticated.Reason.SessionExpired -> {
                // Already declared dead (server rejection or client
                // unrecoverable); the app is routing to sign-in.
                log(source, HealAction.SKIP_SESSION_EXPIRED)
            }
            // A sign-out that landed while retry() waited on the lock.
            unauth.reason == AuthState.Unauthenticated.Reason.SignedOut -> log(source, HealAction.SKIP_SIGNED_OUT)
            else -> {
                val stranded = cachedServerProfile()
                val held = expiredAccount()
                when {
                    // A server account demonstrably exists on this device but
                    // its session is gone and retry couldn't revive it (AUTH-19).
                    stranded != null -> stopForRecovery(source, wasAnonymous = stranded.isAnonymous, id = stranded.id)
                    // A relaunch after a session expiry: the account's data is
                    // still here, waiting for it to sign back in.
                    held != null -> stopForRecovery(source, wasAnonymous = held.wasAnonymous, id = held.userId)
                    !hasUserOnboarded() -> {
                        // Pre-onboarding: onboarding's sign-in step is where a
                        // session comes from.
                        log(source, HealAction.SKIP_NOT_ONBOARDED)
                    }
                    appState.isOffline.value -> {
                        // Offline looks the same as no session; the
                        // connectivity-regained edge decides again.
                        log(source, HealAction.SKIP_OFFLINE)
                    }
                    // Onboarded with no account at all: a guest from an older
                    // build whose creation never landed. Signing in is the way in.
                    else -> stopForRecovery(source, wasAnonymous = false, id = null)
                }
            }
        }
    }

    /**
     * The cached server-backed profile, if one survives locally while we're
     * session-less — proof this device had a real account whose session storage
     * failed us. A fresh install has no cache at all.
     */
    private suspend fun cachedServerProfile(): Profile.Authenticated? =
        Catching { profileRepository.current() }
            .logOnFailure { "Reading profile for heal decision failed; assuming none cached" }
            .getOrNull() as? Profile.Authenticated

    private suspend fun expiredAccount(): ExpiredAccount? =
        Catching { expiredAccounts.read() }
            .logOnFailure { "Reading the expired-session account for heal failed; assuming none" }
            .getOrNull()

    private suspend fun stopForRecovery(source: String, wasAnonymous: Boolean, id: String?) {
        log(source, HealAction.STOP_FOR_RECOVERY)
        logger.w {
            "Session unrecoverable: ${id?.let { "account $it" } ?: "no account on this device"} " +
                "(isAnonymous=$wasAnonymous) has no session and retry could not revive it; routing to sign-in"
        }
        Catching { authRepository.markSessionUnrecoverable(wasAnonymous = wasAnonymous) }
            .logOnFailure { "Marking session unrecoverable failed; will re-decide on the next trigger" }
    }

    private suspend fun hasUserOnboarded(): Boolean =
        Catching { appCache.get().hasUserOnboarded }
            .logOnFailure { "Reading hasUserOnboarded for heal failed; assuming not onboarded" }
            .getOrNull() ?: false

    private fun log(source: String, action: HealAction) {
        logger.i { "heal source=$source action=$action" }
    }

    /** The decision the healer reached this trigger — one line per trigger. */
    private enum class HealAction {
        SKIP_ALREADY_RUNNING,
        SKIP_HAS_SESSION,
        SKIP_SESSION_EXPIRED,
        SKIP_SESSION_UNCONFIRMED,
        SKIP_SIGNED_OUT,
        SKIP_NOT_ONBOARDED,
        SKIP_OFFLINE,
        RETRY_RECOVERED,
        STOP_FOR_RECOVERY,
    }
}
