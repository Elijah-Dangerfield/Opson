# Integrations and Health plan (0.2)

Watch and health apps: connect Apple Health or Health Connect, read today's
energy, and show it on the Integrations page and as **Expended so far** on
Today. Everything sits behind `release.health` (default off). Read `shared.md`
and `docs/plans/0.2/README.md` first.

## Source
- Design v3: `integrations.png`, `integrations--full.png`, `today-full.png`
  (the "Expended so far" line in the summary card), `more.png` (the "Watch and
  health apps" row). v3 wins over the handoff.
- Handoff: `README.md` health section; `settings.md` "0.2: Watch and health apps".
- Domain already in place: `Metric.EnergyOut` (no soften word, so softened it
  hides), `settings_metric_energy_out` ("Watch energy") in the Modes grid,
  `ExerciseAdjustment` and `Targets.today(adjustment, movement, workout)`.

## What is built, and what is not

| v3 shows | 0.2 | Why |
|---|---|---|
| Health Connect / Apple Health row, Connected chip | Built | The connection itself. One row: the platform's own store. |
| Wear OS watch row ("Quick-log saved meals · Plus") | Not built | Watch is wave 2 (`widgets.png`), and Plus has no purchase flow. |
| Garmin, Fitbit, Oura "Set up" | Not built | They reach us through Health Connect / Apple Health already; a row that only says so adds nothing yet. |
| Expended today: Resting, Everyday movement, Workouts, Total | Built | The breakdown of the number Today shows. |
| Should exercise change your range? | Built (second pass) | See "Exercise in the range" below. Moves today's range up only, capped, `TODO(clinician)` on the cap and the share. Never asked of, or applied to, someone who said numbers have been hard. |
| Days left out of the math | Built (second pass) | See "Days left out of the math" below. They are the days marked untracked, listed and picked here. |
| Exercise adjustment row on Targets (`targets.png`) | Built (second pass) | Shows the choice, opens this page. |
| Health card on Today (heart rate, sleep, steps) | Not built | The Health card is its own scope gate; reading heart rate, sleep and steps would ask for three more permissions for a card nobody can see yet. |
| Expended so far on Today | Built | |

**What is read**: energy only. Active energy, resting energy and workouts
(so movement and workouts can be told apart, as v3 does). Not steps, weight,
heart rate or sleep: no 0.2 surface shows them, and asking for data the app
does not use is both a privacy cost and an App Review risk (HealthKit
guideline 5.1.3 / Health Connect's minimum-permissions rule). The row's
subtitle says exactly this, so v3's "Energy, steps, workouts, weight" becomes
"Reads energy and workouts."

## Privacy

Health data stays on the device. It is read on demand from the platform store,
held in memory for the screen showing it, and never written to Room, the
settings JSON, the server, logs or telemetry. The only thing persisted is a
boolean, "the person connected", in `AppData` (device storage, not synced),
reset on a user change with the other account-scoped fields. Nothing in v3
needs the values synced: no server feature reads them. If that changes
(Balance on Trends over weeks, or exercise in the range) the plan comes back
here first.

Owner actions before flipping the flag:
- **App Store privacy label**: no change. Apple's label covers data that
  leaves the device, and none does, so *Health & Fitness* stays "not
  collected". Do register the HealthKit capability on the bundle id in the
  developer portal (the provisioning profile must carry it or the build fails
  to sign), and add a review note: "Opson reads active and resting energy and
  workouts to show energy expended today. It does not write to Health or send
  the data off the device."
- **Play data safety**: Health and fitness › "Health info" / "Fitness info":
  not collected, not shared (processed on device only, which Play excludes
  from "collected"). Complete the **Health Connect declaration form** in Play
  Console (App content › Health apps) listing the four read permissions and
  the reason. Play rejects a build that requests health permissions without it.
- **Privacy policy** (`legal/privacy.md`): add, under "What else the app
  sends" or a new heading, before the flag flips:
  "If you connect Apple Health or Health Connect, Opson reads your active
  energy, resting energy and workouts to show what you've expended today. It
  reads them on your phone and never sends them to our server or anyone else.
  Disconnect any time in More, under Watch and health apps." Not edited here,
  because legal-sync opens a website PR on every change and the feature is off.

## Modules

| Module | What |
|---|---|
| `:libraries:health` (new, kmp, DI) | `HealthSource` (AppleHealth, HealthConnect), `EnergyExpended`, `HealthStatus`, `ConnectOutcome`, the `HealthRepository` interface, the `HealthReleaseFlag` (`release.health`). |
| `:libraries:health:impl` (new) | `RealHealthRepository` over a platform `HealthReader` seam (commonMain); `HealthConnectReader` + `HealthPermissionsRationaleActivity` + manifest (androidMain); `HealthKitReader` in Kotlin/Native `platform.HealthKit` (iosMain, a Kotlin twin, no Swift). Telemetry fires here, the single writer. |
| `:features:settings` | `IntegrationsRoute()` |
| `:features:settings:impl` | `integrations/` (state, builder, view model, screen, previews), the More row. |
| `:features:today:impl` | `TodayInputs.expended`, `TodaySummary.Energy.expended`, one combine input in the view model. Nothing else. |
| `:libraries:diary:ui` | `EnergySummaryCard` gains an optional expended line next to "Nutrients". |
| `:libraries:ui` | `AppIcons.Watch`, `StatusPill` (the "✓ Connected" pill). |

```kotlin
// :libraries:health
enum class HealthSource(val key: String) { AppleHealth("apple_health"), HealthConnect("health_connect") }

/** Energy expended over part of a day. Null parts were not available. */
data class EnergyExpended(val restingKcal: Double?, val activeKcal: Double?, val workoutKcal: Double?) {
    val movementKcal: Double?   // active minus workouts, never below 0
    val totalKcal: Double?      // resting plus active, null when both are
}

sealed interface HealthStatus {
    data object Unsupported : HealthStatus   // the device can't (old Android, iPad without Health)
    data object NeedsInstall : HealthStatus  // Health Connect missing or out of date
    data object NotConnected : HealthStatus
    data object Connected : HealthStatus
}

enum class ConnectOutcome { Connected, Denied, NeedsInstall, Unsupported }

interface HealthRepository {
    val source: HealthSource
    val status: StateFlow<HealthStatus>
    suspend fun refresh()                         // re-checks availability and access
    suspend fun connect(): ConnectOutcome         // asks for permission; only ever from a tap
    suspend fun disconnect()
    fun energy(day: LocalDate): Flow<EnergyExpended?>  // null unless connected; re-reads every 5 min while collected
    fun openSourceSettings(): Boolean             // Health Connect's own screen; false where there is none (iOS)
}
```

The reader returns the day from local midnight to now. On Android: aggregate
`TotalCaloriesBurnedRecord.ENERGY_TOTAL` and
`ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL`, resting is total minus
active; workouts are active calories aggregated over each
`ExerciseSessionRecord`'s window. On iOS: cumulative-sum statistics for
`activeEnergyBurned` and `basalEnergyBurned`, workouts are each `HKWorkout`'s
`statisticsForType(activeEnergyBurned)` sum.

**Permission is asked only from Connect.** `refresh()` checks status and
never prompts. iOS cannot report whether read access was granted (HealthKit
hides it on purpose), so on iOS a finished request counts as connected and an
empty read shows "Nothing from Apple Health yet today." Android knows, so a
denial is reported, and a connected flag whose permissions were later revoked
in Health Connect reads as not connected on the next `refresh()`.

## Platform changes

- **Android** (`libraries/health/impl/src/androidMain/AndroidManifest.xml`,
  merged into the app): `android.permission.health.READ_ACTIVE_CALORIES_BURNED`,
  `READ_TOTAL_CALORIES_BURNED`, `READ_BASAL_METABOLIC_RATE` (Health Connect
  derives total energy from it), `READ_EXERCISE`; `<queries>` for
  `com.google.android.apps.healthdata`; `HealthPermissionsRationaleActivity`
  for `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` (Android 13 and
  lower) and an `activity-alias` for `VIEW_PERMISSION_USAGE` +
  `HEALTH_PERMISSIONS` guarded by `START_VIEW_PERMISSION_USAGE` (14+). The
  activity shows the privacy policy. `connect-client` 1.1.0 needs minSdk 26
  and the app is 24, so the health module's manifest carries
  `tools:overrideLibrary` for it (there, not in the app's, so the screenshot
  tests' merged manifest gets it too) and the reader reports Unsupported below
  Android 9, where Health Connect does not exist.
- **iOS**: `com.apple.developer.healthkit` (true) and
  `com.apple.developer.healthkit.access` (empty) in `iosApp.entitlements`;
  the HealthKit capability in the Xcode target; `HealthKit.framework` linked
  explicitly, as adding the capability in Xcode would;
  `NSHealthShareUsageDescription` in `Info.plist`. No
  `NSHealthUpdateUsageDescription`: Opson never writes.

## Routes and flag

`IntegrationsRoute()` (`screen<>`, settings). `HealthReleaseFlag`
(`release.health`, default false, in the QA menu). Off: More has no row,
Today never collects energy, and no health call is made at all. The route
stays registered (nothing links to it), as a dead route costs nothing and a
gated registration would crash a stale back stack.

## State

```kotlin
data class IntegrationsState(
    val loading: Boolean = true,
    val source: HealthSource = HealthSource.HealthConnect,
    val connection: Connection = Connection.NotConnected,
    val denied: Boolean = false,            // the last Connect was refused
    val canOpenSource: Boolean = false,     // Android: "Open Health Connect"
    val expended: ExpendedCard? = null,     // only while connected
)
enum class Connection { Unsupported, NeedsInstall, NotConnected, Connecting, Connected }

sealed interface ExpendedCard {
    data class Shown(val rows: List<ExpendedLine>, val total: Display) : ExpendedCard
    data object Empty : ExpendedCard        // connected, nothing read yet today
    data object Hidden : ExpendedCard       // the mode keeps watch energy out of sight
}
data class ExpendedLine(val label: StringResource, val display: Display)
```

`IntegrationsStateBuilder.build(inputs, policy, connecting, denied)`; inputs
are `(source, status, energy, today)`. Every amount is
`policy.resolve(Metric.EnergyOut, kcal, Surface.Settings, FormatContext(day = today))`.

## Actions
- `InputsArrived(inputs)`, `PolicyArrived(policy)`
- `ConnectTapped`: `connect()`; Denied sets `denied`, NeedsInstall opens the store.
- `DisconnectTapped`: `disconnect()`.
- `GetHealthConnectTapped`: `OpenLink(Play listing)`.
- `OpenSourceTapped`: `openSourceSettings()`.
- `BackTapped`: `Close`.
- `ScreenResumed`: `refresh()`, so returning from Health Connect's own screen updates the status.
  More has the same action for its row.

## Events
`Close`, `OpenLink(url)`, `ShowToast(toast)` ("Connected to Health Connect." / "Disconnected.").

## Visibility surfaces

| Number | Metric | Surface | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|---|
| Resting, Everyday movement, Workouts, Total (Integrations) | EnergyOut | Settings | `1,580 kcal` | hidden (no soft word) | hidden until the reveal, then shown | hidden: the card becomes one line, "Your mode isn't showing energy numbers right now." No label, no digit. |
| Expended so far (Today summary) | EnergyOut | Today | `1,990 kcal` | hidden | as Calories' reveal | the line is gone, label and watch icon included |

The Today line exists only in the `Energy` summary (calories shown), only for
today (it says "so far"), and only when the flag is on and a value was read.
The line is decided in `TodayStateBuilder`; the screen never checks a mode.

## Copy (`strings_integrations.xml`, prefix `integrations_`)
- Title `Watch and health apps`; back `More`
- `Apple Health` / `Health Connect`; `Reads energy and workouts.`
- `Connected`, `Connect`, `Disconnect`, `Get Health Connect`, `Open Health Connect`
- Unsupported: `Not available on this phone.`
- Denied: `Opson can't read anything until you allow it. You can change that in Health Connect.` (iOS: `…in the Health app.`)
- `Expended today`; `Resting`, `Everyday movement`, `Workouts`, `Total`
- Empty: `Nothing from %1$s yet today.`
- Hidden: `Your mode isn't showing energy numbers right now.`
- Footer: `Health data stays on this phone. Opson doesn't send it anywhere.`
- iOS disconnect note: `To remove access completely, open the Health app, tap your picture, then Apps.`
- Toasts: `Connected.`, `Disconnected.`
- More row: `Watch and health apps`, `Connected` / `Not connected`
- Today: `Expended so far`

## Telemetry (`docs/practices/app-events.md`)
From `RealHealthRepository`, attribute `source` (`apple_health` / `health_connect`) only:
`health.connected`, `health.disconnected`, `health.permission_denied`. Never a value.

## Edge cases
- Midnight: `energy(day)` is keyed on the date; Today asks only for today.
- Reader throws (Health Connect unbound, HealthKit error): logged at warn, the
  card shows Empty, Today shows no line. Never a crash, never a zero.
- A value of 0 (a fresh morning) is still a value: shown as `0 kcal` in Full.
- Large text: rows wrap; the Today line drops below "Nutrients" if it must.

## Tests
- `RealHealthRepositoryTest` (fake reader, fake cache): connect granted,
  denied, needs install; disconnect; refresh drops a revoked connection;
  energy is null when not connected; the three events and no values in them.
- `EnergyExpendedTest`: movement never below 0, total null only when both are.
- `IntegrationsStateBuilderTest`: each connection state; Full shows four
  lines; Blind, Fuel (soften), Delayed before the reveal and Full with an
  EnergyOut Hide override all give `Hidden` and no amount; Delayed after the
  reveal shows.
- `IntegrationsViewModelTest`: one per action.
- `MoreTest`: row present only with the flag on.
- `TodayStateBuilder` tests: the line in Full; absent in Blind, Fuel, Delayed
  before the reveal, Full with EnergyOut hidden, a past day, and without data.
- `TodayViewModelTest`: flag off never collects energy; flag on puts it in state.
- Leak test: `integrations` (Settings) and `today-expended` (Today) in
  `SURFACES` and `REQUIRED_SURFACES`; `integrations` marked
  `showsCaloriesInFull = true` (its energy amounts carry `metric:energyOut`).
  Plus `EnergyOutHiddenLeakTest`: both surfaces in Full with EnergyOut set to
  Hide show no `metric:energyOut` node and no kcal text from energy.
- Previews: Integrations not connected, connected, permission denied, Blind,
  dark; Today with the expended line, light and dark.

## Second pass: exercise in the range, days left out

Everything here is behind `release.health` too. Off, the page can't be
reached, Today and Day close never read energy, and the range is the base
range exactly as in 0.1.

### Exercise in the range

**Setting.** A new top-level key, `exerciseRange: ExerciseAdjustment? = null`
(`"none"`, `"some"`, `"all"`; anything unreadable decodes to `none`). Not the
0.1 `exercise` key: 0.1 synced the prototype's default (`"some"`) for everyone
who never saw a question, so it says nothing about a choice, and settings sync
merges shallowly, so a new key is the only clean way to start from "not asked".
`exercise` stays in `Settings`, unread, so old JSON still round-trips.

**Unanswered means No.** `Settings.exerciseInRange` (in `:libraries:diary`) is
`None` when `onboarding.hard` is true, whatever is stored, and otherwise
`exerciseRange ?: None`. v3 draws "Some of it" picked, but connecting a health
app must not move anyone's range by itself ("settings change only when the
user changes them"), so the radio starts on No until the person picks.

**Numbers have been hard.** Someone who answered yes to "Numbers or tracking
have been hard for me before" never gets range changes from exercise: the
question is not rendered, Targets has no Exercise adjustment row, and
`exerciseInRange` is `None` for them even if a value was stored before they
answered. Tying food to exercise is the compensation pattern the handoff keeps
out (no manual exercise logging, never "earn" or "burn it off"), so for this
person the design's own rule ("a quiet day never lowers it") is not enough.

**The math** (`Targets.today(adjustment, movementKcal, workoutKcal)`, in
`:libraries:nutrition`):
- activity = max(0, movement) + max(0, workouts); All adds all of it, Some adds
  `EXERCISE_SOME_SHARE` (0.5) of it, None adds 0.
- capped at `EXERCISE_CAP_KCAL` (1,000). Both constants carry
  `TODO(clinician)`: an engineer picked them. The cap stops one bad watch
  reading (a 5,000 kcal glitch) from moving the range absurdly, and sits above
  a normal active day so ordinary movement is not clipped.
- the result is added to the target and both ends, never subtracted. The
  base is the floor, so whatever the watch says, today's range is at least the
  base range. Tomorrow always starts from the base (`Targets.tomorrow()`).

**Where it applies.** `todayRange(settings, day, today, expended)` in
`:features:today:impl` is the one place Today and Day close ask. Activity
counts only when all hold: the day is today, `expended` is non-null (the view
model passes null unless the flag is on and the store is connected), and the
day is not left out of the math (untracked). Movement is
`EnergyExpended.movementKcal`, workouts `workoutKcal`, rounded.

**Where it shows** (v3): Today's "Your range" and Day close's range line move
up; no extra caption, as `today-full.png` has none. Targets gets the
`targets.png` row, `Exercise adjustment` with the choice under it ("No",
"Some of it", "All of it"), opening this page; shown when the flag is on, the
store is connected, the person is not `hard`, and the Targets amounts are
visible. The Targets steppers keep showing the base range: that is what the
person sets.

**Visibility.** The question carries no numbers, so it shows in every mode.
The range is the Calories metric and resolves as before. When energy out is
hidden but calories shown, the range still moves (the person asked for it);
nothing says by how much, so no energy-out amount is rendered.

### Days left out of the math

**What they are.** The days marked untracked (`Day.untracked`, Today's day
menu "Mark as untracked"). The app already leaves those out of every average
and count on Trends (`Day.countsInTrends`, `WeekAverages`, the recap, Meal
patterns), and Today has no hero card on them. This pass adds the third piece
of "the math": an untracked day gets no exercise in its range. So this card is
a list of, and a way to pick, those days. One concept, one stored field, no
second list that could disagree with Trends.

**The card** (v3 `integrations--full.png`): title, one row per day left out
in the last 90 days (Trends' longest span; older days are in no math), newest
first, `Sun, Oct 18` with the day's note muted at the end ("Family visit" is
the note Day close saved). Then `Leave out another day` (or `Leave out a day`
with none, above the line `None yet. A day you leave out doesn't count in your
averages.`). Shown whether or not a store is connected, because Trends uses it
either way.

**The sheet.** `LeaveOutDayRoute()`, `bottomSheet<>` in settings, its own
`LeaveOutViewModel`. A `MonthGrid` (Sunday first, as Today's calendar), days
from 89 days ago to today enabled, left-out days drawn selected. Tapping a day
leaves it out, tapping a left-out day counts it again
(`DiaryRepository.setUntracked`), and the sheet stays open; `Done` closes it.
Future days can't be picked: nothing has happened on them to leave out, and
Today's day menu already offers it for tomorrow if wanted.

```kotlin
data class LeaveOutState(
    val month: LocalDate, val title: String,
    val cells: List<LeaveOutCell?>, val canGoPrev: Boolean, val canGoNext: Boolean,
)
data class LeaveOutCell(val date: LocalDate, val leftOut: Boolean, val isToday: Boolean, val enabled: Boolean)
```
Actions: `DaysArrived(month, leftOut)`, `PrevMonthTapped`, `NextMonthTapped`,
`DayTapped(date)`, `DoneTapped`. Event: `Dismiss`.

### Integrations state, second pass

```kotlin
data class IntegrationsState(
    ...,
    val exercise: ExerciseQuestion? = null,   // connected and not `hard`
    val leftOut: LeftOutCard = LeftOutCard(emptyList()),
)
data class ExerciseQuestion(val selected: ExerciseAdjustment)
data class LeftOutCard(val days: List<LeftOutDay>)
data class LeftOutDay(val date: LocalDate, val label: String, val note: String?)
```
Inputs gain `settings` and `untracked: List<Day>` (from
`observeDays(today - 89, today)`, filtered). New actions:
`ExercisePicked(choice)` (writes `exerciseRange`; ignored for `hard`),
`LeaveOutDayTapped` (event `OpenLeaveOut`). Order on the page as v3: source,
Expended today, the question, days left out, the privacy footer.

### Copy, second pass (v3 verbatim where it has it)
- `Should exercise change your range?`; `No` / `Your range stays the same however much you move.`;
  `Some of it` / `Half of your movement and workouts is added.`; `All of it` /
  `All of your watch's active energy is added.`; `Your range only ever goes up for movement. A quiet day never lowers it.`
- `Days left out of the math`; `Leave out another day`; `Leave out a day`;
  `None yet. A day you leave out doesn't count in your averages.`
- Sheet: `Leave out a day`; `Tap a day to leave it out of your averages. Tap it again to count it.`;
  `Done`; cell description `%1$s, not counted` (never "left": a date next to it reads as calories left to the leak test).
- Targets: `Exercise adjustment` (`integrations_targets_exercise`, kept in this file with the rest of the 0.2 copy so it merges cleanly).

### Tests, second pass
- `TargetsTest` / `ExerciseRangeTest` (property style over a grid of base
  ranges, readings including negatives and huge values, every choice): never
  below the base, never more than the cap above it, None adds 0, Some is half
  of All before the cap, tomorrow unchanged.
- `SettingsTest`: `exerciseRange` round trip, missing key is null, unknown
  value is `none`, `exerciseInRange` is None for `hard` and for unanswered.
- `todayRange` tests: zero activity when not connected (null), a past day, an
  untracked day, `hard`; moves with Some and All.
- `IntegrationsStateBuilderTest`: question hidden when not connected and for
  `hard`; selected reflects the setting (null is None); left-out rows newest
  first with notes; empty card. Across Full, Blind, Fuel, Delayed.
- `IntegrationsViewModelTest`: `ExercisePicked` writes, ignored for `hard`;
  `LeaveOutDayTapped` sends `OpenLeaveOut`.
- `LeaveOutViewModelTest`: tap leaves out, tap again counts it, out-of-window
  and future taps do nothing, month arrows stop at the window, Done dismisses.
- `TargetsStateBuilderTest`/`TargetsViewModelTest`: the row only with flag on,
  connected, not `hard`, calories shown; tapping it opens Integrations.
- `TodayStateBuilderTest` / `DayCloseStateBuilderTest`: range moves with
  Some/All, not for `hard`, not without energy.
- Leak test: `today-exercise` (Today, All of it, with energy) and
  `integrations-leave-out` (the sheet, no amounts) in `SURFACES` and
  `REQUIRED_SURFACES`; `today-exercise` also in `ENERGY_OUT_SURFACES`.
- Previews: Integrations connected with breakdown and each choice (No, Some,
  All), with left-out days, Blind, dark, numbers-have-been-hard (no question);
  the sheet light and dark; Targets with the row; Today with Some of it.
