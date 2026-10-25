# Plan: Add a Ktor backend + Supabase auth to the template

Status: **mostly complete**, Phases 1–5 + the client identity library built and
verified; only Phase 6 polish (rename-tooling coverage) remains. Source of truth
for patterns: the `Cards` repo at `../Cards` (started from this template, then
grew a real KMP Ktor backend). This doc extracts that backend into a reusable,
genericized template slice.

## Progress

- [x] **Phase 1: Foundations + bootstrap**: `:apps:server` JVM module, catalog
  additions, `Main`/`Application` (`module` + `installApp` seam), `Env`/`ServerConfig`,
  `ServerScope`/`ServerComponent`, Serialization/Cors/Errors/Observability plugins,
  health + `/v1/example`. *Verified: compiles, tests pass, boots + serves endpoints.*
- [x] **Phase 2: Database**: Hikari + Flyway + Exposed, `V1__profiles.sql`,
  `ProfileRepository` + Postgres impl, Testcontainers tests, `docker-compose.yml`.
  DB optional (limited mode when unset). *Verified: Testcontainers tests pass.*
- [x] **Phase 3 (server): Supabase auth**: `JwtVerification` seam, `call.userId()`,
  `/v1/me`, `V2__fk_auth_users.sql` + `init-auth.sql`, route tests (HS256 + 401
  matrix) + `FullStackMeTest`. *Verified: tests pass; end-to-end boot applies V1+V2.*
- [x] **Phase 3 (client): identity library**: `:libraries:identity(:impl)`:
  supabase-kt sign-in (`AuthRepository`), `SupabaseAuthTokenProvider` replacing
  `NoOpAuthTokenProvider` (moved to the `:networking` api module so `replaces`
  respects the boundary), `SupabaseModule` provider. *Verified: `:apps:compose`
  Android compiles, including the KSP DI merge.*
- [x] **Phase 4: Observability**: env-gated Sentry + OpenTelemetry (traces/logs/
  metrics, stdout until OTLP endpoint set) + rate limiting. *Verified: compiles,
  tests pass, boots clean with the full stack.*
- [x] **Phase 5: Deploy**: Dockerfile + `.dockerignore` + `fly.toml` + `DEPLOY.md`
  + `serverOnly` slimming. *Verified: slimmed graph = only `:apps:server`;
  `installDist` produces the start script.*
- [~] **Phase 6: Standards/tooling**: `docs/decisions.md` added; AGENTS.md server
  pointer added. Remaining: confirm `init_project`/`rename_to_template` rewrite the
  server package + `fly.toml` app name; optional `create_module` server template.

### Pre-existing template bugs fixed along the way

The client app (`:apps:compose`) did not compile on `main`. Fixed: (1) the
`enforceModuleBoundaries` self-edge, (2) `NetworkClientImpl`'s `Logger.log`
expression body, (3) `AndroidActivityProvider`'s missing `@ContributesBinding`
`boundType`. The Android client now compiles. **iOS was not verified** and may
have its own pre-existing issues.

Docs note: detailed server docs live in [`apps/server/README.md`](../apps/server/README.md);
AGENTS.md carries only a short pointer (per request).

## Goal

Ship a **working, reference-grade** backend in `:apps:server` plus the
client-side Supabase auth plumbing, so that cloning the template gives you a
server that:

- boots and serves `/_health` with **zero** secrets configured,
- verifies Supabase-issued JWTs and serves an authed `GET /v1/me` profile,
- persists to Postgres via Flyway + Exposed with Testcontainers-backed tests,
- ships env-gated observability (Sentry + OpenTelemetry) that no-ops until
  configured,
- deploys to Fly.io via a documented runbook.

The overriding design principle (the reason to copy *these* patterns): **every
axis of variation has exactly one blessed shape, and the server mirrors the
client's conventions** (kotlin-inject DI, `domain/`↔`impl` split, `Catching {}`,
conventional commits, convention plugins). That symmetry is what lets an agent
move between client and server without re-learning.

## Locked scope decisions

| Decision | Choice |
|---|---|
| First-cut completeness | **Full vertical slice**, bootstrap + health + Supabase JWT auth + Postgres + a real `/v1/me` repository + Testcontainers tests. |
| Observability | **Full env-gated stack**, Sentry + OpenTelemetry (traces/logs/metrics) + logback + rate limits, all no-op / stdout-only until their env var is set. |
| Deployment | **Fly.io**, Dockerfile + `.dockerignore` + `fly.toml` + `DEPLOY.md` + `.env.example`. |

## What's already in place (this finishes started work)

- `settings.gradle.kts:38` already `include(":apps:server")`: a **dangling**
  reference (the dir doesn't exist; this likely fails `./gradlew` configuration
  today). Phase 1 fills it in.
- `gradle/libs.versions.toml` already declares `supabaseKt = "3.2.6"` (version
  only, no library entry yet) and the client-side Ktor + kotlin-inject + anvil
  + client Sentry stack.
- `libraries/networking` already ships the client auth seam: `AuthTokenProvider`
  + `NoOpAuthTokenProvider` (bound in `:networking:impl`) + a `NetworkClientImpl`
  that installs Ktor's `Auth { bearer { loadTokens/refreshTokens } }`. The real
  Supabase provider drops in via
  `@ContributesBinding(AppScope::class, replaces = [NoOpAuthTokenProvider::class])`.

## Target layout

Package root: **`com.opson.server`** (keep dir == package; do **not**
replicate the Cards `com/cards/server` dir vs `com.dangerfield.cards.server`
package mismatch).

```
apps/server/
  build.gradle.kts                         # kotlinJvm + kotlinSerialization + ksp + application (no convention plugin)
  Dockerfile  .dockerignore  fly.toml  DEPLOY.md  .env.example
  src/main/kotlin/com/opson/server/
    Main.kt                                # embeddedServer(Netty){ module(config) }
    Application.kt                         # module(config) + installApp(component, verification, adminConfig)
    config/Env.kt                          # OS-env -> .env file reader, injectable
    config/ServerConfig.kt                 # typed tree: Database, Supabase, Http, Sentry, Observability, Admin
    di/ServerScope.kt  di/ServerComponent.kt
    db/Database.kt  db/Tables.kt  db/JsonbColumn.kt  db/TimeConversions.kt
    plugins/Serialization.kt  plugins/Errors.kt  plugins/Cors.kt
    plugins/Authentication.kt  plugins/RateLimits.kt  plugins/WebSockets.kt
    plugins/Sentry.kt  plugins/Telemetry.kt  plugins/Tracing.kt
    plugins/Observability.kt  plugins/Metrics.kt
    http/ClientContext.kt
    domain/ProfileRepository.kt  domain/SupabaseAdminClient.kt
    data/PostgresProfileRepository.kt  data/HttpSupabaseAdminClient.kt
    routes/HealthRoutes.kt
    routes/MeRoutes.kt  routes/MeDto.kt
  src/main/resources/
    db/migration/V1__profiles.sql  db/migration/V2__fk_auth_users.sql
    logback.xml
  src/test/kotlin/com/opson/server/
    config/EnvTest.kt  config/ParsedPostgresUrlTest.kt
    db/DatabaseTest.kt  db/DatabaseSchemaTest.kt
    data/PostgresProfileRepositoryTest.kt
    routes/MeRoutesTest.kt  routes/HealthRoutesTest.kt
    FullStackMeTest.kt
  src/test/resources/init-auth.sql        # stub auth.users(id, is_anonymous) for FK in tests

libraries/identity/                        # NEW client auth library (interface)
  src/commonMain/.../AuthRepository.kt  SupabaseAuthGateway.kt
libraries/identity/impl/                    # NEW (impl)
  src/commonMain/.../SupabaseClientFactory.kt
  src/commonMain/.../RealSupabaseAuthGateway.kt
  src/commonMain/.../SupabaseAuthRepositoryImpl.kt
  src/commonMain/.../SupabaseAuthTokenProvider.kt   # replaces NoOpAuthTokenProvider
```

## Phased execution (PR-sized, dependency-ordered)

Each phase is independently reviewable and ends at a green verification gate.
Tests are written *with* their code, not deferred. Conventional-commit titles
given (release-please reads them).

### Phase 1: Foundations + bootstrap skeleton
`feat(server): scaffold Ktor server module with health + example resource`

- **Catalog**: add server entries: `ktor-server-{contentNegotiation,cors,
  statusPages,callLogging,callId,rateLimit,websockets,auth,authJwt}`,
  `ktor-client-{cio,mock}`. **Align Ktor to one version** across client + server
  (template is `3.3.3`, Cards `3.5.0`; pick one, keep all modules on the catalog
  ref, Cards documents a `NoSuchMethodError` from skew).
- **settings.gradle.kts**: adopt the `serverOnly` build-slimming block (gate
  client/feature modules behind `if (!serverOnly)`; keep `:apps:server` + the
  shared libs it needs always-included).
- **build-logic**: make the KMP convention plugins skip Android/iOS targets when
  `-Dopson.serverOnly=true` (mirrors Cards) so a server Docker build needs
  no mobile toolchain.
- **apps/server**: `build.gradle.kts`, `Main.kt`, `Application.kt`
  (`module` + `installApp` split, documented plugin order), `config/Env.kt`,
  `config/ServerConfig.kt` (with `HttpConfig` + one example sub-config),
  `di/{ServerScope,ServerComponent}.kt`, `plugins/{Serialization,Errors,Cors}.kt`
  (incl. the `ProblemResponse` envelope), `http/ClientContext.kt` (optional),
  `domain/ExampleSource.kt` + `data/InMemoryExampleSource.kt`,
  `routes/{HealthRoutes,ExampleRoutes}.kt` + `ExampleDto.kt`, `logback.xml`,
  `.env.example`.
- **Gate**: `./gradlew :apps:server:run` serves `{"ok":true}` at `/_health` and
  the in-memory `/v1/...` example, with no secrets. `./gradlew help` configures
  cleanly (dangling-include fixed). One `EnvTest`.

### Phase 2: Database layer
`feat(server): add Postgres + Flyway + Exposed persistence with Testcontainers tests`

- `config` → add `DatabaseConfig` + `ParsedPostgresUrl` (+ `ParsedPostgresUrlTest`).
- `db/{Database,Tables,JsonbColumn,TimeConversions}.kt` (Hikari fail-fast +
  Flyway-before-Exposed + suspend `transaction {}`).
- `V1__profiles.sql`, `domain/ProfileRepository` (+ sealed outcome types),
  `data/PostgresProfileRepository` (every method `database.transaction {}`,
  injected `Clock`, idempotency via `23505` catch, model on Cards'
  `PostgresWalletRepository`, the cleaner exemplar).
- Catalog: `postgres-jdbc`, `hikaricp`, `exposed-{core,jdbc,javaTime}`,
  `flyway-{core,postgres}`, `testcontainers-postgres`.
- Tests: `DatabaseTest` (Docker-aware `Assume`-skip, class-scoped container),
  `DatabaseSchemaTest`, `PostgresProfileRepositoryTest`.
- **Gate**: repo + schema tests pass under Docker; skip cleanly without it.

### Phase 3: Supabase auth vertical slice  ★ keystone
`feat: add Supabase JWT auth (server verification + client sign-in + /v1/me)`

- **Server**: `plugins/Authentication.kt` (the `JwtVerification` sealed seam:
  `Jwks` prod / `Static` test; `validate{}` requires UUID `sub`; JSON 401
  `challenge{}`; `ApplicationCall.userId()` / `isAnonymousUser()`). Add
  `SupabaseConfig` (issuer + JWKS URL derived from `SUPABASE_URL`). Catalog:
  `auth0-jwt`, `auth0-jwksRsa`.
- `routes/{MeRoutes,MeDto}.kt`: `GET /v1/me` get-or-create on the Postgres
  profile repo; `MeResponse.isAnonymous` mirrors the claim back.
- `V2__fk_auth_users.sql` (`user_id -> auth.users(id) ON DELETE CASCADE`) +
  `src/test/resources/init-auth.sql` stub + `seedAuthUser()` test helper.
- **Client**: new `:libraries:identity` (+ `:impl`) depending on
  `supabase-auth` (bump `supabaseKt` to match Cards): `SupabaseClientFactory`
  (`install(Auth){}`, default autoRefresh/autoLoad), `SupabaseAuthGateway` +
  `RealSupabaseAuthGateway`, a slim `SupabaseAuthRepositoryImpl`
  (resolve-session + `createGuestSession()` + sign-out; **no auto-account on
  launch**), and `SupabaseAuthTokenProvider`
  (`@ContributesBinding(replaces = [NoOpAuthTokenProvider::class])`). Wire into
  `settings.gradle.kts` + `apps/compose` DI.
- Tests: `MeRoutesTest` (HS256 `validJwt()` minter + `testVerifier` + the full
  401 matrix), `FullStackMeTest` (real DI + Postgres via the `installApp` seam +
  `JwtVerification.Static`).
- **Gate**: a request with a valid minted JWT gets a profile; the 401 matrix is
  green; full-stack test boots the real graph against Testcontainers.

### Phase 4: Observability scaffolding (env-gated)
`feat(server): add env-gated Sentry + OpenTelemetry observability`

- `plugins/{Sentry,Telemetry,Tracing,Observability,Metrics,RateLimits}.kt`.
  Sentry no-ops until `SENTRY_DSN`; OTel degrades to a **stdout exporter** until
  `OTEL_EXPORTER_OTLP_ENDPOINT` (one var flips traces+logs+metrics to OTLP
  together); logback STDOUT always works; rate limits always-on. Replace the
  Cards `anon_orphans` gauge with a generic example gauge.
- Wire the production-only installs into `module()` (outside `installApp`, so
  tests don't pay for them). Catalog: the OpenTelemetry set + `sentry` (JVM) +
  `logback` (move these out of hardcoded versions into the catalog).
- **Gate**: server still boots with none of these env vars set; `flyctl`-style
  stdout shows spans locally; a unit test using the in-memory span exporter
  asserts one span per request.

### Phase 5: Deployment & ops
`build(server): add Dockerfile, fly.toml, and deploy runbook`

- `Dockerfile` (2-stage temurin jdk→jre, `installDist`), `.dockerignore`
  (root-context un-ignore of only the server-consumed libs), `fly.toml` (health
  check on `/_health`, `force_https`, JVM heap tuning with rationale comments,
  `min_machines_running = 1`, get this right; Cards' prose is stale at `0`),
  `DEPLOY.md` (one-time setup → CI auto-deploy → secret rotation → prod cutover),
  `.env.example` finalized.
- **Gate**: `docker build` (or `fly deploy --remote-only`) produces a running
  image answering `/_health`. (CI workflow wiring can be a follow-up.)

### Phase 6: Standards docs & tooling (highest leverage)
`docs: document backend conventions; scaffold server modules`

- **Write the backend section of `AGENTS.md`** (currently zero server guidance),
  see "Standards to encode" below.
- Add `docs/decisions.md` (append-only ADR log; Cards has one, template doesn't)
  seeded with the server architecture calls.
- Extend `scripts/create_module.main.kts` to scaffold a `routes`/`domain`/`data`
  triple; ensure `scripts/rename_to_template.sh` + `init_project.main.kts`
  rewrite the server package + `fly.toml` app name.
- (Optional) Enrich `libraries/networking` with the Cards `NetworkCall` helpers
  (`authedCall/unauthedCall` + pre-flight `awaitAuthReady()`) + `RetryPolicy`,
  the client half of "every HTTP call has one shape." `awaitAuthReady` pairs
  naturally with the Phase-3 token provider.

## Version alignment (do in Phase 1)

| Dep | Template now | Cards | Action |
|---|---|---|---|
| Ktor | 3.3.3 | 3.5.0 | Pick one catalog version; all modules on the ref. |
| supabaseKt | 3.2.6 (version only) | 3.6.0 | Bump + add `supabase-auth` library entry. |
| Sentry (JVM), logback |, | hardcoded in build.gradle | Add to catalog (don't hardcode). |

## Standards to encode in AGENTS.md (Phase 6)

A "Server (`:apps:server`)" section covering, as blessed one-way patterns:

1. **Bootstrap**: `Main` → `module(config)` → `installApp(...)`; observability
   in `module()` only; the documented plugin install order.
2. **Config**: `Env` is the only env reader; `require()` = boot-critical,
   `int()/?:` = optional, nullable `[]` = degrade-to-503; `.env.example` is the
   contract.
3. **DI**: annotate impl `@ContributesBinding(ServerScope::class) @SingleIn
   @Inject`, expose an `abstract val` on `ServerComponent`. Done.
4. **domain/data split**: `domain/` pure interfaces + sealed outcomes; `data/`
   impls prefixed by backing store (`Postgres*`/`Http*`/`InMemory*`/`NoOp*`).
5. **Routes**: one `fun Route.xRoutes(deps)`; DTOs in `XxxDto.kt`
   (`*Response/*Request/*Dto`); `/v1/...` versioned, `/_health` un-versioned;
   auth via `authenticate(...) { call.userId() }`; sealed outcome → status via
   exhaustive `when`; one `ProblemResponse` envelope.
6. **DB**: SQL is source of truth, Exposed objects are projections kept honest by
   `DatabaseSchemaTest`; `V##__snake.sql` never edited once applied; repos are
   `database.transaction {}` + injected `Clock` + `23505`-catch idempotency.
7. **Auth**: server verifies JWTs (never holds a secret); `call.userId()` is the
   only identity source; admin = separate `X-Admin-Token`.
8. **Testing**: the 3-layer pyramid; fakes (`InMemory*` reused, inline
   `Fake*/Empty*/Stub*` with spy fields, forbidden methods `error()`);
   `FixedClock`/seeded `Random`; await-by-polling, never `Thread.sleep`.
9. **Graceful degradation is a law**: every external integration no-ops/503s
   when its env var is absent; the server boots with zero secrets.

## Risks & fixes to bake in

- **Dangling `:apps:server` include** may fail Gradle config today: verify
  `./gradlew help` before/after Phase 1.
- **Ktor/Supabase version skew**: align in Phase 1, no hardcoded module versions.
- Don't copy Cards' **dir/package mismatch** or its **stale "shared secret"
  catalog comment** (the server verifies via JWKS/ES256, not a shared secret).
- `fly.toml min_machines_running` = **1** (warm baseline); Cards' DEPLOY prose is
  stale at 0.

## Explicitly out of scope (document as advanced patterns, don't ship as code)

- Anonymous→claimed identity linking (Apple/Google/email) and orphan-account
  sweeps (install-id + TTL), deeply tied to game-account semantics. The
  anon-first + `is_anonymous` plumbing in Phase 3 is the reusable core.
- The `:apps:integration` real-client-vs-real-server harness: document the
  pattern; ship only when a client↔server feature warrants contract tests.
- `:libraries:config` remote-config + QA menu (server serves `/v1/app-config`),
  strong follow-up, not in this slice.
