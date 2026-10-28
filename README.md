# Opson

A Kotlin Multiplatform app with the production systems already wired, not a hello-world scaffold. It carries the hardened patterns of a shipped KMP app (Compose Multiplatform client, Ktor server on Fly.io, Supabase auth) so a new project starts at "day 30", not day 0.

## What you get, working, on day one

**Client (Android + iOS from one codebase)**
- **Supabase auth** with accounts required: Sign in with Apple, Google through the browser, and email when it is switched on, encrypted session storage (Keychain / EncryptedSharedPreferences), session self-heal, blocking screens for expired sessions and banned accounts
- **Offline detection that tells the truth**: OS connectivity combined with witnessed request reachability, driving an offline banner and a `ConnectivityRegained` event
- **Triggered sync**: implement one idempotent `sync()`, register it, and it runs on sign-in, foreground, and reconnect with retry. Plus an offline-write outbox pattern with a shipped reference
- **Remote config end-to-end**: typed `ConfiguredValue`s, offline-first fetch with kill-switch flags (`upgrade.maintenanceMode`, forced-upgrade), QA overrides, and a hosted **admin console** (Kotlin/JS) with targeting rules, audit log, and prod confirm-by-typing
- **Telemetry that answers pages**: one `session_id` pivots Sentry issues, Grafana Loki logs, and Tempo traces; structured `logEvent`s ship over OTLP with disk-buffered durability; MetricKit exit reports on iOS
- **Dev tooling**: shake to report a bug, a QA menu (More › Testing) on debug and beta builds, on-device Wiretap network inspector (debug-only, noop artifact in store builds), a living design-system catalog, in-app review prompting with sane eligibility gates

**Server (Ktor + Postgres, deploys to Fly.io)**
- Supabase JWT verification, ban gate (403 envelope the client understands), player reports (Google Play UGC compliance), account deletion (`DELETE /v1/me`), remote-config source + admin API, session-correlated tracing/logging
- Boots gracefully with zero config (limited mode) and ships a docker-compose local stack
- Two environments: dev auto-deploys on merge, prod behind an approval gate

**Process**
- CI from the first push: build + unit/server/integration test jobs, release-please versioning, TestFlight/Play release pipelines, detekt with a custom user-facing-strings rule, conventional-commit hooks
- **Legal documents that stay true**: the privacy policy and terms are Markdown in `legal/`, next to the code they make claims about, and a workflow publishes them to the studio site by pull request. Keeping them in a marketing folder is how an app ends up telling people it serves no ads while it serves ads
- An **integration harness** that drives the real client stack against the real server over a real Postgres, in a unit test
- Docs that assume nothing: `SETUP.md` runbook from init to first release, practice guides for testing, observability, app events, and outboxes

## Build & Run

```shell
# Android
./gradlew :apps:compose:assembleDebug

# iOS - compile Kotlin framework
./gradlew :apps:compose:compileKotlinIosSimulatorArm64

# iOS - or open in Xcode
open apps/ios/iosApp.xcodeproj

# Server (boots in limited mode with zero config)
./gradlew :apps:server:run

# Server with a local Postgres
docker compose -f apps/server/docker-compose.yml up -d

# Everything the CI gate runs
./gradlew testDebugUnitTest :apps:server:test :apps:integration:testDebugUnitTest
```

### First-time setup

See **[SETUP.md](SETUP.md)** for the hour-1/day-1 runbook, Supabase project + auth providers, Fly dev/prod apps, GitHub secrets, Sentry/Grafana keys, store listings, and the first-release manual-promotion gotcha. Each step has the command and the expected output.

Before your first commit:

```shell
./scripts/install_hooks.sh   # installs the Conventional Commits + detekt hooks
```

## Project Structure

```
apps/compose/          # KMP entry point (Android + iOS)
apps/ios/              # Swift/Xcode wrapper
apps/server/           # Ktor + Postgres backend (Fly.io)
apps/admin/            # Kotlin/JS remote-config admin console
apps/integration/      # End-to-end harness (real client ↔ real server ↔ real DB)
features/<name>/       # Routes and public API
features/<name>/impl/  # Screens and ViewModels
libraries/<name>/      # Interfaces
libraries/<name>/impl/ # Implementations
```

Architecture rules (enforced at Gradle configuration time), the ViewModel/DI/navigation patterns, and every convention live in **[AGENTS.md](AGENTS.md)**. It's written for AI agents and humans alike and is the single source of truth for how code here is shaped.

## Doc map

| Doc | What it covers |
|---|---|
| [SETUP.md](SETUP.md) | Init → running app → first release, step by step |
| [AGENTS.md](AGENTS.md) | Architecture, conventions, auth model, sync, testing rules |
| [docs/practices/testing.md](docs/practices/testing.md) | Which layer catches which bug; fakes; the integration harness |
| [docs/practices/observability.md](docs/practices/observability.md) | The session_id pivot; finding one session across Sentry/Loki/Tempo |
| [docs/practices/app-events.md](docs/practices/app-events.md) | The structured-event registry + `logEvent` discipline |
| [docs/practices/outbox.md](docs/practices/outbox.md) | Offline writes that must not be lost |
| [apps/server/DEPLOY.md](apps/server/DEPLOY.md) | Fly.io two-environment deployment |
| [apps/admin/README.md](apps/admin/README.md) | The remote-config admin console |
| [docs/swift-kotlin-communication-patterns.md](docs/swift-kotlin-communication-patterns.md) | Exposing Kotlin to Swift and vice versa |

## This template learns from the apps built with it

Apps generated from here reach production before the template does. They hit the store review, the policy deadline, the R8 rule that only breaks at runtime, the Compose bug that only appears at 60fps with real data. That knowledge is worth more than anything written speculatively in this repo, and it only arrives if someone carries it back.

So the flow runs both ways. A generated app takes the scaffolding; when it learns something a brand-new app would want on day one, that goes in the template's own `docs/PORT-CANDIDATES.md`. Code, or just as often a one-line warning about a landmine that cost a day. Working in a generated app? Its AGENTS.md carries the rule. Working here? That file is the queue.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
