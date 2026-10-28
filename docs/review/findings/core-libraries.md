# Findings: core libraries (round two)

`libraries/` modules config, core, flowroutines, navigation, opson and review.
Each finding names the place, the rule from [PRACTICES.md](../PRACTICES.md),
what breaks, and its fate. Line numbers are from before the fix.

Counts: 10 fixed, 9 deferred (6 of them cross-slice), 5 not a problem (each a
group of checks). The audit item routed here is finding 1 (fixed) and
finding 12 (its deletion half, deferred cross-slice).

## Fixed

### 1. AuthGate's KDoc described gating that doesn't exist: fixed (audit: truth)

- **Where:** `libraries/core/.../AuthGate.kt:6-13`, `:20-27`, `:36-37`, `:41`, `:71`, `:91-103`.
- **Rule:** §1 stale comments are bugs; no dead code.
- **Failure:** the KDoc said routes declare an `AuthRequirement` that rides on
  every `Route`, and that `AuthReason` rides on an `AuthGateRoute`. `Route` has
  no such field and `AuthGateRoute` does not exist. `FinishingSetup` was
  "guest-account creation is still finishing", `Account` included "a guest
  whose creation is still pending", and the gate cached "guest-creation" state.
  Nothing creates guests; `AuthGateImpl` returns `FinishingSetup` only while
  `SessionHealer` revives or reroutes a stranded session. `mapAuthFailure` and
  `onAuthFailure` had no callers, and the `@Serializable` annotations served the
  nav arguments the enums never became.
- **Fix:** "docs(core): AuthGate says what it gates today, and drops helpers
  nothing calls". The `ClaimedAccount` half of the audit item is finding 12.

### 2. Dead helpers in core, and an AutoInit KDoc naming one entry point of three: fixed

- **Where:** `RateLimiter.kt` (no callers), `Catching.eitherWay`, the top-level
  `throwIfDebug(Throwable)` in `ThrowableExtensions.kt:23`, `Scope.removeTag` and
  `removeExtra` in `LogContext.kt:36`; `AutoInit.kt:18` said the set resolves in
  `App.kt` on first composition, with `ProductsRepository` and "catalog grids,
  avatar pickers" as examples.
- **Rule:** §1 no dead code; stale comments.
- **Failure:** the set resolves first in `OpsonApplication.onCreate` and
  `iOSApp.init`; a reader timing launch work from the KDoc looks in the wrong place.
- **Fix:** "refactor(core): delete RateLimiter and other helpers nothing calls".

### 3. Every visit to Feedback or Bug report rewrote AppData for a counter nobody read: fixed in slice (round one's 40)

- **Where:** `DelegatingRouter.kt:102`, `:151`; `AppNavigationTracker.kt`;
  `NavigationTracker.kt`; `AppCache.kt:31` (`screenVisits`, `getVisitCount`,
  `incrementVisit`); `BugReportRoute.kt:10`.
- **Rule:** §1 no dead code; §3 launch-path and per-action cost.
- **Failure:** each navigate to a `TrackableRoute` launched a coroutine that
  rewrote the whole `app_data` file. Nothing read the count.
- **Fix:** "refactor(navigation): stop counting screen visits nobody reads".
  The cache decodes with `ignoreUnknownKeys`, and `AppDataReadTest` pins that a
  file still holding `screenVisits` reads with the onboarding flag intact (an
  unreadable `app_data` would reset it). `TrackableRoute` stays, marked
  `TODO(review)`, until finding 15 lands.

### 4. A link that would not open went to Sentry with the link, and crashed debug builds: fixed

- **Where:** `DelegatingRouter.kt:233` (`openWebLink`), `IosWebLinkLauncher.kt:17`, `:21`.
- **Rule:** §5 never health data; errors carry context, not payloads; right level.
- **Failure:** `openWebLink` logged `"Failed to open web link: $url"` at error
  with the platform exception, whose message quotes the link (Android's
  `ActivityNotFoundException` prints the intent). Support's eating disorder
  helpline opens through this path, so a failed tap became a Sentry error event
  under the user's id that says they reached for the helpline. `SentryUrlScrubbing`
  strips queries, not hosts. The same call ran `throwIfDebug()`, so a debug
  build crashed on a device with no browser.
- **Fix:** "fix(navigation): a link that won't open is a warning that leaves the
  link out". A warning naming the scheme and exception type; the iOS messages
  drop the link. `OpenWebLinkTest` failed before the fix (it caught the
  `DebugException` carrying the URL).

### 5. ObserveWithLifecycle kept the first composition's handler: fixed

- **Where:** `libraries/flowroutines/.../Compose.kt:63`.
- **Rule:** §4 no `LaunchedEffect` that holds stale values; §2 flows.
- **Failure:** the effect is keyed on the lifecycle and the flow, so `onItem`
  and everything it captured were frozen at first composition. Every screen's
  `ObserveEvents` goes through it; a handler that reads a parameter or state
  (a sheet state after recomposition, a route argument) acts on the old one.
  No current caller visibly misbehaves, which is why it survived.
- **Fix:** "fix(flowroutines): ObserveWithLifecycle hands items to the latest
  handler", with `rememberUpdatedState` as `OnTabReselected` already does.
  `ObserveWithLifecycleTest` (navigation's composition tier, since flowroutines
  has none) failed with `[first]` before the fix.

### 6. SessionExpiredRoute promised a "start fresh" guest: fixed

- **Where:** `libraries/navigation/.../ErrorRoutes.kt:21`.
- **Rule:** §1 stale comments.
- **Failure:** the KDoc said an anonymous session gets "start fresh (mint a new
  guest session)". The screen's one way on is signing in.
- **Fix:** "docs(navigation): SessionExpiredRoute no longer offers a fresh guest".

### 7. Config KDocs from the template, and an override nothing set: fixed

- **Where:** `ConfiguredValue.kt:11-27` (a `GoogleSignInEnabled` example bound
  as `ConfiguredValue::class`; real values bind as `QaConfigValue`),
  `ConfiguredValue.kt:68` (`debugOverride`, set by nothing) and its read in
  `AppConfigMap.value`, `JsonConfigValue.kt:16-45` (level ladders, loot tables,
  `ProgressionConfig`), `OfflineFirstAppConfigRepository.kt:215`
  (`progressionConfig.levelCurve()`), `MapExt.kt:25` ("monetization flag").
- **Rule:** §1 stale comments; no dead code.
- **Failure:** a new flag copied from the KDoc would bind under the wrong key
  and never appear in the QA menu.
- **Fix:** "refactor(config): drop the unused debugOverride and the template's examples".

### 8. AppEvent.UserChanged listed guest creation: fixed

- **Where:** `libraries/opson/.../AppEvent.kt:15`; `AppCache.kt:88` (continue-as-guest).
- **Rule:** §1 stale comments.
- **Fix:** "docs(opson): UserChanged no longer lists guest creation" and, for
  `resetAccountScoped`, the commit in finding 3.

### 9. A test named for a guest heal that no longer exists: fixed

- **Where:** `UserScopedSyncCoordinatorTest.kt:72`.
- **Fix:** "test(opson): the fresh-install sync test signs in rather than healing a guest".

### 10. `asDeferredOrNull` returns a value, never a Deferred: fixed

- **Where:** `AndroidReviewLauncher.kt:52`.
- **Rule:** §1 names do the documenting.
- **Fix:** "refactor(review): name the Play task await for what it returns".

## Deferred

### 12. `AuthRequirement.ClaimedAccount` and `AuthReason.NeedClaimedAccount` have no caller: deferred (cross-slice, identity-networking)

- **Where:** `libraries/core/.../AuthGate.kt` (marked `TODO(review)`),
  `libraries/identity/impl/.../auth/AuthGateImpl.kt:68-70`,
  `AuthGateImplTest.kt:150-166`, `libraries/networking/.../NetworkCall.kt:22-29`.
- **Rule:** §1 no dead code.
- **Why deferred:** deleting the enum entries breaks `AuthGateImpl` and its
  test, which another slice owns. The change is below.

### 13. The blocking-error gate only knows the route nothing opens: deferred

- **Where:** `DelegatingRouter.kt:274` (`isBlockingErrorActive` checks `BlockingErrorRoute`).
- **Rule:** §10 journeys; review-checklist blocking surfaces.
- **Failure:** nothing navigates to `BlockingErrorRoute`, so the gate never
  closes. `SessionExpiredRoute` and `AccessDeniedRoute` are the screens meant to
  block, and they don't: a reminder tap or a widget's + calls
  `router.navigate(LoggerRoute(...))` and the Logger opens over a dead session
  or a suspension, with Back returning to the block.
- **Why deferred:** covering them needs `OnboardingRoute` (features/onboarding)
  let through, since it is SessionExpired's own way out, and a router test
  harness with a real `NavHostController` that `:libraries:navigation:impl`
  lacks. `TODO(review)` in place. Round one's 26 (delete the unreachable
  blocking-error and error-dialog routes) is the other half of the same call.

### 14. `Telemetry.setContext` has no callers: deferred (cross-slice)

- **Where:** `libraries/opson/.../Telemetry.kt:51-61` (KDoc speaks of "the
  current lobby code"), `AppTelemetry.kt:147`.
- **Why deferred:** a test fake in `features/home` overrides it.

### 15. FeedbackRoute keeps TrackableRoute alive: deferred (cross-slice, home)

- **Where:** `features/home/src/commonMain/kotlin/com/opson/features/home/FeedbackRoute.kt:7`.
- **Why deferred:** home slice. Then `TrackableRoute.kt` can go.

### 16. `AppData.feedbacksGiven` and `bugsReported` are written and never read: deferred (cross-slice, home)

- **Where:** `AppCache.kt:34-35`; writers `FeedbackViewModel.kt:41`, `BugReportViewModel.kt:55`.
- **Failure:** another whole-file rewrite per report for a counter nothing reads.

### 17. The shake easter egg's context is always the same: deferred (cross-slice, apps-shells)

- **Where:** `ShakeMessageProvider.kt:15-16` (`isLateNight`, `isFirstSession`
  and the branches they gate); `apps/compose/.../ShakeHandler.kt:77-78` passes
  `false` for both.
- **Failure:** dead branches, about thirty lines of copy no one can see.

### 18. A stale TODO(review) in settings for a bug the router fixed: deferred (cross-slice, settings)

- **Where:** `features/settings/impl/.../SettingsFeatureEntryPoint.kt:187-188`
  says two quick taps push two pause sheets and asks for a router fix. Round
  one's 3 made `navigateUnlessShowing` drop a navigate whose route is already on
  top, sheets included (`RepeatNavigationTest`).

### 19. The shake copy is English in a Kotlin file: deferred

- **Where:** `ShakeMessageProvider.kt:59-246`.
- **Rule:** user-facing copy lives in `libraries/resources`.
- **Why deferred:** about a hundred strings, the app ships English only, and
  the shake dialog is an easter egg in front of the bug report. Worth doing
  with localization, or with finding 17.

### 20. Round one's deferrals in this slice, unchanged

- 25 (the unexplained 100 ms in `awaitGraphAttachment`, `TODO(review)` in
  place), 26 (unreachable error routes), 27 (no review prompt is ever
  requested), 28 (exports written on the main thread), 41 (`example_user_data`
  with no consumer). Round one's 39 (QA overrides had no writer) is resolved:
  `features/qa/impl` writes them now.

## Not a problem

### 11. Round one's fixes in this slice still hold

- `SEAViewModel.stateFlow` is the backing flow (round one 2); config refreshes
  launch in the app scope (7); `Catching` rethrows cancellation (9, 10); deep
  links are a queue (20); QA overrides are read off the launch path (31); an iOS
  reactivation is not a foreground (32); only the user id reaches Sentry (33);
  the fallback config is never persisted (37); `DelegatingRouter.Bind` no longer
  logs per recomposition (22).

### 21. Environment values in this slice follow one switch

- `SupabaseInfo` picks dev or prod on `BuildInfo.isDebug`, the same switch as
  `apiBaseUrl` in `OpsonNetworkConfig.kt`. Android's `isDebug` is the library's
  `BuildConfig.DEBUG`, which follows the consuming variant; iOS reads
  `Platform.isDebugBinary`. Sentry's environment carries channel, platform and
  build type. Telemetry keys come from build config, blank leaving each pipe off.

### 22. Cancellation and catches

- `tryWithTimeout` catches only its own `TimeoutCancellationException`;
  `runWhen` and `AndroidReviewLauncher` go through `Catching`;
  `DefaultUserScopedDataReset` and `AppEventDispatcher` catch per participant
  without eating cancellation.

### 23. A throw in `handleAction` ends the view model's action loop

- By design: a programmer error fails fast (§2), and `updateState` logs and
  rethrows only in debug.

### 24. unslop scanner hits

- The emoji are `DevConsoleWriter`'s debug-only severity markers (round one
  agreed); `example.com` appears only in previews and tests; the long names are
  test names and Apple's `startAccelerometerUpdatesToQueue`.

## Deferred cross-slice changes

1. **identity-networking (finding 12):** in `libraries/core/src/commonMain/kotlin/com/opson/libraries/core/AuthGate.kt`
   delete `AuthRequirement.ClaimedAccount` with its `TODO(review)` and
   `AuthReason.NeedClaimedAccount`. In
   `libraries/identity/impl/src/commonMain/kotlin/com/opson/libraries/identity/impl/auth/AuthGateImpl.kt:66-75`
   replace the `Authenticated` branch with
   `is AuthState.Authenticated -> AuthVerdict.Ready` (keep the "even offline"
   comment). In `AuthGateImplTest.kt` delete the two `ClaimedAccount` tests
   (`:150-166`). In `libraries/networking/src/commonMain/kotlin/com/opson/libraries/networking/NetworkCall.kt:22-29`
   replace "(no session, offline, guest where a claimed account is needed, …)"
   with "(no session, offline, an expired session)" and the two sentences from
   "Callers surface it with `mapAuthFailure`" to "only the rare claimed-account
   call passes one." with "Callers ignore it with `getOrNull()`; a call that
   does nothing special is already correct. [requirement] defaults to
   [AuthRequirement.Account]." (`mapAuthFailure` and `onAuthFailure` are deleted.)
2. **home (finding 14):** delete `setContext` from
   `libraries/opson/src/commonMain/kotlin/com/opson/libraries/Telemetry.kt:51-61`,
   its override in `libraries/opson/impl/.../AppTelemetry.kt:147-152`, and the
   fake overrides in `features/home/impl/src/commonTest/.../feedback/FeedbackRepositoryImplTest.kt:31`
   and `libraries/opson/impl/src/commonTest/.../TelemetryUserBinderTest.kt:176`.
3. **home (finding 15):** `features/home/src/commonMain/kotlin/com/opson/features/home/FeedbackRoute.kt:7`
   `class FeedbackRoute : Route()` (import `Route`), then delete
   `libraries/navigation/src/commonMain/kotlin/com/opson/libraries/navigation/TrackableRoute.kt`.
4. **home (finding 16):** delete `feedbacksGiven` and `bugsReported` from
   `libraries/opson/src/commonMain/kotlin/com/opson/libraries/AppCache.kt:34-35`,
   the `appCache.update` lines in `FeedbackViewModel.kt:41` and
   `BugReportViewModel.kt:55`, and the four assertions on them in
   `FeedbackViewModelTest.kt` (`:38`, `:54`, `:80`, `:94`). `AppDataReadTest`
   reads `feedbacksGiven`; change its assertion to `installId` only.
5. **apps-shells (finding 17):** drop `isLateNight = false` and
   `isFirstSession = false` from `apps/compose/src/commonMain/kotlin/com/opson/ShakeHandler.kt:77-78`,
   then delete both fields from `ShakeMessageContext` and the `if (context.isLateNight)`
   and `if (context.isFirstSession)` blocks in `ShakeMessageProvider.kt`.
6. **settings (finding 18):** delete the two `TODO(review)` comment lines at
   `features/settings/impl/src/commonMain/kotlin/com/opson/features/settings/impl/SettingsFeatureEntryPoint.kt:187-188`.

## Product notes

- **A suspended or signed-out phone still opens the Logger from a reminder or
  the widget** (finding 13). Someone whose session died sees the "sign in
  again" screen, taps a reminder, and logs a meal that can't sync. Decide
  whether those taps should wait behind the block. Sure of the mechanism; not
  reproduced on a device.
- **Support's helpline taps were reaching Sentry when they failed** (finding 4).
  Worth checking Sentry for "Failed to open web link" events and deleting them,
  since they tie a user id to the helpline. Fairly sure some exist if any
  device lacked a browser; medium.
- **Feedback and bug report counters, screen visits and the review prompt
  timestamps are all bookkeeping for a review prompt nobody asks for**
  (findings 3 and 16, round one's 27). Either wire the review prompt to a good
  moment (a closed day, a week of logging) or delete the whole set. Sure it's
  unwired.
- **The shake dialog greets people by name and counts their shakes** ("Old
  friends now", "Is this love?"). For an eating disorder recovery app the jokey
  register around an accidental jostle may land badly. Low confidence; a design
  call.
