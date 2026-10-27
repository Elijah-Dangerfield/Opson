# Findings: server

`apps/server`, all 174 ledger files. Every main source file and migration was
read in full; the tests were read where a fix touched them and otherwise
scanned for sleeps, ignored tests and dead helpers. Each finding names the
place, the rule from [PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

New files: `V19__rls_on_early_tables.sql`, `plugins/BodyLimit.kt`, and the
tests `DatabaseTimeoutTest`, `FullStackFoodLatencyTest`, `QueryPlanTest`,
`StatusPagesTest`, `BanGateTest`, `BodyLimitTest`, `PostgresExportRepositoryTest`.

## 1. A saturated database made phones wait 17 to 27 s — fixed

- **Where:** `db/Database.kt` (before: no statement timeout, Hikari's default
  30 s `connectionTimeout`, Exposed's default `maxAttempts = 3`),
  `plugins/Errors.kt`.
- **Rule:** §6 "external calls have timeouts, bounded retries"; §3 error states.
- **What the lead asked:** with `FOOD_SEARCH_OFF_ENABLED` off, neither search
  nor detail reads `off_foods`. Search runs `SEARCH_SQL` over `foods` and
  inserts one `food_search_events` row; `findById` reads `foods` and only falls
  back to `off_foods` when the flag is on. The importer fills
  `off_foods_next` and takes a lock on `off_foods` only for the rename swap,
  so it never locks what these routes read. (The coordinator later traced the
  27 s searches to a Postgres restart mid-import.)
- **Failure:** whatever the cause, nothing bounded the wait. A statement could
  run until Supabase's role timeout, a request waited 30 s for a pool
  connection, and Exposed reran any transaction that threw an `SQLException`
  three times, so one stall cost up to three times its length. The phone sat
  until its own 30 s timeout. The blanket retry also replayed non-idempotent
  work: an export or waitlist CSV that failed mid-stream would have written its
  rows again.
- **Fix:** `fix(server): a saturated database answers 503 busy in seconds, not
  27 s`. Every pooled connection sets `statement_timeout` (5 s,
  `DATABASE_STATEMENT_TIMEOUT_MS`), the pool waits 3 s
  (`DATABASE_POOL_TIMEOUT_MS`), Exposed no longer retries, and `Database`
  retries only serialization failures and deadlocks. A timeout becomes
  `DatabaseBusyException`, answered 503 `busy` with `Retry-After: 2`.
  Migrations run on their own connection without the timeout. Hikari needed
  `isIsolateInternalQueries` or it rolled the `SET` back (autocommit off).
  `FullStackFoodLatencyTest` locks `off_foods` (search and detail stay fast)
  and then `foods` (both answer 503 within the timeout);
  `DatabaseTimeoutTest` covers the statement timeout, the pool wait, the
  conflict retry and migrations under a 1 ms timeout.

## 2. SQL text and diary values in error bodies and logs — fixed

- **Where:** `plugins/Errors.kt:62` (before), `resources/logback.xml`.
- **Rule:** §5 "Errors carry context, not payloads"; never log food names,
  notes, nutrition.
- **Failure:** a 500 answered `cause.message`. For a database error that
  message, `ExposedSQLException.toString()`, and Exposed's own WARN on every
  failed transaction ("Statement(s): INSERT ... VALUES ('my note', ...)")
  quote the statement with its bound values. Those went to the client, Loki
  and Sentry.
- **Fix:** `fix(server): keep SQL text and row values out of error bodies and
  logs`. A fixed 500 message, database errors logged and sent to Sentry as
  their SQLSTATE plus stack, the `Exposed` logger held to ERROR.

## 3. Every authenticated request read `auth.users` first, and swallowed cancellation — fixed

- **Where:** `plugins/Authentication.kt:113` (before), `plugins/AccessControl.kt`.
- **Rule:** §6 hot paths; §2 "Cancellation is never swallowed".
- **Failure:** the ban gate ran its own transaction before every route, so a
  slow database cost each request twice the pool. Its `runCatching` also
  caught `CancellationException` and logged it as a failed lookup.
- **Fix:** `fix(server): read a user's ban standing once per 30 s, and let
  cancellation through`. `BanGate` caches standing per user for 30 s (a ban
  takes up to 30 s to bite; a cached ban lapses on its `until`).

## 4. Search text in traces; food ids and barcodes in request logs — fixed

- **Where:** `plugins/Tracing.kt`, `plugins/Telemetry.kt`, `plugins/Observability.kt`.
- **Rule:** §5 never log search text or what someone eats; `FoodRoutes`'
  KDoc promised "none logs a query".
- **Failure:** the OTel Ktor instrumentation records `url.query` on every
  server span, so `q=` reached Tempo under the session and install ids.
  `url.path` and the CallLogging line carried every food id opened and
  barcode scanned, a per-install shopping list for 31 days (V13 keeps even
  barcode misses unlinkable for this reason).
- **Fix:** `fix(server): no search text, food ids or barcodes in traces and
  request logs`. `UrlRedactingSpanProcessor` and a CallLogging format with
  `redactedPath`.

## 5. One unstorable row blocked a phone's sync for good — fixed

- **Where:** `domain/DiaryRules.kt`.
- **Rule:** §3 offline first, §6 validate at the route.
- **Failure:** Postgres refuses a NUL in text and `\u0000` in jsonb. A note,
  name or settings value carrying one failed the whole `/v1/sync`
  transaction with a 500, and the outbox sends it first every time.
- **Fix:** `fix(server): refuse a synced row Postgres can't store instead of
  failing the batch`. The row is rejected alone as `invalid_row`; the rest
  applies (`FullStackSyncTest`). The client rolls a rejected row back, so the
  pasted note is lost rather than every later write; stripping the NUL
  instead would keep it, but needs a JSON-aware strip for settings and items.

## 6. No request body cap — fixed

- **Where:** every `call.receive` but the photo and waitlist routes.
- **Rule:** §6 "sizes are capped".
- **Failure:** `/v1/sync` read any body into memory; one authenticated client
  could exhaust the 512 MB dev machine.
- **Fix:** `fix(server): cap request bodies at 16 MB` (413
  `request_too_large`, by `Content-Length` and by stopping a chunked read).
  The theoretical largest legal batch (500 mutations of 64 KB JSON) is 32 MB;
  real batches are a few hundred KB.

## 7. Search, describe and export had only the global limit — fixed

- **Where:** `plugins/RateLimits.kt`, `routes/FoodRoutes.kt`, `routes/ExportRoutes.kt`.
- **Rule:** §6 rate limits on anything expensive.
- **Failure:** 600 a minute per IP let one client keep the pool busy with
  searches; a description is up to 20 searches; an export holds a connection
  while it streams.
- **Fix:** `fix(server): per-IP limits on search, describe and export` (120
  and 30 a minute, 10 an hour). Waitlist, photo, barcode lookup and barcode
  misses already had limits: not a problem.

## 8. AuthZ coverage — tested

- **Rule:** §6 "a test proves user A can't read or overwrite user B's rows".
- **Finding:** the upsert guard filters on `user_id`, but only log entries had
  a test. `test(server): prove a user can't overwrite another's weigh-ins,
  custom foods or saved meals, or export them`. Behaviour was already right.

## 9. Open Food Facts listed one product several times — fixed

- **Where:** `data/OpenFoodFactsSearch.kt` `merge`.
- **Rule:** §1 code that reads true; coordinator lead from dev ("nutella").
- **Failure:** the export has a row per barcode (jar size, country,
  multipack) with one label, and Branded Foods has its own copies.
- **Fix:** `fix(server): one row per product when search merges Open Food
  Facts`. The merge keeps the first row per name and per-100 g numbers (the
  catalog's best ranked, Open Food Facts' most scanned); each side fetches up
  to twice the page so the page stays full.

## 10. A named brand's Branded rows pushed Open Food Facts off the first screen — fixed

- **Where:** same `merge`.
- **Was it intended?** Partly. The rule "an Open Food Facts row never passes a
  generic food or a catalog row of the same or better match tier" is
  deliberate (verified label data first). But the merge placed each Open Food
  Facts row after the *last* row that outranks it. When a query names a brand
  the catalog lists that brand's Branded rows above any generic food, so one
  generic match at the bottom pulled every Open Food Facts row below all the
  Branded rows.
- **Fix:** same commit. A row now goes above the first catalog row it
  outranks, walking from the top. For queries that don't name a brand the
  order is the same as before. Requests without Open Food Facts (flag off, a
  category, `source=usda`) still run the 0.1 statement unchanged. If the first
  screen is still all Branded on dev, it is because USDA lists many rows
  named exactly "Nutella" with differing labels, which outrank a community
  row by design; see Product notes.

## 11. Comments that no longer matched the code — fixed

`docs(server): fix comments that no longer match the code`: an orphaned
KDoc in `PostgresFoodCatalogRepository`, "V13" for an index V15 creates, a
`products` table in `JsonbColumn`, per-table deletes in `DELETE /v1/me` that
the `ON DELETE CASCADE` keys already do, and an export that claimed to hold
"everything". V15's own header says "V13"; applied migrations can't be
edited (checksum), so that one stays: **not fixable**.

## 12. Six tables without row level security — fixed

- **Where:** V1, V3, V4, V5, V6 (`profiles`, `player_reports`,
  `app_config_values`, `app_config_rules`, `app_config_audit`,
  `app_config_manifest`).
- **Rule:** review checklist "Row level security is on for every table".
- **Failure:** on Supabase the postgres role's public tables are granted to
  `anon` and `authenticated` by default. With RLS off, the app's anon key
  could read every display name through PostgREST and write
  `app_config_values`: maintenance mode on, or a minimum version above every
  install, for everyone. Not verified against prod (no access by rule).
- **Fix:** `fix(server): row level security on profiles, player reports and
  app config` (V19, policies none, owner unaffected; no client uses
  PostgREST). `DatabaseSchemaTest` now checks every public table. Left off:
  `flyway_schema_history`, which Flyway locks on another connection during a
  migration, so altering it there deadlocks. **Orchestrator:** if another
  slice also adds a V19, renumber one.

## 13. Barcode scans never read `off_foods` — fixed

- **Where:** `PostgresFoodCatalogRepository.findByBarcode`.
- **Failure:** with 3.3 million products loaded, a code missing from `foods`
  always went to the live API, which limits reads per IP, and every user
  shares the server's.
- **Fix:** `fix(server): a barcode scan reads the Open Food Facts export
  before the live API`. With the flag on, every equivalent form (8, 12, 13,
  14 digits, UPC-E expanded) is looked up in `off_foods` through its unique
  index. The lookup is recorded as `catalog` in `barcode_lookups`.

## 14. Live Open Food Facts answers are cached into `foods` — deferred

- **Where:** `data/LiveFoodSources.kt` (`OpenFoodFactsSource.cacheable = true`),
  `CatalogBarcodeLookup.kt:48`, `upsertExternalProduct`.
- **Finding:** yes, it mixes ODbL rows into our own table: each live answer is
  upserted into `foods` as source `off`, which V12's header and the importer
  avoid for share-alike reasons (V12 calls it "a separate question").
- **Clean option:** stop adding rows, keep the ones already there (log entries
  reference their ids through `food_id`). Smallest change: `cacheable =
  !config.searchOpenFoodFacts`, so with the export loaded a live answer (now
  rare, see 13) is served once with the deterministic `servedOnce` id, as
  Nutritionix is. Better: upsert it into `off_foods` by barcode with
  `source_version = 'live-<date>'`, so detail by id keeps working until the
  next monthly reload replaces it.
- **Why deferred:** both change what 0.1 clients see. An uncached food 404s on
  `GET /v1/foods/{id}`, and an `off_foods` row is only readable with the flag
  on. Needs a call on whether a 404 on detail is acceptable to 0.1.

## 15. Code nothing called — fixed

`refactor(server): delete code nothing calls`: `POST /v1/reports` and its
repository (template trust-and-safety; no client calls it; the table stays), a
WebSocket plugin with no routes and an unbounded frame size, the anonymous
user sweep, an unused locale matcher, an always-1 example gauge.

## 16. EXPLAIN on the hot paths — tested

`test(server): EXPLAIN the hot paths against real Postgres`: sync pull on all
eight synced tables, the export, a barcode in `foods` and `off_foods`, and the
catalog search each use an index (plans taken with `enable_seqscan = off`).
No missing index found.

## 17. Concurrent first `GET /v1/me` calls failed — fixed

- **Where:** `PostgresProfileRepository.findOrCreateResult`.
- **Failure:** it caught the unique violation from a racing insert and re-read
  the profile, but the error had aborted the transaction, so the re-read threw
  `25P02`. Exposed's blanket retries hid this; finding 1 removed them.
- **Fix:** `fix(server): concurrent first GET /v1/me calls no longer 500`
  (`ON CONFLICT DO NOTHING`; the race surfaces as a serialization failure,
  which `Database` retries). The test runs eight first contacts at once.

## 18. App config 503'd for everyone while the database was busy — fixed

`fix(server): app config keeps serving its last snapshot when the database is
busy`. The launch-time config (kill switch, forced upgrade) now serves the
last snapshot for another 30 s instead of waiting out the timeout.

## 19. Control characters in display names — fixed, client copy deferred (cross-slice)

`fix(server): refuse control characters and bidi overrides in display names`.
A NUL was a 500; a newline or a right-to-left override could make one name
read as another. **Deferred (cross-slice):**
`libraries/identity/.../DisplayNameRules.kt` should refuse the same
characters (`Character.isISOControl(c)`, U+202A to U+202E, U+2066 to U+2069) so the client explains it before the round trip.

## 20. Sync push writes one statement per mutation — deferred

- **Where:** `PostgresSyncRepository.sync` (one upsert per mutation, up to 500,
  under the user's advisory lock).
- **Cost:** 500 round trips for a backlog, about 0.5 to 1 s from Fly to
  Supabase. Pull is a fixed eight queries; no N+1 there.
- **Plan:** group by entity, drop all but the newest copy of each key in the
  batch (an `INSERT ... ON CONFLICT` can't touch one row twice), and
  `batchUpsert` with the guard written against `EXCLUDED.client_updated_at`.
  The `written` flag only matters for rows that didn't exist, which always
  insert, so popularity counting survives. Deferred: core write path, and the
  cost is bounded.

## 21. Describe runs up to 20 searches one after another — deferred

`CatalogFoodDescriber.describePhrases`. Each phrase is its own transaction and
search; parallel would take up to 20 of the 10 pooled connections. Now rate
limited (7). A single statement over all phrases would be the fix.

## 22. Smaller things

- `clientIp` trusts `X-Forwarded-For` when `Fly-Client-IP` is absent, so
  off Fly a client can pick its own rate-limit bucket. **Not a problem** on
  Fly, whose proxy sets `Fly-Client-IP`.
- `PhotoEstimateLimiter` never forgets a user (`WaitlistLimiter` sweeps).
  **Deferred**, low: Plus-only, bounded by users.
- `GET /v1/example` is the template's demo route, still public in prod.
  **Deferred**: the README uses it as the limited-mode check; removing it
  means editing the README and `docs/decisions.md`.
- `foods.barcode` is UNIQUE but `upsertExternalProduct` targets
  `(source, source_id)`; a cached Nutritionix row and a live OFF answer with
  the same barcode string would 500. **Deferred**, rare (the catalog is read
  first).
- A user who renames themselves to another's future default name
  (`user-<8 hex>`) blocks that user's profile creation. **Deferred**, low.
- The `IllegalArgumentException` handler echoes the message as a 400. Today
  those are UUID and `require` messages without user data. **Not a problem**.
- Migrations V12 to V18 are additive and idempotent, and 0.1 sees nothing new
  (fasts only with `accepts`, check-in nullable, settings merged with `||`).
  V17's `ADD CONSTRAINT ... CHECK` scanned `foods` under an exclusive lock,
  already applied. **Not a problem.**

## Product notes

- **The export misses most of what people write.** Notes, feelings, check-ins,
  meal feelings, fasts, custom foods, saved meals and settings are not in
  `export.csv`. Someone exporting "my data" will expect them. Sure.
- **"Nutella" may still open on USDA rows.** USDA Branded has many rows named
  exactly "Nutella" with slightly different labels; they outrank a community
  row of the same match by design. Collapsing Branded rows that differ only by
  rounding, or showing one row per brand with sizes inside it, would read
  better. Fairly sure it surprises people.
- **A calm "busy" state.** The server now says 503 `busy` with `Retry-After`;
  the app could show "Search is slow right now" instead of a generic error.
  Medium.
- **Barcode dashboards can't tell the export from our data**: an `off_foods`
  hit is recorded as `catalog`. A distinct outcome needs a migration on the
  `barcode_lookups` CHECK. Medium.
- **A ban takes up to 30 s** to block an already signed-in user. Fine for
  moderation today; worth knowing. Sure.
