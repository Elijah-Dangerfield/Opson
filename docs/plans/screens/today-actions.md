# Today actions plan (design sync 4)

The Today actions area of `docs/plans/design-sync-4.md`: the header and its
calendar, select mode, dragging foods between meals, the food ⋮ sheet and
Edit a copy.

## Source
- Export: `/Users/elijahdangerfield/Desktop/Opson Assets/design-sync-4/`
- Screens: `today-calendar` 13.3a, `today-select-v4` 13.3b,
  `today-drag-move` 13.3c, `food-actions` 13.3d, `edit-a-copy` 13.3e
- Behaviour: `manifest.json` › `behaviorBySection` › "13 Round 4" › TODAY

## Flags
- Header and calendar: a refinement, no flag.
- Select mode and drag: `release.todaySelect`, as before.
- The row ⋮, the sheet's new actions and Edit a copy: a new flag,
  `release.foodActions` (`TodayFoodActionsEnabled`), off by default. Off, a
  row tap opens the same sheet with only Delete, as the old item sheet did.

## Header (13.3a)
- `‹ Today ▾ ›` then ⋮. The title is Yesterday, Today, Tomorrow or the date
  ("Sun, Oct 25"), never a second line. No calendar button.
- Tapping the title opens `CalendarRoute`, a `dialog<>` that drops from the
  top: ‹ month ▴ › over a card with the month, dots on logged days, a hairline,
  then Jump to today and the legend. ‹ › page months; the title or a tap on
  the scrim closes it. No Done button.
- `CalendarViewModel` stays; Done goes, Jump to today stays.

## Select mode (13.3b)
- Long-press a food (combinedClickable plays the haptic). Top bar:
  ✕, "N selected", Select all (accent text). Rows: a round check, the food,
  kcal, a drag handle. The meal header still toggles its foods.
- The bottom bar replaces the old totals panel: Move, Copy to…, Delete. Each
  opens its step (meal chips, day and meal chips, "Remove N foods?") in a
  panel above the bar. Save as meal goes, since the design has no place for
  it (`CreateMealRoute.entryIds` keeps its tests, no caller for now).
- Selection totals go with the panel; the count stays.

## Drag (13.3c)
- In select mode, drag a row by its handle. A handle on an unpicked row picks
  it first. Every picked food moves. A card follows the finger: "Apple + 1
  more / 2 foods". The meal under the finger outlines in accent with a dashed
  "Move to Dinner" box; the foods being dragged dim.
- Targets come from `listState.layoutInfo` (meal rows are keyed
  `meal-<slot>`), so they track scrolling. Near the top or bottom edge the list
  scrolls by itself while dragging.
- Drop: `TodayAction.FoodsDropped(slot)` moves them with `moveItems` (times
  kept), ends selecting and shows "Moved 2 foods to dinner" with Undo. Undo
  moves each food back to the meal it came from. Move on the bottom bar does
  the same, so the drag has a non-drag path. Haptic `confirm()` on drop,
  `tick()` when the target changes.
- Accessibility: the handle is out of the tree; each selectable row has a
  "Move to another meal" custom action that opens the Move step.

## Food ⋮ sheet (13.3d)
`ItemSheetRoute` stays, the sheet is rebuilt.
- Header: the name, then "2 tbsp · Weighed · Snack · 190 kcal". The kcal goes
  through the policy (Today surface); hidden drops it and its separator.
- Rows: Change amount, Move to another meal, Copy to another day, Edit a copy
  (with "Fix or change the details. Saved to Your foods."), Add to favorites
  (Remove from favorites when starred), Delete.
- Change amount: a quantity field in the item's unit; Save scales the
  nutrients, grams and micros by new ÷ old and keeps the precision. Only for
  an item with a quantity.
- Move: meal chips; a pick moves it and closes with a toast.
- Copy: Yesterday, Today, Tomorrow and meal chips, then Copy.
- Favorites: `Settings.toggleFavorite` with the food ref key; only for an item
  logged from a food.
- Delete: as before, a tombstone and a toast.

## Edit a copy (13.3e)
`EditCopyRoute(dayIso, entryId)` in `:features:foods`, a `screen<>`.
- Name "<name> (mine)", Brand (a custom food's own brand, else blank),
  Serving read only ("2 tbsp = 32 g"), then Per serving: Calories, Protein,
  Carbs, Fat, Fiber pre-filled from the entry (one serving is the logged
  amount). A changed field says "Was 190" in accent on an accent-tint row.
- A field shows only when its metric is Show on the Foods surface; a hidden
  one is copied as it was, so no number appears that the mode hides.
- Use for this entry, on by default: "Today's snack switches to your copy".
- Save: `CustomFoodRepository.create`, then with the switch on,
  `DiaryRepository.updateItem` points the entry at the copy with its
  nutrients. The source food is never written. Both sync through the outbox.

## Tests
- `TodayViewModelTest`: long-press, toggles, select all, Move, Copy, Delete,
  drop, Undo of a move, the title opening the calendar.
- `ItemSheetTest` (in `SheetsTest`): each action, flag off.
- `EditCopyTest`: prefill, Was hints, save with and without the switch,
  hidden metrics carried over, the original untouched.
- Leak test: `food-actions`, `food-actions-amount`, `edit-a-copy`,
  `today-calendar`, `today-drag-move`.
- Previews: `TodayCalendarPreview`, `TodaySelectV4Preview`,
  `TodayDragMovePreview`, `FoodActionsPreview`, `EditACopyPreview`.
