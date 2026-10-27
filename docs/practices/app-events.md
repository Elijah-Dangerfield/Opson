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
step's own name, never an index: `welcome`, `question_1`, `question_2`, `statement` (the
optional "numbers have been hard" step, `question_4` before design v3), `recommendation`, the
setup steps `goal`, `resting_energy` and `meal_times` (`release.onboardingSetup`), and `sign_in`
(the required sign-in step straight after Welcome; `account` before accounts were required).
`question_3` ("What do you want to notice?") was cut in onboarding v2 (`docs/plans/onboarding-v2.md`). **No event carries an answer to the setup questions or the statement**, not even as a
count: agreeing that "numbers or tracking have been hard for me before" is close to a clinical
disclosure.

| Event | Attributes | Fires |
|---|---|---|
| `onboarding.step_viewed` | `step` | Each step shown: Welcome on a fresh entry, and every move forward or back between steps |
| `onboarding.auth_selected` | `method` (`apple`, `google`), `returning` | A successful sign-in on the sign-in step. `returning` false is a new account, which goes on to the questions; true is an account that already existed (straight to Today) or a guest from an older build linking onto their log. Email sign-in runs on its own screens and does not fire this |
| `onboarding.sign_in_failed` | `method`, `reason` (`offline`, `not_enabled`, `in_use`, `failed`) | A sign-in on the sign-in step that came back with an error. A dismissed Apple sheet or a cancelled browser is not a failure |
| `onboarding.completed` | `duration_sec`, `preset` (key), `took_recommendation` | Once, when Start or the last setup step saves the mode, just before Today. Before accounts were required it also carried `account_ready` and `skipped_all` |
| `onboarding.abandoned` | `step` | `onCleared` before the mode was saved or the person left for Today. A process kill never gets here |

### Modes and the pause

All of these fire from `SettingsNumbersChanger` (`:libraries:diary:impl`), the only writer of the
numbers settings, so no screen emits them itself. None carries a level, an amount or the pause
sheet's "What's prompting this?" answer.

| Event | Attributes | Fires |
|---|---|---|
| `mode.changed` | `from`, `to` (preset keys), `adjusted` (overrides remain), `fewer` (the user now sees fewer numbers), `via_pause` | When a change moved the preset or an override, directly or after the pause. The pause switch on its own does not fire it |
| `mode.timer_set` | `weeks`, `then` (preset key) | End after a set time turned on or changed |
| `mode.timer_cleared` | | End after a set time turned off |
| `mode.timer_ended` | `from`, `to` (preset keys), `fewer` | The timer ran out and the mode switched. A switch that would show more with the pause on waits for the person instead, and fires `mode.changed` when they take it |
| `friction.shown` | `subject` (`preset` / `metric` / `pause` / `reveal_time` / `reveal_on` / `progress_photos`) | The "Pause before showing more" sheet opened |
| `friction.completed` | `subject`, `applied` | The sheet closed: `applied=true` from "Show them" after the wait, `false` from "Keep things as they are", a swipe down or back |

| Event | Attributes | Fires |
|---|---|---|
| `logger.method_picked` | `method` (search, favorite, usual, recent, saved, foods, describe, yesterday, ate_no_details, quick_number, name_only, barcode, photo, water, weigh_in, note), `items` (count) | When the logger or the Log screen writes something (`LoggerViewModel`, `LogViewModel`): once per pick source on a basket commit, once for each other way of logging. Weigh-in and Add a note fire with `items` 0 when their chip opens the screen that does the writing. Never a food name, an amount or typed text; `LoggerTelemetry` takes only the `LogVia` enum and a count. |

### Search options

From `SearchOptionsViewModel` and `LoggerViewModel` through `SearchOptionsTelemetry`
(`:features:logger:impl`), behind `release.searchOptions`. Values only: **no event carries the
query** or a food. A pick of what is already picked fires nothing.

| Event | Attributes | Fires |
|---|---|---|
| `search.sort_picked` | `sort` (`best_match`, `most_recent`, `a_to_z`) | A sort picked in the options sheet |
| `search.database_picked` | `database` (`all`, `usda`, `open_food_facts`, `my_foods`) | A database picked in the options sheet |
| `search.tabs_toggled` | `on` | The Category tabs switch flipped |
| `search.tab_picked` | `tab` (`all`, `favorites`, `custom`, `common`, `restaurant`, `beverages`) | A category tab tapped under the search bar |

### Barcode scan

All from `BarcodeScanViewModel` through `BarcodeTelemetry` (`:features:logger:impl`), behind
`release.barcode`, and read by the Scanner panel on `ops/grafana/foods.json`. **No event carries
the barcode**, a food name or an amount: what someone scans is what they eat. The products people
miss are counted server-side instead, in `barcode_misses` with no account attached.

| Event | Attributes | Fires |
|---|---|---|
| `barcode.scan_started` | `permission` (`granted`: it already was, `asked`: the prompt was just answered yes) | The viewfinder goes live: on opening with the camera allowed, after a yes, or on coming back from Settings with it turned on |
| `barcode.hit` | `source` (`usda_branded`, `off`, `nutritionix`, …), `slow` (the lookup took over 400 ms, so an outside source answered), `typed` (the code was typed by hand, not read by the camera) | A lookup found a food, just before the amount sheet opens |
| `barcode.miss` | `typed` | The server had no food for the code (a 404). A lookup that failed, offline or a 503, is not a miss and fires nothing |
| `barcode.camera_unavailable` | | The camera was allowed but could not run: no back camera (the iOS simulator), a failed start, or another app holding it. Once per camera session |
| `barcode.permission_denied` | `can_ask_again` | The camera prompt was answered no, or the scanner opened with the camera already off. Counts people who could not scan at all |

A pick from a scan logs with `logger.method_picked` `method=barcode`; "Save the name only" after a
miss logs as `name_only`.

### Photo estimate

0.2, behind `release.photo`. All from `PhotoEstimateViewModel` through `PhotoTelemetry`
(`:features:logger:impl`). **No event carries the photo, the note, a food name or an amount**:
what someone photographs is what they eat. Counts and reasons only.

| Event | Attributes | Fires |
|---|---|---|
| `photo.started` | `source` (`camera`, `library`) | A photo came back from the camera or the picker and the estimate was asked for. Backing out of either fires nothing |
| `photo.estimated` | `items`, `matched` (counts) | The review shows: `items` lines, `matched` of them found in the catalog |
| `photo.added` | `items` (count) | Add to {meal} wrote the photo's lines |
| `photo.failed` | `reason` (`unavailable`: no key on the server; `too_many`: the per-user limit; `offline`; `error`: the server or the provider failed, or the photo could not be read; `nothing_found`: the model saw no food) | A calm end instead of the review |

Lines added from a photo log with `logger.method_picked` `method=photo` (stored as method
`describe`, precision `estimate`, since the server has no `photo` method).

### Voice log (0.2, behind `release.voice`)

All from `LoggerViewModel` through `VoiceTelemetry` (`:features:logger:impl`), for the mic in
Describe it. **No event carries the transcript, a word of it, or anything parsed from it**: what
someone says is a meal in their own words. Length is a bucket, never a count. A spoken
description that gets logged also fires `logger.method_picked` `method=describe`, as typed text
does.

| Event | Attributes | Fires |
|---|---|---|
| `voice.started` | `on_device` (the words are worked out on the phone), `permission` (`granted`: it already was, `asked`: the prompt was just answered yes) | The mic went live |
| `voice.transcribed` | `words` (`1-3`, `4-7`, `8-15`, `16+`) | A session ended with words, which then go to Describe's parse |
| `voice.permission_denied` | `can_ask_again` | The microphone (or on iOS, speech recognition) was refused, the mic was tapped with it already off, or a session found it turned off as it started |
| `voice.fallback` | `reason` (`no_recognizer`, `no_model`, `no_microphone`, `needs_connection`, `no_speech`, `busy`, `error`) | Voice could not help and offered typing instead: no recognizer on the phone, nothing said, no connection for a server recognizer, or an error with nothing heard |

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

### Widgets

0.2, behind `release.widgets`. Fired through `WidgetEvents` in `:libraries:widgets`. Never a
number, a meal name or any text from the snapshot.

| Event | Attributes | Fires |
|---|---|---|
| `widget.log_tapped` | `next_meal` (whether a meal was still to come, so the Logger opened on it rather than its own default) | A home screen widget's + opened the app (`WidgetOpener`) |

### App guard (0.2)

From `AppGuardImpl` and `AppGuardLayer` through `AppGuardTelemetry` (`:features:upgrade:impl`).
Which state someone saw and what they did; **never the operator's maintenance message.** Not behind
a release flag: the guard is how a 0.2 build is stopped, so it must work before anything is turned
on.

| Event | Attributes | Fires |
|---|---|---|
| `guard.shown` | `state` (`maintenance_blocking`, `maintenance_banner`, `maintenance_offline`: a blocking maintenance shown as the banner because the phone could not reach the server, `upgrade_required`, `update_available`) | Each time the guard moves into one of those states from another, launch included |
| `guard.store_opened` | `from` (`upgrade_required`, `update_available`) | Update tapped on the stop or the strip |
| `guard.update_dismissed` | | The update strip put away; it stays away until the next launch |

### Fasting (0.2)

Both from `FastingRepositoryImpl` (`:libraries:diary:impl`), the only writer of fasts, so no
screen emits them. **No event carries a duration, a start or an end time.** How long someone
goes without eating is health data, and a duration in Loki is one query away from a
longest-fast record, which the app refuses to keep.

| Event | Attributes | Fires |
|---|---|---|
| `fast.started` | `plan` (`12:12`, `14:10`, `16:8`) | When this device writes the evening's fast. A fast starts when the eating window closes, so this fires the first time a screen showing the timer looks after the close, not at the close itself. A fast pulled from another device does not fire it |
| `fast.ended` | `by` (`user`, `food`, `timer_off`) | End fast, logging food on today or earlier, or switching the timer off, while a fast runs. Reaching the goal is derived, not written, and fires nothing |

## Warn+ log forwarding (not events)

Besides events, `GrafanaLogTree` forwards plain KLog lines at Warn and above to Loki as ordinary
OTLP logs, client errors visible without waiting on a Sentry crash. Query them with

```
{service_name="opson-client"} | detected_level=~"warn|error"
```

These records have **no `event_name`** (that's how you tell them apart from events); they carry
`session_id`/`install_id`, the logger `tag`, and `exception_type` when a throwable was attached.
The exception's message is never sent: it can carry food names, emails or URLs. Gated by `telemetry.klogForwardingEnabled` (remote config, default **on**)
and still behind the `telemetry.appEventsEnabled` kill switch + per-session sampling, flipping
the forwarding flag off never affects events.

In-app feedback is not a Loki event: `FeedbackRepository` sends it straight to Sentry via
`Telemetry.captureUserFeedback` (verbatim message, screenshots, session-log attachment).
