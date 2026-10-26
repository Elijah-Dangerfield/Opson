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

---

## More

`MoreRoute : TabRoute()`. `MoreViewModel`.

```kotlin
data class MoreState(val rows: List<MoreRow>)
sealed interface MoreRow {
    data class Modes(val preset: PresetId, val adjusted: Boolean) : MoreRow
    data class Targets(val energy: Display, val macroMode: MacroMode) : MoreRow   // energy Hidden unless level(Calories) == Show; bare = false, "2,100 kcal" 
    data object Water : MoreRow
    data object LookAndFeel : MoreRow
    data object Support : MoreRow
}
```

| Action | Does |
|---|---|
| `InputsArrived(settings, policy)` | |
| `RowTapped(row)` | Event to `ModesRoute`, `TargetsRoute`, `WaterSettingsRoute(fromMore = true)`, `LookAndFeelRoute`, `SupportRoute`. |

`TabReselected` is dropped: the `Router` has no tab reselect stream yet, and
More fits on one screen.

Visibility: the Targets subtitle `2,100 kcal, ratio macros` resolves
`Calories` on `Settings` only when `level(Calories) == Show` (configured, as
the prototype), else `Out of view in this mode`.

Copy: `More`; `Modes and numbers` / `%s` (preset label) or `%s, adjusted`;
`Targets` / `%s, ratio macros` / `%s, gram macros` / `Out of view in this
mode`; `Support and sharing` / `Helpline, export, delete`. New rows: `Water` /
`Goal, glass size and units`; `Look and feel` / `Colors, text size and tone`.
Not built in 0.1: Reminders and celebrations, Watch and health apps, Widgets,
Fasting.

Tests: subtitle per level (Full shows kcal, Delayed and Blind say out of view),
adjusted label. `SURFACES`: `more` (`showsCaloriesInFull = true`).

---

## Modes and numbers

`ModesRoute()`. `ModesViewModel`.

```kotlin
data class ModesState(
    val presets: List<PresetRow>,              // Presets.all order
    val matrix: List<MatrixRow>,               // Metric.entries order
    val pauseOn: Boolean,
    val changedNote: ChangedNote,              // DuringSetup | LastChanged(timestamp: String), DateCopy.timestamp in the user's zone
)
data class PresetRow(val id: PresetId, val selected: Boolean)            // selected: current preset and no overrides
data class MatrixRow(val metric: Metric, val level: Visibility)         // configured level, Delay included
```

| Action | Does |
|---|---|
| `InputsArrived(settings)` | `ModesStateBuilder.build(settings.numbers, timeZone)`. |
| `PresetTapped(id)` | `NumbersChanger.request(PickPreset(id))`. Picking clears overrides. Sprint also sets `sprintStartedOn = today` (inside the changer). No-op when the row is already selected. |
| `LevelTapped(metric, level)` | `NumbersChanger.request(Override(metric, level))`. No-op when already at that level. |
| `PauseToggled` | Off to on: `NumbersChanger.turnOnPause()`, at once. On to off: `NumbersChanger.request(TurnOffPause)`, which always goes through the pause. |
| `TargetsTapped` | `OpenTargets`. |
| `BackTapped` | `Close`. |

`NumbersChanger` outcomes: `Applied(fewer = true)` toast `Saved. Fewer numbers
from now on.`; `Applied(fewer = false)` toast `Saved.`; `NeedsPause` event
`OpenPause(change)`.

**Judgment call, safety first:** turning the pause off goes through the pause.
Otherwise anyone can skip it in two taps (pause off, then raise), which defeats a
setting the user turned on to protect themselves. Turning it on is instant.

Telemetry: `mode.changed` (`from`, `to` preset keys, `adjusted`), emitted by
`NumbersChanger`, never with levels for weight or amounts.

Visibility: no numbers. The matrix shows levels, not values.

Copy: `‹ More`; `Modes and numbers`; intro `Pick a starting point, then change
any single number below. Showing fewer numbers applies right away.`; preset
labels and lines (resources keyed by `PresetId`):

| Preset | Label | Line |
|---|---|---|
| Full | Full numbers | Everything visible. Watch sync shapes your target. |
| Delayed | Delayed reveal | Log all day. Totals open at a time you pick. |
| Blind | Blind | No calories anywhere in the app. |
| Nutrient | Nutrient focus | Calories hidden. Protein and fiber as fill bars. |
| Rhythm | Rhythm | A meal checklist with reminders. Amounts optional. |
| Explorer | Explorer | Safe foods, new foods, small wins for trying. |
| Mindful | Mindful | Hunger, fullness and how it felt. No nutrition data. |
| Fuel | Fuel | Eating enough first. Protein and meal count up front. |
| Sprint | Sprint | Track for two weeks to learn, then take a break. |
| Coast | Coast | Light logging and one weekly check. |

Matrix header `Each number` / `Show` / `Soft` / `Later` / `Hide`; metric labels
Calories / Left in range / Energy expended / Protein / Carbs / Fat / Fiber /
Vitamins, minerals / Weight / Water; cell description `%1$s: %2$s` (`Calories:
Show`); footnote `Soft shows a word or a fill bar instead of an amount. Later
keeps it locked until your reveal time.`; `Pause before showing more` / `A short
wait and a question before any number comes back.`; `Targets` / `Energy range
and macro split`; note `Last changed by you, %s.` (`Oct 25, 9:41 PM`) or `Set by
you during setup.`, followed by ` App updates never change these.`

Two lines in the handoff overpromise for 0.1 and stay because they describe the
mode, not a feature: "Watch sync shapes your target" (Full) and "with
reminders" (Rhythm). Flagged as open copy for when 0.2 lands.

Edge cases: a preset tap equal to the current preset with overrides clears the
overrides (lowering or raising, judged by score as usual). Large text: matrix
cells stay 44 dp and the label column wraps.

Tests: `ModesViewModelTest` for each action, including pause off routing
through `OpenPause` and pause on applying; `NumbersChangeTest` in
`:libraries:nutrition` for `applyTo` and `needsPause` (raise, lower, equal
score, TurnOffPause). Previews: Full, adjusted, from Blind. `SURFACES`: `modes`.

---

## Pause before showing more (sheet)

`PauseBeforeMoreRoute(kind: String, presetKey: String? = null, metricKey: String? = null, levelKey: String? = null)`,
`bottomSheet<>`. Primitive args on purpose (see `shared.md`);
`NumbersChange.toRoute()` and `PauseBeforeMoreRoute.change()` live in the
settings api so today, trends and settings impls can all open it.

```kotlin
data class PauseState(
    val subject: PauseSubject,                 // Preset(id) | Metric(metric) | ThePause
    val secondsLeft: Int,                      // starts at 10
    val reason: PauseReason?,                  // Curious | WorkingTowardAGoal | WorriedOffTrack | SomeoneAsked
    val finished: Boolean,                     // set once Show them or Keep has run, so a later swipe does nothing
)
```

| Action | Does |
|---|---|
| `CountdownTicked(left)` | From a 1 s `delay` loop started in `init` (virtual time in tests). |
| `ReasonPicked(reason)` | Selects or clears. Never stored, never logged ("Only you see the answer"). |
| `ShowThemTapped` | Only at 0: `NumbersChanger.confirm(change)`, toast `Saved. You can hide them again any time.`, `Dismiss`. |
| `KeepAsTheyAreTapped` | `NumbersChanger.pauseKept(change)`, `Dismiss`, nothing changes. |
| `SheetDismissed` | A swipe down or back: the same as Keep, unless the sheet already finished. |

Telemetry, all through `NumbersChanger`: `friction.shown` (`subject`: preset,
metric or pause) from `pauseShown` in `init`; `friction.completed` with
`applied` true from `confirm`, false from `pauseKept`. No reason attribute.

Copy: `Showing more numbers`; `You asked for a pause here. %s will come back
once the wait is over.` (`%s` is the preset label or metric label); for
`ThePause` (new) `You asked for a pause here. Turning it off takes the same short
wait.`; `What's prompting this? Only you see the answer.`; chips `Curious` /
`Working toward a goal` / `Worried I'm off track` / `Someone asked me to`;
button `Opens in %ds` (track colour, muted, disabled) then `Show them`; `Keep
things as they are`.

Tests: countdown reaches 0 after 10 virtual seconds; Show them before 0 does
nothing; confirm applies the exact change; cancel writes nothing; telemetry has
no reason. Preview: from Blind with 7 s left. `SURFACES`: `pause-sheet`.

---

## Targets

`TargetsRoute()`. `TargetsViewModel`.

```kotlin
data class TargetsState(
    val visible: Boolean,                      // level(Calories) == Show
    val range: Display,                        // resolveRange(Calories, min, max, Settings): "1,900 to 2,300 kcal"
    val aim: Display,                          // resolve(Calories, energyKcal, Settings): "2,100 kcal"
    val canLower: Boolean,                     // Targets.canLower
    val macroMode: MacroMode,
    val macros: List<TargetMacroRow>,          // only macros effectively Show
)
data class TargetMacroRow(val metric: Metric, val share: Display, val grams: Display)   // "30%", "120 g"
```

| Action | Does |
|---|---|
| `InputsArrived(settings, policy)` | |
| `RaiseTapped` | `targets.raise(scaleMacros = macroMode == Ratios)`. Always allowed. |
| `LowerTapped` | `lower(...)` inside the update transform; null (would cross the floor) writes nothing and shows toast `Going lower needs a floor set with a dietitian first.` |
| `MacroModePicked(mode)` | `settings.macroMode`. |
| `ModesTapped` | `OpenModes` (from the hidden card). |
| `BackTapped` | `Close`. |

Floor: `GOAL_FLOOR_KCAL ?: startingKcal`, so while the dietitian value is null
the target can never go below where the user started. No UI ever edits
`startingKcal`.

Visibility (surface `Settings`): range, aim, shares and grams appear only when
calories are configured `Show`; otherwise the whole block is the `Targets work
in the background` card, so Soft and Delay modes see no amounts here.

Copy: `‹ More`; `Targets`; `Daily energy range`; range `%s` (`1,900 to 2,300
kcal`); `Aim for about %s, before activity`; `Lower` / `Raise`; note `Moves 50
kcal at a time.` or `Raising is always open. Going below where you started needs
a floor set with a dietitian, so it's off for now.`; `Set macros using` /
`Ratios` / `Grams`; macro rows label, share, grams; notes `Grams follow your
energy target, so the split stays the same.` / `Grams stay put when your energy
target changes.`; hidden card `Targets work in the background` / `They're out of
view in this mode. To see or change them, show calories in Modes and numbers.`
/ `Modes and numbers`; footer `No extra credit for coming in under.` The
Exercise adjustment row is 0.2 (it has no effect without watch data).

Edge cases: Lower greys out (transparent background, muted text) when
`canLower` is false but still taps, so the toast explains. A macro at Hide drops
its row and label.

Tests: raise then lower back to start allowed, one more lower refused with the
toast; ratio mode scales grams, gram mode does not; hidden card for Fuel and
Delayed; `Targets` domain tests for the floor (exist) and `scaleMacros` (new).
Previews: Full, Blind. `SURFACES`: `targets` (`showsCaloriesInFull = true`).

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
| `DeleteTapped` | `Confirming`. |
| `KeepMyDataTapped` | `Idle`. |
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
`Keep my data`; `Delete`. Toasts: `Export ready: opson-export.csv` (renamed from
friendly-tracking.csv); `Everything was deleted.` New: `settings_send_feedback`
Send feedback (last row of Your data); `settings_export_offline`
Export needs a connection. Try again when you're online.;
`settings_delete_offline` We couldn't reach the server, so nothing was deleted.
Try again when you're online.; `settings_delete_failed` Something went wrong,
so nothing was deleted. Try again in a little while.

Visibility: none. Tests: export syncs before fetching, offline paths, delete
needs the confirm step, success resets onboarding and emits the event, failure
deletes nothing locally. `SURFACES`: `support`.

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
| Fasting | `FastingRoute()` | Offered only when `Fasting.allowed(preset)` (Full, Sprint); else `Fasting isn't part of this mode` with the prototype body and a Meal reminders link. Timer switch off by default, `Show on Today`, plans 12:12 / 14:10 / 16:8 only, status card with Start/End. Logging food ends a fast (`DiaryRepository.add` clears `fastActive`). No streaks, no records, nothing over 16 h. `TODO(clinician)` stays on the allowed modes and the cap. |
| Reminders and celebrations | `RemindersRoute()` | Quiet mode; Off / Subtle / Full for After a big day, New foods, Weekly recap, Meal reminders; meal reminder switches with times; tone and hunger questions move here from Look and feel; the line `We don't send evening messages about what's left to eat. That isn't a setting, we just don't.` There is no evening "calories left" notification and no setting for one. "Change meal times" on Rhythm links here. |
| Watch and health apps | `HealthRoute()` | Sources list, `Health on Today` master and per-row switches, Expended today rows (energyOut gated), `Should exercise change your target?` All of it / Some / None (never lowers tomorrow), days left out of the math. Balance ring and Expended cards light up only after `TODO(clinician)` review. No manual exercise logging, ever. |
