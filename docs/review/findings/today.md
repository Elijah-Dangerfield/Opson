# Findings: today (round two)

`features/today/` and `features/today/impl/`, all 58 files in the ledger, plus the four audit
findings routed here. Each names the place, the rule from [PRACTICES.md](../PRACTICES.md), what
breaks, and its fate.

## 1. Swiping the summary card on one phone could undo No numbers on the other: fixed (half deferred)

- **Where:** `TodayViewModel.kt:214-216` (before the fix), `SummaryPageShown`, `CollapseTapped`
  and `ExpandTapped` each called `settingsRepository.update`. Routed from the journeys audit.
- **Rule:** §10 "two devices editing the same day"; the visibility policy hides what the mode hides.
- **Failure:** `SettingsStore.update` queues the whole `Settings` object with a newer
  `clientUpdatedAt` (`settingsPayload`, `SettingsStore.kt:208`), and `applyPulled` skips a pulled
  row while one is pending (`:124`). Phone A switches to No numbers; phone B, before its pull lands,
  swipes the card. B's pull is skipped, B pushes its full stale object, the server's shallow
  `jsonbMerge` takes B's `mode`, `numbers` and `preset`, and both phones show calories again. Before
  this card, settings writes came only from deliberate settings edits; now a swipe makes one.
  Verified against `SettingsStore.kt`: the outbox's `basePayload` is used only to revert a
  rejected row, so the push really is the whole object.
- **Fix:** "fix(today): the summary card's page and collapse stay on this phone". A device cache,
  `TodayCardViewStore` (`TodayCardView.kt`, `CacheFactory.persistent("today_card_view")`), holds
  the page and the collapse; nothing on the card writes Settings now. A value Settings held before
  the move still opens until it is changed on this phone. Read only with `release.todaySummary` on.
  `TodayViewModelTest.swipingOrCollapsingTheCard_neverWritesSettings` (fails on the old code),
  `thePageLeftOpenAndTheCollapseAreSavedForNextTime`, `aPageSavedInSettingsBeforeTheMove_stillOpens`.
- **Deferred (cross-slice):** the second half, which closes the same race for every settings
  write, including Today's main number toggle (`MainNumberToggled`, a deliberate tap that still
  writes Settings). See the cross-slice list below.

## 2. Saving a note before the day was read wiped the note already there: fixed

- **Where:** `dayclose/DayCloseScreen.kt` `DoneTapped`, `daynote/DayNoteSheet.kt` `SaveTapped`,
  `meal/MealNoteSheet.kt` `SaveTapped`.
- **Rule:** brief, "missing loading states"; §10 lifecycle, a slow first read; §3 "the UI never
  waits on the network to show what the user just did" (here, it must not lose it either).
- **Failure:** Close the day draws Done outside its loading branch, and with `release.health` on
  the screen waits for the health store's first reading before the day arrives. Tapping Done in
  that window ran `setDayNote(day, "".ifEmpty { null })` on an empty model and then closed the
  day, so a note written earlier from the Day menu was erased. The day note and meal Note sheets
  had the same hole for the moment before Room answered (both already tracked `loaded` and never
  read it). A second Done while leaving also wrote again and sent a second `Close`.
- **Fix:** "fix(today): saving a note before the day is read no longer wipes it". Done waits for
  the inputs and acts once; both sheets save only once loaded.
  `DayCloseTest.doneBeforeTheDayIsRead_keepsTheNoteAndStays`,
  `aSecondDoneWhileLeavingWritesAndLeavesOnce`,
  `SheetsTest.aMealNoteSavedBeforeTheDayIsReadWritesNothing`,
  `aDayNoteSavedBeforeTheDayIsReadWritesNothing` (all four fail without it). A held diary,
  `FakeDiaryRepository.heldUntil`, makes the slow read.
- **Since closed days:** main's closed-day work moved the note into `closeDay(day, DayReflection)`,
  one write, and put Reopen beside Save on a closed day. The guard still sits ahead of that write,
  and the same once-only flag now covers Reopen, so Save tapped while Reopen leaves cannot close
  the day again. `DayCloseTest.onAClosedDay_aTapWhileLeavingAfterReopenNeitherClosesItAgainNorLeavesTwice`
  fails without it. Main's Save-then-Reopen test now opens Day close twice, as a person would.

## 3. A second tap while a food's sheet closed copied it twice, or starred and unstarred it: fixed

- **Where:** `item/ItemSheet.kt` `done()`, `DeleteTapped`, `FavoriteTapped`, `EditCopyTapped`.
- **Rule:** §6 "Idempotent writes"; round one's finding 2, in a sheet built since.
- **Failure:** `done` set `dismissed` but never checked it. Copy to another day, tapped twice as the
  sheet slid away, wrote two copies; Add to favorites tapped twice toggled the star on and off and
  said "Added to favorites"; each tap sent its own toast and `Dismiss`.
- **Fix:** "fix(today): a second tap while a food's sheet closes copies or stars once".
  `ItemSheetTest.aSecondTapWhileTheSheetCloses_copiesOnce_andLeavesTheStarOn` and
  `aSecondDeleteWhileTheSheetCloses_removesAndDismissesOnce`. Since closed days, `done()` takes
  the write's `DayWrite` and a refused one opens the closed sheet; the guard runs before either.

## 4. Every Today state recomposed every visible meal card and food row: fixed (config deferred)

- **Where:** `TodayScreen.kt:243` (before), `TodayListItem(row, state, drag, onAction)` and on
  into `Meal`, `FullMeal` and `FoodRow`. Routed from the Compose performance audit.
- **Rule:** §4 "Stable parameters", "No work in composition".
- **Failure:** the compose report listed `unstable state: TodayState` and `unstable card:
  MealCard` on all three; strong skipping compares those by identity, and the builder makes new
  ones on every action. So each select tick, swipe, ⋮ open, Undo bar, highlight fade and fasting
  minute re-ran every card and its `card.items.map { MealCardItem(...) }`.
- **Fix:** "perf(today): meal cards skip when nothing they draw changed". The rows take a small
  `@Immutable FoodRowContext` (picked ids, swipe, the two flags, the outline, and since closed
  days whether the day is closed, which takes every meal's +) instead of the
  state; `MealEntry`, `MealCard`, `ItemRowUi` and `MealNoteUi` are `@Immutable`, so equal cards
  skip by equality. The report now lists `Meal`, `FullMeal` and `FoodRow` with stable parameters
  only. `TodayStateBuilderTest.theMenuOrAnUndoBarLeavesTheMealsAndWhatTheirRowsReadEqual` pins
  the equality the skip depends on, and that closing the day breaks it. No composition harness in this module to count recompositions.
- **Left as is:** the `MealCardItem` mapping stays in `FullMeal`; it needs `stringResource` for
  the precision label, and it now runs only when that card or the row context changes.
- **Deferred (cross-slice):** the repo-wide stability config, below. With it, `WeightCard`'s
  `RelativeDay` and the summary card's `Display`s would skip too.

## 5. Today's list had no contentType: fixed

- **Where:** `TodayScreen.kt:242` (before). Routed from the Compose performance audit.
- **Rule:** §4 "Lazy lists have stable keys and contentType".
- **Failure:** the summary, water, weight and meal rows shared the null type, so a scrolled-off
  summary slot could be reused for a meal card and rebuilt from nothing.
- **Fix:** `contentType = { it::class }`, in the same commit as 4.

## 6. Stale comments and plans: fixed

- `TodayFeatureEntryPoint.kt:89` listed a How it felt sheet; the Note replaced it (6e6b4555).
- `TodayState.kt:298` said the Note's record was the "How it felt" record; it is the meal's
  `MealFeeling`.
- `TodaySheetRoutes.kt` said the meal sheet offers "ate no details" (gone) and that Edit Today's
  cards is meal groups and water (it has the summary stats and fasting too).
- `MealNoteRoute.kt` left out the Notes list, which opens it.
- `TodayState.kt` `StatTile` and `DayCloseState.kt` `DayTotals` each had two KDocs in a row; only
  the second attaches, so the first said nothing to the IDE.
- `docs/plans/screens/adding.md:47` and `:126` still drew Quick add (0cb315c1) and Ate, no
  details (ca4eba51) in the add grid and had no Exercise (`release.exercise`, as
  `LoggerStateBuilder.actions` has it); `today.md:219` put Close the day in a card after the meals
  (92795cb8 made it a header check). A dated line now says where it and the Nutrients link are.
- **Rule:** §1 "Stale comments are bugs". Routed in part from the truth audit.
- **Fix:** "docs(review): Today's KDocs and plans say what the code does now".

## 7. The energy card did the day's range and the eaten bar twice: fixed

- **Where:** `TodayStateBuilder.energy` recomputed `todayRange` after `summary` had, and
  `barParts` twice (once for the card); `foods` repeated `inSentence`; `DayCloseScreen.kt` kept its
  own copy of `Metric.color()`.
- **Rule:** §1 "No dead code"; a concrete cost on every build of the state.
- **Fix:** "refactor(today): build the day's range and eaten bar once per state". Covered by the
  builder and energy card tests.

## 8. No numbers' meal circles dropped their time for a screen reader: fixed

- **Where:** `TodayModeViews.kt` `TimelineMeal`.
- **Rule:** §8 accessibility.
- **Failure:** the circle's `contentDescription` ("Breakfast, done") stands in for its merged
  texts, so TalkBack and VoiceOver never said when it was logged or when it usually is.
- **Fix:** "fix(today): No numbers' meal circles tell a screen reader when, too". The description
  ends with the time, or "Around 7:00 PM" rather than the tilde. UI only; no composition harness.

## 9. Round one's "Save as meal" in the select panel is gone: deferred (cross-slice)

- **Where:** `TodaySelectViews.kt` `SelectBar`; 8e9e0b05 rebuilt select mode to design sync 4's
  bar (Move, Copy to…, Delete) and removed `SaveAsMealTapped` with round one's fix 6.
- **Rule:** §1 "No dead code".
- **Failure:** a deliberate redesign, so nothing in Today is wrong, but
  `CreateMealRoute.entryIds`, added for that button, now has no caller in production.
- **Why deferred:** the route is the foods slice's; see the list below, and the product note.

## Round one's fixes, checked

- 1 (spinner while loading): holds; `healthStoreStillAnswering_keepsTodayLoading_untilItDoes`.
- 2 (meal and water sheets act once): holds; extended to the food's sheet in 3 above.
- 3 (opaque sticky bar): holds; `StickyLayer` still fades only the contents, in `graphicsLayer`.
- 4 (food bridge regex): moot; `FoodBridges.kt` went with Explorer (4dcbb205).
- 5, 8, 9: hold. 6: superseded, see 9. 7 (time picker for Move): still waits for a design; the
  `TODO(design-sync-2)` in `TodaySelectViews.kt` says so.

## Not a problem

- **The card's view is not cleared on sign-out.** `TodayCardView` is a page index and a flag, no
  account data; the next account on the phone opening on page 2 is harmless.
- **NaN in the rings.** `EnergyCardBuilder` divides by Burned's total and the day's target. Burned
  is null until resting energy is known and a target is never zero, so neither reaches zero.
- **SwipePages writing on first draw.** It calls `onPageChange` only when a swipe settles on
  another page, so opening Today writes nothing.
- **Release flags.** `release.todaySummary`, `.todaySelect`, `.foodActions` default off; with the
  summary flag off the new card-view cache is not read, as the weight store is not.
- **Logging.** Nothing in the slice logs; no `catch` or `runCatching` sits around a suspend call.
- **unslop-code scanner.** 126 low "verbose identifier" hits, all DI factories and framework names.

## Second review

A second reviewer checked this branch's commits and flagged five possible regressions. Fates:

1. **`Settings.kt` KDocs for `todaySummaryCollapsed` and `todaySummaryPage` now lie (026cd6f8):
   real, deferred (cross-slice).** Nothing writes either field since the move, and the KDocs still
   describe them as the live store. The file belongs to domain-diary-nutrition, so the exact change
   is item 6 of the cross-slice list.
2. **The card's page and collapse survived a user change (026cd6f8): real, fixed.** The move took
   them out of Settings, which `SettingsStore` wipes as a `UserScopedClearer`, into a device cache
   nothing cleared. Signing out and in as someone else on the same phone opened the card on the
   last account's page and collapse, and because the cache was then non-null it also hid the new
   account's own synced choice. No numbers leaked, only view state. Fixed in "fix(today): a user
   change drops the summary card's page and collapse": `TodayCardViewStore` is now a
   `UserScopedClearer` multibinding that clears its cache.
   `TodayViewModelTest.aUserChange_dropsTheCardViewLeftByThePreviousAccount` fails without it
   (expected page 0, was 1). The test fake's `clear()` now writes the default back, as the
   DataStore cache does, so a collector sees the reset.
3. **No numbers' circle description was glued from two strings, untested (8d5c81e1): real,
   fixed.** It joined a localized "Breakfast, done" to a localized "Around 7:00 PM" with a
   hard-coded ", ", so it read "Breakfast, done, Around 7:00 PM" and no translator could reorder
   it. Fixed in "fix(today): a meal circle's spoken time is one translatable sentence": one string
   per status in `strings_modes.xml` (`modes_timeline_done_at_cd`, `modes_timeline_done_around_cd`,
   `modes_timeline_open_cd`, the last now with the time), chosen by a pure `TileStatus.spoken()`.
   `TimelineMealSpokenTest` covers Logged, AteNoDetails and Empty. The old `modes_timeline_done_cd`
   is gone.
4. **Delete's half of the double-tap guard had no test (f01971dc): real gap, test added.** The
   guard itself was right. "test(today): a second Delete while a food's sheet closes removes it
   once" asserts one removal, one toast and one Dismiss; with Delete taken back out of `done()` it
   fails (the item removed twice).
5. **`docs/plans/screens/adding.md` edited outside the slice (07019c0b): not a regression.**
   `docs/plans/` is in no slice's ledger, `today.md` included, so no reviewer owns it, and the
   second reviewer confirmed the edit matches `LoggerStateBuilder.actions` (no Quick add or Ate,
   no details; Exercise behind `release.exercise` and energy out). Reverting it would put a stale
   plan back, which §1 counts as a bug, and splitting it out means rewriting a commit already on
   the branch. Left as is; the logger reviewer should know `adding.md` changed here.

## Deferred cross-slice changes

1. **domain-diary-nutrition:** `libraries/diary/impl/src/commonMain/kotlin/com/opson/libraries/diary/impl/SettingsStore.kt`,
   `update`: push a patch, not the whole object. Encode `next` and the last-synced copy
   (`record.clientUpdatedAtEpochMs?.let { settingsPayload(before, it) }` is already computed as
   `base`), keep only the top-level keys of `settings` whose JSON differs, always keep `preset`,
   and enqueue that object. The server's shallow merge then leaves untouched keys such as
   `numbers` with the other phone's value. When a pending row is coalesced in `DiaryStore.enqueue`,
   merge the two patches' keys rather than replacing. This also covers Today's main number toggle.
2. **test-apps:** `apps/integration/src/androidUnitTest/kotlin/com/opson/integration/sync/TwoDeviceSyncTest.kt`:
   A sets No numbers and syncs; B (not yet pulled) changes an unrelated key (`todayMainNumber`);
   both sync; assert `numbers.preset == NoNumbers` on both. It fails until 1 lands.
3. **build-config:** add `compose-stability.conf` at the repo root with `kotlin.collections.*`,
   `kotlinx.datetime.*`, `com.opson.libraries.nutrition.*` and `com.opson.libraries.diary.*`, and in
   `ComposeMultiplatformConventionPlugin.kt` and `ApplicationConventionPlugin.kt` add
   `extensions.findByType(ComposeCompilerGradlePluginExtension::class.java)?.stabilityConfigurationFiles?.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))`.
4. **onboarding-foods:** `features/foods/src/commonMain/kotlin/com/opson/features/foods/CreateMealRoute.kt`
   `entryIds` has no caller since 8e9e0b05. Either drop it with its branch in
   `CreateMealViewModel.kt:64-69` and `CreateMealTest.selectVm`, or keep it if the product note
   below brings Save as meal back.
5. **domain-diary-nutrition, later:** `Settings.todaySummaryPage` and `todaySummaryCollapsed`
   (`libraries/diary/.../Settings.kt:141-143`) are now read only as the fallback for a phone that
   never set its own. Drop them, and the fallback in `TodayStateBuilder.build`, once 0.2 has been
   out long enough that every device has opened Today on it.

6. **domain-diary-nutrition:** `libraries/diary/src/commonMain/kotlin/com/opson/libraries/diary/Settings.kt:140-143`.
   The KDocs still call `todaySummaryCollapsed` "kept across days and restarts" and
   `todaySummaryPage` "the page last left open", but nothing writes them since the card's view moved
   to `TodayCardViewStore`. Replace both with: legacy, no longer written; read only as Today's
   fallback until this phone's `TodayCardViewStore` holds a value; keep while 0.1 clients still sync
   it. A reader who takes the old KDoc at its word could start writing them again and bring back the
   two-phone race in finding 1. Second review, item 1.

## Product notes

- **Save as meal from select mode.** Design sync 4's bar dropped it, but picking three foods and
  saving them as a meal is the quickest way to make a saved meal, and Create a meal already takes
  `entryIds`. Worth asking design whether it belongs in the bar or the ⋮. Fairly sure it is missed.
- **Two phones, two card pages.** The card's page and collapse are now per phone. Someone with a
  phone and a tablet sets each once. Sure that is fine; it is how scroll position works too.
- **Done while Close the day loads.** Done now does nothing until the day is read, under a spinner.
  Drawing it disabled until then would say so. Fairly sure; small.
- **Burned on a planned day.** Today's Burned section (`release.exercise`) shows on tomorrow too,
  with a resting estimate for a day that has not happened. Unsure whether that helps planning or
  reads as a number nobody asked for.
