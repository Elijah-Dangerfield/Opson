# Today plan

The Today tab in every layout, rebuilt to **design v3** (`docs/design/v3/`,
screens 2.1 to 2.11 and 3.9; `CHANGES.md`). v3 supersedes the handoff's
Today wherever they disagree. Its menu and sheets are in `today-sheets.md`,
Day close in `day-close.md`. Read `shared.md` first.

## Design sync 2 (2026-10-26)

The owner's latest export is the spec now (`docs/plans/design-sync-2.md`). Ids:
every `today-previews__*` pair plus the unpaired `today-macros`, `today-sticky`,
`today-collapsed`, `today-select`, `today-swipe-delete`, `today-delete-confirm`.
The manifest notes are the behaviour spec. Where this section and the older
ones below disagree, this section wins.

### Flags

New behaviour sits behind three release flags (`FlagConfigValue`, default off,
in the QA menu, the admin registry and the drift test). Off renders the refined
screens below with none of the new behaviour.

| Flag | Class | What it turns on |
|---|---|---|
| `release.todaySummary` | `TodaySummaryEnabled` | The macros page, the stats row, pager dots, the collapse chevron and collapsed line, the sticky bar, long-press to customise, the stats section of Edit Today's cards, and the Weight row. |
| `release.todaySelect` | `TodaySelectEnabled` | Long-press to select foods and the bottom bar (Move, Copy to…, Delete), dragging picked foods to another meal with Undo, swipe to delete with the inline confirmation, Undo after a removal, and "Move to…" on Uncategorized. Reworked for design sync 4: see `today-actions.md`. |
| `release.foodActions` | `TodayFoodActionsEnabled` | A food row's ⋮ and the food's sheet with Change amount, Move, Copy to another day, Edit a copy and Add to favorites (design sync 4, `today-actions.md`). |
| `release.checkIn` | `CheckInEnabled` | Day close's Check in card (hunger through the day, energy, sleep, moods) in every mode, in place of Blind's "How did today feel?". |

`TodayInputs` gains `summaryOn` and `selectOn`, read per emission like
`remindersOn`. Nothing runs at launch for any of them.

### Refinements (no flag)

- **Summary cards are the surface colour** in every palette (2.1 to 2.8): Energy,
  Soft, Focus, Locked, Counted and New this month move off the hero colour onto
  `TodayCard` with ink text. The big-day reset on Day close stays on the hero.
  The surface versions live in `:libraries:diary:ui`; `HeroParts` is left alone
  because Trends uses it.
- **The eaten bar is split by macro** (2.1): protein, carbs and fat in their own
  colours by their share of the energy, end to end inside the eaten fill. A
  macro not effectively Show, and energy no macro explains, merge into one
  neutral segment. `RangeBar` gains `segments`.
- **The header is pinned** above the list, so the sticky bar can sit under it.
- **Empty morning (2.2)**: no copy-yesterday box and no Saved meals button under
  the next meal (both live in the add sheet now); the Expended so far line stays.
  Close the day hides on a day with nothing logged.
- **How it felt chips show in Mindful only.** Meal cards in the other layouts drop
  them (the daily check-in replaces them; the feeling stays stored).
- **Fuel (2.6)**: protein's label in its colour, the lead figure large with
  "of 120 g" small, meal segments in ink, a dot before Fiber.
- **Plan tomorrow (2.10)**: a planned meal has no +.
- **Meal feel sheet (3.10)**: "How was lunch?" with "Everything here is optional,
  and only you see it."; hunger shown Not hungry, A bit, Hungry, Very hungry;
  fullness Still hungry, Satisfied, Full, Too full (stored values unchanged:
  Peckish reads "A bit", Very full reads "Too full"); picked chips tonal with a
  check; Skip as big as Save.
- **Day close (4.1 to 4.5)**: the big-day card reads "Everyone has big days.
  Tomorrow is a fresh start, and you don't need to make up for today." and "Your
  range tomorrow is {range} kcal, the same as usual." with a **Why not cut back?**
  link (a short sheet; wording under TODO(clinician), no study named) and the
  support row "If eating feels hard right now, talk to someone" (opens
  `SupportRoute`, never logged), whenever the card shows, Subtle included.
  "Expended, from your watch" is a row when energy out is Show and a health app
  reported today.

### Summary card with `release.todaySummary`

`TodaySummary.Energy` gains `bar: List<BarPart>`, `stats: List<StatTile>` and
`macrosPage: MacrosPage?`. The state gains `summaryPage`, `collapsed`,
`compact: CompactSummary?` (the sticky bar per page and the collapsed line, as
`CompactPart`s: a value with its metric, a meal count, a logged count),
`weight: WeightRow?` and `summaryTools`.

| Piece | Rule |
|---|---|
| Pages | Energy only, not on an empty day: page 1 energy, page 2 macros when `todayMacrosPage` and some macro is visible. Both pages compose at once and the card is as tall as the taller (`SwipePages`, `:libraries:ui`). The page left on is saved (`SummaryPageShown`). |
| Stats row | Up to 3 `SummaryStat`s in the person's order (`todaySummaryStats`), or the suggestion from the first onboarding goal when they never chose: changing weight → Expended, Deficit so far, Meals; eating regularly → Meals, To your range, Water; eating enough or training → Protein, Meals, To your range; anything else → Expended, To your range, Meals. A stat that resolves Hidden drops out. None on an empty day. |
| Stat values | Expended: `EnergyOut`, today only. Deficit so far: expended less eaten, floor 0, only where `TargetStyle.deficitAllowed(preset)` and both energies Show. To your range: the gap to the low end through `Calories`, only with `Remaining` at Show; inside the range "On target", above it "Above range" (the policy's words). Meals: "3 of 4". Protein, carbs, fat, fiber: "51 g" or their soft word. Water: glasses "3 of 8" unless water is Hide. |
| Macros page | "Macros / of 1,245 kcal", the four macros 2×2 in `GoalStyle.Slash` with bars, then the split bar with each Show macro's share of energy ("Carbs 52%"). A macro at Hide drops its row and its share. |
| Sticky bar | Under the pinned header once the card has scrolled away. Energy page 1: "1,245 kcal · range 1,900 to 2,300" and the split bar; page 2: "P 51 · C 162 · F 44". Soft: meals and the lead macro. Focus: each nutrient's word. Locked: "3 meals logged". Everything else (Blind, Rhythm, Mindful, Explorer, revealed): "3 of 4 meals". Tap scrolls to the top. |
| Collapsed | Chevron on the card, saved in `todaySummaryCollapsed`. The card becomes one pinned line: page 1's first value then page 2's ("1,245 kcal · P 51 · C 162 · F 44"), the bar, and a chevron to expand. Other cards collapse to their sticky line. One line, ellipsised. |
| Customise | Long-press the card, or Edit Today's cards: the sheet gains a Summary card section ("Suggested from your goals: Changing my weight", Reset), page 1's stats with checkboxes and drag handles (Move up and Move down as accessibility actions), at most 3 ticked; a stat the levels don't allow shows a lock and "Hidden in Modes and numbers" and opens Modes; Deficit so far is listed only where deficits are offered. Then "Page 2 · Macros" with a switch, then the existing cards. Done closes. |
| Weight row | The day's latest weigh-in, "This morning · 7:02 AM", through `Metric.Weight` on `Surface.Today` with the 14-day slope, so Soften reads "Steady" and Hide removes the row. Opens Weight. |

Settings keys, new and top level: `todaySummaryCollapsed: Boolean = false`,
`todaySummaryPage: Int = 0`, `todaySummaryStats: List<String>? = null` (stat
wire keys; unknown ones skipped), `todayMacrosPage: Boolean = true`.

### Select and swipe with `release.todaySelect`

Design sync 4 replaced the panel below with a bottom bar and added dragging;
`today-actions.md` is current where the two disagree.

State gains `selection: Selection?` (ids, count, energy and macro totals as
`Display`s, `step`: Actions, CopyTo(day, slot), MoveTo(slot), ConfirmRemove),
`swipe: SwipeState(openRow, confirmRow)` and `removed: RemovedNotice?`.

| Piece | Rule |
|---|---|
| Start | Long-press a food: selecting, that food picked. A food then toggles; a meal header toggles all its foods. The header becomes "3 selected" with Close and Select all; meal +s hide; the tab bar hides (`TabBar`, new in `:libraries:navigation`). |
| Panel | "3 foods" and the energy total, then protein, carbs, fat, fiber, all through the policy, so Blind shows "3 foods" and the actions. Copy to…, Move, Remove. "Save as meal" is left out: Create a meal takes a slot, not a set of foods, and is Foods' module. |
| Copy to… | Yesterday, Today or Tomorrow and a meal, then Copy (`copyItems`). Toast "Copied 3 foods." |
| Move | Meal chips, Uncategorized last, then Move (`moveItems`). Moving to a time is TODO: no time picker outside Settings' module. |
| Remove | Inline "Remove 3 foods?" with Keep and Remove. Remove: `removeItems`, selection ends, the Undo bar for 6 s; Undo is `restoreItems`, which un-tombstones those rows (last write wins on the server). |
| Swipe | A food row drags left to reveal Delete. One row open at a time; opening another, scrolling or swiping right closes it. Past half goes straight to the confirmation. Delete turns the row into "Remove banana from breakfast?" with Keep and Remove. A "Delete" accessibility action on the row opens the same confirmation. Totals change only after Remove. |
| Uncategorized | Foods logged outside a usual meal already sit in the Uncategorized card. Dragging between cards of a lazy list is not something to do well here, so the card gets **Move to…**: selecting starts with its foods picked and the panel on Move. |

`DiaryItemEditor` (new, `:libraries:diary`, implemented by
`DiaryRepositoryImpl`): `removeItems`, `restoreItems`, `moveItems`,
`copyItems`. Separate so other modules' `DiaryRepository` fakes don't change.

### Day close Check in with `release.checkIn`

`Day.checkIn: CheckIn?` (hunger through the day Rarely to Very; energy Low, OK,
Good; sleep Poor, OK, Good; moods Calm, Social, Stressed, Tired, Low, Happy),
stored as JSON on the day record (Room 9 `day_records.checkIn`, server V18
`day_records.check_in`, both additive; the server checks length only), written
by `DayCheckIns.setCheckIn(day, checkIn)` on Close the day, never on Back. Shown in every mode; Blind's "How did today
feel?" steps aside while the flag is on. A row left blank stays blank. Feeding
Patterns is Trends' work and the Reminders switch for the card is Settings'
(TODOs there).

### Deliberate differences kept

- Ranges read "1,900 to 2,300" (the brief's "to" rule); the collapsed line drops
  "range …" so it stays on one line (the design's own mock wraps).
- Times read "8:10 AM" (`DateCopy`, shared by every screen; changing it is a
  cross-screen call, not this module's).
- Nutrient focus words are the policy's ("Getting there"), not "Partway" or
  "Over halfway"; the formatter is shared.
- No "+ Add" on Safe foods, no photo or voice note on Mindful, no "Usual
  breakfast" on a planned meal, no "from your scale": none has a domain yet.
- No ⋮ in the select header: it would hold nothing that is built.
- Deficit goals ("Goal about 1,870") and by-day names ("Training day") on the
  card are Settings' targets work and are not drawn here yet.
- How it felt keeps the stored moods: "Tired" is not one (the check-in has it),
  and Anxious and Low stay after the design's five.
- No "Save as meal" in the select panel and no time in Move (TODOs in
  `TodaySelectViews.kt`): Create a meal takes a slot and is Foods' module, and
  the only time picker is Settings'.
- The snack tile keeps its leaf: Material has no apple, the design's glyph is a
  Symbols-only one.
- The sticky bar's tap scrolls to the top inside the screen; it is UI state, not
  an action.

### Leak test

New entries in `TodayLeakSurfaces`, built through the real builder and run in
every preset: `today-sticky` (the bar for both pages), `today-collapsed`,
`today-macros`, `today-select`, `today-select-remove`, `today-swipe-delete`,
`today-weight`, `day-close-check-in`. Added to `REQUIRED_SURFACES`.

### Tests

- `TodayStateBuilderTest`: the split bar (a hidden macro merges), stats (each goal's
  suggestion, chosen order, hidden drops, deficit only where allowed), the macros
  page, compact lines per layout and level, the collapsed line, weight at Show,
  Soften and Hide, selection totals in Full and Blind, flags off.
- `TodayViewModelTest`: each new action, including Undo after a removal, page and
  collapsed saved to settings, one open row at a time, scroll closing it.
- `TodayCardsTest`: toggle, the limit of 3, move, reset, a locked stat opens Modes,
  the page 2 switch.
- `DayCloseTest`: the check-in prefills once, saves on Close and not on Back, and
  is absent with the flag off; the support and why-not rows only with the big-day card.

## Source
- Design v3: `today-full`, `today-empty`, `today-delayed-locked`, `today-blind`, `today-nutrient-focus`, `today-fuel`, `today-rhythm`, `today-explorer`, `today-mindful`, `today-plan-tomorrow`, `today-day-menu`, `today-added`, and the manifest notes for each.
- Handoff: `README.md` section 2 (safety) and 5 (tokens). The handoff's sticky hero, ring pager and header + are gone in v3.

## What changed from the first plan (v3)

| Was | Now |
|---|---|
| Sticky hero with header, 3-page pager, rings, ring-mode toggle, scores page | A plain header on the page colour, then **one** hero card chosen by layout and calorie level. No pager, no rings, no Remaining/Balance, no scores (cut in v3), no "Turn on". |
| Header check button and + (Add menu) | Neither. Close the day is a labelled card after the meals and a ⋮ item. Adding is the nav + or a meal's +. |
| Meal cards with "Nothing logged" | Empty meals collapse to one row; the next one offers yesterday's version (Copy) or Saved meals. |
| Mindful and Coast banners | Gone (Coast's moved to Trends). Sprint's stays. |
| Up to six days ahead | One day ahead: "Plan, then hide". |
| Locked card alone | Counts stay visible: "3 meals logged", "3 glasses". At 9 pm, "Your totals are open" links to Day close. |
| Water row in every layout but Explorer | Only with the amount on show; locked shows the glasses count instead. |
| Day menu flat list with Hide water | Grouped; Hide water lives in Edit Today's cards (`today-sheets.md`). |
| (new) | After food lands on the day, its meal is outlined for 2 s and scrolled to. |

Not built, and why: **Expended so far** (0.2, watch data; now built behind
`release.health`, see `integrations.md`). **Safe foods "+ Add"**
(no way to mark a safe food in the domain). **Dinner reminder** card on Rhythm
(Reminders, 0.2). **Photo and voice** on Mindful (0.2).

### Added since (v3 follow-up)

- **Undo for 6 s after adding (3.9).** The logger no longer toasts; it publishes
  `LoggedToMeal(day, slot, itemIds, at)` on `RecentLogEvents.latest`. Today
  collects it: for the day it shows and while `undoOpenAt(clock.now())`, it
  outlines and scrolls to the meal, sets `added = AddedNotice(slot, count)`
  ("Added 2 foods to dinner" with **Undo**), calls `consumed`, and clears the
  notice when the six seconds run out (the time left, not a fresh six). Undo is
  `RecentLogEvents.undo(event)`, which removes exactly those ids. An event for
  another day is left for that day; an expired one is consumed and shown
  nothing. Quiet: no sound or vibration. The bar is drawn by Today, not the
  app snackbar, so it lasts exactly the window and shows in previews.
- **Mark as untracked (2.11).** `Day.untracked`, written by
  `DiaryRepository.setUntracked(day, untracked)` (`day_records.untracked`,
  synced). The menu item reads "Mark as untracked" or "Track this day again".
  An untracked day keeps its meals but swaps the hero card (and the counts,
  the planning banner and Close the day) for one calm `untracked` card: "Not
  tracking this day / Trends leaves a gap here, not a low day." with "Track it
  again". Trends treats it as a gap.
- **Food bridge (2.8).** Explorer only. `FoodBridges.suggest(frequent, days,
  today)` (pure) picks a food the person eats often and one they have had only
  once or twice that shares a word with it ("A small step from rice / You've
  had rice pudding before, and it has rice in it too."). Their own foods only,
  no outside data; no card when nothing pairs. "I tried it" opens the logger
  with that food picked; "Not today" hides it until tomorrow. Neither records
  anything (`bridgeSkippedOn` lives in the view model only), so "Not today"
  never counts against anyone.

## Route
`TodayRoute : TabRoute()` in `:features:today`, registered with `screen<TodayRoute>` in `TodayFeatureEntryPoint`. No args; the day comes from `SelectedDay`.

## State

```kotlin
data class TodayState(
    val loading: Boolean = true,
    val day: LocalDate,
    val dayLabel: RelativeDay,
    val canGoForward: Boolean,          // day < today + 1
    val summary: TodaySummary?,         // the hero card, or none
    val banner: TodayBanner?,           // Sprint only
    val planning: Boolean,              // a day ahead
    val counts: Counts?,                // with Locked: meals logged, glasses (null when water is Hide)
    val mealTiles: List<MealTile>?,     // Meals layout (Blind, Coast)
    val rhythm: RhythmCard?,            // Rhythm layout
    val safeFoods: List<String>?,       // Explorer layout
    val water: WaterRow?,               // Full layout, water Shown, not locked
    val meals: List<MealEntry>,
    val whatYouHad: WhatYouHad?,        // PerMeal (Meals layout), Together (Rhythm)
    val closeDay: CloseDayCard?,        // not on a planned day
    val highlight: MealSlot?,           // the meal that just changed
    val added: AddedNotice?,            // "Added 2 foods to dinner" + Undo, 6 s
    val untracked: Boolean,             // the calm untracked card instead of the hero
    val bridge: FoodBridgeCard?,        // Explorer's food bridge
    val menu: DayMenu?,
)
sealed interface TodaySummary {
    data class Energy(eaten: Display, empty: Boolean, range: Display, rangeStart: Float, rangeEnd: Float, macros: List<MacroTile>)
    data class Soft(mealCount: MealCount?, lead: MacroTile?, rows: List<MacroTile>, energy: Display)
    data class Focus(rows: List<MacroTile>)
    data class Locked(title: StringResource, body: StringResource)
    data object Revealed
    data class Counted(done: Int, next: MealSlot?, nextAround: LocalTime?)
    data class NewFoods(foods: List<NewFood>)
}
sealed interface MealEntry {   // keyed by slot
    data class Full(card: MealCard)                                    // header, items, feel chips, note
    data class Collapsed(slot, around, usual, yesterday: Yesterday?, offerSaved)
    data class Compact(slot, title, time, foods: String, feel: FeelLine?, askFeel)   // Explorer, Mindful
    data class Planned(slot, calories: Display, foods: String)          // a day ahead
    data class NotPlanned(slot)
}
```

`TodayStateBuilder.build(inputs, policy, menuOpen, clearArmed, highlight, added, bridgeSkipped)`, pure.
`TodayInputs(today, timeZone, day, dayBefore, settings, explorer)`.

### Which hero card

Layout is `policy.preset.today`: Full, Meals, Rhythm, Explorer, and **Journal**
(new, Mindful's). No branch reads `PresetId` except Sprint's banner copy.

| Condition (in order) | Card |
|---|---|
| a day ahead | none (the planning banner instead) |
| layout Explorer | `NewFoods` ("New this month") |
| layout Rhythm or Journal | none |
| calories effectively Show, level Delay, day is today | `Revealed` (links to Day close) |
| calories effectively Show | `Energy`: eaten (`of` = max × 1.2 so its fraction draws the bar), range via `resolveRange` as a dashed box at min/max ÷ (max × 1.2), protein/carbs/fat in `GoalStyle.Slash`; `empty` on a day with nothing logged hides the macros and says "Nothing yet" |
| level Delay (not open) | `Locked` (day or week copy by `revealOn`, warm body by tone) |
| calories Soften | `Soft`: meals counted when `preset.mealCount`, lead = first macro at Show (`GoalStyle.Of`), the other visible macros as rows, energy as the soft word |
| some macro not Hidden | `Focus` |
| otherwise | `Counted`: "Three meals", next meal "usually around 7:00" |

### The rest

| Field | Rule |
|---|---|
| `mealTiles` | layout Meals, groups on. Done tiles tinted with a check; open ones dashed with "Around 7:00". |
| `rhythm` | layout Rhythm, groups on. Actions ("Already ate", "Log it") only on the first open meal. |
| `water` | layout Full, water display `Shown`, not locked. |
| `meals` | Full: each meal with items or ate-no-details is `Full` (compact macro line when all three macros Show, else the long form of the ones that do; items bare numbers), empty ones `Collapsed`; Uncategorized `Full` when it has items; groups off is one "All food" card. Meals and Rhythm: none. Explorer and Journal: logged meals `Compact` (Journal carries how it felt, or asks), empty ones `Collapsed`. A day ahead: `Planned` / `NotPlanned`. |
| `Collapsed.usual` | the first meal not done; it alone gets yesterday's version (`dayBefore.items(slot)`, energy policy-resolved) or, with no yesterday, Saved meals. |
| `closeDay` | every day but a planned one; `closed` from `Day.isClosed(today)`. |
| `menu` | see `today-sheets.md`. |

## Actions

| Action | Does |
|---|---|
| `InputsArrived` / `PolicyArrived` | Rebuild. New item ids on the same date set `highlight` to the newest one's slot and clear it after 2 s (`HighlightFaded`). |
| `PreviousDayTapped` / `NextDayTapped` | `SelectedDay.select(day ∓ 1)`; next only up to today + 1. |
| `DayLabelTapped`, `CalendarTapped` | `OpenCalendar(day)`. |
| `NutrientsTapped` | `OpenNutrients(day)` (`NutrientsRoute(dayIso, Day)`). |
| `RevealedTapped`, `CloseDayCardTapped` | `OpenDayClose(day)`, not on a planned day. |
| `MealTileTapped`, `RhythmLogTapped`, `PlanItTapped` | `OpenLogger(day, slot)`. |
| `MealPlusTapped` | `OpenMealSheet(day, slot)`. |
| `CopyYesterdayMealTapped(slot)` | `copySlot(day − 1, day, slot)`, toast "Added to {meal}." |
| `SavedMealsTapped(slot)` | `OpenLog(day, slot, Saved)`. |
| `FeelPromptTapped(slot)` | `OpenMealFeel(day, slot)`. |
| `ItemTapped(entryId)` | `OpenItemSheet(day, entryId)`. |
| `WaterPlusTapped` | `OpenWaterSheet(day)`. |
| `AlreadyAteTapped(slot)` | `setAteNoDetails(day, slot, true)`. |
| `LoggedArrived(event)` | From `RecentLogEvents.latest`: notice, outline, scroll, `consumed`; `UndoExpired` when the window ends. |
| `UndoTapped` | `RecentLogEvents.undo(event)`, notice cleared. |
| `UntrackedMenuTapped` / `TrackAgainTapped` | `setUntracked(day, !untracked)` / `setUntracked(day, false)`. |
| `BridgeTriedTapped` | `OpenLogger` with the bridge food picked; card hidden for the day. |
| `BridgeNotTodayTapped` | Card hidden for the day. Nothing written. |
| Menu actions | `today-sheets.md`. |

## Copy
`strings_today.xml`, `today_` prefix. v3 copy verbatim: "Eaten", "Your range",
"Nothing yet", "Nutrients", "Your focus today", "Totals open at 9:00 pm",
"Log as you go. There's nothing to check until then.", "So far today",
"Three meals", "{Meal} is usually around 7:00.", "3 meals logged", "3 glasses",
"New this month", "3 foods", "Meals today", "planned around 8:00",
"Already ate", "Log it", "What you had", "Usually around 8:00 AM",
"Around 12:30 PM", "Yesterday's breakfast", "Copy", "Saved meals",
"How did it feel?", "Planned meals count as logged", "Edit anything tomorrow
if plans change.", "Planned", "Not planned", "Plan it", "Close the day",
"Look back and add a note. It takes about a minute." New: "Your totals are
open / See them in Close the day. They lock again tomorrow morning.",
"Day closed / Look back, or change the note."

Known differences from the images, all from the policy's one formatter (shared
by every screen, so not changed here): ranges read "1,900 to 2,300" not
"1,900–2,300"; macros "51 / 120 g" and "51 of 120 g" in one style; times
"8:10 AM" (`DateCopy`); water "750 of 2,000 ml" without the glasses count;
Nutrient focus words are the policy's ("Building", "Getting there").

## Visibility surfaces

Surface `Today`. Every number is a `Display` from the builder and renders
through `NutrientText` / `NutrientBar` / `MacroLine`; the eaten bar is tagged
`metric:calories` and exists only with its `Display`.

| Number | Show | Soften | Delay | Hide |
|---|---|---|---|---|
| Eaten | Energy card | Soft card word | Locked, counts only | Counted or Focus |
| Range | dashed box and text | none | none | none |
| Macros | "51 / 120 g" with bar | word with bar (Soft, Focus) | none | none |
| Meal energy, item energy | "445 kcal", "300" | none | none | none |
| Macro line | "13P 80C 8F" | part omitted | omitted | omitted |
| Water | card | none (Locked shows a glasses count) | none | none |
| Meal and food counts | always | always | always | always |

## Tests
- `TodayStateBuilderTest`: each layout and level (Full, empty day, big day, Delayed at noon / 21:00 / past, Coast, warm tone, Blind, Nutrient, Fuel, Rhythm, Explorer, Mindful, Sprint), collapsed meals and yesterday, compact vs long macro line, groups off, planning, past day, name-only meal, ate no details, the grouped menu, hidden water.
- `TodayViewModelTest`: one per action, day bounds (one ahead), the outline on new food and its fade, copying, the two-tap clear.
- Previews in `TodayPreviews.kt` named after the v3 screen ids, one theme each at 390.
- `FoodBridgesTest`: pairs on a shared word, prefers rarer and more recent, skips today's and quick numbers, none when nothing pairs.
- `TodayLeakSurfaces`: `today`, `today-big-day`, `today-added` (with the Undo bar), `today-untracked`, `today-explorer-bridge`, `today-groups-off`, `today-past`, `today-future`, `today-day-menu`, `today-day-menu-armed`, `today-cards`.
