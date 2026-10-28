# Findings: platform-libraries (round two)

`libraries/foods/`, `health/`, `reminders/`, `scanner/`, `speech/`,
`widgets/`, `progressphotos/` and `telemetry/`. Each finding names the place,
the rule from [PRACTICES.md](../PRACTICES.md), what breaks, and its fate.
Findings 1 to 6 came from the cross-cutting audits; the rest are this
review's. The unslop-code scanner found no bug-class hits, only long
identifiers (mostly Apple API names) and one "narrating comment" in
`MicSession.kt:24` that is a why comment.

## 1. A barcode scan with Open Food Facts down waited about 40 s: fixed

- **Where:** `libraries/foods/impl/src/commonMain/kotlin/com/opson/libraries/foods/impl/FoodApi.kt:154`
  and `:163` (before the fix: `RetryPolicy.idempotent()` on `foods.barcode`
  and `foods.describe`). Audit: contract.
- **Rule:** §6 external calls have timeouts and bounded retries; the retry
  policy's own KDoc on retrying 5xx.
- **Failure:** the idempotent policy retries a 503 or a timeout three more
  times. With Open Food Facts and Nutritionix down, the server spends up to
  5 s on each before answering 503 `barcode_lookup_unavailable`, so the
  scanner waited four tries plus backoff before the cached food or the error
  showed. Every retry also cost the server's one IP another outside call and
  spent the 60/min `BARCODE_LOOKUP_LIMIT`. Describe runs on every typing
  pause, is up to 20 catalog searches, and re-sent on 503 `busy`.
- **Fix:** both use search's policy, renamed `WAITED_ON_RETRY` (one quick
  retry on a dropped connection, none on a response or a timeout), with its
  KDoc now covering all three calls.
  `HttpFoodApiSearchTest.aBarcodeLookupWithNoSourceInReachIsNotAskedAgain`,
  `aScanOnADroppedConnectionIsRetriedOnce` and `aBusyDescribeIsNotAskedAgain`.
  Commit: `fix(foods): a scan or a description waits for one answer, not four, when the server is behind`.

## 2. Signed out, a No numbers widget became a calorie card: fixed

- **Where:** `libraries/widgets/impl/src/commonMain/kotlin/com/opson/libraries/widgets/impl/WidgetRefresher.kt:46`
  (before the fix: gated on `SettingsRepository.isKnown` alone). Audit:
  journeys.
- **Rule:** §7 the Blind leak test covers every surface, widgets included;
  §10 log out.
- **Failure:** a sign-out or an expired session resets the settings to
  `Settings()` (All numbers), and `SettingsStore.isKnownFor` treats
  `SignedOut` as known. The refresher then published a snapshot built from
  the Full policy, and the home screen widget switched from meal circles to
  "0 kcal" and a range bar for someone who chose never to see calories.
- **Fix:** the refresher also follows `AuthRepository.observe()` (through a
  provider, as `SyncTriggers` does). It publishes only while the app is
  someone's (`isSomeones`: signed in, or an unconfirmed offline session for
  someone past onboarding) and their settings are known; a sign-out or an
  expired session clears the widget unconditionally, so a snapshot an
  earlier run wrote goes too. The offline case matters: Supabase access
  tokens last an hour by default, and opening the app offline after that settles
  `Unauthenticated(None)`, where gating on `Authenticated` alone would have
  frozen the widget while the person logged. `hasUserOnboarded` is cleared
  by the sign-out dump, so a relaunch after a sign-out is nobody's again.
  Not cleared at launch otherwise: with no session and nobody onboarded the
  widget is left as it is, as round one's finding 5 decided.
  `WidgetRefresherTest.aSignOut_clearsTheWidget_andDoesNotDrawTheDefaultSettings`,
  `anExpiredSession_clearsWhatAnEarlierRunPublished`,
  `noSessionAndNotOnboarded_leavesTheWidgetAsItIs`,
  `aSessionThatCannotRefreshOffline_stillPublishesForSomeoneOnboarded`,
  `signingInAgain_publishesOnceTheirSettingsAreKnown`. Commits:
  `fix(widgets): signed out, the widget is cleared instead of drawn from the default settings`
  and `fix(widgets): an offline session that can't refresh still moves the widget and its +`.

## 3. The widget's + opened the Logger for nobody: fixed

- **Where:** `libraries/widgets/impl/src/commonMain/kotlin/com/opson/libraries/widgets/impl/WidgetOpener.kt:59`,
  meeting `apps/compose/src/commonMain/kotlin/com/opson/App.kt:141`. Audit:
  journeys.
- **Rule:** §10 log out; §3 offline first (the outbox belongs to a user).
- **Failure:** the Android widget draws the + on the "Open Opson" card too
  (`OpsonWidget.kt:122`). Signed out, a tap reached `router.navigate(LoggerRoute)`
  with no auth check, the Logger opened over onboarding, and what was logged
  went into an outbox nobody owned. The next sign-in has no previous user, so
  no dump runs and those entries go up into that account.
- **Fix:** `WidgetOpener` waits for auth's first answer and asks for the
  Logger only when `isSomeones` holds; otherwise the tap just opens the app.
  App.kt needs no change for the widget: it only routes what `requests`
  emits. `WidgetSourceAndOpenerTest.theLogLink_signedOut_opensNothing`,
  `theLogLink_onAFreshInstall_opensNothing` and
  `theLogLink_onASessionThatCannotRefreshOffline_stillOpensTheLogger`.
  Commits: `fix(widgets): the widget's + opens the Logger only for someone signed in`
  and the offline commit in 2.

## 4. A tapped reminder routes to the Logger with no auth check: deferred (cross-slice)

- **Where:** `apps/compose/src/commonMain/kotlin/com/opson/App.kt:133`
  (`reminderOpens.requests.collect { router.navigate(LoggerRoute(...)) }`).
  Audit: journeys.
- **Rule:** §10 log out.
- **Failure:** smaller than the widget's: a sign-out the process sees
  replans with the default settings, which schedule nothing and dismiss
  every delivered reminder. The gap is a reminder delivered before an
  expiry the app has not seen yet, tapped on a cold start: `opened` sends the
  request before auth resolves.
- **Change for the apps-shells slice:** in that collector, skip the navigate
  unless the app is someone's, the same rule as `WidgetOpener`:
  `val auth = authRepository.current(); val onboarded = appComponent.appCache.get().hasUserOnboarded;`
  `if (auth is AuthState.Authenticated || (auth is AuthState.Unauthenticated && auth.reason == AuthState.Unauthenticated.Reason.None && onboarded)) router.navigate(...)`.
  (Or make `isSomeones` in `WidgetRefresher.kt` public and import it; apps
  may depend on impls.)

## 5. No leak-test case for a sign-out under No numbers: deferred (cross-slice)

- **Where:** `apps/leaktest`. Audit: journeys.
- **Rule:** §7 the Blind leak test covers every surface.
- **Failure:** the leak test renders `WidgetCard` from `WidgetSnapshotBuilder`
  per preset, which cannot see this bug: the builder was right, the
  refresher fed it the wrong settings.
- **Fate:** the behaviour is pinned in this slice by
  `WidgetRefresherTest.aSignOut_clearsTheWidget_andDoesNotDrawTheDefaultSettings`.
  If the orchestrator wants it in the leak test too: a case in the test-apps
  slice that builds `WidgetRefresher` with a No numbers snapshot source, flips
  a fake `AuthRepository` to `Unauthenticated(reason = SignedOut)`, and
  asserts no published snapshot carries a `WidgetDay.energy`.

## 6. `WidgetsEnabled.isOn` had no caller, and the strings note lied: fixed

- **Where:** `libraries/widgets/src/commonMain/kotlin/com/opson/libraries/widgets/WidgetsEnabled.kt:32`,
  `libraries/resources/src/commonMain/composeResources/values/strings_widgets.xml:4`.
  Audit: truth.
- **Rule:** §1 no dead code; stale comments are bugs.
- **Failure:** 8873f760 removed the refresher's flag gate and with it the
  only call. The strings note still said the widget copy was "0.2, behind
  release.widgets", though only the More row and the page are.
- **Fix:** companion deleted; the note says the More row and the Widgets page
  are behind the flag and the widget is in every build. Commit:
  `refactor(widgets): drop WidgetsEnabled.isOn and fix two notes that no longer held`.

## 7. The widget's goal said activity adds nothing: fixed

- **Where:** `libraries/widgets/src/commonMain/kotlin/com/opson/libraries/widgets/WidgetSnapshotBuilder.kt:76`.
- **Rule:** §1 stale comments are bugs.
- **Failure:** "A widget has no watch reading, so activity adds nothing",
  on the line that passes today's Burned to `rangeFor`, so a set deficit or
  surplus does aim at activity.
- **Fix:** the comment says so. Same commit as 6.

## 8. An iOS lock screen answer could leave its completion handler uncalled: fixed

- **Where:** `libraries/reminders/impl/src/iosMain/kotlin/com/opson/libraries/reminders/impl/IosReminderDelegate.kt:116`
  (before the fix: `appScope.launch { actions.alreadyAte(reminder); withCompletionHandler() }`,
  the same for snooze).
- **Rule:** §8 platform contracts; §2 errors at the edges.
- **Failure:** "Already ate" and "In 30 min" run in the background, and iOS
  keeps the app alive only until the handler is called. A throw from the
  work (a locked or full database on `setAteNoDetails`) went to the app
  scope's handler, which logs it, and the completion handler was never
  called. iOS answers that by cutting the app's background time. Android's
  receivers already wrap the same work in `Catching` and always `finish()`.
- **Fix:** `answerInBackground` (`BackgroundAnswer.kt`, new, in common code
  so it can be tested) runs the work in `Catching` and the handler in
  `finally`. `BackgroundAnswerTest`: after the work, when it fails, when it
  is cancelled. Commit:
  `fix(reminders): an iOS lock screen answer always calls its completion handler`.

## 9. The iOS progress photo camera was set up on the main thread: fixed

- **Where:** `libraries/scanner/impl/src/iosMain/kotlin/com/opson/libraries/scanner/impl/IosStillCamera.kt:134`
  (before the fix: discovery, `deviceInputWithDevice`, `addInput`, `addOutput`
  and the preset in `StillCaptureSession.init`, built by `remember(facing)`).
- **Rule:** §2 right dispatcher, never block main; §4 no work in composition.
- **Failure:** the session was constructed in composition, on main, and
  adding a camera input blocks until the camera answers. Opening the camera
  and every front/back flip held a frame for that long. The barcode camera
  already does all of it on `captureQueue` ("never on the main thread").
- **Fix:** `configure()` runs on `captureQueue` inside `start()`, between
  `beginConfiguration` and `commitConfiguration`, and `usable` is set on main
  once the session runs. iOS only, no AVFoundation test seam; checked by the
  iOS compile. Commit: `perf(scanner): the iOS still camera is set up off the main thread`.

## 10. iOS barcode reads could land after the scanner closed: fixed

- **Where:** `libraries/scanner/impl/src/iosMain/kotlin/com/opson/libraries/scanner/impl/IosBarcodeCamera.kt:181`.
- **Rule:** §1 match the neighbours (Android drops them: `if (!stopped) onCode(code)`).
- **Failure:** a read already dispatched to main when the screen closed
  reached the disposed composable's last `onCode`, so the view model could
  get a code after it had moved on.
- **Fix:** the delegate's callback checks `stopped` on main. Same commit as 9.

## 11. A `!!` in the reminder coordinator: fixed

- **Where:** `libraries/reminders/impl/src/commonMain/kotlin/com/opson/libraries/reminders/impl/ReminderCoordinator.kt:105`
  (`.filter { it != null }.map { it!! }`).
- **Rule:** §2 no `!!`.
- **Fix:** `filterNotNull()`. Commit: `refactor(reminders): known settings filter nulls without a !!`.

## 12. The reminder receivers' own coroutine scope: not a problem

`ReminderReceivers.kt:21` is a `CoroutineScope(SupervisorJob() + Dispatchers.Default)`
nothing cancels, which §2 forbids in general. Here it is the `goAsync`
pattern: every launch is bounded by `withTimeoutOrNull(8.seconds)` and ends
in `result.finish()`, and a receiver runs before, or without, the app's
graph and its `AppCoroutineScope`.

## 13. Android re-arms every alarm on each replan: not a problem

`AndroidLocalNotifications.replaceAll` sets all of the plan's alarms (about
42) on every foreground and every logged meal. That is deliberate and
documented: `AlarmManager` cannot list what it holds, alarms vanish on a
reboot or update, and re-setting the same `PendingIntent` is idempotent. The
cost is a few dozen binder calls on `Dispatchers.Default`.

## 14. Telemetry's environment follows the build switch: not a problem

Checked against §9. `GrafanaLogTree` tags `deployment.environment` from
`BuildInfo.isDebug`, the same switch `SupabaseInfo` and the API server use,
and adds `release_channel` and `build_number`. Missing Grafana credentials
leave the tree unplanted with an info log at boot. Plain Warn+ logs forward
the exception type, never its message (which can carry a URL's query). The
one launch-time cost, the SDK and install facts built on main by the first
event, already carries a `TODO(review)` (finding 18).

## 15. Progress photos: not a problem

The index is never remembered as empty after a read that failed (iOS file
protection while locked), images are written before the index entry that
names them and deleted after it, Android uses `noBackupFilesDir` and iOS
excludes the folder from backup with complete protection, and
`ProgressPhotosClearer` deletes everything on any user change. Nothing runs
at launch.

## 16. Round one's fixes still hold: not a problem

Checked each: the photo estimate's 70 s socket timeout and its test (1), one
serial `captureQueue` for every session (2), iOS requests built on main (3),
the widget's range from `rangeFor` (4), the clear on a user change, now one
branch of finding 2 (5), and the capture file deleted in `finally` (6).

## 17. Two copies of the iOS shrink-and-encode helpers: deferred

Round one's finding 12, still true: `IosStillCamera.kt` (`redraw`, `toBytes`)
and `IosMealPhotoPicker.kt` (`shrink`, `toByteArray`). Cosmetic; worth one
shared helper the next time either changes.

## 18. The Grafana SDK is built on main by the first event: deferred

`GrafanaLogTree.kt:59` already has the `TODO(review)`: on a cold start the
first forward is `app.launched` from the foreground callback, on main, and it
builds the SDK (install facts' binder calls and file stats, the buffer
directory). Left until a StrictMode run says it is worth moving.

## Product notes

- **Signing out deletes progress photos for good.** They exist only on the
  phone, and `ProgressPhotosClearer` runs on every user change, sign-out
  included. Someone who signs out to switch accounts and back loses them.
  Worth checking the sign-out confirmation says so, or keeping photos across
  a sign-out back into the same account. Fairly sure this will surprise
  people.
- **A failed scan now fails fast.** With outside barcode sources down, the
  scanner shows its error after the server's first answer instead of its
  fourth. The copy could offer "Search instead" more prominently there, since
  "Try again" is likely to fail the same way for a while. Medium sure.
- **The widget's tomorrow uses today's Burned.** The snapshot's second day,
  shown after midnight with the app closed, aims a set deficit at today's
  burn. Small and self-correcting on the next open. Low stakes.
