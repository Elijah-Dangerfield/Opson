# Findings: today

`features/today/` and `features/today/impl/`, all 45 files. Each names the place, the rule from
[PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

## 1. Today and Close the day were blank while the day loaded: fixed

- **Where:** `TodayScreen.kt` (`state.loading` was never read; `todayRows` of the initial state
  is empty) and `dayclose/DayCloseScreen.kt:250` (`if (state.loading) return@Column`).
- **Rule:** brief, "a blank screen while something loads is a bug".
- **Failure:** both screens `combine` the day with the health store's energy
  (`TodayViewModel.inputs`, `DayCloseViewModel.expended`). With `release.health` on and a store
  connected, the first value waits for `HealthRepository.energy`, which refreshes and then runs a
  HealthKit or Health Connect query before it emits, on every open of today. Until then Today was
  an empty page under its header and Close the day stopped after its title.
- **Fix:** "fix(today): Today and Close the day show a spinner while the day loads". A
  `TodayRow.Loading` row (`DelayedLoadingIndicator`) while loading, the same indicator on Close
  the day, and the loading header names the selected day rather than always "Today".
  `TodayViewModelTest.healthStoreStillAnswering_keepsTodayLoading_untilItDoes` and
  `TodayStateBuilderTest.beforeTheDayIsReadTheListIsOnlyTheSpinner`. The rows are now
  `remember`ed per state instead of rebuilt on every recomposition.
- **Left as is:** the wait itself. Emitting `null` energy first would draw the day's range
  without exercise and then move it, and on Close the day a big-day card could flash and vanish.
  If a store ever hangs rather than answers, a timeout in `RealHealthRepository.energy`
  (`:libraries:health`) is the place for it.

## 2. A second tap while the meal or water sheet closed wrote twice: fixed

- **Where:** `meal/MealSheet.kt` (`CopyFromDayBeforeTapped`, `AteNoDetailsTapped`) and
  `water/WaterSheet.kt` (`AddGlassTapped`, `AddCustomTapped`, `RemoveGlassTapped`).
- **Rule:** §6 "Idempotent writes"; §7 every bug gets its test.
- **Failure:** actions run in order, and the sheet takes a moment to animate down after a write,
  so a quick second tap copied yesterday's meal twice or counted two glasses.
- **Fix:** "fix(today): a second tap while the meal or water sheet closes writes nothing more".
  `SheetsTest.aSecondTapWhileTheMealSheetClosesWritesNothingMore` and
  `aSecondTapWhileTheWaterSheetClosesCountsNoSecondGlass`. The two existing tests that fired
  several writes at one sheet (which a person cannot do) now use a sheet per write.

## 3. The sticky summary bar was see-through while it slid in: fixed

- **Where:** `TodayScreen.kt` `StickyLayer`, `fadeIn() + slideInVertically`.
- **Rule:** walkthrough lead; §4 animate in the draw phase.
- **Failure:** the bar's surface faded in with its contents, so for a moment the water row and
  the big calorie figure under it showed through.
- **Fix:** "fix(today): the sticky summary bar stays opaque while it slides in". An opaque card
  surface from the first frame; only the bar's contents fade, through `graphicsLayer` alpha read
  in the draw phase. The list box clips, so the bar slides out from under the header instead of
  over it. UI only; no composition test harness in this module.

## 4. The food bridge compiled a regex for every word lookup: fixed

- **Where:** `FoodBridges.kt` `words`, `Regex("[^\\p{L}]+")` inside the function.
- **Rule:** §4 "no allocation of big objects" in hot paths; a concrete cost.
- **Failure:** `words` runs for every candidate against every safe food on each Explorer rebuild,
  and each call compiled the pattern again.
- **Fix:** "perf(today): compile the food bridge's word pattern once; fix stale calendar and
  select notes". Covered by the existing `FoodBridgesTest`.

## 5. Stale notes: fixed

- `CalendarSheet.kt` said "up to six days ahead can be picked"; it is
  `TodayStateBuilder.MAX_DAYS_AHEAD`, one.
- The Move TODO said the time picker lives in Settings' module; `TimePickerDialog` is in
  `:libraries:ui` (the amount sheet uses it). Both TODOs now say what they wait for.
- Same commit as 4.

## 6. "Save as meal" from multi-select: fixed

- **Where:** `TodaySelectViews.kt` `Actions`, `TODO(design-sync-2)`.
- **Rule:** design 2.12 draws Save as meal first in the select panel's row.
- **Fix:** "refactor(foods): let Create a meal start from chosen diary entries" gave
  `CreateMealRoute` an `entryIds` argument; "fix(today): add Save as meal to the select panel" adds
  `SaveAsMealTapped`, `TodayEvent.OpenCreateMeal(day, entryIds, slot)` and the panel button with
  the bookmark-plus icon, before Copy to…. Behind `release.todaySelect` like the rest of the panel.
  The slot is passed only when every pick sits in the same meal, so it can name the meal.

## 7. A time picker for Move: deferred

- **Where:** `TodaySelectViews.kt` `MoveTo`, `TODO(design-sync-2)`.
- **Rule:** design 2.12 notes, "Move changes the meal or time".
- **Why deferred:** the design has no frame or copy for the Move step's time control, so building
  it would mean inventing both. The parts exist: `TimePickerDialog` in `:libraries:ui`, and
  `DiaryRepository.updateItem` to rewrite `loggedAt` after `DiaryItemEditor.moveItems`.

## 8. `MealCard.feel` was always empty: fixed (cross-slice)

- **Where:** `TodayStateBuilder.mealCard` set `feel = emptyList()`; `TodayScreen.FullMeal` passed
  it to `TodayMealCard`.
- **Rule:** §1 "No dead code".
- **Fix:** "refactor(today): drop the meal card's always-empty feel chips". `MealCard.feel`, its
  assertion in `TodayStateBuilderTest`, and `TodayMealCard`'s `feel` parameter and chip row in
  `:libraries:diary:ui` are gone. Mindful's feel chips (`MealEntry.Compact`, `JournalEntry`) stay.

## 9. "Different by day" never reached Today or Close the day: fixed

- **Where:** `ExerciseRange.kt` `todayRange` called `Targets.today` with the base range;
  `dayclose/DayCloseState.kt` took tomorrow from `Targets.tomorrow()`, the base range too.
- **Rule:** checklist "derived values are computed in the domain"; the Targets screen's own goal
  styles.
- **Failure:** with a training-day group of 2,300 to 2,700, Today and Close the day still drew
  the base range on a training day, and the big-day card named the base as tomorrow's range.
- **Fix:** "fix(today): a day's range starts from its own goal, a training day's included", on top
  of 9c434c0 from the nutrition slice (cherry-picked here). `todayRange` passes
  `rangeFor(day.date, settings.numbers.preset)`; tomorrow is `rangeFor` of the next date, which
  still reads only settings and the date, so a big day cannot move it.
  `TodayStateBuilderTest.aTrainingDayShowsItsOwnRange` and
  `DayCloseTest.tomorrowIsTomorrowsOwnRangeUnderDifferentByDay`.
- **Left open:** a set deficit still reads as the base range. `rangeFor` needs `burnKcal` for it,
  and nothing on Today passes one; whether that is today's burn so far, a typical burn, or the
  watch's total is a product call, not a review one.

## Not a problem

- **Check-in wording.** The check-in strings (`today_checkin_*`) match design 4.1 word for word:
  "Check in", "Optional · only you see this", "Hunger through the day", "Energy", "Sleep last
  night", "Mood · pick any" and each choice. No `TODO(design-sync-2)` for it remains.
- **SEA actions.** All intents or arrivals; the select and swipe actions name gestures
  (`RowSwipedOpen`, `DeleteRequested`), not state.
- **Double taps elsewhere.** Remove on the item sheet tombstones (a repeat is a no-op); copy,
  move and remove from selection clear the selection before a repeat could act; the ⋮ menu closes
  before its writes; notes and feelings set rather than add.
- **Explorer's 180 days.** `explorerStats` reads six months of days on every diary change, but only
  in the Explorer layout and from Room; the builders are linear in items.
- **The item sheet's loading.** It returns after its header while loading, but its only read is
  one day from Room, well under a frame, and nothing waits on the network or a health store.
- **Fasting card ticker** runs only while the card can show and is driven by the injectable
  `MinuteTicker`.
- **Release flags.** `release.todaySummary`, `.todaySelect` and `.checkIn` default off and are read
  when Today or Close the day builds its inputs; nothing runs at launch. With the summary flag off
  the weight store is never read, and with `release.health` off the health store is never touched.
- **unslop-code scanner.** 131 low "verbose identifier" hits, all DI factories and framework names.

## Product notes

- **The check-in's off switch.** Design 4.1 says "a setting in Reminders turns the whole card
  off"; Close the day shows the check-in whenever `release.checkIn` is on, and Reminders has no
  such setting. Needs a top-level setting and a row in Reminders (settings slice). Sure it is
  specified; unsure how many people will want it.
- **Copying yesterday's meal has no Undo.** The meal sheet's copy writes and toasts, while the
  logger's Yesterday's chip announces through `RecentLogEvents` and gets Today's six-second Undo.
  Routing the meal sheet's copy the same way would make the two consistent. Fairly sure.
- **Water's quick + counts from the sheet only.** Adding a glass always goes through the water
  sheet; a long-press on the card's + to add one glass straight away would save a step for the
  most repeated action on the screen. Unsure.
