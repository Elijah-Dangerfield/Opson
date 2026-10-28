# Findings: test apps (round two)

`apps/integration/`, `apps/leaktest/` and `apps/screenshots/`. Round one's findings are in
[round-1/findings/test-apps.md](../round-1/findings/test-apps.md); its fixes still hold (the
barcode-form journeys, the pinned surface lists, the energy-out control, the flow waits). Each
finding names the place, the rule from [PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

Four of the journeys below fail against today's code. Each is committed with `@Ignore` and a
`TODO(review)` naming the fix, so the suite stays green; **remove the `@Ignore` when the fix
merges** and the test proves it.

## 1. Delete everything never checked the exercises or installs tables: fixed

- **Where:** `helpers/InProcessServer.kt` (`USER_TABLES`, before the fix), `account/DeleteEverythingTest.kt`.
- **Rule:** §7 "delete everything" journey; §1 no hand list that only grows when someone remembers it.
- **Failure:** the list of tables a delete must empty was written by hand and stopped at
  `fasts`. `exercises` (V24) and `user_installs` (V22) hold a user's rows and were never
  checked, so a migration that dropped their `ON DELETE CASCADE` would have kept a deleted
  person's exercise and device history with the test green.
- **Fix:** `test(integration): delete everything checks every table with a user_id, exercises and installs included`.
  The list is read from `information_schema`, the journey logs an exercise, and every table
  must hold a row before the delete (`fasts` and `apple_refresh_tokens` excepted, with why).
  Proved by dropping the exercise from the fill: the test names `exercises`.

## 2. Paging across the wire had no journey: fixed (audit: contract)

- **Where:** `sync/PagingTest.kt` (new).
- **Rule:** §9 a change on one side gets a test that runs both sides.
- **Failure:** the server's 500-per-entity page, `hasMore`, the cursor held at the lowest
  truncated page and the client's `MAX_REQUESTS_PER_SYNC` were unit-tested on each side only.
  A fresh install on a heavy account is exactly that path.
- **Fix:** `test(integration): a fresh install pulls more than one page of two entities at once`.
  1,200 entries and 600 day records, shuffled into the server through `rawSync`, all reach a
  new phone in one `syncNow()`, which must take at least three requests. Proved by switching the
  server's `minOf` to `maxOf`: entries go missing and the test fails on them.

## 3. An update that starts accepting an entity never pulls what came before it: deferred (cross-slice)

- **Where:** `libraries/diary/impl/.../DiarySyncer.kt:237` (the cursor is reused whatever
  `SYNC_ACCEPTS` says), against `PostgresSyncRepository.pull`, which moves the cursor past
  entities the request did not accept.
- **Rule:** §9 the contract, and what older clients send; §10 lifecycle.
- **Failure:** a phone on a build that did not accept `exercise` syncs past another phone's
  exercise rows; after the update its cursor is already beyond them and they never arrive. The
  same holds for fasts on a 0.1 phone that updates to 0.2: fasts logged on the person's other
  phone before the update are never pulled. Reproduced by
  `ExerciseSyncTest.aPhoneThatUpdatesToAcceptExercise_pullsExerciseLoggedBeforeTheUpdate`
  (ignored until the fix).
- **Deferred to `libraries/diary/impl`:** keep the accepted set beside the cursor and pull from
  cursor 0 once when it grows. The smallest change: a settings-store or `SyncStateEntity` field
  `accepts: String?` (a new defaulted column is a Room auto-migration, version 11); in `drain()`,
  if the stored value is not `SYNC_ACCEPTS.joinToString(",")`, start at cursor 0 and store the new
  value with the cursor in `apply`. A full re-pull is safe: local rows with pending changes win.

## 4. A 0.1 save of a day wipes the check-in a 0.2 phone saved: deferred (cross-slice, audit: contract)

- **Where:** `apps/server/.../routes/SyncDto.kt:155` (`checkIn: String? = null`) and
  `PostgresSyncRepository.kt:219` (`it[checkIn] = row.checkIn` on every upsert).
- **Rule:** §9 the server keeps accepting what 0.1 sends; settings learned the same lesson
  (shallow merge).
- **Failure:** 0.1 decodes day records without `checkIn` and sends the whole row back on its
  next change to that day, a glass of water say. The server reads the missing key as null and
  stores it, so the 0.2 phone's check-in is gone on its next pull.
  `DayRecordAcrossVersionsTest.aV01WaterChange_keepsTheCheckInA02PhoneSaved` reproduces it
  (ignored until the fix).
- **Deferred to `apps/server`:** when a `day_record` mutation's row JSON has no `checkIn` key,
  leave the stored column alone: carry a `checkInPresent` flag from `decodeRow` (the raw
  `JsonObject` is in hand in `SyncRoutes`) and drop `checkIn` from the upsert's update set when
  it is false. Check `untracked` and `feeling` (V11) the same way if any shipped build predates
  them.

## 5. `unknown_entity` is rolled back like a bad row: deferred (cross-slice, audit: contract)

- **Where:** `libraries/diary/impl/.../DiarySyncer.kt:300-310` (every rejection deletes the
  outbox row and reverts).
- **Rule:** §3 offline first, never a lost write; §9 the contract.
- **Failure:** a phone that ships ahead of its server (a store release before the deploy, or a
  server rollback) logs an exercise; the server answers `unknown_entity`, and the phone deletes
  it. `ErrorSurfacesTest.sync_anEntityTheServerDoesNotKnowYet_keepsTheWrite` reproduces it
  against a stand-in older server (ignored until the fix). Low in practice while new entities
  sit behind release flags the same server serves.
- **Deferred to `libraries/diary/impl`:** in `apply`, leave rows rejected with
  `unknown_entity` in the outbox, unreverted, and exclude them from the `Stalled` comparison
  so the drain does not give up on them.

## 6. A busy server's 503 had no journey: fixed (audit: contract)

- **Where:** `errors/ErrorSurfacesTest.kt`, `helpers/FaultProxy.kt`.
- **Rule:** §7 error surfaces.
- **Fix:** `test(integration): a busy server's 503 is retried, and an entity the server does not know yet is pinned`.
  `FaultProxy.Fault.Status` takes headers; two 503 `busy` answers with `Retry-After` are
  retried and the write goes up once. The client does not read `Retry-After` (its own backoff is
  shorter), which is fine at this size.

## 7. A phone holding another project's token never gets the version floor: deferred (cross-slice, audit: contract)

- **Where:** `apps/server/.../Application.kt:160` (`authenticate(SUPABASE_JWT_AUTH, optional = true)`
  around `appConfigRoutes`), met by `RemoteConfigRemoteDataSource`, which attaches any token it has.
- **Rule:** §9 a wrong environment fails loudly, and the release path; this is the 0.2 TestFlight incident.
- **Failure:** Ktor's optional auth lets a request with no token through but still challenges
  one it cannot verify. A build signed in to the wrong Supabase project sends such a token, gets
  401 on `/v1/app-config`, and the guard fails open: the version floor and the maintenance
  switch, the only ways to stop that build, never reach it. An expired token on the first fetch
  of a cold start does the same until the next refresh.
  `AppGuardJourneyTest.aPhoneWithAForeignToken_stillGetsTheVersionFloor` reproduces it (ignored
  until the fix); the same test with a valid token passes, so the rule targeting is sound.
- **Deferred to `apps/server`:** mount `appConfigRoutes` outside `authenticate`, and resolve the
  user there by verifying the bearer token with the same verifier, treating a token that fails
  as no token. (A client-only alternative, retrying the fetch without the header on 401, would
  also pass the test but leaves 0.2.0 binaries already out stuck.)

## 8. The harness server ran without the ban gate: fixed

- **Where:** `helpers/InProcessServer.kt` (`installApp` without `banGate`, before the fix).
- **Rule:** §9 release configuration exercised end to end; the KDoc said "configured as
  production is".
- **Failure:** production's `module` passes a `BanGate`; the harness did not, so every journey
  ran an auth path production does not, and a ban had no journey at all.
- **Fix:** `test(integration): the harness server checks bans as production does, and a banned user meets the denial`.
  `BanTest` sets `banned_until`, and the 403 envelope reaches `AccessDeniedBus` with the
  appeal URL; the write stays queued.

## 9. Session expiry had no real-client, real-server journey: fixed (audit: contract)

- **Where:** `account/SessionTest.kt` (new); `helpers/DeviceSeams.kt`, `IntegrationAuth.kt`,
  `TestClient.kt`, `IntegrationTest.kt`.
- **Rule:** §10 a token that expires mid-session.
- **Fix:** `test(integration): an expired token refreshes, a refused refresh signs out, and a foreign token is pinned`.
  The Supabase stand-in can start a session on an expired or foreign-signed token and refuse
  a refresh with the SDK's own `UnauthorizedRestException` (built on a real 401 from the
  server). An expired token refreshes on the 401 and the sync succeeds; a refused refresh
  signs out with `SessionExpired` and drops the session. The setup runs inside `TestClient`'s
  constructor because the bearer plugin loads the token once and keeps it until a 401.

## 10. Routes the client handles by code had no journey: fixed (audit: contract)

- **Where:** `account/ExportTest.kt`, `account/DisplayNameTest.kt` (new);
  `foods/FoodSearchAndBarcodeTest.kt`, `foods/DescribeAndPhotoTest.kt`.
- **Rule:** §9 sign in, search, barcode, log, sync, settings, delete, end to end.
- **Fix:** `test(integration): export, display name, search options, barcode outages and the photo limit, end to end`.
  `GET /v1/export.csv` holds another phone's log and not a deleted entry or another account's;
  `PATCH /v1/me` maps 409 and 400 to `DisplayNameTaken` and `InvalidDisplayName` and the name
  stays; `category`, `source` and `sort` filter as asked and `/search/options` reports Open
  Food Facts off; Open Food Facts down makes an unknown scan a failure rather than a miss, and
  a code scanned before still opens from the device; the eleventh photo is `TooMany`. All pass.
  `FakeUpstreams.openFoodFactsDown` and `seedFood(category)` are new.
- **Not done:** the queued-edit flush in `ProfileRepositoryImpl` (an edit made offline, sent on
  reconnect, then refused) still has no journey.

## 11. A barcode scan during an outage keeps the camera waiting for seconds: deferred (cross-slice)

- **Where:** `libraries/foods/impl/.../FoodApi.kt:153` (`barcode` uses `RetryPolicy.idempotent()`).
- **Rule:** §6 bounded retries and a circuit for outages; §10 error states that tell the person what to do.
- **Failure:** the server answers 503 `barcode_lookup_unavailable` only after it has already
  tried every outside source. The phone retries that three more times with backoff (about 2 to
  3.5 s here, against a stand-in that fails at once), and each retry makes the server call every
  source again; against a real Open Food Facts that hangs to its timeout it is far longer. The
  barcode test above takes 8 s for two such scans.
- **Deferred to `libraries/foods/impl`:** give `barcode` a policy like `SEARCH_RETRY` that does
  not retry a `ResponseException` (keep one quick retry for a dropped connection).

## 12. Sync authorization skipped exercise: fixed

- **Where:** `security/SyncAuthorizationTest.kt`.
- **Rule:** §6 a test proves user A can't read or overwrite user B's rows.
- **Fix:** `test(integration): another account cannot read or overwrite exercise rows either`.
  Bob's pull now accepts every entity, and his replay includes Alice's exercise. Fasts are
  still not covered: making one needs `release.fasting` and an evening, and the upsert shares
  the guard the test already proves for seven entities.

## 13. The next Room version could ship without a migration test: fixed

- **Where:** `storage/RoomMigrationTest.kt`.
- **Rule:** §7 the test that would catch it.
- **Failure:** one hand-written test per version, 5 to 9. Bumping to 11 would fail the
  `user_version` check, but the missing `fromVersion10` test would not be noticed.
- **Fix:** `test(integration): a new Room version cannot ship without its migration test`.
  The exported schemas must match the tested set and `Schema.CURRENT`.

## 14. Stale comments and a runCatching that ate cancellation: fixed

- **Where:** `apps/integration/build.gradle.kts:46-64`; `ConfigManifestRegistryDriftTest.kt:72`;
  `AdminUserLookupTest.kt:49`.
- **Rule:** §1 stale comments are bugs; §2 cancellation is never swallowed.
- **Failure:** the build file and the drift test said the config keys lived in two impl modules
  (they span a dozen modules now) and put `release.checkIn` next to Today (it is in
  `:libraries:diary`; Today's third flag is `release.foodActions`). `AdminUserLookupTest`
  wrapped a suspend wait in `runCatching`, which turned any cancellation into an assertion failure.
- **Fix:** `refactor(integration): stale build and drift-test comments, and a runCatching that ate cancellation`.

## 15. A CI runner without Docker passed every journey without running one: fixed

- **Where:** `helpers/InProcessServer.kt` (`assumeDockerAvailable`).
- **Rule:** §9 a step that skips says so where someone will see it, not a silent pass.
- **Fix:** `test(integration): a CI runner without Docker fails the journeys instead of skipping them green`.
  With `CI=true` (GitHub Actions sets it) a missing Docker fails; a laptop still skips. No test:
  the only way to run it is a CI runner without Docker.

## 16. The leak test is named for a mode that no longer exists: comments fixed, rename deferred (cross-slice, audit: truth)

- **Where:** `apps/leaktest/.../BlindModeLeakTest.kt`, `BlindMealGapsTest.kt`, `LeakScan.kt:43`
  ("Pine"), `MicrosHiddenLeakTest.kt:28` ("Sprint"), `LeakTestControlTest.kt:18`.
- **Rule:** §1 names do the documenting.
- **Fix:** `test(leaktest): the leak tests are named for what they check, and No numbers reads what a screen reader says`.
  Slate; no Sprint. That commit also renamed the classes to `CaloriesHiddenLeakTest` and
  `NoNumbersMealGapsTest`; the second review put the old names back (see below), so the class
  rename travels with the sweep that renames everything pointing at it.
- **Deferred (cross-slice):**
  - the class rename itself, in one commit with every file that names the classes:
    `git mv` `BlindModeLeakTest.kt` to `CaloriesHiddenLeakTest.kt` and `BlindMealGapsTest.kt` to
    `NoNumbersMealGapsTest.kt` in `apps/leaktest` (plus the six KDocs and `build.gradle.kts` there),
    `docs/plans/design-polish.md:66` for the gaps test, and for the main one:
    `grep -rl BlindModeLeakTest --exclude-dir=build --exclude-dir=.claude features libraries settings.gradle.kts docs/system-design.md docs/practices | xargs sed -i '' 's/BlindModeLeakTest/CaloriesHiddenLeakTest/g'`
    (about 30 KDocs in `features/*/impl`, `libraries/ui`, `libraries/nutrition`, `libraries/reminders`,
    `libraries/widgets`, plus `settings.gradle.kts:68` and `docs/system-design.md:552`). Leave
    `docs/design/handoff/*`, which quotes the prototype's own name.
  - `docs/practices/review-checklist.md`, `docs/practices/testing.md` and
    `docs/plans/screens/blind-leak-test.md`: the same rename, and `PRACTICES.md` §7's "The Blind
    leak test" becomes "The leak tests".
  - the audit's comment list in other slices (`DayCloseState.kt:208`, `TodayPreviews.kt`,
    `PatternsState.kt:50`, `AddFoodScreen.kt`, `Presets.kt:49/108`, the onboarding KDocs and
    the rest): "Blind" to "No numbers", "Full numbers" to "All numbers"; and
    `TodayModeViews.kt:84` `NutrientsFirstCard` to `SoftSummaryCard`.
  - Preview and surface ids with `blind` in them (`trends-blind`, `foods-add-food-blind`,
    `patterns-blind`) pair with design screen ids; leave them until the design is re-exported.

## 17. No numbers ignored what a screen reader says: fixed

- **Where:** `NoNumbersLeakTest.kt:39`, `MicrosHiddenLeakTest.kt:94` (before the fix).
- **Rule:** §7 the leak test covers every surface; the visibility policy is the only thing
  that may show a number.
- **Failure:** both read only a tagged node's `Text`, while the calorie scan reads its content
  description and editable text too. A metric-tagged ring whose content description said
  "30 g protein" would pass No numbers while TalkBack read it out.
- **Fix:** same commit. All three scans share `SemanticsNode.texts()`. Nothing leaks today. A
  micros surface renamed away now fails `SurfacesCoverageTest.everyMicrosSurfaceIsListed`, as
  an energy-out one already did.

## Not problems

- **The scanner's 43 hits** are all sentence-length test names (`theSourceScanCanActuallyFail`),
  the module's style, and `example.com` test addresses.
- **`SettingsMergeAcrossVersionsTest.V02_KEYS` is a hand list.** The sturdier form is "drop
  everything 0.1 did not know", but there is no 0.1 tag or exported schema to read 0.1's keys
  from (`docs/handoff` says 0.1.0 never reached the store), so the list is the record.
- **`FaultProxy.Fault.Status`'s default body** (`{"code":…}`) is not the server's
  `{"error":{…}}` envelope. Nothing reads it for the paths that use the default.
- **`ScreenIds` has no unit test.** The screenshot module's tests only run when a Roborazzi task
  asks, so a test there would not run in CI either; the rule is written out beside the code
  and in `docs/practices/design-sync.md`.
- **The ban commit does not compile on its own** (`BanTest` reads `TestClient.accessDenied`,
  added one commit later). The branch compiles at its tip; noted so nobody bisects into it.

## Second review

A skeptical second reviewer flagged two regressions on this branch.

1. **`IntegrationTest`'s KDoc still promised "no Docker → skipped, not red".** Real: since
   finding 15, `assumeDockerAvailable()` fails when `CI=true`. Fixed in
   `docs(integration): the base class says a missing Docker fails on CI, not that it is never red`.
   Comment only, so no test; the behaviour and its own KDoc in `InProcessServer` were already
   right.
2. **The leak test rename left about 40 files outside the slice naming classes that no longer
   existed** (`settings.gradle.kts:68`, `HardcodedDisplay.kt:19`, `review-checklist.md:59`,
   every `*LeakSurfaces.kt` and `*Fixtures.kt` KDoc). Real. Those files belong to other slices,
   so the fix here is the reviewer's alternative: keep the old names. Fixed in
   `test(leaktest): keep the leak tests' old names until every file that names them moves too`.
   Every reference resolves again, and finding 16's deferred entry now carries the class rename
   with the sweep so both land in one commit. No test: it is a rename, and the suite runs the
   same cases under the old names.

## Product notes

- **Display names are machinery no screen uses.** Nothing in the app calls
  `ProfileRepository.update(displayName)` or observes `observeEditRejections()`, so a name can
  be neither changed nor refused. Either give it a place in Settings (and then show the refusal
  of a queued edit) or cut the route, the queue and the unique index. Sure about the code; the
  product call is yours.
- **A wrong-environment build should say so on screen.** Finding 7's fix makes the version floor
  reach it; a QA-only banner when `/v1/me` 401s right after a sign-in that succeeded would have
  named the 0.2 TestFlight problem in a minute. Confident it would have helped; cheap to add.
- **When barcode lookup is down, say "couldn't check right now" and offer search,** not a
  spinner and then the not-found sheet. The client already tells the two apart (finding 10);
  check the logger's wording follows. Moderately sure the copy is generic today.
