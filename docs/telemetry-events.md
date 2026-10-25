# Telemetry events

What a project generated from this template emits before anyone adds a feature,
and which platform emits it. Written down because a dashboard, an alert or a
funnel built against an event the app does not actually send reads zero forever
and looks exactly like a real zero.

This is a description of what exists, **not a contract**. Nothing enforces it
yet. If you build dashboards on these, add a test that holds the queries to the
emit sites, see "Dashboards" at the bottom.

Every record also carries install attribution (`genuine_install`, `is_emulator`,
`is_sideloaded`, `is_rooted`, `installer_package`, `device_class`, `os_version`)
on the OTel **resource**, plus `session_id` and `install_id`. Those are what let
you separate real users from developer emulators and sideloaded debug builds;
without them, crash-free rate is measured against a population that includes you.

## App lifecycle

| Event | Attributes | Platforms | Emitted by |
|---|---|---|---|
| `app.launched` | `cold_start`, `previous_exit` | both | `GrafanaAppEvents` |
| `app.foregrounded` | `cold_start` | both | `LifecycleAppEventLogger` |
| `app.backgrounded` | `session_duration_sec` | both | `LifecycleAppEventLogger` |

`app.launched` deliberately waits for the cold-boot dispatch so it carries a
settled `session_id`.

## Performance

| Event | Attributes | Platforms | Emitted by |
|---|---|---|---|
| `app.startup` | `startup_ms` | **Android only** | `StartupReporter` |
| `app.jank` | `screen`, `frames`, `janky_frames`, `jank_pct`, `worst_frame_ms` | **Android only** | `AndroidJankMonitor` |

Both are Android-only on purpose, and the absence is the honest answer rather
than a gap to fill:

- **`app.startup`** needs a readable process-start clock. iOS has none short of
  `sysctl(KERN_PROC)`, which Kotlin/Native does not expose for Apple targets and
  which is a required-reason API. The right iOS source is MetricKit's
  `MXAppLaunchMetric`, which is a daily histogram rather than one launch. So it
  belongs under its own event name, not this one.
- **`app.jank`** needs per-frame timing with a "missed its deadline" signal.
  UIKit has no equivalent, and a lookalike number reconstructed from
  `CADisplayLink` would mean something different enough that charting the two
  platforms together would mislead.

Anything that splits these by platform must therefore expect iOS to be empty. A
single-number "startup" panel across both platforms is wrong.

`app.startup` drops anything over 30s (that is the system having started the
process in the background hours earlier, not a launch) and reports once per
process (an Activity recreation draws a fresh first frame that is not a
startup). `app.jank` reports once per screen visit with a 120-frame floor, so a
screen passed through in three frames does not report 33% jank.

## Network

| Event | Attributes | Platforms | Emitted by |
|---|---|---|---|
| `net.backend_unreachable` | `operation`, `error_kind` | both | `NetworkCall` |
| `net.offline_banner` | `visible`, `os_online`, `backend_reachable` | both | `AppStateImpl` |

## Onboarding

| Event | Attributes | Platforms | Emitted by |
|---|---|---|---|
| `onboarding.step_viewed` | `step` | both | `OnboardingViewModel` |
| `onboarding.auth_selected` | `method`, `returning` | both | `OnboardingViewModel` |
| `onboarding.abandoned` | `step` | both | `OnboardingViewModel` |
| `onboarding.completed` | `duration_sec`, `account_ready` | both | `OnboardingViewModel` |

## Names that exist only in tests

These appear in test fixtures and **nothing in production code emits them**. A
panel querying any of them reads zero forever and looks healthy:

- `conn.reconnecting`
- `purchase.failed`
- `example.joined`, `example.completed`, `example.abandoned`

They are fine as fixture data: a test needs *some* event name. But they are
not part of the app's surface. Either emit them for real or do not build on
them.

Finding these is the reason this file exists. The inventory that produced it is
worth re-running when you add events, and worth running with a regex that spans
lines: a single-line `logEvent\("..."` pattern misses every multi-line call,
which on the first pass here hid `net.offline_banner` and `onboarding.completed`
entirely.

## Dashboards

None ship here, see [decisions.md](decisions.md). The
short version: a board's panels encode one app's questions, and the prerequisite
for a portable one is deciding which events the template *guarantees*, which is
a design decision nobody has made.

If you add dashboards in a generated project, keep the JSON in the repo and add
a contract test that parses the queries out of the panels and asserts that every
attribute and every filtered value is one some emit site can actually produce.
Without it a query drifts from the code and the panel quietly reads less than it
claims. One trap when you write that test: a Gradle test task cannot see files
the test reads at runtime, so the dashboards, this file and the scanned source
tree all have to be declared with `inputs.files(...)`, otherwise the task stays
`UP-TO-DATE` and passes while checking nothing.
