# Findings: identity, networking and storage (round two)

`libraries/identity`, `libraries/networking`, `libraries/storage` and their
`impl` modules. Each finding names the place, the rule from
[PRACTICES.md](../PRACTICES.md), what breaks, and its fate. Line numbers are
from before the fix. Findings routed from the cross-cutting audits say which
audit sent them.

Counts: 13 fixed, 2 deferred (finding 13 is cross-slice), 1 not a problem (a
group of checks). The nine cross-slice changes at the end include the parts of
fixed findings that live in other slices; the first one is needed for
`:apps:integration` to compile.

## 1. Logging out offline left the session behind, and the next edge signed the person back in: fixed

- **Where:** `libraries/identity/impl/.../auth/RealSupabaseAuthGateway.kt:102`
  (`signOut`), meeting `SupabaseAuthRepositoryImpl.kt:528` and
  `SessionHealer.kt:100`. Routed by the journeys audit.
- **Rule:** §10 lifecycle and offline; §5 privacy.
- **Failure:** supabase-kt 3.6.0 clears the session only after the logout
  request returns, and catches only `RestException`. In airplane mode the
  request throws first, so the session stayed in memory and in the Keychain /
  EncryptedSharedPreferences (and in the anonymous mirror). The repository
  swallowed the failure and emitted `SignedOut`, but the next
  connectivity-regained or foreground edge ran `SessionHealer.retry()`, which
  found the gateway still authenticated and emitted `Authenticated(X)`. The
  person who handed the phone over "logged out" was signed back in and their
  log pulled down again; a relaunch did the same through `loadSession`.
- **Fix:** "fix(identity): logging out offline clears the stored session".
  `signOut` calls `supabase.auth.clearSession()` in a `finally`, so the local
  copy goes whether or not the server heard. `RealSupabaseAuthGatewayTest`
  runs the real gateway over a real supabase-kt client whose engine throws
  like airplane mode, and checks the session and the stored copy are gone
  (it fails without the `finally`).

## 2. A sign-out decayed to "no session" on the first re-resolve, and self-heal retried before checking it: fixed

- **Where:** `SupabaseAuthRepositoryImpl.kt:232` (`settleUnauthenticatedLocked`
  kept only `SessionExpired` sticky) and `SessionHealer.kt:100` (`retry()`
  ran before the `SignedOut` check). Routed by the journeys audit (the healer
  half); the decay was found here.
- **Rule:** §10 lifecycle; §1 stale comments (the `Reason.SignedOut` KDoc said
  self-heal leaves a sign-out alone).
- **Failure:** `AuthReResolver` calls `retry()` on every warm foreground, which
  re-emitted `Unauthenticated(None)`, so `SignedOut` lasted until the person
  next switched apps. The healer also called `retry()` on a signed-out state,
  which is how finding 1 turned into a sign-in.
- **Fix:** "fix(identity): self-heal never re-resolves a session the person
  logged out of". `SignedOut` is sticky like `SessionExpired`, and the healer
  returns on `SignedOut` before `retry()`. Tests:
  `signOut_offline_staysSignedOutAcrossALaterRetry`,
  `skip_withoutRetrying_whenTheLastStateIsASignOut` (both fail before the fix).

## 3. A session expiry wiped the outbox the screen promised to keep: fixed

- **Where:** `SupabaseAuthRepositoryImpl.kt:316` (`onSessionRejected`) and
  `:357` (`emitLocked` treated X → none as a change of user). Routed by the
  journeys audit.
- **Rule:** §10 a token that expires mid-session; §3 offline first.
- **Failure:** meals logged offline sit in the outbox. When the refresh token
  is then rejected (revoked, reuse detection, an action on another device),
  `onSessionRejected` emitted `SessionExpired` and `emitLocked` ran
  `clearFor(X)`, deleting the outbox. The expired-session copy says to sign in
  again to pick up where you left off; signing back in as X found the meals
  gone.
- **Fix:** "fix(identity): a session expiry keeps unsynced meals for the same
  account". A new `ExpiredAccountStore` holds the departing account (id and
  whether it was a guest) across launches instead of dumping. On the next
  user transition, the same account keeps its data and the hold is released;
  a different account, or a log out, dumps the held data before the new
  state is emitted. `SessionHealer` routes a relaunch with a held account to
  sign in with the right guest or account copy, even offline (the profile
  cache is cleared on expiry, so without this a relaunched guest got the
  claimed copy). Tests: `aSessionExpiry_keepsTheAccountsLocalData_andTheSameAccountSigningBackInKeepsIt`,
  `theHeldAccount_survivesARelaunch` and
  `aRelaunchAfterASessionExpiry_sendsTheHeldAccountToSignIn_withItsGuestCopy`
  fail with the hold disabled; `…thenADifferentAccount_wipes…` and
  `…thenALogOut_wipes…` pin the wipe.
- **Left as is:** after a relaunch, Today renders from Room for the moment
  before the healer pushes the session-expired screen, so the held account's
  own log can show briefly on its own phone. Needs the cross-slice
  `TestClient` change below to compile `:apps:integration`.

## 4. Deleting an account: a dead status table, and every 503 read as "not configured": fixed

- **Where:** `libraries/identity/impl/.../ProfileApi.kt:42` (`deleteMe()`
  returned the raw response) and `SupabaseAuthRepositoryImpl.kt:556`. Routed
  by the contract audit.
- **Rule:** §1 no dead code, stale comments; §9 the client handles the status
  codes the server sends.
- **Failure:** the authenticated client sets `expectSuccess = true`, so any
  non-2xx threw and the `onSuccess` table (401, 503, 404 as success) never
  ran. `onFailure` mapped every 503 to `NotConfigured`, including the server's
  `delete_failed` (a Supabase admin call that failed and is worth retrying),
  so logs reported a deploy problem.
- **Fix:** "fix(identity): a failed account delete says try again, not \"not
  configured\"". `deleteMe()` returns `Unit`; the repository reads the problem
  envelope's code (`ResponseException.problemCode()`): 401 is `NotSignedIn`,
  `delete_not_configured` is `NotConfigured`, any other answer `Unknown`, no
  answer `NetworkError`. Four `deleteAccount_*` tests build the real
  `ServerResponseException` from a MockEngine client. The settings screen
  shows the same message for all three failures, so the change is in the logs.

## 5. `MeDto.createdAtEpochMs` said the server doesn't send it: fixed

- **Where:** `libraries/identity/impl/.../MeDto.kt:31`. Routed by the contract audit.
- **Rule:** §1 stale comments; §9 contract.
- **Failure:** `MeResponse` has carried `createdAtEpochMs` for a while; the
  comment said "(yet)".
- **Fix:** in the commit for finding 4.

## 6. Reason, Fallback and queued-edit KDocs still described guest minting: fixed (identity part); deferred cross-slice (the rest)

- **Where:** `AuthRepository.kt:115`, `:239`, `:248`, `:256`, `:261`;
  `ProfileRepository.kt` (`Profile.Fallback` "syncs to the server",
  `UpdateProfileOutcome.Queued` "applied server-side when a session is
  minted", `resolveIsNewAccount` "Consuming" while the impl is not);
  `SupabaseClientFactory.kt:23`; `SessionHealer.kt:146`;
  `ProfileRepositoryImpl.kt:392`. Routed by the truth audit.
- **Rule:** §1 stale comments are bugs.
- **Failure:** the docs said self-heal mints a guest for `Reason.None`, a guest
  on the expired screen "starts fresh", the Fallback's name reaches the server,
  and onboarding can finish offline. None of that happens: self-heal refreshes
  or sends the person to sign in, and the Fallback's name never leaves the
  device.
- **Fix:** "docs(identity): describe today's sign-in-only flow, not guest
  minting", plus the `SessionHealer` and `ProfileRepositoryImpl` lines in the
  commits for findings 3 and 8.
- **Deferred (cross-slice):** listed below (`ErrorRoutes.kt`,
  `AppViewModel.kt`, `AppCache.kt`, `SignUpViewModel.kt`).

## 7. The Supabase config KDocs named one source; release reads another: fixed (identity part); deferred cross-slice (server README)

- **Where:** `DefaultIdentityConfig.kt:11`, `IdentityConfig.kt:4` ("points at
  the dev project; flip via build variants when prod ships"). Routed by the
  truth audit.
- **Rule:** §9 one switch per build picks the environment.
- **Failure:** both said the project comes from `local.properties` or env
  vars. Since d67368c3 that is only debug; release reads the committed
  `supabase.properties` through `SupabaseInfo` on `BuildInfo.isDebug`. This
  reasoning is what let 0.1.0 ship signed in to dev.
- **Fix:** in the identity docs commit.
- **Deferred (cross-slice):** `apps/server/README.md:128-134`, below.

## 8. `PendingGuestAccountStore` held no guest account: fixed (rename); the writers it names are test-only

- **Where:** `libraries/identity/impl/.../auth/PendingGuestAccountStore.kt:23`,
  `ProfileRepositoryImpl.queueGuestIdentityEditLocked`. Routed by the truth audit.
- **Rule:** §1 names do the documenting.
- **Fix:** "refactor(identity): name the Fallback's held display name for what
  it is". Now `FallbackDisplayNameStore` (cache file still
  `pending_guest_account`) and `holdFallbackDisplayNameLocked`. The KDoc says
  `set()` is reached only through `ProfileRepository.update`, which no screen
  calls (finding 13). Needs the `TestClient` change below.
- **Deferred (cross-slice):** `SignUpViewModel.kt:13`, below.

## 9. Gateway KDocs that were false: fixed

- **Where:** `SupabaseAuthGateway.kt:5` ("no HTTP engine slot", while the
  factory sets `httpEngine`), `:161` ("sign in (anonymous, or otherwise)"),
  `GatewaySession.isEmailConfirmed` ("always true for anonymous accounts";
  `RealSupabaseAuthGateway` returns false for them), and the `signOut`
  contract (now: always clears locally, then throws).
- **Rule:** §1 stale comments.
- **Fix:** the identity docs commit and finding 1's commit.

## 10. `InternalNetworkingApi` sent callers to a deleted WebSocket helper: fixed

- **Where:** `libraries/networking/.../InternalNetworkingApi.kt:7` and `:19`
  (the opt-in error message).
- **Rule:** §1 stale comments.
- **Failure:** round one deleted `authedWebSocketSession`; the KDoc and the
  compiler error a developer sees still recommended it.
- **Fix:** "docs(networking): drop the deleted WebSocket helper and a README
  that never existed".

## 11. The missing-base-URL error pointed at a README that doesn't exist: fixed

- **Where:** `libraries/networking/impl/.../UnresolvedRelativeRequest.kt:14`.
- **Rule:** §1 stale comments.
- **Failure:** "see libraries/networking/README or SETUP.md → Networking";
  only the second exists.
- **Fix:** same commit as finding 10.

## 12. The bearer KDoc said the token is re-read on every call: fixed

- **Where:** `NetworkClient.kt:31`, plus "anon disabled" in
  `AuthTokenProvider.kt:36` and `NetworkClientImpl.kt:70`.
- **Rule:** §1 stale comments.
- **Failure:** Ktor's bearer plugin caches the loaded token; the
  `AuthTokenInvalidator` exists precisely because it isn't re-read. A reader
  trusting the KDoc would skip the invalidation on a new session path.
- **Fix:** same commit as finding 10.

## 13. The whole profile-edit pipeline has no production caller: deferred (cross-slice)

- **Where:** `ProfileRepository.update`, `flushPendingEdits`,
  `observeEditRejections`, `observeAccountJustCreated`;
  `PendingProfileEditStore`, `ProfileEditFlusher`, the flush half of
  `ProfileRepositoryImpl`, `FallbackDisplayNameStore.set`, and the client's
  `DisplayNameRules` (only its own test calls it).
- **Rule:** §1 no dead code.
- **Failure:** no screen edits the display name, so none of this runs in the
  app; it costs a listener on every foreground and connectivity edge. If a name
  editor ships as is, there is a real bug waiting: `PendingProfileEditStore`
  is not a `UserScopedClearer`, so an edit queued offline by one account would
  be PATCHed onto the next account to sign in.
- **Why deferred:** deleting it changes the `ProfileRepository` interface and
  the fakes in `features/home` (`ScenarioProfileRepository`),
  `features/onboarding` (`FakeProfileRepository`) and `:apps:integration`
  (`TestClient`). It is also a product call (finding P1). Either delete it all
  in one change, or, if the editor is coming, make `PendingProfileEditStore` a
  `UserScopedClearer` first.

## 14. The session store writes with `apply()`: deferred

- **Where:** `libraries/identity/impl/src/androidMain/.../EncryptedSessionStorage.kt:44`.
- **Rule:** §8 Android, process death survives.
- **Failure (possible):** supabase-kt rotates the refresh token on every
  refresh. `apply()` writes to disk later; a process killed in that window
  (a background refresh just before Android reclaims the app) restarts with
  the old refresh token, which Supabase's reuse detection can answer by
  revoking the session. `commit()` costs nothing here because the session
  manager already writes on the IO dispatcher.
- **Why deferred:** the change needs a device or instrumented check;
  EncryptedSharedPreferences needs the Android Keystore, which Robolectric
  doesn't provide, so the module has no harness for a test that fails first.

## 15. Unused `datastore-preferences` shipped in both apps: fixed

- **Where:** `libraries/storage/build.gradle.kts:16`.
- **Rule:** §1 no dead code.
- **Failure:** every cache is a typed okio DataStore; nothing in the repo uses
  Preferences DataStore, but it was an `api` dependency.
- **Fix:** "build(storage): drop datastore-preferences, which nothing uses".
  The Android app still compiles.
- **Deferred (cross-slice):** the now-unused version-catalog aliases, below.

## 16. Checked: not a problem

- **Cancellation:** every `Catching`, `runCatching` and `catch` in the slice.
  `awaitOAuthRedirect` catches only its own `TimeoutCancellationException`;
  the `classExists` catch in `NetworkInspectorLauncher.android.kt` wraps no
  suspend call. The new code uses `Catching` and `finally` only.
- **Round one's fixes hold:** the offline launch settles `SessionUnconfirmed`
  and `markSessionUnrecoverable` refuses during `RefreshFailure`; a 401 for a
  stale token retries with the newer one; the 10 s connect timeout is applied;
  cache corruption reports the exception type only; the DAO wipe logs at
  error; the self-heal log carries the error type, not its message.
- **`AuthGateImpl.verdict` kicking the healer** from a non-suspending getter:
  single-flight, fire-and-forget, and only for the healable state.
- **`DataStoreCacheFactory`'s plain map:** round one's reading still holds;
  the new `expired_session_account` cache is requested by one singleton.
- **Wiretap on iOS defaults to the real artifact:** the store pipelines set
  `OPSON_WIRETAP_IOS`, and installation and launch are gated on
  `BuildInfo.isDebug` at runtime.
- **The Android connectivity callback** (round one's finding 15): the
  `TODO(review)` is still in place and still needs a device check.
- **unslop scanner:** the HIGH hits are KDoc lines starting "Note:", the emoji
  are test data and the code-point table in `DisplayNameRules`, and the long
  names are Ktor and Network framework APIs.

## Second review

A second reviewer read this branch's commits for regressions. Its first list
was lost before the fates were written, so the four fixes it led to were
re-checked here (items 1 to 4), and a second pass found four more (items 5 to
8). Every test named below was run with its fix reverted and failed.

1. **The expired screen's "sign in again" tap wiped the held meals (b71a2c2b):
   real, fixed.** That screen signs out before routing to sign-in, and the
   sign-out counted as a log out of the held account, so the outbox the copy
   promised to keep went on the tap. Fixed in "fix(identity): the \"sign in
   again\" tap keeps the expired account's meals": only a user change settles
   the hold. `aSessionExpiry_thenTheSignInAgainTap_thenTheSameAccount_keepsItsLocalData`
   fails with the old condition back (cleared `[user-1]`);
   `…thenTheSignInAgainTap_thenADifferentAccount_wipesTheHeldData` pins the wipe.
2. **An unreadable hold let the next account inherit the held data (b71a2c2b):
   real, fixed.** A failed read was treated as no hold, so a different account
   signing in kept the previous one's rows and drained its outbox. Fixed in
   "fix(identity): an unreadable expiry hold wipes before a sign-in".
   `aSessionExpiry_thenASignInWhileTheHoldCantBeRead_wipesTheHeldData` fails
   with the wipe disabled, and `aLaunchWithALiveSession_whileTheHoldCantBeRead_keepsItsData`
   fails if the guard on the previous state is dropped. Two corners are left,
   both needing a disk read to fail: a corrupt file is replaced by DataStore's
   corruption handler with an empty record, which reads as no hold, and an
   offline launch that reconnects while the read fails wipes its own outbox.
3. **A sign-out cancelled while the logout request hung kept the Keychain copy
   (f54d22fd): real, fixed.** `clearSession()` ran in the `finally`, but the
   production `SecureSessionManager` hops to the IO dispatcher, which throws
   in a cancelled coroutine. Fixed in "fix(identity): a cancelled sign-out
   still clears the stored session" (`NonCancellable`).
   `RealSupabaseAuthGatewayTest.signOut_cancelledWhileTheRequestHangs_stillClearsTheStoredSession`
   fails without it. The same commit imports the test session after the
   client initialises, which stopped a hang about one run in forty.
4. **The impl's latch KDoc said reading it resets it (left stale beside
   12272ba5): real, fixed** in "docs(identity): the new-account latch resets
   on sign-out, not on read". Comment only. Checked against the code:
   `resolveIsNewAccount` never resets it; any unauthenticated resolve (a
   sign-out among them) and a change of account id do.
5. **A cancelled sign-out or delete now left the app signed in with no session
   (f54d22fd, ac0c2083): real, fixed.** The gateway clears the session even
   when cancelled, but the repository's `Catching` rethrew the cancellation
   before `emitUnauthenticatedLocked`. Leaving the More screen while the logout
   spun kept `Authenticated`, the cached bearer and the account's data, with
   nothing behind them; before this branch the same cancel simply left the
   person signed in. Fixed in "fix(identity): a sign-out cancelled mid-request
   still signs out": the emit runs in a `NonCancellable` `finally`.
   `signOut_cancelledWhileTheLogoutRequestHangs_stillSignsOut` and
   `deleteAccount_cancelledWhileSigningOut_stillSignsOut` fail without it
   (the state stays `Authenticated`).
6. **A relaunch after the "sign in again" tap showed the expired screen again
   (040124e9 with b71a2c2b's healer branch): real, fixed.** The tap now keeps
   the hold and resets onboarding, and self-heal checked the hold before
   onboarding, so a relaunch mid-sign-in pushed the expired screen over
   onboarding and the person had to tap it twice. Fixed in "fix(identity): a
   relaunch after \"sign in again\" stays on sign-in".
   `aRelaunchAfterTheSignInAgainTap_staysOnSignIn_withoutTheExpiredScreenAgain`
   fails without it (marked unrecoverable once).
7. **Two KDocs that the branch made false (040124e9, 9393d613): real, fixed**
   in "docs(identity): the expiry hold survives the expired screen's sign-out".
   `ExpiredAccountStore` still said a log out wipes the held data, and
   `FallbackDisplayNameStore.set` promised to save the name once a session
   exists. No test. Finding 3's "or a log out" above describes the first
   version of that fix; cross-slice item 7 below is corrected to match.
8. **A stranded account's data went to whoever signed in next: real, fixed.
   Not a regression of this branch; it was there before.** When the session
   died before the run saw it signed in (revoked while the app was closed, or
   lost from storage, AUTH-19), `emitLocked` had no previous user to hold or
   dump. After "sign in again", a different account kept the stranded
   account's diary rows and its outbox drained into the new account. Fixed in
   "fix(identity): a stranded account's data is held for the next sign-in":
   self-heal holds the account it found before routing to sign-in, so the hold
   from finding 3 settles it. `aStrandedAccount_isHeldForWhoeverSignsInNext`
   fails without it (no hold).

Checked and not a problem: 36d7699e (its two tests fail with the fix removed;
any sign-in clears the sticky `SignedOut`); 4c469f1d (404 is no longer a
success, but the server answers an already deleted account with 204, and a 401
the network layer remaps to `AuthUnready` still reads as `NetworkError`, as
before; the `delete_failed` test fails with the old 503 mapping); 9393d613 (the
cache file keeps its name); the claims in 12272ba5 and 753e9821 (`SupabaseInfo`
keys on `BuildInfo.isDebug`, `isEmailConfirmed` comes from `emailConfirmedAt`,
`SETUP.md` has a Networking section); 3914451a (nothing in the repo uses
Preferences DataStore); 7efc5f24 (test import only); 2bb829f1 (the ledger edit
stays inside this slice's section). The hold tests from b71a2c2b and the
offline sign-out test from f54d22fd fail with their fixes disabled.

## Deferred cross-slice changes

1. **`apps/integration/src/androidUnitTest/kotlin/com/opson/integration/helpers/TestClient.kt`**
   (test-apps). Required for `:apps:integration` to compile with findings 3
   and 8. Verified: with this change the whole integration suite passes.
   - Replace `import com.opson.libraries.identity.impl.auth.PendingGuestAccountStore`
     with `import com.opson.libraries.identity.impl.auth.ExpiredAccountStore`
     and `import com.opson.libraries.identity.impl.auth.FallbackDisplayNameStore`.
   - In the `SupabaseAuthRepositoryImpl(...)` call, after
     `sessionRejectionBus = sessionRejectionBus,` add
     `expiredAccounts = ExpiredAccountStore(cacheFactory),`.
   - In the `ProfileRepositoryImpl(...)` call, replace
     `pendingGuestAccountStore = PendingGuestAccountStore(cacheFactory),` with
     `fallbackDisplayNameStore = FallbackDisplayNameStore(cacheFactory),`.
2. **`libraries/navigation/src/commonMain/kotlin/com/opson/libraries/navigation/ErrorRoutes.kt:22-23`**:
   replace `"sign in again" for a claimed account, "start fresh" (mint a new guest session) for an anonymous one.`
   with `"sign in again" for a claimed account, and sign in (losing this phone's guest log) for a guest.`
3. **`apps/compose/src/commonMain/kotlin/com/opson/AppViewModel.kt:191`**:
   `// owns "sign in again" (claimed) vs "start fresh" (guest).` becomes
   `// owns the copy: sign in again (claimed), or sign in losing this phone's guest log (guest).`
   Also line 170-173: "The teardown (clear caches / session / account-scoped data)
   already happened" is no longer true for an expiry; say "The session teardown
   already happened in AuthRepository; the account's data is kept until someone signs in."
4. **`libraries/opson/src/commonMain/kotlin/com/opson/libraries/AppCache.kt:88-90`**:
   `// A full sign-out → continue-as-guest is a deliberate fresh start, so the next identity is re-offered onboarding ...`
   becomes `// A sign-out is a fresh start: the next account is offered onboarding rather than inheriting the previous user's completion.`
5. **`features/onboarding/impl/src/commonMain/kotlin/com/opson/features/onboarding/impl/SignUpViewModel.kt:12-15`**:
   `if the current session is anonymous (the typical guest-claim path), call linkEmailIdentity ...; otherwise fall back to signUpWithEmail`
   becomes `if the session is a guest from an older build, link the email onto it (linkEmailIdentity) so its log stays; otherwise sign up (signUpWithEmail).`
6. **`apps/server/README.md:128-134`**: replace the client paragraph with:
   "The **client** half lives in `:libraries:identity(:impl)`. It signs in with
   Apple, Google or email (accounts are required) through supabase-kt, and
   `GatewayAuthTokenProvider` supplies the bearer token to the network client,
   replacing the default `NoOpAuthTokenProvider`. The Supabase project comes
   from `SupabaseInfo`: the dev project from `local.properties` in debug
   builds, prod from the committed `supabase.properties` in release."
   (It currently names `AuthRepository.signInAnonymously()` and a
   `SupabaseAuthTokenProvider`, neither of which exists.)
7. **`AGENTS.md`, "Auth model", the "User-change choke point" bullet**: add
   "A session expiry is the exception: the departing account is held
   (`ExpiredAccountStore`) and dumped only when a different account signs in,
   so signing back in keeps unsynced meals."
8. **`gradle/libs.versions.toml:248-249`** (build-config): delete the
   `androidx-datastore-preferences` and `androidx-datastore-preferences-core`
   aliases; nothing references them after finding 15.
9. **Finding 13** (profile-edit pipeline): the interface and fake changes
   listed there, if the product decision is to delete.

## Product notes

- **P1. There is no way to change your display name.** The repository,
  offline queue, flusher and validation rules are all built, and no screen
  calls them. Either ship a name field in settings or delete the machinery
  (finding 13). Sure that it is unreachable; unsure how much people want it.
- **P2. Logging out offline only logs out this phone.** The local session is
  cleared now, but the server never heard, so the refresh token stays valid
  on Supabase until it expires. Retrying the server logout on the next
  connection would close that. Fairly sure it matters to someone who logs out
  because they lost trust in a device.
- **P3. A failed delete reads the same whatever went wrong.** The settings
  screen shows one message for "not configured", "session gone" and "try
  again". Now that the outcomes are told apart, "try again in a minute" for
  `delete_failed` and "sign in again first" for `NotSignedIn` would tell the
  person what to do. Fairly sure.
- **P4. After a session expiry and a relaunch, Today flashes the held log
  before the sign-in screen covers it.** It is the same person's data on
  their own phone, but if the session was revoked on purpose (a lost phone),
  showing it at all is the wrong call. A blocking state at boot while an
  account is held would avoid both the flash and the leak. Medium sure.

## Deferred cross-slice items: fates (2026-10-28)

Applied on branch `worktree-agent-a89d6bb1fb957d17a` once every slice was on main.

1. Applied in 7e3358f7, with the rest of what `:apps:integration` needed to
   compile.
2. Already done on main: `SessionExpiredRoute`'s KDoc says a guest is told
   their log can't be recovered.
3. Applied in ce067efe.
4. Already done on main: `resetAccountScoped` says a sign-out is a fresh start.
5. Applied in ce067efe.
6. Applied in 2f254417.
7. Applied in 2f254417.
8. Applied in def6dc41. Only `androidx-datastore-preferences` was left; the
   `-core` alias was already gone.
9. Still open, needs a decision (finding 13, P1): whether a name editor is
   coming. Delete the pipeline, or make `PendingProfileEditStore` a
   `UserScopedClearer` first.
