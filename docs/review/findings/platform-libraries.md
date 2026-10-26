# Findings: platform-libraries

`libraries/foods/`, `health/`, `reminders/`, `scanner/`, `speech/` and
`widgets/`. Each finding names the place, the rule from
[PRACTICES.md](../PRACTICES.md), what breaks, and its fate. The unslop-code
scanner found only long identifiers, almost all of them Apple API names.

## 1. A photo estimate slower than 30 s failed on the phone and was paid for anyway — fixed

- **Where:** `libraries/foods/impl/src/commonMain/kotlin/com/opson/libraries/foods/impl/HttpMealPhotoEstimator.kt:46`
  (before the fix: `timeout { requestTimeoutMillis = TIMEOUT_MS }`).
- **Rule:** §6 external calls have deliberate timeouts; §3 error states.
- **Failure:** the app's client sets `socketTimeoutMillis` to 30 s
  (`NetworkClientImpl.kt:153`). The estimator raised only the request timeout
  to 70 s, and the server sends no bytes until the vision model answers, so any
  estimate past 30 s ended as `Failed` on the phone while the server finished
  the paid model call. "Try again" paid twice.
- **Fix:** the socket timeout is raised with it (`:48`).
  `HttpMealPhotoEstimatorTest.theWaitForTheModelOutlastsTheClientsDefaultTimeouts`
  reads the request's timeout capability under a client configured like the app's.
  Commit: `fix(foods): a photo estimate waits for the model past the 30 s socket timeout`.

## 2. iOS camera sessions could be left running after the screen closed — fixed

- **Where:** `libraries/scanner/impl/src/iosMain/kotlin/com/opson/libraries/scanner/impl/IosBarcodeCamera.kt:144`
  and `:162`, `IosStillCamera.kt:169` and `:205` (before the fix: `start` and
  `stop` each on `dispatch_get_global_queue`).
- **Rule:** §8 platform calls on the right thread; §2 don't parallelise work
  that must order.
- **Failure:** the global queue is concurrent. Close the scanner, or flip the
  progress-photo camera, within the second `startRunning` blocks: the stop runs
  alongside, sees `running == false`, does nothing, and the start then leaves
  the camera on with no screen showing it (the green camera dot, battery).
- **Fix:** one serial `captureQueue` (`IosBarcodeCamera.kt:207`) for every
  session's start and stop, so a stop always follows its start. iOS only, with
  no test target for AVFoundation; checked by the iOS compile. Commit:
  `fix(scanner): iOS camera sessions start and stop in order`.

## 3. iOS reminder requests were built off the main thread — fixed

- **Where:** `libraries/reminders/impl/src/iosMain/kotlin/com/opson/libraries/reminders/impl/IosLocalNotifications.kt:123`
  (before the fix: `schedule` built `UNMutableNotificationContent`,
  `NSDateComponents`, the trigger and the request on the caller's thread).
- **Rule:** §8; "Learned in wave 1" (build UserNotifications objects on main).
- **Failure:** `ReminderCoordinator` runs in `AppCoroutineScope`, on
  `Dispatchers.Default`. Commit `9937632` found `UNNotificationAction`
  segfaulting when built on that worker at launch; the notification requests
  are the same family of objects, built on the same worker, every replan once
  `release.reminders` is on.
- **Fix:** `schedule` hops to `Dispatchers.Main` for building and adding the
  requests. Reads and removals stay where they are: those calls are documented
  as safe from any thread. Checked by the iOS compile. Commit:
  `fix(reminders): iOS notification requests are built on the main thread`.

## 4. The widget's range box ignored a range that differs by day — fixed

- **Where:** `libraries/widgets/src/commonMain/kotlin/com/opson/libraries/widgets/WidgetSnapshotBuilder.kt:74`
  (before the fix: `settings.targets.today(settings.exercise, 0, 0)`).
- **Rule:** correctness; §1 (`Settings.exercise` is documented "never read").
- **Failure:** with "Different by day" set, the widget drew the base range on
  every day, and it read the 0.1 `exercise` key to do it.
- **Fix:** it asks `rangeFor` for the day's goal.
  `WidgetSnapshotBuilderTest.aRangeThatDiffersByDay_drawsThatDaysRange`.
  Commit: `fix(widgets): the range box follows a range that differs by day`.
  Today has the same gap; see domain-diary-nutrition #2.

## 5. A user change left the last account's day on the home screen — fixed

- **Where:** `libraries/widgets/impl/src/commonMain/kotlin/com/opson/libraries/widgets/impl/WidgetRefresher.kt:55`.
- **Rule:** checklist "Visibility" (the leak test covers widgets); §5 privacy.
- **Failure:** `RealWidgetSnapshotSource` emits nothing while settings are not
  known, which after a sign-out and sign-in lasts until the new account's first
  pull. The widget kept the previous account's day, in the previous account's
  mode: their calories, on a phone now signed in as someone who may be Blind.
- **Fix:** with the flag on, the refresher clears what it published while
  `isKnown` is false and publishes again once it is true. Nothing changes with
  the flag off. `WidgetRefresherTest.aUserChange_clearsTheLastAccountsDay_untilTheNextAccountsSettingsArrive`
  and `settingsNotKnownYetAtLaunch_publishNothing_andClearNothing`. Commit:
  `fix(widgets): a user change takes the last account's day off the home screen`.

## 6. A camera photo could stay in the cache after the sheet closed — fixed

- **Where:** `libraries/scanner/impl/src/androidMain/kotlin/com/opson/libraries/scanner/impl/AndroidMealPhotoPicker.kt:66`.
- **Rule:** §5 privacy (the class promises "no photo outlives the estimate").
- **Failure:** the capture file was deleted after the shrink returned, inside a
  composition scope. Closing the sheet mid-shrink cancelled the scope first, and
  the meal photo sat in `cache/meal_photo/` until the next capture.
- **Fix:** deleted in a `finally` (`:74`). An activity-result launcher in
  composition has no unit seam; compiled for Android. Commit:
  `fix(scanner): a camera photo is deleted even when the sheet closes mid-shrink`.

## 7. Reminders touch the notification center at launch with the flag off — not a problem

`ReminderCoordinator` is `AutoInit` and, with `release.reminders` off, still
reads pending and delivered requests and clears its own. That is how a remote
flip to off cancels what an earlier run scheduled, and
`ReminderCoordinatorTest.withTheFlagOffNothingIsScheduled_andWhatWasShowingGoes`
pins it. It builds no UserNotifications object and registers no category
(`IosReminderDelegate` waits for the flag); the delegate itself is set at launch
because a tap that cold-starts the app is lost otherwise.

## 8. Health — not a problem

- HealthKit's `requestAuthorization` is reached only from
  `IntegrationsViewModel` on `viewModelScope`, the main thread; queries resume
  from HealthKit's queue into a coroutine and touch no UI.
- No value is logged: failures log the step, the source key and the exception;
  telemetry carries the source only. A failed read is null, never zero.
- Health Connect's permission request runs on main and unregisters its
  launcher on result and on cancel.

## 9. Speech — not a problem

Every audio session, engine and recognizer call of a session runs on one serial
queue (`OneSessionAtATime`), never main, and the engine's input node is read
only after the session is active with an input. `availability()` reads two
`AVAudioSession` properties on the caller's thread; those are not what aborted
(the input node was). Transcripts are never logged: the steps log names, codes
and counts.

## 10. Midnight emitters wait with one long delay — not a problem

`ReminderCoordinator.midnights()` and `RealWidgetSnapshotSource.midnights()`
sleep straight to midnight, which a sleeping phone does not count, unlike the
one-minute steps in `LivePolicy`. Both also re-read today on every foreground,
the reminder plan runs seven days ahead, and a widget picks its own day from
the snapshot, so a late wake only delays a background replan. Left as is.

## 11. Foods clients — not a problem

Every read retries with the idempotent policy and logs no argument; the photo
estimate never retries (a paid call); a barcode 404 is a definite miss and
only a definite miss is reported; the miss id is made outside the retry so a
replay is the same row; an offline search falls back to foods picked before.

## 12. Two copies of the iOS shrink-and-encode helpers — deferred

`IosStillCamera.kt` (`redraw`, `toBytes`) and `IosMealPhotoPicker.kt`
(`shrink`, `toByteArray`) draw a `UIImage` upright at a size and copy the JPEG
out the same way. Cosmetic; worth one shared helper the next time either changes.

## Product notes

- **The widget's home screen copy outlives the release flag between runs.** If
  `release.widgets` is turned off remotely while the app is closed, nothing
  clears the snapshot until a run sees the flag go from on to off, so the widget
  can keep a stale day (with its numbers) indefinitely. Clearing at launch when
  the flag is off would touch the platform at launch, which the flag rule
  forbids; persisting "published" would fix it. Fairly sure it matters only if
  widgets are ever rolled back.
- **Photo estimate cost.** Before finding 1, slow estimates failed at 30 s and
  were retried by hand, each a paid model call. Worth checking the photo route's
  latency and spend in Grafana since 0.2 builds went out. Sure about the cause,
  not about how often it happened.
