# Today menus and sheets plan

The Add menu, Day menu, Calendar, Meal sheet (with its note and how-it-felt
sheets), Water sheet, and two small sheets the handoff does not have: Item and
Day note. Read `shared.md` and `today.md` first.

## Source
- Handoff: `README.md` section 4.2.
- Prototype markup: `Phone.dc.html` `sheet.menu` (line 900), `sheet.mealMenu` (923), `sheet.mealNote` (935), `sheet.mealFeel` (953), `sheet.waterMenu` (966), `sheet.cal` (981).
- Logic: `phone-logic.js` `addMenuItems`, `dayItems`, `mealMenu`, `note`, `feel`, `wt`, `cal`, `cells`.

All classes live in `:features:today:impl`, package `com.opson.features.today.impl.<sheet>`.
Routes only Today opens live there too (as the logger keeps `AmountSheetRoute`
in its impl); `MealNoteRoute` and `DayCloseRoute`, which other features open,
are in `:features:today`, package `com.opson.features.today`. Every sheet
passes `scrollableContent = true` and has a separate `XSheetContent(state, onAction)`.

Common to every sheet below: components are `SheetHeader` (title plus a Close
icon button), `ListRow` with a muted `IconTile`, `PillChip`, `TextArea`,
`NumberField` and buttons from `:libraries:ui`; data is `DiaryRepository` and
`SettingsRepository`, local first, so offline changes nothing; `Dismiss` is the
event that pops the sheet; and a sheet whose day or entry no longer exists
(cleared or removed on another device) dismisses itself on the next emission.

---

## Add menu and Day menu (popovers inside Today)

Not nav destinations: they anchor to the header buttons, so they are
`MenuPopover`s driven by `TodayState.menu` and handled by `TodayViewModel`.
Closing on outside tap sends `MenuDismissed`.

```kotlin
enum class MenuKind { Add, Day }
sealed interface TodayMenu {
    data class Add(val items: List<AddMenuItem>) : TodayMenu
    data class Day(val items: List<DayMenuItem>, val clearArmed: Boolean) : TodayMenu
}
enum class AddMenuItem { Food, DescribeIt, GlassOfWater, WeighIn, Note }
sealed interface DayMenuItem {
    data class CloseDay(val isToday: Boolean) : DayMenuItem
    data object Nutrients : DayMenuItem
    data object CopyPreviousDay : DayMenuItem
    data object CopyToTomorrow : DayMenuItem
    data object AddNote : DayMenuItem
    data class MealGroups(val on: Boolean) : DayMenuItem
    data class Water(val shown: Boolean) : DayMenuItem
    data object ClearDay : DayMenuItem
}
```

Built by `TodayStateBuilder`: `GlassOfWater` only when water is not `Hide`;
`CloseDay` not on a future day; `CopyToTomorrow` not at today + 6. Scan,
Photo and Start/End fast are not built (0.2 / Later).

| Action | Does |
|---|---|
| `AddTapped` | `menu = Add`. |
| `DayMenuTapped` | `menu = Day`, `clearArmed = false`. |
| `MenuDismissed` | `menu = null`, `clearArmed = false`. |
| `AddFoodTapped` | Close menu, `OpenLogger(day, slot = null)`. |
| `AddDescribeTapped` | Close menu, `OpenLog(day, MealSlot.defaultFor(now, groupsOn), Describe)` (`LogRoute`). |
| `AddGlassOfWaterTapped` | Close menu, `DiaryRepository.addWater(day, glassMl)`, toast. |
| `AddWeighInTapped` | Close menu, `OpenWeight`. |
| `AddNoteTapped` / `DayNoteTapped` | Close menu, `OpenDayNote(day)` (not Day close, see `shared.md`). |
| `DayCloseMenuTapped` | Close menu, `OpenDayClose(day)`. |
| `DayNutrientsTapped` | Close menu, `OpenNutrients(day, Day)`. |
| `CopyPreviousDayTapped` | `DiaryRepository.copyDay(from = day − 1, to = day)`, appended to every slot, toast. |
| `CopyToTomorrowTapped` | `DiaryRepository.copyDay(from = day, to = day + 1)`, appended, toast. |
| `MealGroupsToggled` | `SettingsRepository.update { mealGroups = !day.mealGroupsOn(it) }` (global, as the prototype; `Day.mealGroups` stays null in 0.1). |
| `WaterVisibilityToggled` | Shown: `NumbersChanger.request(Override(Water, Hide))`. Hidden: `ClearOverride(Water)` when the preset's level is not Hide, else `Override(Water, Show)`. `NeedsPause` emits `OpenPause`. |
| `ClearDayTapped` | First tap: `clearArmed = true`, menu stays open. Second: `DiaryRepository.clearDay(day)` (items and ate-no-details; water, notes and feelings stay), close menu, toast. |

Copy:

| Key | String |
|---|---|
| `today_add_food` / `_describe` / `_water` / `_weigh_in` / `_note` | Food / Describe it / Glass of water / Weigh-in / Note |
| `today_menu_close_day` / `_close_this_day` | Close the day / Close this day |
| `today_menu_nutrients` | This day's nutrients |
| `today_menu_copy_previous` / `_copy_tomorrow` | Copy previous day / Copy this day to tomorrow |
| `today_menu_add_note` | Add a note for the day |
| `today_menu_groups_off` / `_groups_on` | Turn off meal groups / Turn on meal groups |
| `today_menu_hide_water` / `_show_water` | Hide water / Show water |
| `today_menu_clear` / `_clear_confirm` | Clear this day / Tap again to clear (icon tinted `c-protein` when armed) |
| Toasts | Glass of water added. / Copied the previous day in. / Copied. It will be waiting on the next day. / Cleared this day. |

Icons: `restaurant`, `mic`, `water_drop`, `monitor_weight`, `edit_note`;
`check_circle`, `monitoring`, `content_copy`, `file_copy`, `edit_note`,
`view_agenda`, `water_drop`, `delete`.

Visibility: no numbers. The Water item's presence follows water's level.

Tests (in `TodayViewModelTest` and `TodayStateBuilderTest`): items hidden by
day and water level; clear needs two taps and a dismiss disarms it; copy
appends rather than replaces; water toggle issues the right `NumbersChange`
for a gentle preset (clears the override) and for Full; `OpenDayNote` from both
note entries. `SURFACES`: `today-add-menu`, `today-day-menu`, `today-day-menu-armed`.

---

## Calendar sheet

`CalendarSheetRoute(selectedIso: String)`, `bottomSheet<>`. `CalendarViewModel(selected: LocalDate)`.

```kotlin
data class CalendarState(
    val month: YearMonth,                  // kotlinx-datetime 0.7
    val title: String,                     // "October 2026", DateCopy.monthYear
    val weekdayInitials: List<StringResource>,   // S M T W T F S, Sunday first as the prototype
    val cells: List<CalendarCell?>,        // null = leading blank
    val canGoPrev: Boolean,                // month > today's month − 12
    val canGoNext: Boolean,                // month < month of (today + 6)
)
data class CalendarCell(val date: LocalDate, val selected: Boolean, val isToday: Boolean, val enabled: Boolean, val logged: Boolean)
```

| Action | Does |
|---|---|
| `LoggedDaysArrived(month, days)` | Sets `logged`. |
| `PrevMonthTapped` / `NextMonthTapped` | Moves the month within bounds, re-subscribes. |
| `DayTapped(date)` | Enabled only (up to today + 6): `SelectedDay.select(date)`, event `Dismiss`. |
| `TodayTapped` | `SelectedDay.select(today)`, `Dismiss`. |
| `DoneTapped` | `Dismiss`. |

Data: `DiaryRepository.observeLoggedDates(year, month): Flow<Set<LocalDate>>`, re-subscribed per month with `flatMapLatest`. Offline: local.
Components: `MonthGrid`, `SheetHeader`, buttons. Selected day: accent circle;
today: accent ring; logged: a `c-fat` dot; disabled: `line` colour.

Copy: `today_cal_prev_cd` Previous month, `today_cal_next_cd` Next month, cell
content description `October 25` (month long, day), `today_cal_legend`
Something logged, `today_cal_today` Today, `today_cal_done` Done.

Edge cases: today + 6 crosses into next month, so Next is enabled for it;
selected day outside the shown month opens on its month. Visibility: none (a dot
is "something logged", never an amount).

Tests: bounds for prev and next, disabled future days ignore taps, Today
selects and dismisses, logged dots from the repository. Preview: October 2026
with dots. `SURFACES`: `calendar`.

---

## Meal sheet

`MealSheetRoute(dayIso: String, slot: MealSlot)`, `bottomSheet<>`, typeMap for
`MealSlot`. `MealSheetViewModel(day, slot)`.

```kotlin
data class MealSheetState(
    val title: StringResource,             // slot name, or "All food" for Uncategorized with groups off
    val items: List<MealSheetItem>,
)
sealed interface MealSheetItem {
    data object AddFood : MealSheetItem
    data object AddNote : MealSheetItem
    data object HowItFelt : MealSheetItem
    data class CopyFromDayBefore(val dayIsToday: Boolean) : MealSheetItem   // only when day − 1 has items in this slot
    data object AteNoDetails : MealSheetItem
    data object SaveAsMeal : MealSheetItem                                 // only when this slot has items
}
```

| Action | Does |
|---|---|
| `DaysArrived(day, dayBefore, settings)` | Rebuilds `items` and `title`. |
| `AddFoodTapped` | `OpenLogger(day, slot)`. |
| `AddNoteTapped` | `OpenMealNote(day, slot)`. |
| `HowItFeltTapped` | `OpenMealFeel(day, slot)`. |
| `CopyFromDayBeforeTapped` | `DiaryRepository.copySlot(from = day − 1, to = day, slot)`, toast, `Dismiss`. |
| `AteNoDetailsTapped` | `DiaryRepository.markAteNoDetails(day, slot)`, toast, `Dismiss`. |
| `SaveAsMealTapped` | `OpenCreateMeal(dayIso, slot)`: `CreateMealRoute(fromDayIso, fromSlot)`, added to `:features:foods` as `foods.md` specifies (Foods registers it). |

Every `Open*` event dismisses this sheet first (`router.goBack()` then
`router.navigate(next)`). Data: `DiaryRepository.observeDay(day)` and `(day − 1)`,
`SettingsRepository.observe()`.

Copy: `today_meal_add_food` Add food, `_add_note` Add a note, `_how_felt` How it
felt, `_copy_yesterday` Copy yesterday's %s, `_copy_day_before` (new, when the
viewed day is not today) Copy %s from the day before, `_ate` Ate, no details,
`_save_as_meal` Save as a meal, `today_close_cd` Close. Toasts: `Added to %s.` /
`Marked %s as eaten.` Icons `restaurant`, `edit_note`, `mood`, `history`,
`check_circle`, `bookmark_add`. Scan a barcode, Photo and Voice log are not built.

Visibility: none. Tests: copy item only when the day before has that slot;
save-as-meal only with items; each tap's event and write. `SURFACES`: `meal-sheet`.

### Meal note sheet

`MealNoteRoute(dayIso, slot)`. `MealNoteState(title args = slot, draft: String, loaded: Boolean)`.

| Action | Does |
|---|---|
| `NoteLoaded(text)` | Prefills `draft` once. |
| `NoteTyped(text)` | `draft = text`. |
| `SaveTapped` | The note is `MealFeeling.note` (there is no separate meal note): `setFeeling(day, slot, feeling.copy(note = trimmed or null))`, keeping hunger, fullness and moods. Non-empty: toast. `Dismiss`. |

Copy: `today_note_title` Note, %s; placeholder `today_note_placeholder` Where
you were, who with, anything worth remembering; `today_note_save` Save; toast
`Note saved to %s.` Voice log (0.2) is not built. Tests: prefill, empty clears,
trimmed save. `SURFACES`: `meal-note`.

### How it felt sheet

`MealFeelRoute(dayIso, slot)`. 

```kotlin
data class MealFeelState(val hunger: Hunger?, val fullness: Fullness?, val moods: Set<Mood>)
```

| Action | Does |
|---|---|
| `FeelingLoaded(feeling)` | Prefills once. |
| `HungerTapped(h)` / `FullnessTapped(f)` | Selects, or clears when tapped again. |
| `MoodTapped(m)` | Toggles in the set. |
| `SaveTapped` | `DiaryRepository.setFeeling(day, slot, ...)`, keeping the note, toast, `Dismiss`. |

Copy: title `How %s felt`, subtitle `All optional. Only you see this.`, `Hunger
before`: Very hungry / Hungry / Peckish / Not hungry; `Fullness after`: Still
hungry / Satisfied / Full / Very full; `Mood, pick any`: Calm / Happy / Social /
Rushed / Stressed / Anxious / Low; `Save`; toast `Saved. Only you see this.`

No telemetry from this sheet at all: feelings are health data. Tests: toggle
semantics, save payload. `SURFACES`: `meal-feel`.

---

## Water sheet

`WaterSheetRoute(dayIso)`. `WaterSheetViewModel(day)`.

```kotlin
data class WaterSheetState(
    val glassLabel: String,                // formatter.glassSize(glassMl, unit): "250 ml", "8 fl oz"
    val unit: WaterUnit,
    val custom: String,                    // digits only
    val canRemove: Boolean,                // day's water > 0
)
```

| Action | Does |
|---|---|
| `InputsArrived(day, settings)` | Rebuilds. |
| `AddGlassTapped` | `addWater(day, +glassMl)`, toast, `Dismiss`. |
| `CustomAmountTyped(text)` | Keeps digits only. |
| `AddCustomTapped` | When > 0: fl oz converted with `Units.flOzToMl`, `addWater`, toast, `Dismiss`. |
| `RemoveGlassTapped` | `removeWater(day, glassMl)`, clamped at 0 by the repository, toast, `Dismiss`. |
| `WaterSettingsTapped` | `OpenWaterSettings` (settings api). |

Copy: title `Water`; `Add a glass, %s`; placeholder and description `Custom
amount`; unit `ml` / `fl oz`; `Add`; `Remove a glass`; `Water settings`. Toasts:
`Glass of water added.` / `Water added.` / `Removed a glass.` Icons
`local_drink`, `edit`, `remove`, `settings`, `chevron_right`.

Visibility: shows no intake, only the glass size (a setting). Tests: oz
conversion, digits filter, remove hidden at 0. `SURFACES`: `water-sheet`.

---

## Item sheet (new, not in the handoff)

`ItemSheetRoute(dayIso: String, entryId: String)`. Reason: nothing in the handoff removes a
wrong entry, and a reviewer will try. The repository has no lookup by id, so
the sheet observes the entry's day and finds it there.

```kotlin
data class ItemSheetState(val name: String, val sub: String, val calories: Display, val slot: MealSlot?)
```

| Action | Does |
|---|---|
| `EntryArrived(entry, day, policy)` | Calories resolved on `Today` with the entry's day context. |
| `RemoveTapped` | `DiaryRepository.removeItem(entryId)` (a tombstone, so it syncs), toast, `Dismiss`. |

Copy (new): `today_item_remove` Remove from %s; toast `Removed from %s.`
Edge: entry already gone (removed on another device) dismisses. `SURFACES`: `item-sheet`.

## Day note sheet (new)

`DayNoteRoute(dayIso)`. Same field as Day close, so the copy matches it.
`DayNoteState(draft, isToday)`.

| Action | Does |
|---|---|
| `NoteLoaded(text)` | Prefills once. |
| `NoteTyped(text)` | `draft = text`. |
| `SaveTapped` | `DiaryRepository.setDayNote(day, trimmed or null)`, toast when non-empty, `Dismiss`. Does not close the day. |

Copy: label `Anything to remember about today? (optional)`, or (new) `Anything
to remember about this day? (optional)` for other days; placeholder `Dinner at
Lola's for her birthday`; `Save`; toast (new) `Note saved.` `SURFACES`: `day-note`.
