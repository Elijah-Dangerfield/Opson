# Logger plan

The center + sheet, the Amount sheet, and the Log screen (Describe and Saved
tabs). Read `shared.md` first.

## Source
- Handoff: `README.md` sections 4.3 and 4.4; `COMPETITIVE_REVIEW.md` "What changed".
- Prototype markup: `Phone.dc.html` `sheet.quick` (line 1002), `sheet.add` (858), `is.log` (304 to 377).
- Logic: `phone-logic.js` `lg`, `lgSum`, `foodRow`, `macroLine`, `quickActions`, `af`, `quick`, `saved`, `addItems`.

## Routes

| Route | Where | Kind |
|---|---|---|
| `LoggerRoute(slot: MealSlot? = null, dayIso: String? = null, focusSearch: Boolean = false, prePickFoodId: String? = null, prePickIsCustom: Boolean = false, prePickSavedMealId: String? = null)` | `:features:logger` | `bottomSheet<>` with `BottomSheet(heightFraction = BottomSheetDefaults.FullHeightFraction)` (0.93), typeMap for `MealSlot` |
| `LogRoute(dayIso: String, slot: MealSlot, tab: LogTab = LogTab.Describe, focusNumber: Boolean = false)` | `:features:logger` | `screen<>`, typeMap for `MealSlot` and `LogTab` |
| `AmountSheetRoute(foodId: String, isCustom: Boolean, source: PickSource)` | `:features:logger:impl` | `bottomSheet<>` over the Logger, typeMap for `PickSource`. In the impl because only the Logger opens it. |

`@Serializable enum class LogTab { Describe, Saved }` in the api. Search and
Photo tabs are not built: search lives in the Logger, Photo is Later.

`dayIso == null` means the day Today is showing (`SelectedDay.date.value`, read
once at open). `slot == null` means `MealSlot.defaultFor(now, mealGroups)`, so
Uncategorized with meal groups off.

The Logger also needs two routes other features own and register:
`WeightRoute()` (`:features:trends`, for the Weigh-in chip) and
`MealNoteRoute(dayIso: String, slot: MealSlot)` (`:features:today`, for "Add a
note"). The Logger adds the classes with the signatures `trends.md` and
`today-sheets.md` give them; Trends and Today register them. Until they do, the
router's navigation failure is logged (and throws in debug).

## Library additions this plan makes

| Addition | Where | Why |
|---|---|---|
| `FoodRef` (`Catalog(id)` / `Custom(id)`, `key`), `LogItem.foodRef()`, `LogDraft.foodRef()` | `:libraries:foods` | The basket, the Foods pre-pick and the amount sheet all name "this food" the same way. |
| `AmountUnit`, `PortionAmount` (`step`, `convert`), `Food.amountUnits()`, `CustomFood.amountUnits()`, `Food.draftFor(unit, quantity, precision)`, `CustomFood.draftFor(...)` | `:libraries:foods` | Pure portion math for the amount sheet, unit tested. |
| `DiaryRepository.frequentFoods(limit, days)` | `:libraries:diary` | Favorites. The handoff has no way to star a food, so Favorites are the foods logged most over the last 30 days. `usualAt` only answers per slot. |
| `LocalCalendar` moved from `:libraries:diary:impl` to `:libraries:diary` | `:libraries:diary` | Every screen needs "today" in the user's zone; the impl was the only one that could ask. |
| `SearchField` (leading search icon, single line, focus on open) and `TextLine` (one-line text input) | `:libraries:ui` | The Logger search and Describe's input; neither exists. |
| `:libraries:diary:ui` | new module | See `shared.md`. |

## Basket

`BasketStore` (`@SingleIn(AppScope)`, logger impl) holds
`StateFlow<List<BasketItem>>` so the Logger and the Amount sheet share it
without a nav result. It survives a dismissed Logger (picks are not lost to a
stray swipe) and clears on commit and on user change (it is a
`UserScopedClearer`, contributed to that multibinding).

```kotlin
data class BasketItem(
    val key: String,                       // ref.key, or "saved:<mealId>:<index>" for a saved meal's name-only item
    val ref: FoodRef?,
    val draft: LogDraft,                   // amount, precision and nutrients, ready for addItems
    val source: PickSource,                // Search | Favorite | Usual | Recent | Saved
    val food: Food? = null,                // the catalog food when we have it, for markUsed
    val hunger: Hunger? = null, val fullness: Fullness? = null,
)
```

`PickSource` maps to the draft's `LogMethod`: Search to `Search` (a custom food
found by search stays `Custom`), Favorite and Usual to `Favorite` (the server
enum has no usual), Recent to `Recent`, Saved to `Saved`.

---

## Logger sheet

### State

```kotlin
data class LoggerState(
    val loading: Boolean = true,
    val otherDay: RelativeDay?,            // non-null when the day is not today: header says "For Yesterday"
    val slot: MealSlot,
    val slots: List<MealSlot>,             // all five, or [Uncategorized] with groups off
    val totals: List<TotalsColumn>?,       // null when all four are Hidden
    val mealChecks: List<MealCheck>?,      // shown instead of totals, null otherwise
    val query: String,
    val focusSearch: Boolean,
    val methods: List<LoggerMethod>,
    val body: LoggerBody,
    val basket: BasketFooter?,             // null when nothing is picked
)
data class TotalsColumn(val metric: Metric, val label: StringResource, val display: Display)   // GoalStyle.Slash, bare
data class MealCheck(val slot: MealSlot, val done: Boolean)
sealed interface LoggerMethod {
    data object DescribeIt; data object Water         // water not Hide
    data object SavedMeals; data class Yesterday(val slot: MealSlot)   // only when day − 1 has items in this slot
    data object AteNoDetails                          // only for one of the four meals
    data object WeighIn
    data object JustANumber                           // level(Calories) != Hide
    data object AddANote                              // replaces JustANumber when calories are Hide
}
data class FoodRowUi(
    val key: String, val ref: FoodRef, val source: PickSource,
    val name: String, val shortName: String,
    val amount: String,                    // "1 medium", the last logged amount for a food from the diary
    val macros: List<MacroPart>,           // macroLine(Compact): "1P 27C 0F", Show only
    val calories: Display,                 // Logger surface, no goal, so Soft never applies
    val picked: Boolean,
)
sealed interface LoggerBody {
    data class Browse(val favorites: List<FoodRowUi>, val usualTitle: UsualTitle, val usual: List<FoodRowUi>, val recent: List<FoodRowUi>)
    data object FirstRun                   // favorites, usual and recent all empty
    data object Searching
    data class Results(val rows: List<FoodRowUi>, val offline: Boolean)
    data class NoResults(val offline: Boolean)
}
sealed interface UsualTitle { data class UsualAt(val slot: MealSlot); data object Suggested }
data class BasketFooter(val count: Int, val names: String, val calories: Display, val slot: MealSlot)
```

`LoggerStateBuilder.build(inputs, policy, slot, query, search, focusSearch)`,
pure. `inputs` is `LoggerInputs(today, day, dayBefore, settings, basket,
favorites, usual, recent, customFoods)`; `search` is the latest
`SearchOutcome(query, foods, offline)` or null.

- Totals strip: the day's totals (not the basket). Calories against
  `targets.energyKcal`, protein/carbs/fat against their gram goals,
  `GoalStyle.Slash`, `bare`, the day's `formatContext` (delayed numbers open on
  a past or closed day).
- `shortName` cuts the name at the first comma or bracket (`SHORT` in the
  prototype, generalized).
- Rows come from `LogItem`s (Favorites, Usual, Recent) or from search hits.
  Diary rows carry the item's own last logged amount and nutrients; entries with
  no food (name-only, quick numbers) are left out because they have nothing to
  open. Search hits use the catalog food's first portion (`FoodAmount.Servings(1)`)
  or a custom food's one serving.
- Usual: `usualAt(slot)` under "Usual at {meal}"; when empty, the first three
  favorites under "Suggested"; for Uncategorized always Suggested.
- Search results put the user's custom foods whose name contains the query
  first, then the catalog's (deduplicated by key).

### Actions

| Action | Does |
|---|---|
| `InputsArrived(inputs)` | Diary, settings, basket, policy. Rebuilds. |
| `PolicyArrived(policy)` | Rebuilds. |
| `SlotPicked(slot)` | `slot = slot`; Usual list and Yesterday chip follow it. |
| `SearchTyped(text)` | `query = text`. Trimmed empty: `Browse`. Else `Searching`, debounced 250 ms, `FoodCatalog.search(q, 25)`. |
| `SearchResultsArrived(outcome)` | Ignored unless `outcome.query` is still the trimmed query. `Results`, `NoResults`, offline caption when the catalog answered from its cache or failed. |
| `FoodNameTapped(row)` | Event `OpenAmount(ref, source)`. |
| `FoodToggleTapped(row)` | `BasketStore.toggle` with the row's default draft. |
| `DescribeItTapped` | Event `OpenLog(day, slot, Describe)`. |
| `WaterTapped` | `addWater(day, glassMl)`, toast; the sheet stays open (prototype). |
| `SavedMealsTapped` | Event `OpenLog(day, slot, Saved)`. |
| `YesterdayTapped` | `copySlot(day − 1, day, slot)`, toast, `Dismiss`. |
| `AteNoDetailsTapped` | `setAteNoDetails(day, slot, true)`, toast, `Dismiss`. |
| `WeighInTapped` | Event `OpenWeight`. |
| `JustANumberTapped` | Event `OpenLog(day, slot, Describe, focusNumber = true)`. |
| `AddANoteTapped` | Event `OpenMealNote(day, slot)`. The prototype opened Day close; see `shared.md`. |
| `AddToMealTapped` | One `DiaryRepository.addItems(day, slot, drafts, Search)` (each draft carries its own method), merges any hunger/fullness into `setFeeling(day, slot)`, `markUsed` for each catalog food (detail from cache when the basket only had the id), `BasketStore.clear()`, toast, `Dismiss`. |
| `CloseTapped` | `Dismiss`. |

Route pre-picks (`prePickFoodId`, `prePickSavedMealId`, from Foods) are added to
the basket once in `init`. The view model is scoped to the back stack entry, so
recomposition cannot add twice.

### Events
`OpenAmount(ref, source)`, `OpenLog(day, slot, tab, focusNumber)`, `OpenWeight`,
`OpenMealNote(day, slot)`, `ShowToast(message, args)`, `Dismiss`. The entry
point closes the sheet and navigates in one `router.batch`.

### Data

| Read | Notes |
|---|---|
| `DiaryRepository.observeDay(day)`, `observeDay(day − 1)` | totals strip, meal checks, Yesterday chip, existing feelings |
| `DiaryRepository.frequentFoods(limit = 5, days = 30)` | Favorites |
| `DiaryRepository.usualAt(slot, limit = 3, days = 28)` | Usual at {meal} |
| `DiaryRepository.recentFoods(limit = 6)` | Recent |
| `CustomFoodRepository.observeAll()` | search merges these first |
| `FoodCatalog.search(q, 25)` | `fromCache` or a failure means offline |
| `BasketStore.basket`, `SettingsRepository.settings`, `VisibilityPolicySource.policy`, `LocalCalendar.today()`, `SelectedDay.date` | |

Writes: `addItems`, `addWater`, `copySlot`, `setAteNoDetails`, `setFeeling`,
`FoodCatalog.markUsed`. All local first.

Telemetry: `logger.method_picked` (`method`: search, favorite, usual, recent,
saved, foods, describe, yesterday, ate_no_details, quick_number, name_only,
water, weigh_in, note; `items`: count). Fired when something is logged; a basket
commit fires once per source in it. Weigh-in and Add a note fire with 0 items
when they open the screen that does the writing. Describe, Saved and Just a
number fire from the Log screen when it logs. No food names, no amounts.
`logger.opened` is the caller's (`shell.md`).

### Components
`BottomSheet(heightFraction)`, pinned header (close `RoundIconButton`,
`PillChip` row, totals strip of four `TotalsColumn`s from `:libraries:diary:ui`
or `MealCheckChip`s, `SearchField`, horizontally scrolling `ActionChip`s),
`LazyColumn` body, pinned footer. Favorites are `FavoriteFoodTile`s (round
`FoodIconTile` with a + / check badge). Rows are `FoodPickRow`. Barcode button
not built (0.2). Photo chip not built (Later).

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
| `logger_for_day` (new) | For %1$s (only when the day is not today) |
| Slot chips | `diary_slot_*` from `:libraries:diary:ui`: Breakfast / Lunch / Snack / Dinner / Uncategorized |
| `logger_totals_energy` / `_protein` / `_carbs` / `_fat` | Energy / Protein / Carbs / Fat |
| `logger_search_placeholder` | Search for a food |
| `logger_method_describe` / `_water` / `_saved` / `_yesterday` / `_ate` / `_weigh_in` / `_number` / `_note` | Describe it / Water / Saved meals / Yesterday's %1$s / Ate, no details / Weigh-in / Just a number / Add a note |
| `logger_favorites` / `_usual_at` / `_suggested` / `_recent` | Favorites / Usual at %1$s / Suggested / Recent |
| `logger_no_results` | Nothing by that name yet. Try Describe, or make it a custom food under Foods. |
| `logger_offline_results` (new) | Search needs a connection. Showing foods you've used before. |
| `logger_first_run` (new) | Search for anything you ate. Foods you log show up here next time. |
| `diary_pick_cd` / `diary_remove_cd` | Pick %1$s / Remove %1$s |
| `logger_basket_count` | %1$d picked |
| `logger_add_to` | Add to %1$s |
| Toasts | Added %1$s to %2$s. (one item, short name) / Added %1$d foods to %2$s. / Glass of water added. / Added to %1$s. / Marked %1$s as eaten. |

Meal names inside sentences use `MealSlot.sentenceName` (lower case).

Method icons and tints: `mic` (fiber), `water_drop` (fat), `bookmark` (fat),
`history` (carbs), `check_circle` (protein), `monitor_weight` (fiber), `dialpad`
(fat), `edit_note` (fat).

### Edge cases
- **Groups off**: one chip, Uncategorized; commit goes there.
- **Logging for another day**: header shows "For Yesterday"; everything writes to that day.
- **Future day**: allowed (planning); toasts unchanged.
- **Offline search**: cache and custom foods only, caption shown; no spinner forever (the catalog answers from its cache).
- **Stale results**: a response for an older query is dropped.
- **Picking a food twice**: toggle removes; the Amount sheet replaces the basket entry for that food.
- **Commit with groups switched off while open**: the repository puts it in Uncategorized, and the toast names Uncategorized.
- **Large text**: totals strip wraps each column's text to two lines; chip row scrolls.
- **First launch**: `FirstRun` body under search and methods.

### Tests
- `LoggerStateBuilderTest`: totals at each level, meal checks when all four Hidden (Blind), Fuel shows soft words in the strip and nothing on rows, compact macro parts drop hidden macros, methods by water and calorie level, Yesterday chip only with yesterday's slot, groups off chips, `otherDay` only off today, usual falls back to Suggested, rows without a food are left out, custom foods lead search results.
- `LoggerViewModelTest`: each action; debounce and stale-result drop (virtual time); toggle and commit (writes, clears basket, merges tags, toast text for one vs many); pre-pick added once; `WaterTapped` keeps the sheet open; telemetry attributes contain no names or amounts.
- `BasketStoreTest`: toggle, upsert replaces by key, clear, cleared on user change.
- Previews: empty Full, picked Full, picked Blind, results, no results, offline, Fuel, groups off, other day.
- `LoggerLeakSurfaces`: `logger`, `logger-picked`, `logger-results` (all `showsCaloriesInFull = true`).

---

## Amount sheet

`AmountViewModel(ref, source)`. Only "Pick this amount" in 0.1: the add-directly
mode is the barcode path (0.2).

```kotlin
data class AmountState(
    val loading: Boolean = true,
    val missing: Boolean = false,              // the food could not be loaded (offline, never cached)
    val name: String, val tag: String?,        // tag: brand
    val precision: Precision,                  // default Estimate
    val amountText: String,                    // "1.5 cup", "150 g"; hidden with Just the name
    val units: List<String>, val unitIndex: Int,
    val canLess: Boolean,
    val showAmount: Boolean,                   // false for Just the name
    val nutrition: List<NutritionTile>,        // Energy, Protein, Fiber, Calcium; Hidden tiles dropped
    val askTags: Boolean,
    val hunger: Hunger?, val fullness: Fullness?,
)
data class NutritionTile(val metric: Metric, val label: StringResource, val display: Display)
```

Portion math is `PortionAmount` in `:libraries:foods` (pure): grams step 10
(minimum 10), other units step 0.5 (minimum 0.5); switching to grams rounds the
current grams, switching away rounds to the nearest 0.5. Nutrients come from
`Food.toLogDraft` / `CustomFood.toDraft`.

"Just the name" logs the name and nothing else: no amount, no nutrients, so the
amount stepper and the tiles go away. The prototype kept the numbers; a name-only
item is "we do not know" everywhere else (`LogItem`), and this is where the user
says so.

| Action | Does |
|---|---|
| `FoodArrived(food)` / `FoodMissing` | `FoodCatalog.detail(id)` or `CustomFoodRepository.get(id)`; an existing basket entry for the food prefills precision, unit, amount and tags. |
| `InputsArrived(settings, policy)` | Rebuilds. |
| `PrecisionPicked(p)` | Weighed / Estimated / Just the name. |
| `LessTapped` / `MoreTapped` | Steps the amount. |
| `UnitPicked(index)` | Converts the amount. |
| `HungerTapped(h)` / `FullnessTapped(f)` | Select or clear. |
| `StopAskingTapped` | `askTags = false` in settings, clears the tags, toast. |
| `PickTapped` | `BasketStore.upsert(item)`, `Dismiss` back to the Logger. |
| `CloseTapped` | `Dismiss` without changes. |

Visibility (surface `Logger`): Energy `calories` (`105 kcal`), Protein (`1 g`),
Fiber (`3 g`), Calcium `micros` (`1%`, Hidden in Full by default). Soften needs a
goal, so softened metrics hide here. The nutrition block disappears when every
tile is Hidden.

Copy: title is the food name, brand under it; `logger_amount_sure` How sure are
you?; `diary_precision_picker_*` Weighed / Estimated / Just the name;
`logger_amount` Amount; Less / More (content descriptions); tile labels Energy /
Protein / Fiber / Calcium; `logger_amount_before` Before eating (optional): Very
hungry / Peckish / Not hungry; `logger_amount_after` After (optional): Still
hungry / Satisfied / Very full; `logger_amount_stop_asking` Stop asking these;
`logger_amount_pick` Pick this amount; `logger_amount_missing` (new) This food
needs a connection to open. Toast: `Okay, no more hunger questions. You can turn
them back on in More, under Look and feel.` (changed from "in Reminders", see
`shared.md`).

Edge cases: a food with no portions offers `g` only; a custom food offers its
one serving unit; offline detail falls back to the cached food, and a food never
cached shows the missing line. Tests: `PortionAmountTest`; `AmountStateBuilderTest`
(tiles by level, Named hides the amount); `AmountViewModelTest` for each action,
upsert replaces, prefill from the basket. Previews: Full, Blind, tags off.
`LoggerLeakSurfaces`: `amount-sheet` (`showsCaloriesInFull = true`).

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
data class DayBeforeRow(val slot: MealSlot, val names: String)     // "Greek yogurt, Banana", first comma cut
data class SavedMealUi(val id: String, val name: String, val foods: String, val calories: Display)
```

`LogStateBuilder.build(inputs, policy, text, status, number, ...)`, pure. Parsed
items are kept in the view model as `ParsedItem`s and shown through the builder.

| Action | Does |
|---|---|
| `InputsArrived(inputs)` | Settings, saved meals, day before, policy. |
| `SlotPicked(slot)` / `TabPicked(tab)` | |
| `DescribeTyped(text)` | `text = text`, status back to Idle. |
| `GoTapped` | Non-blank: `Parsing`, `FoodCatalog.describe(text)`, then `DescribeArrived(outcome)`. |
| `DescribeArrived(items)` / `DescribeFailed` | `Parsed(items)`; a failure is `Offline` when `AppState.isOffline`, else `Failed`; an empty parse is `Failed`. |
| `AddParsedTapped` | `addItems(day, slot, items.map(ParsedItem::toLogDraft), Describe)`, toast, `Close`. |
| `NumberTyped(text)` | Digits only. |
| `AddNumberTapped` | n > 0 and calories not Hide: adds "Quick add", Named, `kcal = n`, method `QuickNumber`; toast; `Close`. |
| `SaveNameOnlyTapped` | Non-blank text: adds it by name, Named, all nutrients null, method `Describe`; toast; `Close`. |
| `CopyDayBeforeTapped` | `copySlot(day − 1, day, slot)`, toast, `Close`. |
| `SavedMealAddTapped(id)` | `addItems(day, slot, meal.items, Saved)`, toast, `Close`. |
| `BackTapped` | `Close`. |

`Close` pops back to where the Logger was opened (Today). Data:
`FoodCatalog.describe`, `SavedMealRepository.observeAll()`,
`DiaryRepository.observeDay(day − 1)`, `SettingsRepository`, policy,
`AppState`. Describe needs the network; Saved works offline.

Visibility (surface `Logger`): parsed rows and saved meal totals show
`calories` at Show only. "Or just a number" is not built when calories are Hide,
since typing a number is itself a calorie entry (`PROTOTYPE_NOTES.md`).

Copy: `logger_log_adding_to` Adding to; tabs `logger_tab_describe` Describe /
`logger_tab_saved` Saved; `logger_describe_prompt` Say it the way you'd say it;
placeholder `2 eggs and toast`; `Go`; parsed sub `%1$s · Estimate`; `Add to %1$s`;
`logger_or_number` Or just a number; placeholder `kcal`; `Add`;
`logger_name_only` Just save the name, no details; `logger_copy_yesterday` Copy
yesterday's %1$s; `Copy`; saved row button `Add`. New: `logger_describe_offline`
Describe needs a connection. You can still save it by name.;
`logger_describe_failed` That didn't parse. Try fewer words, or save it by name.;
`logger_saved_empty` Saved meals show up here. Make one under Foods. Toast
`Added to %1$s.`

Tests: `LogViewModelTest` per action (digits filter, zero ignored, parse round
trip with a fake catalog, offline status, name-only has null nutrients);
`LogStateBuilderTest` that `canNumber` is false in Blind and Mindful and that the
day-before row needs items. Previews: Describe parsed Full and Blind, Saved.
`LoggerLeakSurfaces`: `log-describe-parsed`, `log-saved` (both
`showsCaloriesInFull = true`).
