# Logger plan

The center + sheet, the Amount sheet, and the Log screen (Describe and Saved
tabs). Read `shared.md` first.

## Source
- Handoff: `README.md` sections 4.3 and 4.4; `COMPETITIVE_REVIEW.md` "What changed".
- Prototype markup: `Phone.dc.html` `sheet.quick` (line 1002), `sheet.add` (858), `is.log` (304 to 377).
- Logic: `phone-logic.js` `lg`, `lgSum`, `foodRow`, `macroLine`, `quickActions`, `af`, `quick`, `saved`, `addItems`.

## Routes

All in `:features:logger`, package `com.opson.features.logger`. Impl in
`:features:logger:impl`, `com.opson.features.logger.impl`.

| Route | Kind |
|---|---|
| `LoggerRoute(slot: MealSlot? = null, dayIso: String? = null, focusSearch: Boolean = false, prePickFoodId: String? = null, prePickIsCustom: Boolean = false, prePickSavedMealId: String? = null)` | `bottomSheet<>` with `BottomSheet(heightFraction = 0.93f)`, typeMap for `MealSlot` |
| `AmountSheetRoute(foodId: String, isCustom: Boolean)` | `bottomSheet<>` over the Logger |
| `LogRoute(dayIso: String, slot: MealSlot, tab: LogTab, focusNumber: Boolean = false)` | `screen<>`, typeMap for `MealSlot` and `LogTab` |

`@Serializable enum class LogTab { Describe, Saved }`. Search and Photo tabs are
not built: search lives in the Logger, Photo is Later.

`dayIso == null` means the day Today is showing (`SelectedDay.value` read once
at open). `slot == null` means `MealSlot.defaultFor(now)`, or Uncategorized with
meal groups off.

## Basket

`BasketStore` (`@SingleIn(AppScope)`, logger impl) holds
`StateFlow<List<BasketItem>>` so the Logger and the Amount sheet share it
without a nav result. It survives a dismissed Logger (picks are not lost to a
stray swipe) and clears on commit and on user change (`UserScopedData`).

```kotlin
data class BasketItem(
    val ref: FoodRef,                      // :libraries:foods, Catalog(id) | Custom(id)
    val name: String,
    val amountText: String,                // "1 medium", "150 g"
    val quantity: Double, val unit: String, val grams: Double,
    val precision: Precision,
    val nutrients: NutrientAmounts,
    val micros: Map<Micronutrient, Double>,
    val hunger: Hunger? = null, val fullness: Fullness? = null,
    val method: LogMethod,                 // Search | Favorite | Usual | Recent
)
```

---

## Logger sheet

### State

```kotlin
data class LoggerState(
    val loading: Boolean = true,
    val day: LocalDate,
    val otherDay: RelativeDay?,            // non-null when day != today, shown in the header
    val slot: MealSlot,
    val slots: List<MealSlot>,             // all five, or [Uncategorized] with groups off
    val totals: List<TotalsColumn>?,       // null when all four are Hidden
    val mealChecks: List<MealCheck>?,      // shown instead of totals
    val query: String,
    val focusSearch: Boolean,
    val methods: List<LoggerMethod>,
    val body: LoggerBody,
    val basket: BasketFooter?,             // null when nothing is picked
)
data class TotalsColumn(val metric: Metric, val label: StringResource, val display: Display)   // GoalStyle.Slash, bare
data class MealCheck(val slot: MealSlot, val done: Boolean)
sealed interface LoggerMethod {
    data object DescribeIt : LoggerMethod
    data object Water : LoggerMethod                       // water not Hide
    data object SavedMeals : LoggerMethod
    data class Yesterday(val slot: MealSlot) : LoggerMethod  // only when day − 1 has this slot
    data object AteNoDetails : LoggerMethod
    data object WeighIn : LoggerMethod
    data object JustANumber : LoggerMethod                 // level(Calories) != Hide
    data object AddANote : LoggerMethod                    // replaces JustANumber when calories are Hide
}
data class FoodRowUi(
    val ref: FoodRef, val name: String, val shortName: String,
    val amount: String,                    // default portion: "1 medium"
    val macros: List<MacroPart>,           // macroLine(Compact): "1P 27C 0F", Show only
    val calories: Display,                 // Logger surface, no goal, so Soft never applies
    val picked: Boolean,
)
sealed interface LoggerBody {
    data class Browse(val favorites: List<FoodRowUi>, val usualTitle: UsualTitle, val usual: List<FoodRowUi>, val recent: List<FoodRowUi>) : LoggerBody
    data object FirstRun : LoggerBody      // nothing logged yet, all three lists empty
    data object Searching : LoggerBody
    data class Results(val rows: List<FoodRowUi>, val offline: Boolean) : LoggerBody
    data object NoResults : LoggerBody
}
sealed interface UsualTitle { data class UsualAt(val slot: MealSlot) : UsualTitle; data object Suggested : UsualTitle }
data class BasketFooter(val count: Int, val names: String, val calories: Display)
```

`LoggerStateBuilder.build(inputs, policy, query, search: SearchResult?)`, pure.
Totals strip: calories against today's target, protein/carbs/fat against their
goals, from the day's totals (not the basket), `GoalStyle.Slash`, `bare`.
`shortName` cuts the name at the first comma or bracket (`SHORT` in the
prototype, generalized).

### Actions

| Action | Does |
|---|---|
| `InputsArrived(inputs, policy)` | Day, settings, basket, favorites, usual, recent, yesterday's slots. Rebuilds. |
| `SlotPicked(slot)` | `slot = slot`; Usual list and Yesterday chip follow it. |
| `SearchTyped(text)` | `query = text`. Trimmed empty: `Browse`. Else `Searching`, debounced 250 ms, `FoodCatalog.search(q, 25)`. |
| `SearchResultsArrived(query, result)` | Ignored unless `query` still matches. `Results`, `NoResults`, or offline results. |
| `FoodNameTapped(ref)` | Event `OpenAmount(ref)`. |
| `FoodToggleTapped(ref, method)` | `BasketStore.toggle` with the default portion and `Estimate`. |
| `DescribeItTapped` | `OpenLog(day, slot, Describe)`, dismiss first. |
| `WaterTapped` | `addWater(day, glassMl)`, toast; the sheet stays open (prototype). |
| `SavedMealsTapped` | `OpenLog(day, slot, Saved)`. |
| `YesterdayTapped` | `copySlot(day − 1, day, slot)`, toast, `Dismiss`. |
| `AteNoDetailsTapped` | `markAteNoDetails(day, slot)`, toast, `Dismiss`. |
| `WeighInTapped` | `OpenWeight` (trends api). |
| `JustANumberTapped` | `OpenLog(day, slot, Describe, focusNumber = true)`. |
| `AddANoteTapped` | `OpenMealNote(day, slot)` (today api). The prototype opened Day close; see `shared.md`. |
| `AddToMealTapped` | `DiaryRepository.add(day, slot, items)`, merges any hunger/fullness tags into `setMealFeeling(day, slot)`, `BasketStore.clear()`, toast, `Dismiss`. |
| `CloseTapped` | `Dismiss`. |

Route pre-picks (`prePickFoodId`, `prePickSavedMealId`, from Foods) are added to
the basket once in `init`, keyed on the back stack entry so recomposition
cannot add twice.

### Events
`OpenAmount(ref)`, `OpenLog(day, slot, tab, focusNumber)`, `OpenWeight`,
`OpenMealNote(day, slot)`, `ShowToast`, `Dismiss`.

### Data

| Read | Notes |
|---|---|
| `DiaryRepository.observeDay(day)`, `observeDay(day − 1)` | totals strip, meal checks, Yesterday chip |
| `DiaryRepository.observeFrequentFoods(limit = 5, since = 30 days)` | Favorites. The handoff has no way to star a food, so Favorites are the foods logged most. |
| `DiaryRepository.observeFrequentFoods(slot, limit = 3, since = 30 days)` | Usual at {meal}; falls back to Favorites' top 3 under "Suggested"; hidden when both empty |
| `DiaryRepository.observeRecentFoods(limit = 6)` | Recent; new custom foods appear first |
| `FoodCatalog.search(q, limit)` | Server search merged with `CustomFoodRepository` matches first. Offline: `food_cache` and custom foods only, `offline = true`. |
| `BasketStore.basket`, `SettingsRepository.observe()`, `VisibilityPolicySource.policy` | |

Writes: `DiaryRepository.add`, `addWater`, `copySlot`, `markAteNoDetails`,
`setMealFeeling`. All local first.

Telemetry: `logger.opened` (`source`: nav, today, foods, meal_sheet),
`logger.method_picked` (`method`: search, favorite, usual, recent, describe,
saved, yesterday, ate_no_details, quick_number, water, weigh_in, note; `items`:
count). No food names, no amounts.

### Components
`BottomSheet(heightFraction)`, pinned header (close `IconButton`, `PillChip`
row, totals strip of four `FillBar` columns, search field, horizontally
scrolling method chips with `IconTile`), `LazyColumn` body, pinned footer.
Favorites are round `IconTile`s with a + / check badge. Rows are
`FoodPickRow` and `FoodIconTile` from `:libraries:diary:ui`. Barcode button not
built (0.2).

### Visibility surfaces

Surface `Logger` for the whole sheet. Delay resolves to Hidden here until 9 pm
or a past day; Coast never reveals here.

| Number | Metric | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|
| Totals strip Energy | calories | `1,245 / 2,100` and bar | soft word and bar | Hidden | Hidden |
| Totals strip macros | protein, carbs, fat | `47 / 120 g` and bar | soft word and bar | Hidden | Hidden |
| Meal checks | none | shown only when all four columns are Hidden | | | |
| Row calories | calories | `105 kcal` | Hidden (no goal) | Hidden | Hidden |
| Row macros | protein, carbs, fat | `1P 27C 0F` parts | omitted | omitted | omitted |
| Basket total | calories | `205 kcal` | Hidden | Hidden | Hidden |
| Just a number chip | calories | shown | shown | shown | becomes Add a note |

### Copy

| Key | String |
|---|---|
| `logger_close_cd` | Close |
| `logger_for_day` (new) | For %s (only when the day is not today) |
| `logger_slot_*` | Breakfast / Lunch / Snack / Dinner / Uncategorized |
| `logger_totals_energy` / `_protein` / `_carbs` / `_fat` | Energy / Protein / Carbs / Fat |
| `logger_search_placeholder` | Search for a food |
| `logger_method_describe` / `_water` / `_saved` / `_yesterday` / `_ate` / `_weigh_in` / `_number` / `_note` | Describe it / Water / Saved meals / Yesterday's %s / Ate, no details / Weigh-in / Just a number / Add a note |
| `logger_favorites` / `_usual_at` / `_suggested` / `_recent` | Favorites / Usual at %s / Suggested / Recent |
| `logger_no_results` | Nothing by that name yet. Try Describe, or make it a custom food under Foods. |
| `logger_offline_results` (new) | Search needs a connection. Showing foods you've used before. |
| `logger_first_run` (new) | Search for anything you ate. Foods you log show up here next time. |
| `logger_pick_cd` / `_remove_cd` | Pick %s / Remove %s |
| `logger_basket_count` | %d picked |
| `logger_add_to` | Add to %s |
| Toasts | Added %1$s to %2$s. (one item, short name) / Added %1$d foods to %2$s. / Glass of water added. / Added to %s. / Marked %s as eaten. |

Method icons: `mic` (fiber), `water_drop` (fat), `bookmark` (fat), `history`
(carbs), `check_circle` (protein), `monitor_weight` (fiber), `dialpad` (fat),
`edit_note` (fat).

### Edge cases
- **Groups off**: one chip, Uncategorized; commit goes there.
- **Logging for another day**: header shows "For Yesterday"; everything writes to that day.
- **Future day**: allowed (planning); toasts unchanged.
- **Offline search**: cache and custom foods only, caption shown; no spinner forever (results arrive from local immediately).
- **Stale results**: a response for an older query is dropped.
- **Picking a food twice**: toggle removes; the Amount sheet replaces the basket entry for that food.
- **Commit with groups switched off while open**: slot recomputed on commit.
- **Large text**: totals strip wraps each column's text to two lines; chip row scrolls.
- **First launch**: `FirstRun` body under search and methods.

### Tests
- `LoggerStateBuilderTest`: totals at each level, meal checks when all four Hidden (Blind), Fuel shows soft words in the strip and nothing on rows, compact macro parts drop hidden macros, methods by water and calorie level, Yesterday chip only with yesterday's slot, groups off chips, `otherDay` only off today.
- `LoggerViewModelTest`: each action; debounce and stale-result drop (virtual time); toggle and commit (writes, clears basket, merges tags, toast text for one vs many); pre-pick added once; `WaterTapped` keeps the sheet open; telemetry attributes contain no names or amounts.
- `BasketStoreTest`: toggle, upsert replaces by ref, clear, cleared on user change.
- Previews: empty Full, picked Full, picked Blind, results, no results, offline, Fuel, groups off, other day.
- `SURFACES`: `logger`, `logger-picked`, `logger-results` (all `showsCaloriesInFull = true`).

---

## Amount sheet

`AmountViewModel(ref)`. Only "Pick this amount" in 0.1: the add-directly mode is
the barcode path (0.2).

```kotlin
data class AmountState(
    val loading: Boolean = true,
    val name: String, val tag: String?,
    val precision: Precision,                  // default Estimate
    val amountText: String,                    // "1.5 cup", "150 g"
    val units: List<String>, val unitIndex: Int,
    val nutrition: List<NutritionTile>,        // Energy, Protein, Fiber, Calcium; Hidden tiles dropped
    val askTags: Boolean,
    val hunger: Hunger?, val fullness: Fullness?,
)
data class NutritionTile(val metric: Metric, val label: StringResource, val display: Display)
```

Portion math is `PortionAmount` in `:libraries:foods` (pure): grams step 10
(minimum 10), other units step 0.5 (minimum 0.5); switching to grams rounds the
current grams, switching away rounds to the nearest 0.5. Nutrients are
per-100 g times grams over 100 (`NutrientAmounts.times`).

| Action | Does |
|---|---|
| `FoodArrived(food, settings, policy)` | `FoodCatalog.detail(id)` or `CustomFoodRepository.get(id)`; existing basket entry for the food prefills. |
| `PrecisionPicked(p)` | Weighed / Estimated / Just the name. |
| `LessTapped` / `MoreTapped` | Steps the amount. |
| `UnitPicked(index)` | Converts the amount. |
| `HungerTapped(h)` / `FullnessTapped(f)` | Select or clear. |
| `StopAskingTapped` | `askTags = false` in settings, toast. |
| `PickTapped` | `BasketStore.upsert(item)`, `Dismiss` back to the Logger. |
| `CloseTapped` | `Dismiss` without changes. |

Visibility (surface `Logger`): Energy `calories` (`105 kcal`), Protein (`1 g`),
Fiber (`3 g`), Calcium `micros` (`1%`, Hidden in Full by default). Soften needs a
goal, so softened metrics hide here. The nutrition block disappears when every
tile is Hidden.

Copy: title is the food name, tag beside it; `logger_amount_sure` How sure are
you?; Weighed / Estimated / Just the name; `logger_amount` Amount; Less / More
(descriptions); tile labels Energy / Protein / Fiber / Calcium; `Before eating
(optional)`: Very hungry / Peckish / Not hungry; `After (optional)`: Still
hungry / Satisfied / Very full; `Stop asking these`; `Pick this amount`. Toast:
`Okay, no more hunger questions. You can turn them back on in More, under Look
and feel.` (changed from "in Reminders", see `shared.md`).

Edge cases: a food with no portions offers `g` only; a custom food offers its
one serving unit plus `g` when grams are known; offline detail falls back to the
cached food. Tests: `PortionAmountTest`; `AmountViewModelTest` for each action,
upsert replaces, tags hidden when `askTags` is false. Previews: Full, Blind,
tags off. `SURFACES`: `amount-sheet` (`showsCaloriesInFull = true`).

---

## Log screen (Describe and Saved)

`LogViewModel(day, slot, tab, focusNumber)`.

```kotlin
data class LogState(
    val slot: MealSlot, val slots: List<MealSlot>,
    val tab: LogTab,
    val describe: DescribeUi,
    val saved: SavedUi,
)
data class DescribeUi(
    val text: String,
    val status: DescribeStatus,                // Idle | Parsing | Parsed(items) | Offline | Failed
    val canNumber: Boolean,                    // level(Calories) != Hide
    val number: String,                        // digits only
    val focusNumber: Boolean,
)
data class ParsedItemUi(val name: String, val amount: String, val calories: Display)
data class SavedUi(val dayBefore: DayBeforeRow?, val meals: List<SavedMealUi>)
data class DayBeforeRow(val names: String)     // "Greek yogurt, Banana", first comma cut
data class SavedMealUi(val id: String, val name: String, val foods: String, val calories: Display)
```

| Action | Does |
|---|---|
| `InputsArrived(...)` | Settings, saved meals, day before's slot, policy. |
| `SlotPicked(slot)` / `TabPicked(tab)` | |
| `DescribeTyped(text)` | `text = text`, status back to Idle. |
| `GoTapped` | Non-blank: `Parsing`, `FoodCatalog.describe(text)`, then `DescribeArrived(outcome)`. |
| `DescribeArrived(outcome)` | `Parsed(items)`, `Offline` or `Failed`. |
| `AddParsedTapped` | `DiaryRepository.add(day, slot, items, method = Describe, precision = Estimate)`, toast, `Close`. |
| `NumberTyped(text)` | Digits only. |
| `AddNumberTapped` | n > 0: adds `Quick add`, Named, `kcal = n`, method `QuickNumber`; toast; `Close`. |
| `SaveNameOnlyTapped` | Non-blank text: adds it by name, Named, all nutrients null; toast; `Close`. |
| `CopyDayBeforeTapped` | `copySlot(day − 1, day, slot)`, toast, `Close`. |
| `SavedMealAddTapped(id)` | Adds the saved meal's item snapshots, method `Saved`, toast, `Close`. |
| `BackTapped` | `Close`. |

`Close` pops back to where the Logger was opened (Today). Data:
`FoodCatalog.describe`, `SavedMealRepository.observeAll()`,
`DiaryRepository.observeDay(day − 1)`, `SettingsRepository`, policy. Describe
needs the network; Saved works offline.

Visibility (surface `Logger`): parsed rows and saved meal totals show
`calories` at Show only. "Or just a number" is not built when calories are Hide,
since typing a number is itself a calorie entry (`PROTOTYPE_NOTES.md`).

Copy: `logger_log_adding_to` Adding to; tabs Describe / Saved;
`logger_describe_prompt` Say it the way you'd say it; placeholder `2 eggs and
toast`; `Go`; parsed sub `%s · Estimate`; `Add to %s`; `logger_or_number` Or
just a number; placeholder `kcal`; `Add`; `logger_name_only` Just save the name,
no details; `logger_copy_yesterday` Copy yesterday's %s (day before's variant
as in `today-sheets.md`); `Copy`; saved row button `Add`. New: `logger_describe_offline`
Describe needs a connection. You can still save it by name.;
`logger_describe_failed` That didn't parse. Try fewer words, or save it by
name. Toast `Added to %s.`

Tests: `LogViewModelTest` per action (digits filter, zero ignored, parse
round trip with a fake catalog, offline status, name-only has null nutrients);
builder test that `canNumber` is false in Blind and Mindful. Previews: Describe
parsed Full and Blind, Saved. `SURFACES`: `log-describe-parsed`, `log-saved`
(both `showsCaloriesInFull = true`).
