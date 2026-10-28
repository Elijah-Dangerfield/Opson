# Findings: build config (round two)

`build-logic/`, `detekt-rules/`, `config/`, the root Gradle files, `gradle/`, the
committed `.properties` files, the release-please config and `apps/baselineprofile/`,
plus the cross-cutting audit findings routed here. Each finding names the place, the
rule from [PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

Round one's fixes still hold: the convention plugin KDocs, the catalog Compose runtime,
the release PR header and the detekt rule tests are as round one left them. Its
deferred finding 8 is fixed below (finding 5).

## 1. A release build could ship any Supabase project, and nothing checked: fixed, with two parts deferred

- **Where:** `build-logic/.../Versioning.kt:210-219` (before the fix), audit release-path.
- **Rule:** §9 a wrong environment fails loudly at build time; no placeholder values in a
  release build; release values never depend on whose machine built it.
- **Failure:** prod resolved as `SUPABASE_PROD_*` env, then `supabase.properties`, then
  the `UNCONFIGURED_*` placeholder, silently. Deleting `supabase.properties` or misspelling
  `supabase.prod.projectId` shipped a binary signing in to
  `set-supabase-projectId-in-local-properties.supabase.co`; a stray `SUPABASE_PROD_PROJECT_ID`
  in a shell redirected a release build; nothing compared prod with dev. Meanwhile the
  "Supabase is not configured" warning fired on every CI release build (no
  `local.properties` there) although release builds no longer read the dev values, which
  teaches people to ignore it.
- **Fix:** `build(release): a release build with a wrong Supabase project or no Sentry DSN fails at build time`.
  `SupabaseMetadata.releaseProblems()` refuses a prod ref that isn't 20 lower-case
  letters (so the placeholder too), a key without `sb_publishable_`, and prod equal to
  dev. `TelemetryMetadata.releaseProblems()` refuses a blank Sentry DSN and half-set
  Grafana values. `verifyReleaseEnvironment` (ApplicationConventionPlugin) throws on
  either, and every non-debug Android variant's `pre<Variant>Build` and every
  `KotlinNativeLink` with `NativeBuildType.RELEASE` depends on it, so release.yml,
  beta.yml, the Fastfile and `scripts/beta_ios.sh` all run it (checked with `-m`: in the
  graph for `preReleaseBuild` and `linkReleaseFrameworkIosArm64`, absent for the debug
  ones), and it fails the build with `supabase.properties` moved away. Prod now comes
  from `supabase.properties` only; the env override had no users.
  The dev warning moved to where `OpsonBuildConfig` is written, once per build, and says
  it is about debug builds. `ReleaseEnvironmentTest` covers each case; it fails with the
  old env override and without the dev check.
- **Deferred (cross-slice), the live check:** this is what would have caught the actual
  2026-10-27 bug, where both values were well formed but different projects.
  1. `apps/server/.../routes` (the `/_health` route): also return
     `"supabaseProject": <host label of SUPABASE_URL>` (public; the JWKS URL already
     exposes it).
  2. `.github/workflows/beta.yml` and `release.yml`, a step before the builds:
     `[ "$(curl -fsS https://opson-server-prod.fly.dev/_health | jq -r .supabaseProject)" = "$(grep '^supabase.prod.projectId=' supabase.properties | cut -d= -f2)" ] || { echo '::error::the binary would sign in to a different Supabase project than prod verifies'; exit 1; }`
     (beta builds also sign in to prod, since they are release builds).
- **Deferred (cross-slice), one function for the switch:** `libraries/core/.../SupabaseInfo.kt`
  and `apps/compose/.../OpsonNetworkConfig.kt` each key on `BuildInfo.isDebug`. Move
  `isDebug -> (apiBaseUrl, supabaseUrl, anonKey)` into one function in `:libraries:core`
  (the API URLs move with it), have both read it, and unit-test both branches.

## 2. Client Grafana telemetry is off in every build that has shipped: partly fixed, the decision is the owner's

- **Where:** `build-logic/.../Versioning.kt:260` (before the fix), audit release-path.
- **Rule:** §9 a missing secret is a visible warning, not a silent pass.
- **Failure:** confirmed: `gh secret list` has no `GRAFANA_*` secrets, `local.properties`
  has no `grafana.*` keys, `telemetry.properties` holds only the DSN. So
  `GrafanaCloud.isConfigured` is false in every build and nothing says so. The release
  guard (`release.yml:111`) checks only Sentry, and the privacy policy describes usage
  events that are never sent.
- **Fixed here:** in the same commit as finding 1, every release build prints "Grafana app
  events are off in this build" with the script that sets the secrets, as a `::warning::`
  annotation when it runs in GitHub Actions. Some-but-not-all Grafana values fails the
  build, because that is a typo or a missing secret, never a choice.
- **Deferred (owner):** turning it on means running `./scripts/setup_github_secrets.main.kts`
  with Grafana write credentials, and adding the keys to the local credential store so
  `beta_ios.sh` builds match. That sends data to a third party and needs secrets I must
  not handle.
- **Deferred (cross-slice):** `libraries/telemetry/impl/.../GrafanaAppEvents.kt`: log once at
  Info that the pipe is dormant and why, when `GrafanaCloud.isConfigured` is false. The
  `release.yml` guard needs no change now: `verifyReleaseEnvironment` runs in the same
  jobs and checks more.

## 3. AppBuildConfig held dev and prod Supabase side by side and nothing read it: fixed

- **Where:** `build-logic/.../ApplicationConventionPlugin.kt:111` (before the fix), audit
  release-path; `SETUP.md:343`.
- **Rule:** §1 no dead code; §9 one switch per build.
- **Failure:** `com.opson.appconfig.AppBuildConfig` had `SUPABASE_URL` (dev) next to
  `SUPABASE_PROD_URL` with no reference anywhere. The next reader reaching for
  `AppBuildConfig.SUPABASE_URL` gets dev in a release build. SETUP.md told readers to bind
  `AppBuildConfig.API_BASE_URL`, which never existed.
- **Fix:** `build: drop the unused AppBuildConfig that held dev and prod Supabase side by side`
  (the buildconfig plugin is no longer applied to the app), and the SETUP.md snippet is now
  `OpsonNetworkConfig` with `apiBaseUrl(BuildInfo.isDebug)` in
  `docs(setup): Supabase setup describes required accounts and where release builds get prod`.

## 4. The profile journey and the R8 smoke test can't get past sign-in: fixed on the journey side, the rest deferred

- **Where:** `apps/baselineprofile/.../BenchmarkJourney.kt:126` and
  `MinifiedReleaseSmokeTest.kt:67` (before the fix), audit journeys.
- **Rule:** §9 environments and the release path; §10 fresh install reaches sign-in.
- **Failure:** `SKIP_AND_START_LOGGING` is in no strings file since 908798dd, so
  `verifyBenchmarkJourneyCopy` failed the root `check`. Fixing the constant would not have
  helped: sign-in is required and the journey had no way past it. The generators and the
  smoke test run release variants, so as built they would sign in against prod Supabase
  and the prod server.
- **Fix (journey):** `build(baselineprofile): the profile journey signs in instead of tapping a Skip that is gone`.
  `reachToday()` taps Get started and Continue with email, fills the two fields with
  `benchmarkEmail` / `benchmarkPassword` from the instrumentation arguments, taps Sign in
  and waits for Today; it still returns early when Today is already up, and a missing
  account fails with the flags to pass. The smoke test calls it instead of its own copy.
  `verifyBenchmarkJourneyCopy` skips the `*_ARGUMENT` names, which are not copy, and passes.
- **Deferred (cross-slice):**
  1. `apps/compose/build.gradle.kts`: the baseline-profile variants (`nonMinifiedRelease`,
     `benchmarkRelease`) set an `isBenchmark` BuildConfig/BuildInfo flag and take the DEV
     Supabase project and `DEV_API_BASE_URL` (from the `SUPABASE_PROJECT_ID` /
     `SUPABASE_ANON_KEY` CI secrets). The store `release` type never carries it; a
     build-logic check (beside `verifyReleaseEnvironment`) should assert that.
  2. `features/onboarding/impl/.../OnboardingScreen.kt:868` and `AuthScreens.kt:184`: show
     the email option when `SignInMethods.EMAIL || BuildInfo.isBenchmark`.
  3. Owner: an email/password user created and confirmed in the dev project with the
     admin API, already onboarded, so sign-in lands on Today.
  4. `.github/workflows/baseline-profile.yml`: pass
     `-Pandroid.testInstrumentationRunnerArguments.benchmarkEmail=…` and `…benchmarkPassword=…`
     from GitHub secrets to `generateBaselineProfile`, and fail with `::error::` when they
     are missing. Its comment at "Generation runs a *release* variant" should point at the
     flag from 1.
  Until 1 to 3 land, a run stops at the sign-in step with no "Continue with email".

## 5. Release signing fell back to the debug key when asked for release: fixed

- **Where:** `build-logic/.../Signing.kt:27-33` (before the fix); round one's deferred 8.
- **Rule:** §6 prod fails closed; §9 fails loudly at build time.
- **Failure:** `-Prelease.signing=true` with a missing secret logged a warning and signed
  with the debug key. Play rejected the AAB only after the whole build, and the APK
  attached to the GitHub release was debug-signed.
- **Fix:** `build(release): asking for release signing without an upload key fails instead of using the debug key`.
  Round one held off for lack of a harness and in case a local workflow relied on the
  fallback. Only beta.yml and release.yml pass the flag, both with the secrets, and a
  local release build without the flag still signs with the debug key.
  `ReleaseSigningTest` (Gradle's `ProjectBuilder`) covers off, on with no key, and on with
  a key in `local.properties`.

## 6. Thirteen version catalog entries nothing used: fixed

- **Where:** `gradle/libs.versions.toml`.
- **Rule:** §1 no dead code.
- **Failure:** appcompat, espresso-core, coil and coil-ktor, ktor client websockets and
  logging, ktor server websockets, the otel-kotlin OTLP exporter and
  datastore-preferences-core had no `libs.` reference in any build file; the versions
  `android-lifecycle-etx`, `kotlinx-serialization` and `composeIcons` had no entry pointing
  at them. A reader can't tell what the app depends on, and an upgrade bumps phantoms.
- **Fix:** `build: drop version catalog entries nothing uses`.

## 7. The two verify tasks claimed to be incremental: fixed

- **Where:** `build.gradle.kts:88-89`, `:133` (before the fix).
- **Rule:** §1 stale comments are bugs.
- **Failure:** "No output; up-to-date is driven purely by the inputs above" with
  `outputs.upToDateWhen { true }`. A task with no declared outputs is never up to date, so
  the line did nothing and the comment said the opposite.
- **Fix:** `build: comments in the root build and detekt rules say what the code does now`.
  The same commit fixes HardcodedDisplay's KDoc ("someone who chose Blind", a mode that is
  gone) and a `// Note:` in settings.gradle.kts.

## 8. `moduleConfig { di() }` repeats what every convention plugin already does: deferred (cross-slice)

- **Where:** `build-logic/.../ConfigurationExtension.kt:37-42`.
- **Rule:** §1 no dead code, names that need a comment.
- **Failure:** all three library plugins call `configureKotlinInject()`, so `di()` adds the
  same KSP and runtime dependencies a second time. Its own comment says most modules
  don't need it; none do.
- **Deferred:** remove the `di()` line from `libraries/config/impl`, `libraries/flowroutines`,
  `libraries/identity/impl`, `libraries/review/impl` and `libraries/telemetry/impl`
  `build.gradle.kts`, then delete `ConfigurationExtension.di()`. `TODO(review)` at the
  function.

## 9. Every library's androidMain gets androidx.activity.compose: deferred

- **Where:** `build-logic/.../KotlinMultiplatform.kt:22-24`.
- **Rule:** §1 match the neighbours; a concrete build cost.
- **Failure:** `:libraries:core`, `:libraries:networking` and every other plain Kotlin
  library compile against activity-compose and the Compose UI it drags in. Nothing in them
  needs it.
- **Why deferred:** removing it needs each module checked for an implicit use of
  `ComponentActivity` or `setContent`, and a full Android build of every module, which is
  more than this pass should risk. `TODO(review)` at the line.

## 10. The release docs, legal pages and rubric still describe the guest flow and retired modes: fixed

Routed from audit truth. Each verified against the code before editing.

- **`legal/privacy.md:43`, `terms.md:43`, `health-data.md:71`** (§1, §10): the policy said
  the app makes an account on first open with no email and a "QuietFox72" name, didn't
  list Google sign-in, and told people we might not find their account from an email.
  Confirmed: sign-in is required, `DisplayNameSuggester` is only referenced by itself, the
  server default is `user-` plus eight characters of the id. Fixed in
  `docs(legal): accounts are required, carry an email, and can come from Google`, then
  taken back out in `docs(legal): keep the 0.1 policy until 0.2 ships` (see the second
  review, item 1): the text is right for 0.2 and wrong for the 0.1 build in the store. One
  departure from the audit's wording: Supabase keeps the name and picture Google shares in
  the sign-in record, so the policy says so rather than only "we don't use the name". The
  dates were already 2026-10-27, and legal-sync runs on its own when this reaches main.
  **The owner should read the new "Your account" paragraph before merging; it is public.**
- **`docs/app-store/listing.md:57-89`:** "Sign-in required: No", four questions, "Full
  numbers", Blind mode, "only if the user signs in with Apple" for email. Fixed in
  `docs(app-store): the review notes say sign-in is required and use today's mode names`,
  with a 0.2 checklist section for App Review information and the App Privacy email answer.
  `PrivacyInfo.xcprivacy` already declares the email.
- **`docs/system-design.md`:** "No manual exercise logging", fasting in "Full and Sprint",
  the Blind leak test, ten presets and removed `ModePreset` fields, `Feel` and
  `FoodIconTile`, "Ate no details", widgets under Later, and photo estimates in 0.2 (held
  out of 0.2 by bef16211). Fixed in
  `docs(system-design): the contract matches the four modes and hand-logged exercise`.
  The exercise line follows `docs/decisions.md`: a "My watch didn't record this" entry does
  add on top, so the audit's "never on top of a watch's workout" would have been wrong.
- **`SETUP.md:364`, `:386-389`, `:476-487`, `README.md:9,13`:** anonymous-first auth, guest
  onboarding, `is_anonymous = true`, `supabase.url` (unread), a QA shake dialog and a
  test-crash button that don't exist. Fixed in
  `docs(setup): Supabase setup describes required accounts and where release builds get prod`.
- **`docs/plans/onboarding-v2.md:19,27,223`, `docs/plans/screens/onboarding.md`,
  `docs/practices/review-checklist.md:35,52,59,62`, `docs/TESTING-ON-DEVICE.md:31,37`,
  `docs/plans/screens/BUILDER-BRIEF.md:9`, `AGENTS.md:325`:** Skip and the account step in
  the flow table, guest creation without a superseded note, `BlindModeLeakTest.SURFACES`,
  Blind and Nutrient focus previews, `NutrientRingLabel`, `IconTile`, "Continue as guest",
  the native widget "held", fasting in Sprint, and a `User.kt` that never existed. Fixed in
  `docs: onboarding plans, the review rubric and the builder brief drop the guest flow and retired modes`.

## 11. Components only their own previews use: deferred (cross-slice)

- **Where:** `libraries/diary/ui/.../FavoriteFoodTile.kt`, `DayTotals.kt` (`MealCheckChip`,
  `NutrientTotalColumn`), `NutrientTile.kt`, `FoodPickRow.kt`, and
  `libraries/nutrition/ui/.../NutrientRingLabel.kt`.
- **Rule:** §1 no dead code.
- **Failure:** found while checking the docs: each is called only from its own `@Preview`.
  The docs above no longer point builders at them.
- **Deferred:** delete them (and their previews and screenshot baselines) in the ui-library
  and domain slices.

## Not problems

- `ModuleBoundaries.kt`, `GitHooks.kt`, `ComposeMetrics.kt`, `SharedConstants.kt`,
  `AndroidConfiguration.kt` and the two Compose/feature plugins do what their KDocs say.
- The five detekt rules are active in `detekt.yml`, the baseline is empty, and each rule
  has a test.
- The release PR header's "submitted to App Store review with phased release" holds: the
  Fastfile skips phased release only on a first version, which the header's first-release
  checkbox covers.
- `gradle.properties`, the wrapper, `versions.properties` (block markers in place),
  `telemetry.properties` (the DSN only) and `supabase.properties` (a publishable key, which
  is not a secret) are as their comments say.

## Second review

A second reviewer flagged five possible regressions in this branch. Each was checked
against the code.

1. **The legal pages described 0.2 while 0.1 is in the store: real, reverted.**
   `legal-sync.yml` publishes `legal/**` as soon as it reaches main, and the site repo
   merges it on its own. `.release-please-manifest.json` is at 0.1.0, which makes an
   anonymous account with no email, so the new "You sign in to use Opson" and "Tell us the
   email you sign in with" would have been wrong about the live app. The rewrite also
   dropped the guidance for no-email accounts from the rights section and from
   `health-data.md`, and added a public statement about Google's name and picture the owner
   had not read. Fixed in `docs(legal): keep the 0.1 policy until 0.2 ships`, which reverts
   02c9e84f, so this branch no longer changes `legal/` at all. No test: it is prose.
   **Deferred (cross-slice, owner):** with the 0.2 release, after the owner reads the
   "Your account" paragraph, `git cherry-pick 02c9e84f`. Add a line under "Before merging
   the legal changes to main" in `docs/plans/0.2/release-checklist.md`: "Re-apply 02c9e84f
   (accounts are required, carry an email, can come from Google); keep a sentence for
   pre-0.2 accounts with no email in the rights section and in health-data.md, pointing them
   to the in-app export and delete buttons."

2. **The doc edits outside build-config: not a regression.** `AGENTS.md`, `README.md`,
   `SETUP.md`, `docs/system-design.md`, `docs/app-store/listing.md`,
   `docs/plans/0.2/release-checklist.md`, `docs/plans/onboarding-v2.md`,
   `docs/plans/screens/*.md`, `docs/practices/review-checklist.md` and
   `docs/TESTING-ON-DEVICE.md` belong to no slice: `LEDGER.md` lists only code and config,
   and none of these files is in any section. The orchestrator's routing record
   (`routedCounts` in the round-two run) sends 13 audit findings to build-config, and the
   doc edits are recorded as routed from the truth audit (finding 10). The reviewer found no false
   statement in these edits. The App Store review notes in `listing.md` describe 0.2, which
   is safe because the 0.2 checklist gates pasting them. The orchestrator should still
   confirm the routing when it merges.

3. **Nothing checked that a release build runs `verifyReleaseEnvironment`: real, fixed.**
   `ReleaseEnvironmentTest` covers what the check refuses, but not the wiring in
   `ApplicationConventionPlugin.verifyReleaseEnvironment()`. Dropping the `dependsOn`, or AGP
   renaming `preReleaseBuild`, would leave every test green while release builds ship
   unchecked. Fixed in `build: check fails when a release build would skip the release
   environment check`: the root task `verifyReleaseEnvironmentWired`, on `check`, fails when
   `:apps:compose:preReleaseBuild` or `:apps:compose:linkReleaseFrameworkIosArm64` is missing
   or doesn't depend on `:apps:compose:verifyReleaseEnvironment`. It passes on this branch and
   fails with the Android `dependsOn` removed. CI's `./gradlew :check` runs on macOS, so both
   tasks exist there.

4. **The R8 smoke test's wait for Today dropped from 30 s to 20 s, with sign-in inside it:
   real, fixed.** Before ac32761e `MinifiedReleaseSmokeTest` waited `LAUNCH_TIMEOUT_MS` (30 s)
   for Today; through `reachToday` it waited `FIRST_SCREEN_TIMEOUT_MS` (20 s), and the wait
   after Sign in now includes a Supabase and server round trip on a cold minified start.
   Fixed in `test(baselineprofile): the smoke test waits as long for Today as it did before
   sign-in`: `reachToday(todayTimeoutMs)` with the smoke test passing 30 s, and
   `SIGN_IN_TIMEOUT_MS` (30 s) for the wait after Sign in. No failing test: this is emulator
   code that can't run until the benchmark account and variant exist (finding 4); the module
   compiles.

5. **`ReleaseSigningTest` failed on a machine exporting `ANDROID_KEY_ALIAS`: real, fixed.**
   The environment wins over `local.properties`, and the `assumeTrue` guard skipped only for
   the two keystore variables, so `askedForWithAKeyInLocalProperties_usesIt` read the
   machine's alias. Fixed in `test(build-logic): the release signing tests ignore the
   machine's ANDROID_KEY* variables`: `releaseCredentials` takes the environment as a
   parameter (`System::getenv` by default) and the tests pass an empty one, so nothing skips.
   With `ANDROID_KEY_ALIAS=ci-alias` exported the old test fails (1 of 3) and the new one
   passes 3 of 3, with no skips, even with `ANDROID_KEYSTORE_PATH` set too.

## Product notes

- **Decide whether the app sends usage events.** Grafana has been off in every shipped
  build while the privacy policy and App Privacy label say usage data is collected. Either
  set the three secrets, or keep it off and the disclosure is merely generous. Sure it's
  off; the choice is yours.
- **The baseline profile has not been regenerated since sign-in became required**, and
  can't be until the benchmark account and variant exist (finding 4). Cold start quietly
  loses AOT coverage for the new sign-in path. Fairly sure.
- **Google sign-in stores a name and picture we never use.** Supabase keeps them in the
  auth record. Dropping them (or saying nothing more about them) is a privacy choice worth
  making on purpose. Moderately sure Supabase stores them by default.

## Deferred cross-slice items: fates (2026-10-28)

Applied on branch `worktree-agent-a89d6bb1fb957d17a` once every slice was on main.

- Finding 1, the live check: applied in e0166bf6. `/_health` reports the
  project ref, and beta.yml and release.yml compare it before building. A prod
  server deployed before this reports none, which is a warning, not a pass.
- Finding 1, one function for the switch: applied in fb618517
  (`BackendEnvironment`).
- Finding 2 (log once when Grafana is off): already done on main.
- Finding 4: still open, waiting on the owner: a confirmed dev account and the
  GitHub secrets. The code steps do nothing without them.
- Finding 8: applied in def6dc41.
- Finding 11: four of the five were deleted on main; `FoodPickRow` went in
  bf65076c.
- Second review, the legal rewrite: the checklist line landed in 39ce95a7.
  The cherry-pick itself is the owner's, with 0.2.
