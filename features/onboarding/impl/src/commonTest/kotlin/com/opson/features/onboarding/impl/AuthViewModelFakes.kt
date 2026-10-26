@file:OptIn(ExperimentalTime::class)

package com.opson.features.onboarding.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.identity.auth.AccountCreationState
import com.opson.libraries.identity.auth.AppleSignInCoordinator
import com.opson.libraries.identity.auth.AppleSignInCredential
import com.opson.libraries.identity.auth.AuthOutcome
import com.opson.libraries.identity.auth.AuthOutcomeClassifier
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.DeleteAccountOutcome
import com.opson.libraries.identity.auth.GuestAccountCreator
import com.opson.libraries.identity.auth.LinkEmailIdentityOutcome
import com.opson.libraries.identity.auth.LinkIdentityOutcome
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.identity.auth.PendingIdentity
import com.opson.libraries.identity.auth.RefreshOutcome
import com.opson.libraries.identity.auth.ResendOutcome
import com.opson.libraries.identity.auth.SendResetOutcome
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.identity.auth.SignUpOutcome
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.identity.profile.UpdateProfileOutcome
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Pluggable in-memory [AuthRepository] for unit-testing the onboarding
 * ViewModels. Construct with the outcomes the test under test cares about
 * (per-method default = the "Unknown" failure case, so any code path you
 * forget to stub fails loudly instead of silently passing).
 *
 * The methods that the auth ViewModels never call are `error()`-stubbed
 * — if a future refactor reaches for them the test fails with a clear
 * message rather than a NPE.
 */
internal class FakeAuthRepository(
    val signInOutcome: SignInOutcome = SignInOutcome.Unknown(RuntimeException("not stubbed")),
    val signUpOutcome: SignUpOutcome = SignUpOutcome.Unknown(RuntimeException("not stubbed")),
    val refreshOutcome: RefreshOutcome = RefreshOutcome.Unknown(RuntimeException("not stubbed")),
    val resendOutcome: ResendOutcome = ResendOutcome.Unknown(RuntimeException("not stubbed")),
    val sendResetOutcome: SendResetOutcome = SendResetOutcome.Unknown(RuntimeException("not stubbed")),
    val oauthSignInOutcome: SignInOutcome = SignInOutcome.Unknown(RuntimeException("not stubbed")),
    val linkEmailOutcome: LinkEmailIdentityOutcome = LinkEmailIdentityOutcome.Unknown(RuntimeException("not stubbed")),
    val linkAppleOutcome: LinkIdentityOutcome = LinkIdentityOutcome.Unknown(RuntimeException("not stubbed")),
    val appleSignInOutcome: SignInOutcome = SignInOutcome.Unknown(RuntimeException("not stubbed")),
    initialAuthState: AuthState = AuthState.Unauthenticated(),
) : AuthRepository {
    var signInCalls: Int = 0
        private set
    var lastSignInArgs: Pair<String, String>? = null
        private set
    var signUpCalls: Int = 0
        private set
    var lastSignUpArgs: Pair<String, String>? = null
        private set
    var refreshCalls: Int = 0
        private set
    var resendCalls: Int = 0
        private set
    var lastResendEmail: String? = null
        private set
    var sendResetCalls: Int = 0
        private set
    var lastSendResetEmail: String? = null
        private set
    var oauthSignInCalls: Int = 0
        private set
    var lastOAuthProvider: OAuthProvider? = null
        private set
    var linkEmailCalls: Int = 0
        private set
    var lastLinkEmailArgs: Pair<String, String>? = null
        private set
    var linkAppleCalls: Int = 0
        private set
    var appleSignInCalls: Int = 0
        private set

    private val state = MutableStateFlow(initialAuthState)

    override suspend fun current(): AuthState = state.value
    override fun observe(): Flow<AuthState> = state
    override suspend fun retry(): AuthState = state.value

    override suspend fun signInWithEmail(email: String, password: String): SignInOutcome {
        signInCalls += 1
        lastSignInArgs = email to password
        return signInOutcome
    }

    override suspend fun signUpWithEmail(email: String, password: String): SignUpOutcome {
        signUpCalls += 1
        lastSignUpArgs = email to password
        return signUpOutcome
    }

    override suspend fun refreshSession(): RefreshOutcome {
        refreshCalls += 1
        return refreshOutcome
    }

    override suspend fun resendVerificationEmail(email: String): ResendOutcome {
        resendCalls += 1
        lastResendEmail = email
        return resendOutcome
    }

    override suspend fun sendPasswordResetEmail(email: String): SendResetOutcome {
        sendResetCalls += 1
        lastSendResetEmail = email
        return sendResetOutcome
    }

    override suspend fun signOut() { /* not used here */ }

    override suspend fun deleteAccount(): DeleteAccountOutcome =
        error("deleteAccount not used by the auth ViewModels")

    override suspend fun linkOAuthIdentity(provider: OAuthProvider): LinkIdentityOutcome =
        error("linkOAuthIdentity not used by the auth ViewModels")

    override suspend fun linkEmailIdentity(email: String, password: String): LinkEmailIdentityOutcome {
        linkEmailCalls += 1
        lastLinkEmailArgs = email to password
        return linkEmailOutcome
    }

    override suspend fun signInWithOAuth(provider: OAuthProvider): SignInOutcome {
        oauthSignInCalls += 1
        lastOAuthProvider = provider
        return oauthSignInOutcome
    }

    override suspend fun linkAppleIdentity(credential: AppleSignInCredential): LinkIdentityOutcome {
        linkAppleCalls += 1
        return linkAppleOutcome
    }

    override suspend fun signInWithApple(credential: AppleSignInCredential): SignInOutcome {
        appleSignInCalls += 1
        return appleSignInOutcome
    }
}

/**
 * Coordinator that immediately reports [credential] — `null` models the user
 * dismissing the native sheet; [errorMessage] models a hard native failure.
 */
internal class FakeAppleSignInCoordinator(
    private val credential: AppleSignInCredential? = null,
    private val errorMessage: String? = null,
) : AppleSignInCoordinator {
    override fun requestCredential(
        onComplete: (credential: AppleSignInCredential?, errorMessage: String?) -> Unit,
    ) = onComplete(credential, errorMessage)
}

internal val sampleAppleCredential = AppleSignInCredential(
    identityToken = "token",
    nonce = "nonce",
    authorizationCode = null,
)

/**
 * In-memory [AppCache] that records writes — used to assert the
 * `hasUserOnboarded = true` side effect on successful sign-in / link.
 */
internal class FakeAppCache(
    initial: AppData = AppData(),
    private val onSet: (AppData) -> Unit = {},
) : AppCache {
    private val state = MutableStateFlow(initial)
    override val updates: Flow<AppData> = state
    override suspend fun get(): AppData = state.value
    override suspend fun set(value: AppData) {
        state.value = value
        onSet(value)
    }
    override suspend fun clear() { state.value = AppData() }
}

internal class FakeGuestAccountCreator(
    private val failCreation: Boolean = false,
    private val failureCause: Throwable? = null,
    // Models a wedged / offline creation that never reaches a terminal state —
    // start() parks in InProgress and awaitTerminal() never returns. The finish
    // path must time out and proceed Home anyway rather than trap the user.
    private val hangInProgress: Boolean = false,
    private val onStart: () -> Unit = {},
) : GuestAccountCreator {
    private val _state = MutableStateFlow<AccountCreationState>(AccountCreationState.Idle)
    override val state: StateFlow<AccountCreationState> = _state

    var startCalls: Int = 0
        private set
    var lastIdentity: PendingIdentity? = null
        private set

    override fun start(identity: PendingIdentity) {
        startCalls++
        lastIdentity = identity
        onStart()
        _state.value = when {
            hangInProgress -> AccountCreationState.InProgress
            failCreation -> AccountCreationState.Failed(identity, failureCause)
            else -> AccountCreationState.Succeeded
        }
    }

    override suspend fun awaitTerminal(): AccountCreationState =
        state.first { it is AccountCreationState.Succeeded || it is AccountCreationState.Failed }

    override fun retry() {
        lastIdentity?.let { start(it) }
    }

    override suspend fun ensureSession(fallbackIdentity: PendingIdentity): AccountCreationState {
        start(fallbackIdentity)
        return _state.value
    }
}

/**
 * Mirrors the production classifier: reads the brand-new-account signal off
 * the [FakeProfileRepository] so tests keep driving it via `profile.isNewAccount`.
 */
internal class FakeAuthOutcomeClassifier(
    private val profile: FakeProfileRepository,
) : AuthOutcomeClassifier {
    override suspend fun classify(wasLink: Boolean): AuthOutcome = when {
        wasLink -> AuthOutcome.Linked
        profile.isNewAccount -> AuthOutcome.SignedUp
        else -> AuthOutcome.SignedIn
    }
}

internal class FakeProfileRepository(
    initial: Profile = Profile.Fallback(id = "anon"),
    private val updateOutcome: UpdateProfileOutcome = UpdateProfileOutcome.Success(
        profile = Profile.Authenticated(
            id = "11111111-1111-1111-1111-111111111111",
            displayName = "Default",
            email = null,
            isAnonymous = true,
            createdAt = Instant.fromEpochSeconds(0),
        ),
    ),
) : ProfileRepository {
    private val flow = MutableStateFlow(initial)
    var lastUpdatedDisplayName: String? = null
        private set

    /** Drives [resolveIsNewAccount] — the authoritative brand-new-account signal
     *  the VM uses to classify SIGN-UP vs SIGN-IN (server `/v1/me` isNewAccount). */
    var isNewAccount: Boolean = false

    suspend fun emit(next: Profile) { flow.emit(next) }

    override suspend fun current(): Profile = flow.value
    override fun observe(): Flow<Profile> = flow
    override suspend fun resolveIsNewAccount(): Boolean = isNewAccount

    override suspend fun update(displayName: String?): UpdateProfileOutcome {
        lastUpdatedDisplayName = displayName
        return updateOutcome
    }
}

/** Convenience for tests that need an Authenticated state. */
internal val sampleAuthenticated = AuthState.Authenticated(
    userId = "11111111-1111-1111-1111-111111111111",
    isAnonymous = false,
    email = null,
)

internal val sampleAnonymousGuest = sampleAuthenticated.copy(userId = "anon-1", isAnonymous = true)

/**
 * In-memory [SettingsRepository] that records each write's stamp flag, so a
 * test can assert onboarding saved its preset without stamping `changedAt`.
 */
internal class FakeSettingsRepository(
    initial: Settings = Settings(),
    private val onUpdate: (Settings) -> Unit = {},
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = state
    override val isKnown: StateFlow<Boolean> = MutableStateFlow(true)

    /** `stampNumbersChange` of every update, in order. */
    val stampFlags = mutableListOf<Boolean>()

    override suspend fun update(stampNumbersChange: Boolean, transform: (Settings) -> Settings): Settings {
        stampFlags += stampNumbersChange
        state.value = transform(state.value)
        onUpdate(state.value)
        return state.value
    }
}
