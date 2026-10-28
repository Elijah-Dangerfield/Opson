# Adding (design sync 4, 13.4a to 13.4d)

The + half sheet with its grid of actions, full-screen search where + logs at
once, and the food detail with a typed amount. Replaces the pick list
("basket") everywhere. Read `logger.md` and `describe.md` first; this
supersedes their Search pane, basket and amount sheet sections.

Spec: `add-sheet-half.png`, `add-sheet-expanded.png`, `search-full.png`,
`food-detail-v4.png` and the "ADD SHEET AND SEARCH" behaviour in the design
sync 4 manifest. `logger-search-empty.png` (3.6) is still the empty state,
without "Photograph the package" (the package scan is not built).

No release flag: this reworks the existing logger. Each action keeps the flag
it already had.

## Routes

| Route | Kind | Opened from |
|---|---|---|
| `LoggerRoute(slot, dayIso, prePickFoodId, prePickIsCustom, prePickSavedMealId, start, describeText)` | `bottomSheet`, 60% then full | The nav +, a meal's +, reminders, widgets, Today |
| `FoodSearchRoute(slot, dayIso, pickForMeal)` (api) | `screen` | The sheet's Search food, the Foods tab, Create a meal |
| `FoodDetailRoute(foodId, isCustom, source, dayIso, slot)` (impl) | `screen` | A row's name in search, a barcode hit, a Foods or Today pre-pick |

`LoggerStart` is `Actions`, `Describe`, `Saved`, `Number`. `focusSearch` and
`pickForMeal` move from `LoggerRoute` to `FoodSearchRoute`. A `prePickFoodId`
opens the food detail in place of the sheet; a `prePickSavedMealId` opens the
sheet on Saved meals.

Whatever the sheet opens takes its place (`goBack()` then `navigate`), so
Back from search, the scanner, the photo, Weigh-in or Fasting lands on Today.

## The sheet (13.4a, 13.4b)

"Add to {meal} ▾" with Close. The meal comes from `MealSlotPicker` as before
(Uncategorized fallback), a meal's + always uses its own.

A 3-column grid, in this order, each shown only when it can run:

| Action | Shown when | Does |
|---|---|---|
| Search food | always | Full-screen search on this meal and day |
| Scan barcode | `release.barcode` | The scanner |
| Photo | `release.photo` | The photo estimate |
| Say or type it | always ("Describe it" with `release.voice` off) | The Describe pane |
| Saved meals | always | The Saved pane; Add logs the meal at once |
| Copy a meal | yesterday has this meal | Copies yesterday's {meal} |
| Water | always | One glass, with Undo |
| Weigh-in | always | `WeightRoute` |
| Note | always | `MealNoteRoute` |
| Fasting | `release.fasting`, offered in this mode and the timer on | `FastingRoute` |
| Exercise | `release.exercise`, energy out not hidden | `ExerciseRoute` |

Fasting reads "Fasting", not "Start a fast": fasts start on their schedule and
there is no manual start to advertise.

At 60% the bottom edge fades over whatever is below. Dragging up grows the
sheet to full height (`expandOnDragUp`), which shows Recent under the grid:
the last six foods with a one-tap +. A + logs at the food's last amount and
draws ✓; Today offers Undo from `RecentLogEvents`. Drag down closes.

## Search (13.4c)

Back arrow, the search field (barcode button inside while empty, behind
`release.barcode`), then chips: the meal dropdown, All, Your foods, Saved meals.

- **All**: as the Search pane was. Usual at {meal} and Recent before typing,
  your foods then the catalog while typing. `release.searchOptions` keeps its
  options button and category tabs under the chips.
- **Your foods**: custom foods and what this person has logged, no server call.
- **Saved meals**: saved meals by name or food; + logs the whole meal.

A row: name, then "Your usual" when the amount is this person's habit
(`frequentFoods`), the amount and macros; energy; a round +. + logs one item
at the last-used amount (`recentFoods`, else one serving), haptic confirm, a
snackbar "{food} added to {meal}" with Undo (`RecentLogEvents.undo`, run in
the app scope so it outlives the screen), and stays. Rows logged to this meal
since the screen opened draw ✓; tapping ✓ takes those items out again.

Tapping the name opens the food detail. "Not finding it?" closes the results
with Describe it (the sheet's Describe pane, with the term) and Save the name
(a name-only custom food, logged at once with Undo). With no results the 3.6
empty state offers the same, and with `release.submitFood` also Add this
food: photos of the pack's front and label, read and checked, logged as the
person's own food and sent for review (`submit-food.md`). The meal photo
estimate is not offered from search: it is the + sheet's Photo tile alone
(docs/decisions.md, 2026-10-27).

Kept from the speed work: the catalog's five-minute client cache answers with
no debounce, otherwise 250 ms; the spinner only after a delay.

Pick for a meal (Create a meal): + and the name toggle a pick (✓), "Add to
this meal" pinned at the bottom hands the picks back through `MealPicks`.
No meal chip, no Saved meals chip, no scanner or Add this food, and a miss
offers only Save the name.

## Food detail (13.4d)

Back, the name, ⋮ (Meal, Time, Save to favorites). Then:

- **Amount**: a typed number, the unit dropdown (each unit with its grams,
  then "Custom size…"), and serving chips: one and two of the first household
  unit with their grams, and 100 g.
- **How sure**: Estimate, Weighed, From label. Typing an amount turns Estimate
  into Weighed; a chip or the unit menu does not; once the person picks one
  themselves, typing leaves it.
- **Energy card**: kcal, "All nutrients ›" (opens the vitamins and minerals
  below, only when micros are visible), Protein, Carbs and Fat tiles in their
  colours, and "Today with this 1,435 kcal".
- **Add to {meal}** pinned.

`Precision.Label` ("label") is new in `:libraries:diary` and on the server
(V21 widens the check). A 0.1 client reads it as Estimate.

"Today with this" carries the day's ± (`Day.eatenUncertainty`) with this food
counted at the how-sure picked, so weighing it narrows the ±. A softened day
shows its word and no ±; `loggedAs` reads From label as a label.

## Visibility (surface `Logger`)

| Number | Show | Soften | Delay | Hide |
|---|---|---|---|---|
| Row energy, macros | `205`, `4P 45C 0F` | hidden | hidden | hidden |
| Detail kcal, macro tiles | `190 kcal`, `8 g` | hidden (no goal) | hidden | hidden |
| Today with this | `1,435 kcal` | word | hidden | row gone |
| All nutrients | percents | words | hidden | link and card gone |
| Grams on units and chips | amounts of food | shown | shown | shown |

The grid itself shows no number. Leak surfaces: `add-sheet`, `add-sheet-expanded`, `search-full`,
`search-empty`, `search-saved-meals`, `search-tabs`, `search-options`,
`food-detail`, `food-detail-nutrients`, `food-detail-units`,
`food-detail-scan`; Describe, Saved, photo and scanner keep theirs.

## Removed

`BasketStore`, `BasketCommitter` (now `FoodLogger`, which writes a given list),
the basket footer, "Pick and keep going", the quick ½ 1 1½ 2 chips and
stepper, "This portion" and "Your day with this" cards, the method chips and
the Search pane inside the sheet, and their previews, strings and leak
surfaces.

## Tests

`LoggerViewModelTest` (grid actions by flag and policy, recent +, Describe and
Saved log directly), `FoodSearchViewModelTest` (+ logs and stays, last-used
amount, ✓ and take back, Undo event, cache hit with no debounce, scopes, pick
for a meal), `FoodSearchStateBuilderTest`, `FoodDetailTest` (typing switches
Estimate to Weighed, chips do not, a manual pick sticks, Add logs one item),
and the Describe, photo and barcode tests on `FoodLogger`.
