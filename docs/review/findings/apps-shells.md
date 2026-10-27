# Findings: apps-shells (`apps/admin`, `apps/compose`, `apps/ios/iosApp`)

All 55 files in the ledger were read, with the Info.plist, the entitlements and
the admin's `config-manifest-registry.json` beside them, and the unslop-code
scanner was run over all three (framework names only). Checks: `./gradlew
detekt`, `:apps:compose:testDebugUnitTest`, `:apps:admin:jsTest`,
`:apps:compose:compileKotlinIosSimulatorArm64` and the `xcodebuild` Debug
simulator build are green. Nothing here touches `iosApp.entitlements` or the
Release configuration.

**Runs at launch after this review:** the same as before, minus the Swift
`IOSPermissionManager` (finding 13). `MainActivity` now also collects the
theme setting to style the system bars (finding 4): one StateFlow, no I/O.

## 1. Main didn't compile: the boot screen used a deleted component (fixed)

- **Where:** `BootLoadingScreen.kt:12`, after `42122a5` removed
  `CircularLoadingIndicator`.
- **Failure:** `:apps:compose` failed to compile, and the iOS build phase hid it.
- **Fix:** by the orchestrator, `fix(app): boot screen uses
  CircularProgressIndicator after the alias was removed` and `build(ios): fail
  the Xcode build when the Kotlin framework fails to compile`, picked into this
  branch.

## 2. A returning user could be held on the boot screen forever (fixed)

- **Where:** `AppViewModel.kt:150` (before): `profileRepository.observe().first()`.
- **Rule:** §3 "every launch-time path is cheap"; the file's own comment, "a
  wedged fetch can never strand the user".
- **Failure:** the config wait was capped at 8 s, the profile wait wasn't. The
  first profile is a `/v1/me` call, and a resolve that throws (anything but a
  network error) emits nothing, so `first()` never returned and the loading
  captions cycled forever.
- **Fix:** `fix(app): a profile that never resolves can't hold the boot
  screen`. `AppViewModelTest` fails without it (checked by reverting).

## 3. A deep link that failed to route was logged with its tokens (fixed)

- **Where:** `App.kt:120`: `"Failed to handle deep link: $url"`.
- **Rule:** §5 "Never ... tokens"; "Secrets never in code, logs, URLs".
- **Failure:** an error-level line goes to Sentry and Grafana. An auth redirect
  carries the session in its fragment or query (`opson://auth/confirmed#access_token=…`).
- **Fix:** `fix(app): a deep link that fails to route is logged without its
  query or fragment`, with `DeepLinkLogTest`. The same commit drops the
  transition `reason` strings, built on every navigation and read by nothing.

## 4. Android's status bar icons were dark in a dark theme (fixed)

- **Where:** `MainActivity.kt:26`: `enableEdgeToEdge` once, with
  `SystemBarStyle.light` for both bars.
- **Rule:** §8 Android; Look and feel's Dark must be readable.
- **Failure:** in Dark (or System on a dark phone) the clock, battery and
  navigation icons were dark on the dark app.
- **Fix:** `fix(app): Android's bar icons follow the theme the app draws`. The
  bars follow Settings.theme and, on System, the night mode (uiMode changes
  stay in process, so `onConfigurationChanged` feeds it). `ThemeMode.isDark` is
  shared with `UserAppTheme` and tested in `UserThemeTest`.

## 5. iOS has the same problem under a forced theme (deferred)

- **Where:** `MainViewController.kt`, `TODO(review)`.
- **Failure:** the status bar follows the system appearance, so Dark on a light
  phone draws dark status text over the dark app.
- **Why deferred:** the fix sets the window's `overrideUserInterfaceStyle` from
  the theme, which also restyles system sheets, and needs checking in the
  simulator, which this review doesn't drive.

## 6. Shake handling could end the process (fixed)

- **Where:** `ShakeHandler.kt:35`: `CoroutineScope(SupervisorJob() + Dispatchers.Main)`.
- **Rule:** §2 "No ad-hoc `CoroutineScope(...)`".
- **Failure:** no exception handler, so a throw from `profileRepository.current()`
  or the router while handling a shake reached Android's default handler.
- **Fix:** `refactor(app): shake handling runs in the app scope; the splash
  drops a dead branch`. `AppCoroutineScope` logs instead (its own test covers
  that); the collector still runs on Main for the router.

## 7. Dead code in the Compose shell (fixed)

- `SplashOverlay` re-checked the platform its only caller checks and latched a
  flag a run-once effect never needed (commit for 6).
- `ComposeAppCommonTest` asserted 1 + 2 = 3; `projects.libraries.opson.storage`
  was listed twice (commit for 2).

## 8. Unused Swift and a dead iOS 14 branch (fixed)

- **Where:** `KotlinPrimitives.swift` (no callers, grepped repo-wide),
  `RootComposeView`'s unread `scenePhase`, `ReviewLauncher.swift`'s pre-iOS 14
  branch on an 18.2 target.
- **Rule:** §1 "No dead code".
- **Fix:** `refactor(ios): drop unused Swift helpers and an iOS 14 fallback, fix
  the HealthKit note`. The review prompt now calls `AppStore.requestReview(in:)`,
  which replaces the deprecated `SKStoreReviewController` call.

## 9. Info.plist misplaced the HealthKit entitlement (fixed)

- **Where:** `Info.plist`, the HealthKit comment.
- **Rule:** §1 "Stale comments are bugs".
- **Failure:** it said the entitlement is in `iosApp.entitlements`; it is only in
  `iosAppDebug.entitlements` until 0.2 (`docs/plans/0.2/release-checklist.md`).
- **Fix:** same commit as 8.

## 10. Admin kept the last environment's flags after Disconnect (fixed)

- **Where:** `apps/admin/.../Main.kt:139` (before): Disconnect cleared only `api`
  and `connectedEnv`.
- **Rule:** brief, error and loading states; the console exists to make prod
  unmistakable.
- **Failure:** connect to dev, Disconnect, connect to prod: the table showed
  dev's flags, manifest and versions under the PROD banner until the reload
  landed, and kept them if it failed.
- **Fix:** `fix(admin): connecting elsewhere clears the last environment, and
  failed loads say so`. Connect starts from the list its token check fetched.

## 11. Admin dropped a rule or flag draft on Cancel (fixed)

- **Where:** `RuleEditor.kt:177`, `FlagsView.kt:380`: the form cleared before
  the confirm sheet answered.
- **Failure:** Cancel, or a write the server refused, lost a rule with a dozen
  conditions typed in.
- **Fix:** same commit as 10; the form clears once the write lands.

## 12. Admin's Waitlist and Audit said Loading forever on a failed read (fixed)

- **Where:** `WaitlistView.kt:80`, `AuditView.kt:49`.
- **Fix:** same commit as 10, which also labels the error log's ✕ button and
  drops a `@Suppress("TooGenericExceptionCaught")` with no catch under it.
- **No test for 10 to 12:** the admin's jsTest has no DOM harness; its pure
  functions are unchanged and their tests pass.

## 13. A PermissionManager built at launch and never asked (fixed in this slice, deferred cross-slice)

- **Where:** `apps/ios/iosApp/Platform/PermissionManager.swift`,
  `IosAppComponent.kt`, `iOSApp.swift`.
- **Rule:** §1 "No dead code"; §2 "Never block main" (its status check waited
  on a semaphore).
- **Failure:** the template's notifications-only manager was constructed in
  `iOSApp.init` and bound, but no Kotlin code injects `PermissionManager`.
- **Fix:** `refactor(ios): remove the PermissionManager nothing asks` removes
  the Swift class and its wiring.
- **Deferred (cross-slice):** delete
  `libraries/opson/src/commonMain/kotlin/com/opson/libraries/PermissionManager.kt`
  and the `Permission`, `PermissionResult` and `PermissionStatus` types beside
  it; nothing references them now (grep `Permission` under
  `libraries/opson/src/commonMain`).

## 14. The kill-switch panel promised a lockout nothing performs (fixed)

- **Where:** `apps/admin/.../KillSwitchPanel.kt:40` and `Support.kt:96`.
- **Rule:** §1 "Code that reads true".
- **Failure:** neither the app nor the server reads
  `upgrade.maintenanceMode`, `maintenanceMessage` or `minSupportedVersionCode`
  (`SERVER_OWNED_PATHS` in `ConfigManifestRegistryDriftTest`; grep finds no
  reader). The panel said "BLOCKING, all users are locked out" and the confirm
  sheet said raising the floor force-upgrades everyone. In an incident the
  operator would believe users were stopped when nothing had changed.
- **Fix:** `fix(admin): the kill-switch panel stops promising a lockout nothing
  performs`. The panel says no released app reads these yet, the warnings name
  the effect for the build that will, and `apps/admin/README.md` says the same.

## Not problems

- **The admin's editor matches `config-manifest-registry.json`.** The pinned
  kill switches use the registry's three `upgrade.*` paths and fall back to its
  `off/banner/blocking`; types and allowed values come from the uploaded
  manifest; the registry's 20 entries match the 20 `ConfiguredValue` classes,
  which `ConfigManifestRegistryDriftTest` holds.
- **Usage strings.** Kotlin and Swift reference AVFoundation, Speech, AVFAudio,
  HealthKit, LocalAuthentication, PhotosUI, UserNotifications and CoreMotion.
  Camera, microphone, speech, Face ID and Health share all have strings;
  PHPicker, notifications and the accelerometer need none.
- **Launch work in the shell.** `App.kt` and `OpsonApplication` resolve
  `autoInits`; whether each contributor is cheap and flag-gated is reviewed in
  its own slice. The shell adds telemetry init, the activity tracker and the
  shake listener (foreground only).
- **`ShakeDialogEntryPoint`'s "I felt that." fallback** matches the literals in
  `ShakeMessageProvider`, where all shake copy lives.
- **Apple sign-in threading.** `awaitCredential` is called from a ViewModel on
  Main, and `ASAuthorizationController` answers on Main.

## Product notes

- **0.1 has no remote kill switch or forced upgrade.** The flags, the admin
  panel and the server validation exist, but no client reads them, so a bad
  build or a server outage can't be met with a maintenance screen or an upgrade
  prompt. Sure; worth a client gate before the app has many users.
- **The splash plays 1.5 s on every iOS launch,** on top of the boot gate. Fairly
  sure a warm launch would feel faster without it.
- **The admin console could show who changed a flag last on the flag row**, not
  only in Audit. Low certainty it's needed with one operator.
