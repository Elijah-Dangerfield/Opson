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
- The app root themes from `Settings.theme` / `textSize` through
  `UserAppTheme` in `apps/compose`, which collects only those two fields.
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

Design sync 2 (2026-10-26, `docs/plans/design-sync-2.md`; the export's notes
are the behaviour spec and win over v3):

| Screen id | Change |
|---|---|
| `more-screen__more` | The main preview shows every 0.2 row (flags on), as the design. Support gains the subtitle `Eating disorder helplines`. Targets uses the concentric target glyph. |
| `modes-screen__modes` | Already matches (badge, re-pick clears, `Changed from`). **Weight's Numbers / Trend only / Hidden lives here**: it is the matrix's Weight row (Show / Soft / Hide, Later too), behind the pause like every other raise, now that the Trends agent removes the switch from Weight. Tests cover it. |
| `pause-sheet-content__modes-pause` | Already matches (Keep is primary, the countdown fills quietly). Adds `Talk to someone`, a quiet link above the buttons that opens Find support (only with `release.findSupport` on). |
| `targets-screen__*` | Steppers: the lock is the outlined glyph in muted, numbers one step smaller. Macro split: one bar of flush segments, amounts bold with a regular label. Deficit rules read on one line (smaller type). By day: the first group ("Other days") renders last, week bands are flat bars, the pencil is the outlined glyph. |
| `goal-style-sheet-content__targets-goal-style` | No change. |
| `reminders-screen__reminders`, `reminder-notification__…` | The notification lookalike sits on a lock screen (clock and date), light card, tinted action chips. |
| `integrations-screen__integrations-connected` | In a mode that hides energy the Expended card reads `Your watch syncs in the background. Nothing from it shows in this mode.` Smaller status pill. |
| `fasting-screen__fasting` | Already status first; no change. |
| `support-screen__support` | With `release.findSupport` on: `Find support near you` (accent, chevron) and the crisis row for the phone's country, which dials. Off: the 0.1 links stay. Icons outlined. |
| `support-screen__support-delete` | Outlined trash icon, body in ink. Still a modal with Keep in accent. |
| `support-screen__support-find` (new) | Find support, below. |
| `widgets__widgets` | The page labels the widget with the person's mode ("Full numbers") and shows the medium widget on the hero colour. Preview file renamed so the ids pair. **No watch face** (out of scope). |

Kept different on purpose:

- More keeps **Look and feel** (the design drops it): Appearance and text size
  have no other home.
- Modes keeps `Every number visible, all day.` for Full numbers and `A meal
  checklist. Amounts optional.` for Rhythm. The design's versions advertise watch
  sync and reminders, which are behind 0.2 flags. No Progress photos row: the
  feature doesn't exist.
- Goal style has **One number** and adds **Set surplus**, both behind
  `release.goalsAndEnergy` (see "Goals and energy" under Targets).
- Targets by day names groups `Day group 2`, not `Training days`: groups have no
  names yet. `TODO(design-sync-2)`.
- Deficit has no `My watch` / `Intake and weight` switch: the watch isn't a burn
  source yet.
- Integrations lists only the store Opson reads (no Wear OS watch, no Garmin row,
  no Health card on Today), and keeps Disconnect.
- Support keeps `Send feedback`, has no Trusted person or clinician rows (App
  Review), and keeps the true data line (`Saved on this phone and synced…`).
- `to` in ranges, `PM` from `DateCopy` (shared).

---

## Find support (new, design sync 2)

`FindSupportRoute()` in `:features:settings`, so Onboarding, Today and the
pause sheet can open it. `FindSupportViewModel`. Behind **`release.findSupport`**
(`FindSupportEnabled`, a `FlagConfigValue` in `:features:settings`, default
off, in the config registry): off, nothing links here and Support keeps its 0.1
links.

```kotlin
data class FindSupportState(
    val country: SupportCountry?,             // null: the phone's region has no list
    val pickingCountry: Boolean,
    val crisis: CrisisLine?,                  // the country's 24/7 line: call and text
    val lines: List<SupportLine>,             // the country's eating-disorder lines
    val elsewhere: List<SupportLine>,         // every other country's first line
    val couldNotOpen: SupportLine?,           // the phone couldn't open it: say the number
)
```

| Action | Does |
|---|---|
| (init) | `DeviceRegion.countryCode()` picks the country (`SupportCountry.of`). |
| `CountryTapped` / `CountryPickerDismissed` | Opens and closes the country menu. |
| `CountryPicked(country)` | Rebuilds for that country. Not stored: the next visit starts from the region again. |
| `CallTapped(line)` / `TextTapped(line)` / `OpenTapped(line)` | `tel:`, `sms:` or the web page, straight through `WebLinkLauncher`. A failure sets `couldNotOpen`; it is never logged. |
| `BackTapped` | `Close`. |

The list (`SupportDirectory`, in the impl) ships with the app and works
offline: US, UK, Canada, Australia, Ireland and New Zealand, each a crisis line
and its eating-disorder lines. Every entry carries its source and `TODO(launch):
verify` the number and hours, then each quarter.

**Never logged, synced or shared**: no app event, the chosen country is not
saved, the launcher's failures are swallowed, and `App.kt` doesn't report
`FindSupportRoute` to the crash scope or the jank monitor (the screen beneath
keeps the attribution). iOS lists `tel` and `sms` in `LSApplicationQueriesSchemes`
so `canOpenURL` can see them.

Support's crisis row (`SupportState.crisis`) comes from the same region:
`In crisis? Call or text 988` / `24/7 · United States`, and tapping it dials.
With no list for the region it reads `In crisis? Find a line near you` and opens
Find support.

Copy: `Find support`; `Free and confidential. Calls open your phone app.`;
`In crisis right now`; `24/7 · call or text` (or `24/7 · text SHOUT to
85258` where the text line wants a keyword); `Call %s` / `Text %s`;
`Eating disorder support`; `Outside the US` and one per country (`Other
countries` with no country);
`Choose a country`; `Call` / `Text` / `Open`; `%1$s · %2$s` rows;
`Your phone couldn't open this. The number is %s.`; `If you're in danger, call
your local emergency number.`

Visibility: none, no numbers. Tests: region picks the country, unknown region,
re-picking, each tap's URL, a failed open, the support row per country.
Previews: `support-find` (US), UK dark, no country. `SURFACES`: `support-find`.

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
  At the cap "+" becomes a lock with a note under the stepper, as the range's
  floor does, and "−" goes quiet at 0. A deficit picked with no burn known
  starts at 0, so it is locked at 0 until a burn source or a dietitian's
  maximum exists; a greyed "+" there read as a broken stepper.
- By day: at most four groups, each weekday in exactly one. Tapping a day in
  a group moves it there; a day is never removed, only moved. Removing a group
  hands its days to the first group. Each group has its own range, stepped
  under the same floor rule.

```kotlin
data class TargetsState(
    val visible: Boolean,                      // level(Calories) == Show
    val style: StyleKind,                      // Range | Deficit | ByDay
    val range: RangeCard?,                     // low and high Display, the three step flags, band position
    val deficit: BurnGoalCard?,                // amount, step flags, today's target against Burned
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
| `StylePicked(kind)` | Writes the style at once (design sync 4 replaced the goal style sheet; see "Goals and energy"). |
| `StepTapped(end, up, group)` | The step inside `SettingsRepository.update`; blocked writes nothing and toasts `The low end can't go down until a dietitian sets a safe floor.` |
| `DeficitStepped(up)` | As above; a blocked raise toasts `A bigger deficit needs a limit set with a dietitian first.` |
| `DayTapped(group, day)` | Moves the day to that group. |
| `AddGroupTapped` | A new empty group with the first group's range (four at most). |
| `EditGroupTapped(group)` / `RemoveGroupTapped(group)` | Inline editing in the group card, and removal (not the first group). |
| `MacroCardTapped` | Opens or closes the Ratios / Grams editor in the card. |
| `MacroModePicked(mode)` | `settings.macroMode`. |
| `ModesTapped`, `BackTapped` | `OpenModes`, `Close`. |

Burn: 0.1 had no burn source. With `release.goalsAndEnergy` the view model
reads today's Burned (see "Goals and energy"); without it, the deficit keeps
the range and a surplus goes on top of the usual target.

Not in 0.1: the Exercise adjustment row (no watch data), "My watch" as a
burn source. "One number" and a set surplus came in 0.2, below. The macro card
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

### Goals and energy (design sync 4, `release.goalsAndEnergy`)

Design sync 4's 13.2 (`targets-goal-style-v4`, `resting-energy`,
`energy-today`). The flag gates one number and a set surplus in the list, a
day group's shape, "Burned comes from", and the two new pages. A style
already saved always shows, so a phone with the flag off never misreads a
goal set on one with it on.

**The goal style list (13.2e).** Targets lists the styles in one card:
Range ("1,900 to 2,300 kcal" under it), One number, Set deficit, Set surplus,
Different by day. A tap writes the style at once (`StylePicked`); the old
goal style sheet and `GoalStyleRoute` are gone. The picked row opens its
controls under it: the range's ends, one number's stepper, or the deficit or
surplus stepper with "Today's target 1,680 · moves with what you burn".
Different by day's groups follow the card. Deficit and surplus are Full
numbers only (`TargetStyle.surplusAllowed` is `deficitAllowed`).

- `TargetStyle.Exact`: one number, the aim (`energyKcal`). The range's ends
  are kept and move with it (`stepNumber`, 50 kcal, macros scaling in ratio
  mode), so switching back to a range lands around the number and the floor
  under the range is the floor under the number. `rangeFor` answers
  `DayGoal.Exact(kcal)`.
- `TargetStyle.Deficit` and `Surplus(kcal, startingKcal)` aim at Burned:
  `rangeFor(date, preset, burnKcal = burned.totalKcal)`, never under
  `GOAL_FLOOR_KCAL` (or where the user started). **Owner's call
  (2026-10-27), which overrides the design's working 500 kcal stepper:** the
  deficit stays locked until a dietitian sets `MAX_DEFICIT_KCAL`, and the
  surplus has the same kind of cap, `MAX_SURPLUS_KCAL: Int? = null`
  (`TODO(clinician)`). Until set, each can come down but never grow past
  where it started, which is what the aim already implies against Burned
  when picked (0 without Burned). The lock and its note say why.
- A surplus does not wait on Burned: until it is known it goes on top of the
  aim (it can only put the goal above it), and the card says so.
- Day groups: `DayGroup.shape` is `GroupShape.Range`, `Exact` (the group's
  middle, both ends moving with it) or `Surplus(kcal, startingKcal)` (over
  Burned, or the group's middle until it is known, capped as the style is,
  Full numbers only). Editing a group shows a `Range / One number / Surplus`
  switch above the stepper for its shape. A new group is a range. A set
  deficit is not offered per group.
- Drawn as one number everywhere a range is drawn: Today's bar and the
  widget mark it with a line, Trends draws a thin line where the band was,
  the words say "goal" ("Your goal", "To your goal", "Close to your goal of
  2,100"). `NutritionFormatter.energyRange` prints one number when the ends
  meet, never "2,100 to 2,100".
- Judging a day against one number (Close the day's sentence and the
  big-day card) allows `ONE_NUMBER_LEEWAY_KCAL` (100, `TODO(clinician)`)
  either side, so a few bites past it are not a big day.
- Trends draws one band for a span: `Targets.spanGoal` is every day's goal,
  or under "Different by day" the lowest group goal to the highest.
- Sync: the style travels as the top-level `targetStyle`, lifted out of
  `targets` by `SettingsSerializer`, because the server merges one level
  deep and 0.1 rewrites `targets` whole. A style saved inside `targets` by an
  earlier 0.2 build still reads.

**Burned.** `BurnedRepository.burned(day)` (`:libraries:health`) is resting
energy for the whole day plus the activity the health app has synced so far
when "Add activity to Burned" is on (`Burned`, `:libraries:nutrition`).
Resting energy comes from `Settings.restingSource`: Calculate (Mifflin–St
Jeor from `birthYear`, `heightCm`, `bodySex` and the latest weigh-in,
recalculated when a weight is logged; the default), the health app (its most
recent whole day in the last week, falling back to the calculation) or
Enter my own (500 to 5,000). Onboarding collects none of age, height or sex,
so the Resting energy page asks for what is missing while Calculate is
picked; until then there is no Burned and every page says so. `BodySex` has
`Unsaid`, the midpoint of the equation's two constants. "Burned comes from"
on Targets shows resting energy (energy out) and the activity source.

**Resting energy (13.2d)**, `RestingEnergyRoute(fromEnergyToday)`. The three
sources with what each gives now, the details the calculation needs, "Steps
and workouts" (to Integrations) and "Add activity to Burned". Typed fields
write only once sensible, so typing "1600" writes once. Every amount is
energy out on `Surface.Settings`; with it hidden, no amount shows, not even
the person's own.

**Energy today (13.2c)**, `EnergyTodayRoute()`, for Today's summary card to
link to. Eaten with its ± and "Likely 1,145 to 1,345. 5 of 7 foods are
estimates.", Burned with resting, daily movement and workouts and where each
comes from, then "Deficit so far" (or "Surplus so far") and "To your range".
Eaten is calories and Burned energy out, both on `Surface.Today`; the deficit
needs both on show. The ± is `Day.eatenUncertainty(usual)` (`:libraries:diary`,
the math in `EatenUncertainty`, `:libraries:nutrition`): σ per food by how it
was logged (weighed 5%, label 10%, estimate 25%, quick add 15%, a meal eaten
with no details 40% of that meal's usual size, `UsualMealSize`), √Σσ²,
rounded to 10.

| Action | Does |
|---|---|
| `StylePicked(kind)` | Writes the style unless it is already saved or not offered (checked again inside the write). A deficit or surplus starts from Burned. |
| `NumberStepped(up, group)` | One number, or day group `group`'s; a blocked lower writes nothing and toasts the floor. |
| `SurplusStepped(up, group)` | The surplus, or day group `group`'s; at the cap writes nothing and toasts the lock's reason. |
| `GroupShapePicked(group, shape)` | The group's shape; its range is kept for when it goes back. |
| `RestingEnergyTapped`, `ActivityTapped` | `OpenRestingEnergy`, `OpenIntegrations`. |

Previews: `targets-goal-style-v4`, `targets-exact`, `targets-exact-at-floor`,
`targets-surplus`, `targets-surplus-no-burn-yet`, `targets-surplus-large-text`,
`targets-by-day-shapes`, `targets-by-day-surplus-dark`, `resting-energy`,
`resting-energy-needs-details`, `resting-energy-own-dark`,
`resting-energy-large-text`, `energy-today`, `energy-today-no-burned-dark`,
`energy-today-large-text`. `SURFACES`: `targets-goal-style`, `targets-exact`,
`targets-surplus`, `targets-by-day-shapes`, `resting-energy`, `energy-today`.

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
design section 11 makes appearance and large text settings, and because "Stop
asking these" and tone need a home while Reminders is 0.2. Design sync 4 took
the palette picker out: Slate is the only scheme.

```kotlin
data class LookAndFeelState(
    val theme: ThemeMode, val textSize: TextSize,
    val weightUnit: WeightUnit, val tone: Tone, val askTags: Boolean,
)
```

Actions: `ThemePicked`, `TextSizePicked`, `WeightUnitPicked`,
`TonePicked`, `HungerQuestionsToggled`, `BackTapped`. Plain settings writes.

Copy (new unless marked): `Look and feel`; `Appearance`: System / Light /
Dark (design sync 4); `Text size`: Normal / Large; `Weight
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
