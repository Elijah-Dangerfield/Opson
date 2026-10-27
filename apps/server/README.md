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
   the authenticated routes (`/v1/me`, `/v1/sync`, `/v1/foods/*`, `/v1/describe`,
   `/v1/export.csv`). The server verifies the client's Supabase JWTs against the project's
   public keys (JWKS); no secret is stored server-side.

Boot modes (graceful degradation is a deliberate standard):

| `DATABASE_URL` | `SUPABASE_URL` | What's served |
|---|---|---|
| unset | unset | `/_health`, `/v1/example` |
| set   | unset | + DB connected (no auth routes) |
| set   | set   | + `/v1/me`, `/v1/sync`, `/v1/foods/*`, `/v1/describe`, `/v1/export.csv` (full) |

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
- **Full-stack test** (`FullStackMeTest.kt`, `FullStackSyncTest.kt`): the real DI graph + real Postgres
  through the `installApp` seam; proves auth + repo + route integrate.

```bash
./gradlew :apps:server:test           # add -Dopson.skipGitHooksCheck=true outside a hooked checkout
```

## Opson routes

The food logger's API. The contract is `docs/system-design.md` §5 to §9; this
is the working summary. Everything needs a Supabase JWT. JSON is camelCase,
times are epoch milliseconds (`…EpochMs`), days are ISO dates, and errors are
the usual `ProblemResponse`.

| Method | Path | Notes |
|---|---|---|
| POST | `/v1/sync` | Push the outbox and pull changes in one call. 400 `too_many_mutations` (> 500), 400 `invalid_cursor`. |
| GET | `/v1/foods/search?q=&limit=25` | Ranked search, `limit` 1 to 50. 400 `invalid_query`. Records one anonymous `food_search_events` row. Optional `category` (`restaurant`, `beverage`, `common`), `source` (`usda`, `off`) and `sort` (`best`, `name`); 400 `invalid_category` / `invalid_source` / `invalid_sort`. NEVO foods only with `credits=nevo` (RIVM requires its citation, which 0.1 can't show). |
| GET | `/v1/foods/search/options` | `{sources, categories}` this server takes; `off` only when `FOOD_SEARCH_OFF_ENABLED` is on. |
| GET | `/v1/foods/{id}` | 400 `invalid_id`, 404 `not_found`. |
| GET | `/v1/foods/barcode/{code}` | Catalog (any of the code's UPC-A / EAN-13 / GTIN-14 / UPC-E forms), then each enabled outside source in tier order (5 s timeout each). 400 `invalid_barcode`, 404 `not_found`, 503 `barcode_lookup_unavailable` when no source could answer. 60/min per IP. |
| POST | `/v1/foods/barcode-misses` `{id, barcode}` | The app reports a code the barcode route answered 404. 204, idempotent on `id`. Region, platform and app version come from the `X-*` headers; no user id is stored. 400 `invalid_id`, `invalid_barcode`. 60/min per IP. Feeds `barcode_misses` (V13). |
| POST | `/v1/describe` `{text}` | Heuristic parse into items. 400 `invalid_text` (empty or > 1000 chars). |
| GET | `/v1/export.csv` | Streams the caller's entries, weigh-ins and water. |

### Sync (`SyncRoutes`, `PostgresSyncRepository`)

```
POST /v1/sync  { "cursor": 0, "mutations": [ { "id": "<outbox id>", "entity": "log_entry", "row": { … } } ] }
200 { "applied": [ids], "rejected": [{ "id", "code", "message" }],
      "changes": { "logEntries", "dayRecords", "mealFeelings", "weighIns", "customFoods", "savedMeals", "settings" },
      "cursor": 1301, "hasMore": false }
```

- `entity` is `log_entry`, `day_record`, `meal_feeling`, `weigh_in`,
  `custom_food`, `saved_meal` or `settings`. Row shapes are the `*RowDto`
  classes in `routes/SyncDto.kt`; pulled rows come back in the same shapes.
- Rejections carry a stable code: `unknown_entity`, or `invalid_row` (could
  not be decoded, or broke a rule in `domain/DiaryRules.kt`). Messages name
  the field and never echo the value. A rejection never blocks the rest.
- Last write wins on `clientUpdatedAtEpochMs`, strictly newer only, so a
  replay is a no-op and does not bump `server_seq`. An older write is still
  reported in `applied`: the server is done with it.
- One READ COMMITTED transaction per request, holding a per-user advisory
  lock, so a pull by cursor can never skip a row another device committed
  late. 500 rows per entity per response; follow `hasMore`.
- A log entry naming an unknown catalog food is kept with `foodId` null.
  The first sync of an entry that names a catalog food bumps its popularity.

### Food catalog

We own the catalog: every search and every barcode is answered from `foods`
first, and outside sources only fill gaps one product at a time. Rows come
from four tiers of source, best first (`foods.tier`, V10):

| Tier | Sources (`foods.source`) | How rows get there | `verified` |
|---|---|---|---|
| 1 reference | `usda_foundation`, `usda_sr`, `usda_fndds`, `nevo` | committed seed, `import_usda_foods.py`; NEVO by `import_nevo_foods.py` | true |
| 2 label | `usda_branded` | `import_usda_foods.py --branded`, monthly workflow | true |
| 3 licensed | `nutritionix` (later NCCDB) | barcode miss, only with keys and caching on | true |
| 4 community | `off` (later user submissions) | barcode miss, cached; the full export goes in its own table, `off_foods` | false |

A trigger fills `tier` and `verified` from `source`, so loaders never write
them; `food_source_tier()` is the one mapping, and `DatabaseSchemaTest`
checks it against `CatalogSource`.

**Search.** Match tiers, best first: exact name, head (the name's first comma
segment, "Egg, whole, raw" for `eggs`; also after a category word, "Fish,
salmon" for `salmon`; or the first two segments in any order, "Rice, brown"
for `brown rice`), whole-word prefix, full text, trigram similarity. Rules
above the match tier, in order: a match on the food's name beats a match only
through its brand (`brown rice` never leads with the brand "Rice Browns"); a
query that names a brand (the brand's words appear in the query as a run,
filler such as "Inc." aside, and the query has 4 or more characters) ranks
that brand first, so `cheerios` and `chobani greek yogurt` lead with the
product; otherwise generic (tier 1) foods come before branded ones; and
USDA's own restaurant rows ("CHICK-FIL-A, ...") follow plain foods unless
named. Trigram-only matches stay last, except a close typo of a generic food
when nothing generic matched (`avacado` finds "Avocado, raw" even though
products spell it that way). Within a match tier: verified first, lower
source tier first, whole-word matches, plain forms (`raw`, `NFS`, `whole`,
…), similarity, then popularity. Generic rows are read first through their
own partial indexes (V15); Branded rows are only read when they can reach the
results. pg_trgm lives in the `extensions` schema (as on Supabase) and every
use of it is schema-qualified.

**Relevance suite.** `SearchRelevanceTest` runs 78 queries a person types
(`src/test/resources/search-relevance.tsv`, with what each top 3 must hold)
against the generic seed plus a committed Branded fixture
(`search-relevance-branded.sql.gz`, made by
`scripts/search_relevance_fixture.py` from a database with the Branded load).
It prints the hit rate and every top 3, and fails under its floor. Any
ranking change answers to it. To run it against a full local copy instead,
set `SEARCH_RELEVANCE_JDBC_URL` (plus `_USER`, `_PASSWORD`); Flyway migrates
that database first, so never point it at a shared one.

**Barcodes.** A scan is looked up in every equivalent form of the code:
leading zeros never change a GTIN, so a UPC-A, its EAN-13 and its GTIN-14
match the same row, and an 8-digit code is also tried as a UPC-E expanded to
its UPC-A. Branded rows store the 14-digit form; Open Food Facts rows keep the
code as scanned. If both exist for one product, the better tier answers.
On a miss, `CatalogBarcodeLookup` asks each enabled `FoodSource` in tier
order (`ConfiguredLiveFoodSources`): Nutritionix when configured, then Open
Food Facts. The first that knows the product answers; a cacheable answer is
upserted into `foods`. Every lookup records `catalog`, `off`, `nutritionix`,
`miss` or `error` in `barcode_lookups` for the Food catalog dashboard.

**Loading USDA generic foods.** `scripts/import_usda_foods.py` (Python 3
stdlib only) downloads the Foundation, SR Legacy and FNDDS CSV releases into a
fresh temp directory, maps nutrients per 100 g and portions, dedupes names
across datasets, and writes `apps/server/fooddb/usda_foods.sql.gz`: batched
`INSERT … ON CONFLICT (source, source_id) DO UPDATE` with stable uuid5 ids, so
re-running it updates in place and never touches popularity. The generated
file is committed (about 1.5 MB, 13,324 foods).

```bash
python3 -I scripts/import_usda_foods.py                 # regenerate fooddb/usda_foods.sql.gz
gunzip -c apps/server/fooddb/usda_foods.sql.gz \
  | psql "$DATABASE_URL" --single-transaction -v ON_ERROR_STOP=1   # load it (after migrations)
```

Load it after the server has run its migrations once. `UsdaFoodSeedTest`
applies the committed file to a Testcontainers Postgres on every test run.

**Loading USDA Branded Foods.** `--branded` imports FoodData Central Branded
Foods: about 430,000 US packaged products with a barcode, from a 450 MB
release. It finds the newest release on the FDC download page (`--branded-url`
or `--archive` to pin one), reads the CSVs straight out of the zip without
extracting them, and keeps one record per barcode, the newest by
`available_date` then `modified_date`. Names and brands are ALL CAPS in the
data and are title-cased ("Campbell's Chunky Soup w/ Beef 18.8 oz"); records
with no energy and no macros, or impossible per-100 g values, are skipped.
The label's serving becomes the one portion. Grams only come from a serving
stated in grams: a serving stated in ml keeps its label ("1 can (355 ml)")
with no grams, and the client falls back to weighing. For those products the
nutrients are per 100 ml, as the label states them. Branded rows are keyed on
the barcode (`ON CONFLICT (barcode)`), because FDC gives a resubmitted
product a new `fdc_id`; the food id is derived from the barcode, so it
survives releases. A row is only rewritten when something in it changed, so
re-importing a release already loaded leaves the table as it was instead of
doubling it on disk until vacuum runs. With the 2026-04 release loaded,
`foods` takes about 345 MB including indexes (the generic foods alone are a
few percent of that): check the database plan's size limit before the first load.

The output is too big to commit. Write it to the gitignored
`apps/server/fooddb/usda_branded.sql.gz`, or stream it straight into a
database with `--psql`, which runs `psql` with `ON_ERROR_STOP` in one
transaction, prints progress, and ends with row counts by source and the
table size. With no value, `--psql` reads the connection string from
`CATALOG_DATABASE_URL`, and it hands it to psql through libpq environment
variables, so the password is never on a command line. It refuses to start
if the database has not run V10.

```bash
python3 -I scripts/import_usda_foods.py --branded       # write fooddb/usda_branded.sql.gz
CATALOG_DATABASE_URL=postgresql://… python3 -I scripts/import_usda_foods.py --branded --psql
python3 -I -m unittest discover -s scripts -p 'test_import_usda_foods.py'   # importer unit tests
```

`.github/workflows/food-catalog-refresh.yml` runs the same `--branded --psql`
import on the 3rd of every month (and on demand) against the
`CATALOG_DATABASE_URL` secret, and posts the counts to the job summary. With
the secret unset it skips with a notice.

**Loading Open Food Facts.** `scripts/import_off_foods.py` loads the
monthly CSV export (about 1.5 GB, 4.8 million products) into `off_foods`
(V12), a table of its own: Open Food Facts is ODbL, share-alike, so its rows
are never copied into `foods`. Search reads both tables once
`FOOD_SEARCH_OFF_ENABLED=true` (off by default): `OpenFoodFactsSearch` caps the
candidates of each kind of match before it scores them, so a query costs about
the same at 3.3 million rows as at 3,000, and works the rows in after generic
foods and Branded rows of the same match. The reload writes rows most-scanned
first, which is what makes the caps keep the popular products.
It keeps the 3.3 million products with energy and macros per 100 g that are
physically possible, an 8 to 14 digit barcode and a name, stores valid
barcodes as 14-digit GTINs like Branded Foods, and keeps `countries_tags` for
ranking. Every row is tier 4, not verified, with the export date in
`source_version`.

Each run is a full reload, because the export's deltas don't record
deletions. It loads `off_foods_next` (a copy of the table's definition, with
its RLS and grants) one committed batch at a time, builds the search indexes
from the live table's definitions, and swaps the two tables in one short
transaction, so search never sees a half-loaded table. A failed run resumes
when re-run; a failed swap is retried with `--swap-only`. It refuses to swap
in fewer than a million rows, or under 80% of the live table (`--allow-shrink`
overrides). While it runs the database holds both copies, so it needs about
twice the table's size free. Supabase switches a database to read-only when
its disk is 95% full, which is what a load too big for the disk ends in
(`cannot execute INSERT in a read-only transaction`). To recover, drop the
staging table from a session that opts back into writes, and the platform
lifts read-only within minutes:

```sql
SET SESSION CHARACTERISTICS AS TRANSACTION READ WRITE;
DROP TABLE IF EXISTS off_foods_next;
```

```bash
CATALOG_DATABASE_URL=postgresql://… python3 -I scripts/import_off_foods.py --psql   # download and reload
python3 -I scripts/import_off_foods.py --csv products.csv.gz --dry-run              # counts only
python3 -I scripts/bench_off_search.py                                              # search p50/p95/p99
python3 -I -m unittest discover -s scripts -p 'test_import_off_foods.py'
```

The monthly workflow has the same reload as a second job, off until the
`OFF_IMPORT_ENABLED` repository variable is `true`. Measured size (2.3 GB with
the V15 indexes), load time and search latency, before and after bounding, are
in `docs/food-data-sources.md`.

**Default servings.** The app logs a food's first portion, so the importer
puts a typical serving first (`default_portion_first`: a cup of blueberries,
not one berry; an ounce of almonds, not a cup). For rows already loaded:

```bash
CATALOG_DATABASE_URL=postgresql://… python3 -I scripts/backfill_default_portions.py --dry-run
CATALOG_DATABASE_URL=postgresql://… python3 -I scripts/backfill_default_portions.py   # idempotent
```

**Nutritionix (off until there is budget).** `NutritionixSource` calls
`GET https://trackapi.nutritionix.com/v2/search/item?upc=` with the
`x-app-id` / `x-app-key` headers and scales the per-serving answer to per
100 g. It is only bound when `NUTRITIONIX_APP_ID` and `NUTRITIONIX_APP_KEY`
are both set, and then goes before Open Food Facts. Nutritionix's API terms
limit storing their data, so by default answers are served once and not
cached (each such scan is a paid request). Set `NUTRITIONIX_CACHE_RESULTS=true`
only when the contract allows cached copies; they are stored as source
`nutritionix`, tier 3. To turn it on in production:

```bash
stty -echo; printf "Nutritionix app id: "; read -r NX_ID; printf "\nNutritionix app key: "; read -r NX_KEY; stty echo; printf "\n"
[ -n "$NX_ID" ] && [ -n "$NX_KEY" ] && fly secrets set NUTRITIONIX_APP_ID="$NX_ID" NUTRITIONIX_APP_KEY="$NX_KEY" -a opson-server-prod; unset NX_ID NX_KEY
```

**Photo estimates (`POST /v1/describe/photo`).** `PhotoDescriber` asks an
`EstimateProvider` what foods a meal photo holds, then matches each one
through `CatalogFoodDescriber`, so the answer is the same `DescribeResponse`
typed text gets. The provider is `AnthropicEstimateProvider` (the official
Java SDK, structured JSON output) when `ANTHROPIC_API_KEY` is set; without it
the route answers 503 `photo_estimate_unavailable` and the app shows a calm
"not available" state. Limits: body 6 MB, decoded image 4 MB (JPEG, PNG or
WebP by its bytes), note 300 characters, 10 per user per 10 minutes and 40 per
day (`PhotoEstimateLimiter`, in memory). The image is never stored and nothing
from it or the note is logged, only the outcome, item count and latency. Do
not set the SDK's `ANTHROPIC_LOG=debug` in production: it logs request
bodies, which here are meal photos. To turn it on:

```bash
stty -echo; printf "Anthropic API key: "; read -r AK; stty echo; printf "\n"
[ -n "$AK" ] && fly secrets set ANTHROPIC_API_KEY="$AK" -a opson-server-prod; unset AK
```

**NCCDB (a slot, not code).** The Nutrition Coordinating Center Food and
Nutrient Database from the University of Minnesota is licensed and delivered
as files, not an API, so it is not a `FoodSource`. When there is a licence,
its importer belongs beside the Branded import in
`scripts/import_usda_foods.py` (or a sibling script with the same safety
rules: fresh temp dir, untrusted archive, upsert keyed on `(source,
source_id)` with uuid5 ids). It needs a migration that adds `nccdb` to the
`foods.source` CHECK and maps it to tier 3 (licensed, verified) in
`food_source_tier()`, plus `CatalogSource.Nccdb`. Nothing parses its format
yet because we have not seen it.

Micros keys carry their unit: `sugars_g`, `added_sugars_g`, `saturated_fat_g`,
`trans_fat_g`, `cholesterol_mg`, `sodium_mg`, `potassium_mg`,
`calcium_mg`, `iron_mg`, `magnesium_mg`, `phosphorus_mg`, `zinc_mg`,
`copper_mg`, `manganese_mg`, `selenium_ug`, `vitamin_a_rae_ug`,
`vitamin_c_mg`, `vitamin_d_ug`, `vitamin_e_mg`, `vitamin_k_ug`,
`thiamin_mg`, `riboflavin_mg`, `niacin_mg`, `vitamin_b6_mg`,
`folate_dfe_ug`, `vitamin_b12_ug`, `choline_mg`.

### Website waitlist (`V16__waitlist.sql`)

The site's form posts here; the contract is in `docs/plans/website.md`.

| Method | Path | Notes |
|---|---|---|
| POST | `/v1/waitlist` `{email, source?, company?}` | No auth. 204 on a new address, a known one (any case) and a filled honeypot (`company`, then nothing is stored). 400 `{"error":"invalid_email"}`, also for a body that is not JSON or is over 4 KB. 429 `{"error":"rate_limited"}` past 5 per IP per 10 minutes or 50 per IP per day (`WaitlistLimiter`, in memory, keyed like the other per-IP limits on `Fly-Client-IP`). |
| GET | `/v1/admin/waitlist?limit=50&offset=0` | Admin token. Newest first, with `total`. |
| GET | `/v1/admin/waitlist/export.csv` | Admin token. `email,source,created_at`, oldest first. |
| POST | `/v1/admin/waitlist/remove` `{email}` | Admin token. Deletes the address in any case; 404 `not_found`. |

The email is stored lower-cased, unique on `lower(email)`, and inserted with
`ON CONFLICT DO NOTHING`, so the answer never says who is on the list. The
route logs the outcome only, never the address. Removal deletes the row,
because the site's privacy wording promises deletion; `unsubscribed_at` is
there for a future one-click unsubscribe, and rows with it set are left out of
the list, the total and the export. `analytics.waitlist_daily` (day, signups)
feeds the Pulse board and holds no emails. The admin console's Waitlist tab
uses these routes.

CORS for this route comes from `WAITLIST_ALLOWED_ORIGINS` and only this route
uses it: `plugins/Cors.kt` installs the API-wide policy on the routing root,
and `waitlistRoutes` installs its own on `/v1/waitlist`, which Ktor applies
instead for that path (preflight included). With the variable unset, dev
allows the Astro dev server and the GitHub Pages site and prod allows no
browser origin. To set it on prod (no secret, but `fly secrets` is how the
apps take env vars):

```bash
printf "Allowed origins, comma-separated [https://elijah-dangerfield.github.io]: "; read -r WO; WO="${WO:-https://elijah-dangerfield.github.io}"
fly secrets set WAITLIST_ALLOWED_ORIGINS="$WO" -a opson-server-prod; unset WO
```

### Analytics (`V9__analytics.sql`)

Views in the `analytics` schema aggregate across users and never select a
nutrient amount or a user id (`DatabaseSchemaTest` enforces both). The
`grafana_ro` role (NOLOGIN) can read that schema and nothing else. To connect
Grafana, create a login role outside migrations and grant it `grafana_ro`.
`V13__barcode_misses.sql` adds `barcode_hit_rate_daily`, `top_missed_barcodes`
and `barcode_misses_by_country` for the Food catalog board. `V16__waitlist.sql`
adds `waitlist_daily` for Pulse.

## Environment variables

| Var | Required | Default | Notes |
|---|---|---|---|
| `DATABASE_URL` | no |, | `postgresql://user:pass@host:port/db`. Unset → limited mode. URL-encode `$`→`%24`. |
| `DATABASE_POOL_MAX_SIZE` | no | 10 | |
| `DATABASE_POOL_MIN_IDLE` | no | 2 | |
| `SUPABASE_URL` | no |, | `https://<project>.supabase.co`. Unset → `/v1/me` not mounted. |
| `APPLE_TEAM_ID` | no |, | With `APPLE_KEY_ID` and `APPLE_PRIVATE_KEY`, lets `DELETE /v1/me` revoke the person's Sign in with Apple token, which App Review requires. Unset → `POST /v1/me/apple-authorization` answers 503 and deletion skips revocation with a warning. |
| `APPLE_KEY_ID` | no |, | The id of a Sign in with Apple key (Certificates, Identifiers & Profiles › Keys). |
| `APPLE_PRIVATE_KEY` | no |, | Secret. That key's `.p8` file contents; `\n` may stand for the newlines. |
| `APPLE_CLIENT_ID` | no | `com.opson.Opson` | The app's bundle id. |
| `SERVER_HOST` | no | `0.0.0.0` | |
| `SERVER_PORT` | no | 8080 | |
| `NUTRITIONIX_APP_ID` | no |, | With `NUTRITIONIX_APP_KEY`, turns on Nutritionix barcode lookups (tier 3, asked before Open Food Facts). |
| `NUTRITIONIX_APP_KEY` | no |, | Secret. Both keys or neither. |
| `NUTRITIONIX_CACHE_RESULTS` | no | `false` | `true` stores Nutritionix answers in `foods`. Only if the contract allows it. |
| `ANTHROPIC_API_KEY` | no |, | Secret. Turns on photo estimates. Unset → `/v1/describe/photo` answers 503. |
| `ANTHROPIC_PHOTO_MODEL` | no | `claude-opus-5-5` | The model that reads meal photos (effort `low`). A cheaper model is a config change, not a release. |
| `WAITLIST_ALLOWED_ORIGINS` | no | dev: `http://localhost:4321,https://elijah-dangerfield.github.io`; prod: none | Comma-separated exact origins (`scheme://host[:port]`) allowed to call `POST /v1/waitlist` from a browser. Only that route uses it. |

## Roadmap (planned, not yet wired)

- Env-gated observability (Sentry + OpenTelemetry), no-op until configured.
- Containerized deploy (Dockerfile + Fly.io + `DEPLOY.md`).

See `docs/backend-and-supabase-auth-plan.md` for the full plan.
