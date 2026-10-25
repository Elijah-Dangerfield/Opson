# `:apps:server`: Ktor backend

A small, opinionated Ktor + Postgres backend with Supabase JWT auth. It mirrors
the client's conventions (kotlin-inject DI, `domain/`↔`data` split, one blessed
way to do each thing) so moving between client and server is the same mental
model. This README is the detailed reference; keep `AGENTS.md` minimal and point
here.

## Quick start

The server degrades gracefully, so you can run it with **zero config**:

```bash
./gradlew :apps:server:run            # boots in "limited mode": /_health + /v1/example
curl localhost:8080/_health           # {"ok":true}
curl localhost:8080/v1/example        # {"message":"…","items":[…]}
```

To enable the database-backed + authenticated routes, set env vars (copy
`apps/server/.env.example` → `apps/server/.env`, which is gitignored):

1. **Database**: start the bundled Postgres and point at it:
   ```bash
   docker compose -f apps/server/docker-compose.yml up -d
   # in apps/server/.env:
   DATABASE_URL=postgresql://postgres:postgres@localhost:5432/postgres
   ```
   On boot, Flyway applies everything in `src/main/resources/db/migration`.

2. **Supabase auth**: set `SUPABASE_URL=https://<project>.supabase.co` to mount
   `/v1/me`. The server verifies the client's Supabase JWTs against the project's
   public keys (JWKS); no secret is stored server-side.

Boot modes (graceful degradation is a deliberate standard):

| `DATABASE_URL` | `SUPABASE_URL` | What's served |
|---|---|---|
| unset | unset | `/_health`, `/v1/example` |
| set   | unset | + DB connected (no auth routes) |
| set   | set   | + `/v1/me` (full) |

## Layout

```
config/      Env (the only env reader) + ServerConfig (typed, parsed once)
di/          ServerScope + ServerComponent (kotlin-inject + anvil)
db/          Database (Hikari + Flyway + Exposed), Tables, TimeConversions
domain/      interfaces + models + sealed outcomes (no framework imports)
data/        impls, prefixed by backing store (InMemory*, Postgres*)
plugins/     Ktor plugins: Serialization, Errors, Cors, Observability, Authentication
routes/      one `fun Route.xRoutes(deps)` per resource + its DTO file
resources/db/migration/   Flyway V<n>__snake.sql (source of truth for the schema)
```

`Main.kt` parses config → `Application.module(config)` does production-only setup
(observability, DB connect, DI graph, JWT strategy) → `installApp(component,
verification)` installs the functional plugins + mounts routes. `installApp` is
the seam full-stack tests reuse (real graph, real DB, `JwtVerification.Static`).

## Conventions (copy these)

### Add a config value: `config/ServerConfig.kt`
`Env` is the only place env vars are read. Pick by criticality:
- boot-critical → `env.require("KEY")` (fail fast)
- optional w/ default → `env.int("KEY", 8080)` / `env["KEY"] ?: "x"`
- optional, degrades → nullable `env["KEY"]`, branch at the call site

Group related vars into a `data class XxxConfig` with a `fromEnv(env)` companion.
Document the var in `.env.example`.

### Add a service: `domain/` interface + `data/` impl
```kotlin
// domain/Thing.kt
interface ThingRepository { suspend fun get(id: UserId): Thing }

// data/PostgresThingRepository.kt
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class PostgresThingRepository(private val database: Database, private val clock: Clock) : ThingRepository
```
Then expose it on `ServerComponent` as `abstract val thingRepository: ThingRepository`.
anvil + KSP wire the rest. The impl prefix names the backing store
(`InMemory*`, `Postgres*`, `Http*`).

### Add a route: `routes/XxxRoutes.kt` + `XxxDto.kt`
```kotlin
fun Route.thingRoutes(repo: ThingRepository) {
    authenticate(SUPABASE_JWT_AUTH) {                 // omit for public routes
        get("/v1/thing") {
            val userId = call.userId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            call.respond(repo.get(userId).toResponse())  // respond with a DTO, never a domain type
        }
    }
}
```
- real paths are versioned under `/v1`; `/_health` is the deliberate exception.
- DTOs live in `XxxDto.kt`, named `*Response` / `*Request`; map with `Thing.toResponse()`.
- the caller's id is the JWT `sub` via `call.userId()`: never trust a body field.
- map domain outcomes to status codes with an exhaustive `when`; errors use the
  one `ProblemResponse` envelope (`call.respond(status, problem("code", "msg"))`).
- mount it in `installApp`.

### Add a migration: `resources/db/migration/V<n>__snake.sql`
Flyway SQL is the **source of truth** for the schema; the Exposed objects in
`db/Tables.kt` are read-side projections. Never edit an applied migration, add
the next one. Mirror schema changes into `Tables.kt` and add a line to
`DatabaseSchemaTest`. Repositories run every method in `database.transaction { }`,
take an injected `Clock`, and treat a unique-violation (SQLSTATE `23505`) as the
arbiter rather than pre-checking.

## Auth

The server is a pure resource server: it **verifies** Supabase JWTs, it never
issues them. `plugins/Authentication.kt` exposes a `JwtVerification` seam:
`Jwks` (production: ES256 via the project JWKS endpoint) and `Static` (tests:
a caller-supplied verifier, so tests mint HS256 tokens with no network). Inside
`authenticate(SUPABASE_JWT_AUTH) { }`, `call.userId()` is the `sub` claim and
`call.isAnonymousUser()` reflects Supabase's `is_anonymous` claim.

`profiles.user_id` references `auth.users(id)` (V2 FK, cascade delete). In
production `auth.users` is owned by Supabase; locally and in tests it's a minimal
stub (`docker/init-auth.sql` for compose, `src/test/resources/init-auth.sql` for
Testcontainers). Tests seed a row via `DatabaseTest.seedAuthUser()` before
creating dependent rows.

> The **client** half lives in `:libraries:identity(:impl)`. It signs in via
> supabase-kt (`AuthRepository.signInAnonymously()`) and binds a
> `SupabaseAuthTokenProvider` that supplies the bearer token to the network
> client, replacing the default `NoOpAuthTokenProvider`. Point it at your
> Supabase project by setting `supabase.projectId` / `supabase.anonKey` in
> `local.properties` (read via `:libraries:core` `SupabaseInfo`); until then it
> returns no token and requests go out unauthenticated.

## Testing

Three patterns, each with a copyable example:
- **Route test** (`routes/MeRoutesTest.kt`, `routes/ExampleRoutesTest.kt`):
  `testApplication` + the real plugins + a fake passed as a plain arg. Auth is
  faked by minting an HS256 token + `JwtVerification.Static`.
- **Repository test** (`data/PostgresProfileRepositoryTest.kt`): real Postgres
  via Testcontainers (`DatabaseTest`), `@After` table cleanup, injected clock.
  Skips cleanly (JUnit `Assume`) when Docker is absent.
- **Full-stack test** (`FullStackMeTest.kt`): the real DI graph + real Postgres
  through the `installApp` seam; proves auth + repo + route integrate.

```bash
./gradlew :apps:server:test           # add -Dopson.skipGitHooksCheck=true outside a hooked checkout
```

## Environment variables

| Var | Required | Default | Notes |
|---|---|---|---|
| `DATABASE_URL` | no |, | `postgresql://user:pass@host:port/db`. Unset → limited mode. URL-encode `$`→`%24`. |
| `DATABASE_POOL_MAX_SIZE` | no | 10 | |
| `DATABASE_POOL_MIN_IDLE` | no | 2 | |
| `SUPABASE_URL` | no |, | `https://<project>.supabase.co`. Unset → `/v1/me` not mounted. |
| `SERVER_HOST` | no | `0.0.0.0` | |
| `SERVER_PORT` | no | 8080 | |

## Roadmap (planned, not yet wired)

- Env-gated observability (Sentry + OpenTelemetry), no-op until configured.
- Containerized deploy (Dockerfile + Fly.io + `DEPLOY.md`).

See `docs/backend-and-supabase-auth-plan.md` for the full plan.
