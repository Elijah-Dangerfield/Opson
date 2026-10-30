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
| `LoggedFoodRoute(dayIso, entryId)` (api) | `screen` | A food's row on Today: the same detail, editing that one entry |

`LoggerStart` is `Actions`, `Describe`, `Saved`, `Number`. `focusSearch` and
`pickForMeal` move from `LoggerRoute` to `FoodSearchRoute`. A `prePickFoodId`
opens the food detail in place of the sheet; a `prePickSavedMealId` opens the
sheet on Saved meals.

Whatever the sheet opens takes its place (`goBack()` then `navigate`), so
Back from search, the scanner, the photo, Weigh-in or Fasting lands on Today.

## The sheet (13.4a, 13.4b)

"Add to {meal} ▾" with Close. The meal comes from `MealSlotPicker` as before
(Uncategorized fallback), a meal's + always uses its own. A meal's + on
Today, its header when it has food, and anywhere on an empty meal's card all
open this sheet straight on that meal; there is no meal sheet in between
(owner feedback, 2026-10-29: "fewer clicks is better"). The dropdown still
switches meal.

A 3-column grid, in this order, each shown only when it can run:

| Action | Shown when | Does |
|---|---|---|
| Search food | always | Full-screen search on this meal and day |
| Scan barcode | `release.barcode` | The scanner |
| Photo | `release.photo` | The photo estimate |
| Say or type it | always ("Describe it" with `release.voice` off) | The Describe pane, listening; with voice off, the box focused |
| Saved meals | always | The Saved pane; Add logs the meal at once |
| Copy a meal | yesterday has this meal | Copies yesterday's {meal} |
| Save as a meal | this meal has food on the day, and the day is open | `CreateMealRoute(fromDayIso, fromSlot)`, filled with the meal's foods |
| Water | always | One glass, with Undo |
| Weigh-in | always | `WeightRoute(focusInput = true)`, the field focused |
| Note | always | `MealNoteRoute`, the note focused |
| Fasting | `release.fasting`, offered in this mode and the timer on | `FastingRoute` |
| Exercise | `release.exercise`, energy out not hidden | `ExerciseRoute`, on the activity search, focused |

Fasting reads "Fasting", not "Start a fast": fasts start on their schedule and
there is no manual start to advertise.

**Each lands ready for input** (owner feedback, 2026-10-29: "lessen the
taps"). Where the next thing anyone does is type, the field has focus and the
keyboard is up once the screen or sheet is in (`FocusOnOpen` in
`libraries/ui`, which waits a frame and the enter animation, once per visit):
search, Describe it without voice, Weigh-in, Note and Exercise's activity
search. Water and Copy a meal already finish in one tap, Saved meals is a
list to tap, and the scanner opens on the camera. Photo and Packaged food stay
on their first step, since both start with a choice (camera or library, which
photo first) that a launched camera would take away. Fasting opens its page,
which is a status rather than a form. Today's own entries follow the same
rule: the day note and a meal's note focus their field, Activity's Add
exercise opens on the focused search, and the weight row, which shows a
weigh-in already logged, opens Weight without the keyboard.

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
(`frequentFoods`), the amount and macros; energy; a round +. In results for
typed words the first detail says instead why the food is there: "Favorite",
"Logged 3 times" or "Your food", and the person's own foods come first when
their use says so (`PersonalRanking`, `docs/plans/search-ranking.md`). + logs one item
at the last-used amount (`recentFoods`, else one serving), haptic confirm, a
snackbar "{food} added to {meal}" with Undo (`RecentLogEvents.undo`, run in
the app scope so it outlives the screen), and stays. Rows logged to this meal
since the screen opened draw ✓; tapping ✓ takes those items out again.

Tapping the name opens the food detail. "Can't find it?" closes the results
with Describe it (the sheet's Describe pane, with the term) and Save the name
(a name-only custom food, logged at once with Undo), and with
`release.submitFood` a row of its own, "Add it from the label": photos of the
pack's front and label, read and checked, logged as the person's own food
and sent to Opson's food database for review (`submit-food.md`). With no
results the 3.6 empty state offers the same three. The meal photo estimate is
not offered from search: it is the + sheet's Estimate a meal tile alone
(docs/decisions.md, 2026-10-27).

A food of the person's own that they sent for review reads "Unverified" in
place of its source badge until a reviewer approves it, in every mode (it is
a word, not a number); a catalog food they added and nobody has checked
reads the same. An approved one is Opson's: the "Opson" badge with the
verified check, with the search options on (2026-10-28).

Kept from the speed work: the catalog's five-minute client cache answers with
no debounce, otherwise 250 ms; the spinner only after a delay.

Pick for a meal (Create a meal): + and the name toggle a pick (✓), "Add to
this meal" pinned at the bottom hands the picks back through `MealPicks`.
No meal chip, no Saved meals chip, no scanner or Add it from the label, and a
miss offers only Save the name.

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

**Where the numbers come from**, under the cards: "From USDA FoodData
Central", "From Open Food Facts", the NEVO citation, "From Opson's food
database (OPDB)", "From Nutritionix", "Your own food", and on an entry with
no catalog food "An estimate" (Describe it, a photo) or "Numbers you entered"
(a quick number). A tap opens one line on what the source is, with Credits
(`CreditsRoute`) under it; Open Food Facts' line carries its ODbL credit in
full. NCCDB is not in the catalog (`food-data-sources.md`: a paid licence
nobody has signed), so nothing names it.

## An entry on the log (`LoggedFoodRoute`)

A food's row on Today opens its detail for that one entry
(`DetailOpen.Entry`); its ⋮ still opens the food's sheet (`today-actions.md`).
The view model reads the entry by its id on its day, never "the food": the
same food logged twice is two rows, and each opens, saves and removes itself
alone.

- **Numbers**: the entry's own, the snapshot it was logged with
  (`AmountFood.Logged`). A new amount in its own unit scales them by new ÷ old,
  as Change amount does; another unit (the food's, or a weight) by the new
  weight ÷ the logged one. A food corrected or deleted since changes nothing.
  The food, while it is still there, only lends its units, favorite and source.
- **Starts from** its amount and unit, meal, time (not "now") and how sure;
  how sure stays as logged unless the person picks another or types a
  weight. No numbers adds the meal's hunger answer.
- **Today with this** is the day without the entry as it was, plus the entry
  as it would be saved, so it is counted once.
- **Save changes** (pinned, in Add's place) is on only when something differs,
  and writes that entry alone (`DiaryRepository.updateItem`): a new meal moves
  it, a new time restamps it on its day, a time left alone keeps the exact
  instant. With meal groups off for the day its meal stays. A changed hunger
  answer goes to the meal's feeling. A closed day refuses the write and opens
  the closed sheet, as Add does.
- **Remove from log** under it tombstones that entry (`removeItem`); the page
  closes and the snackbar's Undo restores that id (`DiaryItemEditor.restoreItems`).
- **⋮**: the favorite and Report a problem (only with a food behind it; for a
  catalog food with `release.foodReports` on, the report sheet: what is wrong,
  the right numbers for the amount on screen, a label photo and a note, sent
  to Opson's reviewers; for the person's own food, or with the flag off, the
  bug report),
  Repeat this or Edit repeat (`release.repeats`), and Edit a copy, which opens
  `EditCopyRoute(dayIso, entryId)` with its "Use for this entry" switch; the
  page gives way to it, so the entry is never saved back from a stale page.
  Copy to another day stays on the row's ⋮ (`release.foodActions`).
- **Kinds**: a catalog or own food, Describe and photo lines, saved-meal and
  repeat entries all open here. A quick number or a name has no Amount row,
  nothing to star or report, and its meal, time and how sure still change.
- An entry changed under the page (another phone, a sync) starts it again
  from the new one; an entry gone closes it.

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
`food-detail-scan`, and on an entry `food-detail-entry`,
`food-detail-entry-changed`, `food-detail-entry-more`,
`food-detail-entry-quick`; Describe, Saved, photo and scanner keep theirs.

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
`LoggedFoodDetailTest` (of two identical entries, Save and Remove touch only
the one opened; the entry's own numbers; meal, time and hunger changes),
and the Describe, photo and barcode tests on `FoodLogger`.
