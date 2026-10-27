# Findings: trends (`features/trends/`)

Every file in the slice was read, and the unslop-code scanner run over it. Each finding names
the place, the rule from [PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

## 1. The intake chart drew calories that had not opened yet: fixed

- **Where:** `TrendsState.kt` `intake()` and `nutrients()`, now gated by `opens()` at `:323`.
- **Rule:** checklist "No nutrition number is formatted outside `VisibilityPolicy`"; the
  visibility rule this app exists for.
- **Failure:** the week view is open in Delayed all day (`isRevealed` returns true for
  `Surface.Week`), so the chart drew today's bar at noon, and its height against the band says
  roughly what today came to before the 9 pm reveal. On Month and 3 months with Coast and a
  Saturday or Sunday week start, the week window ends before today, so no check is pending and
  the last bar (and the macro averages) took in days of a week whose check hadn't come.
  `BlindModeLeakTest` scans text, so a bar height never trips it. Patterns already handled the
  Delayed case and its comment said "Trends and Nutrients leave today out the same way"; Trends
  only left it out of the average.
- **Fix:** each day's calories (and each delayed macro) count only once that day has opened:
  `Surface.Today` with the day's own context for a daily reveal, the check of the week holding
  it for a weekly one. `fix(trends): the intake chart leaves out days whose calories have not opened`,
  with `TrendsStateBuilderTest.delayed_beforeTheReveal_todaysBarIsAGap_untilTheRevealOpensIt`
  and `coast_aMonthReachingIntoAWeekBeforeItsCheck_leavesThatWeekOut` (both fail without it).

## 2. Patterns' meal sizes opened on the wrong day for Coast: fixed

- **Where:** `PatternsState.kt:102`.
- **Rule:** as 1.
- **Failure:** the sizes card asked whether a week ending *today* had opened. With a Saturday
  week start that is true on Sunday evening, six days before the check of the week holding
  today, so the per-meal energy averages took in that week's two days.
- **Fix:** gate on the end of the user's week. `fix(trends): Patterns' meal sizes wait for Coast's check on the user's week`,
  with `PatternsStateBuilderTest.coast_meanSizesWaitForTheCheckOfTheWeekHoldingToday`.

## 3. Five pages carried a loading flag no screen read: fixed

- **Where:** `TrendsScreen.kt`, `PatternsScreen.kt`, `NutrientsScreen.kt`, `WeightScreen.kt`,
  `photos/ProgressPhotosScreen.kt`.
- **Rule:** brief "a blank screen while something loads is a bug".
- **Failure:** each state starts `loading = true` and each builder sets it false, but nothing
  drew it, so the Trends tab (three months of days from Room) and the rest showed a header over
  nothing. Weight was worse than blank: its log card drew with the default unit (kg), and
  `SaveTapped` reads `state.unit`, so a weight typed before settings arrived would be stored as
  kilograms whatever the user's unit.
- **Fix:** `DelayedLoadingIndicator` under the header while loading.
  `fix(trends): every page shows a spinner while it loads`.

## 4. Photos decoded on the main thread inside composition: fixed

- **Where:** `photos/PhotoParts.kt:95` (before: `remember(image) { … decodeToImageBitmap() }`).
- **Rule:** §4 "No work in composition"; §2 "Never block main".
- **Failure:** `PhotoImage` draws the grid's thumbnails, Compare's pair and the full photo. A
  full camera JPEG takes tens to hundreds of milliseconds to decode, and the grid decoded every
  thumbnail in the frame that unlocked it.
- **Fix:** `produceState` decoding on `Dispatchers.Default`, stripes until it lands; previews
  decode at once because a screenshot doesn't wait. `perf(trends): progress photos decode off the main thread`.

## 5. The photo grid waited for every thumbnail, and so did every tap: fixed

- **Where:** `photos/ProgressPhotosViewModel.kt:98`.
- **Rule:** §2 "Parallelise independent work".
- **Failure:** thumbnails were read one by one inside the action that brought the photo list,
  and the state was rebuilt only after the last. `SEAViewModel` runs actions one at a time, so
  the grid stayed empty and Back did nothing until the disk was done.
- **Fix:** rebuild first (dates and poses are already known), then read the missing thumbnails
  together in a job beside the page, which reports back as `ThumbnailsRead`; a read that lands
  after the lock came down is dropped. `perf(trends): the photo grid draws before its thumbnails are read`,
  with `ProgressPhotosViewModelTest.theGridShowsBeforeTheThumbnailsAreRead_andBackStillWorksMeanwhile`.

## 6. A weigh-in that failed to save said nothing: fixed

- **Where:** `WeightViewModel.kt:94`.
- **Rule:** §2 "Errors at the edges: expected failures are values … handled at the ViewModel".
- **Failure:** a failed `addWeighIn` (a full disk, as the shared machine had during this review)
  was logged and the handler returned, so the number sat in the field with no word that it
  hadn't been kept.
- **Fix:** `saveFailed` in state and a polite live-region line under the log card, cleared on
  the next keystroke. `fix(trends): a weigh-in that fails to save says so`, with
  `WeightViewModelTest.saveTapped_aFailedWrite_keepsTheWeight_andSaysSo_untilTheyTypeAgain`.

## 7. Dead string and a stale comment: fixed

- **Where:** `strings.xml` `trends_placeholder` (used nowhere); `TrendsSettingsViewModel.kt:15`
  said Expended waits on 0.2 "(no watch data)", which 0.2 has.
- **Rule:** §1 "No dead code", "Stale comments are bugs".
- **Fix:** `refactor(trends): drop the unused trends_placeholder string`,
  `docs(trends): the cards comment no longer says Expended waits on 0.2`.

## 8. Design sync 2: the Expended card: deferred (cross-slice)

- **Where:** `TrendsScreen.kt:136` (`TODO(design-sync-2)`).
- **Why deferred:** the card is a daily average across the range. `HealthRepository.energy(day)`
  (`libraries/health`) reads one day and polls it; a range needs a new
  `suspend fun energyBetween(from: LocalDate, to: LocalDate, timeZone): Map<LocalDate, EnergyExpended>`
  on `HealthRepository` and both readers, plus a `TrendsCards.expended` settings field
  (`libraries/diary`). Then the card is `policy.resolve(Metric.EnergyOut, …, Surface.Week)`
  behind `release.health`.

## 9. Design sync 2: hunger from the check-ins on Patterns: deferred (cross-slice)

- **Where:** `PatternsState.kt` `hunger`.
- **Why deferred:** the card reads per-meal hunger tags through `MealPatterns`
  (`libraries/diary`); the design's "From your check-ins" reads `Day.checkIn.hunger`, one per
  day. Its flag `CheckInEnabled` sits in `features/today/impl`, which this module can't depend on.
  Exact change: move `CheckInEnabled` to `libraries/diary`; add
  `MealPatternFacts.checkInHungerByWeekday` (average `DayHunger.ordinal / 4` per weekday over
  days with a check-in) to `MealPatterns.of`; then `PatternsStateBuilder` uses it when the flag
  is on and the tags otherwise, with a "From your check-ins" caption string.

## 10. Design sync 2: the photos visibility switch: deferred

- **Where:** `WeightState.kt:53` (`TODO(design-sync-2)`). Waits on settings finding 8; until
  then the preset default stands, which is what the design defaults to.

## Not a problem

- **Bars over 100%.** `IntakeBarChart` clamps each fraction to `0..1`, and the nutrient fill bars
  stop at full.
- **Error states.** Every Trends read is local (Room, the photo index); there is no network
  call to fail. The writes that can fail now say so (finding 6; Capture already had `failed`).
- **Date formatting in composables** (`DateCopy` on Weekly check, Patterns' facts, photo tags).
  O(1) string building on cards that recompose only when their state changes; moving it to the
  builders would put formatted strings in state for no gain.
- **Compare and the single photo read their image inside an action.** One or two reads, on a
  page with nothing else to do meanwhile.
- **Stable parameters.** State classes are immutable `data class`es and strong skipping is on;
  no `LaunchedEffect` is keyed on a changing object, and no animated value is read in
  composition (detekt passes).
- **Scanner hits.** All 28 are `collectAsStateWithLifecycle` and factory parameter names.

## Product notes

- **Delayed users lose today's bar until the reveal** (finding 1). The caption still says today
  "is striped because it's still in progress", over what is now a gap before 9 pm. A caption
  saying today's bar opens at the reveal time would read better. Fairly sure.
- **Coast with a Saturday or Sunday week start reads oddly.** The Week view shows the last
  closed week while Month shows the current, partly closed one. Worth deciding whether Coast
  should force a Monday week. Not sure.
