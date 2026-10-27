# Findings: trends, round two (`features/trends/`)

Every file in the slice was read and the unslop-code scanner run over it. Round one's fixes
still hold: the intake chart and Patterns' sizes wait for the reveal (`TrendsState.opens()`,
`PatternsState.kt:117`), every page shows a spinner while it loads, photos decode off the main
thread, and a failed weigh-in says so. Each finding below names the place, the rule from
[PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

## 1. The photo grid composed, read and decoded every photo to show six: fixed

- **Where:** `photos/ProgressPhotosScreen.kt:64` (`verticalScroll` on the content) and `:121`
  (`state.months.forEach { MonthSection }`), with `photos/ProgressPhotosViewModel.kt:98`
  reading every thumbnail on open. Routed from the compose-perf audit.
- **Rule:** §4 "Lazy lists have stable keys and contentType"; §4 "No work in composition".
- **Failure:** someone taking a front and a side each week has about 100 tiles after a year,
  150 with a third pose. Each open read every thumbnail from disk, composed every tile and
  started a decode for each, holding every `ImageBitmap` while six were on screen.
- **Fix:** a `LazyVerticalGrid(GridCells.Fixed(3))`: the header, cards, month names and saved
  pairs are full-span items with their own keys, tiles are `items(key = "photo-<id>",
  contentType = "photo")`. A tile asks for its thumbnail when it is composed
  (`ProgressPhotosAction.ThumbnailWanted`); the view model reads each once, only while
  unlocked, and cancels and drops them all when it locks. A saved pair's tiles ask for the same
  ids, so the bytes are read once; they decode a second time only when a pair and its months
  are on screen together, which the lazy grid makes rare.
  `perf(trends): the photo grid composes and reads only the tiles on screen`, with
  `ProgressPhotosViewModelTest.opening_readsNoThumbnail_untilItsTileAsks`,
  `aTileAskingTwice_readsItsThumbnailOnce` and
  `locking_whileAThumbnailIsBeingRead_dropsIt_andAsksNothingMoreUntilUnlocked`.

## 2. A photo write that failed crashed the app: fixed

- **Where:** `photos/CompareViewModel.kt` (`setNote`, `saveComparison`, `removeComparison`),
  `photos/PhotoViewModel.kt` (`delete`), `photos/ProgressPhotosViewModel.kt` (`deleteAll`).
- **Rule:** §2 "Errors at the edges: expected failures are values handled at the ViewModel".
- **Failure:** `ProgressPhotoRepositoryImpl` writes the index and image files and lets the file
  store's exception through (a full disk; on iOS, protected data unavailable). Each call sat
  bare in `handleAction`, and an exception there ends `SEAViewModel`'s action loop in
  `viewModelScope`, which crashes the app. Typing a note writes on every keystroke, so a full
  disk took the app down mid-word. `CaptureViewModel` and `WeightViewModel` already caught theirs.
- **Fix:** each write is caught and logged, with the operation and no payload; the page keeps
  what it shows. `fix(trends): a photo write that fails no longer crashes the app`, with
  `CompareViewModelTest.aWriteThatFails_leavesThePageWorking`,
  `PhotoViewModelTest.aDeleteThatFails_keepsThePhoto_andThePageWorking` and
  `ProgressPhotosViewModelTest.aDeleteAllThatFails_keepsTheGrid_andThePageWorking` (the photo
  one checked failing without the fix).

## 3. Compare's note showed more than it kept: fixed

- **Where:** `photos/CompareViewModel.kt:62` (before the fix).
- **Rule:** §1 code that reads true; brief "a lie" in what the screen says.
- **Failure:** the note was cut to 280 characters on its way to the index, but the field held
  everything typed. Past the limit the text looked saved and was gone next time Compare opened.
- **Fix:** the field holds what is kept. `fix(trends): Compare's note field stops at the 280
  characters it keeps`, with `CompareViewModelTest.aNotePastTheLimit_showsOnlyWhatIsKept`.

## 4. Nutrients' week average lost the week at midnight: fixed

- **Where:** `NutrientsViewModel.kt:76` (before the fix).
- **Rule:** §10 lifecycle; §2 flows follow what they depend on.
- **Failure:** the week window was worked out only when settings emitted. Left open across the
  midnight that starts a week (Sunday, for a Monday start), the page kept observing last week's
  days while the builder, which reads the date afresh, looked for this week's, and said nothing
  was logged.
- **Fix:** the window follows the policy source too, which re-emits at midnight, as
  `TrendsViewModel` does. `fix(trends): Nutrients' week average moves to the new week at
  midnight`, with `NutrientsViewModelTest.weekAverage_openOverTheMidnightThatStartsAWeek_readsTheNewWeek`
  (checked failing without the fix).

## 5. Compare and a single photo said "locked" while they loaded: fixed

- **Where:** `photos/CompareViewModel.kt` `settle`, `photos/PhotoViewModel.kt` `settle`,
  `photos/CompareScreen.kt:135`, `photos/PhotoScreen.kt:74`.
- **Rule:** §10 "Every user-visible flow has … a loading state"; brief "a blank screen while
  something loads is a bug".
- **Failure:** both view models started from `PhotoLockState.Locked` and read the full photos
  inside the lock action before publishing anything. Opened from the unlocked grid, both pages
  showed "Your photos are locked" with an Unlock button for as long as one or two full JPEGs
  took to read. `CompareState.loading` was set and never drawn.
- **Fix:** both start from the lock as it stands, publish the dates and pickers first and the
  images when they land, and show a spinner (not the lock panel) while unlocked with nothing
  yet. `fix(trends): Compare and a single photo never say locked while they load`, with
  `CompareViewModelTest.whileThePhotosAreRead_thePageIsUnlocked_withThePairsDates_neverTheLockPanel`
  and `PhotoViewModelTest.whileThePhotoIsRead_thePageIsUnlocked_withItsDate_neverTheLockPanel`.

## 6. Weight ignored a number nobody weighs without a word: fixed

- **Where:** `WeightViewModel.kt:85` (before the fix).
- **Rule:** §10 "the error state tells the person what they can do".
- **Failure:** Save returned silently for zero or anything past 700 kg. A typo such as 7255 for
  72.55 left the number in the field and nothing else happened; with weight hidden the field
  shows dots, so there was no way to see why.
- **Fix:** a refused save says "That doesn't look like a weight, so it wasn't saved" in a polite
  live region until they type again; an empty field still does nothing.
  `fix(trends): Weight says when what was typed isn't a weight`, with
  `WeightViewModelTest.saveTapped_aNumberNoOneWeighs_savesNothing_andSaysSo_untilTheyTypeAgain`.

## 7. Dragging Compare's slider rebuilt the whole page model each frame: fixed

- **Where:** `photos/CompareViewModel.kt:61` (before the fix).
- **Rule:** §4 work per frame; §2 never block main.
- **Failure:** each drag event went through `settle`: re-checking the pair against every photo,
  copying the image map and building a `PhotoChoice` per photo, on main, at the pointer's rate.
- **Fix:** a slider move updates the handle only. `perf(trends): dragging Compare's slider
  moves the handle and nothing else`; `CompareViewModelTest.modeAndSlider` still covers it.
  The page still recomposes per frame because the position lives in state; with strong
  skipping only the slider frame and the two pickers re-run, so it is left there.

## 8. Comments that no longer matched the code: fixed

- **Where:** `TrendsViewModel.kt:50`, `TrendsFeatureEntryPoint.kt:72`, `TrendsFixtures.kt:81`
  and `TrendsPreviews.kt:142` said the Notes link was unbound (Today binds it,
  `TodayNotesListLink`); `TrendsFixtures.kt:45`, `:126`, `:170` and the builder tests called the
  fixture's Thursday "untracked" when it is a day with nothing logged (untracked is a separate
  flag, tested on its own); `TrendsSettingsRoute.kt:6` said the sheet holds the range (it moved
  onto the page); `NutrientsState.kt:56` had two KDoc blocks on `EnergyRow`, the first orphaned;
  `PatternsRoute.kt` named only the Recap as its way in.
- **Rule:** §1 "Stale comments are bugs".
- **Fix:** `docs(trends): comments match what the code does now`.

## 9. The charts say nothing to a screen reader: deferred

- **Where:** `PatternsScreen.kt` `HungerCardView` (a `TODO(review)` marks it), and the shared
  `IntakeBarChart` that Trends' Eaten and Hunger cards draw (`libraries/ui`, another slice).
- **Rule:** §8 accessibility, content descriptions.
- **Failure:** TalkBack and VoiceOver read the weekday labels and skip the bars, so the cards'
  whole point (which day runs hungrier, how the week went) is missing for someone who can't see
  them.
- **Why deferred:** each bar needs words for a relative level ("hungrier than most days"), and
  for the Eaten chart those words are themselves visibility-sensitive. That is a copy and
  policy decision, not a code fix. Cross-slice when decided: `IntakeBarChart` takes an optional
  per-bar description and sets it with `clearAndSetSemantics`.

## Not a problem

- **Nutrients' week average takes today's Burned as it stood on opening.** Only with
  `release.goalsAndEnergy` and a deficit or surplus, and only on a page left open past
  midnight; yesterday's full Burned is a better stand-in for the week's goal than a few minutes
  of the new day's.
- **Compare and the single photo still read their full images inside an action.** One or two
  reads; since finding 5 the page shows its dates and a spinner meanwhile, and Back still
  waits at most for those reads.
- **Nothing runs at launch.** `PhotoLock` registers its background observer when the first
  photo screen builds it; `StillCamera` and `BarcodeCamera`, injected into the entry point,
  have empty constructors.
- **Scanner hits.** All 31 are `collectAsStateWithLifecycle` and factory parameter names.

## Product notes

- **Charts need spoken summaries** (finding 9): today a screen reader user gets the card titles
  and weekday names only. Deciding the words for "hungrier than usual" unlocks Patterns and the
  Hunger card at once. Fairly sure this matters.
- **Weight could catch plausible typos too.** Finding 6 stops zero and 7255; 27.5 for 72.5 still
  saves. A gentle "that's a long way from your last weigh-in, save it anyway?" when a reading is
  more than, say, 15% off the trend would catch the rest. Not sure the extra step is welcome.
- **Compare's note limit is invisible.** The field now stops at 280 characters without saying
  why; a counter in the last 40 would explain it. Fairly sure.
- **A year of photos is one long scroll.** The grid is fast now, but there is no way to jump to
  a month. A month strip or a year header would help people who take photos weekly. Not sure.
