# Client app events

The registry of structured events the client emits for product analytics. One event = one
`logEvent(name, attrs)` call (the extension in `:libraries:core` `logging/AppEvents.kt`) riding
the normal KLog tree system: it lands in logcat/os_log, as a Sentry breadcrumb, and. Via
`GrafanaLogTree` in `:libraries:telemetry:impl`, as an OTLP log record in Grafana Cloud Loki.
Query conventions are in [`observability.md`](observability.md).

Dashboard queries treat this page as the source of truth for names and attributes, and the
dashboards themselves are committed under [`ops/grafana/`](../../ops/grafana/README.md) so a query
naming an event or attribute nothing emits fails a build rather than rendering an empty panel
forever. Rename an event here and the panels that read it go red, which is the point. Names are
dot-namespaced snake_case; every record automatically carries `session_id` + `install_id` +
`is_offline` (per-record) plus resource attributes (`service.name="opson-client"`,
deployment environment, version, platform). `is_offline` is `AppState.isOffline` captured **at
emit time**, records that ship later from the disk buffer still say what connectivity looked
like when the event happened, so reliability funnels can segment "emitted offline" without span
archaeology.

**Delivery is durable, effectively at-least-once.** The export chain is batch → disk buffer →
OTLP: every batch is written to a file-backed buffer (`<files>/telemetry/…`, via
`durableLogRecordProcessor`) before export and deleted only after the gateway acknowledges it, so
events emitted offline survive process death and ship on a later launch or flush tick. Retention
is the library's defaults, 100 buffered batches, 30-day max age. After which oldest batches are
dropped. A record can rarely ship twice (export acknowledged but the process dies before the
buffer delete), so dashboards counting events should tolerate the odd duplicate rather than
assume exactly-once. Two edges remain lossy by design: records ride in RAM for up to one flush
tick (5s) before reaching disk, and `TelemetryBackgroundFlusher` closes most of that window by
force-flushing the pipe (RAM → disk → export attempt) on every app background, the last reliable
moment before the OS suspends or kills the process.

**Deliberate pipeline calls** (so nobody re-litigates them blind):

- **Batch tuning stays at library defaults** (2048-record queue, 5s flush, 512-record export
  batches, 30s export timeout). Typical volume is a handful of events per user-minute; the
  defaults are sized far above it and the 5s RAM window is bounded by the background flush.
- **Exports are NOT gated on `AppState.isOffline`.** Tempting (skip doomed POSTs while offline),
  but `isOffline` also trips on *backend* unreachability, and surviving backend outages is the
  whole reason this pipe goes direct to Grafana rather than through our server. A failed export
  while offline just stays in the buffer; the DNS failure is contained by
  `FailSafeLogRecordExporter`.
- **`telemetry.appEventsEnabled` + `appEventsSampleRate` stay separate.** The flag is an instant
  kill switch for library bugs / ingest incidents and reads as one in the QA menu; the rate is a
  gradual volume dial. Collapsing them makes the emergency lever a magic number.
- **iOS `previous_exit` is a day-granular MetricKit sample, not per-run truth.** iOS has no
  per-launch exit API, so `IosPreviousExitProvider` subscribes to `MXAppExitMetric`, classifies
  each day-window's foreground exits to the most severe (crash > anr > oom > clean), persists the
  result, and the next launch reports it exactly once (re-reporting every launch would multiply
  one crash by launch frequency). Background jetsam kills are deliberately excluded, routine on
  iOS, they'd read as fake OOMs next to Android's user-perceived `REASON_LOW_MEMORY`. MetricKit
  never delivers on the simulator; only real devices produce non-unknown values.

**Rules for adding events:** emit through the `logEvent` extension only (never a raw
`EXTRA_APP_EVENT` extra), fire on user actions / state transitions. Never per-frame, per-poll,
or per-flow-emission, and add the event here in the same change. Client events answer
intent/funnel/abandonment questions; the backend DB stays source-of-truth for anything already in
a ledger.

## Engagement & session shape

| Event | Attributes | Fires |
|---|---|---|
| `app.launched` | `cold_start` (always true), `previous_exit` (clean/crash/anr/oom/unknown) | Once per cold start, on the boot foreground (`AppLaunchedEmitter`). After the session tracker rolls session #1, so it shares the boot's `session_id` with every other event (it used to fire at DI init and land orphaned on a pre-rollover id). Doubles as the pipeline smoke test. `previous_exit` comes from Android's historical exit reasons (API 30+; older devices report `unknown`); **iOS derives it from MetricKit**, day-granular and up to 24h late. Most iOS launches say `unknown`. Always segment by platform before reading exit rates |
| `app.foregrounded` | `cold_start` | Every foreground (`LifecycleAppEventLogger`); `cold_start=true` on the boot foreground. Count users/sessions from this event, not `app.launched` |
| `app.backgrounded` | `session_duration_sec` | Every background; whole seconds since the matching foreground (monotonic clock), so session length is a direct query. No span join. Omitted in the (shouldn't-happen) case of a background with no prior foreground |

## Reliability from the client's chair

The events that motivated shipping direct-to-Grafana: what never reaches the backend.

| Event | Attributes | Fires |
|---|---|---|
| `net.backend_unreachable` | `operation`, `error_kind` (timeout / exception class) | Shared `NetworkCall` failure path, non-HTTP failures only. An HTTP status IS reachability |
| `net.offline_banner` | `visible`, `os_online`, `backend_reachable` | Each edge of the app-wide offline banner (`AppStateImpl`), carrying which signal drove it |
| `conn.regained` |, | Reserved for offline→online recovery signals (`ConnectivityEdgeDispatcher` drives the app event; emit here if you need it in Loki). Apps with a long-lived socket should extend the `conn.*` namespace: `conn.reconnecting` (`attempt`), `conn.recovered` (`attempts`, `downtime_ms`), `conn.reconnect_failed` (`attempts`) |

## Product funnels

Seed your own here as features land. Keep the pattern: one row per event, name the attributes and
the exact fire site.

### Onboarding

All from `OnboardingViewModel`, read by `ops/grafana/onboarding-funnel.json`. `step` is always the
step's own name, never an index: `welcome`, `question_1` to `question_3`, `statement` (the
optional "numbers have been hard" step, `question_4` before design v3), `recommendation`,
`account`. **No event carries an answer to the setup questions or the statement**, not even as a
count: agreeing that "numbers or tracking have been hard for me before" is close to a clinical
disclosure.

| Event | Attributes | Fires |
|---|---|---|
| `onboarding.step_viewed` | `step` | Each step shown: Welcome on a fresh entry, and every move forward or back between steps |
| `onboarding.auth_selected` | `method` (guest), `returning` | Get started and Skip and start logging (`guest`, false); a stranded anonymous session adopted at entry (`guest`, true). Returning users sign in from Welcome's link, whose flow does not fire this |
| `onboarding.completed` | `duration_sec`, `account_ready`, `preset` (key), `took_recommendation`, `skipped_all` | Once, when Start or Skip all first saves the mode, before the account step or Today. `account_ready` false means guest creation had not landed in time. `took_recommendation` is false on skip all, where nothing was recommended on screen. Coming back from the account step and starting again does not fire it twice |
| `onboarding.account_choice` | `choice` (`phone`, `google`, `apple`, `email`) | When someone leaves the optional account step: Keep it on this phone, a successful Google or Apple link onto the guest, or the hand-off to email sign-up |
| `onboarding.abandoned` | `step` | `onCleared` before the mode was ever saved. A process kill never gets here |

### Modes and the pause

All three fire from `SettingsNumbersChanger` (`:libraries:diary:impl`), the only writer of the
numbers settings, so no screen emits them itself. None carries a level, an amount or the pause
sheet's "What's prompting this?" answer.

| Event | Attributes | Fires |
|---|---|---|
| `mode.changed` | `from`, `to` (preset keys), `adjusted` (overrides remain), `fewer` (the user now sees fewer numbers), `via_pause` | When a change moved the preset or an override, directly or after the pause. The pause switch on its own does not fire it |
| `friction.shown` | `subject` (`preset` / `metric` / `pause`) | The "Pause before showing more" sheet opened |
| `friction.completed` | `subject`, `applied` | The sheet closed: `applied=true` from "Show them" after the wait, `false` from "Keep things as they are", a swipe down or back |

| Event | Attributes | Fires |
|---|---|---|
| `logger.method_picked` | `method` (search, favorite, usual, recent, saved, foods, describe, yesterday, ate_no_details, quick_number, name_only, barcode, water, weigh_in, note), `items` (count) | When the logger or the Log screen writes something (`LoggerViewModel`, `LogViewModel`): once per pick source on a basket commit, once for each other way of logging. Weigh-in and Add a note fire with `items` 0 when their chip opens the screen that does the writing. Never a food name, an amount or typed text; `LoggerTelemetry` takes only the `LogVia` enum and a count. |

### Barcode scan

All from `BarcodeScanViewModel` through `BarcodeTelemetry` (`:features:logger:impl`), behind
`release.barcode`, and read by the Scanner panel on `ops/grafana/foods.json`. **No event carries
the barcode**, a food name or an amount: what someone scans is what they eat. The products people
miss are counted server-side instead, in `barcode_misses` with no account attached.

| Event | Attributes | Fires |
|---|---|---|
| `barcode.scan_started` | `permission` (`granted`: it already was, `asked`: the prompt was just answered yes) | The viewfinder goes live: on opening with the camera allowed, after a yes, or on coming back from Settings with it turned on |
| `barcode.hit` | `source` (`usda_branded`, `off`, `nutritionix`, …), `slow` (the lookup took over 400 ms, so an outside source answered) | A lookup found a food, just before the amount sheet opens |
| `barcode.miss` | | The server had no food for the code (a 404). A lookup that failed, offline or a 503, is not a miss and fires nothing |
| `barcode.permission_denied` | `can_ask_again` | The camera prompt was answered no, or the scanner opened with the camera already off. Counts people who could not scan at all |

A pick from a scan logs with `logger.method_picked` `method=barcode`; "Save the name only" after a
miss logs as `name_only`.

### Watch and health apps (0.2, behind `release.health`)

All from `RealHealthRepository` (`:libraries:health:impl`), the one place that asks the platform
store for anything, so no screen emits them. `source` is `apple_health` or `health_connect`, and it
is the only attribute: **no event carries an energy value, a count of workouts or anything read
from the store.** Health values never leave the device.

| Event | Attributes | Fires |
|---|---|---|
| `health.connected` | `source` | Connect went through: Health Connect granted every permission, or HealthKit's sheet closed without an error (iOS never says whether reading was allowed) |
| `health.permission_denied` | `source` | Connect came back without the permissions, or the prompt failed |
| `health.disconnected` | `source` | Disconnect on Watch and health apps |

### Reminders

0.2, behind `release.reminders`. Fired through `ReminderEvents` in `:libraries:reminders`. Kinds,
levels, counts and permission states only: never a meal name, a meal time or any text.

| Event | Attributes | Fires |
|---|---|---|
| `reminder.set` | `kind` (`meal` / `water`), `level` (`off` / `subtle` / `full`, the meal level as chosen), `meals_on` (0 to 4), `water_on`, `permission` (`granted` / `denied` / `not_asked`) | A change to what gets scheduled: a meal level, a meal switch or a meal time on Reminders (`RemindersViewModel`), water reminders on Water settings (`WaterSettingsViewModel`) |
| `reminder.permission` | `granted` | After the system notification prompt answers. Only asked when someone turns a reminder on |
| `reminder.delivered` | `kind`, `snoozed` | **Android only**, when the alarm posts the notification. iOS has no delivery callback for a backgrounded app, so there is no iOS series to compare |
| `reminder.tapped` | `kind`, `action` (`open` / `log_it` / `already_ate` / `snooze`) | The notification itself or one of its buttons (`ReminderCoordinator`) |

## Warn+ log forwarding (not events)

Besides events, `GrafanaLogTree` forwards plain KLog lines at Warn and above to Loki as ordinary
OTLP logs, client errors visible without waiting on a Sentry crash. Query them with

```
{service_name="opson-client"} | detected_level=~"warn|error"
```

These records have **no `event_name`** (that's how you tell them apart from events); they carry
`session_id`/`install_id`, the logger `tag`, and `exception_type`/`exception_message` when a
throwable was attached. Gated by `telemetry.klogForwardingEnabled` (remote config, default **on**)
and still behind the `telemetry.appEventsEnabled` kill switch + per-session sampling, flipping
the forwarding flag off never affects events.

In-app feedback is not a Loki event: `FeedbackRepository` sends it straight to Sentry via
`Telemetry.captureUserFeedback` (verbatim message, screenshots, session-log attachment).
