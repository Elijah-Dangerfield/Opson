# Today menus and sheets plan

Rebuilt to design v3 (`docs/design/v3/`). The Day menu, Edit Today's cards, Calendar, a meal's Note
sheet (the Meal sheet is gone, below), Water sheet, and two small sheets the handoff does not have: Item and
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

## Day menu (⋮, a popover inside Today)

Design v3, 2.11. The Add menu and the header + are gone (adding is the nav +
or a meal's +), so this is the only menu. Not a nav destination: it anchors
to the ⋮ button, so it is a `PopoverMenu` driven by `TodayState.menu` and
handled by `TodayViewModel`. Closing on an outside tap sends `MenuDismissed`.

```kotlin
data class DayMenu(val groups: List<List<DayMenuItem>>, val clearArmed: Boolean)
sealed interface DayMenuItem {
    data class CloseDay(val isToday: Boolean) : DayMenuItem   // not on a planned day
    data object Nutrients : DayMenuItem
    data object CopyYesterday : DayMenuItem
    data object CopyToTomorrow : DayMenuItem                  // not on a planned day
    data object AddNote : DayMenuItem
    data class Untracked(val marked: Boolean) : DayMenuItem    // "Mark as untracked" / "Track this day again"
    data object Select : DayMenuItem                           // release.todaySelect, a day with foods
    data object EditCards : DayMenuItem                        // "Edit Today screen"
    data object ClearDay : DayMenuItem
}
```

Groups, by what each item affects: the day (Close, This day's nutrients);
copying and notes (Select, Copy yesterday, Copy this day to tomorrow, Add a
note, Mark as untracked); layout (Edit Today screen, Clear this day). Rows are icon
first in the accent (layout rows muted), no lines inside a group. **Mark as
untracked** writes `day_records.untracked` (see `today.md`); on an untracked
day it reads "Track this day again", and Close the day leaves the menu.
Fasting is 0.2.

| Action | Does |
|---|---|
| `DayMenuTapped` | Open, `clearArmed = false`. |
| `MenuDismissed` | Close, disarm. |
| `DayCloseMenuTapped` | Close menu, `OpenDayClose(day)`. |
| `DayNutrientsTapped` | Close menu, `OpenNutrients(day)`. |
| `CopyYesterdayTapped` | `copyDay(from = day − 1, to = day)`, appended, toast. |
| `CopyToTomorrowTapped` | `copyDay(from = day, to = day + 1)`, appended, toast; nothing on a planned day. |
| `DayNoteTapped` | Close menu, `OpenDayNote(day)` (not Day close, see `shared.md`). |
| `UntrackedMenuTapped` | Close menu, `setUntracked(day, !untracked)`. |
| `SelectMenuTapped` | Close menu, select mode with nothing picked: a tap on a food picks it, and the bar's Save as meal, Move, Copy to… and Delete wait, dimmed, until one is. Only with `release.todaySelect`, on a day with foods that is not closed. |
| `EditCardsTapped` | Close menu, `OpenTodayScreenSettings`: More › Today screen with "‹ Today". |
| `ClearDayTapped` | First tap: `clearArmed = true`, menu stays open, icon in `c-protein`. Second: `clearDay(day)`, close, toast. |

Copy: Close the day / Close this day, This day's nutrients, Select, Copy yesterday,
Copy this day to tomorrow, Add a note, Edit Today screen, Clear this day / Tap
again to clear. Toasts: Copied the previous day in. / Copied to the next
day. / Cleared this day.

### Edit Today screen (was Edit Today's cards)

There is one place Today is arranged: More › Today screen
(`TodayScreenSettingsRoute`, in `:features:settings:impl`, `todayscreen/`).
The ⋮ item and a long press on the summary card open it with `fromToday = true`
("‹ Today"); More opens it with "‹ More". It is there whatever the release
flags say; they only decide which parts it arranges. Edit Today's cards was a
second, smaller sheet that wrote some of the same settings (Card pages, a water
switch with a different meaning) and none of the rest, and opening it from
Today when More › Today was expected is what the owner reported (2026-10-29).

What moved from the sheet: Meal groups, the Fasting card switch (while fasting
is offered and its timer is on) and the summary card's stats (`release.todaySummary`
where design sync 4's card is not drawn: the suggestion with Reset, page 1's
stats to tick and drag, a lock on what the levels hide that opens Modes).
`SummaryStats` moved to `:libraries:diary:ui` and `TodaySummaryEnabled` to
`:libraries:diary` so both Today and the page read them. The sheet's Water
switch, which hid the water level through `NumbersChanger`, is gone: Water here
is `todayWater`, the card on Today alone, and when the levels hide water the row
says so and links to Display.

A choice the page offers that something else overrides says so under it, with
a link to what decides (`OverrideNote`): Equation or Deficit or surplus without
Burned (Resting energy, or Display when energy out is hidden), water,
weight or vitamins and minerals hidden by the levels, the card's layout set by
the mode or by calories on Later, and Burned breakdown on a preset other than
In and out. Today and the page read the same rule for the drawn preset
(`TodayCardRules` in `:libraries:diary`).

`SURFACES`: `today-day-menu`, `today-day-menu-armed`; the page is
`today-screen-settings` and `today-screen-settings-stats` in `SettingsLeakSurfaces`.

---|---|
| `AddTapped` | `menu = Add`. |
| `DayMenuTapped` | `menu = Day`, `clearArmed = false`. |
| `MenuDismissed` | `menu = null`, `clearArmed = false`. |
| `AddFoodTapped` | Close menu, `OpenLogger(day, slot = null)`. |
| `AddDescribeTapped` | Close menu, `OpenLog(day, MealSlot.defaultFor(now, groupsOn), Describe)` (`LoggerRoute` on the Describe pane). |
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
| Toasts | Glass of water added. / Copied the previous day in. / Copied to the next day. / Cleared this day. |

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
    val month: LocalDate,                  // the 1st of the month shown
    val title: String,                     // "October 2026", DateCopy.monthYear
    val weekdayInitials: List<StringResource>,   // S M T W T F S, Sunday first as the prototype
    val cells: List<CalendarCell?>,        // null = leading blank
    val canGoPrev: Boolean,                // month > today's month − 12
    val canGoNext: Boolean,                // month < month of (today + 1)
)
data class CalendarCell(val date: LocalDate, val selected: Boolean, val isToday: Boolean, val enabled: Boolean, val logged: Boolean)
```

| Action | Does |
|---|---|
| `LoggedDaysArrived(month, days)` | Sets `logged`. |
| `PrevMonthTapped` / `NextMonthTapped` | Moves the month within bounds, re-subscribes. |
| `DayTapped(date)` | Enabled only (up to today + 1, v3 plans one day ahead): `SelectedDay.select(date)`, event `Dismiss`. |
| `TodayTapped` | `SelectedDay.select(today)`, `Dismiss`. |
| `DoneTapped` | `Dismiss`. |

Data: `DiaryRepository.observeLoggedDates(year, month): Flow<Set<LocalDate>>`, re-subscribed per month with `flatMapLatest`. Offline: local.
Components: `MonthGrid`, `SheetHeader`, buttons. Selected day: accent circle;
today: accent ring; logged: a `c-fat` dot; disabled: `line` colour.

Copy: `today_cal_prev_cd` Previous month, `today_cal_next_cd` Next month, cell
content description `October 25` (month long, day), `today_cal_legend`
Something logged, `today_cal_today` Today, `today_cal_done` Done.

Edge cases: today + 1 can cross into next month, so Next is enabled for it;
selected day outside the shown month opens on its month. Visibility: none (a dot
is "something logged", never an amount).

Tests: bounds for prev and next, disabled future days ignore taps, Today
selects and dismisses, logged dots from the repository. Preview: October 2026
with dots. `SURFACES`: `calendar`.

---

## Meal sheet (removed 2026-10-29)

A meal's + used to open a sheet of Add food, Note, Copy yesterday's {meal} and
Save as a meal. It now opens the add sheet on that meal (`LoggerRoute(slot,
dayIso)`, see `adding.md`), which already had Note and Copy a meal and now
has Save as a meal when the meal has food. Owner feedback: one sheet fewer on
the way to logging. `MealSheetRoute`, its view model and the `meal-sheet` leak
surface are gone.

### Note sheet

One entry for a meal's journal note and how hungry it was. It replaced the
separate "Add a note" and "How it felt" entries (owner feedback on TestFlight
702: "How it felt" read badly, and two entries for one thought was one too
many). The add sheet's Note tile opens the same sheet.

`MealNoteRoute(dayIso, slot)`. `MealNoteState(slot, hunger: Hunger?, draft: String, loaded: Boolean)`.

| Action | Does |
|---|---|
| `FeelingLoaded(feeling)` | Prefills `hunger` and `draft` once. |
| `HungerTapped(h)` | Selects, or clears when tapped again. |
| `NoteTyped(text)` | `draft = text`. |
| `SaveTapped` | Both live on `MealFeeling` (there is no separate meal note): `setFeeling(day, slot, feeling.copy(hunger, note = trimmed or null))`, keeping any fullness and moods an old "How it felt" row holds. Anything answered: toast. `Dismiss`. |

Storage: the `meal_feelings` row, unchanged, so no migration. `Hunger` is one
scale, Starving / Hungry / Neutral / Full / Stuffed; the first three keep the
wire values of the old four-step scale (`very_hungry`, `hungry`, `not_hungry`)
and the old `peckish` reads as Hungry. Today shows the hunger as a chip beside
the meal's note, in every mode.

Copy: `today_note_title` Note, %s; subtitle `today_note_subtitle` Both
optional, and only you see them; `today_note_hunger` How hungry; placeholder
`today_note_placeholder` Where you were, who with, anything worth remembering;
`today_note_save` Save; toast `Note saved to %s.`

No telemetry from this sheet at all: hunger is health data. Tests: prefill,
hunger toggle, old fullness and moods kept, empty clears, trimmed save.
`SURFACES`: `meal-note`.

---

## Water sheet

`WaterSheetRoute(dayIso)`. `WaterSheetViewModel(day)`. Half the screen's height
(`heightFraction = 0.5`), so the water card stays in view above it.

The card itself takes taps too: each glass is a target (`WaterGlassTapped(index)`
on `TodayViewModel`). A tap fills the glasses up to the one tapped, and a tap on
the last full glass empties it (`WaterTaps.glassesAfter`). The day's water moves
by the glasses added or taken off (`WaterTaps.mlAfter`, then `setWater`), never
to that many glasses outright: the bar floors the amount and stops at the goal,
so 830 ml keeps its 80 ml and a tap on the last glass at 3,000 ml takes off one
glass, not 1,250 ml. A closed day takes no taps.

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
the sheet observes the entry's day and finds it there. Rebuilt as the food's ⋮
sheet (`today-actions.md`), and only the ⋮ opens it now: a row opens the food's
detail for that entry (`LoggedFoodRoute`, `adding.md`), which has Remove from log.

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

`DayNoteRoute(dayIso)`. Same field as Day close, so the copy matches it: one
day note, whichever saved it last. Opened from Today's ⋮ menu, a day note in
the Notes list, and the list's "Write a note for today".
`DayNoteState(draft, isToday)`.

| Action | Does |
|---|---|
| `NoteLoaded(text)` | Prefills once. |
| `NoteTyped(text)` | `draft = text`. |
| `SaveTapped` | `DiaryRepository.setDayNote(day, trimmed or null)`, toast when non-empty, `Dismiss`. Does not close or reopen the day; a closed day takes it, as Close the day's Save does (decisions.md, 2026-10-29). |

Copy: label `Anything to remember about today? (optional)`, or (new) `Anything
to remember about this day? (optional)` for other days; placeholder `Dinner at
Lola's for her birthday`; `Save`; toast (new) `Note saved.` `SURFACES`: `day-note`.
