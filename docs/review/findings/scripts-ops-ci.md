# Findings: scripts, ops and CI (round two)

`.github/workflows/`, `.githooks/`, `apps/ios/fastlane/`, `ops/` and `scripts/`.
Each finding names the place, the rule from [PRACTICES.md](../PRACTICES.md),
what breaks, and its fate. Findings 1 to 8 were routed here by the
release-path and truth audits; 9 onward are this review's.

New script tests: `scripts/test_workflows.py` (workflow invariants, the
Fastfile's helpers under Ruby, the release-config guard and the store notes),
`scripts/test_seed_dev_config.py` and `scripts/test_setup_sentry_ci.py`. CI's
Script tests job picks them up.

## Owner actions these fixes need

None of these were done here: each touches a live service.

1. **Dev admin token**, both sides at once. Until it exists, every dev deploy
   now goes red at "Upload config manifest" (finding 2). Either run
   `./scripts/setup_fly.main.kts`, which now sets both, or:

   ```bash
   T=$(openssl rand -hex 32); [ -n "$T" ] && printf 'ADMIN_API_TOKEN=%s\n' "$T" | fly secrets import -a opson-server-dev && printf '%s' "$T" | gh secret set ADMIN_API_TOKEN_DEV; unset T
   ```

2. **Grafana client credentials** (finding 9). Create a Grafana Cloud
   access-policy token with `logs:write`, add it to the credential store as
   `grafana.logsWriteToken` (`./scripts/setup_credentials.main.kts`), then run
   `./scripts/setup_github_secrets.main.kts` to push all three `GRAFANA_*`
   secrets.
3. **NIGHTJAR_SITE_TOKEN** (finding 7), only if Legal Sync should publish:
   `./scripts/setup_legal_sync.sh`.

## 1. The App Store submission job would die in bundler on the first release, fixed

- **Where:** `.github/workflows/release.yml:787` (before the fix), `ios-submit`'s
  `ruby-version: '3.2'`.
- **Rule:** §9 "before a build is called ready, its release configuration has
  been exercised end to end".
- **Failure:** `Gemfile.lock` resolves excon 1.8.0, which needs Ruby 3.3. release.yml
  has never run. On the first tag, `ios` uploads the binary, Android ships to Play
  production (it waits only on the upload), and `ios-submit` dies in `bundle
  install`. The App Store never gets the submission: the "Google shipped, Apple
  didn't" split the job split exists to prevent.
- **Fix:** `ci(release): the App Store submit job runs the Ruby fastlane's bundle installs on`.
  `RubyVersionTest` reads every workflow's `ruby-version`.

## 2. Nothing put new release flags into dev, and the lever for it was off, fixed (secret deferred)

- **Where:** `.github/workflows/server-deploy.yml:109` (manifest step),
  `scripts/setup_fly.main.kts` (never made an admin token).
- **Rule:** §9 "secrets and repo settings the pipelines need exist; a step that
  skips for a missing secret says so".
- **Failure:** 17 `release.*` flags default false and dev only had the ones set by
  hand. `opson-server-dev` has no `ADMIN_API_TOKEN`, so its admin routes aren't
  mounted, and the repo has no `ADMIN_API_TOKEN_DEV`, so every dev deploy skipped the
  manifest with a warning nobody read. Dev's `config.refreshThrottleMs` isn't 0 either.
- **Fix:** `ci(server): every dev deploy turns on the release flags dev doesn't have yet`.
  `scripts/seed_dev_config.py` runs after each dev deploy and writes only flags dev
  has no row for (one set false on purpose stays false), plus
  `config.refreshThrottleMs=0`. A missing `ADMIN_API_TOKEN_DEV` is now `::error::`.
  The script is a trigger path, so a change to it redeploys dev. Tested against a
  fake admin server, including a stale token.
  `chore(scripts): setup_fly keeps secrets off argv and gives dev an admin token`
  makes `setup_fly.main.kts` set both sides to one fresh value whenever either is
  missing.
- **Deferred (owner):** the secret itself, owner action 1.

## 3. The iOS Sentry release never met its events, fixed

- **Where:** `apps/ios/fastlane/Fastfile:341` and `:401` (before the fix).
- **Rule:** §9 "one switch per build picks the environment, and every value
  follows it".
- **Failure:** the Fastfile named the release `opson@<version>+<CFBundleVersion
  timestamp>`; the app tags events `opson@<versionName>+<BuildInfo.buildNumber>`
  (the commit count). Sentry shows `opson@0.2.0+202610271424` with commits and no
  events, and `opson@0.2.0+702` with every event and no commits.
- **Fix:** `build(ios): the Sentry release carries the build number the app reports`.
  `sentry_release_name` reads `VERSION_NAME_OVERRIDE` and `BUILD_NUMBER_OVERRIDE`,
  the values `build_and_upload` bakes into `BuildInfo`. `FastfileTest` loads the
  Fastfile under Ruby with the fastlane DSL stubbed.

## 4. The App Store "What's New" would have been release-please's changelog, fixed

- **Where:** `.github/workflows/release.yml:796` (before the fix).
- **Rule:** §9 store copy is part of the release path.
- **Failure:** `ios-submit` fed `gh release view --json body` to deliver as
  `release_notes.txt`: markdown headings, shas and repo links on the App Store
  listing, uneditable once in review. The Play step's own comment forbids it.
- **Fix:** `ci(release): the App Store What's New comes from release-notes, not the changelog`.
  Play, TestFlight and the submission all call `scripts/store_release_notes.py`
  (the version's `release-notes/<version>.txt` or "Small fixes.", cut by
  characters). `StoreReleaseNotesTest` covers it and checks no workflow reads the
  release body.

## 5. Laptop store builds skipped every release check, fixed (Gradle half deferred)

- **Where:** `scripts/beta_ios.sh:140` and `:137` (before the fix).
- **Rule:** §9 "release values come from committed config or CI secrets, never
  from a developer's machine"; "a wrong environment fails loudly at build time".
- **Failure:** 0.1.0 and the 0.2 TestFlight build came from this script, which
  skipped release.yml's only guard (the Sentry DSN). Its comment cited
  `AdUnits.useTestUnits`; the app has no ads.
- **Fix:** `ci(release): one release-config guard for release, beta and the laptop build`.
  `scripts/verify_release_config.py` fails on a missing Sentry DSN or a missing or
  placeholder prod Supabase project or key, and release.yml, beta.yml and
  `beta_ios.sh` all run it before building. The comment now says what the channel
  does. `VerifyReleaseConfigTest` covers it.
- **Not changed:** the `beta` default channel. AGENTS.md prefers `store`, but
  `QaGate` hides the QA menu once the App Store installs a promoted binary, and the
  channel otherwise only labels telemetry. Same call as round one's finding 18.
- **Deferred (cross-slice):** the same checks belong in Gradle so no build path can
  skip them (see finding 8's Versioning.kt entry).

## 6. A missing Sentry token skipped in silence, fixed

- **Where:** `release.yml:370` (Android Sentry step), `Fastfile:395`, `release.yml:317`.
- **Rule:** §9 "a step that skips for a missing secret says so as a warning";
  §1 stale comments.
- **Failure:** both skipped without a word. `upload-proguard ... || true` had no
  ProGuard UUID to match (the Gradle plugin uploads with one) and the native
  `debug-files upload` pointed at libraries the app doesn't ship. The mapping
  comment said minify is off; R8 is on. The header said the `ios` job submits to
  review and listed neither `build-number` nor `ios-submit`.
- **Fix:** `ci(release): a missing Sentry token is a warning, and the mapping is the plugin's`
  and the Fastfile commit in finding 3 (`UI.important` on a missing token).
  `AndroidSentryTest` and `FastfileTest` cover both.
- **Deferred (cross-slice):** `apps/compose/build.gradle.kts`,
  `autoUploadProguardMapping` keys off `SENTRY_AUTH_TOKEN` being *present*, and a
  workflow passing an unset secret sets it to an empty string, which is present. A
  missing secret then makes the Gradle plugin try an upload with no token. Change it
  to `providers.environmentVariable("SENTRY_AUTH_TOKEN").map { it.isNotBlank() }.orElse(false)`.
  The workflows now unset an empty token before Gradle runs (second review, item 4),
  so this is only for a shell that exports it empty.

## 7. Legal Sync was a red X on main, fixed (secret deferred)

- **Where:** `.github/workflows/legal-sync.yml:86` (before the fix).
- **Rule:** §9 a missing secret is a visible warning, not a red build everyone
  learns to ignore.
- **Failure:** `NIGHTJAR_SITE_TOKEN` isn't set, so every push to `legal/` failed at
  the site checkout (runs 40555793317, 40552018955, 40540506621).
- **Fix:** `ci(legal): a missing site token skips the sync with a warning, not a red main`.
  The job still checks the legal files, then skips with a warning naming
  `setup_legal_sync.sh`. `LegalSyncTest` checks every step using the token waits for it.
- **Deferred (owner):** owner action 3, if the workflow should publish.

## 8. setup_supabase described a guest flow that's gone, fixed (two cross-slice)

- **Where:** `scripts/setup_supabase.main.kts:241`, `:12`, `:280-291`, `:333`,
  `:24`; `scripts/setup.main.kts:120`; `scripts/README.md:222`.
- **Rule:** §1 stale comments are bugs; §9 one switch per build.
- **Failure:** it turned on anonymous sign-ins "or every first launch fails", and
  nothing creates guests since ed6ef6d8, so on a fresh project it only let anyone
  with the publishable key mint accounts. It never turned on manual linking, which
  linking onto a guest needs. It set `SUPABASE_PROJECT_ID` / `SUPABASE_ANON_KEY`
  repo secrets no workflow reads and said CI reads them. It ended with "complete
  onboarding as a guest", and said the app works without Google, while Android
  signs in with nothing else.
- **Fix:** `chore(scripts): setup_supabase stops opening anonymous sign-up on new projects`.
  A project it creates gets anonymous sign-ins off; an adopted one keeps its
  setting, because 0.1 and TestFlight 702 still make guests (HANDOFF.md says keep
  them on); `--allow-anonymous` turns them on. Manual linking goes on. The secret
  step is gone and the text says release builds read `supabase.properties`.
  Compile-checked; not run (it writes to Supabase).
- **Deferred (cross-slice):**
  - `build-logic/src/main/java/com/opson/util/Versioning.kt:196-203`: warn only that
    debug builds will fail to sign in when the dev half is unset (every CI release
    build prints the current warning, which is false for a release), and fail a
    release build whose prod project id or key is the placeholder.
  - `SETUP.md:372`: "creates the project, turns on anonymous sign-ins," should read
    "creates the project, turns on manual linking (anonymous sign-ins go off on a
    project it creates and stay as they are on one it adopts),".

## 9. No store build has ever sent app events to Grafana, fixed (secret deferred)

- **Where:** `release.yml` and `beta.yml` pass `secrets.GRAFANA_*`; `scripts/beta_ios.sh`
  passed nothing.
- **Rule:** §9 "every environment value follows the switch (Grafana endpoint)";
  "a step that skips for a missing secret says so".
- **Failure:** the repo has no `GRAFANA_*` secrets, `local.properties` and
  `~/.gradle/gradle.properties` have no `grafana.*` keys, and the credential store
  has no `grafana.logsWriteToken`. `GrafanaCloud.isConfigured` is therefore false in
  every build so far, CI or laptop, and app events never leave the phone. Every
  client panel on Pulse, the onboarding funnel and App health is empty for store
  users, and nothing says why.
- **Fix:** `ci(release): a store build without Grafana credentials says so`. The
  release-config guard warns (not fails, since SETUP.md calls them optional),
  naming each missing value, and release.yml and beta.yml hand the guard the
  secrets. `beta_ios.sh` exports whatever the store holds.
- **Deferred (owner):** owner action 2.

## 10. The baseline profile job would fail every month from 3 November, fixed (journey deferred)

- **Where:** `.github/workflows/baseline-profile.yml:15-18` (the schedule).
- **Rule:** §10 journeys; §9 a red build everyone learns to ignore.
- **Failure:** `BenchmarkJourney.reachHome` taps "Skip and start logging", which no
  longer exists: since 908798dd sign-in comes right after Welcome, so the journey
  can't reach Today and the job fails at "Never reached Today". It has never run.
- **Fix:** `ci(perf): the baseline profile stops its monthly run until the journey can sign in`.
  The schedule waits behind a `TODO(review)`; dispatch still works.
- **Deferred (cross-slice):**
  `apps/baselineprofile/src/main/kotlin/com/opson/baselineprofile/BenchmarkJourney.kt`
  needs a way past sign-in. It runs a release build, so it talks to prod: a signed-in
  journey needs a test account gated on the backend environment, as its own KDoc
  says, not on `BuildConfig.DEBUG`.

## 11. The first-release banner promised the wrong thing, fixed

- **Where:** `.github/workflows/release-please.yml:38-86`.
- **Rule:** §1 code that reads true; §9 the release path.
- **Failure:** with no `v*` tag (none exists) the banner said the release would go
  to the Play internal track and TestFlight internal only. release.yml decides from
  the stores, and 0.1.0 and the 0.2 TestFlight build already went up from a laptop,
  so merging can go straight to Play production and App Store review.
- **Fix:** `ci(release): the first-release banner says what release.yml will actually do`.

## 12. setup_fly put the database password in every process listing, fixed

- **Where:** `scripts/setup_fly.main.kts:79-81` (before the fix).
- **Rule:** §5 secrets never in URLs or argv (round one's finding 4 for the same class).
- **Failure:** `fly secrets set DATABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=...` on
  argv, two functions below a KDoc explaining why the deploy token is piped.
- **Fix:** in finding 2's setup_fly commit: `fly secrets import` on stdin.

## 13. setup_sentry_ci.sh put the Sentry token on curl's command line, fixed

- **Where:** `scripts/setup_sentry_ci.sh:89-90` (before the fix).
- **Rule:** §5.
- **Fix:** `chore(scripts): setup_sentry_ci keeps the Sentry token off curl's command line`.
  `test_setup_sentry_ci.py` runs the function against a fake curl.

## 14. `setup --status` called installed hooks missing, fixed

- **Where:** `scripts/setup.main.kts:76-77` (before the fix).
- **Rule:** §1 match the neighbours (GitHooks.kt dropped this exact comparison).
- **Failure:** it compared `core.hooksPath` to the string ".githooks"; a worktree
  writes an absolute path, so a working setup read as not installed.
- **Fix:** `chore(scripts): setup --status reads the hooks path as a path, and stale pointers go`.
  The same commit drops pointers to `init_project.main.kts`, `verify_template.sh`,
  `enable_ci.sh` and `docs/template-maintenance.md` from `setup_store.main.kts` and
  `scripts/README.md`; this repo has none of them. Checked by running `--status`.

## 15. create_module made a module that failed detekt, fixed

- **Where:** `scripts/create_module.main.kts:335`, `:222`, `:363`, `:79`, `:3`.
- **Rule:** §1 dead code, untagged TODO, narrating comments; the VerifyStrings gate.
- **Failure:** the feature screen it writes rendered an inline `"<Name> Screen"`,
  which VerifyStrings fails, so a fresh module broke the pre-push gate. It made a
  `jvmMain` folder for a target no convention plugin has, its starter view model
  waited five seconds, a failure exited 0, and it resolved kotlinx-coroutines over
  the network without using it.
- **Fix:** `chore(scripts): create_module makes modules that pass detekt and has no jvmMain`.
  Checked by generating a feature module in a scratch project; the generated module
  was not compiled against this repo.

## 16. setup_cloudflare_site claimed every step checks first, fixed

- **Where:** `scripts/setup_cloudflare_site.sh:13`.
- **Rule:** §1 stale comments.
- **Failure:** step 6 sets `WAITLIST_ALLOWED_ORIGINS` on prod every run and replaces
  any origin added by hand.
- **Fix:** `docs(scripts): setup_cloudflare_site says which step re-runs write anyway`.

## 17. A workflow that doesn't parse fails on its trigger day, fixed

- **Where:** `.github/workflows/*.yml`.
- **Rule:** §7 every bug class gets the test that catches it.
- **Failure:** release.yml's mistakes surface on release day. Nothing checked the
  files parse.
- **Fix:** `test(ci): every workflow file must parse` (Ruby's YAML parser in the
  script tests). `actionlint` is clean over every workflow as well; CI doesn't run it.

## Not problems

- **Round one's fixes still hold:** every third-party action is pinned to a SHA
  (including the newer wrangler and create-pull-request steps), prod deploys run
  only `main`, the deploy health checks retry, the Play edit failure warns.
- **Grafana boards:** `generate_db_dashboards.py` regenerates the committed JSON byte
  for byte, and every board has the single `env` toggle; the three hand-made Loki
  boards use it consistently.
- **`import_nevo_foods.py`** (new since round one): reads the file as untrusted data,
  refuses a version the app doesn't cite, keeps the URL off argv for psql, and has
  tests. Nothing found.
- **Check-mark glyphs in setup scripts** (the scanner's 83 "emoji"): terminal status
  marks, the same in every script.
- **`retag-release.yml`, `auto-merge.yml`, `commitlint.yml`, `pages.yml`,
  `food-catalog-refresh.yml`, `server-deploy-prod.yml`, the hooks:** read, nothing
  found beyond round one.

## Still deferred from round one

- **The importers' downloads have no retry** (round one 17). Unchanged.

## Second review

A second reviewer flagged five possible regressions in this branch. Four were
real and are fixed; the fifth is the expected owner action, with its stale
comment fixed.

1. **Dev deploys go red until the dev admin token exists.** Real, and expected:
   it is owner action 1, and the red run is the point (the old warning went unread
   for every deploy). Not done here, because it writes Fly and GitHub secrets. The
   manifest step's "don't fail an otherwise-healthy deploy" comment was stale once
   the seed step fails on the same 401; it now says so. Fixed in
   `docs(review): second-review fixes for scripts-ops-ci comments and docs`.
2. **Owner action 1's one-liner put the token on argv** with `fly secrets set`,
   the leak finding 12 fixes. Real. It now pipes `ADMIN_API_TOKEN=...` into
   `fly secrets import`. Same commit.
3. **scripts/README.md said release builds read `supabase.properties` and nothing
   else.** Real: `Versioning.kt` and `verify_release_config.py` take
   `SUPABASE_PROD_PROJECT_ID` / `SUPABASE_PROD_ANON_KEY` from the environment first.
   The text now says that, and that `local.properties` is never read. Same commit.
4. **With no Sentry token, the release build fails before "Warn on missing Sentry
   token" can run.** Real. The job env turns an unset secret into an empty
   `SENTRY_AUTH_TOKEN`, which Gradle's `isPresent` counts as set, so the Sentry
   plugin tries the mapping upload with no token. The Gradle release steps in
   release.yml and beta.yml now unset an empty token first, and beta warns, since
   it had no warning of its own. Fixed in
   `ci(release): a store build with no Sentry token skips the mapping upload instead of failing`.
   `SentryTokenReachesGradleTest` runs each step's script against a fake `gradlew`
   and fails without the fix. The Gradle-side change in finding 6 stays deferred.
5. **setup_cloudflare_site's re-run note undersold what re-runs write.** Real:
   steps 4, 5 and 7 also act every run, and step 6 restarts prod. The header now
   lists all of it. Same commit as item 1.

## Product notes

- **Dev now runs every release flag on.** That makes dev the place to try a
  half-built feature, and also means dev never shows the app a 0.2 customer sees with
  a flag still off. A "prod-like" toggle in the admin console (copy prod's flag rows
  onto dev) would cover the second. Medium.
- **App-event analytics have never reached Grafana from a real install** (finding 9).
  Every product decision so far that leaned on the client panels leaned on debug
  builds. Sure of the cause; worth checking the boards' numbers against Sentry's
  session counts once the token is set.
- **The first CI release goes straight to production on both stores.** The
  release-please PR body now says so, but the owner may want the first one dispatched
  with `skip_app_store` to watch the pipeline end to end before a review submission.
  Medium.

## Deferred cross-slice items: fates (2026-10-28)

Applied on branch `worktree-agent-a89d6bb1fb957d17a` once every slice was on main.

- Finding 5, the Gradle half: already done on main (`verifyReleaseEnvironment`,
  build-config finding 1).
- Finding 6: applied in a1418f75.
- Finding 8: the `Versioning.kt` half was already done on main by build-config
  finding 1; `SETUP.md` applied in 2f254417.
- Finding 10: still open, same as build-config finding 4.
