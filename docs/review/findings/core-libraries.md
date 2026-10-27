# Findings: core libraries

`libraries/` core modules: config, core, flowroutines, identity, navigation,
networking, opson, progressphotos, review, storage, telemetry. Each finding names
the place, the rule from [PRACTICES.md](../PRACTICES.md), what breaks, and its
fate. Line numbers are from before the fix.

Counts: 34 fixed, 12 deferred (3 of them cross-slice), 5 not a problem (each a group of checks).

## Identity, networking, flowroutines and core

### 1. An offline launch signed out the stored account: fixed

- **Where:** `libraries/identity/impl/.../auth/SupabaseAuthRepositoryImpl.kt:166`
  (`resolveAndEmitLocked`) with `GuestSessionHealer.kt:134` (`stopForRecovery`).
- **Rule:** §3 offline first; §2 errors at the edges (a transient failure is a value, not a verdict).
- **Failure:** with the access token expired and no network, supabase-kt keeps
  the session and reports `RefreshFailure` while it retries. The resolve loop
  polled that five times with no wait and settled plain `Unauthenticated`. The
  healer then found the cached profile, called `markSessionUnrecoverable`, and
  `onSessionRejected` signed the session out. A guest who opened the app offline
  an hour after last use lost their account for good; a claimed user was sent to
  sign in again.
- **Fix:** "fix(identity): an offline launch never signs out the stored account".
  The resolve settles with a `SessionUnconfirmed` cause that the healer skips,
  and `markSessionUnrecoverable` refuses while supabase still reports
  `RefreshFailure`. Tests: `aStoredSessionAwaitingRefresh_isNeverSignedOutAsUnrecoverable`
  (failed before the fix), `skip_whenTheStoredSessionIsOnlyAwaitingRefresh`.

### 2. State read right after an update was the old state: fixed

- **Where:** `libraries/flowroutines/.../SEAViewModel.kt:84` (`stateFlow` was a
  `stateIn` copy of the backing flow, collected in `viewModelScope`).
- **Rule:** §2 flows; §3 one state.
- **Failure:** `state` lagged `updateState` until the collector ran, so a second
  action queued in the same frame read the old state. Guards like
  `if (state.export == Working) return` in `SupportViewModel` let a double tap
  through. `SEAViewModelStateTest` showed `[0]` after an update to `1` under both
  test dispatchers.
- **Fix:** "fix(flowroutines): state read right after an update sees the update".
  `stateFlow` is the backing `MutableStateFlow`. `mapEachState` and
  `updateStateDebounced`, which the copy existed for, had no users and are gone.

### 3. A double tap pushed the same screen twice: fixed (requested by the settings review)

- **Where:** `libraries/navigation/impl/.../DelegatingRouter.kt:150` (`executeNavigate`).
- **Rule:** §3; review-checklist (two taps in 50 ms).
- **Failure:** two navigates to the same route queued in one frame each pushed
  an entry: two pause sheets on Modes.
- **Fix:** "fix(navigation): a double tap no longer opens the same screen twice".
  `NavController.navigateUnlessShowing` drops a navigate whose route, arguments
  included, is already on top, unless the call clears the back stack. Routes
  compare by serialized form, because most are plain classes and two instances
  are never `equals`. `RepeatNavigationTest` (Robolectric) failed both cases
  with the guard disabled.

### 4. A 401 for a stale token forced a second refresh: fixed

- **Where:** `libraries/identity/impl/.../auth/GatewayAuthTokenProvider.kt:61`,
  `libraries/networking/impl/.../NetworkClientImpl.kt:77`.
- **Rule:** §6 retries; identity session refresh races.
- **Failure:** supabase-kt refreshes before expiry, but Ktor's bearer plugin keeps
  the token it loaded first. So once an hour the first authed call 401s and
  `refreshTokens` forced another refresh. If that extra refresh could not reach
  the auth server the call failed, though a valid token was already in hand.
- **Fix:** "fix(identity): a 401 for a stale token retries with the session's
  newer one". `refreshAccessToken(rejected)` returns the session's current token
  when it differs from the refused one. Test:
  `refreshAccessToken_whenTheSessionAlreadyRefreshed_returnsItWithoutAnotherRefresh`.

### 5. A connection that never opens held a call for 30 s: fixed

- **Where:** `libraries/networking/impl/.../NetworkClientImpl.kt:152`
  (`connectTimeoutMillis = config.requestTimeoutMillis`).
- **Rule:** §6 external calls have timeouts.
- **Failure:** on a dead Wi-Fi the OS still reports as up, a search or sync sat
  for the whole 30 s before an error or the offline state could show.
- **Fix:** "fix(networking): give up on a connection that will not open after 10 s".
  `NetworkConfig.connectTimeoutMillis` (10 s). OkHttp applies it; NSURLSession
  has no separate connect timeout, so iOS still waits for the 30 s socket
  timeout. Test: `CommonConfigTimeoutTest`.

### 6. The photo estimate's 70 s timeout is cut to 30 s: deferred (cross-slice)

- **Where:** `libraries/foods/impl/.../HttpMealPhotoEstimator.kt:44` sets
  `timeout { requestTimeoutMillis = TIMEOUT_MS }` only.
- **Rule:** §6 timeouts.
- **Failure:** the client's `socketTimeoutMillis` stays 30 s, and the server is
  silent while the vision model works, so OkHttp's read timeout and
  NSURLSession's idle timeout fire at 30 s. An estimate that takes 31 to 70 s
  fails although the code means to wait.
- **Change (platform-libraries):** `timeout { requestTimeoutMillis = TIMEOUT_MS; socketTimeoutMillis = TIMEOUT_MS }`.

### 7. Finished config refreshes piled up jobs in the app scope: fixed

- **Where:** `libraries/config/impl/.../OfflineFirstAppConfigRepository.kt:145`, `:170`.
- **Rule:** §2 structured concurrency.
- **Failure:** each refresh launched in a new `childSupervisorScope`, whose
  `SupervisorJob` never completes, so every foreground refresh left a job in the
  app scope for the life of the process (8 children after 6 refreshes in the test).
- **Fix:** "fix(config): finished config refreshes no longer pile up jobs in the
  app scope". Both launch in the app scope; `childSupervisorScope` is deleted.
  Test: `finishedRefreshes_leaveNothingBehindInTheAppScope`.

### 8. Feedback dropped with crash reporting off still thanked the person: fixed (requested)

- **Where:** `libraries/opson/impl/.../AppTelemetry.kt:154`, and the caller
  `features/home/impl/.../feedback/FeedbackRepository.kt` (home slice, changed at
  the coordinator's request).
- **Rule:** review-checklist error surfaces.
- **Failure:** with Sentry off or a blank message the report was dropped and
  `submitFeedback` still succeeded, so the feedback and bug report screens
  thanked the person.
- **Fix:** "fix(opson): feedback dropped with crash reporting off reads as not
  sent". `captureUserFeedback` returns whether it sent; the repository maps a
  drop to `FeedbackNotSent`, which both screens already show as a failure. Test:
  `FeedbackRepositoryImplTest`.

### 9. `Catching.recoverFrom` could never recover, and most helpers had no callers: fixed

- **Where:** `libraries/core/.../Catching.kt:77` checked
  `exception::class.isInstance(error)`, asking whether the KClass object is an
  instance of the exception's class. Always false.
- **Rule:** §1 no dead code.
- **Fix:** "refactor(core): delete helpers nothing calls, including a recoverFrom
  that never matched". Also deleted: `asCatching`, `failFast`,
  `getExceptionOrNull`, `ignore`, `illegalStateFailure`, `withBackoffRetry`,
  `success`/`failure`, `checkInDebug`, `allOrNone`, commented-out code.
  `Catching` now says why it rethrows cancellation.

### 10. Cancellation through `Catching` and every `catch`: not a problem

- **Where:** every `Catching`, `runCatching` and `catch` in the slice.
- **Why:** `Catching` rethrows `CancellationException` except
  `TimeoutCancellationException`, which is meant to be a value. The two remaining
  explicit catches (`tryWithTimeout`, `awaitOAuthRedirect`) catch only
  `TimeoutCancellationException` from their own `withTimeout`. The two
  `catch (e: Exception)` in `VersionedCacheJsonSerializer` wrap no suspend call.
  `KnownErrorCodes`' `runCatching` was around a plain predicate and is deleted.

### 11. Dead core and flowroutines code, including a race in KLog: fixed

- **Where:** `MotionDetector` and both platform detectors; `ConcurrentHashMap`
  (built on `kotlinx.coroutines.internal`); `StringExt.applyArgs`;
  `BuildInfo.versionTag`; in `KLog.kt` the never-enabled call-site tagging (it
  would have built a stack trace per log line), the unused global scope, and
  `setTag`, which mutated a field on loggers shared across threads;
  `CalculateOnce`, `LazySuspend`, `TriggerFlow` (in the wrong package),
  everything in `FlowExt` but `collectIn`, `observeWithLifecycleIn`,
  `IllegalViewModelStateException`.
- **Rule:** §1 no dead code.
- **Fix:** same commit as finding 9.

### 12. Networking shipped an unused WebSocket client and test-only artifacts: fixed

- **Where:** `NetworkCall.authedWebSocketSession`, `WebSocketKeepalive.*`,
  `installWebSocketInspector`, `ApiProblem.kt`; `ktor.client.mock` and
  `ktor.client.logging` in `networking/impl` `commonMain`.
- **Rule:** §1 no dead code.
- **Failure:** nothing opens a socket from the app; the authenticated client
  still installed the plugin and the Wiretap socket capture. The MockEngine
  artifact shipped in main.
- **Fix:** "refactor(networking): drop the unused WebSocket client, problem
  envelope and test-only deps".

### 13. Stale comments in networking, core and the test helpers: fixed

- **Where:** `NetworkClientImpl` named `RoomRepositoryImpl`; `NetworkCall` named
  `ReconnectingRoomSocket` and `docs/plans/client-app-events-otel.md`;
  `WebSocketKeepalive` cited a `SocketKeepaliveTest` that doesn't exist;
  `RetryPolicy` and `NetworkCall` used template examples (chip purchases, wallet
  sync, `inventory.sync`); `CoroutineTest` described a "bot loop" and "four"
  dispatchers; `AuthGate` and identity KDocs spoke of multiplayer rooms,
  real-money purchases, chips and XP.
- **Rule:** §1 stale comments are bugs.
- **Fix:** in the networking and core refactor commits, and "docs(identity):
  describe what an account holds in this app, not the template's chips and XP".

### 14. A failed guest mint logged the Supabase error message at warn: fixed

- **Where:** `GuestSessionHealer.kt:190`.
- **Rule:** §5 errors carry context, not payloads.
- **Fix:** "fix(identity): a failed guest mint logs the error type, not its message".

### 15. The Android connectivity callback listens to every network: deferred

- **Where:** `libraries/networking/impl/src/androidMain/.../AndroidConnectivityObserver.kt:50`.
- **Rule:** §8 Android.
- **Failure:** `registerNetworkCallback` reports every network with INTERNET, so
  an unvalidated background network (captive Wi-Fi while cellular carries
  traffic) can send `false` and hold the offline banner up while requests work.
- **Why deferred:** `registerDefaultNetworkCallback` is the likely fix, but the
  difference is in which events the OS delivers, which needs a device or a
  Robolectric harness this module doesn't have. `TODO(review)` in place.

### 16. Auth stays unauthenticated after supabase recovers on its own: deferred

- **Where:** `SupabaseAuthRepositoryImpl.settleUnconfirmedLocked`.
- **Failure:** nothing watches supabase-kt's status, so after finding 1's case
  the app stays unauthenticated until the next foreground or connectivity edge
  calls `retry()`, even once supabase has refreshed.
- **Why deferred:** needs a status flow on `SupabaseAuthGateway` and its fakes.
  `TODO(review)` in place.

### 17. `AppState.isBlockActive` is never read: fixed (cross-slice)

- **Where:** `libraries/core/.../AppState.kt:7`, `AppStateImpl.kt:78`.
- **Rule:** §1 no speculative generality.
- **Change:** delete the property from `AppState` and `AppStateImpl`, and the
  overrides in `features/logger/impl/src/commonTest/.../Fakes.kt:409` and
  `libraries/ui/src/commonMain/.../Preview.kt:160` (plus the identity and opson
  test fakes in this slice). Done by the orchestrator: "refactor(core): drop
  AppState.isBlockActive, which nothing read".

### 18. Smaller things checked: not a problem

- `Router.toastOnError` and its English defaults (the UI review's request): the
  helpers had no caller and are deleted (finding 22).
- Full URLs in logs (the shells review's request): no log in this slice carries
  a URL.
- `NetworkJson` is strict in debug and lenient in release: deliberate and documented.
- `PermissionManager` and its types (the shells review's request): deleted after
  a whole-repo grep, "refactor(opson): delete the unused permission manager and its types".
- unslop scanner: the emoji hits are test data and the code-point table in
  `DisplayNameRules`; the "Note:" hits are KDoc.

## Navigation, review and progress photos

### 19. A failed read of the progress photo index orphaned every earlier photo: fixed

- **Where:** `libraries/progressphotos/impl/.../ProgressPhotoRepositoryImpl.kt:117`, `:130`, `:134`; `IosProgressPhotoFileStore.kt:60`.
- **Rule:** §2 errors at the edges; §3 never lose a write.
- **Failure:** a read that threw (disk error, iOS file protection while locked)
  was cached as "no photos", and the next `add` wrote an `index.json` with only
  the new photo.
- **Fix:** "fix(progressphotos): an index that can't be read is not overwritten
  with an empty one". Test: `anIndexThatCannotBeReadIsNeitherRememberedAsEmptyNorOverwritten`.

### 20. The deep link bridge dropped early links and replayed the last one: fixed

- **Where:** `libraries/navigation/impl/.../DeepLinkBridgeImpl.kt:23` (`MutableSharedFlow(replay = 1)`), KDoc at `:14`.
- **Rule:** §1 stale comments; §2 flows.
- **Failure:** before `App.kt` collected, only the newest link survived; each new
  collector got the last link again (an OAuth callback re-imported).
- **Fix:** "fix(navigation): deep links wait for the app and are handled once".
  `Channel(UNLIMITED)`; the KDoc example now uses `routeDeepLink`.

### 21. A suspended user saw a raw ISO timestamp: fixed

- **Where:** `AccessDeniedScreen.kt:84`.
- **Fix:** "fix(navigation): a suspension's end shows as a date, not a raw
  timestamp". Test: `AccessRestoredDateTest`.

### 22. Dead navigation code, a log per recomposition, and a route logged with its arguments: fixed

- **Where:** `Router.kt:92-144` (`blockingScreenOnError`, `dialogOnError`,
  `toastOnError` with hardcoded joke copy), `KnownErrorCodes.kt`, `Anims.kt`,
  `Route.get*Transition()`, `DelegatingRouter.viewScope`;
  `DelegatingRouter.Bind` logged at info on every recomposition (§4, §5);
  `DelegatingRouter.kt:266` logged a blocked route with its arguments, which
  for `BugReportRoute` is an error message (§5); stale KDoc in
  `FeatureEntryPoint` and `FloatingWindowNavigator`.
- **Fix:** "refactor(navigation): delete dead error helpers, animations and router state".

### 23. `SessionExpiredViewModel` had no tests: fixed

- **Fix:** "test(navigation): cover the session-expired screen's two ways out".

### 24. Review module: stale comment, misnamed test, unused deps: fixed

- **Where:** `RealReviewPromptCoordinator.kt:22`; `concurrentRequests_collapseToOnePrompt`; `libraries/review/build.gradle.kts`.
- **Fix:** "refactor(review): fix a stale comment and a misnamed test, drop unused deps".

### 25. An unexplained 100 ms delay before the navigation queue drains: deferred

- **Where:** `DelegatingRouter.awaitGraphAttachment`.
- **Why:** nobody knows what it waits for. `TODO(review)` until a launch and deep-link test can show it is unneeded.

### 26. The blocking-error and error-dialog routes are unreachable: deferred

- **Where:** `ErrorRoutes.kt`, `BlockingErrorScreen.kt`, `ErrorDialogContent.kt`, `ErrorEntryPoints.kt`, `NavigableWhileBlocked`.
- **Why:** removing the subsystem (about 300 lines) is a product call.

### 27. No app-store review prompt ever shows: deferred

- **Where:** `ReviewPromptCoordinator` has no caller. Product call (see notes).

### 28. Shared exports are written on the main thread: deferred

- **Where:** `AndroidFileShareLauncher.kt:30`, `IosFileShareLauncher.kt:47`.
- **Why:** making `FileShareLauncher.share` suspend changes the interface for a few milliseconds of cost today.

### 29. `BugReportRoute` sits in a feature package inside a library: fixed (cross-slice)

- **Change:** move `libraries/navigation/src/commonMain/kotlin/com/opson/features/profile/BugReportRoute.kt`
  to `com.opson.libraries.navigation` and update its importers in the home
  feature and the app shell. Done by the orchestrator: "refactor(navigation): move
  BugReportRoute into com.opson.libraries.navigation".

### 30. Navigation items checked: not a problem

- `IosWebLinkLauncher` gates on `canOpenURL`: https links work in daily use;
  only undeclared schemes would silently do nothing.
- `TabBarImpl.hide` updates two flows non-atomically: main thread only.
- Visit tracking runs before the blocking gate: the gate never blocks today (finding 26).

## Opson and config

### 31. Reading QA overrides blocked main at every launch: fixed

- **Where:** `ConfigOverrideRepositoryImpl.kt:36` (`runBlocking { configCache.get() }` in the constructor of a class an `AutoInit` repository injects).
- **Rule:** §2 never block main; §3 launch path is cheap.
- **Fix:** "fix(config): read QA overrides off the launch path". Test: `construction_doesNotReadTheCache`.

### 32. iOS sent a foreground on every reactivation, and the session could roll mid-use: fixed

- **Where:** `IosAppLifecycle.kt:64` (`DidBecomeActive`), `AppEventDispatcher.kt:74`, `SessionTrackerImpl.kt:94`.
- **Failure:** Control Center, a permission alert or a call ending each sent
  `WarmBoot` and `OnForeground`: a sync of every store, an extra
  `app.foregrounded`, and a session gap measured from an old background.
- **Fix:** "fix(opson): an iOS reactivation is not a new foreground". Tests:
  `aReactivationWithoutABackground_isNotAnotherForeground`,
  `aSecondForeground_withNoBackgroundBetween_doesNotRoll`. Needs finding 50's test change.

### 33. Every user's email and display name went to Sentry: fixed

- **Where:** `TelemetryUserBinder.kt:60`, `AppTelemetry.kt:125`.
- **Rule:** §5 never emails.
- **Fix:** "fix(opson): send only the user id to crash reporting". Test: `nameAndEmailChanges_doNotReachTelemetry`.

### 34. A template stub syncer ran in production: fixed

- **Where:** `ExampleUserScopedSyncer.kt`, contributed to the live syncer set, with
  a `runCatching` recipe in its KDoc.
- **Fix:** "refactor(opson): delete the example syncer and other unused code".
  The recipe moved to the `UserScopedSyncer` KDoc, using `Catching`.

### 35. Unused extension telemetry and `StartUpWarning`: fixed

- **Fix:** same commit as finding 34.

### 36. Opson comments pointing at things that don't exist: fixed

- **Where:** `Session.kt`, `SessionTrackerImpl.kt`, `UserScopedWorkRegistry.kt:17`, `UserScopedData.kt`, `UserScopedSyncCoordinator.kt`.
- **Fix:** "docs(opson): correct comments that describe code that no longer exists".

### 37. A failed first fetch saved the bundled fallback config as if the server sent it: fixed

- **Where:** `OfflineFirstAppConfigRepository.kt:178`.
- **Fix:** "fix(config): stop saving the bundled fallback config to disk". Test:
  `refresh_failureWithNoCachedConfig_servesTheFallbackWithoutPersistingIt`.

### 38. Dead config helpers and a derived path that did nothing: fixed

- **Where:** `MapExt.kt` (seven helpers), `ConfiguredValue.toPath()` (lowercased inside `apply`, result discarded), `config/impl/build.gradle.kts` stale comment and unused dependency.
- **Fix:** "refactor(config): remove unused helpers and the derived config path".

### 39. QA config overrides have no writer: deferred (product)

- The override layer runs at launch, but nothing can set an override. Restore a QA menu or delete the layer.

### 40. `AppData.screenVisits` is written on every navigation and never read: deferred (cross-slice)

- **Where:** `libraries/navigation/impl/.../AppNavigationTracker.kt:27` rewrites the whole
  `AppData` file on each visit to a `TrackableRoute` to keep a counter nothing reads.
- **Change:** delete `AppNavigationTracker`, `NavigationTracker`, `TrackableRoute` and the
  three `AppData` members; `FeedbackRoute` (home slice) and `BugReportRoute` then extend
  `Route`. Left for the orchestrator because of the home-slice route.

### 41. `example_user_data` is created and wiped with no consumer: deferred

- **Why:** dropping the table needs a new Room migration; low value.

### 42. Opson and config items checked: not a problem

- `FallbackConfigMap`'s `runBlocking`: the compose-resources reader is
  synchronous on iOS and the file is 2 bytes.
- Emoji in `DevConsoleWriter`: debug-only severity markers.

## Telemetry and storage

### 43. Warn-and-above forwarding sent search text and response bodies to Grafana: fixed

- **Where:** `GrafanaLogTree.kt:137` (`exception_message = throwable.message`), `:126`.
- **Rule:** §5 never search text.
- **Failure:** a Ktor `ClientRequestException` message quotes the URL
  (`/v1/foods/search?q=<what the user typed>`) and the response body, and
  `NetworkCall` logs every failed call at warn.
- **Fix:** "fix(telemetry): forward the exception type to Grafana, never its
  message". Test: `klogForwardingOn_aFailedSearch_doesNotShipTheSearchText`.

### 44. A corrupt cache file sent its contents to Sentry: fixed

- **Where:** `VersionedCacheJsonSerializer.kt:33`, `:52`.
- **Failure:** kotlinx.serialization quotes the input in its message, and
  `ProfileCache` holds the email.
- **Fix:** "fix(storage): keep a cache file's contents out of its corruption
  report". Test: `anUnreadableFile_doesNotCarryItsContentsIntoTheException`.

### 45. A failed user-data wipe was only a warn: fixed

- **Where:** `UserScopedDaoCleaner.kt:36`.
- **Fix:** "fix(storage): log a failed user-data wipe as an error". Test: `UserScopedDaoCleanerTest`.

### 46. Dead storage code: fixed

- `FileManager.deleteFile`/`deleteAll`, `CacheEncryption`/`NoEncryption`, the
  never-read `inMemoryCaches` map (held every in-memory cache forever),
  `CoreTypeConverters`, the Room plugin on `:libraries:storage`, and a file in
  the wrong directory. "refactor(storage): delete file-manager deletes, cache
  encryption and Room converters nothing uses".

### 47. Stale `GrafanaLogTree` KDoc: fixed

- "docs(telemetry): point GrafanaLogTree at the app-events doc that exists".

### 48. No test runs the Room migrations 5 to 9: fixed (cross-slice)

- **Change (test-apps):** add a `RoomMigrationTest` to `:apps:integration` that,
  for each version 5 to 8, builds a file database from the exported schema JSON
  with `BundledSQLiteDriver`, sets `user_version`, inserts a `log_entries` row,
  reopens it with `Room.databaseBuilder<AppDatabase>` and asserts the row survives.
- **Fix:** "test(integration): Room migrations from schema 5, 6, 7 and 8 keep every row". It
  fills every table of the old schema, opens the file through `RealAppDatabaseProvider` and
  compares every value; dropping `AutoMigration(7, 8)` fails it.

### 49. The Grafana SDK is built on main at cold start: deferred

- **Where:** `GrafanaLogTree.openTelemetryLazy`, first forced by `app.launched` on
  the main-thread foreground callback (binder calls and file checks on Android).
- **Why:** both fixes change event ordering; measure with StrictMode first. `TODO(review)` in place.

### 50. iOS keeps DataStore files and the database in Documents: deferred

- Moving to Application Support needs a one-time move on upgrade, and the backup may be wanted.

### 51. Telemetry and storage items checked: not a problem

- `DataStoreCacheFactory.persistent`'s plain map: each cache name is requested by one singleton.
- DataStore on the Default dispatcher: off main, small files.
- A downgrade resets a cache: every cache is at version 1.
- The OTel disk buffer is capped (100 batches, 30 days).
- Kill switch, sampling and install facts are evaluated per forward; `app.jank` sends only the route class name.

## Deferred cross-slice changes

1. **platform-libraries:** `libraries/foods/impl/.../HttpMealPhotoEstimator.kt:44`
   add `socketTimeoutMillis = TIMEOUT_MS` (finding 6).
2. **test-apps:** `apps/integration/.../config/RemoteFlagTest.kt` lines 60 and 69:
   add `phone.lifecycle.background()` before each re-foreground. Required by
   finding 32: without it `RemoteFlagTest` times out, since it foregrounds twice
   with no background, which no device does.
3. **test-apps:** the `RoomMigrationTest` in finding 48. Done.
4. **repo root:** `AGENTS.md:117`, replace "see `ExampleUserScopedSyncer` for the
   two-line registration recipe" with "see the `UserScopedSyncer` KDoc for the
   registration recipe".
5. **docs:** `docs/practices/app-events.md:233`, change
   "`exception_type`/`exception_message` when a throwable was attached" to
   "`exception_type` (never the message, which can carry URLs and response
   bodies) when a throwable was attached".
6. **logger and ui:** remove the `isBlockActive` overrides with finding 17. Done.
7. **home and apps-shells:** move `BugReportRoute` (finding 29). Done.
8. **apps-shells:** `apps/compose/src/androidMain/AndroidManifest.xml:36` says
   MainActivity forwards links to the `DeepLinkBridge`; `MainActivity` does not
   (only reminder and widget taps). Forward `intent.data` to
   `deepLinkBridge.emit` in `onCreate`/`onNewIntent`, or fix the comment. If the
   Android browser OAuth return relies on it, it never reaches
   `completeOAuthRedirect`. Unverified.

Made at the coordinator's request outside this slice: the feedback caller in
`features/home/impl/.../feedback/FeedbackRepository.kt` and its new test
(finding 8).

## Product notes

- **Opening the app offline after a while used to cost guests their account**
  (finding 1). Worth checking Sentry for `markSessionUnrecoverable` and
  SessionExpired screens around offline launches; any guest who hit it is gone.
  Fairly sure the bug was real; I didn't reproduce it on a device.
- **No store review prompt ever appears.** The coordinator and both launchers
  exist; nothing asks. Wire it to a good moment or delete it. Sure it's unwired.
- **iOS session counts and `app.foregrounded` were inflated** before finding 32.
  Expect a step down in those dashboards after release. Moderately sure.
- **Warn-and-above forwarding to Loki is on by default** for every warn line in
  the app. Volume and the shared stack's quota grow with users; decide before
  launch. Medium.
- **Any decode failure of `AppData` resets the whole record**, onboarding flag
  included, so a schema slip in one release sends everyone back through
  onboarding. Tolerant decoding for that cache would help. Medium.
- **QA config overrides** cost launch work and can't be set. Restore a debug
  menu or delete them. Fairly sure.
- **The suspension screen shows a date only.** If suspensions are hours long,
  "restored on" today's date reads oddly. Low.
- **Progress photos can look empty while the iPhone is locked** (file
  protection). No longer loses data, but expect "my photos vanished" reports. Medium.
