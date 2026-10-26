# Today plan

The Today tab in all four layouts. Its menus and sheets are in
`today-sheets.md`, Day close in `day-close.md`. Read `shared.md` first.

## Source
- Handoff: `README.md` section 4.1, section 2 (safety), section 5 (tokens).
- Prototype markup: `Phone.dc.html` lines 88 to 300, the `is.today` block.
- Logic: `phone-logic.js` `renderVals()` keys `td`, `sw`, `meals`, `lo`, `mealTiles`, `rhythm`, `newFoods`, `safeFoods`, `wt`, `day`, `banner`, `emptyText`, `macroText`. Policy: `nv`, `revealed`, `soften`.

## Route
`TodayRoute : TabRoute()` in `:features:today` (`com.opson.features.today`), registered with `screen<TodayRoute>` in `TodayFeatureEntryPoint` (`:features:today:impl`, `com.opson.features.today.impl`). No args; the day comes from `SelectedDay`.

## State

```kotlin
// com.opson.features.today.impl
data class TodayState(
    val loading: Boolean = true,
    val day: LocalDate,
    val dayLabel: RelativeDay,                 // Today | Yesterday | Tomorrow | Date(day)
    val canGoForward: Boolean,                 // day < today + 6
    val closeButton: CloseButton,              // Hidden (future day) | Open | Closed
    val summary: SummaryCard?,                 // sticky block; only when calories are effectively Show
    val banner: TodayBanner?,
    val lockedCard: LockedCard?,
    val softCard: SoftCard?,
    val mealTiles: List<MealTile>?,
    val macrosCard: List<MacroTile>?,
    val rhythm: List<RhythmRow>?,
    val explorer: ExplorerCard?,
    val water: WaterRow?,
    val uncategorized: MealCard?,
    val meals: List<MealCard>,
    val emptyText: StringResource,             // tone and future/past resolved
    val menu: TodayMenu?,                      // see today-sheets.md
)

data class SummaryCard(
    val rings: List<SummaryRing>,              // Eaten, then the third ring (Expended is 0.2)
    val ringMode: SummaryRingMode,
    val canCycleRingMode: Boolean,             // availableRingModes(...).size > 1
    val macros: List<MacroTile>,               // page 2, GoalStyle.None, fraction from of = goal
    val scores: ScoresPage,                    // page 3
)
data class SummaryRing(val metric: Metric, val label: StringResource, val value: RingValue, val arcs: List<RingSegment>)
data class RingSegment(val color: RingColor, val fraction: Float)   // RingColor { Protein, Carbs, Fat, OnHero }
data class MacroTile(val metric: Metric, val label: StringResource, val display: Display)
sealed interface ScoresPage {
    data class On(val rows: List<ScoreRow>) : ScoresPage                // first four scores
    data class Off(val canTurnOn: Boolean) : ScoresPage                 // canTurnOn: level(Micros) == Hide
}
data class ScoreRow(val label: StringResource, val display: Display, val emphasis: Boolean)

data class TodayBanner(val text: StringResource, val args: List<Any> = emptyList())
data class LockedCard(val title: StringResource, val body: StringResource)
data class SoftCard(val label: StringResource, val eaten: Display, val mealCount: MealCount?)
data class MealCount(val done: Int, val total: Int)
data class MealTile(val slot: MealSlot, val status: TileStatus)       // Empty | Items(count, time) | AteNoDetails
data class RhythmRow(val slot: MealSlot, val status: RhythmStatus)    // Pending(around: LocalTime) | Logged(time) | AteNoDetails
data class ExplorerCard(val safeFoods: List<String>, val newFoods: List<NewFood>)
data class NewFood(val name: String, val firstLogged: RelativeDay)
data class WaterRow(val display: Display, val filled: Int, val total: Int)   // filled/total from WaterProgress
data class MealCard(
    val slot: MealSlot,
    val title: StringResource,                 // slot name, or "All food" when groups are off
    val time: LocalTime?,                      // first item's logged time, null when empty
    val calories: Display,                     // meal total, Hidden when empty
    val macros: List<MacroPart>,               // macroLine(Long), Show only
    val items: List<ItemRowUi>,
    val ateNoDetails: Boolean,
    val feel: List<StringResource>,            // hunger, fullness, moods
    val note: String?,
)
data class ItemRowUi(val entryId: String, val name: String, val amount: String, val precision: Precision, val calories: Display)
```

`TodayStateBuilder.build(inputs: TodayInputs, policy: VisibilityPolicy, menu: MenuKind?, clearArmed: Boolean): TodayState`, pure.

```kotlin
data class TodayInputs(
    val today: LocalDate,
    val timeZone: TimeZone,        // LocalCalendar.timeZone(), for item times
    val day: Day,                  // DiaryRepository.observeDay(selected)
    val settings: Settings,
    val explorer: ExplorerStats?,  // only collected when the layout is Explorer
)
data class ExplorerStats(val safeFoods: List<String>, val newFoods: List<NewFoodSeen>)   // ExplorerStats.from(frequent, days, today)
```

`Settings.ringMode` is the diary's `RingMode`; the builder maps it to
`SummaryRingMode` by name and `RingModeTapped` maps back.

Every `resolve` call passes `FormatContext(day = day.date, dayClosed = day.isClosed(today))`.

### Which card renders

| State field | Rule (port of `td`) |
|---|---|
| `summary` | calories effectively `Show` on `Today` |
| `softCard` | calories resolve to `Soft` |
| `lockedCard` | `level(Calories) == Delay` and not revealed. Title/body by `preset.revealOn` |
| `mealTiles` | calories `Hidden` (locked counts) and layout is not Rhythm or Explorer |
| `macrosCard` | some macro is not `Hidden` and `summary` is null |
| `rhythm` | layout Rhythm |
| `explorer` | layout Explorer |
| `meals`, `uncategorized` | layout is not Rhythm. `uncategorized` when it has items, or always (as "All food" holding every item) when meal groups are off; `meals` empty when groups are off |
| `water` | water not `Hidden` and layout is not Explorer |
| `banner` | `preset.banner` |
| Expended ring, energy card, Health card, fasting card | 0.2, not built |

No branch reads `PresetId`. Layout comes from `policy.preset.today`, the rest from levels.

### Rings (page 1)
- **Eaten**: `ringValue(Calories, eaten, bare)`. Arcs: one full ring when eaten > 0, split by protein × 4, carbs × 4, fat × 9 only when all three macros are effectively `Show`; otherwise one `OnHero` arc.
- **Third ring** by `ringMode` (falls back to Target when the saved mode is no longer available):
  - Target: `ringValue(Calories, target)`, arc `min(1, eaten / target)`.
  - Remaining: `ringValue(Remaining, target − eaten, eaten, min, max)`, arc `1 − eaten / target` clamped.
- `target`, `min`, `max` come from `settings.targets.today(settings.exercise, 0, 0)` in 0.1 (no watch data).

## Actions

| Action | Does |
|---|---|
| `InputsArrived(inputs, policy)` | Rebuilds state through the builder, keeping `menu` and `clearArmed`. |
| `PreviousDayTapped` | `SelectedDay.select(day − 1)`. Clears `menu`. |
| `NextDayTapped` | `SelectedDay.select(day + 1)` when `canGoForward`, else nothing. |
| `DayLabelTapped` | Event `OpenCalendar(day)`. |
| `CloseDayTapped` | Event `OpenDayClose(day)`. Ignored when `closeButton == Hidden`. |
| `SummaryTitleTapped`, `MacrosCardTapped` | Event `OpenNutrients(day, Day)`. |
| `RingModeTapped` | Next mode in `availableRingModes`, saved to `settings.ringMode`. |
| `TurnOnScoresTapped` | `NumbersChanger.request(Override(Micros, Show))`; `NeedsPause` emits `OpenPause(change)`, `Applied` emits the matching toast. |
| `MealTileTapped(slot)` | Event `OpenLogger(day, slot)`. |
| `MealPlusTapped(slot)` | Event `OpenMealSheet(day, slot)`. Uncategorized card uses the same action. |
| `ItemTapped(entryId)` | Event `OpenItemSheet(day, entryId)` (the repository has no lookup by id, so the sheet reads the entry from its day). |
| `WaterPlusTapped` | Event `OpenWaterSheet(day)`. |
| `AlreadyAteTapped(slot)` | `DiaryRepository.setAteNoDetails(day, slot, ate = true)`. |
| `RhythmLogTapped(slot)` | Event `OpenLogger(day, slot)`. |
| `LogSomethingNewTapped` | Event `OpenLogger(day, slot = null)` with search focused. |
| Menu actions | Listed in `today-sheets.md`. |

## Events
`OpenCalendar(day)`, `OpenDayClose(day)`, `OpenNutrients(day, range)`,
`OpenLogger(day, slot?, focusSearch)` (`LoggerRoute(slot, dayIso, focusSearch)`), `OpenLog(day, slot, tab)` (`LogRoute`), `OpenWeight`,
`OpenDayNote(day)`, `OpenMealSheet(day, slot)`, `OpenItemSheet(day, entryId)`,
`OpenWaterSheet(day)`, `OpenPause(change)`, `ShowToast(message, args)`.
No `TabReselected` / `ScrollToTop` in 0.1: the shell's `Router` has no reselect
signal yet (`shell.md` lists it; it was not ported). Add both when it lands. The entry point maps each to a route in the owning feature's
api (logger, trends, settings, today itself). `NutrientsRoute(dayIso, range)`
and `NutrientsRange` did not exist yet, so this work adds them to `:features:trends`
exactly as `trends.md` specifies; Trends registers the screen.

## Data

| Read | Flow |
|---|---|
| `SelectedDay` then `DiaryRepository.observeDay(day)` | `flatMapLatest`, so a day change cancels the old day |
| `SettingsRepository.observe()` | settings |
| `VisibilityPolicySource.policy` | re-emits at 9 pm, so Delayed opens while the screen is up |
| `DiaryRepository.frequentFoods(limit = 6, days = 30)` and `observeDays(today − 180, today)` | Explorer only. Safe foods are the six most logged in 30 days; a new food is one whose first entry in the last 180 days falls in this month (`ExplorerStats.from`, pure and tested). There is no food-stats query, and 180 days keeps the scan small. |

All combined into `TodayInputs`, sent as `InputsArrived`. Writes:
`SelectedDay.select`, `SettingsRepository.update` (ring mode, meal groups),
`NumbersChanger.request`, and the diary writes in `today-sheets.md`.
Offline: everything is Room, so nothing changes; writes queue in the outbox.

## Components
Every size in the handoff lives in a library, not in the feature: the hero
header, hero buttons, locked card, meal tiles, checklist rows and sheet menu
rows go in `:libraries:ui`; the pieces that draw a `Display` or `RingValue`
(summary rings and cells, the macros card row, the water card, the meal card
header, the soft card) go in `:libraries:diary:ui` (`TodayCards.kt`).

- `:libraries:ui`: `HeroBlock` (sticky, bottom radius 24, shadow), `EdgeCard`, `IconTile`, `ProgressRing(segments)`, `PagerDots`, `SegmentedBar` (water), `FillBar` (macro bars, 5 dp), `Banner`, `MenuPopover`, icon buttons at 40 dp (header and summary buttons are 40 by design), `RelativeDay`.
- `:libraries:nutrition:ui`: `NutrientText`, `NutrientBar`, `NutrientVisible`, `NutrientRingLabel`, `MacroLine`.
- `:libraries:diary:ui`: `MealSlot` icon and tints, `DiaryItemRow`, feel chip labels.
- Summary pager: `HorizontalPager` with 3 pages; page index is UI state (title, dots and arrows read `PagerState`), not ViewModel state.

## Visibility surfaces

Surface `Today` for everything on this screen.

| Number | Metric | Show | Soften | Delay (locked) | Hide |
|---|---|---|---|---|---|
| Eaten ring | calories | `1,245` / `kcal` | no ring; soft card word | no pager; locked card | no pager |
| Target ring | calories | `2,100` / `kcal` | none | none | none |
| Remaining ring | remaining | `1,060` / `kcal`, `On` / `target`, `Past` / `range` | not offered | not offered | not offered |
| Soft card | calories | n/a | "Early in the day" ... "A bigger day" | n/a | n/a |
| Macro grid (page 2), macros card | protein, carbs, fat, fiber | `47 g` with bar (grid), `47 g of 120 g` (card) | "Building / Getting there / Plenty today" with bar | hidden, label too | hidden, label too |
| Meal macro line | protein, carbs, fat | `47 g protein` part | part omitted | omitted | omitted |
| Meal header total, item kcal | calories | `445 kcal` | hidden | hidden | hidden |
| Scores (page 3) | micros (summed from each item's `micros` map through `Micronutrient.itemKey`) | `62%` | "Some / Most / Plenty" | "Nutrition scores are off" | "Nutrition scores are off" + Turn on |
| Water row | water | `750 of 2,000 ml` and segments | segments only, no text | segments hidden | row hidden |
| Meal count | none (a count) | `3 of 4` on the soft card when `preset.mealCount` | same | same | same |

Delayed past days and after 9 pm resolve to Show. Coast reveals only on Week, so
Today stays locked.

## Copy

| Key | String |
|---|---|
| `today_label_today` / `_yesterday` / `_tomorrow` | Today / Yesterday / Tomorrow (other days `Thu, Oct 22` from `RelativeDay`) |
| `today_close_day_cd` / `today_day_closed_cd` | Close the day / Day closed |
| `today_prev_day_cd` / `today_pick_day_cd` / `today_next_day_cd` | Previous day / Pick a day / Next day |
| `today_add_cd` / `today_day_menu_cd` | Add / More for this day |
| `today_summary_energy` / `_macros` / `_scores` | Energy summary / Macros / Nutrition scores (rendered upper case, 0.75em, 800, 0.08em tracking) |
| `today_ring_mode_cd` | Switch the third ring |
| `today_ring_mode_target` / `_remaining` / `_balance` | Target / Remaining / Balance |
| `today_ring_eaten` / `_expended` / `_target` / `_remaining` | Eaten / Expended / Target / Remaining |
| `today_scores_off_title` / `_body` / `_turn_on` | Nutrition scores are off / Built from vitamins and minerals. / Turn on |
| `today_score_all` / `_vitamins` / `_minerals` / `_bone` | All targets / Vitamins / Minerals / Bone health |
| `today_summary_prev_cd` / `_next_cd` / `_page_cd` | Previous summary / Next summary / Summary page %d |
| `today_banner_mindful` | Tag how meals felt when you add them. No nutrition data in this mode. |
| `today_banner_sprint` | Sprint, day %1$d of 14. Tracking takes a break on %2$s. |
| `today_banner_sprint_done` (new) | Sprint is done. Pick what's next in Modes and numbers. |
| `today_banner_coast` | Your weekly check is on %s evening. (the day before `weekStart`; Sunday by default, matching the prototype) |
| `today_locked_day_title` / `_week_title` | Open at 9:00 pm / Saved for your weekly check |
| `today_locked_day_body` / `_body_warm` | Keep logging. Totals unlock tonight. / Keep logging as you go. Nothing to check until tonight. |
| `today_locked_week_body` | Log as much or as little as you like. Totals show up once a week. |
| `today_soft_label` / `today_soft_label_other_day` (new) | Today so far / That day |
| `today_meals_count_label` / `_value` | Meals / %1$d of %2$d |
| `today_tile_empty` / `_items_one` / `_items_other` / `_ate` | Tap to add / %1$d item · %2$s / %1$d items · %2$s / Ate, no details |
| `today_nutrients_cd` | Nutrients for this day |
| `today_rhythm_already_ate` / `_log` / `_logged` / `_around` | Already ate / Log / Logged %s / around %s (defaults 8:00, 12:30, 3:30, 7:00) |
| `today_explorer_safe` / `_new` / `_log_new` | Safe foods / New foods this month / Log something new |
| `today_explorer_safe_empty` / `_new_empty` (new) | Foods you log often show up here. / Nothing new yet this month. |
| `today_water` / `today_water_cd` | Water / Water options |
| `today_add_to_cd` | Add to %s |
| `today_all_food` / `today_uncategorized` | All food / Uncategorized |
| `today_empty_plain` / `_warm` | Nothing logged. / Nothing here yet. Add it whenever. |
| `today_empty_future_plain` / `_warm` | Nothing planned. / Plan it now and the app stays quiet later. |
| `today_item_sub` | %1$s · %2$s (amount · Weighed/Estimate/Named; just the precision when amount is blank) |
| Toasts | `Saved. Fewer numbers from now on.` / `Saved.` (from `NumbersChanger`, see `settings.md`) |

Not built in 0.1: "Change meal times" (Rhythm, links to Reminders), "Expended today, from your watch", the Health card copy, the fasting card copy.

## Edge cases
- **Empty day**: no summary arcs (eaten 0 draws an empty track), every meal card shows `emptyText`.
- **Future day** (up to +6): close button hidden, empty text is the "planned" pair, logging allowed (planning ahead), Next disabled at +6.
- **Past day**: close button shows Closed; Delayed numbers revealed (`dayClosed = true`).
- **9 pm while open**: the policy source re-emits and the locked card turns into the summary.
- **Meal groups off**: one "All food" card holds every item; meal tiles, rhythm and meal cards for the four slots are not built; logging goes to Uncategorized.
- **Name-only items** (`kcal` null): the row shows no calories; the meal total adds only known values.
- **Ate, no details**: the tile says "Ate, no details"; the card shows that line instead of `emptyText` (prototype shows the empty line; this is clearer and adds no number).
- **Rhythm with overrides showing calories**: the summary renders above the checklist; levels decide, not the preset.
- **Sprint with no `sprintStartedOn`**: treat the `changedAt` date, then today, as the start.
- **Large text** (19 sp): header keeps the 1fr auto 1fr grid with the label ellipsized; rings stay 62 dp with the value auto-sized down one step.
- **Offline**: no difference.

## Tests
- `TodayStateBuilderTest` (pure, the bulk):
  - one test per preset at noon: Full, Delayed, Blind, Nutrient, Rhythm, Explorer, Mindful, Fuel, Sprint, Coast; assert which cards are non-null.
  - Delayed at 21:00 and on a past day: summary present.
  - Coast at 21:00: still locked.
  - Ring split only when all three macros Show; override protein Hide gives one arc.
  - Ring modes: Remaining offered only when Remaining is Show; a saved Remaining falls back to Target after Remaining is hidden.
  - Groups off: `uncategorized` titled All food with every item, `meals` empty.
  - Future and past day close button and empty text; tone Warm picks warm keys.
  - Water Soften: segments with `Display.Soft(text = null)`; Explorer: no water row.
  - Name-only item: Hidden calories, meal total excludes it.
- `TodayViewModelTest` (fakes for `DiaryRepository`, `SettingsRepository`, `VisibilityPolicySource`, `NumbersChanger`, `SelectedDay`), one per action in the table: day navigation bounds, `CloseDayTapped` ignored on a future day, `RingModeTapped` cycles and persists, `TurnOnScoresTapped` emits `OpenPause` when pause is on and a toast when off, tile/plus/item/water/rhythm events, `AlreadyAteTapped` writes.
- Domain tests in `:libraries:nutrition` for `Micronutrient.itemKey` (`vitamin_a_rae_ug`, `folate_dfe_ug` and the rest of the catalog's keys) and `NutritionScores.percentsOfItems` (new), `ringValue`, `macroLine`, `availableRingModes`, `NutritionScores`, `WaterProgress` (see `shared.md`).
- Previews (`org.jetbrains.compose.ui.tooling.preview.Preview`, from `TodayFixtures`): Full populated, Full big day, Full empty, Delayed locked, Delayed revealed, Blind, Nutrient focus, Rhythm, Explorer, Mindful (banner), Fuel (soft card with meal count), Coast, groups off, future day, dark, large text.
- `SURFACES` entries (`TodayLeakSurfaces`): `today`, `today-big-day`, `today-groups-off`, `today-past`, `today-future`. The layout comes from the policy, so the leak test's calories-hidden presets (Blind, Nutrient, Rhythm, Explorer, Mindful) cover the Meals, Rhythm and Explorer layouts with no extra fixtures. All but `today-future` set `showsCaloriesInFull = true`.
