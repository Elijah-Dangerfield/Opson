# Findings: build config

`build-logic/`, `detekt-rules/`, `config/`, the root Gradle files, `gradle/`, the
release-please config and `apps/baselineprofile/`. Each finding names the place, the
rule from [PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

## 1. The profile journey and the R8 smoke test waited for copy the app no longer has — fixed

- **Where:** `apps/baselineprofile/.../BenchmarkJourney.kt:93` and `:136` (before the
  fix), `FOODS_PLACEHOLDER = "Your favorites and saved meals will live here."`.
- **Rule:** §7 tests fail when the behaviour breaks, not later.
- **Failure:** the Foods tab lost that line when it became real (8069e81), so
  `JourneyProfileGenerator` and `MinifiedReleaseSmokeTest` would stall at the Foods tab
  on the emulator. They run monthly or by hand, so nothing said so.
- **Fix:** `build(baselineprofile): the profile journey finds the Foods tab by text it still shows`.
  It waits for "Create a meal". A root `verifyBenchmarkJourneyCopy` task, on `check`,
  fails when any string the journey waits for is missing from the composeResources
  strings; checked by putting the old line back.

## 2. Two of the five custom detekt rules had no test — fixed

- **Where:** `VerifyStrings.kt`, `ClassNameInTelemetryAttribute.kt`.
- **Rule:** §7; AGENTS.md "a clean detekt run does not prove a custom rule ran".
- **Failure:** a rule that stops matching leaves the build green.
- **Fix:** `test(detekt): prove VerifyStrings and ClassNameInTelemetryAttribute fire`.
  Both fail when the rule's match list is broken (checked).

## 3. ClassNameInTelemetryAttribute said the repo has no dashboards — fixed

- **Where:** `ClassNameInTelemetryAttribute.kt:30-37` (before the fix).
- **Rule:** §1 stale comments.
- **Failure:** `ops/grafana` filters on event attributes; the KDoc argued the rule from a
  state that has not been true for a while.
- **Fix:** same commit as 2.

## 4. The convention plugins' KDocs described another project — fixed

- **Where:** the three library plugins and `ApplicationConventionPlugin.kt`.
- **Rule:** §1 stale comments, dead code.
- **Failure:** they promised JVM targets that were never configured, sent readers to an
  `opson.android.application` plugin that doesn't exist, listed example modules
  (`libraries:database`, `features:profile`, `apps:desktop`) that don't exist, and said
  the feature plugin brings Navigation Compose. Also: an unused import of a Kotlin
  compiler internal (`AnalysisFlags.optIn`), an empty `androidMain.dependencies {}`, and
  opt-ins the feature plugin repeated from the plugin it applies.
- **Fix:** `build: convention plugin docs describe what the plugins do, and the test Compose runtime comes from the catalog`.

## 5. The Compose runtime for common tests was hard-coded — fixed

- **Where:** `ComposeMultiplatformConventionPlugin.kt:77` (before the fix),
  `"org.jetbrains.compose.runtime:runtime:1.9.1"`.
- **Rule:** §1 match the neighbours (versions live in the catalog).
- **Failure:** 1.9.1 against a 1.9.3 Compose plugin; the next upgrade would leave it behind.
- **Fix:** same commit; `libs.compose.runtime` on `composeMultiplatform`.

## 6. The Supabase warning sent people to a file that doesn't exist — fixed

- **Where:** `build-logic/.../Versioning.kt:169`, "See template/SETUP.md."
- **Rule:** §1; AGENTS.md on dead paths in docs.
- **Fix:** same commit; it says `SETUP.md`.

## 7. The release PR told the person merging it the wrong pipeline — fixed

- **Where:** `release-please-config.json`, `pull-request-header`.
- **Rule:** §1 stale comments; this text is what someone reads before shipping to production.
- **Failure:** it said Android goes out as a 10% staged rollout and iOS to a TestFlight group
  called `main`, and asked them to check for a staged rollout still in progress.
  `release.yml` ships Android at full rollout (staged rollouts were dropped on purpose) and
  the group comes from `TESTFLIGHT_EXTERNAL_GROUP`.
- **Fix:** `build(release): the release PR describes the pipeline that actually runs`.

## 8. Release signing falls back to the debug key when asked for release — deferred

- **Where:** `build-logic/.../Signing.kt:27-33`.
- **Rule:** §6 "prod fails closed".
- **Failure:** `-Prelease.signing=true` with a missing secret logs a warning and signs with
  the debug key. Play rejects the AAB at upload, so it fails loudly but only after the
  whole build, and the APK attached to the GitHub release is debug-signed.
- **Why deferred:** the fix is to throw a `GradleException` there instead of returning
  null, but build-logic has no test harness to prove it, and a local `-Prelease.signing`
  workflow may rely on the fallback. The owner should say which.

## Not problems

- `config/detekt/baseline.xml` is empty and all five rules are active in `detekt.yml`.
- `ModuleBoundaries.kt` and `GitHooks.kt` do what their KDocs say, worktrees included.
- `MinifiedReleaseSmokeTest` and the two generators share `BenchmarkJourney`, so finding 1
  covers all three.
- `gradle.properties`, the wrapper properties, `versions.properties` (block markers in
  place) and `telemetry.properties` (only the DSN, which is not a secret) are as their
  comments say.

## Changes made for the UI review

- `ScrollInsideBottomSheet` no longer mentions the deleted `BasicBottomSheet`.
- `VerifyStrings` checks `Text` only; `OutlinedText`, `ClickableText` and
  `BoldPrefixedText` are deleted. Its test now covers positional and named `Text`.
- The unused compottie version and its five library entries are gone from the catalog.

## Product notes

None from this slice.
