# Observability: one id, three systems

The stack is Sentry (crashes, user feedback, stack traces), Loki (logs: client app events and
server request logs), and Tempo (server traces). What ties them together is a single correlation
id: **`session_id`**, the UUID of the current client app session.

## The session_id pivot

A "session" is a user-perceptible run of the app: it starts on cold boot and rolls over after
15 minutes in the background (`SessionTracker` in `:libraries:opson`). Each session mints a
fresh UUID, and that one value is stamped everywhere:

- **Client → Sentry.** `SessionTelemetryBinder` writes it onto the crash-reporting scope as a
  `session_id` tag on every rollover, so every crash, error event, and user-feedback report
  carries it. `install_id` (stable per install) rides along as a second tag.
- **Nothing typed rides along to Sentry.** A food search's words are in its URL's query, and
  Ktor's exception messages and the iOS SDK's network breadcrumbs carry whole URLs, so every
  breadcrumb, message, tag, extra and exception message loses URL queries, fragments and barcode
  segments first (`SentryUrlScrubbing.kt`, from 0.2). The iOS failed-request event is off for the
  same reason. Uncaught crashes go straight to the native SDK and are not scrubbed; the network
  layer catches its own failures, so a URL rarely reaches one.
- **Client → server.** `DefaultClientHeadersProvider` sends it on every request as `X-Session-Id`
  (plus `X-Install-Id`).
- **Server → Tempo.** `installHttpServerTracing` (apps/server `plugins/Tracing.kt`) pins
  `session_id`/`install_id` onto the HTTP root span from the headers, and carries them in OTel
  Baggage so `BaggageAttributeSpanProcessor` copies them onto **every child span**, the whole
  trace tree matches `{ .session_id = "…" }`, not just the root.
- **Server → Loki.** CallLogging lifts the same headers into MDC (`plugins/Observability.kt`),
  and the logback OTel appender forwards MDC as log attributes (`captureMdcAttributes` in
  `logback.xml`), so every backend log line for the request is filterable by `session_id`.
- **Server → Sentry.** `captureToSentry` tags server errors with the MDC `session_id`/`install_id`
  plus the active `trace_id`/`span_id`, so a backend error links to both the client session and
  its Tempo trace.
- **Client → Loki.** `GrafanaLogTree` (`:libraries:telemetry:impl`) stamps `session_id` /
  `install_id` / `is_offline` on every app-event record it exports.

The key naming rule: it is always the underscore form `session_id`, in all systems, so one query
string works everywhere. The same rule applies to any context you add: if a key exists on backend
spans and client Sentry tags, spell it identically (`Telemetry.setContext(key, value)` client-side,
`SpanAttrs` server-side).

## One stack, many apps

Every project generated from this template sends to the **same** Grafana Cloud
stack. Nothing separates them at the account level; they are told apart by
`service_name`, which is `<project>-client` and `<project>-server` and is
per-project because the rename pass rewrites it. `deployment_environment` then
splits dev from prod inside that.

So a query without a `service_name` matcher spans every app you have ever
shipped. That is occasionally what you want and usually not, and it is the
first thing to check when a Loki result looks too broad.

The practical consequences:

- The three `GRAFANA_*` credentials are account-wide, like the Sentry org
  token. They live in the machine-local credential store and
  `setup_github_secrets.main.kts` pushes them, so a new project needs none of
  them typed.
- Quota is shared. One chatty app can eat the allowance and blind the others,
  which is what `telemetry.appEventsSampleRate` and the
  `telemetry.appEventsEnabled` kill switch are for. Both are remote config and
  per app, so throttling one needs no release.
- Retention is per stack, so you cannot keep one app's data longer than
  another's.

Splitting an app onto its own stack later is three secrets and a rebuild, not a
migration, because the separation already lives in the labels. See
decisions.md, 2026-09-21.

## Loki label conventions

Stream labels are only `service_name` + `deployment_environment`. Everything else, `event_name`,
`session_id`, `install_id`, event attributes, `detected_level`. Is **structured metadata**: filter
with pipes, never line filters.

```
# All app events from prod clients
{service_name="opson-client", deployment_environment="prod"} | event_name != ""

# One event type
{service_name="opson-client"} | event_name="app.launched"

# Client Warn+ logs (no event_name, that's how you tell them from events)
{service_name="opson-client"} | detected_level=~"warn|error"

# Server logs for one session
{service_name="opson-server"} | session_id="<uuid>"
```

Client records also carry resource attributes: `service.version`, `platform` (android/ios),
`build_number`, `commit_sha`, `release_channel`, and `deployment.environment` (dev for debug
builds, prod for release).

## Dashboards live in the repo

The boards (Pulse, Users, User detail and the product boards) are committed as JSON under
[`ops/grafana/`](../../ops/grafana/README.md) and imported by hand, rather than created in Grafana
and left there. The reason is what a broken panel looks like: one that filters on an attribute the
app does not emit renders **empty**, which is indistinguishable from a healthy panel before launch.
A dashboard that lives only in Grafana has no way to be wrong in a build, so
`DashboardQueryContractTest` parses every committed query and every `logEvent(...)` call in the
source tree and fails when a dashboard names an event or attribute nothing emits.

## How to find a session

Start from wherever the report landed and pivot on the id:

1. **From a Sentry issue or feedback report:** copy the `session_id` tag.
2. **Client side of the story:** `{service_name="opson-client"} | session_id="<uuid>"` in
   Loki, the app events and Warn+ logs for that session, each stamped with `is_offline` *at emit
   time* (a record that shipped later from the disk buffer still says what connectivity looked
   like when it happened). Feedback reports also carry a `session-log.txt` attachment, the
   in-memory ring buffer of fine-grained logs that never left the device.
3. **Backend side:** `{service_name="opson-server"} | session_id="<uuid>"` for logs;
   `{ .session_id = "<uuid>" }` in Tempo for every request trace the session produced.
4. **The reverse direction works too:** a server error in Sentry carries `trace_id` (paste into
   Tempo) and `session_id` (pull the client's events), so backend-first investigations reach the
   client story in one hop.

Build provenance closes the loop: client Sentry events are tagged `commit_sha`/`commit_branch`
(injected at build time, see `loadVersionMetadata` in build-logic), so a report pins to the exact
code that produced it.

## Credentials and kill switches

All client telemetry credentials are build-time injected and env-optional (`loadTelemetryMetadata`
in build-logic → `TelemetryInfo` in `:libraries:core`): blank `SENTRY_DSN` disables crash
reporting (`Sentry.init` is never called, so it costs nothing at runtime); blank Grafana values
leave the OTLP pipe dormant. Nothing breaks in a fresh clone.

The Sentry DSN resolves env → `local.properties` → the committed
`telemetry.properties`, and the committed file is where it is supposed to live: a DSN is a
write-only ingest endpoint that ships inside every store binary, so it is not a secret, and a
per-developer file means every fresh clone reports nothing with no signal that anything is wrong.
`scripts/setup_sentry.main.kts` fills it in and proves an event arrives. The release workflow
refuses to build a store binary when no DSN resolves at all.

At runtime, remote config owns the levers (`:libraries:telemetry:impl` `TelemetryConfigValues`):
`telemetry.appEventsEnabled` (instant kill switch), `telemetry.appEventsSampleRate` (per-session
sampling, stable-hashed so a session's events are all-or-nothing), and
`telemetry.klogForwardingEnabled` (Warn+ log mirroring). The server's OTel pipeline is gated by a
single env var: `OTEL_EXPORTER_OTLP_ENDPOINT` unset → stdout exporters, set → OTLP/HTTP.

The event registry lives in [`app-events.md`](app-events.md).
