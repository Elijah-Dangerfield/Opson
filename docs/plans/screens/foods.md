# Foods plan

The Foods tab, Create a meal, and Add a food (with its Blind variant).
Read `shared.md` first.

## Source
- **Design v3 is the spec** (`docs/design/v3/CHANGES.md`, Foods row):
  `foods.png` (11.1), `foods-create-meal.png` (11.2), `foods-add-food.png`
  (11.3), `foods-add-food-blind.png` (11.4), and their notes in
  `docs/design/v3/manifest.json`. v3 wins wherever it disagrees with the
  handoff below.
- Handoff, for copy and logic v3 does not show: `README.md` section 4.9;
  `Phone.dc.html` `is.foods` (494 to 533), `is.mealbuilder` (683 to 719),
  `is.customfood` (720 to 749); `phone-logic.js` `makeRows`, `myFoods`,
  `saved`, `recents`, `mb`, `cf`, `cfFields`.

What v3 changed from the handoff:

| Was | v3 |
|---|---|
| "Create a meal" and "Add a custom food" as two list rows | Two side-by-side tiles, "Create a meal" and "Add a food" |
| Your foods, then Saved meals, then Recent | **Saved meals first**, then Your foods, then Recent (below the fold) |
| Create a meal has its own search and picks list | "Add a food" opens the **logger sheet in pick-for-this-meal mode**; one search UI |
| `‹ Foods` text link and a large title | Android top app bar: close (X) and the title |
| Custom food: Energy, Protein, Carbs, Fat, Fiber | Every field optional; Nutrient focus asks only protein and fiber; Blind shows no number fields |
| Blind: "photograph the label" | Photo is 0.2, so the button and its sentence are not built (below) |

## Design sync 2

The owner's export (`docs/plans/design-sync-2.md`, ids `foods-previews__*`)
is the spec now. It draws what v3 drew, and the build already matches it:

| Design sync 2 note | Build |
|---|---|
| Two side-by-side create tiles | Built (`FoodsScreen` tiles) |
| Saved meals before Your foods; Recent below the fold | Built (`FoodsStateBuilder` order) |
| Create a meal: "Add a food" hands off to the logger sheet in pick-for-this-meal mode | Built (`CreateMealEvent.OpenPicker`, `LoggerRoute(pickForMeal)`, `MealPicks`) |
| Add a food: every nutrition field optional | Built: blank saves `null` |
| Nutrient focus asks only protein and fiber | Built: `AddFoodStateBuilder.asked` |
| Blind has no number fields at all | Built: `labelOnly`, the eye-off card |
| "Scan the label instead", "Photograph the label" | Not built: label scan is out of scope (`docs/plans/food-data-import.md`) |

Kept on purpose, with the reason:
- **Fiber in Full numbers.** 11.3 draws four fields; the build asks five,
  because fiber is a tracked macro, Nutrient focus asks for it, and a Full
  user should be able to enter what a Nutrient focus user can.
- **The data credit** under the cards. ODbL requires it for Open Food Facts
  rows; the design leaves it out.
- **Unit chips** read `piece`, `grams`, `cup`. The design's "roll" is the
  pandesal's own word, not a fixed chip.
- **Saved meal subtitles** keep each food's own capitalisation ("Oat porridge
  with milk, Banana"). The design lowercases them, which would also lowercase
  brands and names.

Tests that pin the field rules: `AddFoodTest` (Full asks five, Nutrient focus
asks protein and fiber, Blind and every hiding mode ask none, the view model
drops a field and its typed text when the mode stops asking, a saved Nutrient
focus or Blind food carries no number it didn't ask for).

Routes in `:features:foods` (`com.opson.features.foods`); impl in
`:features:foods:impl` (`com.opson.features.foods.impl`).

---

## Foods tab

### Route
`FoodsRoute : TabRoute()`, `screen<>`.

### Layout (v3 11.1)
Large title "Foods"; a sticky search bar (a button that opens the logger with
search focused); two tiles side by side (Create a meal, pink tile; Add a food,
brown tile); for an active Builder, Verify a food (a card in the Builders
color, `VerifyFoodAvailable`, which opens the scanner reading labels); the
Favorites card; the Saved meals card; the Your foods card; the Recent card; the
data credit in caption type at the very end.

Favorites are the foods starred on their detail (`Settings.favoriteFoods`,
newest star first). A food logged before reads as it was last logged; a
catalog food never logged reads at its first serving (or 100 g) from the copy
the phone keeps (`FoodCatalog.observeFavorites`); the person's own food as
Your foods has it. A star whose food the phone does not have yet waits. Empty,
the card says to tap the star on a food. A catalog row a person checked
against its pack carries the Verified badge, here and in Recent.
 Rows: bold name, a muted
subtitle, the bare calories ("445"), a 40 dp tinted round + with a 48 dp
target. Each card's section label sits inside it, above a hairline.

### State

```kotlin
data class FoodsState(
    val loading: Boolean = true,
    val savedMeals: List<FoodsRowUi> = emptyList(),
    val yourFoods: List<FoodsRowUi> = emptyList(),
    val recent: List<FoodsRowUi> = emptyList(),
)
data class FoodsRowUi(val target: FoodsTarget, val name: String, val sub: String, val calories: Display)
sealed interface FoodsTarget { data class Meal(val id: String); data class Food(val ref: FoodRef) }
```

Subtitles: a saved meal lists its foods' short names ("Oat porridge, banana,
coffee"); a custom food is `brand · 1 roll` (brand left out when blank); a
recent food is its amount. `FoodsStateBuilder.build(inputs, policy)`.
Calories resolve on `Surface.Foods` in a bare context with no goal, so they
show at Show and hide otherwise.

### Actions

| Action | Does |
|---|---|
| `InputsArrived(inputs)` | Custom foods, saved meals, recent foods. |
| `PolicyArrived(policy)` | |
| `SearchTapped` | Event `OpenLogger(LoggerRoute(focusSearch = true))`. |
| `CreateMealTapped` | `OpenCreateMeal`. |
| `AddFoodTapped` | `OpenAddFood`. |
| `RowTapped(target)` | The row or its +. `OpenLogger` with the food (`prePickFoodId`, `prePickIsCustom`) or the meal (`prePickSavedMealId`) already picked. |

Tapping the row or its + opens the Logger with it picked, so the user still
chooses the meal and the day, and adding takes one more tap. A recent item
with no food behind it (a quick number, a name-only line) is not listed, since
there is nothing to pick. No tab-reselect scroll: the shell has no reselect
hook yet.

### Events
`OpenLogger(route: LoggerRoute)`, `OpenCreateMeal`, `OpenAddFood`.

### Data
`CustomFoodRepository.observeAll()`, `SavedMealRepository.observeAll()`,
`DiaryRepository.recentFoods(limit = 6)`, `VisibilityPolicySource.policy`.
All local, so offline changes nothing.

### Visibility surfaces
Surface `Foods`.

| Number | Metric | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|
| Saved meal, Your foods and Recent row calories | calories | `445` | Hidden | Hidden until reveal / never for Coast | Hidden |

### Copy (`strings_foods.xml`)

| Key | String |
|---|---|
| `foods_title` | Foods |
| `foods_search` | Search foods, dishes, restaurants |
| `foods_create_meal` / `_create_meal_sub` | Create a meal / Group foods you eat together |
| `foods_add_food` / `_add_food_sub` | Add a food / Something search doesn't have |
| `foods_saved` | Saved meals |
| `foods_yours` / `_yours_empty` | Your foods / Foods you make show up here and in search. |
| `foods_recent` | Recent |
| `foods_add_cd` | Add %1$s |
| `foods_credit` | Food data from USDA FoodData Central and Open Food Facts. Open Food Facts data is available under the Open Database License. |

The credit is required by ODbL for Open Food Facts rows (system design
section 8).

### Edge cases
Nothing saved: the tiles, the Your foods card with its empty line, and the
credit; Saved meals and Recent are left out when empty. Large text: tiles grow
taller together, subtitles wrap.

### Tests
`FoodsViewModelTest` per action (events carry the right refs);
`FoodsStateBuilderTest`: section order and subtitles, Blind rows carry Hidden
calories, Delayed hides before the reveal. Previews: `Foods` (Full), dark,
Blind, empty. Leak surface `foods` (`showsCaloriesInFull = true`).

---

## Create a meal

### Route
`CreateMealRoute(fromDayIso: String? = null, fromSlot: MealSlot? = null)`,
`screen<>`, typeMap for `MealSlot?`. Opened from the Foods tile, and from
the add sheet's "Save as a meal" (a meal's + on Today opens it on that meal), which arrives pre-filled.

### Layout (v3 11.2)
Top app bar with a close X and "Create a meal". Card 1: Name field, "Usually
at" chips (Breakfast, Lunch, Snack, Dinner; the picked one tinted with a
check). Card 2, "In this meal": item rows (name, amount, bare calories, a
round X), then a heavy rule and Total (macro line under it, calories on the
right). Card 3: "+ Add a food". Save meal pinned to the bottom.

### State

```kotlin
data class CreateMealState(
    val name: String = "",
    val usualSlot: MealSlot = MealSlot.Lunch,
    val items: List<MealItemUi> = emptyList(),
    val total: Display = Display.Hidden,       // calories on Foods
    val totalMacros: List<MacroPart> = emptyList(), // macroLine(Compact)
    val canSave: Boolean = false,              // name not blank and at least one item
)
data class MealItemUi(val name: String, val amount: String, val calories: Display)
```

`CreateMealStateBuilder.build(draft: MealDraft, policy)`, where `MealDraft`
is the name, slot and `List<LogDraft>` the view model holds.

### Actions

| Action | Does |
|---|---|
| `PolicyArrived(policy)` | |
| `NameTyped(text)` | |
| `UsualSlotPicked(slot)` | |
| `ItemRemoveTapped(index)` | |
| `AddFoodTapped` | Event `OpenPicker(requestId)`: the logger sheet as `LoggerRoute(pickForMeal = requestId)`. |
| `FoodsPicked(items)` | From `MealPicks` when `requestId` matches: appended, then `consumed`. |
| `SaveTapped` | Only reachable when `canSave`. `SavedMealRepository.create(name.trim(), usualSlot, items)`, toast, `Close`. |
| `CloseTapped` | `Close`. |

Prefill happens once in `init`: with `fromDayIso` and `fromSlot`, the day's
items in that slot are copied (`LogItem.toDraft()`), the name is `My %1$s`
(sentence name) and `usualSlot` is the slot (Uncategorized falls back to
Lunch, since "Usually at" has no Uncategorized).

### The pick-for-this-meal hand-off
`LoggerRoute` gets `pickForMeal: String? = null`. When set, the logger sheet:
- keeps its picks in a basket of its own, so the person's real basket is left alone;
- shows "Add to / This meal" with no meal dropdown and no day;
- shows no method chips, and offers only "Save the name only" when a search misses (Describe, Yesterday's and Just a number write to the diary);
- toggles a food when its name is tapped (the amount sheet logs, so it is not opened);
- on "Add to meal", publishes the picks' drafts through `MealPicks.picked(PickedForMeal(requestId, drafts))`, clears its basket and closes. Nothing is written to the diary.

`MealPicks` (in `:libraries:diary`, impl in `:libraries:diary:impl`) is a
`StateFlow<PickedForMeal?>` with `picked` and `consumed`, the same shape as
`RecentLogEvents`: the Create a meal view model is alive under the sheet, but a
state rather than an event means a late collector still gets it.

### Data
`SavedMealRepository.create`, `DiaryRepository.observeDay` for the prefill,
`MealPicks`, the policy. All local.

### Visibility surfaces
Surface `Foods`.

| Number | Metric | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|
| Item calories, Total | calories | `300` / `445 kcal` | Hidden | Hidden | Hidden |
| Total macros | protein, carbs, fat | `13P 80C 8F` | omitted | omitted | omitted |

When the macro line is empty, the total's subtitle is the count ("3 foods"),
so a Blind total still says something.

### Copy
`Create a meal`; close description `Close`; `Name`, placeholder `Usual
breakfast`; `Usually at`; `In this meal`; remove description `Remove %1$s`;
`Nothing yet. Add a food below.`; `Total`; `%1$d food` / `%1$d foods`; `Add a
food`; `Save meal`. Toast `Meal saved. It's in the + sheet under Saved
meals.` Prefill name `My %1$s`. The logger's title in pick mode: `This meal`;
its button `Add to meal` ("Add to this meal" crowded out the picked count).

### Edge cases
Duplicate names are allowed. Leaving with unsaved changes just leaves (no
dialog in the design). Picking the same food twice adds it twice (two
portions).

### Tests
`CreateMealViewModelTest`: prefill once, Uncategorized prefill picks Lunch,
picks for another request are ignored, picks appended and consumed, remove,
save guard and payload. `CreateMealStateBuilderTest`: totals, Blind hides
calories and macros. Logger: `LoggerViewModelTest` covers pick mode (own
basket, no chips, name tap toggles, Add hands the drafts back and writes
nothing). Previews: `FoodsCreateMeal` (demo, Full), dark, Blind, empty. Leak
surface `foods-create-meal` (`showsCaloriesInFull = true`).

---

## Add a food

### Route
`AddFoodRoute()`, `screen<>`.

### Layout (v3 11.3, 11.4)
Top app bar with a close X and "Add a food". Card 1: Name, "Brand or where
it's from · optional", "One serving is" (an amount field and unit chips).
Card 2, when any field is asked: "Per serving" with "Fill in what you know"
on the right, then the number fields two to a row. Card 2 in Blind (no
fields): an eye-off tile, "Numbers are hidden", and one sentence.
Save food pinned to the bottom.

### State

```kotlin
data class AddFoodState(
    val name: String = "",
    val brand: String = "",
    val servingAmount: String = "1",
    val servingUnit: ServingUnit = ServingUnit.Piece,    // Piece | Grams | Cup
    val fields: List<NutritionField> = emptyList(),      // only metrics whose level is not Hide
    val canSave: Boolean = false,                        // name not blank
) { val labelOnly: Boolean get() = fields.isEmpty() }
data class NutritionField(val metric: Metric, val text: String)
```

Field order: Calories (kcal), Protein, Carbs, Fat, Fiber (g). The filter is
the configured `level(metric) != Hide`, as the prototype: a Delayed user still
fills them in, and Nutrient focus (protein and fiber softened, the rest
hidden) asks only protein and fiber. Typed text is the user's own input, not a
policy number. v3 draws three unit chips; its first reads "roll" for the
pandesal, which is that food's own word, so the build offers `piece`, `grams`,
`cup`, stored as `piece`, `g`, `cup` (grams as `g`, so `CustomFood.toDraft`
fills grams).

### Actions

| Action | Does |
|---|---|
| `PolicyArrived(policy)` | Rebuilds `fields`, keeping typed text. |
| `NameTyped`, `BrandTyped`, `ServingAmountTyped(text)` | Amount keeps digits and one decimal point. |
| `ServingUnitPicked(unit)` | |
| `NutritionTyped(metric, text)` | Digits and one decimal point. |
| `SaveTapped` | Only reachable when `canSave`. `CustomFoodRepository.create(...)`: a blank field, or one the mode does not ask, saves `null` (unknown), never `0`. A blank amount saves 1. Toast, `Close`. |
| `CloseTapped` | `Close`. |

### Data
`CustomFoodRepository.create`. The food shows in the Foods tab and first in
Logger search (the logger puts custom matches before catalog ones).

### Visibility surfaces
No resolved numbers. A metric at Hide has no field; Blind hides all five.

| Field | Show / Soften / Delay | Hide |
|---|---|---|
| Calories, Protein, Carbs, Fat, Fiber | field shown | field and label removed |

### Copy
`Add a food`; `Name`, placeholder `Lola's pandesal`; `Brand or where it's from
· optional`, placeholder `Corner bakery`; `One serving is`, amount description
`Serving amount`, units `piece` / `grams` / `cup`; `Per serving`,
`Fill in what you know`; labels Calories / Protein / Carbs / Fat / Fiber with
kcal / g; Blind card `Numbers are hidden`, `A name is enough to save
it.`; `Save food`. Toast `%1$s saved. It shows up in search now.`

**Cut for 0.1**: v3's "Scan the label instead" (Full) and "Photograph the
label" with its sentence (Blind) are photo features, which are 0.2. Not
rendered, and the Blind body stops at "A name is enough to save it." Reason:
the copy must not offer what the build cannot do.

### Edge cases
Mode changes while open: fields rebuild, and text for a removed field is not
saved. A `0` the user typed saves as `0`. Large text: the two-up fields keep
their row and the labels wrap.

### Tests
`AddFoodViewModelTest`: Full asks five, Nutrient focus asks protein and fiber,
Blind asks none; blank and unasked fields save null; input filters; save toast
uses the trimmed name. Previews: `FoodsAddFood` (Full), dark, Nutrient focus,
`FoodsAddFoodBlind`. Leak surfaces `foods-add-food` (Calories field holding
120, so Full reads `120 kcal`; `showsCaloriesInFull = true`) and
`foods-add-food-blind` (`false`).

## Recipes

Behind `release.recipes`; the whole design is `docs/plans/recipes.md`. On the
Foods tab a Recipes card sits under Saved meals: each recipe with "Makes 6
servings · 4 ingredients" (and "Shared" when it is), one serving's calories,
then New recipe and Scan a recipe code. Drawn even with none, with a line
saying what a recipe is for. Routes (`RecipeRoutes.kt`, all registered by
`RecipesEntryPoint`): `RecipeEditRoute` (New/Edit recipe: name, servings
stepper, ingredients through search in pick-for-this-meal mode, the whole
recipe's and one serving's numbers, notes), `RecipeRoute` (its page: Log a
serving, Share, Edit, Delete), `RecipeShareRoute` (sheet: what becomes public,
then the link and QR code), `PublicRecipeRoute` (someone's shared recipe, by
code), `LogRecipeRoute` and `ReportRecipeRoute` (sheets) and `ScanRecipeRoute`
(QR only). Previews are in `RecipePreviews.kt`; leak surfaces `foods-recipes`,
`recipe-edit`, `recipe`, `recipe-public`, `recipe-log`.

## Repeating

Behind `release.repeats`. The owner asked for foods that log themselves every
day or on chosen days; the storage, timing and sync decisions are in
`docs/decisions.md`, 2026-10-29.

### Where it is
- The Foods tab: a **Repeating** card under Saved meals. Each repeat is a row,
  its name over "Breakfast · Weekdays · until Nov 3" ("Paused · ..." when it is),
  with its foods' calories through the policy, and the row or its pencil opens
  it. **New repeat** ends the card. With none yet the card says what a repeat is.
- Today: a food's sheet offers **Repeat this** above Delete (also with
  `release.foodActions` off, and on a closed day, since it changes nothing on
  the day), or **Edit repeat** for a food a repeat logged. A food a repeat
  logged carries a small loop (`AppIcons.Repeat`, "Repeats") before its line.

### The editor (`RepeatRoute(ruleId?, fromDayIso?, fromEntryId?)`, a `screen<>`)
- What: the foods, each removable, and Add a food, which opens search in
  pick-for-this-meal mode like Create a meal. Or a saved meal, one of the chips
  below: then the meal's current foods log while it exists. Adding or removing
  a food makes the list the repeat's own.
- Meal (the four meals), Days (Every day, Weekdays, Weekends, and a toggle per
  day), Starts (Today or Tomorrow, until it has started), Ends (No end, after a
  week, 2 weeks or a month from the start, or the date it already had).
- A saved repeat adds Pause and Delete repeat. Delete keeps what it logged.
- Under the form, what saving does: "Saving logs it for today too.", "The one
  you logged today counts for today." (Repeat this from today), "Today's is in
  your log. Changes start with the next one." (an edit), "It won't log again
  today. Changes start with the next one." (an edit after today's food was
  removed), the next day it logs, or that it is paused or past its end. Whether
  it has had today comes from its ledger (`RepeatRepository.observeHasRun`), not
  from the day's entries, since the ledger is what stops it logging again.
  Then how it works: it logs when Opson
  is opened that day, missed days stay empty, and its foods edit and delete
  like any.
- Saved: "Repeat saved. Today's breakfast is logged." when saving logged today.
- From Repeat this: the entry's food and amount, its meal (Uncategorized takes
  the meal its time falls in), every day from today. On today it counts as
  today's, so the food is not logged twice.
- Repeats live on this phone. Signing out keeps them for the same account;
  another account signing in on the phone deletes them (`RepeatOwner`).
- Copy yesterday, copy to tomorrow and copying a meal leave out a repeat's
  foods when that repeat logs on the day copied onto (it falls on it, and the
  day is today or later, or it already logged there). A repeat that skips that
  day is copied like any food.

### Tests
`RepeatRepositoryImplTest` (diary impl) for the logging rules, `RepeatsTest`
for the ids and schedules, `RepeatTest` and `FoodsTabTest` here,
`ItemSheetRepeatTest` on Today. Previews `FoodsRepeating` (and dark, empty),
`FoodsRepeat` (dark, Blind, empty, edit), `FoodActionsRepeat` and
`FoodActionsEditRepeat`. Leak surfaces `foods-repeating`, `foods-repeat` and
`foods-repeat-edit`, all `showsCaloriesInFull = true`.
