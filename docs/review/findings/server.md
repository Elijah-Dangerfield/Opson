# Findings: server (round two)

`apps/server` outside `data/` and `db/`: all 108 ledger files, every main
source file, config and script read in full, the tests read for sleeps,
ignored tests and what they actually pin. The unslop scanner flagged four
cosmetic hits, all false positives (emoji inside the comments that document
the emoji ranges `DisplayNameRules` refuses, and "Create or replace" KDoc).
Each finding names the place, the rule from [PRACTICES.md](../PRACTICES.md),
what breaks, and its fate. Findings 1, 3 and 4 came routed from the
cross-cutting audits.

Round one's fixes still hold: the ban gate's cache, the body cap, the URL and
path redaction, the SQL-free error bodies and the database timeouts all have
their tests, and the full suite (505 tests) is green.

## 1. App config answered a stale or foreign token with a 401 (fixed)

- **Where:** `Application.kt:160` (before), `routes/AppConfigRoutes.kt`,
  `plugins/Authentication.kt`.
- **Rule:** §9 the server and the client agree on the contract (auth header);
  §9 a wrong environment fails loudly, not as a 401 on a phone.
- **Failure:** `GET /v1/app-config` sat under
  `authenticate(SUPABASE_JWT_AUTH, optional = true)`. Ktor's optional mode lets
  through only a request with no credentials; an expired token, one signed by
  another Supabase project or a banned user's ran the challenge. The client
  attaches whatever token it holds, without a refresh. So 0.1.0 and the first
  0.2 TestFlight build (signed in to dev Supabase, calling prod) got a 401 on
  every fetch and never saw the kill switch, maintenance mode or the version
  floor, and any phone whose token expired while it slept got one on a warm
  foreground. Confirmed by running the new test against the old wiring: five
  of its seven cases failed.
- **Fix:** `fix(server): app config answers a stale or foreign token with the
  anonymous tree, not a 401`. The route is out of `authenticate`. A
  route-scoped plugin, `identifyCallerIfSignedIn`, verifies the bearer with
  `BearerVerifier`, which the auth provider now uses too (one instance, one
  JWKS cache), and sets the principal only when it verifies, so per-user
  targeting and install sightings keep working. Anything else is served
  anonymously. `AppConfigRoutesTest` covers no token, valid, expired, foreign,
  malformed and the production JWKS path with real ES256 keys (good, forged,
  wrong issuer) for both app config and a protected route.
- **Changes:** a banned user now gets their own tree from app config instead
  of a 403. Deliberate: the ban still stops every route that touches their
  data, and the kill switch has to reach them too.
- **Deferred (cross-slice):** the client half the audit proposed, see the
  list at the end. With this server deployed it only matters against an
  older server.

## 2. A JWKS entry of the wrong key type was a 500 (fixed)

- **Where:** `plugins/Authentication.kt`, `jwksVerifier`.
- **Rule:** §2 errors at the edges; §6 app config must answer when the
  database or the keys are trouble.
- **Failure:** found while fixing 1. Reading the key and choosing the
  algorithm sat outside the `try`, so a JWKS entry whose `alg` didn't match
  its key type threw `ClassCastException` out of app config (500) and out of
  the auth provider.
- **Fix:** `fix(server): a JWKS entry of the wrong key type answers
  anonymously, not a 500`. No verifier, as for an unknown key or an
  unreachable JWKS. The test covers both (it fails without the catch).

## 3. Prod is missing the Apple key and the Anthropic key (deferred to the owner, partly fixed)

- **Where:** `config/ServerConfig.kt` (`AppleSignInConfig.fromEnv`),
  `fly.prod.toml`.
- **Rule:** §9 server env vars set on dev and prod, or absent with a logged
  reason; secrets the pipelines need exist.
- **Failure (from the audit):** `opson-server-prod` has no `APPLE_TEAM_ID`,
  `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`, so `POST /v1/me/apple-authorization`
  answers 503 and `DELETE /v1/me` skips revoking the Sign in with Apple token,
  which App Review requires. `ANTHROPIC_API_KEY` is unset on both apps, so
  `/v1/describe/photo` answers 503 the moment `release.photo` is on.
  `fly.prod.toml` listed only the database and Supabase secrets as needed.
- **Fixed in this slice:** `fly.prod.toml` now lists every secret by what it
  is needed for, `AppleSignInConfig`'s KDoc says plainly that a release
  offering Sign in with Apple needs it, and the boot log names every feature
  on or off (finding 4), so `appleRevoke=off` shows in `fly logs`.
- **Deferred:** setting the secrets is a prod change the reviewer may not
  make; the commands are in `docs/plans/0.2/release-checklist.md:429`. The
  release preflight is in another slice's file (list at the end).

## 4. Boot said nothing about most optional features, and LOG_LEVEL did nothing (fixed)

- **Where:** `Application.kt` (`module`), `resources/logback.xml:45`,
  `.env.example`, `README.md`, `fly.prod.toml`.
- **Rule:** §9 the server logs which optional features are off at boot; every
  env var it reads is documented.
- **Failure:** boot warned about a missing `SENTRY_DSN`, `DATABASE_URL` or
  `SUPABASE_URL` and nothing else: no line when the admin routes were
  unmounted (as on dev), when telemetry went to stdout for want of an OTLP
  endpoint, or when Apple revocation was off. `LOG_LEVEL` was set in both Fly
  files and `.env.example` but `logback.xml` hardcoded `INFO`. The README's
  table and `.env.example` left out `DATABASE_POOL_TIMEOUT_MS`,
  `DATABASE_STATEMENT_TIMEOUT_MS`, `OTEL_SERVICE_VERSION`,
  `OTEL_DEPLOYMENT_ENVIRONMENT`, and the README also every Sentry, OTel,
  admin, search-cache and `LOG_LEVEL` variable; its "Roadmap" still called
  observability and the Dockerfile unbuilt.
- **Fix:** `fix(server): log which optional features are on at boot, and
  document every env var` (`ServerConfig.featureSummary`, one
  `Features (<env>): database=on auth=on accountDeletion=… appleRevoke=…
  sentry=… otlp=… admin=… configWebhook=… photo=… nutritionix=… offSearch=…`
  line; `ServerConfigTest` proves each variable flips its feature and no value
  is printed) and `fix(server): LOG_LEVEL sets the root log level`
  (`${LOG_LEVEL:-INFO}`; `LogbackConfigTest` loads the shipped file in its own
  context and checks the level follows, defaults to INFO, and that `Exposed`
  stays at ERROR).
- **Deferred (owner):** setting `SENTRY_DSN`, `OTEL_EXPORTER_OTLP_ENDPOINT`
  and `OTEL_EXPORTER_OTLP_HEADERS` on both Fly apps is a prod change.

## 5. A malformed DATABASE_URL put its password in the boot error (fixed)

- **Where:** `config/ServerConfig.kt`, `ParsedPostgresUrl.parse`.
- **Rule:** §5 secrets never in logs.
- **Failure:** the scheme check quoted `raw.take(20)`. A value without a
  scheme or with another one (`mysql://u:hunter2@…`, `u:hunter2@host/db`)
  has the password inside those 20 characters, and the boot exception reaches
  stdout, Loki and Sentry (installed first). `URLDecoder`'s message for a bad
  `%` escape quotes part of the password too.
- **Fix:** `fix(server): a malformed DATABASE_URL fails boot without quoting
  its password`. Only a plausible scheme is named; a bad escape gets its own
  message. `ParsedPostgresUrlTest.aRejectedUrlIsNeverQuoted`.

## 6. The local docker-compose database failed its migrations (fixed)

- **Where:** `docker/init-auth.sql` (deleted), `docker-compose.yml`.
- **Rule:** §1 stale comments are bugs; §10 fresh setup works.
- **Failure:** the compose stub "mirrors src/test/resources/init-auth.sql"
  but lacked `email`, `created_at` and `last_sign_in_at`, which V22's support
  views select, so a fresh compose database stopped at V22 and
  `:apps:server:run` against it never booted.
- **Fix:** `fix(server): the local docker-compose database migrates past V22
  again`. Compose mounts the tests' stub, so there is one file and it is the
  one every database test migrates against. The comment says the init script
  only runs on a fresh volume.

## 7. Comments that no longer matched the code (fixed)

`docs(server): fix comments that no longer match the code`: `MeRoutes` named
V21 for the Apple token table (V23), left V22 and V24 out of the cascade
list, and promised "a future sweep" for orphan profiles that the cascade
already makes impossible; the photo route's error list left out
`invalid_body` (and caught `SerializationException` separately from the
`IllegalArgumentException` it is); `Application`, `SupabaseConfig` and the
boot warning said only `/v1/me` goes without `SUPABASE_URL`; the Apple config
KDoc read as though setting the keys refused codes; `.env.example` mentioned
the anonymous-user sweep round one deleted.

## 8. Three copies of the platform wire names, two error envelopes (fixed, one copy deferred cross-slice)

`refactor(server): one platform wire name and one problem envelope`.
`InstallSightings`, `BarcodeMissRoutes` and the admin resolve preview each
had their own `when` from `ClientContext.Platform` to `"android"`/`"ios"`/
`"other"` and back; the enum now carries `wire` and `fromWire`.
`ConfigAdminRoutes` built its errors as a hand-made map of `problem()`'s
shape. The fourth copy is in `data/AppConfigTargetingEngine.kt` (another
slice): see the list.

## 9. Smaller things

- `fly.toml` and `fly.prod.toml` passed `-XX:+UnlockExperimentalVMOptions`
  with no experimental option after it. **Fixed:** `chore(server): drop an
  experimental-options unlock no flag uses`.
- `withSpan` is called only by tests. **Not a problem:** it is the one
  manual-span helper, `TelemetryTest` drives the baggage processor through it,
  and its KDoc records the trace-parenting trap for whoever adds the first
  manual span.
- `provideServerCoroutineScope` is never cancelled. **Not a problem:** it is
  the process-lifetime scope (the server's `AppCoroutineScope`) for the
  fire-and-forget config webhook, and it dies with the JVM.
- `GET /v1/admin/config/audit?limit=` is not capped at the route. **Not a
  problem:** `PostgresAppConfigAdminRepository.listAudit` coerces it to
  `1..MAX_AUDIT_LIMIT`.
- `UserAdminRoutesTest` polls with `delay(25)` under a 10 s `withTimeout`.
  **Not a problem:** that is the `awaitUntil` pattern §7 allows; the sighting
  is written off the request on purpose.
- Round one's deferred items (`/v1/example` public in prod, the photo limiter
  never forgetting a user, the barcode uniqueness clash, the display-name
  squat) are unchanged and still deferred for the reasons given there.

## Deferred (cross-slice)

1. `libraries/config/impl/src/commonMain/kotlin/com/opson/libraries/config/impl/data/RemoteConfigRemoteDataSource.kt`,
   `getConfig`: when the call fails with a `ClientRequestException` whose
   status is 401 or 403 and `token != null`, repeat the same `unauthedCall`
   once without the `Authorization` header. Only needed against a server that
   predates finding 1; cheap insurance for the next environment mix-up.
2. `.github/workflows/release.yml`, the build-number job: a preflight step
   with `FLY_API_TOKEN_PROD` that fails the release when prod lacks a secret:
   `need='APPLE_TEAM_ID APPLE_KEY_ID APPLE_PRIVATE_KEY ANTHROPIC_API_KEY SUPABASE_URL SUPABASE_SERVICE_ROLE_KEY DATABASE_URL SENTRY_DSN'; have=$(flyctl secrets list -a opson-server-prod --json | jq -r '.[].Name'); for n in $need; do echo "$have" | grep -qx "$n" || { echo "::error::opson-server-prod is missing $n"; miss=1; }; done; [ -z "$miss" ]`.
   Drop `ANTHROPIC_API_KEY` from `need` while `release.photo` stays off for
   0.2 (see `docs/plans/0.2/release-checklist.md`).
3. `apps/server/src/main/kotlin/com/opson/server/data/AppConfigTargetingEngine.kt:53`
   and `:110-114` (server-data): delete the private `Platform.wireName()` and
   use `context.platform.wire`.
4. Owner, on Fly (prod and dev): set `APPLE_TEAM_ID`, `APPLE_KEY_ID`,
   `APPLE_PRIVATE_KEY` (release-checklist.md:429), `SENTRY_DSN`,
   `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_EXPORTER_OTLP_HEADERS`, and
   `ANTHROPIC_API_KEY` before `release.photo` is turned on; `ADMIN_API_TOKEN`
   on dev if the console should work there. Then check the boot log's
   `Features` line.

## Product notes

- **Let the release check ask the server, not the secret list.** A
  token-gated `GET /v1/admin/features` returning the same map as the boot
  line would let the release preflight and the admin console see what the
  running prod server can actually do (a secret can be set but malformed;
  `AppleSignInConfig` needs all three). Fairly sure it is worth it.
- **Banned people still get app config.** After finding 1 a banned user sees
  their own flags, maintenance and version floor; the block screen comes from
  the first data call. That is what you want for a kill switch, but anyone
  using a per-user rule to "hide" something from a banned account should
  know. Sure.
- **The 0.1.0 binary will start getting prod config once this deploys.**
  Rules targeted by build number or app version now reach it for the first
  time; check that no rule meant for 0.2 matches 0.1 by accident before
  deploying. Fairly sure this is worth a look.

## Second review

A second reviewer flagged four possible regressions in this branch. All four
held up against the code; each fate is below.

1. **App config answered an unparseable `Authorization` header with a 400
   (fixed).** `identifyCallerIfSignedIn` called
   `request.parseAuthorizationHeader()`, which turns a Ktor `ParseException`
   into `BadRequestException`, so `Bearer a b`, `Bearer a,b`, `Bearer "x`
   and `Bearer =x` got a 400 from `/v1/app-config` (with or without
   StatusPages). On main the JWT provider used the non-throwing parser and
   those requests got the anonymous tree. The reviewer's suggested fix
   (catching `IllegalArgumentException`) would not have worked, since
   `BadRequestException` is not one; the route now reads the raw header and
   parses it with `ktor-http`'s parser, treating a `ParseException` as no
   token. `fix(server): app config serves an unparseable Authorization header
   anonymously, not a 400`; `AppConfigRoutesTest.aMalformedHeader_…` now
   covers the four headers and runs with StatusPages installed, and failed
   with a 400 before the fix.
2. **The banned-user change on app config had no test (fixed).** Finding 1
   moved a banned user from a 403 to their own tree, deliberately, but nothing
   pinned it. `test(server): pin that a banned user gets app config while data
   routes answer 403` installs auth with a `BanGate` that bans everyone and
   one shared `BearerVerifier`, as `installApp` does, and checks app config
   answers 200 with the user's tree while a protected route answers 403 with
   the access-denied body. It fails (403) with main's
   `authenticate(optional = true)` wiring.
3. **The bad-escape row in `aRejectedUrlIsNeverQuoted` proved nothing
   (fixed).** `URLDecoder` quotes only the two characters after the `%`, so
   `hunter2%zz` passed without the fix. `test(server): the bad-escape case
   actually catches a quoted password` replaces the row with
   `aMalformedEscapeIsNotQuoted`, which puts the password in the escape
   (`%hunter2`) and checks the exact message; without the catch it fails on
   `Error at index 0 in: "hu"`.
4. **The route lists for a missing `SUPABASE_URL` were incomplete (fixed).**
   The `Application.kt` KDoc and boot warning left out barcode misses, and
   `.env.example` left out photo and barcode misses. `docs(server): name every
   route that SUPABASE_URL gates`.
