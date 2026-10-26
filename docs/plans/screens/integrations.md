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
| Should exercise change your range? | Not built | It moves the energy range on Today. That is a targets change with its own safety rule and a `TODO(clinician)` (settings.md). Today keeps `targets.today(exercise, 0, 0)`, so connecting never moves anyone's range. Next step once reviewed. |
| Days left out of the math | Not built | Only means something with the exercise question. |
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
  and the app is 24, so the app manifest carries
  `tools:overrideLibrary` for it and the reader reports Unsupported below
  Android 9, where Health Connect does not exist.
- **iOS**: `com.apple.developer.healthkit` (true) and
  `com.apple.developer.healthkit.access` (empty) in `iosApp.entitlements`;
  the HealthKit capability in the Xcode target; `HealthKit.framework` linked
  (the Kotlin framework is static, so it does not bring its own link);
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
- Toasts: `Connected to %1$s.`, `Disconnected.`
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
