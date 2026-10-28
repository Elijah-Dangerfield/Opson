# Findings: apps-shells, round two (`apps/admin`, `apps/compose`, `apps/ios`)

All 62 files in the ledger were read, with `Info.plist`, the three entitlements
files, the Android manifest, `project.pbxproj` and
`config-manifest-registry.json` beside them. The unslop-code scanner flagged
only long framework and test names and the admin's ✕ dismiss glyph (it has an
`aria-label`), none of them findings. Each rule is from
[PRACTICES.md](../PRACTICES.md).

Checks run green: `./gradlew detekt`, `:apps:compose:testDebugUnitTest`,
`:apps:admin:jsTest`, `:apps:compose:compileKotlinIosSimulatorArm64`, and an
`xcodebuild` Debug simulator build of `iosApp` with its widget extension.
Nothing here touches `iosApp.entitlements` or the Release build configuration.

**Runs at launch after this review:** less than before. The boot gate no
longer waits on `/v1/me` (finding 1). Nothing new runs at start.

Round one's fixes hold: the deep-link log scrub, Android's bar icons, shake
handling in the app scope, the admin's environment reset and kept drafts, and
the kill-switch wording. Round one's finding 2 (a cap on the profile wait) is
replaced by finding 1 here, which removes the wait.

## 1. Every cold start waited for `/v1/me` behind the boot screen (fixed)

- **Where:** `apps/compose/src/commonMain/kotlin/com/opson/AppViewModel.kt:157`
  (before): `withTimeoutOrNull(BootWaitTimeoutMillis) { profileRepository.observe().first() }`.
  Routed from the journeys audit.
- **Rule:** §3 "every launch-time path is cheap", §10 "a slow network".
- **Failure:** `ProfileRepositoryImpl` emits only after `GET /v1/me` answers or
  fails. On one bar of signal, an onboarded person sat on the loading screen
  for up to 8 s on every launch, for a value Today and the Logger never read
  (only Home's feedback and sign-in do). `ProfileRepositoryImpl` is `AutoInit`
  and resolves on its own either way.
- **Fix:** `fix(app): the boot gate waits for the config only, not the profile`.
  The gate waits on the offline-first config alone, which answers from its
  cached or bundled copy. `AppViewModelTest` checks a returning user opens on
  Today without a profile, and that a config load that never finishes is
  still capped. The KDocs in `AppViewModel`, `App.kt` and `BootLoadingScreen`
  say the same.

## 2. A shake before the profile resolved froze shake-to-report (fixed)

- **Where:** `apps/compose/src/commonMain/kotlin/com/opson/ShakeHandler.kt:72`
  (before): `val profile = profileRepository.current()`.
- **Rule:** §10 Lifecycle, "a slow network"; §3 "every launch-time path is
  cheap" (the shake listener starts with the app).
- **Failure:** `current()` is the profile flow's `first()`. While `/v1/me` was
  in flight, or after a resolve that threw (it emits nothing), the shake
  collector suspended there. Shakes arriving meanwhile were dropped (the
  detector does not buffer), so shake-to-report did nothing until the app went
  to the background, exactly when someone wants to report a problem.
- **Fix:** `fix(app): a shake opens the dialog even while the profile is
  unresolved`. The name, which only flavours the copy, gets 500 ms
  (`ShakeNameWaitMillis`). `ShakeHandlerTest` fails without it (checked by
  reverting).

## 3. The iOS widget drew the retired Pine theme before the first snapshot (fixed)

- **Where:** `apps/ios/OpsonWidgets/TodayWidgetView.swift:187`. Routed from the
  truth audit.
- **Rule:** §1 "Stale comments are bugs"; §10 "Fresh install".
- **Failure:** `WidgetColors.fallback` was Pine (teal accent on green-grey).
  It draws for the WidgetKit placeholder, a widget added from the gallery
  before the app has published, the whole of Welcome and sign-in on a fresh
  install, and after sign-out. The app is Slate with an orange accent.
- **Fix:** `fix(ios): the widget's fallback is Slate, not the retired Pine
  theme`. Light and dark Slate from `Slate.kt` (`track` is the theme's `line`,
  as `WidgetPalette` maps it); the sample snapshot reuses them; the previews
  say "No numbers" instead of Blind and gentle. New
  `apps/compose/src/androidUnitTest/.../WidgetFallbackColorsTest.kt` reads the
  Swift file and holds it against `WidgetPalette.colors`, and the test task
  declares the Swift file as an input so an edit to it reruns the test. It
  fails on the old Pine values (checked).

## 4. Two flag descriptions in the admin console were wrong (fixed; one part deferred cross-slice)

- **Where:** `apps/admin/config-manifest-registry.json:10` and `:15`. Routed
  from the truth audit (`:10`); `:15` found here.
- **Rule:** §1 "Stale comments are bugs".
- **Failure:** the console shows these to whoever flips a flag on prod.
  `release.reminders` named a "Rhythm reminder line" that no longer exists.
  `release.photo` said "0.2:" though the photo estimate stays out of 0.2
  (`bef16211`, `release-checklist.md`), so an operator rolling out 0.2 would
  think it belongs in the set to turn on.
- **Fix:** `fix(admin): the reminders and photo flag descriptions match what
  they gate`. Reminders now matches `RemindersEnabled`'s KDoc; photo says "Not
  in 0.2, keep off on prod". The text reaches the console with the next
  manifest upload.
- **Deferred (cross-slice, scripts-ops-ci):** `ops/grafana/onboarding-funnel.json:614`
  explains a rising p90 as "the guest-creation wait at the finish line", which
  no longer exists. Replace that sentence with: "A p90 that climbs while p50
  holds is usually the sign-in step (Google's browser round trip)."

## 5. Admin's Users tab said Loading forever on a failed search (fixed)

- **Where:** `apps/admin/src/jsMain/kotlin/com/opson/admin/UsersView.kt:83`
  (before), and the detail panel under it.
- **Rule:** §10 "a loading state and an error state"; round one's finding 12
  fixed the same in Waitlist and Audit.
- **Failure:** a first search that failed (a bad token, a server without
  `/v1/admin/users`) left "Loading…" for good. Picking an account showed
  nothing at all until its detail arrived, and nothing if it failed.
- **Fix:** `fix(admin): Users says when a search or an account fails to load`.
  `userListNote` picks loading, failed or no match, tested in `UsersViewTest`;
  the detail area says "Loading the account…" or that it failed.

## 6. Apple sign-in could lose a second flow's callback (fixed)

- **Where:** `apps/ios/iosApp/Platform/IOSAppleSignInCoordinator.swift:37`
  (before): `onFinish: { [weak self] in self?.delegate = nil }`.
- **Rule:** brief, "a real maybe is worth fixing if the fix is small".
- **Failure:** the coordinator retains one delegate. If a second request
  started before the first ended, the first ending cleared the second's
  delegate, ARC freed it (`ASAuthorizationController.delegate` is weak), and
  `awaitCredential` never resumed, leaving that sign-in waiting for good.
- **Fix:** `fix(ios): Apple sign-in releases only its own delegate`: a flow
  clears the delegate only if it is still its own. No test: the iOS app has no
  Swift test target. The Debug simulator build compiles it.

## 7. Dead code and stale comments in the shell (fixed)

- **Where:** `apps/compose/src/iosMain/kotlin/com/opson/SwiftUsageBridge.kt`
  (three duration helpers, no callers repo-wide);
  `IOSNativeViewFactory.swift:9` (an unused `JSONDecoder`, and `import
  SwiftUI` with nothing SwiftUI in the file); `AppComponent.kt:65` ("the
  user's palette", a 0.1 setting that is no longer read); `iOSApp.swift:39`
  (Universal Links, which need an Associated Domains entitlement the app
  doesn't have, and "calls navController.handleDeepLink", which skips the OAuth
  and widget routes); `App.kt:387-445` (a narrating comment on every line of
  the four transitions, with a "pope Exit" typo).
- **Rule:** §1 "No dead code", "Comments say why, never what", "Stale comments
  are bugs".
- **Fix:** `refactor(app): drop unused Swift helpers and fix stale shell
  comments`. The transitions keep one comment that states the rule; the code
  is unchanged.

## 8. The binary declared a background mode nothing uses (fixed)

- **Where:** `apps/ios/iosApp/Info.plist`: `UIBackgroundModes` = `processing`
  and `BGTaskSchedulerPermittedIdentifiers` = `com.opson.maintenance`, both
  from the template's first commit.
- **Rule:** §1 "No dead code"; §8 iOS.
- **Failure:** no Kotlin or Swift code registers or submits a `BGTask` (grep
  for `BGTaskScheduler`, `com.opson.maintenance`). A declared background mode
  with no use is something App Review asks about (guideline 2.5.4).
- **Fix:** `build(ios): drop the background processing mode nothing
  schedules`. Not an entitlement or capability, so signing is unaffected.

## 9. iOS Debug and Release share one bundle id and App Group (deferred)

- **Where:** `apps/ios/iosApp.xcodeproj/project.pbxproj:376` (Debug
  `PRODUCT_BUNDLE_IDENTIFIER = com.opson.Opson`, same as Release `:477`; the
  widget at `:503` and `:530`), `IOSWidgetBridge.swift:23` and
  `WidgetSnapshot.swift:87` (`group.com.opson.Opson` hardcoded). Routed from
  the release-path audit.
- **Rule:** §9 "One switch per build picks the environment … bundle and App
  Group ids".
- **Failure:** confirmed. A Debug build from Xcode (dev server, dev Supabase)
  installs over the TestFlight build in the same container: the Room
  database, outbox and widget snapshot hold the prod account's data while the
  app talks to dev, and reinstalling TestFlight brings back a dev-touched
  database. `docs/TESTING-ON-DEVICE.md` walks the developer into this on their
  own phone. Android keeps them apart with `.debug`.
- **Why deferred:** the fix needs two App IDs and an App Group registered in
  the developer portal before a Debug device build can sign, and the 0.2 rules
  hold back capability changes until they are registered. The change, for
  whoever registers them:
  - Debug `PRODUCT_BUNDLE_IDENTIFIER` = `com.opson.Opson.debug` (app) and
    `com.opson.Opson.debug.Widgets` (widget);
    `INFOPLIST_KEY_CFBundleDisplayName = "Opson Dev"` for Debug.
  - `iosAppDebug.entitlements` App Group `group.com.opson.Opson.debug`, and a
    new `OpsonWidgets/OpsonWidgetsDebug.entitlements` with the same group,
    set as the widget's Debug `CODE_SIGN_ENTITLEMENTS`.
  - A per-configuration `APP_GROUP_ID` build setting, an `AppGroupID =
    $(APP_GROUP_ID)` key in both Info.plists, and `IOSWidgetBridge.appGroup`
    and `WidgetStore.appGroup` reading it from `Bundle.main`.
  - The Keychain service needs nothing: items are per bundle id already.
  - Register `com.opson.Opson.debug`, `com.opson.Opson.debug.Widgets` and
    `group.com.opson.Opson.debug` in the portal, and add the step to
    `docs/plans/0.2/release-checklist.md`.

## 10. Android debug and release both claim `opson://` (deferred, cross-slice)

- **Where:** `apps/compose/src/androidMain/AndroidManifest.xml:53`
  (`android:scheme="opson"`), `MainActivity.kt:185` (`APP_SCHEME =
  "opson://"`), with `SupabaseClientFactory.OAUTH_REDIRECT_SCHEME = "opson"`.
  Routed from the release-path audit.
- **Rule:** §9 "… deep link scheme and redirect URLs".
- **Failure:** confirmed. `com.opson` and `com.opson.debug` both register the
  scheme. With both installed, Google's browser sign-in returns to
  `opson://login-callback` and Android shows a chooser; picking the other app
  hands the PKCE code to a client without its verifier, and sign-in fails. A
  verify-email link can do the same.
- **Why deferred:** the scheme has to change in five places at once or debug
  sign-in breaks outright, and four are outside this slice, one outside the
  repo. The change:
  - `build-logic/.../ApplicationConventionPlugin.kt` (build-config):
    `manifestPlaceholders["authScheme"] = "opson"` for release and
    `"opson-debug"` for debug, and a matching `BuildConfig`/`BuildInfo` value.
  - `libraries/identity/impl/.../SupabaseClientFactory.kt:112` (identity):
    `OAUTH_REDIRECT_SCHEME = if (BuildInfo.isDebug) "opson-debug" else "opson"`.
  - `features/onboarding/impl/.../OnboardingFeatureEntryPoint.kt:127`: the
    `auth/confirmed` deep link on both schemes (or on the build's own).
  - `libraries/widgets` (`WidgetLinks.LOG`) on the build's scheme.
  - Then here: `android:scheme="${authScheme}"` in the manifest and
    `MainActivity.APP_SCHEME` from the same value.
  - The dev Supabase project's redirect allow list:
    `opson-debug://login-callback` and `opson-debug://auth/confirmed`.
  - iOS follows once Debug has its own bundle id (finding 9).

## 11. iOS status bar under a forced theme (still deferred from round one)

- **Where:** `apps/compose/src/iosMain/kotlin/com/opson/MainViewController.kt:9`,
  `TODO(review)`.
- **Why still deferred:** unchanged from round one (finding 5 there): the fix
  sets the window's `overrideUserInterfaceStyle` from Settings.theme, which
  also restyles system sheets, and wants checking in the simulator.

## Not problems

- **Admin reload racing a reconnect.** `reloadAll` writes its result without
  checking the connection it started on is still the current one. But Connect
  first makes its own `listFlags` round trip to the new environment before it
  clears and loads, so the old reload, sent earlier, lands first in practice.
  One operator; not worth a guard without a DOM test harness.
- **`Logger+MessageScope.swift` wraps every log level** though Swift calls only
  `w` and `e`. It mirrors the Kotlin `Logger`; trimming half the levels
  would surprise the next caller.
- **The Apple nonce charset has no `W`.** It is the Firebase sample's: 63
  characters, still a uniform pick over the bytes it accepts. Harmless.
- **Keychain and reinstall.** The Keychain outlives a delete; `FreshInstall`
  in `:libraries:identity:impl` tells a reinstall from a relaunch so
  `SecureSessionManager` drops the old session (§10). Not the shell's job.
- **Usage strings.** Unchanged since round one, and present for every API the
  binary references, including `NSHealthUpdateUsageDescription`.
- **Scanner hits:** long names are Apple's and the framework's
  (`kSecAttrAccessibleAfterFirstUnlock`, `currentBackStackEntryAsState`) or
  match the repo's sentence-style test names.

## Second review

A second reviewer checked this round's fixes and flagged two problems in
finding 5's commit. Both were real.

1. **Picking a failed account again did nothing** (fixed). The new detail
   note said "Pick it again to retry", but the load was keyed on the selected
   id, and clicking the row already selected sets the same value, so the
   effect never ran again. The operator had to pick another account and come
   back. Fixed in `fix(admin): picking a failed account again retries its
   load`: each click is an `AccountPick` with a running count, so a repeat
   pick is a new key. `UsersViewTest` checks that a repeat pick differs from
   the first and pins the detail note's wording.
2. **A failed search after an empty one said "No account matches."**
   (fixed). `userListNote` checked for results before the failure, so a stale
   empty list from the previous query won. Fixed in `fix(admin): a failed
   search after an empty one says it failed`: the failure is checked first,
   with a `UsersViewTest` case for an empty list plus a failure.

## Product notes

- **A developer's phone can't hold TestFlight and a Debug build at once on
  either platform's sign-in** (findings 9 and 10). Until both are fixed,
  `TESTING-ON-DEVICE.md` should say to delete one before installing the
  other. Sure.
- **The widget gallery shows the widget before anyone has signed in**, saying
  "Open Opson to start." in Slate now. Fairly sure that's fine; a gallery
  preview with sample meal circles would sell it better than a blank card.
- **Shake-to-report greets people by name.** The name rarely matters and the
  greeting is the one thing that needs the network. Low certainty anyone would
  miss it if it went.
