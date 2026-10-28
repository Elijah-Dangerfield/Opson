# Findings: server-data

`apps/server` data, db and migrations: all 90 ledger files, plus the new
`WebhookConfigChangeNotifierTest`. Every main source file and migration was read
in full; the tests were read where a fix touched them and otherwise scanned
for sleeps, ignored tests and dead helpers (the one `delay` races a real
timeout on purpose). The unslop scanner found only long test names, which is
this repo's style. Each finding names the place, the rule from
[PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

Round one's fixes in this slice still hold: the statement and pool timeouts
(`Database.kt`), `ON CONFLICT DO NOTHING` for first contact, the app config
snapshot kept through a busy database, the barcode read of `off_foods`, the
Open Food Facts merge, RLS on every table and the EXPLAIN tests. Its deferred
items here are still open and not re-reported: live Open Food Facts answers
cached into `foods` (14), one upsert per sync mutation (20), describe's
sequential searches (21), the `foods.barcode` versus `(source, source_id)`
conflict and the default display name collision (22).

## 1. A 0.1 phone's day write erased the check-in a 0.2 phone saved (audit: contract): fixed

- **Where:** `data/PostgresSyncRepository.kt:219` (before), the `DayRecord`
  upsert writing `it[checkIn] = row.checkIn`.
- **Rule:** §9 "what 0.1 clients still send"; brief, "Don't break 0.1 clients".
- **Failure:** confirmed against b74c277a: 0.1's `DayRecordRowDto` has no
  `checkIn`, so the server decodes null. A 0.2 phone closes the day with a
  check-in; a 0.1 phone on the same account adds water, its write is newer
  and wins, and the update sets `check_in = NULL`. The 0.2 phone pulls the
  row and loses the check-in. V18's header ("Older clients never send it, so
  it stays null for them") missed the update case.
- **Why not the audit's exact fix:** the 0.2 client encodes with
  `explicitNulls = false` (`SyncWire.kt:219`), so clearing a check-in on 0.2
  also sends no `checkIn` key. Keying on `containsKey("checkIn")` would make a
  cleared check-in come back on the next pull. What tells the two apart is the
  request: 0.2 always sends `accepts` (`SYNC_ACCEPTS`, added in 1dd7c75c,
  before check-ins in c430fdd3) and 0.1 never does.
- **Fix:** `fix(server): a 0.1 phone's day write keeps the check-in a 0.2
  phone saved`. A request without `accepts` updates a day record with
  `onUpdateExclude = check_in`; inserts are unchanged. The class KDoc now says
  a column added to a synced table later needs the same care.
  `PostgresSyncRepositoryTest.aV01WaterChange_keepsTheCheckInA02PhoneSaved`
  fails without the fix; `a02PhoneCanClearItsCheckIn` proves 0.2 can still
  clear one. Entirely in this slice, through `pullFasts || pullExercises`.
- **Deferred (cross-slice), test-apps:** the journey test. See the list at
  the end.
- **Deferred (cross-slice), server:** the repository infers "0.2 client" from
  `pullFasts || pullExercises`. Clearer would be an explicit parameter on
  `SyncRepository.sync` in `domain/Diary.kt` (say `clientKnowsCheckIn`, set
  in `SyncRoutes.kt` from `request.accepts.isNotEmpty()`). Optional; the
  behaviour is right either way.

## 2. A 0.1 edit turns a "From label" entry into an estimate: deferred

- **Where:** `data/PostgresSyncRepository.kt`, `upsertEntry`.
- **Rule:** §9, same class as 1.
- **Failure:** V21 added precision `label`. 0.1 reads it as `estimate`
  (`Precision.fromWire` falls back), so editing that entry on a 0.1 phone
  pushes `estimate` and the 0.2 phone's "From label" becomes "Estimate". The
  numbers are untouched; only the badge is lost.
- **Why deferred:** the fix is
  `precision = CASE WHEN log_entries.precision = 'label' AND EXCLUDED.precision = 'estimate' THEN 'label' ELSE EXCLUDED.precision END`
  for requests without `accepts`, but Exposed's `upsert` takes a custom
  update only as the whole column list, so it means restating every column of
  the entry upsert. Low impact and a short window (two phones on mixed
  versions). `TODO(review)` above `upsertEntry`.

## 3. A country header with a non-ASCII letter failed search with a 500: fixed

- **Where:** `data/OpenFoodFactsSearch.kt:232`, `countryTag`.
- **Rule:** §6 "Every input is validated at the route".
- **Failure:** `X-Country-Code` is only checked for length
  (`http/ClientContext.kt:67`), headers decode as Latin-1, and `countryTag`
  accepted any letter. `Locale.Builder().setRegion("ÉÉ")` throws
  `IllformedLocaleException`, so with `FOOD_SEARCH_OFF_ENABLED` on, a search
  with `X-Country-Code: éé` answered 500. Barcode misses were already safe
  (`BarcodeMiss.of` checks A to Z).
- **Fix:** `fix(server): a country header with a non-ASCII letter no longer
  fails search`. Only A to Z; `OpenFoodFactsSearchTest.aHeaderWithNonAsciiLettersIsNoCountry`.

## 4. Supabase admin calls swallowed cancellation: fixed

- **Where:** `data/HttpSupabaseAdminClient.kt:91` and `:117` (before),
  `catch (e: Throwable)` around the request.
- **Rule:** §2 "Cancellation is cooperative and never swallowed".
- **Failure:** a cancelled `DELETE /v1/me` or display name change came back
  as `Failure(null, CancellationException)`, and the route went on to answer
  as if Supabase had failed.
- **Fix:** `fix(server): Supabase admin calls let cancellation through`,
  matching the Apple and Open Food Facts clients.
  `HttpSupabaseAdminClientTest.cancellationIsNotAFailure`.

## 5. The config webhook could log its own secret, and missed a revoked hook: fixed

- **Where:** `data/WebhookConfigChangeNotifier.kt:46` (before).
- **Rule:** §5 "Secrets never in code, logs, URLs or commits"; §9 a skipped
  step says so.
- **Failure:** the failure line logged `it.message`. A Slack webhook URL is
  its own credential, and `URI.create` quotes the whole URL when it refuses
  one, which a secret pasted with a trailing newline is enough to cause. And
  only exceptions were logged, so a revoked webhook's 404 passed in silence.
- **Fix:** `fix(server): the config webhook logs a failure without its URL,
  and logs 4xx`. The line carries the exception class; a non-2xx status has
  its own warning. `WebhookConfigChangeNotifierTest` (new).

## 6. The export held a pooled connection while a client read it: fixed

- **Where:** `data/PostgresExportRepository.kt:38` (before): one
  `database.transaction` around every `sink` call, and the sink writes the
  response (`routes/ExportRoutes.kt:46`).
- **Rule:** §6 "Transactions are short and never span a network call".
- **Failure:** a client that reads slowly backs the writer up, and the
  transaction stays open with no statement timeout to end it (it is idle,
  not running). Round one noted the hold and rate limited the route to 10 an
  hour per IP, but ten slow exports still empty the 10-connection pool, and
  every other request then waits 3 s and answers 503.
- **Fix:** `fix(server): the export reads in short pages, never holding a
  connection while it writes`. Pages of 500, each in its own transaction,
  keyed by the sort order (`(day, logged_at, id)` for entries) rather than an
  offset, with `day >=` so the `(user_id, day)` index bounds a late page.
  `PostgresExportRepositoryTest` exports two and a half pages with shared
  days and times, checks order and completeness, and asserts no transaction
  is open in the sink (it fails on the old code). `QueryPlanTest` covers a
  later page's plan. The file is no longer one snapshot; the KDoc says so.
- **Deferred (cross-slice), server:** the comment above `EXPORT_LIMIT` in
  `plugins/RateLimits.kt:95` now overstates the cost, and the
  `ExportRepository` KDoc in `domain/Export.kt:26-28` still promises one read
  transaction around every sink call. See the list at the end.

## 7. Comments that no longer matched the code: fixed where they can be

- `db/Tables.kt:319` named V21 for `apple_refresh_tokens`, which V23 creates.
- `AnthropicEstimateProvider`'s KDoc promised token counts in a log line that
  logs the outcome, the count and the latency.
- `Tables.kt` says `DatabaseSchemaTest` checks every projection, but the test
  left out the four app config tables. It reads them now.
- Fixed in `docs(server): comments that no longer matched the code in the
  data layer`.
- **Not fixable:** V23's own header says "V21", and V18's says older clients
  leave `check_in` null (see 1). Applied migrations can't change (Flyway's
  checksum fails boot), like V15's "V13" in round one.

## 8. Smaller things

- The waitlist CSV export has the same streaming transaction as 6. **Not a
  problem:** behind the admin token, one caller, a list of emails.
- `PostgresAppConfigAdminRepository.upsertFlag` is update-then-insert, so two
  admins creating the same new flag at once get a 500 from the primary key.
  **Not a problem:** one admin, and the second try succeeds.
- `PostgresAppConfigManifestRepository.listVersions` reads every manifest row
  to count flags per version, growing by a build's flag count each release.
  **Not a problem** yet: admin only, a few thousand rows; a `GROUP BY` is the
  fix when it matters.

## Deferred cross-slice changes

1. **test-apps**, new `apps/integration/src/androidUnitTest/kotlin/com/opson/integration/sync/DayRecordAcrossVersionsTest.kt`,
   test `aV01WaterChange_keepsTheCheckInA02PhoneSaved`. A `V01Wire(delegate: DiarySyncApi)`
   in the style of `AppearanceAcrossVersionsTest.PaletteWire` that sends
   `request.copy(accepts = emptyList(), mutations = …)` with `"checkIn"` removed
   from every `day_record` row (both matter: the server keys off `accepts`).
   The new phone sets a check-in on a day and syncs; the old phone
   (`client(user, wrapSync = ::V01Wire)`) syncs, changes that day's water and
   syncs; the new phone syncs. Assert
   `SELECT check_in FROM day_records WHERE user_id = ?::uuid` still holds the
   check-in and the new water, and that the new phone's day still shows it.
2. **server**, `apps/server/src/main/kotlin/com/opson/server/plugins/RateLimits.kt:95-96`:
   replace "An export streams someone's whole history and holds a pooled
   connection while it does." with "An export reads someone's whole history,
   a page at a time."
3. **server** (optional), make 1's signal explicit: a `clientKnowsCheckIn`
   parameter on `SyncRepository.sync` (`domain/Diary.kt:238`), passed from
   `SyncRoutes.kt` as `request.accepts.isNotEmpty()`, replacing
   `pullFasts || pullExercises` in `PostgresSyncRepository.sync`.
4. **server**, `apps/server/src/main/kotlin/com/opson/server/domain/Export.kt:26-28`:
   replace "[sink] is called once per row inside one read transaction, so the
   export never holds the whole history in memory." with "Rows are read a page
   at a time and [sink] runs between pages, outside any transaction, so the
   export never holds the whole history in memory or a connection while it
   writes." Stale since 6.

## Second review

1. **The `ExportRepository` KDoc still describes one read transaction
   (5705659a): real, recorded.** `domain/Export.kt:26-28` says the sink is
   called "inside one read transaction". After 6 the sink runs between pages,
   outside any transaction, and the export is no longer one snapshot.
   `domain/Export.kt` is in the server slice, so it isn't edited here. Added
   as item 4 under "Deferred cross-slice changes" and named under 6. No test:
   it is a comment.

## Product notes

- **Mixed versions on one account lose small things.** Today it was a
  check-in (fixed) and a "From label" badge (2). Each new field on a synced
  row repeats the question. A server-side minimum version for sync once 0.2
  is out a few weeks would close the class. Fairly sure it is worth doing.
- **The export still leaves out most of what people write** (round one's
  note): check-ins, feelings, notes, fasts, exercises, custom foods. Sure.

## Deferred cross-slice items: fates (2026-10-28)

Applied on branch `worktree-agent-a89d6bb1fb957d17a` once every slice was on main.

1. Applied in 7e3358f7. `DayRecordAcrossVersionsTest` was on main, ignored and
   on a wire that kept `accepts`; it now drops `accepts` and `checkIn` as 0.1
   does, closes and reopens the day to save the check-in (`setCheckIn` is
   gone), and checks the stored row and the new phone.
2. Already done on main.
3. Applied in 17d6ee5e.
4. Already done on main.
