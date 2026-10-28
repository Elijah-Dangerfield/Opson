# Findings: logger (round two)

`features/logger/` and `features/logger/impl/`, all 81 files in the ledger. Each finding names
the place, the rule from [PRACTICES.md](../PRACTICES.md), what breaks, and its fate. Findings 1
to 4 came in from the cross-cutting audits; the rest are this pass's own.

## 1. The food detail's amount field rewound fast typing: fixed

- **Where:** `FoodDetailContent.kt` `AmountField`, which called
  `androidx.compose.foundation.text.BasicTextField` with `value = state.amount`.
- **Rule:** §4 Compose performance, no backwards writes; state read correctly. Audit
  compose-perf, high.
- **Failure:** `AmountTyped` goes through the action channel, a full rebuild and the state flow,
  so the echo lands a frame or more after the keystroke. In between, recomposition handed the
  field the old text and it dropped the keystrokes typed meanwhile. A quick "150" could log
  "15" or "10", wrong calories with nothing on screen to say so, and worse behind a favorite
  tap's settings write. It was the only raw field in `features/`.
- **Fix:** "fix(logger): the food detail's amount field keeps fast keystrokes". It uses the
  `libraries/ui` `BasicTextField`, which buffers through `rememberTypedText`; a sanitized
  refusal still snaps back when the buffer settles. There is no composition harness in this
  module; `TypedText` carries its own tests in `libraries/ui`.
- **Deferred (cross-slice):** a guard so this cannot come back. Add a detekt rule (or a
  `ForbiddenImport` entry in the detekt config) that bans
  `androidx.compose.foundation.text.BasicTextField` outside
  `libraries/ui/src/commonMain/kotlin/com/opson/libraries/ui/components/text/`.

## 2. Search composed every row of a list at once: fixed

- **Where:** `FoodSearchScreen.kt` `body`: `item(key = "results") { FoodRows(body.rows) }`, and
  the same for `listed`, `usual` and `recent`.
- **Rule:** §4 lazy lists have stable keys and content types. Audit compose-perf, medium.
- **Failure:** Your foods is `customFoods + history` (up to 100 logged foods, custom foods
  unbounded), so opening it composed and measured 100+ rows in one item, recomposed all of
  them on every keystroke because the list is a new instance, and reused nothing while
  scrolling.
- **Fix:** "perf(logger): search lists one lazy item per food row". `foodRows` emits an item per
  row keyed `"$section:${row.key}"` with content type `food`, and `groupedListSlot` draws each
  row's share of the card (gap and top corners on the first, bottom corners on the last). The
  list lost `spacedBy`, since rows sit flush, so every other item brings its own gap through
  `block`. Checked against `FoodRowsCard`'s inset and edge; UI only.

## 3. The exercise sheet was a dead end on Later and a softened energy out: fixed

- **Where:** `ExerciseSheetState.kt` `build`, `ExerciseSheetContent.kt` `Burned` and
  `ActivityPicker`.
- **Rule:** §10 every flow has an error state that says what the person can do. Audit
  journeys, medium.
- **Failure:** `exerciseOffered` offers the sheet whenever energy out is not hidden, which
  includes Later (`Presets.laterLevels`, Delay) and a Soften override. There the number field is
  not drawn. Picking Other (no MET), or a listed activity with no weigh-in, left `draft()` null
  and Save off for good, while the sheet said "type the number below" and "Pick Other to add it
  yourself".
- **Fix:** "fix(logger): the exercise sheet offers Other only where its number can be typed".
  The state carries `offersOther`; the list leaves Other out and its no-match line drops
  "Pick Other"; the no-weigh-in line drops "or type the number below"; the view model refuses
  an Other pick it does not offer. `ExerciseSheetViewModelTest.withEnergyOutOnLater_otherIsNotOffered_andCannotBePicked`,
  `withEnergyOutOnLater_aListedActivityStillSavesItsEstimate`, `withEnergyOutShowing_otherIsOffered`.

## 4. Describe sent text the server refuses: fixed

- **Where:** `LoggerViewModel.kt` `describeTyped` (and the voice partials that fill the same text).
- **Rule:** §9 the client and server agree on the contract. Audit contract, low.
- **Failure:** the server answers a text over `DescribeParser.MAX_TEXT` (1,000) with 400
  `invalid_text`. A long dictation or a pasted list failed on every debounce and read as
  "That didn't parse. Try fewer words", a round trip per pause.
- **Fix:** "fix(logger): Describe sends no more than the server reads". Typed, dictated and
  route-given text is cut to `LoggerViewModel.DESCRIBE_LIMIT`, as the photo note is cut to its
  own. `DescribeReviewViewModelTest.aDescriptionLongerThanTheServerReadsIsCutToWhatItReads`.
- **Deferred (cross-slice):** in `apps/integration/.../foods/DescribeAndPhotoTest.kt`, a journey
  that sends `LoggerViewModel.DESCRIBE_LIMIT` characters (answered) and one more (400
  `invalid_text`), so the two constants cannot drift apart.

## 5. A double tap on a food's + logged it twice: fixed

- **Where:** `FoodSearchViewModel.add` and `addSavedMeal`, `LoggerViewModel.recentAdd`.
- **Rule:** §6 idempotent writes, applied to the client (round one's finding 3 covered the
  sheets that close; these stay open).
- **Failure:** the second tap is queued behind the first and runs before the day is read again,
  so the row has no ✓ to take back out yet: it logged the food a second time, and the Undo
  snackbar only took one back. Two taps in a few milliseconds, but a + that seems not to have
  registered invites exactly that.
- **Fix:** "fix(logger): a double tap on a food's + logs it once". `UnshownLogs` (in
  `FoodPicks.kt`) holds what a + logged until the day shows it; a tap on such a row is ignored,
  and from then on the ✓ works as before. `FakeDiaryRepository.holdWrites` lets a test hold a
  write back as Room's next read would. `FoodSearchViewModelTest.aDoubleTapOnPlusLogsTheFoodOnce`
  and `LoggerViewModelTest.aDoubleTapOnRecentsPlusLogsTheFoodOnce`, both proved failing without
  the fix.

## 6. A second Save on a barcode miss logged the name twice: fixed

- **Where:** `BarcodeScanViewModel.saveName`.
- **Rule:** §6 idempotent writes; round one's guard was never added here.
- **Failure:** Save, then a second tap while the scanner closes: two name-only items.
- **Fix:** "fix(logger): a second Save on a barcode miss logs the name once".
  `BarcodeScanViewModelTest.aSecondSaveWhileTheScannerClosesLogsNothingMore`, proved failing first.

## 7. Describe's find and the photo swap read offline as "nothing by that name": fixed

- **Where:** `LoggerViewModel.findFoods` and `PhotoEstimateViewModel.search` mapped a failed
  search to an empty list; `DescribeFindPane` and `SwapPane` then drew `describe_find_none` /
  `photo_swap_none`.
- **Rule:** §10 the error state tells the person what they can do. Round one left the photo half
  as a product note.
- **Failure:** offline, every food looked missing from the catalog, so someone would conclude
  the food does not exist and give up or save it by name.
- **Fix:** "fix(logger): Describe's find and the photo swap say offline, not \"nothing by that
  name\"". `SwapAnswer.offline` is set when the search failed or came from the cache, and an
  empty offline answer says the search needs a connection. `DescribeReviewViewModelTest.findOfflineWithNothingCachedSaysItNeedsAConnection`,
  `findOnlineWithNothingFoundIsAMiss`, `PhotoEstimateViewModelTest.aSwapSearchOfflineWithNothingCachedSaysItNeedsAConnection`.

## 8. `DescribeRows.forPolicy` was never called: fixed

- **Where:** `DescribeReview.kt` `forPolicy`; `LoggerViewModel` `PolicyArrived` only stored the policy.
- **Rule:** §1 no dead code; match the neighbours (`PhotoEstimateViewModel` applies
  `PhotoRows.forPolicy` on every policy change).
- **Failure:** with calories hidden while Describe was open (a mode change synced from another
  device, a timer ending), a line read in grams kept its weight and its "grams" unit, which the
  line rules say never shows with calories hidden.
- **Fix:** "fix(logger): hiding calories while Describe is open moves weighed lines to
  measures". `DescribeReviewViewModelTest.caloriesHiddenWhileDescribeIsOpenMovesAWeighedLineToItsMeasure`.

## 9. Dead code: fixed

- `LoggerCommon.kt` `LogDraft.nameOnly()` and `AMOUNT_HEIGHT_FRACTION` (the amount sheet is gone).
- `LoggerEvent.ShowToast`: the view model never sends it; its branch in the entry point went too.
- `LoggerState.day`: built and tested, never drawn. Its KDoc promised "the a11y of the title
  when it is not today"; nothing reads it (see the product notes).
- **Rule:** §1 no dead code; stale comments are bugs.
- **Fix:** "refactor(logger): drop dead code and fix KDocs the amount sheet left behind".

## 10. Stale KDocs and comments: fixed

- Four still sent a scan hit or a unit pick to "the amount sheet" (`BarcodeScan.kt`,
  `BarcodeScanScreen.kt` `ReadConfirmation`, `BarcodeScanPreviews.kt`, `DescribeReview.kt`
  `unitPicked`); it is the food detail now.
- `PhotoEstimateRoute`, `PhotoEstimateViewModel` and `PhotoEstimateEnabled` said only "the
  logger's Photo chip" opens the sheet; Photo on the + sheet and search's "Photograph it" do.
  `BarcodeScanEnabled` said the same of the search bar's button alone. Their QA descriptions
  said so too.
- `shortName` named "the Favorites tiles, the basket line": both gone.
- `PhotoEstimateViewModel`'s "Plus gate" comment described a paywall that does not exist.
- **Rule:** §1 stale comments are bugs.
- **Fix:** same commit as 9.

## 11. The torch icons live in the scanner, not `AppIcons`: deferred (cross-slice)

- **Where:** `BarcodeScanScreen.kt:479`, `TODO(review)` over `LightOn` and `LightOff`.
- **Rule:** AGENTS.md, custom UI components in libraries/ui.
- **Why deferred:** `AppIcons` is `libraries/ui`'s. Add `FlashlightOn` and `FlashlightOff`
  (`MaterialIcons.Rounded.FlashlightOn` / `FlashlightOff`, identifiers `flashlight_on` /
  `flashlight_off`) to `libraries/ui/src/commonMain/kotlin/com/opson/libraries/ui/components/icon/AppIcons.kt`;
  then this slice swaps `LightOn`/`LightOff` for them and drops the TODO.

## 12. The photo sheet offers "Back to search" when it was opened from the + sheet: deferred

- **Where:** `PhotoEstimateSheetContent.kt` `StageBody`, Unavailable and TooMany:
  `photo_back_to_search` runs `BackTapped`, which dismisses the sheet.
- **Rule:** §1 copy that reads true; §10.
- **Failure:** opened from the + sheet's Photo tile, the + sheet gave way to it, so Back lands on
  Today, not search.
- **Why deferred:** the button's words are design copy ("Back" or "Search instead" with a real
  route), and `release.photo` stays off for 0.2. A `TODO(review)` sits over the two buttons.

## Not a problem

- **Round one's fixes hold.** Every closing sheet still guards its write (`written`, `logged`,
  `done`, `handedBack`); How it felt and `LogRoute` are gone; the barcode miss report runs in the
  app scope inside `HttpBarcodeMissReporter`.
- **Cancellation.** Catalog calls return `Catching` and are read with `getOrNull()` at the view
  model; `Catching` rethrows cancellation. The voice flow's `.catch` sits upstream of `collect`.
- **Nothing at launch.** `release.barcode`, `.photo`, `.voice`, `.searchOptions` and
  `release.exercise` default off and are read when a sheet opens. `AutoInit` is not used.
- **Telemetry.** Methods, counts, reasons and word buckets only; no food names, amounts,
  barcodes, transcripts or the describe text.
- **Exercise's typed number after a policy change.** `draft()` reads `form.typed` even once the
  field is gone. The person typed it, and an edit of a typed entry keeps it; both are right.
- **Activity picker work in composition.** The labels are string resources and must resolve in
  composition; the filter is `remember`ed on structurally equal keys, over about 40 entries.
- **unslop-code scanner.** 21 "emoji" hits are the ✓ glyph the UI draws, named in KDoc; 9
  "narrating comment" hits are the feature name "Create a meal"; 273 "verbose identifier" hits
  are framework and DI names (`collectAsStateWithLifecycle`, `photoEstimateViewModelFactory`).

## Second review

A second reviewer checked this branch for regressions and flagged two. Both were real.

### R1. `AmountField`'s KDoc said a stray character never shows: fixed

- **Where:** `FoodDetailContent.kt`, the KDoc over `AmountField` (from finding 1).
- **Rule:** §1 stale comments are bugs.
- **Failure:** the KDoc said the amount "comes back sanitized, so a stray character never
  shows". With the library field, a character the view model drops ("15" plus "a" sanitizes to
  "15") changes nothing in the state, so nothing comes back, and the field shows "15a" until
  `TypedText.settle` runs 300 ms after typing pauses. No number leaks; the comment was wrong.
- **Fix:** "docs(logger): the amount field's KDoc says a dropped character shows until typing
  pauses". Comment only, so there is no test to add.

### R2. Editing an Other entry on Later lost Other from the activity list: fixed

- **Where:** `ExerciseSheetState.kt` `build` (`offersOther = canNumber`) and
  `ExerciseSheetViewModel.kt` `offered`, from finding 3.
- **Rule:** §10 every flow keeps its state legible; §7 every bug fixed gets its test.
- **Failure:** an Other entry logged while energy out showed, then edited after it moved to
  Later. The sheet opens on the form and Save works through the entry's typed number, but
  opening the picker drew no Other row, so the selected activity was missing, and tapping it
  could not pick it back.
- **Fix:** "fix(logger): editing an Other entry on Later keeps Other in the activity list".
  `ExerciseSheetStateBuilder.offersOther` offers Other where the number can be typed, where the
  form is already on Other, or where the entry being edited is Other; the view model's pick
  check uses the same rule. `ExerciseSheetViewModelTest.withEnergyOutOnLater_anOtherEntryBeingEdited_keepsOtherInTheList`
  failed before the fix.

## Product notes

- **The + sheet never says it logs to a past day.** With Today on yesterday, the sheet reads
  "Add to dinner" and logs to yesterday. A "yesterday" under the title (the dead `day` field was
  meant for this) would stop a log landing on the wrong day. Fairly sure it surprises people.
- **A saved meal's ✓ adds it again.** In search, a food's ✓ takes it back out, but a saved
  meal's ✓ logs the whole meal a second time (without the haptic). Either make it take the
  meal back out, as foods do, or keep the + after adding. Fairly sure it is unintended.
- **Long descriptions are cut silently.** Past 1,000 characters Describe stops taking text.
  A count near the limit ("980 of 1,000") would explain it, though few will reach it. Unsure
  it is worth the clutter.
