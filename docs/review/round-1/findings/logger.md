# Findings: logger

`features/logger/` and `features/logger/impl/`, all 63 files. Each names the place, the rule
from [PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

## 1. A long unit name cut the grams off the unit button: fixed

- **Where:** `AmountSheetContent.kt`, `UnitMenu` (before the fix it drew
  `"$label · $grams"` in a one-line `Pill` with an ellipsis).
- **Rule:** §8 accessibility, "dynamic type that doesn't truncate"; the design's 3.3b
  note ("each unit shows its weight, so switching isn't a guess").
- **Failure:** a scanned or USDA portion such as "container (7 oz)" left about 126 dp for the
  pill beside the stepper, so it read "container (7 oz) · 2…": the weight, the one part the
  design says matters, was the part that went.
- **Fix:** "fix(logger): a long unit name wraps above its weight instead of cutting the grams
  off". A label that fits stays on one line as the design draws it ("plate · 350 g"); one that
  does not goes on two lines with the grams under it, in the same muted figure the unit menu
  uses. Checked on the `logger-scan-amount` render ("container (150 g)" over "150 g"). There is
  no composition test harness in this module, so the layout has no unit test.

## 2. The search list was blank, and Saved said "nothing saved", while the sheet loaded: fixed

- **Where:** `LoggerSheetContent.kt` `SearchPane` (`if (!state.loading) body(...)`, nothing in
  the else) and `LoggerPanes.kt` `SavedPane` (drew `logger_saved_empty` whenever the list was
  empty, loading or not).
- **Rule:** brief, "a blank screen while something loads is a bug".
- **Failure:** the inputs combine the day, the day before, settings, the basket, usual and
  recent foods, custom foods, saved meals and 14 days of history. Until all of them answer, the
  search body was empty, and a sheet opened on Saved (`LoggerStart.Saved`) told someone with
  saved meals "Saved meals show up here. Make one under Foods."
- **Fix:** "fix(logger): a spinner while the sheet's lists load, not a blank or a false empty".
  Both use `DelayedLoadingIndicator`, as search already does. UI only; no composition test.

## 3. A second tap while a sheet closed logged the food again: fixed

- **Where:** `AmountViewModel.addToMeal` and `PickAndKeepGoingTapped`,
  `PhotoEstimateViewModel.addToMeal`, `LoggerViewModel.copyYesterday`, `addNumber`,
  `ateNoDetails`, `commit`, `handBack`.
- **Rule:** §6 "Idempotent writes and retries", applied to the client; §7 every bug gets its test.
- **Failure:** `SEAViewModel` runs actions one after another, and the sheet takes a few hundred
  ms to animate down after the first write. A second tap on "Add to dinner" ran after the first
  had logged and cleared the basket: `pick()` put the food back and `commit` logged it again,
  then sent a second `Logged` (two more `goBack`s). The photo sheet did the same with its lines,
  Yesterday's copied the meal twice, Just a number added two Quick adds.
- **Fix:** "fix(logger): a second tap while a sheet closes no longer logs the food again". Each
  view model ignores writes once one has landed. `AmountTest.aSecondTapOnAddWhileTheSheetClosesLogsNothingMore`
  (proved failing with the guard removed), `PhotoEstimateViewModelTest.aSecondAddWhileTheSheetsCloseLogsNothingMore`,
  `LoggerViewModelTest.aSecondTapWhileTheSheetClosesWritesNothingMore` and
  `aSecondNumberWhileTheSheetClosesAddsNothingMore`.

## 4. "% of energy" on the amount sheet did not match Today and did not make 100: fixed

- **Where:** `AmountSheet.kt` `AmountStateBuilder.portion` divided each macro's energy by the
  label's kcal; `TodayStateBuilder.macrosPage` divides by the energy of the macros on show.
- **Rule:** §1 "Match the neighbours"; checklist "derived values are computed in the domain".
- **Failure:** smoked salmon, 150 kcal with 18 g protein and 9 g fat, read Protein 48% and Fat
  54% (102%) on "This portion" and 47% and 53% on Today's macros page.
- **Fix:** "fix(logger): the amount sheet's shares of energy are of the macros on show". The
  sheet uses Today's rule: the shares make 100, and with a macro hidden the gap they leave no
  longer gives the hidden macro's share away. `AmountTest.theSharesOfEnergyAreOfTheMacrosOwnEnergySoTheyMakeAHundred`
  and `aHiddenMacroIsLeftOutOfTheShares`.
- **Fixed later (cross-slice):** "fix(nutrition): one rule for a macro's share of energy".
  `macroEnergyShares` in `:libraries:nutrition` (`MacroShares.kt`) is the rule, and both builders
  call it. A softened macro now hides on "This portion" as the spec's table says; before, it read a
  soft word against the other macros' energy. `AmountTest.aSoftenedMacroHidesOnThePortion`.

## 5. The keyboard stayed up over sheets opened from search: fixed

- **Where:** `LoggerSheetContent.kt` `SearchPane`: the options button, the barcode button, the
  method chips and the meal dropdown.
- **Rule:** brief lead; same gap as opening a food, fixed just before this review.
- **Failure:** with the search field focused, Search options (and the scanner, Photo, Describe,
  Saved, the meal menu) opened with the keyboard still covering half of it.
- **Fix:** "fix(logger): the keyboard goes away before the search opens a sheet or menu". UI only.

## 6. How it felt is dead, and the design asks for it to go: fixed

- **Where:** `HowItFelt.kt`, `HowItFeltSheetContent.kt`, `HowItFeltRoute` in
  `features/logger/.../LoggerRoute.kt`, its registration in `LoggerFeatureEntryPoint.kt`.
- **Rule:** §1 "No dead code"; manifest note on `meal-feel-sheet`: "the logger's post-add
  How-it-felt prompt (logger-previews__logger-how-it-felt) should be removed".
- **Failure:** nothing navigates to `HowItFeltRoute` (Today's meal card opens its own
  `MealFeelRoute`), but the sheet, its view model, two previews, a leak surface and a test are
  all kept up, and the previews still publish a screen the design has dropped.
- **Fix:** "refactor(logger): delete the unreachable How it felt sheet", by the orchestrator in one go:
  in this slice delete `HowItFelt.kt`, `HowItFeltSheetContent.kt`, `HowItFeltTest.kt`,
  `HowItFeltRoute`, the `bottomSheet<HowItFeltRoute>` block and `howItFeltViewModelFactory` in
  `LoggerFeatureEntryPoint`, `LoggerHowItFeltPreview` and `LoggerHowItFeltDarkPreview` (and the
  `HowItFelt` helper) in `LoggerPreviews.kt`, the `logger-how-it-felt` entry in
  `LoggerLeakSurfaces.kt`, `LoggerFixtures.howItFeltState`, and `HOW_IT_FELT_HEIGHT_FRACTION`; outside
  it remove `"logger-how-it-felt"` from `REQUIRED_SURFACES` in
  `apps/leaktest/src/androidUnitTest/kotlin/com/opson/leaktest/SurfacesCoverageTest.kt:99` and the
  `logger-how-it-felt` mention in `docs/plans/screens/blind-leak-test.md:155`. Its strings
  (`logger_felt_*`, `logger_hunger_bit`) went too, and `docs/plans/screens/logger.md` no longer
  says the route stays.

## 7. `LogRoute` had no callers: fixed, docs deferred (cross-slice)

- **Where:** `features/logger/.../LoggerRoute.kt` (`LogRoute`, `LogTab`), registered in
  `LoggerFeatureEntryPoint`.
- **Rule:** §1 "No dead code".
- **Fix:** "refactor(logger): drop the unused LogRoute alias and basket feelings, fix stale
  docs". Grepped the whole repo, Swift included: only the entry point and one test used it. The
  test's start-pane checks moved to `LoggerViewModelTest.eachStartOpensItsPane`.
- **Deferred (cross-slice):** `docs/plans/screens/logger.md:175` and `:193`, and
  `docs/plans/screens/today-sheets.md:95`, still describe the alias; drop those lines.

## 8. A basket item's hunger and fullness were never set: fixed

- **Where:** `Basket.kt` `BasketItem.hunger`/`fullness`, read by `BasketCommitter.commit`.
- **Rule:** §1 "No dead code".
- **Failure:** nothing has set them since How it felt left the add flow; the committer still read
  the day again to merge them into the meal's feeling, and a test pinned that merge.
- **Fix:** same commit as 7. `addingTheBasketWritesOnceTellsTodayAndCloses` now asserts no feeling
  is written.

## 9. Stale KDocs: fixed

- `LoggerRoute` said the sheet is always full height (it opens at 60% from a +).
- `HowItFeltRoute` and `HowItFeltViewModel` said the logger opens it after adding and that hunger
  picked on the amount sheet shows there; neither has been true since design sync 2.
- `PhotoEstimateEvent.Logged` said it asks How it felt.
- Same commit as 7.

## 10. `!!` in `AmountChoice.from`: fixed

- **Where:** `AmountSheet.kt:205` (before), `quantity!!` guarded by a separate `known` flag.
- **Rule:** §2 "No `!!` on data from the network, disk or the user"; the draft's quantity comes
  from a basket item. Now a smart cast. Same commit as 7.

## 11. A barcode miss report could be lost when the scanner closed: fixed (cross-slice)

- **Where:** `BarcodeScanViewModel.kt:83`, `viewModelScope.launch { missReporter.report(code) }`.
- **Rule:** §2 structured concurrency, the right owner for work that outlives the screen.
- **Failure:** "Search instead" right after a miss cleared the view model and cancelled the report,
  so the dashboard undercounted the catalog's gaps for exactly the people who move on fastest.
- **Fix:** "fix(foods): a barcode miss report survives the scanner closing".
  `HttpBarcodeMissReporter` runs the call in the injected `AppCoroutineScope` and joins it, so a
  caller can still wait for it but cancelling the caller no longer cancels the report.
  `HttpBarcodeMissReporterTest.cancellingTheCallerDoesNotCancelTheReport`.

## Not a problem

- **SEA actions.** Every action is an intent or an arrival (`SearchTyped`, `InputsArrived`,
  `SpeechArrived`, `CustomSizeTyped`); none is a setter.
- **Search.** Debounced 250 ms, every keystroke cancels the pending job, an answer for another
  query or filter is dropped (`SearchResultsArrived`), and a tab or database that needs no server
  cancels a waiting search. The photo sheet's swap search does the same. Describe waits 700 ms.
- **`Catching` at the edges.** Catalog calls return `Catching` and are read with `getOrNull()` at
  the view model; the project's `Catching` rethrows cancellation. The voice flow's `.catch` does
  not swallow downstream cancellation.
- **Other loading paths.** The barcode lookup shows a spinner at once and a "looking it up"
  caption after 400 ms; the photo estimate shows a spinner with "Looking at your photo"; voice
  disables the mic while it settles and Describe shows "Reading that…" while it parses; the amount sheet already used
  `DelayedLoadingIndicator`.
- **Release flags.** `release.barcode`, `.photo`, `.voice` and `.searchOptions` default off, are
  read when the sheet opens, and nothing in this slice runs at launch. The photo picker is a
  provider so nothing about photos is built until its sheet opens.
- **Telemetry.** Events carry methods, counts, reasons and word buckets; no food names, amounts,
  barcodes or transcripts.
- **unslop-code scanner.** 247 low "verbose identifier" hits are framework and DI names
  (`collectAsStateWithLifecycle`, `barcodeScanViewModelFactory`); the 2 medium "narrating comment"
  hits are KDoc sentences that give a reason.

## Product notes

- **Photo swap offline says "no matches".** `PhotoEstimateViewModel.search` maps a failed search
  to an empty list, so offline the swap search reads as if the catalog had nothing. Saying
  "offline" there, as the logger's search does, would stop people concluding the food is missing.
  Fairly sure it surprises people; small to fix.
- **Unit labels repeat their weight.** USDA and label portions often carry the grams in the name
  ("container (150 g)"), so the pill and the menu say "150 g" twice. Stripping a trailing
  "(… g)" from the label when it matches the weight would read cleaner. Moderately sure.
- **"Pick and keep going" vs "Add".** With a basket already holding foods, "Add to dinner" on the
  amount sheet logs the whole basket, which people coming from search may not expect. A count on
  the button ("Add 3 to dinner") would say so. Unsure; worth watching in the walkthrough.
