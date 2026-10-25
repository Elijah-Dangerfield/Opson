package com.opson.features.onboarding.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.identity.auth.AuthState
import com.opson.libraries.identity.auth.LinkIdentityOutcome
import com.opson.libraries.identity.auth.OAuthProvider
import com.opson.libraries.identity.auth.PendingIdentity
import com.opson.libraries.identity.auth.SignInOutcome
import com.opson.libraries.identity.profile.Profile
import com.opson.libraries.identity.profile.UpdateProfileOutcome
import com.opson.libraries.opson.AppData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/**
 * Covers the **deferred-creation** onboarding flow:
 *
 *  - Returning user (hasUserOnboarded) bounces straight to Home.
 *  - ContinueAsGuest just enters PickIdentity — no auth, no account yet.
 *  - ContinueFromPickIdentity kicks off guest-account creation (the
 *    app-scoped [com.opson.libraries.identity.auth.GuestAccountCreator]),
 *    joins on the result, marks onboarded, and navigates Home. Success or
 *    failure both complete the flow (failure flags the degraded state).
 *    When an account already exists (OAuth), it patches the profile instead.
 *  - Back is blocked once creation has started or an identity is claimed.
 *  - SignInWithOAuth: returning account → onboarded + Home; brand-new account
 *    → run through PickIdentity; cancel/failure handled.
 *  - SignInWithApple: anonymous guest links the identity (keeps progress);
 *    identity-on-another-account signs in and skips onboarding; a dismissed
 *    sheet is a quiet no-op; a native failure surfaces OAuthFailed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest : CoroutineTest() {

    @Test
    fun init_alreadyOnboarded_immediatelyNavigatesHome() = runUnitTest {
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = true))
        val vm = newVm(cache = cache)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToHome, received.firstOrNull())
    }

    @Test
    fun init_authenticatedNotOnboarded_opensAtIdentity_claimed() = runUnitTest {
        // A just-confirmed email signup routed back from VerifyEmail: a real
        // (non-anonymous) account that hasn't finished onboarding opens at
        // PickIdentity with the back-to-Welcome control suppressed — not the
        // guest/OAuth landing it no longer needs.
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = false))
        val auth = FakeAuthRepository(initialAuthState = sampleAuthenticated)
        val vm = newVm(cache = cache, auth = auth)
        runCurrent()

        assertEquals(OnboardingStep.PickIdentity, vm.state.step)
        assertTrue(vm.state.identityClaimed)
    }

    @Test
    fun init_returningAnonymousGuest_skipsOnboarding_navigatesHome() = runUnitTest {
        // An install with a pre-existing anonymous guest session that reaches
        // onboarding-not-onboarded is a RETURNING guest whose flag was lost —
        // not a new user. It must skip new-user onboarding and go straight
        // Home, adopting the onboarded flag so it can't bounce back here.
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = false))
        val auth = FakeAuthRepository(initialAuthState = sampleAnonymousGuest)
        val vm = newVm(cache = cache, auth = auth)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToHome, received.firstOrNull())
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingStep.Welcome, vm.state.step, "must not drop into new-user identity setup")
    }

    @Test
    fun continueAsGuest_entersPickIdentity_withoutAuthing() = runUnitTest {
        // No account is created here — guest-tap just enters the identity
        // step; creation is deferred to ContinueFromPickIdentity.
        val auth = FakeAuthRepository() // Unauthenticated by default
        val vm = newVm(auth = auth)

        vm.takeAction(OnboardingAction.ContinueAsGuest)
        runCurrent()

        assertEquals(OnboardingStep.PickIdentity, vm.state.step)
        assertNull(vm.state.authError)
        // Display name is prefilled from a client suggestion.
        assertTrue(vm.state.displayName.isNotBlank())
    }

    @Test
    fun displayNameChanged_setsValue_andUserEditedFlag() = runUnitTest {
        val vm = newVm()
        vm.takeAction(OnboardingAction.ContinueAsGuest)
        runCurrent()

        vm.takeAction(OnboardingAction.DisplayNameChanged("MyChoice"))
        runCurrent()

        assertEquals("MyChoice", vm.state.displayName)
        assertTrue(vm.state.userEditedName)
    }

    @Test
    fun regenerateDisplayName_replacesValue_andMarksUserEdited() = runUnitTest {
        val vm = newVm()

        vm.takeAction(OnboardingAction.RegenerateDisplayName)
        runCurrent()

        assertTrue(vm.state.userEditedName)
        assertTrue(vm.state.displayName.isNotBlank())
    }

    @Test
    fun continueFromPickIdentity_guest_createsAccount_marksOnboarded_andNavigatesHome() = runUnitTest {
        val cache = FakeAppCache()
        val auth = FakeAuthRepository() // Unauthenticated → guest path
        val creator = FakeGuestAccountCreator()
        val vm = newVm(cache = cache, auth = auth, creator = creator)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.ContinueAsGuest)
        runCurrent()
        vm.takeAction(OnboardingAction.DisplayNameChanged("Picked"))
        vm.takeAction(OnboardingAction.ContinueFromPickIdentity)
        runCurrent()

        assertEquals(1, creator.startCalls)
        assertEquals(PendingIdentity(displayName = "Picked"), creator.lastIdentity)
        assertTrue(vm.state.creationStarted)
        assertFalse(vm.state.creationFailed)
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingEvent.NavigateToHome, received.firstOrNull())
    }

    @Test
    fun continueFromPickIdentity_guestCreationFails_stillNavigatesHome_flaggedDegraded() = runUnitTest {
        val cache = FakeAppCache()
        val creator = FakeGuestAccountCreator(failCreation = true)
        val vm = newVm(cache = cache, creator = creator)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.ContinueAsGuest)
        runCurrent()
        vm.takeAction(OnboardingAction.ContinueFromPickIdentity)
        runCurrent()

        assertTrue(cache.get().hasUserOnboarded)
        assertTrue(vm.state.creationFailed)
        assertEquals(OnboardingEvent.NavigateToHome, received.firstOrNull())
    }

    @Test
    fun continueFromPickIdentity_guestCreationNeverTerminates_finishesAfterTimeout() = runUnitTest {
        // The principle: onboarding must NEVER trap the user on a backend call.
        // If guest creation is wedged/offline and never reaches a terminal state,
        // "Continue" must still complete onboarding after the bounded wait —
        // the creator keeps retrying in the background.
        val cache = FakeAppCache()
        val creator = FakeGuestAccountCreator(hangInProgress = true)
        val vm = newVm(cache = cache, creator = creator)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.ContinueAsGuest)
        runCurrent()
        vm.takeAction(OnboardingAction.ContinueFromPickIdentity)
        runCurrent()
        // Creation never terminates — only the timeout lets us through.
        advanceTimeBy(6_000)
        runCurrent()

        assertTrue(cache.get().hasUserOnboarded, "onboarding completes even when creation never terminates")
        assertTrue(vm.state.creationFailed, "degraded flag set when the wait times out")
        assertEquals(OnboardingEvent.NavigateToHome, received.firstOrNull())
    }

    @Test
    fun continueFromPickIdentity_authenticated_patchesProfile_andSurfacesTakenName() = runUnitTest {
        // A real account already exists (e.g. OAuth claimed): patch directly,
        // surfacing a taken name on the field and keeping the user here.
        val cache = FakeAppCache()
        val auth = FakeAuthRepository(initialAuthState = sampleAuthenticated)
        val profile = FakeProfileRepository(
            initial = Profile.Fallback(id = "f"),
            updateOutcome = UpdateProfileOutcome.DisplayNameTaken,
        )
        val creator = FakeGuestAccountCreator()
        val vm = newVm(cache = cache, auth = auth, profile = profile, creator = creator)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }
        runCurrent()

        vm.takeAction(OnboardingAction.DisplayNameChanged("Taken"))
        vm.takeAction(OnboardingAction.ContinueFromPickIdentity)
        runCurrent()

        assertEquals(0, creator.startCalls, "authed path must not create a guest account")
        assertEquals("Taken", profile.lastUpdatedDisplayName)
        assertEquals(OnboardingSaveError.DisplayNameTaken, vm.state.saveError)
        assertFalse(vm.state.isFinishing)
        assertFalse(cache.get().hasUserOnboarded, "a rejected name must not complete onboarding")
        assertTrue(received.isEmpty(), "must stay put so the user can fix the name")
    }

    @Test
    fun continueFromPickIdentity_authenticated_success_marksOnboarded_andNavigatesHome() = runUnitTest {
        val cache = FakeAppCache()
        val auth = FakeAuthRepository(initialAuthState = sampleAuthenticated)
        val profile = FakeProfileRepository(initial = Profile.Fallback(id = "f"))
        val vm = newVm(cache = cache, auth = auth, profile = profile)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }
        runCurrent()

        vm.takeAction(OnboardingAction.DisplayNameChanged("Chosen"))
        vm.takeAction(OnboardingAction.ContinueFromPickIdentity)
        runCurrent()

        assertEquals("Chosen", profile.lastUpdatedDisplayName)
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingEvent.NavigateToHome, received.firstOrNull())
    }

    @Test
    fun back_isBlocked_onceCreationStarted() = runUnitTest {
        val creator = FakeGuestAccountCreator(hangInProgress = true)
        val vm = newVm(creator = creator)
        vm.takeAction(OnboardingAction.ContinueAsGuest)
        runCurrent()
        vm.takeAction(OnboardingAction.ContinueFromPickIdentity)
        runCurrent()
        assertTrue(vm.state.creationStarted)

        vm.takeAction(OnboardingAction.Back)
        runCurrent()

        // Back is a no-op — the account is forming, no return to landing.
        assertEquals(OnboardingStep.PickIdentity, vm.state.step)
    }

    @Test
    fun back_fromPickIdentity_returnsToWelcome_beforeCreation() = runUnitTest {
        val vm = newVm()
        vm.takeAction(OnboardingAction.ContinueAsGuest)
        runCurrent()
        assertEquals(OnboardingStep.PickIdentity, vm.state.step)

        vm.takeAction(OnboardingAction.Back)
        runCurrent()

        assertEquals(OnboardingStep.Welcome, vm.state.step)
    }

    @Test
    fun back_fromWelcome_isNoOp() = runUnitTest {
        val vm = newVm()
        assertEquals(OnboardingStep.Welcome, vm.state.step)

        vm.takeAction(OnboardingAction.Back)
        runCurrent()

        assertEquals(OnboardingStep.Welcome, vm.state.step)
    }

    @Test
    fun signInWithOAuth_providerNotEnabled_surfacesOAuthProviderNotEnabled() = runUnitTest {
        val auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.ProviderNotEnabled)
        val vm = newVm(auth = auth)

        vm.takeAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Google))
        runCurrent()

        assertEquals(OnboardingAuthError.OAuthProviderNotEnabled, vm.state.authError)
    }

    @Test
    fun signInWithOAuth_networkError_surfacesOAuthNetworkError() = runUnitTest {
        val auth = FakeAuthRepository(
            oauthSignInOutcome = SignInOutcome.NetworkError(RuntimeException("offline")),
        )
        val vm = newVm(auth = auth)

        vm.takeAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Apple))
        runCurrent()

        assertEquals(OnboardingAuthError.OAuthNetworkError, vm.state.authError)
    }

    @Test
    fun signInWithOAuth_returningAccount_marksOnboardedAndNavigatesHome() = runUnitTest {
        // isNewAccount=false (the FakeProfileRepository default) → the account
        // already existed, so it's a returning sign-in: skip straight to Home.
        val cache = FakeAppCache()
        val auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.Success)
        val vm = newVm(cache = cache, auth = auth)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Apple))
        runCurrent()

        assertEquals(OAuthProvider.Apple, auth.lastOAuthProvider)
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingEvent.NavigateToHome, received.firstOrNull())
        assertNull(vm.state.oauthInFlight)
    }

    @Test
    fun signInWithOAuth_brandNewAccount_runsThroughOnboarding_notStraightToHome() = runUnitTest {
        // The server reports isNewAccount=true for a freshly-created account →
        // brand-new. It should enter PickIdentity rather than landing cold on
        // Home.
        val cache = FakeAppCache()
        val auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.Success)
        val profile = FakeProfileRepository().apply { isNewAccount = true }
        val vm = newVm(cache = cache, auth = auth, profile = profile)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Google))
        runCurrent()

        assertEquals(OnboardingStep.PickIdentity, vm.state.step)
        assertTrue(vm.state.identityClaimed)
        assertFalse(cache.get().hasUserOnboarded, "brand-new sign-up isn't onboarded until they finish")
        assertTrue(received.isEmpty(), "must not navigate Home from the brand-new path")
        assertNull(vm.state.oauthInFlight)
    }

    @Test
    fun signInWithOAuth_cancelled_silentlyClearsInFlightFlag() = runUnitTest {
        val cache = FakeAppCache()
        val auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.Cancelled)
        val vm = newVm(cache = cache, auth = auth)

        vm.takeAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Google))
        runCurrent()

        assertFalse(cache.get().hasUserOnboarded)
        assertNull(vm.state.oauthInFlight)
        assertNull(vm.state.authError)
    }

    @Test
    fun signIn_emitsNavigateToSignInEvent() = runUnitTest {
        val vm = newVm()
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.SignIn)
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToSignIn, received.firstOrNull())
        assertEquals(OnboardingStep.Welcome, vm.state.step)
    }

    @Test
    fun back_isBlocked_afterOAuthIdentityClaimed() = runUnitTest {
        // A brand-new OAuth account enters PickIdentity with identityClaimed —
        // the Welcome sign-in options no longer apply, so back must be a no-op.
        val auth = FakeAuthRepository(oauthSignInOutcome = SignInOutcome.Success)
        val profile = FakeProfileRepository().apply { isNewAccount = true }
        val vm = newVm(auth = auth, profile = profile)
        vm.takeAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Google))
        runCurrent()
        assertEquals(OnboardingStep.PickIdentity, vm.state.step)

        vm.takeAction(OnboardingAction.Back)
        runCurrent()

        assertEquals(OnboardingStep.PickIdentity, vm.state.step)
    }

    @Test
    fun appleSignIn_asAnonymousGuest_linkSuccess_entersPickIdentity_claimed() = runUnitTest {
        // Brand-new Apple identity gets LINKED to the current anonymous guest
        // (keeps progress) and continues onboarding like a new signup. The
        // cache starts onboarded so init's resolve takes the plain Home bounce
        // instead of the returning-guest adoption — the link behavior under
        // test is unaffected either way.
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = true))
        val auth = FakeAuthRepository(
            linkAppleOutcome = LinkIdentityOutcome.Success,
            initialAuthState = sampleAnonymousGuest,
        )
        val vm = newVm(
            cache = cache,
            auth = auth,
            appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential),
        )

        vm.takeAction(OnboardingAction.SignInWithApple)
        runCurrent()

        assertEquals(1, auth.linkAppleCalls, "anonymous guest must take the link path")
        assertEquals(0, auth.appleSignInCalls)
        assertEquals(OnboardingStep.PickIdentity, vm.state.step)
        assertTrue(vm.state.identityClaimed)
        assertNull(vm.state.oauthInFlight)
    }

    @Test
    fun appleSignIn_identityOnAnotherAccount_signsIn_andNavigatesHome() = runUnitTest {
        // The Apple identity already belongs to an existing account: the link
        // is rejected, so we switch sessions and skip onboarding entirely.
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = true))
        val auth = FakeAuthRepository(
            linkAppleOutcome = LinkIdentityOutcome.AlreadyOnAnotherAccount,
            appleSignInOutcome = SignInOutcome.Success,
            initialAuthState = sampleAnonymousGuest,
        )
        val vm = newVm(
            cache = cache,
            auth = auth,
            appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential),
        )
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.SignInWithApple)
        runCurrent()

        assertEquals(1, auth.appleSignInCalls, "rejected link must fall back to sign-in")
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingEvent.NavigateToHome, received.firstOrNull())
        assertNull(vm.state.oauthInFlight)
    }

    @Test
    fun appleSignIn_noAnonSession_brandNewAccount_runsThroughOnboarding() = runUnitTest {
        // After an account deletion there's NO anonymous session, so the link
        // path is skipped and we sign in. The backend mints a NET-NEW account
        // (server isNewAccount=true). It must run through onboarding
        // (PickIdentity), not land Home.
        val cache = FakeAppCache()
        val auth = FakeAuthRepository(
            appleSignInOutcome = SignInOutcome.Success,
            initialAuthState = AuthState.Unauthenticated(),
        )
        val profile = FakeProfileRepository().apply { isNewAccount = true }
        val vm = newVm(
            cache = cache,
            auth = auth,
            profile = profile,
            appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential),
        )
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.SignInWithApple)
        runCurrent()

        assertEquals(0, auth.linkAppleCalls, "no anonymous guest to link onto")
        assertEquals(1, auth.appleSignInCalls)
        assertEquals(OnboardingStep.PickIdentity, vm.state.step)
        assertTrue(vm.state.identityClaimed)
        assertFalse(cache.get().hasUserOnboarded, "net-new account isn't onboarded until they finish")
        assertTrue(received.isEmpty(), "must not navigate Home from the net-new Apple path")
        assertNull(vm.state.oauthInFlight)
    }

    @Test
    fun appleSignIn_noAnonSession_existingAccount_navigatesHome() = runUnitTest {
        // No anonymous session, but the Apple sign-in lands on a pre-existing
        // account (isNewAccount=false, the default) → skip straight to Home.
        val cache = FakeAppCache()
        val auth = FakeAuthRepository(
            appleSignInOutcome = SignInOutcome.Success,
            initialAuthState = AuthState.Unauthenticated(),
        )
        val vm = newVm(
            cache = cache,
            auth = auth,
            appleCoordinator = FakeAppleSignInCoordinator(credential = sampleAppleCredential),
        )
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { vm.eventFlow.collect { received += it } }

        vm.takeAction(OnboardingAction.SignInWithApple)
        runCurrent()

        assertEquals(1, auth.appleSignInCalls)
        assertTrue(cache.get().hasUserOnboarded)
        assertEquals(OnboardingEvent.NavigateToHome, received.firstOrNull())
        assertNull(vm.state.oauthInFlight)
    }

    @Test
    fun appleSignIn_sheetDismissed_isQuietNoOp() = runUnitTest {
        // (null, null) from the coordinator = the user closed the sheet; no
        // error banner, no in-flight spinner left behind.
        val vm = newVm(appleCoordinator = FakeAppleSignInCoordinator(credential = null))

        vm.takeAction(OnboardingAction.SignInWithApple)
        runCurrent()

        assertNull(vm.state.oauthInFlight)
        assertNull(vm.state.authError)
        assertEquals(OnboardingStep.Welcome, vm.state.step)
    }

    @Test
    fun appleSignIn_nativeFailure_surfacesOAuthFailed() = runUnitTest {
        val vm = newVm(
            appleCoordinator = FakeAppleSignInCoordinator(errorMessage = "ASAuthorization error"),
        )

        vm.takeAction(OnboardingAction.SignInWithApple)
        runCurrent()

        assertEquals(OnboardingAuthError.OAuthFailed, vm.state.authError)
        assertNull(vm.state.oauthInFlight)
    }

    // ---------- Test scaffolding ----------

    private fun newVm(
        cache: FakeAppCache = FakeAppCache(),
        auth: FakeAuthRepository = FakeAuthRepository(),
        profile: FakeProfileRepository = FakeProfileRepository(),
        creator: FakeGuestAccountCreator = FakeGuestAccountCreator(),
        appleCoordinator: FakeAppleSignInCoordinator = FakeAppleSignInCoordinator(),
    ): OnboardingViewModel = OnboardingViewModel(
        appCache = cache,
        authRepository = auth,
        profileRepository = profile,
        authOutcomeClassifier = FakeAuthOutcomeClassifier(profile),
        guestAccountCreator = creator,
        appleSignInCoordinator = appleCoordinator,
    )
}
