# Logger plan (design v3)

The center + sheet with everything that lives inside it (search, Describe it,
Saved meals), the Amount sheet that stacks on it, and How it felt after
adding. Read `shared.md` first.

## Source
- **Design v3 is the spec**: `docs/design/v3/CHANGES.md` (global rules, the
  Logger row) and `manifest.json` notes for `logger`, `logger-picked`,
  `logger-amount`, `logger-search`, `logger-search-blind`,
  `logger-search-empty`, `logger-describe`, `logger-how-it-felt`,
  `today-added`. Images in `docs/design/v3/`.
- Older handoff (only where v3 says nothing): `README.md` 4.3 and 4.4,
  `Phone.dc.html` `sheet.quick`, `sheet.add`, `is.log`; `phone-logic.js`
  `lg`, `foodRow`, `macroLine`, `af`, `quick`, `saved`, `addItems`.

## What v3 changed
- The sheet opens on search. The meal is a **dropdown in the title** ("Add
  to" over "Dinner ⌄"), the day on the right. The meal chip row, the totals
  strip and the Favorites row are gone.
- Method chips: Describe, Yesterday's, Saved, Ate no details, Just a number
  (Add a note when calories are Hide). Photo is Later and not built; Water and
  Weigh-in are no longer chips. Chips hide while the person is typing.
- **Describe and Saved meals are panes of the sheet.** The Log screen with tabs
  is gone. `LogRoute` survives only as an alias that opens the sheet on the
  matching pane, so callers keep working (Today's in-flight branch uses it).
- Search rows get a brand line (cuisine once the catalog has it), a real
  no-results state that carries the term into Describe or "Save the name only".
- Amount sheet: Weighed / Estimate / Just the name, stepper and unit chips,
  Energy / Protein / Carbs / Fat tiles, Hunger before, and **Pick and keep
  going** beside **Add to {meal}**.
- After adding, the sheet closes and the logger publishes `LoggedToMeal` on
  `RecentLogEvents` (`:libraries:diary`). Today scrolls to the meal, outlines
  it and offers Undo for 6 s; Undo removes exactly those item ids.
- How it felt (Skip as big as Save) follows an add when the preset asks
  (`ModePreset.asksHowItFelt`, Mindful) or hunger questions are on.

## Routes

| Route | Where | Kind |
|---|---|---|
| `LoggerRoute(slot: MealSlot? = null, dayIso: String? = null, focusSearch: Boolean = false, prePickFoodId: String? = null, prePickIsCustom: Boolean = false, prePickSavedMealId: String? = null, start: LoggerStart = LoggerStart.Search)` | `:features:logger` | `bottomSheet<>`, `heightFraction = FullHeightFraction`, drag handle, typeMap for `MealSlot?` and `LoggerStart` |
| `LogRoute(dayIso, slot, tab: LogTab = Describe, focusNumber = false)` | `:features:logger` | Alias: the same sheet, `start` from `tab` / `focusNumber`. Kept so callers written against the old Log screen keep working. New code uses `LoggerRoute(start = ...)`. |
| `HowItFeltRoute(dayIso: String, slot: MealSlot)` | `:features:logger` | `bottomSheet<>`, typeMap for `MealSlot`. In the api so Today's meal sheet can open the same sheet instead of building `MealFeelRoute`. |
| `AmountSheetRoute(foodId, isCustom, source: PickSource, dayIso: String, slot: MealSlot)` | `:features:logger:impl` | `bottomSheet<>` over the Logger, `heightFraction = 0.9`, typeMap for `PickSource` and `MealSlot`. Day and slot because its "Add to {meal}" commits. |

`@Serializable enum class LoggerStart { Search, Describe, Saved, Number }`.
`Number` is Describe with the number field focused (the Just a number chip from
outside the sheet). `dayIso == null` is `SelectedDay.date.value` at open;
`slot == null` is `MealSlot.defaultFor(now, mealGroups)`.

`MealNoteRoute` (`:features:today`) stays the Add a note target. `WeightRoute`
is no longer used (Weigh-in chip removed), so logger impl drops its
`:features:trends` dependency.

## Library additions this plan makes

| Addition | Where | Why |
|---|---|---|
| `RecentLogEvents` (`latest: StateFlow<LoggedToMeal?>`, `logged(event)`, `consumed(event)`, `suspend undo(event)`) and `LoggedToMeal(day, slot, itemIds, at)` | `:libraries:diary` (impl in `:libraries:diary:impl`) | The seam Today reads to scroll, outline and undo. See "After adding" below. |
| `ModePreset.asksHowItFelt` (true for Mindful) | `:libraries:nutrition` | How it felt follows every add in Mindful; screens read the preset's flag, never its id. |
| `AmountUnit.chipLabel`, `AmountUnit.amountText(quantity)` | `:libraries:foods` | "plate", "grams"; "1 plate", "150 g" on the stepper. |
| `SearchField(onClear)` (clear button and accent border once there is text) | `:libraries:ui` | v3 search field while typing. |
| `SheetHeader(onBack, overline)` | `:libraries:ui` | Back arrow plus "For breakfast" over "Describe it", and the Amount sheet's title. |
| `ActionChip` restyled to v3 (surface card, tinted icon, 48 dp touch) | `:libraries:ui` | Method chips. Only the logger uses it. |
| `ChoiceChip` (rounded, soft accent with a check when selected, 48 dp touch) | `:libraries:ui` | v3 unit, hunger, fullness and mood chips. `PillChip` keeps its look for the screens that use it. |
| `RoundIconButton(touchTarget)` | `:libraries:ui` | The 40 dp + keeps a 48 dp touch area. |
| `AppIcons.ArrowBack`, `ExpandMore`, `Cancel`, `SearchOff`, `ContentCopy` | `:libraries:ui` | |
| `FoodPickRow(subtitle)` | `:libraries:diary:ui` | The brand line before the amount, energy bare. |

## Basket

`BasketStore` (`@SingleIn(AppScope)`, logger impl) holds
`StateFlow<List<BasketItem>>` so the Logger, Describe and the Amount sheet share
it without a nav result. It survives a dismissed Logger, clears on commit and on
user change (`UserScopedClearer`). New: `removeWhere(predicate)`.

```kotlin
data class BasketItem(
    val key: String,          // ref.key, "saved:<mealId>:<i>" or "describe:<i>" for items with no food
    val ref: FoodRef?, val draft: LogDraft, val source: PickSource, val food: Food? = null,
    val hunger: Hunger? = null, val fullness: Fullness? = null,
)
```

`PickSource`: Search, Usual, Recent, Saved, Foods, **Describe**, **Name**
(Favorite stays for old baskets, nothing creates it). Method: custom foods are
`Custom`; Search to `Search`; Usual to `Favorite`; Saved to `Saved`; Describe to
`Describe`; Name to `Custom`; the rest `Recent`.

`BasketCommitter` (logger impl) is the one place a basket is written, used by
the Logger and the Amount sheet: `addItems(day, slot, drafts, Search)`, merge
hunger/fullness into `setFeeling`, `markUsed` per catalog food, telemetry once
per source, clear the basket, `RecentLogEvents.logged(...)`, and return
`Committed(slot, itemIds, askHowItFelt)`. `askHowItFelt` is
`Presets[settings.numbers.preset].asksHowItFelt || settings.askTags`.

---

## Logger sheet

### State

```kotlin
data class LoggerState(
    val loading: Boolean = true,
    val day: RelativeDay,                  // right of the title; hidden while typing
    val slot: MealSlot,
    val slots: List<MealSlot>,             // the dropdown; one entry (no chevron) with groups off
    val pane: LoggerPane,                  // Search | Describe | Saved
    val query: String,
    val focusSearch: Boolean,
    val methods: List<LoggerMethod>,       // empty while the query is not blank
    val body: LoggerBody,
    val describe: DescribeUi,
    val saved: SavedUi,
    val basket: BasketFooter?,             // null when nothing is picked, or on Describe / Saved
)
sealed interface LoggerMethod {
    DescribeIt; Yesterday(slot) /* day − 1 has items here */; SavedMeals;
    AteNoDetails /* the four meals */; JustANumber /* calories not Hide */; AddANote /* instead, when Hide */
}
data class FoodRowUi(key, ref, source, name, shortName, subtitle: String? /* brand */, amount,
    macros: List<MacroPart> /* Compact */, calories: Display /* bare */, picked)
sealed interface LoggerBody {
    data class Browse(val usualTitle: UsualTitle, val usual: List<FoodRowUi>, val recent: List<FoodRowUi>)
    data object FirstRun
    data object Searching
    data class Results(val rows: List<FoodRowUi>, val offline: Boolean)
    data class NoResults(val query: String, val offline: Boolean)
}
data class DescribeUi(text, status: DescribeStatus, lines: List<DescribedLine>, canNumber: Boolean,
    number: String, focusNumber: Boolean)
data class DescribedLine(key, ref: FoodRef?, name, sub /* "2 · Estimate" */, calories: Display /* bare */)
data class SavedUi(val meals: List<SavedMealUi>)
data class SavedMealUi(id, name, foods, calories: Display)
data class BasketFooter(count, names, calories: Display, slot)
```

`LoggerStateBuilder.build(inputs, policy, slot, pane, query, search, describe,
number, focusSearch, focusNumber)`, pure. `inputs` is `LoggerInputs(today, day,
dayBefore, settings, basket, usualBySlot, recent, customFoods, savedMeals)`.

- Usual: `usualAt(slot)` under "Usual at {meal}", else the first three recents
  under "Suggested"; Uncategorized is always Suggested.
- Search results: the user's custom foods whose name or brand matches, then the
  catalog's, deduplicated by key. The subtitle is the brand (the catalog has no
  cuisine field yet; when it does, cuisine goes first).
- Describe lines are the basket items with source Describe, in order.
- Row and line energy use a bare format context ("525"); the footer keeps the
  unit ("813 kcal").

### Actions

| Action | Does |
|---|---|
| `InputsArrived(inputs)` / `PolicyArrived(policy)` | Rebuild. |
| `SlotPicked(slot)` | Re-targets everything: the title, Usual, Yesterday's, the footer, the commit. |
| `SearchTyped(text)` | Debounced 250 ms `FoodCatalog.search(q, 25)`. Chips and the day label hide while it is not blank. |
| `SearchCleared` | Query back to empty, Browse. |
| `SearchResultsArrived(outcome)` | Dropped unless still the current trimmed query. |
| `FoodNameTapped(row)` | Event `OpenAmount(ref, source)`. |
| `FoodToggleTapped(row)` | `BasketStore.toggle`. |
| `DescribeItTapped` | Pane Describe. |
| `DescribeTermTapped` | (no-results, "Describe it" link) pane Describe with the query as its text, parse starts. |
| `SaveNameTapped` | (no-results, "save the name" link, Describe's "Just save the name") creates a name-only custom food (or reuses one by that name), picks it (`Named`, source Name), back to Search with the query cleared. "Saved names show up in search next time." |
| `SavedMealsTapped` | Pane Saved. |
| `SavedMealPicked(id)` | The meal's items into the basket, back to Search. |
| `YesterdayTapped` | `copySlot(day − 1, day, slot)`, `LoggedToMeal`, `Dismiss`. |
| `AteNoDetailsTapped` | `setAteNoDetails(day, slot, true)`, toast, `Dismiss`. |
| `JustANumberTapped` | Pane Describe, number field focused. |
| `AddANoteTapped` | Event `OpenMealNote(day, slot)`. |
| `DescribeTyped(text)` | Debounced 700 ms `FoodCatalog.describe(text)`; stale answers dropped. |
| `DescribeArrived(text, items)` / `DescribeFailed(text)` | Replaces the Describe items in the basket; empty or failed is `Failed`, offline is `Offline`. |
| `DescribedLineEditTapped(line)` | Event `OpenAmount(ref, Describe)` (lines with a food only). |
| `NumberTyped(text)` / `AddNumberTapped` | Digits only; n > 0 and calories not Hide logs "Quick add" (`QuickNumber`) on its own, `LoggedToMeal`, `Dismiss`. |
| `BackTapped` | Describe / Saved back to Search; leaving Describe drops its lines. System back does the same. |
| `AddToMealTapped` | `BasketCommitter.commit`, then `OpenHowItFelt(day, slot)` or `Dismiss`. |
| `CloseTapped` | `Dismiss`. |

### Events
`OpenAmount(ref, source)`, `OpenMealNote(day, slot)`, `OpenHowItFelt(day,
slot)`, `ShowToast(toast)`, `Dismiss`.

### Data
Reads: `observeDay(day)`, `observeDay(day − 1)`, `usualAt(slot, 3)`,
`recentFoods(6)`, `CustomFoodRepository.observeAll()`,
`SavedMealRepository.observeAll()`, `FoodCatalog.search` / `describe`,
`BasketStore`, settings, policy, `LocalCalendar`, `SelectedDay`. Writes:
`addItems`, `copySlot`, `setAteNoDetails`, `setFeeling`,
`CustomFoodRepository.create`, `markUsed`, `RecentLogEvents.logged`.

Telemetry: `logger.method_picked` (`method`: search, usual, recent, saved,
foods, describe, yesterday, ate_no_details, quick_number, name_only, note;
`items`). No names, no amounts.

### Visibility surfaces (surface `Logger`)

| Number | Metric | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|
| Row and Describe line energy | calories | `525` | Hidden (no goal) | Hidden | Hidden |
| Row macros | protein, carbs, fat | `32P 60C 18F` | omitted | omitted | omitted |
| Footer energy | calories | `813 kcal` | Hidden | Hidden | Hidden |
| Saved meal energy | calories | `205 kcal` | Hidden | Hidden | Hidden |
| Just a number chip, "Or just a number" | calories | shown | shown | shown | chip becomes Add a note, card not built |

### Copy (v3 where it has the line)

| Key | String |
|---|---|
| `logger_add_to_title` | Add to |
| `logger_search_placeholder` | Search foods, dishes, restaurants |
| `logger_search_clear_cd` | Clear search |
| `logger_meal_picker_cd` | Change meal, %1$s |
| `logger_method_describe` / `_yesterday` / `_saved` / `_ate` / `_number` / `_note` | Describe / Yesterday's / Saved / Ate, no details / Just a number / Add a note |
| `logger_usual_at` / `_suggested` / `_recent` | Usual at %1$s / Suggested / Recent |
| `logger_matches` | %1$d matches (plural) |
| `logger_not_here` / `_not_here_describe` / `_not_here_save` | Not here? / Describe it / save the name |
| `logger_empty_title` | No match for "%1$s" yet |
| `logger_empty_body` | Describe it in your own words, or save just the name and fill in details later if you want to. |
| `logger_empty_describe` | Describe "%1$s" |
| `logger_empty_save` | Save the name only |
| `logger_empty_caption` | Saved names show up in search next time. |
| `logger_basket_count` | %1$d foods picked (plural: 1 food picked) |
| `logger_add_to` | Add to %1$s |
| `logger_for_meal` | For %1$s |
| `logger_describe_title` | Describe it |
| `logger_describe_prompt` | Say it the way you'd say it |
| `logger_read_as` | We read that as |
| `logger_described_sub` | %1$s · Estimate |
| `logger_or_number` / `logger_number_caption` | Or just a number / Only in modes that show calories. |
| `logger_name_only` | Just save the name |
| `logger_saved_title` | Saved meals |

Kept: first run, offline, describe offline / failed, saved empty, toasts for
Ate no details and Stop asking. Meal names inside sentences use
`MealSlot.sentenceName`.

### Edge cases
- **Groups off**: the title reads Uncategorized with no chevron.
- **Another day**: the right label says Yesterday or the date; writes go there.
- **Offline**: search shows cached and custom foods with the offline caption;
  Describe says it needs a connection and still offers "Just save the name".
- **Stale answers**: search and Describe both drop answers for older text.
- **Picking twice**: toggle removes; the Amount sheet replaces by key.
- **Large text**: method chips wrap; rows wrap their subtitle.

---

## Barcode (0.2)

Not built in 0.1. The barcode button opens the camera; a read code calls
`FoodCatalog.barcode(code)` (`GET /v1/foods/barcode/{code}`). Our own catalog
holds about 430,000 US products (USDA Branded Foods), so most scans answer in
well under a second. A code we do not have goes to an outside source (Open
Food Facts, later a paid one), which can take up to 5 seconds, and the answer
is kept so the next person's scan is instant.

### Waiting
- For the first 400 ms show only a quiet spinner under the frozen camera
  frame. A catalog hit lands inside that and the user never sees more.
- Past 400 ms the scan has almost certainly missed our catalog and we are
  asking an outside source. Swap in the first-lookup copy with the spinner.
  Never a progress bar, never a countdown.
- A found food opens the Amount sheet in add-directly mode with the label's
  serving preselected. A serving with no grams (labels in ml) preselects
  nothing and asks for an amount, the same as a food with no portions.

### Copy

| Key | String |
|---|---|
| `barcode_looking_up` | First time anyone's scanned this one. Looking it up now, and it'll be instant for the next person. |
| `barcode_not_found` | We couldn't find this one anywhere. |
| `barcode_add_custom` | Add it as a custom food |
| `barcode_unavailable` | Couldn't look that up right now. Try again in a moment. |
| `barcode_scan_again` | Scan again |

`barcode_not_found` sits above two buttons: `barcode_add_custom` (opens the
custom food editor, empty) and `barcode_scan_again`. The
503 case (`barcode_unavailable`) offers only `barcode_scan_again`. None of
these name a number, so every mode shows them unchanged.

---

## Amount sheet

`AmountViewModel(route)`. Over the logger.

```kotlin
data class AmountState(
    loading, missing, name, subtitle: String? /* "brand · per plate" */,
    precision, showAmount, amountText /* "1 plate" */, units: List<String> /* plate, cup, grams */,
    unitIndex, canLess,
    nutrition: List<NutritionTile>,    // Energy, Protein, Carbs, Fat; Hidden dropped
    askTags, hunger: Hunger?, slot: MealSlot,
)
```

| Action | Does |
|---|---|
| `FoodArrived` / `FoodMissing` / `InputsArrived` | As before; a basket entry for the food prefills. |
| `PrecisionPicked`, `LessTapped`, `MoreTapped`, `UnitPicked`, `HungerTapped` | As before. Fullness moved to How it felt. |
| `StopAskingTapped` | `askTags = false`, toast. |
| `PickAndKeepGoingTapped` | Upsert into the basket, `Dismiss` back to the logger. |
| `AddToMealTapped` | Upsert, `BasketCommitter.commit` (the whole basket), `Committed(askHowItFelt)` so the entry point closes both sheets and opens How it felt when asked. |
| `BackTapped` | `Dismiss` without changes. |

Copy: `logger_amount_sure` How sure are you?; precision Weighed / Estimate /
Just the name; `logger_amount` Amount; tiles Energy / Protein / Carbs / Fat;
`logger_amount_hunger` Hunger before · optional with Not hungry / A bit /
Hungry / Very; `logger_amount_stop_asking` Stop asking this;
`logger_amount_keep_going` Pick and keep going; `Add to %1$s`;
`logger_amount_per` %1$s · per %2$s / `logger_amount_per_only` per %1$s.

Visibility: tiles through the policy at the food's own context (no goal, so
soften hides).

---

## How it felt

`HowItFeltViewModel(route)`: prefilled once from the day's stored feeling for
the slot.

```kotlin
data class HowItFeltState(val loading: Boolean, val slot: MealSlot, val hunger: Hunger?,
    val fullness: Fullness?, val moods: Set<Mood>, val note: String)
```

| Action | Does |
|---|---|
| `FeelingLoaded(feeling)` | Prefill. |
| `HungerTapped` / `FullnessTapped` | Select or clear. |
| `MoodTapped` | Toggle. |
| `NoteTyped` | |
| `SaveTapped` | `setFeeling(day, slot, …)` (empty clears), `Dismiss`. |
| `SkipTapped` / `CloseTapped` | `Dismiss`, nothing written. |

Copy: `How was %1$s?`; Everything here is optional, and only you see it.;
Hunger before: Not hungry / A bit / Hungry / Very hungry; Fullness after: Still
hungry / Satisfied / Full / Too full; Mood · pick any: Calm / Rushed / Social /
Stressed / Happy (the design's Tired is not a `Mood` yet); note placeholder
Add a note (voice is 0.2); Skip; Save. No telemetry (feelings are health data).
No numbers, so no visibility surface beyond the leak test's control.

---

## After adding (for Today)

```kotlin
interface RecentLogEvents {
    val latest: StateFlow<LoggedToMeal?>
    fun logged(event: LoggedToMeal)
    fun consumed(event: LoggedToMeal)        // Today calls this once it has shown it
    suspend fun undo(event: LoggedToMeal)    // removes exactly event.itemIds, then consumed
}
data class LoggedToMeal(val day: LocalDate, val slot: MealSlot, val itemIds: List<String>, val at: Instant)
```

Published by every path that writes items: the basket commit (from the sheet,
Describe or the Amount sheet), Yesterday's, and Just a number. Today, when
`latest` is non-null, on its day, and less than 6 s old: scroll to `slot`,
outline the card, show the "Added N foods to {meal}" snackbar with Undo until 6
s after `at`, then `consumed`. Undo calls `undo(event)`. The logger shows no
toast for these, so it is not doubled.

---

## Tests
- `LoggerStateBuilderTest`: methods by calorie level and slot, chips gone while
  typing, groups off, day label, Usual falls back to Suggested, rows without a
  food left out, custom foods lead results, brand subtitle, Blind drops energy
  and macros, NoResults carries the term, Describe lines from the basket.
- `LoggerViewModelTest`: each action; debounce and stale drops; panes and back;
  commit writes, clears, publishes `LoggedToMeal`, asks How it felt by preset
  and `askTags`; quick number; save the name; saved meal pick.
- `AmountTest`: builder tiles by level, Named hides the amount; each action,
  keep going vs add.
- `HowItFeltTest`, `BasketStoreTest`, `RecentLogEventsTest` (diary impl),
  `PortionAmountTest` additions.
- Previews: `LoggerPreview`, `LoggerDarkPreview`, `LoggerPickedPreview`,
  `LoggerSearchPreview`, `LoggerSearchBlindPreview`, `LoggerSearchEmptyPreview`,
  `LoggerDescribePreview`, `LoggerSavedPreview`, `LoggerAmountPreview`,
  `LoggerAmountBlindPreview`, `LoggerHowItFeltPreview` and a few states
  (first run, groups off, other day, Nutrient focus, dark).
- `LoggerLeakSurfaces`: `logger`, `logger-picked`, `logger-search`,
  `logger-search-empty`, `logger-describe`, `logger-saved`, `logger-amount`
  (calories in Full) and `logger-how-it-felt` (none). Blind is a policy, not a
  surface, so `logger-search-blind` is `logger-search` under Blind.
