# Day close plan

> **A closed day takes nothing (2026-10-27, `docs/decisions.md`):** once
> closed, the diary refuses every add, edit and delete for the day
> (`DayWrite.DayClosed`) until it is reopened. Done saves the note, the feeling
> and the check-in with the close in one write, `closeDay(day, DayReflection)`.
> On a closed day the button reads Save and keeps it closed, Reopen this day
> sits under it (action `ReopenTapped`, then `Close`), and the caption says
> "This day is closed. Reopen it to add or change food." Before closing it
> says "You can reopen it later if you need to add something." State
> `closed`.

> **Design v3 (4.1 to 4.5) changes, built:** the totals card shows the figure,
> "Inside your range of {range}" / "Above your range today" / "Your range is
> {range}", and protein, carbs, fat and fiber in their colours (not on a big
> day). The big-day reset card sits on the **hero colour**; tomorrow shows the
> **range** (`resolveRange` of `Targets.tomorrow()`), not a target; either
> button leads to a committed state with a heart that records nothing; Subtle
> hides the buttons. Blind and Coast show **"Logged today"** with the four meals
> as chips (a check on the ones eaten) and never the big-day card. Delayed
> opened today shows "Your totals are open / They lock again tomorrow morning."
> and a "Meals 4 of 4" row. The note label is "Anything to remember about
> today? · optional", placeholder "Dinner at Lola's, a lazy Sunday". "Close the
> day" is pinned at the bottom with "You can still edit this day afterwards."
> The header stays `PageHeader` (v3's top app bar is changed once, there) with
> the long date under it. Not built: "Expended, from your watch" (0.2).
> **How did today feel? (4.5)**: with "Logged today" showing (calories hidden
> on Day close), an optional single-choice row: Steady, Busy, Tired, Good,
> Hard (`DayFeeling`). Tapping the chosen one clears it. It prefills from
> `Day.feeling` once, like the note, and is saved on Close the day with
> the close (`closeDay(day, DayReflection(feeling = ...))`); Back saves nothing. Stored on the day record
> (`day_records.feeling`, synced): a meal feeling is keyed by meal, and the
> Uncategorized row already belongs to "All food" with groups off.
> Actions `FeelingTapped(feeling)`.
> State: `DayCloseState(loading, title, day, backLabel, revealNote, totals:
> DayTotals(eaten, rangeLine: Inside | Below | Above, macros), soft, bigDay:
> BigDayCard(tomorrow, commitment), mealsLine, loggedMeals: Set<MealSlot>?,
> isToday, note)`. The tables below are the first plan; where they differ, this
> note wins.

Close a day: totals or a soft line, the range, protein, the big-day card, a
note, Done. Read `shared.md` first.

## Source
- Handoff: `README.md` section 4.5 and section 2 (a big day never lowers tomorrow; the card only when calories are fully shown).
- Prototype markup: `Phone.dc.html` lines 378 to 403, the `is.dayclose` block.
- Logic: `phone-logic.js` `dc`, `dEaten`, `dTotals`; `PROTOTYPE_NOTES.md` "Big-day card".

## Route
`DayCloseRoute(dayIso: String)` in `:features:today` (api, so the plan's other entries can open it), `screen<>` (system design
section 10 lists Day close as a full screen). `DayCloseViewModel(day)` in
`:features:today:impl`, package `com.opson.features.today.impl.dayclose`.
Reached from the header check, the Day menu, never from Note (see `shared.md`).

## State

```kotlin
data class DayCloseState(
    val loading: Boolean = true,
    val title: StringResource,           // tone: "Close the day" / "How today went"
    val backLabel: RelativeDay,
    val revealNote: Boolean,             // level(Calories) == Delay, totals shown, day is today
    val totals: DayTotals?,              // calories effectively Show on DayClose
    val soft: Display?,                  // calories Soft on DayClose
    val bigDay: BigDayCard?,
    val mealsLine: MealCount?,           // calories Hidden on DayClose
    val isToday: Boolean,
    val note: String,
    val doneLabel: StringResource,       // "Done for today" when today, else "Done"
)
data class DayTotals(val eaten: Display, val range: Display, val protein: Display)
data class BigDayCard(val tomorrow: Display, val commitment: Commitment)   // Commitment { Hidden, Offered, Committed }
```

`DayCloseStateBuilder.build(inputs, policy, note, committed)`, pure:

| Field | Rule |
|---|---|
| `totals.eaten` | `resolve(Calories, eaten, DayClose, bare)`, shown at Show |
| `totals.range` | `resolveRange(Calories, min, max, DayClose, bare)`: `1,900 to 2,300` |
| `totals.protein` | `resolve(Protein, protein, DayClose)`: `47 g`, Soft word, or Hidden |
| `bigDay` | `totals != null && eaten > max && settings.bigDayEvent != Off` |
| `bigDay.tomorrow` | `resolve(Calories, targets.tomorrow().targetKcal, DayClose)`: `2,100 kcal` |
| `bigDay.commitment` | `Offered` when `bigDayEvent == Full` and not yet tapped, `Committed` after, `Hidden` at Subtle |
| `mealsLine` | `doneMealCount` of 4 |

`min`, `max` are today's range from `targets.today(exercise, 0, 0)`.
`Targets.tomorrow()` takes no inputs, so nothing on this screen can move it.

## Actions

| Action | Does |
|---|---|
| `InputsArrived(day, settings, policy)` | Rebuilds. Prefills `note` from `day.note` once. |
| `NoteTyped(text)` | `note = text`. |
| `CommitmentTapped` | `commitment = Committed`. Saves nothing, on purpose (`PROTOTYPE_NOTES.md`). Both buttons send it. |
| `DoneTapped` | `DiaryRepository.closeDay(day, DayReflection(note, feeling, checkIn))`: the note trimmed or null, and the feeling and check-in as answered here or as stored where they were not asked. One write sets `closed_at` (keeping the first) with them. Event `Close`. |
| `ReopenTapped` | `DiaryRepository.reopenDay(day)`, event `Close`. Only on a closed day. |
| `BackTapped` | Event `Close` without closing the day or saving the note. |

## Events
`Close` (the entry point calls `router.goBack()`).

## Data
`DiaryRepository.observeDay(day)`, `SettingsRepository.observe()`,
`VisibilityPolicySource.policy`. Write: `closeDay`. Offline: Room plus outbox.

## Components
`EdgeCard`, `TextArea`, buttons, back row, `NutrientText`, `NutrientVisible`.

## Visibility surfaces

Surface `DayClose`. Delayed numbers are revealed here (`isRevealed(DayClose)`),
except Coast, which reveals only on Week.

| Number | Metric | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|
| Eaten | calories | `1,245` + "kcal today" | "Today" + soft word | revealed as Show (Coast: Hidden) | "Logged today" + meals line |
| Range | calories | `Your range was 1,900 to 2,300` | Hidden | as Show | Hidden |
| Protein | protein | `Protein 47 g` | `Protein` + soft word | as Show | Hidden, label too |
| Big-day card | calories | when over max | never | as Show | never |
| Tomorrow | calories | `Tomorrow: 2,100 kcal, same as always.` | Hidden | as Show | Hidden |

## Copy

Keys are prefixed `today_dayclose_` in `strings_today.xml` (the brief's one
prefix per feature); the table drops the `today_` for space.

| Key | String |
|---|---|
| `dayclose_title` / `_title_warm` | Close the day / How today went |
| `dayclose_back` | ‹ %s |
| `dayclose_reveal_note` | Your totals are open now. They'll lock again tomorrow morning. |
| `dayclose_kcal_today` / `_kcal_that_day` (new) | kcal today / kcal that day |
| `dayclose_range` | Your range was %s |
| `dayclose_protein` | Protein |
| `dayclose_soft_label` / `_soft_label_other_day` (new) | Today / That day |
| `dayclose_big_day` | Big days happen. Cutting back hard tomorrow tends to backfire and makes the next big day more likely. Tomorrow's plan stays the same. |
| `dayclose_tomorrow` | Tomorrow: %s, same as always. |
| `dayclose_commit_normal` / `_commit_can` | Back to normal tomorrow / I can do this |
| `dayclose_committed` | Heard. That stays between you and you. |
| `dayclose_logged_today` / `_logged_that_day` (new) | Logged today / Logged that day |
| `dayclose_meals_line` | %1$d of %2$d meals |
| `dayclose_note_label` | Anything to remember about today? (optional) |
| `dayclose_note_placeholder` | Dinner at Lola's for her birthday |
| `dayclose_done_today` / `_done` | Done for today / Done |

## Edge cases
- **Future day**: not reachable (the close button is hidden and the Day menu item is not built). If deep-linked, `Close` on load.
- **Past day**: already closed by rule; Done still saves the note and stamps `closed_at`. Reveal note hidden (it promises a relock tomorrow).
- **Already closed today**: the header check opens the same screen; Save updates the note and keeps it closed, Reopen this day opens it to food again.
- **Delayed before 9 pm**: totals revealed here, reveal note shown. Coast: Hidden branch with the meals line.
- **Big day and Fuel**: calories Soft, so no big-day card ever (the card only appears when calories are fully shown).
- **Big day with `bigDayEvent = Subtle`**: card without the buttons. 0.1 has no UI to change it, so Full holds.
- **Name-only items**: excluded from eaten; the meals line still counts the meal.
- **Large text**: the eaten figure wraps under its label.

## Tests
- `DayCloseStateBuilderTest`: Full under range (no card), Full over max (card, Offered), Subtle (card, Hidden buttons), Off (no card), Fuel (soft, no card), Delayed at noon today (totals, reveal note), Delayed on a past day (totals, no reveal note), Coast (meals line), Blind (meals line, no protein label), tomorrow equals `Targets.tomorrow()` whatever eaten is.
- `DayCloseViewModelTest`: note prefills once and is not overwritten by later emissions; `CommitmentTapped` writes nothing to any repository; `DoneTapped` closes with the trimmed note and emits `Close`; `BackTapped` writes nothing.
- Domain: `Targets.tomorrow()` ignores activity (exists), `resolveRange` (new).
- Previews: big day Full, Delayed reveal, Fuel, Blind, past day, warm tone.
- `SURFACES`: `day-close`, `day-close-big-day` (both `showsCaloriesInFull = true`).
