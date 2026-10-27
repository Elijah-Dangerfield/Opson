# Findings: scripts, ops and CI

`.github/workflows/`, `.githooks/`, `apps/ios/fastlane/`, `ops/` and `scripts/`.
Each finding names the place, the rule from [PRACTICES.md](../PRACTICES.md),
what breaks, and its fate.

## 1. The ledger never listed config files without a source extension — fixed

- **Where:** `scripts/review_ledger.py:11` (before the fix), `REVIEWED_EXTENSIONS`.
- **Rule:** PROCESS.md step 3, "reads every Kotlin, Swift, SQL, script and config file".
- **Failure:** the Fastfile, the server Dockerfile, both `fly*.toml`, `gradle.properties`,
  `libs.versions.toml`, `proguard-rules.pro`, the git hooks, the Grafana boards and the
  release-please config were in no slice, so no reviewer was handed them.
- **Fix:** `chore(scripts): the review ledger lists config files that carry no source extension`.
  `test_review_ledger.py` covers it. This file's own slice now holds the hooks, the
  Fastfile and the boards; the build-config section holds the Gradle and release config.
- **Deferred (cross-slice):** run `python3 scripts/review_ledger.py` once every slice is
  merged. It adds `apps/server/Dockerfile`, `apps/server/fly.toml` and
  `apps/server/fly.prod.toml` to server, and `apps/compose/proguard-rules.pro` and
  `apps/ios/Configuration/Config.xcconfig` to apps-shells. Not regenerated here because
  it rewrites other slices' sections while they are being ticked.

## 2. Stopping the watched Open Food Facts load left psql running — fixed

- **Where:** `scripts/load_off_watched.sh:29` (before the fix), `kill $pid`.
- **Rule:** §6 "external calls have timeouts … and a circuit for outages"; the script's
  only job is to stop the load at 75% disk.
- **Failure:** psql is the importer's child and keeps executing SQL already piped to it,
  so a load stopped for disk went on building the GIN indexes, where the disk grows most.
  `release-checklist.md` runs this script against prod.
- **Fix:** `chore(scripts): the watched Open Food Facts load stops psql too, and stops when it can't see the disk`.
  It walks the process tree, sends psql SIGINT (which cancels the statement on the
  server) and then kills the rest. Checked against a fake psql grandchild; not run
  against a live project.

## 3. A failed disk read counted as 0% — fixed

- **Where:** `scripts/load_off_watched.sh:25` (before the fix), `pct=$(disk_pct || echo 0)`.
- **Rule:** AGENTS.md "an empty result and a failed fetch are the same shape".
- **Failure:** an expired management token turned the disk guard off without a word.
- **Fix:** same commit. Three failed reads in a row (90 s) stop the load.

## 4. The watched load ran another checkout's importer, with the token in `ps` — fixed

- **Where:** `scripts/load_off_watched.sh:7` and `:13` (before the fix).
- **Rule:** §5 "secrets never in … URLs"; §1 code that reads true.
- **Failure:** it `cd`'d to one machine's main checkout, so run from a worktree it
  loaded with whatever importer main had. The Supabase token sat on curl's command line.
- **Fix:** same commit. It runs from its own checkout and hands curl the header on stdin.

## 5. One statement built every Open Food Facts index, and psql hung when the backend died — fixed

- **Where:** `scripts/import_off_foods.py`, `finalize_sql` (before the fix), reported by the
  orchestrator from the dev load.
- **Rule:** §6 "external calls have timeouts, bounded retries"; "large backfills run as
  scripts in batches".
- **Failure:** Postgres on a 1 GB instance restarted partway through the DO block that
  built all search indexes, which rolled back the ones already built. Behind Supavisor
  psql's TCP connection is to the pooler, which kept it open after the backend died, so
  keepalives never fired and psql waited 20 minutes.
- **Fix:** `chore(scripts): the Open Food Facts reload builds each index in its own session and notices a dead one`.
  `finish()` runs the checks, then each missing index as one statement in its own psql
  session, then ANALYZE, then the swap; `--swap-only` carries on from the first index not
  built. `run_watched` polls `pg_stat_activity` from a second connection and kills a step
  whose statement has gone (or never appeared within five minutes); the quick steps have
  a 10 minute cap. `maintenance_work_mem` defaults to 64MB. `WatchdogTest`, `FinishTest`
  and `IndexBuildsTest` cover it against fakes; `PsqlTest` covers the new psql calls
  against a fake binary. `--output` now writes the load only and says to finish with
  `--swap-only --psql`.
- **Deferred (cross-slice):** `apps/server/README.md:328`, "a failed swap is retried with
  `--swap-only`" should read "a failed index build or swap carries on with `--swap-only`
  from the first index not yet built".

## 6. The launch smoke passed an app whose Kotlin binary was stale — fixed

- **Where:** `scripts/ios_launch_smoke.sh` (orchestrator's lead after c8a4194).
- **Rule:** §7 "every bug fixed gets the test that would have caught it".
- **Failure:** a failed Kotlin compile used to leave Xcode linking the previous framework;
  the app launched and stayed up, so the smoke said OK.
- **Fix:** `chore(scripts): the launch smoke fails on a stale Kotlin binary and compares crash reports by name`.
  The build stamps the commit into `BuildInfo.commitSha`, which Kotlin/Native stores as
  UTF-16; the smoke fails unless HEAD's 12-character SHA is in the executable or its
  `.debug.dylib`. Checked on another template app: its binary matched the commit it was
  built from and not its repo's newer HEAD. Caveat: it compares against HEAD, so commit
  nothing between the build and the smoke.

## 7. New crash reports were detected by count — fixed

- **Where:** `scripts/ios_launch_smoke.sh:16` and `:22` (before the fix).
- **Rule:** §7 tests fail when the behaviour breaks.
- **Failure:** the system prunes old reports, so one leaving as one arrives keeps the
  count level and a crash passes.
- **Fix:** same commit; reports are compared by name.

## 8. cleanup.sh pointed at a directory that no longer exists — fixed

- **Where:** `scripts/cleanup.sh:7-10` (before the fix).
- **Rule:** §1 stale code is a bug.
- **Failure:** `iosApp/...` paths matched nothing, and with no `cd` the `rm -rf .idea build`
  lines ran in whatever directory it was called from. Pointing the old lines at
  `apps/ios` as written would have deleted the tracked `Package.resolved`.
- **Fix:** same commit.

## 9. Third-party actions ran on floating refs, prod deploy on `@master` — fixed

- **Where:** `server-deploy-prod.yml:101` (`superfly/flyctl-actions/setup-flyctl@master`
  with `FLY_API_TOKEN_PROD`), and dorny, r0adkll (Play service account), peter-evans,
  amannn, ruby/setup-ruby, release-please and gradle/actions on tags.
- **Rule:** §5 "secrets never in code, logs"; the lead's "pinned actions".
- **Failure:** a pushed branch or a moved tag runs new code next to the prod Fly token,
  the Play service account or the App Store key.
- **Fix:** `ci: pin every third-party action to a commit` (each SHA is what the tag
  pointed at today, so behaviour is unchanged). GitHub's own `actions/*` stay on major tags.

## 10. A production deploy could be run from any branch — fixed

- **Where:** `server-deploy-prod.yml`, `workflow_dispatch`.
- **Rule:** the lead's "prod deploy gated"; §6 prod fails closed.
- **Failure:** "Run workflow" offers every branch and the approval screen shows it in
  small print, so a feature branch could be approved onto prod. The environment gate
  itself is in place.
- **Fix:** `ci(server): production deploys only main, and the health check retries instead of sleeping`.

## 11. The deploy health check slept five seconds and tried once — fixed

- **Where:** `server-deploy.yml:96-101`, `server-deploy-prod.yml:116-122` (before the fix).
- **Rule:** §7 "no sleeps".
- **Failure:** an edge slower than five seconds failed a good deploy.
- **Fix:** same commit; curl retries six times.

## 12. auto-merge had triggers its only job never ran on — fixed

- **Where:** `auto-merge.yml:20-24`.
- **Rule:** §1 dead code, stale comments.
- **Failure:** `check_suite` and `workflow_run` queued skipped runs, and the header said
  they re-probed merges. Native auto-merge already waits for checks.
- **Fix:** `ci: auto-merge drops the check_suite and workflow_run triggers its only job never ran on`.

## 13. Retag waited a minute to cancel a run that never starts — fixed

- **Where:** `retag-release.yml:126-156` (before the fix), and `:83` the version input.
- **Rule:** §1 dead code and stale comments; script injection hygiene.
- **Failure:** the tag is pushed with `GITHUB_TOKEN`, which starts no workflow
  (release-please.yml relies on the same rule), so every retag waited 60 s and then warned
  that a parallel release might run. The version was spliced into the script and its
  pattern had no `$`.
- **Fix:** `ci(release): retag stops waiting a minute to cancel a run that never starts`.

## 14. Release: Play outage shipped to internal under a green run, and more — fixed

- **Where:** `release.yml:253-262`, `:75`, `:14-15`, `:367-375`; `Fastfile:421-424`.
- **Rule:** AGENTS.md "an empty result and a failed fetch are the same shape"; §5.
- **Failure:** a failed Play edit routed the release to the internal track with a plain
  echo. The dispatch tag input was spliced into the script. The header named the
  TestFlight group `main` (it is `TESTFLIGHT_EXTERNAL_GROUP`). Both Sentry steps ran
  whatever `@sentry/cli` npm called latest, holding the auth token.
- **Fix:** `ci(release): warn when Play can't be asked, pin sentry-cli, and keep the tag input out of the script`.
  Pinned to 3.8.2, today's latest, so behaviour is unchanged.
- **Deferred (cross-slice):** `docs/release-automation.md:83` still draws TestFlight
  `"main"`; it should say the group named by `TESTFLIGHT_EXTERNAL_GROUP`.

## 15. Nothing ran the Python script tests — fixed

- **Where:** `.github/workflows/ci.yml`.
- **Rule:** §7.
- **Failure:** the importer and ledger tests existed and no job ran them. The lint step's
  comment also said VerifyStrings guards a crash on a missing key; it flags inline copy.
- **Fix:** `ci: run the Python script tests on every PR`.

## 16. The importers' psql seam, the reload order and one backfill had no tests — fixed

- **Where:** `import_usda_foods.py` `Psql`, `PsqlOutput`, `stream`; `import_off_foods.py`
  `reload_sql`; `backfill_default_portions.py`.
- **Rule:** §7 "missing tests for behaviour that matters".
- **Failure:** removing the keepalives, the stop-on-error, the batch order or the
  concurrent-write guard would have stayed green.
- **Fix:** `test(scripts): cover the importers' psql seam and the Open Food Facts reload end to end`
  and `test(scripts): cover the default-portion backfill's reorder and its concurrent-write guard`.
  All against a fake psql binary or plain functions. No script test touches a database.

## 17. The importers' downloads have no retry — deferred

- **Where:** `import_usda_foods.py:182` `download`, `import_off_foods.py` `download_export`.
- **Rule:** §6 "bounded retries".
- **Failure:** one dropped connection in a 1.5 GB download fails the monthly job. It
  fails red, and a manual dispatch retries, so nothing is lost but a month.
- **Why deferred:** the right fix is a resumable download (HTTP Range, three attempts)
  with a test against a local server that drops the connection; not urgent next to the
  index-build fix.

## 18. beta_ios.sh stamps `beta` by default — deferred (owner's call)

- **Where:** `scripts/beta_ios.sh`, `RELEASE_CHANNEL_OVERRIDE:-beta`.
- **Rule:** AGENTS.md landmine: a local script that stamps `beta` will produce a binary
  someone promotes, so `store` is the safer default.
- **Failure:** a promoted TestFlight build reports `release_channel=beta` in telemetry.
  Nothing gates on the channel (only `BuildInfo`, `TelemetryInfo`, `AppTelemetry`,
  `GrafanaLogTree` read it), so the cost is a wrong label.
- **Why deferred:** flipping it relabels every TestFlight build's telemetry; that is the
  owner's choice.

## Not problems

- **Secrets in workflows:** every secret reaches a step through `env:` or `with:`; none
  is echoed, and the Play access token is only ever in a shell variable.
- **Prod deploy gate:** `environment: production` with job-level concurrency, as the
  header says.
- **`baseline-profile.yml` on JDK 17** while every other job uses 21: a KMP Android module
  compiles on JDK 17 here (no Java sources, so javac never runs). Left alone.
- **`setup_supabase.main.kts` prints the new database password once:** interactive setup,
  the API never returns it again, and it is never stored. Deliberate.
- **Grafana boards:** `generate_db_dashboards.py` regenerates the committed JSON byte for
  byte.
- **food-catalog-refresh runs the two imports at once:** they write different tables, and
  the Open Food Facts job is off until `OFF_IMPORT_ENABLED`.

## Product notes

- **Barcode scans never read `off_foods`.** The 3.3 million loaded Open Food Facts rows
  only feed search; a scan goes to the live API (`CatalogBarcodeLookup`). Reading the
  table first would make scans faster and work through an Open Food Facts outage. Sure
  of the behaviour; medium on the value.
- **Open Food Facts answers are cached into `foods`** as source `off` (`LiveFoodSources.kt`),
  while the importer keeps ODbL rows out of `foods` on purpose (share-alike). Worth a
  licence read before launch. Medium.
