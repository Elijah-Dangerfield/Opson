# Grafana dashboards

Three dashboards over the client app events this project emits, committed as JSON. One file per
dashboard, imported by hand.

| File | uid | Answers |
| --- | --- | --- |
| `app-health.json` | `opson-app-health` | Are people launching it, how long do they stay, how slow is the cold start, and how did the last run end? |
| `connectivity.json` | `opson-connectivity` | How often is the app unusable, and when it is, is that us or the user's network? |
| `onboarding-funnel.json` | `opson-onboarding` | Does anyone get through the first-run flow, and where do they drop? |

They are built from the events in [`docs/practices/app-events.md`](../../docs/practices/app-events.md),
which is the taxonomy, and read with the query conventions in
[`docs/practices/observability.md`](../../docs/practices/observability.md).

**These are the template's boards, so they cover the template's events.** A generated app will emit
its own and want its own dashboards; keep these, because the `app.*` and `net.*` questions do not
stop mattering, and add files beside them rather than editing these into something else.

## What has to exist first

**Nothing here has ever rendered real data.** What has been checked is that every query names an
event and an attribute the code actually emits (see below). What has **not** been checked is
anything about rendering — panel types, thresholds, units, query cost at real volume. Nobody has
looked at one of these with data on it. Expect to adjust.

1. **The OTLP credentials exist.** `GRAFANA_OTLP_ENDPOINT` / `GRAFANA_OTLP_TOKEN` reach
   `loadTelemetryMetadata` in build-logic, via GitHub secrets in CI or `grafana.*` keys in
   `local.properties` for a local build. Blank values leave the pipe dormant and every panel here
   stays empty with no error anywhere.
2. **A build has actually run with them.** `app.launched` is the first event through the freshly
   planted tree and doubles as the pipeline smoke test. If
   `{service_name="opson-client"} | event_name="app.launched"` returns nothing in Explore,
   stop here; the dashboards will not tell you why.
3. **The Loki datasource uid matches.** Every panel targets `grafanacloud-logs`, the uid Grafana
   Cloud provisions its Loki datasource under. If your stack differs, rewrite it in one pass:

   ```bash
   printf "Loki datasource uid: "; read -r DSUID
   [ -n "$DSUID" ] && sed -i '' "s/grafanacloud-logs/$DSUID/g" ops/grafana/*.json && unset DSUID
   ```

   Confirm the uid first from **Connections → Data sources → (your Loki) → the URL**, or with
   `/api/datasources` on the stack.

## Importing

Per dashboard, in the Grafana UI: **Dashboards → New → Import → Upload JSON file**. The uid is in
the file, so re-importing updates the existing dashboard rather than making a second copy. No folder
is pinned in the JSON; pick one at import time.

Or over the API, all three at once:

```bash
printf "Grafana URL (e.g. https://myorg.grafana.net): "; read -r URL
stty -echo; printf "Grafana API token (Editor or Admin): "; read -r TOKEN; stty echo; printf "\n"
if [ -z "$URL" ] || [ -z "$TOKEN" ]; then
  echo "both values are required — nothing sent" >&2
else
  for f in ops/grafana/*.json; do
    jq -n --slurpfile d "$f" '{dashboard: $d[0], overwrite: true, folderUid: ""}' |
      curl -sS -X POST "$URL/api/dashboards/db" \
        -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" --data @- |
      jq -r '"\(.status // "ok")\t\(.uid // .message)"'
  done
fi
unset TOKEN URL
```

Import is the whole deployment story. Nothing provisions these automatically and nothing syncs edits
back, so **a change made in the Grafana UI is lost on the next import.** Edit the JSON. Terraform or
Grafana's git-sync would fix that and both are a larger commitment than three files deserve before
anyone has looked at one with real data on it.

## Reading them

**One stack, many apps.** Every project generated from this template exports to the same Grafana
Cloud stack, told apart only by `service_name`. A query without that matcher spans every app you have
ever shipped from here, and a result that looks broad probably is.

**Two stream labels, everything else is structured metadata.** `service_name` and
`deployment_environment` are the only labels; `event_name`, `session_id`, `install_id` and every
event attribute are filtered with pipes. Line filters (`|=`) would work and are wrong — they scan the
body instead of the metadata.

**The `$env` dropdown is `prod` / `dev`**, mapping to `deployment.environment` on the export, which
is `dev` for a debug build and `prod` for a release one. A board reading `prod` while you test a debug
build shows nothing.

**Delivery is at-least-once.** A record can rarely ship twice, so raw counts are approximate. Panels
counting *installs* or *sessions* are immune by construction: a duplicate lands in the same series and
collapses. Ratios are two equally affected counts, so they barely move.

**Sampling is per session.** `telemetry.appEventsSampleRate` hashes the session id, so a sampled-out
session contributes nothing at all rather than losing the middle of a funnel.

## How they are kept honest

`DashboardQueryContractTest` (in `:apps:integration`, under the ordinary `./gradlew testDebugUnitTest`
sweep) parses every query in this directory and every `logEvent(...)` call in the source tree, and
fails if a dashboard references an event or an attribute nothing emits.

This is the check a rename would otherwise pass. A panel filtering on `strikes_used` against an app
emitting `strikes` is not an error anywhere: Loki accepts the query, the panel renders, and it renders
**empty** — which is exactly what a healthy panel looks like before launch. The person who finds it is
whoever eventually asks the dashboard a question and believes the blank answer.

Two consequences for anyone editing these files:

- **The LogQL reader is strict on purpose.** It understands the stream selector, label filters and
  `unwrap`, and throws on anything else. Reaching for `| json`, `| logfmt`, `| line_format` or a `|=`
  line filter fails the test rather than slipping through with nothing extracted. If a panel genuinely
  needs one, teach the reader first. That cost is the feature.
- **Ratios go through `__expr__` math** — two Loki targets and a math node — rather than one
  expression naming two events. A single expression would be allowed, but then every attribute in it
  would have to exist on *both* events.
