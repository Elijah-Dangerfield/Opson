# Foods plan

The Foods tab, Create a meal, and Add a custom food (with its Blind variant).
Read `shared.md` first.

## Source
- Handoff: `README.md` section 4.9; `COMPETITIVE_REVIEW.md` "What changed".
- Prototype markup: `Phone.dc.html` `is.foods` (494 to 533), `is.mealbuilder` (683 to 719), `is.customfood` (720 to 749).
- Logic: `phone-logic.js` `makeRows`, `myFoods`, `saved`, `recents`, `mb`, `cf`, `cfFields`.

Routes in `:features:foods` (`com.opson.features.foods`); impl in
`:features:foods:impl` (`com.opson.features.foods.impl`).

---

## Foods tab

### Route
`FoodsRoute : TabRoute()`, `screen<>`.

### State

```kotlin
data class FoodsState(
    val loading: Boolean = true,
    val yourFoods: List<FoodsRowUi>,
    val savedMeals: List<SavedMealRowUi>,
    val recent: List<FoodsRowUi>,
)
data class FoodsRowUi(val ref: FoodRef, val name: String, val sub: String, val calories: Display)  // sub: "1 serving · Corner bakery"
data class SavedMealRowUi(val id: String, val name: String, val foods: String, val calories: Display)
```

`FoodsStateBuilder.build(inputs, policy)`. Calories resolve on `Foods` with no
goal, so they show at Show and hide otherwise.

### Actions

| Action | Does |
|---|---|
| `InputsArrived(inputs, policy)` | Custom foods, saved meals, recent foods. |
| `SearchTapped` | Event `OpenLogger(focusSearch = true)`. The prototype opened the Log screen's search tab, which is not built; search lives in the Logger. |
| `CreateMealTapped` | `OpenCreateMeal()`. |
| `AddCustomFoodTapped` | `OpenCustomFood`. |
| `FoodPlusTapped(ref)` | `OpenLogger(prePick = ref)`. |
| `SavedMealPlusTapped(id)` | `OpenLogger(prePickSavedMeal = id)`. |
| `TabReselected` | `ScrollToTop`. |

The prototype's + added straight to the logger's current slot. Here it opens
the Logger with the food already picked, so the user chooses the meal and day,
and adding still takes one more tap.

### Events
`OpenLogger(...)`, `OpenCreateMeal(fromDayIso?, fromSlot?)`, `OpenCustomFood`, `ScrollToTop`.

### Data
`CustomFoodRepository.observeAll()`, `SavedMealRepository.observeAll()`,
`DiaryRepository.observeRecentFoods(limit = 6)`, policy. All local.

### Components
Sticky search bar (`EdgeCard` styled field that is a button, `position:sticky`
equivalent: a pinned item above the `LazyColumn`), `ListRow` with `IconTile`
for the create rows, `FoodIconTile`, round + buttons at 44 dp.

### Visibility surfaces
Surface `Foods`.

| Number | Metric | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|
| Your foods, Saved meals, Recent row calories | calories | `190 kcal` | Hidden | Hidden until 9 pm / never for Coast | Hidden |

### Copy

| Key | String |
|---|---|
| `foods_title` | Foods |
| `foods_search` | Search foods, dishes, restaurants |
| `foods_create_meal` / `_create_meal_sub` | Create a meal / Group foods you often eat together |
| `foods_custom` / `_custom_sub` | Add a custom food / For anything we don't have |
| `foods_yours` / `_yours_empty` | Your foods / Foods you make show up here and in search. |
| `foods_saved` | Saved meals |
| `foods_recent` | Recent |
| `foods_add_cd` | Add %s |
| `foods_credit` (new) | Food data from USDA FoodData Central and Open Food Facts. Open Food Facts data is available under the Open Database License. |

The credit sits at the end of the list in caption style. It is required by
ODbL for Open Food Facts rows (system design section 8).

### Edge cases
Empty everything: create rows, the empty line under Your foods, Saved meals and
Recent sections hidden when empty. Large text: row subtitles wrap. Offline: no
change.

### Tests
`FoodsViewModelTest` per action (events carry the right refs); builder test
that Blind rows carry Hidden calories. Previews: Full populated, Blind, empty.
`SURFACES`: `foods` (`showsCaloriesInFull = true`).

---

## Create a meal

### Route
`CreateMealRoute(fromDayIso: String? = null, fromSlot: MealSlot? = null)`,
`screen<>` (system design section 10), typeMap for `MealSlot`. From the Meal
sheet's "Save as a meal" it arrives pre-filled.

### State

```kotlin
data class CreateMealState(
    val name: String,
    val usualSlot: MealSlot,                   // default Lunch, as MB0
    val items: List<MealItemUi>,
    val total: Display,                        // calories on Foods
    val totalCount: Int,
    val totalMacros: List<MacroPart>,          // macroLine(Compact)
    val query: String,
    val picks: List<PickUi>,                   // up to 6
    val canSave: Boolean,                      // name not blank and at least one item
)
data class MealItemUi(val name: String, val amount: String, val macros: List<MacroPart>, val calories: Display)
data class PickUi(val ref: FoodRef, val name: String, val amount: String)
```

### Actions

| Action | Does |
|---|---|
| `PrefillArrived(slot, items)` | From `DiaryRepository.observeDay(fromDay)` once: name `My %s` (sentence name), `usualSlot = slot`, items copied. |
| `InputsArrived(policy, recent)` | |
| `NameTyped(text)` | |
| `UsualSlotPicked(slot)` | |
| `ItemRemoveTapped(index)` | |
| `SearchTyped(text)` | Debounced `FoodCatalog.search(q, 6)`; empty query lists recent foods. |
| `PickResultsArrived(query, rows)` | Dropped when stale. |
| `PickAddTapped(ref)` | Appends the food at its default portion, Estimate. |
| `SaveTapped` | Blank name: toast `Give the meal a name first.` No items: toast `Add at least one food first.` Otherwise `SavedMealRepository.save(name, usualSlot, item snapshots)`, toast, `Close`. |
| `BackTapped` | `Close`. |

### Data
`SavedMealRepository.save`, `FoodCatalog.search`, `DiaryRepository.observeRecentFoods`,
`DiaryRepository.observeDay` for prefill. Offline: search falls back to the cache.

### Visibility surfaces
Surface `Foods`.

| Number | Metric | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|
| Item calories, Total | calories | `300 kcal` / `405 kcal` | Hidden | Hidden | Hidden |
| Item and total macros | protein, carbs, fat | `10P 50C 6F` | omitted | omitted | omitted |

### Copy
`‹ Foods`; `Create a meal`; `Name`, placeholder `Usual breakfast`; `Usually at`
with Breakfast / Lunch / Snack / Dinner; `In this meal`; remove description
`Remove %s`; `Nothing yet. Add foods below.`; `Total`, sub `%1$d food` /
`%1$d foods` then ` · ` and the macro line; `Add foods`; placeholder `Search
foods`; add description `Add %s`; `Save meal` (disabled look until `canSave`,
still tappable so the toast explains). Toasts: `Give the meal a name first.` /
`Add at least one food first.` / `Meal saved. It's in the + sheet under Saved
meals.` Prefill name `My %s`.

### Edge cases
Prefill from an Uncategorized slot sets `usualSlot` to Lunch (Usually at has no
Uncategorized). Duplicate names are allowed. Leaving with unsaved changes just
leaves (no dialog in the handoff).

### Tests
`CreateMealViewModelTest`: prefill once, save guards and their toasts, remove,
stale picks dropped, save payload. Previews: empty, demo (Sunday breakfast with
two items) Full and Blind. `SURFACES`: `create-meal` (`showsCaloriesInFull = true`).

---

## Add a custom food

### Route
`CustomFoodRoute()`, `screen<>`.

### State

```kotlin
data class CustomFoodState(
    val name: String,
    val brand: String,
    val servingAmount: String,                 // default "1"
    val servingUnit: ServingUnit,              // Serving | G | Piece | Cup
    val fields: List<NutritionField>,          // only metrics whose level is not Hide
    val partial: Boolean,                      // 0 < fields.size < 5
    val labelOnly: Boolean,                    // fields empty: the Blind variant
    val canSave: Boolean,                      // name not blank
)
data class NutritionField(val metric: Metric, val label: StringResource, val unit: StringResource, val text: String)
```

Field order: Energy (kcal), Protein (g), Carbs (g), Fat (g), Fiber (g). The
filter uses `level(metric) != Hide` (configured level, as the prototype), so a
Delayed user still sees and fills them. Typed text is the user's own input, not a
policy number.

### Actions

| Action | Does |
|---|---|
| `PolicyArrived(policy)` | Rebuilds `fields`, keeping typed text. |
| `NameTyped`, `BrandTyped`, `ServingAmountTyped(text)` | Amount keeps digits and one decimal point. |
| `ServingUnitPicked(unit)` | |
| `NutritionTyped(metric, text)` | Digits and one decimal point. |
| `SaveTapped` | Blank name: toast `Give the food a name first.` Else `CustomFoodRepository.save(...)`: an empty field saves `null` (unknown), never `0`, so a blank energy never shows as `0 kcal`. Toast, `Close`. |
| `BackTapped` | `Close`. |

### Data
`CustomFoodRepository.save`. The new food appears first in Recent and in
Logger search. Offline: local.

### Components
`TextField`s, `PillChip` units, `NumberField`s with unit suffix, `EdgeCard`
notice for the Blind variant.

### Visibility surfaces
No resolved numbers. The fields themselves are gated: a metric at Hide has no
field. Blind hides all five, so the screen shows the "Nutrition stays out of
view" card.

| Field | Show / Soften / Delay | Hide |
|---|---|---|
| Energy, Protein, Carbs, Fat, Fiber | field shown | field and label removed |

### Copy
`‹ Foods`; `Add a custom food`; `Name`, placeholder `Lola's pandesal`; `Brand or
where it's from (optional)`, placeholder `Corner bakery`; `One serving is`,
amount description `Serving amount`, units serving / g / piece / cup;
`Nutrition per serving`, `Optional. Fill in what you know from the label.`;
labels Energy / Protein / Carbs / Fat / Fiber with kcal / g; partial note `Only
the numbers your mode shows are asked for here.`; Blind card title `Nutrition
stays out of view`; `Save food`. Toasts: `Give the food a name first.` / `%s
saved. It shows up in search now.`

**Conflict resolved**: the Blind body in the handoff reads "A name is enough to
save it. If you'd like the details on file, photograph the label and we'll keep
it without showing it.", with a "Photograph the label" button. Photo is not in
0.1, so the body is cut to `A name is enough to save it.` and the button is not
built. Reason: the copy must not offer what the build cannot do.

### Edge cases
Mode changes while open (from another device): fields rebuild and typed values
for removed fields are dropped on save. A `0` the user typed is saved as `0`.
Large text: fields stack one per row.

### Tests
`CustomFoodViewModelTest`: field lists for Full (5), Nutrient focus (Protein,
Fiber; partial note), Blind (label only); blank fields save null; input filters;
save toast uses the trimmed name. Previews: Full, Nutrient focus, Blind.
`SURFACES`: `custom-food`, with the fixture's Energy field holding `190`. The
scan joins text in tree order, so Full reads `Energy 190 kcal` and matches
(`showsCaloriesInFull = true`); Blind has no field, so nothing matches.
