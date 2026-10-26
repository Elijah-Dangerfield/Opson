# Settings plan

More, Modes and numbers with the pause sheet, Targets, Water settings,
Support, and a small Look and feel page. Fasting, Reminders and Watch and
health apps are 0.2, sketched at the end. Read `shared.md` first.

## Source
- Handoff: `README.md` sections 1 (friction), 2 (safety), 4.10.
- Prototype markup: `Phone.dc.html` `is.more` (534), `is.modes` (543 to 567), `sheet.friction` (887), `is.targets` (750 to 781), `is.water` (809 to 819), `is.support` (662 to 682), `is.reminders` (569 to 601), `is.fasting` (782 to 808), `is.integrations` (631 to 661).
- Logic: `phone-logic.js` `moreRows`, `presets`, `matrix`, `requestChange`, `fr`, `changedNote`, `tg`, `setGoal`, `wt`, `fs`, `ig`, `events`; `Friendly Tracking.dc.html` `onSettings` (stamps `changedAt` when mode or overrides change).

Routes in `:features:settings` (`com.opson.features.settings`); impl
`:features:settings:impl` (`com.opson.features.settings.impl.<page>`). Every
page except More is a full `screen<>` (system design section 10).

Common to every page unless noted: data is `SettingsRepository.observe()` and
`VisibilityPolicySource.policy`, arriving as `InputsArrived`; writes are
`SettingsRepository.update`, local first and synced through the outbox, so
offline changes nothing; anything that changes visibility goes through
`NumbersChanger`, never a direct write; components are `ListRow`, `RadioRow`,
`SegmentedControl`, `AppSwitch` (50 × 30), `PillChip`, `EdgeCard` and buttons
from `:libraries:ui`; events are `Open<Page>`, `OpenPause(change)`,
`ShowToast` and `Close`.

Built on reality (re-plan, 2026-10-26):

- **`NumbersChanger`** is an interface in `:libraries:diary`
  (`com.opson.libraries.diary.NumbersChanger`), implemented in
  `:libraries:diary:impl` over `SettingsRepository.update`:
  `request(change): NumbersOutcome` (`Applied(fewer)` or `NeedsPause`, decided
  inside the update transform so the check and the write are one step),
  `confirm(change)`, `turnOnPause()`, and the two sheet hooks `pauseShown(change)`
  and `pauseKept(change)`. Every app event about modes (`mode.changed`,
  `friction.shown`, `friction.completed`) is emitted there, so one class owns
  the telemetry and a screen only calls it. Picking Sprint from another preset
  sets `sprintStartedOn` in the same write, on both the direct and the paused
  path.
- **Pages share small `:libraries:ui` pieces** added for them: `PageHeader`
  (the `‹ More` link and the page title), `FlatButton` (the handoff's soft,
  outlined, ink and accent buttons), `RadioMatrix` (the per-metric grid),
  `LegendDot`, `ListRowChevron`, a `subtitleContent` slot on `ListRow`, and
  `EdgeCardDefaults.ListPadding` / `InsetPadding` / `SectionGap`. In
  `:libraries:nutrition:ui`, `NutrientPhrase` renders a resolved number inside a
  sentence ("Aim for about 2,100 kcal, before activity"), tagged, and renders
  nothing when the number is hidden.
- The app root themes from `Settings.palette` / `theme` / `textSize` through
  `UserAppTheme` in `apps/compose`, which collects only those three fields.
- Each page lists a `LeakSurface` in `SettingsLeakSurfaces.all`, built from
  `SettingsFixtures` through the page's real `XStateBuilder`.

Design v3 (2026-10-26, `docs/design/v3/`, supersedes the handoff where they
differ):

- `PageHeader` is an Android top app bar: a 48 dp back arrow and the title on
  one row, optional trailing actions. The signature is unchanged (`backLabel`
  now names the destination for screen readers), so every caller just changes
  look.
- Every interactive `:libraries:ui` component has a touch target of at least
  48 dp (`AppTheme.spacing.touchTarget`), without changing what it draws.
- Bars stop at 100% (`FillBar`, `SegmentedBar`, `NutrientBar`), with no
  overflow styling.
- Previews are one theme each at 390 dp, named so their screen ids read next
  to the design ids (`targets-goal-style`, `support-delete`).

---

## More

`MoreRoute : TabRoute()`. `MoreViewModel`. v3 (`more.png`): three cards with
an icon on every row, top to bottom "how the app treats you", "optional
extras", "help and plan".

```kotlin
data class MoreState(val groups: List<List<MoreRow>>)
sealed interface MoreRow {
    data class Modes(val preset: PresetId, val changes: Int) : MoreRow   // "Fuel" or "Fuel, 1 change"
    data object Targets : MoreRow                                        // "Energy range and macro split", never an amount
    data object LookAndFeel : MoreRow
    data class Water(val glasses: Int) : MoreRow                         // trailing "8 glasses": the goal, not intake
    data object Support : MoreRow
    data object Plan : MoreRow                                           // trailing "Free", not a link in 0.1
}
```

| Action | Does |
|---|---|
| `InputsArrived(settings)` | `MoreStateBuilder.build(settings)`. |
| `RowTapped(row)` | Event to `ModesRoute`, `TargetsRoute`, `LookAndFeelRoute`, `WaterSettingsRoute(fromMore = true)`, `SupportRoute`. `Plan` does nothing (no purchase flow in 0.1). |

Groups: `[Modes, Targets, LookAndFeel]`, `[Water]`, `[Support, Plan]`. Not
built in 0.1, so not rendered: Reminders and celebrations and Watch and health
apps (group 1), Fasting and Widgets and watch face (group 2). When Fasting
lands it is offered only where `Fasting.allowed(preset)` (Full, Sprint).

Visibility: none. Subtitles never show amounts (v3), so More resolves no
number and `MoreViewModel` no longer reads the policy.

Copy: `More`; `Modes and numbers` / `%s` or `%1$s, %2$d change(s)`; `Targets` /
`Energy range and macro split`; `Look and feel` / `Colors, text size and
tone`; `Water` / `%d glasses`; `Support and sharing`; `Plan` / `Free`.

Tests: groups and order, changes count, water glasses. `SURFACES`: `more`
(`showsCaloriesInFull = false` now).

---

## Modes and numbers

`ModesRoute()`. `ModesViewModel`. v3 (`modes.png`, `modes--full.png`).

```kotlin
data class ModesState(
    val presets: List<PresetRow>,              // Presets.all order
    val matrix: List<MatrixRow>,               // Metric.entries order: Weight and Vitamins, minerals included
    val pauseOn: Boolean,
    val revealAt: String,                      // "9:00 PM", DateCopy.time
    val pickingRevealTime: LocalTime?,         // the picker dialog, open on this time
    val changedNote: ChangedNote,              // DuringSetup | LastChanged(timestamp)
)
data class PresetRow(val id: PresetId, val selected: Boolean, val changes: Int)   // selected: the current preset; changes > 0 only on it
data class MatrixRow(val metric: Metric, val level: Visibility, val changedFrom: Visibility?)  // the preset's level, when overridden to something else
```

| Action | Does |
|---|---|
| `InputsArrived(numbers)` | `ModesStateBuilder.build(numbers, timeZone)`. |
| `PresetTapped(id)` | `NumbersChanger.request(PickPreset(id))`. Re-picking the current preset clears its changes; a no-op only when it is current with no changes. |
| `LevelTapped(metric, level)` | `NumbersChanger.request(Override(metric, level))`. No-op when already at that level. |
| `PauseToggled` | Off to on: `turnOnPause()`. On to off: `request(TurnOffPause)`, always through the pause. |
| `RevealTimeTapped` / `RevealTimeDismissed` | Opens and closes the time picker dialog. |
| `RevealTimePicked(time)` | `NumbersChanger.request(SetRevealAt(time))`. The same time is a no-op. |
| `BackTapped` | `Close`. |

The Targets link is gone (it lives in More now). The matrix is the only place
Weight and Vitamins, minerals change (v3 global rule).

**Reveal time** is a domain setting: `NumbersSettings.revealAt: LocalTime`,
default 21:00, serialized as `"HH:MM"` with a default so old settings decode.
`VisibilityPolicy.isRevealed` opens delayed numbers from `revealAt` on the day,
and the live policy (`LivePolicy`) re-emits at `revealAt` and at midnight.
**Judgment call, safety first:** an earlier reveal time shows numbers sooner,
so with the pause on, `SetRevealAt` to an earlier time goes through the pause
(`NumbersChange.needsPause`); a later time applies at once.

Copy: as before, plus the `%d change` / `%d changes` badge, `Changed from %s`,
`Reveal time` / `For anything set to Later`, picker `OK` / `Cancel`; `Watch
energy` replaces `Energy expended` as the matrix label (v3).

Tests: badge count and re-pick clears, changed rows, reveal time picked and
the same time ignored, pause off still pauses. `NumbersChangeTest` covers
`SetRevealAt` (earlier pauses, later does not). Previews: Fuel with one change
(`modes`), Blind. `SURFACES`: `modes`.

---

## Pause before showing more (sheet)

`PauseBeforeMoreRoute(kind, presetKey, metricKey, levelKey, timeKey)`,
`bottomSheet<>`; `timeKey` (`"HH:MM"`) is new, for `SetRevealAt`. v3
(`modes-pause.png`): **"Keep things as they are" is the primary (accent)
button** and the countdown is the quiet one, a soft button whose background
fills left to right as the 10 s run down ("Show calories in 0:07").

```kotlin
data class PauseState(
    val subject: PauseSubject,                 // Preset(id) | Number(metric) | ThePause | RevealTime(time)
    val secondsLeft: Int,                      // starts at 10
    val reason: PauseReason?,                  // Curious | PlanningAMeal | ProviderAsked | FeelingAnxious | SomethingElse
    val finished: Boolean,
)
```

Actions unchanged (`CountdownTicked`, `ReasonPicked`, `ShowThemTapped`,
`KeepAsTheyAreTapped`, `SheetDismissed`). Lowering never opens the sheet
(`NumbersChange.needsPause`).

Copy (v3): title `Showing %s again` for a number, `Switching to %s` for a
preset, `Turning off the pause`, `Opening numbers earlier`; body `You asked
for a pause before numbers come back. %s will show once the wait is over.
Nothing changes if you close this.` (one line per subject); chips `Curious` /
`Planning a meal` / `My provider asked` / `Feeling anxious` / `Something
else`; buttons `Keep things as they are`, then `Show %1$s in %2$s` (`0:07`)
until 0 and `Show %s` after.

Tests as before plus the new subject. Previews: a number at 7 s
(`modes-pause`), a preset, the pause itself. `SURFACES`: `pause-sheet`.

---

## Targets

`TargetsRoute()`. `TargetsViewModel`. v3 (`targets.png`, `targets-deficit.png`,
`targets-by-day.png`).

Domain (`:libraries:nutrition`), all additive so existing callers compile:

- `Targets` gains `startingMinKcal` (the low end's floor while
  `GOAL_FLOOR_KCAL` is null) and `style: TargetStyle` (`Range`, the default;
  `Deficit(kcal, startingKcal)`; `ByDay(groups)`), and `MAX_DEFICIT_KCAL: Int?
  = null` sits next to the floor with a `TODO(dietitian)`.
- Each end steps on its own, 50 kcal: `lowerLow()` / `raiseLow()` /
  `lowerHigh()` / `raiseHigh()`, null when blocked. The floor rule is
  unchanged and checked inside the write: the low end never goes below
  `GOAL_FLOOR_KCAL ?: startingMinKcal`, and the aim (the midpoint,
  `energyKcal`) never below `floorKcal`. Raising the high end is always
  allowed. The ends stay at least 50 kcal apart.
- `rangeFor(date, preset, burnKcal = null): DayGoal` is the one question Today
  and Trends ask. `DayGoal.Range(min, max, target, group)` for a range or a
  day group; `DayGoal.About(kcal)` for a set deficit, `max(burn - deficit,
  floorKcal)` rounded to 10 ("Goal about 1,870", never "-300"). A deficit
  falls back to the range when the burn is unknown or the preset does not
  allow deficits (`TargetStyle.deficitAllowed`: Full numbers and Sprint).
- Deficit steps: lowering is always allowed, down to 0; raising stops at
  `MAX_DEFICIT_KCAL ?: startingKcal`. The starting deficit is what the
  user's aim already implies (`burn - energyKcal`, floored to 50, at least
  0), so while the maximum is unset the deficit can never grow past where it
  began.
- By day: at most four groups, each weekday in exactly one. Tapping a day in
  a group moves it there; a day is never removed, only moved. Removing a group
  hands its days to the first group. Each group has its own range, stepped
  under the same floor rule.

```kotlin
data class TargetsState(
    val visible: Boolean,                      // level(Calories) == Show
    val style: StyleKind,                      // Range | Deficit | ByDay
    val range: RangeCard?,                     // low and high Display, the three step flags, band position
    val deficit: DeficitCard?,                 // deficit, burn and goal Displays, step flags, burnKnown
    val groups: List<DayGroupCard>,            // label, days, range Display, steppers while editing
    val week: List<WeekBand>,                  // each weekday's band, 0..1 on a shared scale
    val canAddGroup: Boolean,
    val macroMode: MacroMode,
    val macros: List<TargetMacroRow>,          // only macros effectively Show
    val editingMacros: Boolean,
)
```

| Action | Does |
|---|---|
| `InputsArrived(settings, policy)` | |
| `GoalStyleTapped` | `OpenGoalStyle` (the sheet below). |
| `StepTapped(end, up, group)` | The step inside `SettingsRepository.update`; blocked writes nothing and toasts `The low end can't go down until a dietitian sets a safe floor.` |
| `DeficitStepped(up)` | As above; a blocked raise toasts `A bigger deficit needs a limit set with a dietitian first.` |
| `DayTapped(group, day)` | Moves the day to that group. |
| `AddGroupTapped` | A new empty group with the first group's range (four at most). |
| `EditGroupTapped(group)` / `RemoveGroupTapped(group)` | Inline editing in the group card, and removal (not the first group). |
| `MacroCardTapped` | Opens or closes the Ratios / Grams editor in the card. |
| `MacroModePicked(mode)` | `settings.macroMode`. |
| `ModesTapped`, `BackTapped` | `OpenModes`, `Close`. |

Burn: 0.1 has no burn source (the watch is 0.2, and no intake and weight
estimate exists yet), so the view model passes `burnKcal = null`, the deficit
card says it needs about two weeks of logging, and Today keeps the range.
Fixtures pass a burn so the layout can be checked.

Not in 0.1: the Exercise adjustment row (no watch data), "One number" in the
picker (not in the v3 scope list), "My watch" as a burn source. The macro card
edits how macros are set (Ratios or Grams); per-gram editing needs its own
safety review first.

Visibility (surface `Settings`): every amount appears only when calories are
configured `Show`; otherwise the page is the `Targets work in the background`
card.

Copy (v3): `Goal style` / `A range` / `A set deficit` / `Different by day`;
`Low end` / `High end`; `The low end can't go down until a dietitian sets a
safe floor.`; `Macro split`; `%s protein` / `%s carbs` / `%s fat`; deficit
`Under what you burn, each day`, `You burn, 7-day average`, `Today's goal`,
`About %s`, `Work out what I burn from`, `Intake and weight trend is steadier.
It needs about two weeks of logging first.`, `Never below your floor, even on
a low-burn day`, `After a big day, tomorrow's goal stays the same`; by day
`Every day` / `Other days` / `Day group %d`, `Range`, `Add another day group`,
`This week`, `Remove this group`.

Tests: `TargetsTest` (domain) for each end, the floor on the low end and the
aim, the gap, deficit caps and goal, by-day moves and limits, `rangeFor` per
style and preset; serializer round trip and old JSON; builder per visibility
branch; view model per action. Previews: `targets` (range), `targets-deficit`,
`targets-by-day`, Blind. `SURFACES`: `targets`, `targets-deficit`,
`targets-by-day` (`showsCaloriesInFull = true`).

## Goal style (sheet)

`GoalStyleRoute()`, `bottomSheet<>`. `GoalStyleViewModel`. v3
(`targets-goal-style.png`).

```kotlin
data class GoalStyleState(val options: List<StyleKind>, val selected: StyleKind)
```

`A set deficit` is in `options` only when `TargetStyle.deficitAllowed(preset)`
(Full numbers, Sprint); in every other mode the row is not rendered.

| Action | Does |
|---|---|
| `InputsArrived(settings)` | |
| `StylePicked(kind)` | Local selection. |
| `DoneTapped` | Writes the style (`Targets.withStyle`), nothing when unchanged, then `Dismiss`. |

Copy: `Goal style`; `A range` / `Anywhere between a low and high end`; `A set
deficit` / `A fixed amount under what you burn`; `Different by day` / `Say,
more on training days`; `Done`. Previews: Full (`targets-goal-style`), Fuel
(no deficit row). `SURFACES`: `targets-goal-style`.

---

## Water settings

`WaterSettingsRoute(fromMore: Boolean = false)`. `WaterSettingsViewModel`.

```kotlin
data class WaterSettingsState(
    val backToMore: Boolean,
    val showOnToday: Boolean,                  // level(Water) != Hide
    val glasses: Int,                          // 6, 8, 10, 12
    val goalAmount: String,                    // "2,000 ml", WaterProgress.glassSize; the copy wraps it as "8 glasses, 2,000 ml a day"
    val glassMl: Int,
    val sizes: List<GlassSize>,                // GlassSize(ml, label): 200, 250, 330, 500 in the user's unit
    val unit: WaterUnit,
)
```

| Action | Does |
|---|---|
| `InputsArrived(settings)` | |
| `ShowOnTodayToggled` | Same rule as the Day menu: on to off `Override(Water, Hide)`; off to on `ClearOverride(Water)` when the preset's level is not Hide, else `Override(Water, Show)`, through `NumbersChanger`. |
| `GoalPicked(n)` / `GlassSizePicked(ml)` / `UnitPicked(unit)` | Settings writes, no friction (they are targets, not visibility). |
| `BackTapped` | `Close`. |

Copy: `‹ Today` or `‹ More`; `Water`; `Show on Today`; `Daily goal, in
glasses` / `%1$d glasses, %2$s a day`; `6` `8` `10` `12`; `Glass size` with
`200 ml` ... or fl oz; `Units` `ml` / `fl oz`. Reminders row (`One mid-morning,
one mid-afternoon.`) is 0.2.

Visibility: the goal text is the goal, not intake, and shows in every mode.
Tests: toggle change per preset; unit relabels sizes. `SURFACES`: `water-settings`.

---

## Support and sharing

`SupportRoute()`. `SupportViewModel`.

```kotlin
data class SupportState(
    val export: ExportStatus,                  // Idle | Working | Failed(offline)
    val delete: DeleteStep,                    // Idle | Confirming | Deleting | Failed(offline)
)
```

| Action | Does |
|---|---|
| `HelplineTapped` | `router.openWebLink(HELPLINE_URL)`. |
| `FindProviderTapped` | `router.openWebLink(PROVIDER_URL)`. |
| `ExportTapped` | `DiarySync.syncNow()` first so queued rows are included, then `DataExport.csv()` (new: interface in `:libraries:diary`, `HttpDataExport` in its impl, `GET /v1/export.csv`), then event `ShareExport(bytes)`, which the entry point hands to `router.shareFile("opson-export.csv", "text/csv", bytes)`, and toast. Either call failing: `Failed`. |
| `SendFeedbackTapped` | `OpenFeedback` (`FeedbackRoute`, home api). New row, the old Home screen was its only entry. |
| `DeleteTapped` | `Confirming`: a modal dialog (v3 `support-delete.png`), so it can't be confirmed by scrolling. `Keep my data` is the accent button and `Delete` stays neutral. No red anywhere. |
| `KeepMyDataTapped` | `Idle`. A tap outside or back does the same, except while deleting. |
| `DeleteConfirmed` | `Deleting`, `AuthRepository.deleteAccount()` (`DELETE /v1/me` cascades every table). Success: the auth orchestrator's `UserScopedDataReset` wipes Room, the outbox and the settings cache and resets `hasUserOnboarded` (`resetAccountScoped`), all before the signed-out state is emitted; then toast and event `GoToOnboarding` (`OnboardingRoute`, clearing the stack). `NetworkError`: `Failed(offline = true)`. Any other outcome: `Failed(offline = false)`. Nothing is deleted locally on failure. |
| `BackTapped` | `Close`. |

`HELPLINE_URL` and `PROVIDER_URL` are constants in the impl with
`TODO(launch): verify`. The handoff gives only allianceforeatingdisorders.com,
so both point there until someone checks the real pages. Do not guess a path.

**Conflict resolved:** the handoff says `Kept on this phone. No ad tracking.`
and the delete confirm says `This removes every log, photo and setting from this
phone.` Opson syncs to its server, and 0.1 has no photos. The copy must be
true, so:

| Key | String |
|---|---|
| `settings_support_data_body` (changed) | Saved on this phone and synced to your account. No ad tracking. |
| `settings_support_delete_confirm` (changed) | This removes every log and setting from this phone and from our server. It can't be undone. |

Copy otherwise verbatim: `‹ More`; `Support and sharing`; `If food or numbers
feel hard right now`; `Free, confidential support from people who understand
eating disorders.`; `National Alliance for Eating Disorders helpline`; `Find a
provider`; `Trusted person` / `They approve turning numbers back on` / `Coming
later`; `Dietitian, therapist or coach` / `They see what you choose to share` /
`Coming later`; `Your data`; `Export everything (CSV)`; `Delete everything`;
`Keep my data`; `Delete`; dialog title `Delete everything?` (v3). The v3 dialog
body says "every log, photo and setting from this phone", which is not true
for Opson (it syncs, 0.1 has no photos), so the dialog keeps
`settings_support_delete_confirm` above. Toasts: `Export ready: opson-export.csv` (renamed from
friendly-tracking.csv); `Everything was deleted.` New: `settings_send_feedback`
Send feedback (last row of Your data); `settings_export_offline`
Export needs a connection. Try again when you're online.;
`settings_delete_offline` We couldn't reach the server, so nothing was deleted.
Try again when you're online.; `settings_delete_failed` Something went wrong,
so nothing was deleted. Try again in a little while.

Visibility: none. Tests: export syncs before fetching, offline paths, delete
needs the confirm step, success resets onboarding and emits the event, failure
deletes nothing locally, the dialog closes on Keep. Previews: `support`,
`support-delete`. `SURFACES`: `support`, `support-delete`.

---

## Look and feel (new, 0.1)

`LookAndFeelRoute()`. Not in the handoff as a page. It exists because system
design section 11 makes palette and large text settings, and because "Stop
asking these" and tone need a home while Reminders is 0.2.

```kotlin
data class LookAndFeelState(
    val palette: Palette, val theme: ThemeMode, val textSize: TextSize,
    val weightUnit: WeightUnit, val tone: Tone, val askTags: Boolean,
)
```

Actions: `PalettePicked`, `ThemePicked`, `TextSizePicked`, `WeightUnitPicked`,
`TonePicked`, `HungerQuestionsToggled`, `BackTapped`. Plain settings writes.

Copy (new unless marked): `Look and feel`; `Colors`: Pine / Espresso / Clay /
Plum; `Theme`: Match phone / Light / Dark; `Text size`: Normal / Large; `Weight
units`: kg / lb; verbatim from Reminders: `How we talk to you`, `Plain` / `Warm`,
`Example: %s` with `"Nothing logged."` / `"Nothing here yet. Add it whenever."`,
`Hunger and fullness questions` / `Asked when you add food. Always skippable.`

Tests: each action writes its field. Preview: light and dark. `SURFACES`:
`look-and-feel` (no numbers today; listed so a later row with one is covered).

---

## 0.2: Fasting, Reminders, Watch and health apps

Planned now so the 0.1 settings shape does not need a migration.

| Page | Route | Notes |
|---|---|---|
| Fasting | `FastingRoute()` | Planned and built in `fasting.md` (0.2, behind `release.fasting`). Offered only in Full and Sprint, never to someone who agreed numbers have been hard. No Start button (v3: a fast starts when the window closes), End fast stays. |
| Reminders and celebrations | `RemindersRoute()` | Built: see `reminders.md`. Behind `release.reminders`; New food tried and Weekly recap wait for the features they control. With the flag on, More shows the row and Look and feel drops tone and hunger questions (they live on Reminders). |
| Watch and health apps | `HealthRoute()` | Sources list, `Health on Today` master and per-row switches, Expended today rows (energyOut gated), `Should exercise change your target?` All of it / Some / None (never lowers tomorrow), days left out of the math. Balance ring and Expended cards light up only after `TODO(clinician)` review. No manual exercise logging, ever. |
