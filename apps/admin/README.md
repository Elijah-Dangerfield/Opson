# Remote config admin console

The admin console for remote config / feature flags. It's a Compose
Multiplatform **web** app (Compose HTML / DOM), **hosted by each environment's
server at `/admin`**:

- dev: `https://opson-server-dev.fly.dev/admin/`
- prod: `https://opson-server-prod.fly.dev/admin/`

It's the only `js` target in the repo and shares no code with the client. It
talks to the server's token-gated `/v1/admin/config` API over HTTP.

## How it works (the 60-second mental model)

- **Each server serves its own console.** The deploy workflows build the JS
  bundle and ship it inside the server image (`apps/server/admin-web/` →
  `/app/admin-web`, served by `installAdminWeb`). The console at a given origin
  manages that origin's server + database; the sibling environment is a link to
  its own console.
- **Tokens are pasted at runtime, never baked in.** This repo is public: the
  bundle holds no secrets. On first use you paste the env's `ADMIN_API_TOKEN`;
  the console validates it with a real call, then keeps it in that browser's
  localStorage (per env, with a "Forget token" button). Every request carries
  `X-Admin-Token`, plus `X-Admin-Actor: <your name>` for the audit log.
- **The page is public but inert.** Serving the HTML/JS reveals nothing that
  isn't already in this public repo; every API call it can make is token-gated.
- **Cross-origin still matters for local dev.** When you run the console
  locally against a deployed server, the browser preflights with the admin
  headers and PUT/DELETE methods, both must be in the server's CORS allow-list
  (`apps/server/.../plugins/Cors.kt`).

```
 your browser  ──HTTP + X-Admin-Token──▶  app server (Fly or local)    ──▶  Postgres
   /admin bundle                           /v1/admin/config …                app_config_* tables
```

## Quickstart

**Normal use:** open the environment's `/admin/` URL, paste that env's
`ADMIN_API_TOKEN` (Fly secrets are write-only, so the token's source of truth
is wherever it was generated, ask, or rotate it), set **Actor** to your name,
Connect. Done, the token is remembered by this browser until you forget it.

**Working on the console itself:** the **"Admin Web"** run config in the IDE, or:

```bash
# hot-reloading dev server (recommended, rebuilds on save)
./gradlew :apps:admin:jsBrowserDevelopmentRun --continuous
```

Gradle prints the `localhost` URL. The local run isn't served by a known env,
so you get all three env buttons (Local / Dev / Prod) and paste tokens the same
way.

## Using the tool

- **Target lens.** Set a synthetic client: platform, app version, country,
  locale, user id, install id, and hit *Resolve*. The flag table then shows,
  per flag: **in-code default → DB base → which rule won → resolved value** for
  that target. This is how you answer "what does a 9.1 / US user actually get."
- **Flags.** Each flag expands to a detail view: edit the base value, see its
  rules as plain sentences, and add/edit/enable/disable/delete rules. Add a
  brand-new flag by dotted path at the bottom.
- **Rules / targeting.** Per flag, ordered rules (first match wins, else base):
  platform, **semantic app-version bounds** (`> 1.0.1`), build-code range,
  country, locale, user-id allow/deny, staged rollout %. "Add rule for this
  target" pre-fills the conditions from the lens above.
- **Versions.** What a captured build shipped with: the in-code defaults per
  app version (see the manifest section below).
- **Audit.** Every change, newest first, with before/after diffs.
- **Users.** Support's lookup: find an account by its user id or part of
  its email or display name (blank lists the 25 most recently seen), then see
  the account (email, guest or claimed, joined, last seen, last change
  synced), every build each install has run, the Settings it chose and how it
  logs, in counts. It links to the Grafana User detail board and to Sentry
  for the same id. Server side it's `/v1/admin/users`, reading the `support`
  views, so it shows no food, amount, weight or note. Under each account,
  **Access** shows its role (and who set it, and why), its permissions, every
  entitlement it holds with the reasons (everyone, its role's bundle, a
  grant), its revocations, every grant ever made and the change trail. From
  there you set the role, grant an entitlement or a bundle (Pro is the bundle
  `pro`) with a source tag, a reason and an optional end in days, revoke one
  entitlement, or remove a grant. Server side it's `/v1/admin/users/{id}/access`
  and its siblings (`apps/server/README.md`, "Roles and entitlements").
  Below that, **Builder** shows the account's place in the Builder program:
  status, trust level (and whether it is held), progress toward Pro, Pro
  until, photo retention, and the record trust is worked out from (reviewed in
  all, agreement over the last 50, decided and rejected in 30 days, fake or
  copied rejections, audits and failed audits, auto-approvals since Trusted).
  From there you remove someone (with a reason; it takes the builder role and
  its Pro), restore them, or hold them at Builder whatever the record says. An
  account that never joined reads "Not a Builder". Applications are decided
  in the Builders tab. Server side it's `/v1/admin/users/{id}/builder`.
  Below that, **Moderation**: the account's standing, how much it contributed
  and from how many devices, and the buttons to Ban, Suspend (for N days) or
  Restrict contributions, with a reason, a note, "mark their devices" and
  "also roll back their contributions"; Roll back on its own; Lift and the
  appeal state on each action; Clear on each marked device (a second-hand
  phone); and the trail. A ban or rollback on prod asks you to type the
  environment's name. `#users/<id>` opens straight on an account, which is how
  the Builders board in Grafana links each contributor. Server side it's
  `/v1/admin/users/{id}/moderation` (`apps/server/README.md`, "Moderation").
- **Entitlements.** The policy, which is remote config: `entitlements.everyone`
  (what every account has), `entitlements.bundles` (Pro and any other bundle)
  and `entitlements.roles` (which bundles a role brings), each edited as JSON
  and saved through the config API, so the server refuses an unknown key and
  the change lands in the config Audit. Taking something out of everyone asks
  again. Below that, the entitlements and roles the server knows, who holds
  each by policy, and the latest role and grant changes across accounts.
- **Review.** Foods people added from photos of a pack (`release.submitFood`),
  pending oldest first, with approved and rejected a click away. Each row
  says where routing sent it and how confident it was, its first two reasons
  in plain words ("Fat not found in the photo's text"), and a "Would
  auto-approve" badge when the auto lane would have taken it. A submission
  opens with the front, label and (when there is one) ingredients photos side
  by side, then the routing record (lane, trust, mode, confidence, which
  headline numbers the photo's text showed, the second read's verdict) and
  what the phone read (engine, camera or library, score), then every field as
  the model read it (with its confidence), as the phone drafted it, as the
  person sent it, and as it stands, with the OCR lines beside them (shown by
  default, `t` hides them), and the checks the server ran on each. Correct
  any field and save, approve (the food joins search and barcode lookup for
  everyone, with the barcode and `verified` set) or reject with a reason and
  an optional code (the submitter keeps their own copy; fake data or copied
  data ends their chance of Trusted). After a decision the next pending one
  opens. Keys, outside a text field: `j`/`k` (or the arrows) move, `o` or
  Enter opens the first, `a` approves (with the usual confirm), `r` jumps to
  the reject reason, `s` saves corrections, `t` toggles the text, `Esc`
  closes, `?` lists them.

  Above the pending list sit the weekly pass's shortcuts (the
  [runbook](../../docs/practices/food-review-runbook.md) walks through them).
  **Approve N that passed every check** approves, one at a time after one
  confirm, the pending foods with photos, every check passing and no routing
  reason about the photo or the numbers: only `not_trusted`, `no_cloud_read`,
  `mode:*`, and `low_confidence` at 85% or more (`ReviewBundle.eligible`).
  **Export pending (JSON)** downloads the oldest 200 as an
  `opson-review-bundle/1` file (fields, the phone's draft and OCR lines,
  checks, reasons, photo URLs that need the token, and an empty `decision`
  each) for an AI tool you run yourself to pre-check offline;
  `scripts/review_bundle.py photos` downloads the photos beside it so the
  tool never sees the token. **Import decisions** reads the filled file back
  and shows every decision with its corrections as before → after and any
  problem (unknown id, no longer pending, a reject with no reason). Only the
  ticked rows are applied, after a confirm. **Audits** lists auto-approvals picked for a person to
  check: pass one, or fail it with a note the Builder is told, which puts the
  food back to pending. Below the queue, barcodes suggested by a miss followed by
  a search: attach one to its food or dismiss it. The photos are fetched with
  the admin token into a blob URL that lives in the tab; nothing links to
  them. `#review/<id>` in the address opens one submission, which is how the
  Grafana Food review board links here. Server side it's
  `/v1/admin/food-submissions` and `/v1/admin/barcode-links`.

  Then **Reports on foods**: "Report a problem" from any food's detail
  (`release.foodReports`) and the server's own energy check, grouped per
  food, most reported first, then oldest. A food opens with what it is served
  as now beside every suggestion (per 100 g, as accepting stores it), the
  label photos and the notes. *Accept* writes a correction, prefilled with the
  newest suggestion from a person and editable, that is laid over the
  source's data, so a re-import never undoes it; *Undo* in the food's
  correction history takes it back. *Reject* and *Duplicate* close the
  reports and change nothing. Server side it's `/v1/admin/food-reports` and
  `/v1/admin/food-corrections`.
- **Builders.** Applications to Opson Builders, oldest first, with the count
  waiting on the tab. Each shows what they wrote about why they want to help,
  how often they buy packaged food, their label region and photo choice, and
  the account behind it: age, how much it logs, foods sent and how they went,
  reports accepted and turned down, checks, any moderation (in force or
  lifted), earlier applications and whether they were a Builder before.
  **Accept** makes them a Builder (the membership and the builder role, which
  brings Pro while they're active; audited under your actor name). **Decline**
  takes an optional note they see in the app; they can apply again in 30 days.
  Below, the latest decisions, with **Remove** (and a reason) for an accepted
  Builder. **Open account** jumps to the account under Users. Server side it's
  `/v1/admin/builders/applications` (`apps/server/README.md`, "The Builder
  program").
- **Waitlist.** The website waitlist for the connected environment: how many
  addresses, the newest 50 (page back with *Older*), *Export CSV*
  (`email,source,created_at`, downloaded by the browser), and *Remove*, per
  row or by typing the address from a removal request. Remove deletes the
  address, as the site's privacy wording promises. Server side it's
  `/v1/admin/waitlist` (see `apps/server/README.md`).
- **Kill switches.** The pinned panel above the tabs holds the emergency flags
  (maintenance mode/message, min supported build, and the build below which the
  app offers an update) with an ALL CLEAR / BANNER / BLOCKING state header. The
  app guard reads them from **Opson 0.2 on**, at launch and on every return to
  the app; **0.1 does not read them at all**, so 0.1 users see nothing whatever
  you set. What each does in 0.2+:
  - `maintenanceMode = blocking`: a full-screen stop with the message, and a
    *Try again* that refetches. A phone that cannot reach the server (offline,
    or the server is down) gets the banner instead and keeps logging on the
    phone, because it could not tell that maintenance had ended.
  - `maintenanceMode = banner`: the message in a strip above the app; the app
    works normally.
  - `minSupportedVersionCode`: builds below it get a full-screen update screen
    that opens the store. Offline too.
  - `latestVersionCode`: builds below it get a dismissable "a new version is
    ready" strip. 0, the default, offers nothing.
  To act on one build or platform only, add a targeting rule (build range,
  platform) on the switch rather than changing its base value.
- **Guardrails.** Every write shows a before → after confirm sheet naming the
  environment. Writes are also type-checked server-side against the registry
  (you can't set `upgrade.minSupportedVersionCode = "six"`); lockout/force-upgrade changes get
  scarier wording, and on prod they require typing the env name. Failed writes
  stay in a dismissible error log with the attempted value.

Edits go live on the client's next config refresh (the server caches the
resolved tree for ~30 seconds).

## Testing it end to end

The page is just a client, it needs a **server that has these endpoints and the
CORS allow-list**. Two ways to get one:

### A) Against a deployed environment (dev/prod)

Pick **Dev** (or Prod) and Connect. This only works once the deployed server
includes the config-admin endpoints **and** the CORS header allow-list. If you
get **403**, the server is rejecting the admin headers at CORS (it predates the
fix); if flags load but the target lens / Versions are empty with an "unavailable
on this server" note, the server predates the manifest/resolve endpoints. The fix
is to deploy a server build that includes them.

### B) Locally, end to end (no deploy needed)

Run the server on your machine and point the page at it with the **Local** env.

```bash
# 1. a throwaway Postgres
docker run --rm -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:16

# 2. the server: Flyway applies every migration (incl. the config tables) on boot.
#    Put these in apps/server/.env (gitignored) or export them, then run:
#      DATABASE_URL=postgresql://postgres:postgres@localhost:5432/postgres
#      SUPABASE_URL=https://<your-project-ref>.supabase.co   # any valid URL; admin routes don't use JWT
#      ADMIN_API_TOKEN=<same value as `local=` in admin-tokens.local.properties>
./gradlew :apps:server:run

# 3. the page (separate terminal), then pick the "Local" env and Connect
./gradlew :apps:admin:jsBrowserDevelopmentRun --continuous
```

Local Postgres starts empty, so there are no flags until you add one (or upload a
manifest, see below). This is the fastest way to exercise the full feature set,
including the per-target resolve and the Versions tab, without touching a shared
database.

## Previews?

Compose **HTML** (what this module uses) has **no `@Preview`**, that's a
Compose-UI / Android feature, and this isn't Compose UI, it's DOM. The preview
*is* the hot-reloading dev server: run `jsBrowserDevelopmentRun --continuous` and
the browser updates on save. For a quick render check without a live server you
can also serve the built bundle statically
(`apps/admin/build/dist/js/developmentExecutable`), but the dev server is the
normal loop.

## Per-version defaults manifest (what a build shipped with)

The admin tool can show the **in-code defaults a given app version shipped
with**, the baseline a remote override replaces. Those defaults live in the
client's `ConfiguredValue` classes, which the JS admin module can't read, so the
build exports them and uploads them per deploy.

**Automate this in your server-deploy workflow.** An `Upload config manifest`
step that runs `exportConfigManifest` and PUTs the result after each deploy,
using an `ADMIN_API_TOKEN_{DEV,PROD}` secret, keeps it current. It's stamped from
`versions.properties` and idempotent (re-uploading a version replaces its rows),
and it skips without failing the deploy if the token secret isn't set. Set both
`ADMIN_API_TOKEN_DEV` and `ADMIN_API_TOKEN_PROD` so dev and prod both upload
on deploy.

To do it by hand (e.g. to backfill a version, or to seed a local server):

```bash
./gradlew :apps:admin:exportConfigManifest
# writes apps/admin/build/config-manifest.json, stamped from versions.properties

curl -X PUT "$SERVER_BASE_URL/v1/admin/config/manifest" \
  -H "X-Admin-Token: $ADMIN_API_TOKEN" \
  -H "Content-Type: application/json" \
  --data @apps/admin/build/config-manifest.json
```

### Keeping the registry honest

The registry is a committed, reviewable file: `apps/admin/config-manifest-registry.json`
(the live `ConfiguredValue` multibinding is Android/iOS-only, so neither this JS
module nor a JVM build task can read it directly). Two guards keep it from drifting:

- `exportConfigManifest` **structurally validates** it (valid types, unique paths,
  each default matches its declared type, enum defaults ∈ allowed values) and fails
  the build on any inconsistency.
- `ConfigManifestRegistryDriftTest` in `:apps:integration` instantiates the **real**
  scalar `ConfiguredValue` classes and compares path, type, default and allowed
  values against the registry. It runs under the ordinary `testDebugUnitTest`
  sweep and prints the JSON line to paste when it fails.

So: when you add, remove, or change a scalar `ConfiguredValue`, update
`config-manifest-registry.json` in the same change.
Composite (`JsonConfigValue`) flags are intentionally omitted.

The `upgrade.*` kill switches were seeded by the V4 migration before any client
read them; since 0.2 they are ordinary `ConfiguredValue`s in `features/upgrade`
and the drift test holds them like any other row.

## Why this exists / scope

An in-house tool over a hosted flag service: the whole config stack (client
`ConfiguredValue`s, server targeting, this console) stays in one repo with no
external dependency or per-seat pricing. Auth is a single shared admin token
per environment (no per-user login/roles yet), SSO/RBAC is a possible future
step. localStorage tokens are readable by JS on the console's origin;
acceptable here because the page loads no third-party scripts and Compose HTML
escapes all text nodes.
