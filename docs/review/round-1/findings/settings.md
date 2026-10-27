# Findings: settings (`features/settings/`)

Every file in the slice was read, and the unslop-code scanner run over it. Each finding names
the place, the rule from [PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

## 1. Settings carried its own copy of the shared time picker: fixed

- **Where:** `features/settings/impl/.../TimeDialog.kt` (deleted), used by `modes/ModesScreen.kt`
  and `reminders/RemindersScreen.kt`.
- **Rule:** §1 "No dead code", "Match the neighbours"; checklist "Components come from `:libraries:ui`".
- **Failure:** `TimeDialog` was line for line `:libraries:ui`'s `TimePickerDialog`, apart from
  naming the same 28 dp corner shape itself (`SettingsDefaults.DialogShape`). A fix to one
  would have missed the other. Behaviour matched exactly, so both pages now use the shared
  dialog, and Support's delete dialog uses `AppTheme.shapes.dialog`.
- **Fix:** `refactor(settings): use the shared TimePickerDialog and dialog shape`.
- **Deferred (cross-slice), docs that still name the old dialog:**
  - `libraries/ui/.../components/dialog/TimePickerDialog.kt:31-33`: replace the KDoc's last
    sentence with "The amount sheet's Time, Modes' reveal time and Reminders' meal times use it."
  - `docs/plans/0.2/README.md:79`: `TimeDialog` becomes `TimePickerDialog`.
  - `docs/plans/screens/reminders.md:91`: "the shared settings `TimeDialog`, extracted from
    Modes" becomes "`:libraries:ui`'s `TimePickerDialog`".
  - `docs/plans/screens/logger.md:45`: drop "A public copy of Settings' internal `TimeDialog`;
    Settings can move to it later."

## 2. More asked the health store for its status with `release.health` off: fixed

- **Where:** `more/MoreViewModel.kt:60` (before: `MoreAction.ScreenResumed -> health.refresh()`).
- **Rule:** §3 "Nothing runs … that the user hasn't turned on (release flags)"; 0.2 brief
  "Nothing new runs while its flag is off".
- **Failure:** the init path honoured the flag, but every resume of More (the entry point's
  `LifecycleResumeEffect`, first open included) called `HealthRepository.refresh`, which asks
  Health Connect or HealthKit for availability and access. The existing test only checked init.
- **Fix:** `fix(settings): More leaves the health store alone with release.health off`, with
  `MoreTest.healthFlagOff_comingBackToMore_stillNeverTouchesTheStore`.

## 3. A cancelled export was caught as a failed one: fixed

- **Where:** `support/SupportViewModel.kt:114` (before: `mapCatching { dataExport.csv().getOrThrow() }`).
- **Rule:** §2 "Cancellation is cooperative and never swallowed".
- **Failure:** `mapCatching` wraps a suspending HTTP call, so leaving the page mid-export turned
  the `CancellationException` into `ExportStatus.Failed` ("Export needs a connection") and the
  handler carried on. Harmless today because the scope is dying anyway, but it is the pattern
  the rubric bans. Now `flatMap` from `:libraries:core`, which catches nothing.
- **Fix:** `fix(settings): a cancelled export is not caught as an offline one`, with
  `SupportViewModelTest.aCancelledExport_isNotReportedAsOffline`.

## 4. Fasting and Watch and health apps drew a bare header while loading: fixed

- **Where:** `fasting/FastingScreen.kt:72` (`null -> Unit`), `integrations/IntegrationsScreen.kt:85`
  (`if (state.loading) return@SettingsPage`).
- **Rule:** brief "a blank screen while something loads is a bug".
- **Failure:** Integrations waits on the health store's first status check; until then the
  page was empty under its header. Fasting waits on settings, the latest fast and the minute
  ticker. Both now show `DelayedLoadingIndicator` (empty for 500 ms, so a quick answer doesn't
  flash a spinner).
- **Fix:** `fix(settings): Fasting and Watch and health apps show a spinner while loading`.
  UI-only; the `loading` flags themselves are covered by the existing builder tests.

## 5. Targets measured both range amounts on every recomposition: fixed

- **Where:** `targets/TargetsScreen.kt:202`.
- **Rule:** §4 "No work in composition", "`remember(keys)` for expensive calculations".
- **Failure:** `EndSteppers` ran two `TextMeasurer.measure` calls in the composable body, so
  every stepper tap, macro toggle or settings emission re-measured. Now keyed on the two
  amounts, the type style and the measurer.
- **Fix:** `perf(settings): Targets measures the range amounts only when they change`.

## 6. Dead copy, imports and stale comments: fixed

- **Where:** `support/SupportScreen.kt` imported `SettingsCard`, `SettingsRow` and five strings
  (trusted person, clinician, "Coming later") that no code drew; `strings_settings.xml` still
  held them. `SettingsRoutes.kt:29` said Support holds "the sharing that is coming later";
  `MoreRoute.kt` listed five of More's nine rows; `impl/build.gradle.kts` declared
  `libraries.diary.ui` twice.
- **Rule:** §1 "No dead code", "Stale comments are bugs".
- **Fix:** `refactor(settings): drop Support's unused imports and the copy for rows it no longer shows`,
  `refactor(settings): drop a duplicate dependency and a stale route comment`.

## 7. Two quick taps with the pause on stack two pause sheets: deferred (cross-slice)

- **Where:** `SettingsFeatureEntryPoint.kt:143` (`TODO(review)`), the same in Water settings,
  and every other caller that opens `PauseBeforeMoreRoute` (Today, Trends' Nutrients).
- **Rule:** PRACTICES preamble "a real maybe (a race that needs two taps in 50 ms) is still
  worth fixing".
- **Failure:** with "Pause before showing more" on, `NumbersChanger.request` writes nothing and
  returns `NeedsPause`, so a second tap on the same cell before the sheet covers it passes the
  view model's "already at this level" check and sends a second `OpenPause`. `DelegatingRouter`
  does not dedupe, so two sheets stack; the second runs its own ten seconds, and dismissing it
  logs a `pauseKept` for a change the user already made.
- **Why deferred:** the same double push exists for every navigating tap in the app, so it
  belongs in the router, and proving it needs a navigation test there. Exact change: in
  `libraries/navigation/impl/.../DelegatingRouter.kt`, drop a `navigate(route)` whose route
  equals the current back stack entry's route (same class and arguments), or pass
  `NavigationOptions(launchSingleTop = true)` from these callers.

## 8. Design sync 2: the Photos Show / Hide row in Modes and numbers: fixed (was deferred)

- **Where:** `modes/*`, and its reader `features/trends/.../WeightState.kt:53`.
- **Rule:** 0.2 brief "every new user-facing surface sits behind a release flag"; checklist
  visibility rules.
- **Why deferred:** not small. The design draws Progress photos as a matrix row with only Show
  and Hide, defaulting to Hide in Blind, Mindful, Rhythm and Fuel, and showing more has to go
  through the pause. That needs a new field on `NumbersSettings` (`libraries/nutrition`, synced as
  a new top-level settings key so 0.1 clients merge it), a `NumbersChange` variant that
  `SettingsNumbersChanger` (`libraries/diary`) treats as raising when it goes to Show, the row in
  `RadioMatrix` with two disabled cells (`libraries/ui`), and Trends'
  `ProgressPhotosVisibility` reading it, all behind `release.progressPhotos`.
- **Fix:** `feat(nutrition): a Progress photos choice in NumbersSettings, behind the pause`,
  `feat(ui): RadioMatrix rows can leave columns out`,
  `feat(settings): Progress photos row in Modes and numbers`,
  `feat(trends): Weight's photos row follows Modes' Show or Hide`.

## 9. Design sync 2: "Check in at day close" in Reminders: fixed (was deferred)

- **Where:** `reminders/*`.
- **Why deferred:** the switch needs a settings key that Today's day close reads
  (`libraries/diary/.../Settings.kt`, a new top-level `checkIn: Boolean = true`), and its release
  flag `CheckInEnabled` lives in `features/today/impl/.../TodayReleaseFlags.kt:46`, which a
  settings impl cannot depend on. Move `CheckInEnabled` to `libraries/diary` (beside
  `FastingRelease`), add the key, then the row here is a `SwitchCard` like Quiet mode.
- **Fix:** `refactor(diary): move CheckInEnabled to libraries/diary`,
  `feat(today): a checkIn setting that turns Close the day's check-in off`,
  `feat(settings): Check in at day close switch in Reminders`. The hunger and fullness questions
  row stays above it: the logger still asks them, so removing their switch would leave no way to
  stop it.

## Not a problem

- **The pause flow.** Blind to Full applies at once unless the pause is on, by design
  (`NumbersChanger` KDoc). With it on: the countdown runs ten one-second ticks in
  `viewModelScope`, "Show them" is disabled until zero and ignored before it, Keep, swipe, scrim
  and back all apply nothing, a swipe after Show or Keep changes nothing (`finished`), and Talk
  to someone opens Find support over the sheet without resetting it. `PauseViewModelTest`
  covers each, and `PauseBeforeMoreRouteTest` round-trips every `NumbersChange`.
- **Numbers on Modes, the pause sheet and More.** None render: Modes shows levels and a reveal
  clock time, the sheet names a metric or preset, More's rows carry no amount. All three are in
  `SettingsLeakSurfaces`, and the leak test passes. Targets and Integrations resolve every
  amount through the policy.
- **Water goal in millilitres in every mode.** A setting, not intake, as the plan says; the page
  is in the leak test.
- **`PauseBeforeMoreRoute.timeFor` uses `runCatching`.** A non-suspending parse with no
  cancellation to swallow, in an API module that doesn't depend on `:libraries:core`.
- **Scanner hits.** All 128 are long Material parameter names in the deleted `TimeDialog` and
  descriptive test names.
- **`List` parameters on screens.** Kotlin 2.3's compiler has strong skipping on, so unchanged
  list instances skip.

## Product notes

- **The deficit card never knows what you burn.** `TargetsViewModel` passes `burnKcal = null`
  because 0.1 had no source, and 0.2 has a health connection. With a store connected, the card could show the
  7-day average instead of saying it doesn't know. Fairly sure it surprises people who just
  connected their watch.
- **Watch and health apps reads "today" once.** Left open past midnight it keeps showing the
  previous day's energy until reopened. Low impact; mentioned in case the page becomes a
  dashboard people leave open.
- **Reminders and Look and feel both carry Tone and the hunger questions** depending on
  `release.reminders`. Once reminders ship, Look and feel loses them silently; worth a release
  note so nobody thinks the setting vanished. Fairly sure.
