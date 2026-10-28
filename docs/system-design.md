# Opson system design

Opson is a food logger with everything people expect from Cronometer or
MacroFactor, built so it is safe for people in or near eating disorder
recovery. Every nutrition number can be shown, softened, delayed or hidden, and
one policy object decides which, everywhere.

The product spec is the design handoff in [`docs/design/handoff/`](design/handoff/README.md).
This doc is the engineering contract: how the pieces fit, what each module
owns, the schema, the sync protocol and the API. Agents build against it, so a
change to anything here lands in the same commit as the code that needs it.

The review rubric is [`docs/practices/review-checklist.md`](practices/review-checklist.md).

---

## 1. Non-negotiables

These come from the handoff and override anything else, including this doc.

- **No screen formats a nutrition number.** Numbers go through
  `VisibilityPolicy` and render through `NutrientValue`. `HIDE` removes the
  label too.
- **The Blind leak test stays green.** Blind mode on every surface shows no
  calorie amount and no "N left".
- **The energy target can only go up** until a dietitian sets
  `GOAL_FLOOR_KCAL`. It is `null` with a `TODO(dietitian)`, on purpose.
- **No evening "calories left" notification**, and no setting for one.
- **A big day never lowers tomorrow.** Exercise adjustment never lowers it either.
- **Fasting** is off by default, offered only in Full and Sprint, 12:12 /
  14:10 / 16:8 only, no streaks, and logging food always ends a fast.
- **No manual exercise logging.**
- **Health data is user data.** Nutrition values never appear in logs, span
  attributes or Grafana panels at the user level. Dashboards read aggregate
  `analytics` views, and the per-person `support` views, which hold counts and
  choices and never what someone logged (section 9).

---

## 2. Shape of the system

```
 iOS / Android (Compose Multiplatform)
 ┌───────────────────────────────────────────────────────────────┐
 │ features: today · logger · foods · trends · settings · onboarding │
 │      │ ViewModels (SEAViewModel)                               │
 │      ▼                                                          │
 │ libraries:nutrition   VisibilityPolicy, presets, targets, math  │
 │ libraries:diary       repositories over Room, outbox, sync      │
 │ libraries:foods       food search / barcode / describe client   │
 │      │ Room (source of truth on device)  ── outbox ──┐          │
 └──────┼────────────────────────────────────────────────┼─────────┘
        │ reads                                          │ POST /v1/sync
        ▼                                                ▼
 ┌───────────────────────────── Ktor server (Fly.io) ────────────────┐
 │ /v1/sync  /v1/foods/*  /v1/describe  /v1/export  /v1/me            │
 │ Exposed + Flyway                                                   │
 └───────────────────────────────┬───────────────────────────────────┘
                                 ▼
                 Supabase Postgres (+ Supabase Auth)
                 public.*        user data, food catalog
                 analytics.*     aggregate views for Grafana
```

**Local-first.** The diary lives in Room on the device. Every write lands in
Room and an outbox row in one transaction, so the UI is instant and logging
works with no signal (a grocery store, a restaurant basement). The outbox drains
to `/v1/sync`, and the same call pulls everything that changed elsewhere.
Postgres is the durable, cross-device copy and the source for dashboards.

**Why not have the client talk to Supabase directly.** The template and Cards
both route data through the Ktor server and keep Supabase to auth. That keeps
one place for validation and idempotency, lets RLS be deny-all, and means a
schema change never has to be coordinated with app review.

---

## 3. Modules

New modules follow the AGENTS.md rules (only `:apps:*` depend on `*:impl`,
feature api modules never depend on each other).

| Module | Plugin | Owns |
|---|---|---|
| `:libraries:nutrition` | kotlin.multiplatform | `Metric`, `Visibility`, `ModePreset`, `Presets`, `VisibilityPolicy`, `Display`, soften words, formatting, `Targets`, floor rule, exercise adjustment, EMA weight trend, unit conversion, and the screen helpers in `docs/plans/screens/shared.md` (`NutrientAmounts`, `GoalStyle`, `resolveRange`, `macroLine`, `ringValue`, `share`, ring modes, `NumbersChange`, micronutrients and scores, water glasses). Pure Kotlin, no I/O. |
| `:libraries:nutrition:ui` | compose.multiplatform | `NutrientValue`, `NutrientText`, `NutrientBar`, `NutrientRingLabel`, `MacroLine`, `LocalVisibilityPolicy`, test tags, and `LeakSurface` for the leak test. The only Compose code that touches a `Display`. |
| `:libraries:diary` | kotlin.multiplatform | Room entities and DAOs, repository interfaces (`DiaryRepository`, `WeightRepository`, `CustomFoodRepository`, `SavedMealRepository`, `SettingsRepository`), domain models (`LogItem`, `Day`, `MealSlot`, `Precision`, `Feel`), and `LocalCalendar` (today in the user's zone). |
| `:libraries:diary:impl` | kotlin.multiplatform | Repository impls, the outbox, `DiarySyncer` (a `UserScopedSyncer`), the sync API client. |
| `:libraries:diary:ui` | compose.multiplatform | What every diary screen draws the same way: meal slot icons, tile colours and labels, precision and feeling labels, `DiaryItemRow`, `FoodPickRow`, `FoodIconTile`, `FavoriteFoodTile`, `NutrientTotalColumn`, `MealCheckChip`, `NutrientTile`, `FoodRowsCard`. Strings in `strings_diary.xml`. |
| `:libraries:foods` | kotlin.multiplatform | `FoodCatalog` interface (search, barcode, detail, describe), `Food`, `Portion`, `NutrientProfile`, `FoodRef`, the amount math (`Food.toLogDraft`, `PortionAmount`, `AmountUnit`), and the `food_cache` entity and DAO (here, not in the impl, so `AppDatabase` can register them). |
| `:libraries:foods:impl` | kotlin.multiplatform | Ktor client calls to `/v1/foods/*` over that cache of foods the user has touched, so favorites and recents work offline. |
| `:libraries:health` | kotlin.multiplatform | `HealthRepository` (connect, disconnect, status, today's energy), `EnergyExpended`, `HealthSource`, and the `release.health` flag. Health values stay on the device: read on demand, never stored or synced (`docs/plans/screens/integrations.md`). |
| `:libraries:health:impl` | kotlin.multiplatform | `RealHealthRepository` over a platform `HealthReader`: Health Connect (`androidMain`, with the permission rationale activity and manifest entries) and HealthKit through `platform.HealthKit` (`iosMain`, no Swift). Emits `health.*` app events with the source only. |
| `:libraries:scanner` (+`impl`) | compose.multiplatform | The barcode camera behind `BarcodeCamera` (the composable viewfinder and permission prompt) and `CameraPermissions`. The impl binds CameraX with ML Kit's bundled model on Android and AVFoundation, from Kotlin, on iOS. 0.2, behind `release.barcode`. |
| `:features:today` (+`impl`) | feature | Today screen, Add menu, Day menu, calendar sheet, meal sheet, water sheet, day close. |
| `:features:logger` (+`impl`) | feature | Logger sheet, amount sheet, Describe, Saved. |
| `:features:foods` (+`impl`) | feature | Foods tab, create a meal, add a custom food. |
| `:features:trends` (+`impl`) | feature | Trends, trends settings, nutrients detail, weight. |
| `:features:settings` (+`impl`) | feature | More, modes and numbers (+ friction sheet), targets, water, fasting, reminders, health, support. |
| `:features:onboarding` | existing | Gains the four questions and the mode recommendation. |
| `:features:home` | existing | The template's feedback and bug report screens. Its Home landing screen is gone; the shell lives in `apps/compose` (see section 10). |

Room entities from `:libraries:diary` and `:libraries:foods` register in the one
`AppDatabase` in `:libraries:storage:impl`, which depends on both. Every
user-scoped DAO joins the `ClearableDao` multibinding so a user switch wipes it.

---

## 4. Domain: `:libraries:nutrition`

The prototype's logic block (`docs/design/handoff/phone-logic.js`, functions
`vis`, `revealed`, `nv`, `format`, `soften`, `score`, `setGoal`) is the
executable spec. Port it exactly, with tests for every branch.

```kotlin
enum class Metric(val key: String) { Calories, Remaining, EnergyOut, Protein, Carbs, Fat, Fiber, Micros, Weight, Water }
enum class Visibility(val score: Int) { Show(3), Soften(2), Delay(1), Hide(0) }
enum class TodayLayout { Full, Meals, Rhythm, Explorer }
enum class RevealOn { DayClose, Week }
enum class Surface { Today, Logger, DayClose, Week, Nutrients, Trends, Foods, Settings, Weight }

data class ModePreset(
    val id: PresetId, val today: TodayLayout, val vis: Map<Metric, Visibility>,
    val revealOn: RevealOn? = null, val mealCount: Boolean = false, val fastingAllowed: Boolean = false,
    val banner: Boolean = false,                   // copy lives in the UI, keyed by id
)
enum class PresetId(val key: String) { Full, Delayed, Blind, Nutrient, Rhythm, Explorer, Mindful, Fuel, Sprint, Coast }

sealed interface Display {
    data object Hidden : Display
    data class Soft(val text: String?, val fraction: Float?) : Display   // text is null for water (bar only)
    data class Shown(val text: String, val fraction: Float? = null) : Display
}   // fraction = value / goal, unclamped, null when there is no goal; drives fill bars

class VisibilityPolicy(settings: NumbersSettings, clock: Clock, timeZone: TimeZone,
                       formatter: NutritionFormatter = EnglishNutritionFormatter) {
    fun level(metric: Metric): Visibility          // overrides[m] ?: preset.vis[m], Delay NOT resolved
    fun effectiveLevel(metric: Metric, surface: Surface, context: FormatContext = FormatContext()): Visibility
    fun isRevealed(surface: Surface, context: FormatContext = FormatContext()): Boolean
    fun resolve(metric: Metric, value: Double?, surface: Surface, context: FormatContext = FormatContext()): Display
    fun score(): Int                               // sum of Visibility.score over metrics
    fun isRaise(next: NumbersSettings): Boolean    // friction trigger
}
data class FormatContext(val of: Double? = null, val bare: Boolean = false, val goal: GoalStyle = GoalStyle.Default,
                         val unit: WaterUnit = WaterUnit.Ml,
                         val weightUnit: WeightUnit = WeightUnit.Kg,
                         val day: LocalDate? = null, val dayClosed: Boolean = false,
                         val eaten: Double? = null, val min: Double? = null, val max: Double? = null)
```

The policy picks soften words as `SoftWord` values and a `NutritionFormatter`
spells them and every shown amount, so copy can be localized without touching
a rule. `EnglishNutritionFormatter` is the prototype's strings, with
thousands separators done by hand so the JVM and iOS agree.

Rules to port, each with a test:

- Resolution: `overrides[m] ?: preset.vis[m]`. Picking a preset clears overrides.
- Water is `Soften` in every preset except Full and Sprint.
- `Delay` resolves to shown when the surface is `Week`, or `DayClose`, or the
  day is closed, or the day is in the past, or it is after 9:00 pm local. Coast
  (`RevealOn.Week`) only reveals on `Week`.
- Soften words, exactly as the prototype (calories: Early in the day / Partway
  through / Most of the way / In your range / A bigger day; macros: Building /
  Getting there / Plenty today; micros: Some / Most / Plenty; weight: Trend
  only; water: no text, bar only).
- `Remaining` is "On target" inside the range, "Above your range today" over
  it, otherwise "N left". It never says "0 left", and with no range passed a
  remainder that rounds to zero or below is "On target" too.
- `Remaining` and `EnergyOut` have no soften word, so softened they hide. (The
  prototype falls through to the macro words, and "Plenty today" for energy
  expended reads as praise for exercise.)
- Formatting: `1,245` bare, `1,245 kcal`, `47 g`, `47 g of 120 g`, `72.6 kg`,
  `62%`, `750 of 2,000 ml`, fl oz at 29.57 ml.
- `Targets.raise/lower` in 50 kcal steps; `lower` refuses to go under
  `GOAL_FLOOR_KCAL ?: startingTarget`.
- Exercise adjustment `All / Some (half) / None` adds movement plus workout to
  today only and never changes tomorrow's base.
- Weight trend: EMA with alpha 0.2.

`NumbersSettings` is the subset of settings the policy reads: `preset`,
`overrides`, `pauseBeforeMore`, `changedAt`.

---

## 5. Data model

Every user row carries a client-generated UUID, `client_updated_at` (the
device clock when the user made the change, used for last-write-wins) and
`deleted` (a tombstone, so deletes sync). Postgres adds `server_seq`, the pull
cursor.

### Postgres (`public` schema, Flyway in `apps/server/src/main/resources/db/migration`)

```sql
-- Catalog, shared by everyone: USDA FoodData Central (generic + Branded), NEVO, Open Food Facts, later paid sources
foods (
  id uuid pk, source text check (source in ('usda_foundation','usda_sr','usda_fndds','usda_branded','nutritionix','off','nevo')),
  source_id text, name text, brand text null, barcode text null unique,
  kcal_100g real, protein_100g real, carbs_100g real, fat_100g real, fiber_100g real,  -- nullable when unknown
  micros jsonb not null default '{}',          -- nutrient key -> amount per 100 g
  portions jsonb not null default '[]',        -- [{label:'1 cup', grams:240}]
  search tsvector generated always as (to_tsvector('simple', name || ' ' || coalesce(brand,''))) stored,
  popularity int not null default 0,
  tier smallint not null,                      -- 1 reference (USDA generic, NEVO), 2 label (USDA Branded), 3 licensed, 4 community (OFF)
  verified boolean not null,                   -- true for tiers 1-3
  source_version text null,                    -- the release a row came from; NEVO's "2025/9.0"
  unique (source, source_id))
  -- GIN index on search, trigram index on lower(name) for prefix and typo tolerance.
  -- A trigger sets tier and verified from source (food_source_tier()), so loaders never write them.
  -- Branded rows store the barcode as GTIN-14 and upsert on it; OFF rows keep the code as scanned.

-- Everything below has user_id uuid not null references auth.users(id) on delete cascade,
-- client_updated_at timestamptz not null, server_seq bigint not null default nextval('sync_seq'),
-- deleted boolean not null default false, and RLS enabled with no policies.

log_entries (
  id uuid pk, user_id, day date, slot text,    -- breakfast|lunch|snack|dinner|uncategorized
  logged_at timestamptz,
  food_id uuid null references foods(id) on delete set null, custom_food_id uuid null,
  name text, amount_text text, quantity real null, unit text null, grams real null,
  precision text,                              -- weighed|estimate|named
  kcal real null, protein real null, carbs real null, fat real null, fiber real null,
  micros jsonb null,
  method text)                                 -- search|barcode|describe|saved|copy|ate_no_details|quick_number|recent|favorite|custom

day_records (user_id, day date, pk (user_id, day),
  water_ml int, closed_at timestamptz null, note text null,
  ate_no_details jsonb not null default '{}',  -- slot -> true
  meal_groups boolean null,                    -- null = follow settings
  untracked boolean not null default false,    -- a holiday: a gap in Trends, never a low day (V11)
  feeling text null)                           -- Day close's "How did today feel?": steady|busy|tired|good|hard (V11)

meal_feelings (user_id, day, slot, pk (user_id, day, slot),
  hunger text null, fullness text null, moods text[] not null default '{}', note text null)

weigh_ins (id uuid pk, user_id, measured_at timestamptz, kg real)

custom_foods (id uuid pk, user_id, name, brand null, serving_amount real, serving_unit text,
  kcal real null, protein real null, carbs real null, fat real null, fiber real null)

saved_meals (id uuid pk, user_id, name, usual_slot text null, items jsonb)   -- item snapshots

user_settings (user_id pk, settings jsonb, changed_at timestamptz null)       -- the whole Settings object

fasts (user_id, day date, pk (user_id, day),  -- 0.2 (V14); one fast per evening, keyed by the local day it began
  started_at timestamptz, ended_at timestamptz null,
  goal_hours smallint null check (goal_hours between 1 and 16))
  -- ended_at is null until someone ends it; reaching the goal is derived on the device, never written.
  -- A check refuses a fast longer than 16 hours.

exercises (id uuid pk, user_id, day date,     -- V24, release.exercise; docs/research/energy-expenditure.md
  started_at timestamptz, activity text, name text null, minutes smallint check (1 to 1440),
  kcal int check (0 to 5000), kcal_typed bool, not_on_watch bool)
  -- kcal is what the entry counts in Burned, worked out or typed on the device and stored as saved.

food_search_events (id bigserial, at timestamptz, query_norm text, result_count int, picked boolean)
  -- no user_id on purpose: it answers "which foods are we missing", not "what did this person search"

barcode_lookups (id bigserial, at timestamptz, barcode text, outcome text)   -- catalog|off|nutritionix|miss|error, no user_id
barcode_misses (id uuid pk, created_at, barcode, gtin null, country null, platform, app_version null)   -- V13, reported by the app after a 404, no user_id
```

The migrations are `V7__food_catalog.sql`, `V8__diary.sql`,
`V9__analytics.sql` and `V10__food_tiers.sql`; 0.2 adds `V13__barcode_misses.sql` and `V14__fasts.sql`, then `V24__exercises.sql`. Columns a row cannot exist without (ids, `day`, `slot`,
`name`, `precision`, `method`, `logged_at`, `measured_at`, `kg`, serving
amount and unit) are `not null`; everything else is nullable as written.
`food_id` is `on delete set null` so removing a catalog food never deletes a
logged meal (the entry keeps its own name and nutrient snapshot). pg_trgm is
created in the `extensions` schema, where Supabase keeps it.

Totals are computed, never stored. A stored total is a second source of truth
that drifts.

### Room (device)

The same tables minus `server_seq`, plus:

```
outbox (id uuid pk, entity text, entity_id text, payload text, base_payload text null,
        created_at, day text null)   -- unique (entity, entity_id)
sync_state (singleton: cursor bigint, last_success_at)
food_cache (id pk, json, search_text, barcode null, last_used_at null, favorite boolean)   -- in :libraries:foods
```

The outbox holds at most one change per row: a newer change replaces the
waiting one under a fresh id, so the server's ack for the old id cannot delete
it. `base_payload` is the row before the first waiting change (null for a new
row) and is what a rejection restores. `day` lets the drain hold back changes
to a day the server will not take yet (section 6).

Settings are not a Room table on the device. They are a `@Serializable`
`Settings` data class in a persistent `Cache` (the `AppData` pattern), mirrored
to `user_settings` through the same outbox.

---

## 6. Sync protocol

One endpoint, push and pull in one round trip. The response is authoritative,
so the client always calls it, even with an empty outbox. This is the Cards
inventory pattern.

```
POST /v1/sync
{ "cursor": 1234,
  "mutations": [ { "id": "<outbox uuid>", "entity": "log_entry", "row": { ...full row... } }, ... ],
  "accepts": ["fast", "exercise"] }               -- 0.2 clients; 0.1 clients leave it out

200
{ "applied":  ["<outbox uuid>", ...],
  "rejected": [ { "id": "<outbox uuid>", "code": "invalid_row", "message": "..." } ],
  "changes":  { "logEntries": [...], "dayRecords": [...], "mealFeelings": [...],
                "weighIns": [...], "customFoods": [...], "savedMeals": [...], "settings": {...} | null,
                "fasts": [...],                   -- only when the request accepts "fast"
                "exercises": [...] },             -- only when the request accepts "exercise"
  "cursor": 1301, "hasMore": false }
```

JSON is camelCase like the rest of the API, times are epoch milliseconds
(`clientUpdatedAtEpochMs`, `loggedAtEpochMs`, `measuredAtEpochMs`,
`closedAtEpochMs`, `changedAtEpochMs`) and days are ISO dates. `entity` is one
of `log_entry`, `day_record`, `meal_feeling`, `weigh_in`, `custom_food`,
`saved_meal`, `settings`, and from 0.2 `fast` (keyed by `day`) and `exercise`.
`accepts` lists the entities past the 0.1 set that a client reads from
`changes`; the server leaves the others out, so a 0.1 client gets the 0.1
response byte for byte. Row shapes are the `*RowDto` classes in
`apps/server/.../routes/SyncDto.kt`; pulled rows use the same shapes. The
settings row is `{ settings: {...}, changedAtEpochMs, clientUpdatedAtEpochMs }`.
Rejection codes are `unknown_entity` and `invalid_row`; a request with more
than 500 mutations is a 400 `too_many_mutations`.

- **State-based, not operation-based.** A mutation carries the whole row. The
  server upserts it when `row.clientUpdatedAtEpochMs` is strictly newer than
  what it has (last write wins), and ignores it otherwise. Replaying a
  mutation is harmless, which is what makes retries safe. An ignored mutation
  is still listed in `applied`, because the client should drop it.
- **One user syncs at a time.** Each request takes a per-user advisory lock
  inside one READ COMMITTED transaction. A sequence alone can commit seq 101
  before seq 100, and a pull by cursor would then skip 100 forever.
- **Idempotent by construction.** Row ids are client UUIDs. The outbox id is
  echoed back in `applied` so the client deletes exactly what landed.
- **Every write that changes a row bumps its `server_seq`.** A mutation ignored
  for being older does not. `changes` returns rows for this user with
  `server_seq > cursor`, capped at 500 per entity, and `has_more` asks the
  client to call again.
- **Client merge rule:** a pulled row replaces the local row unless the local
  row has a pending outbox mutation, in which case the local one wins and goes
  up on the next push.
- **Drain discipline from Cards:** page the outbox (200 per call, 10 calls per
  sync), judge progress by the outbox shrinking, and log a warning with the
  remaining count when a sync ends with rows still queued.
- **Triggers:** `DiarySyncer` implements `UserScopedSyncer`, so sync runs on
  sign-in, warm foreground and reconnect. A write also schedules a debounced
  sync (2 s) so another device sees it quickly.
- **Days ahead wait.** The server refuses a day more than two days past today
  (UTC) and Today lets the user plan six ahead, so a change to a later day
  stays in the outbox until its day comes into range, rather than being sent,
  refused and rolled back.
- **Whose settings.** After a user change the device holds default settings
  until that user's first pull lands (`SettingsRepository.isKnown`). A local
  settings change made in that window is not queued, because the whole default
  object would overwrite the user's real settings on the server; if the pull
  brings none (a new account), the local copy is queued then. The settings
  JSON repeats `preset` at the top level, the one key the server reads.

---

## 7. API

All under `/v1`, Supabase JWT required unless noted, DTOs in `routes/*Dto.kt`,
errors in the `ProblemResponse` envelope.

| Method | Path | Purpose |
|---|---|---|
| POST | `/v1/sync` | Push and pull, section 6 |
| GET | `/v1/foods/search?q=&limit=25` | Full text plus trigram search over `foods`, ranked by match then popularity. Logs one `food_search_events` row. Optional `category` (`restaurant`, `beverage`, `common`), `source` (`usda`, `off`), `sort` (`best`, `name`); without them the response is 0.1's. NEVO foods only with `credits=nevo`, which an app that shows RIVM's citation sends. |
| GET | `/v1/foods/search/options` | The `sources` and `categories` search takes here; `off` only with `FOOD_SEARCH_OFF_ENABLED`. |
| GET | `/v1/foods/{id}` | Detail with portions and micros |
| GET | `/v1/foods/barcode/{code}` | Catalog first, in any equivalent form of the code (UPC-A, EAN-13, GTIN-14, UPC-E), then each enabled outside source in tier order (Nutritionix when configured, Open Food Facts); cacheable answers are stored in `foods`. 404 `not_found` when nobody has it, 503 when no source could answer. |
| POST | `/v1/foods/barcode-misses` | `{id, barcode}` after a 404 from the barcode route. 204, idempotent on the app's id. Region, platform and version from the `X-*` headers, GTIN computed server-side, no user id stored. |
| POST | `/v1/describe` `{text}` | Parses "2 eggs and toast" into items: quantity + unit + food match, plus (0.2) the matched food and up to three alternatives. Heuristic, no AI: everyday words, restaurants, corrections and speech noise are handled by rules (`docs/plans/screens/describe.md`). |
| POST | `/v1/describe/photo` `{image, note?}` | 0.2, behind `release.photo`. A base64 meal photo (JPEG, PNG or WebP by its bytes, 4 MB) and an optional note go to Anthropic's Messages API (`ANTHROPIC_API_KEY`, model `ANTHROPIC_PHOTO_MODEL`, default `claude-opus-5-5`), which lists the foods; each is matched through the same pipeline as `/v1/describe` and returned in its shape. Per user 10 per 10 minutes and 40 a day (429). 503 `photo_estimate_unavailable` with no key, 502 `photo_estimate_failed` when the model fails. The photo is never stored or logged; the log line is outcome, counts and latency. |
| GET | `/v1/export.zip` | Everything the caller has: a CSV per kind of row, a daily summary, settings and a README. Local times in the phone's `X-Time-Zone` |
| GET | `/v1/export.csv` | 0.1 phones: every log entry, weigh-in and water day for the caller |
| GET/DELETE | `/v1/me` | Existing. Delete cascades through every table above. |

Search ranking rule: exact name match, then prefix, then full text rank, then
trigram similarity, with `popularity` (picks across all users) as the tiebreak.
Two rules sit above that. When the query names a product's brand (every word
of the brand, apart from filler like "Inc.", is in the query), that brand's
products come first: `cheerios`, `chobani greek yogurt`. Otherwise generic
(reference tier) foods come before branded ones, so `banana` still offers
"Banana, raw" first. Within a match tier, verified before unverified and lower
source tier first. Every food in a response carries `tier` and `verified`.

Two refinements, both forced by how USDA names foods ("Egg, whole, raw"):
a match on the name's first comma segment ranks right after exact, so `eggs`
finds eggs; and prefix means whole words, so `toast` does not rank "Toaster
pastries" first. Within a tier, names containing the query words whole, and
plain forms (`raw`, `NFS`, `plain`, `whole`, `brewed`, `cooked`), come before
similarity and popularity.

---

## 8. Food database

Our own catalog serves every search and every barcode. Sources are layered
by how far their numbers can be trusted (`foods.tier`):

1. **Reference: USDA FoodData Central generic foods**, public domain:
   Foundation, SR Legacy and FNDDS (survey foods "as eaten", which carry
   household portions like "1 cup"). About 13,000 foods with full
   micronutrients. Loaded by `scripts/import_usda_foods.py`, which downloads
   the CSV releases, normalizes nutrients per 100 g, keeps portions, and
   writes an idempotent upsert into `foods`. The output is committed.
2. **Label: USDA Branded Foods**, public domain: about 430,000 US packaged
   products with barcodes, from manufacturers' labels. Loaded by the same
   script with `--branded`, streamed into the database with `--psql`, and
   refreshed monthly by `.github/workflows/food-catalog-refresh.yml`. Keyed
   on the barcode (stored as GTIN-14) so a newer release updates products in
   place. Not committed.
3. **Licensed: paid sources, off until there is budget.** Nutritionix is a
   live barcode source, only bound when its keys are set, and its answers
   are cached only behind `NUTRITIONIX_CACHE_RESULTS` because its terms limit
   storage. NCCDB (University of Minnesota) would arrive as an importer; the
   slot is described in `apps/server/README.md`.
4. **Community: Open Food Facts** for barcodes we do not have, no key. The
   server looks up unknown codes at
   `world.openfoodfacts.org/api/v2/product/{code}.json` with a descriptive
   User-Agent, maps it, and caches it in `foods` with `source = 'off'`. Data
   is ODbL, so the Foods screen credits Open Food Facts. Later, foods people
   submit land here too.

Tiers 1 to 3 are `verified`. A barcode miss asks the enabled outside sources
in tier order; the first that knows the product answers.
- **Photo estimates** (0.2, `release.photo`, `docs/plans/screens/photo.md`)
  send the photo to Anthropic's vision model and match what it lists through
  the Describe pipeline. Every line is an estimate the person edits before
  anything is logged. The handoff puts it in the Plus tier; Plus has no price
  yet, so nothing gates it beyond the per-user limit.

---

## 9. Telemetry and dashboards

Long-term product data comes from Postgres, not Loki. Loki keeps 31 days and is
shared by every app on the stack. This is the Cards rule.

- A read-only Postgres role `grafana_ro` can read only the `analytics` and
  `support` schemas. `analytics` holds views that aggregate across users.
  `support` (`V22__support_views.sql`) holds one row per person or per person
  and day for the Users and User detail boards and the admin lookup: account,
  email, builds, counts and Settings choices, never a food, an amount, a weight,
  a note or an answer. No view in either exposes a single user's intake.
- Grafana datasource `opson-prod-db` / `opson-dev-db` (grafana-postgresql)
  through the Supabase session pooler.
- Dashboards live as JSON in `ops/grafana/`, and are pushed with the Grafana MCP
  or the import script.
- Views (`V9__analytics.sql`): `daily_active_loggers`,
  `entries_by_method_precision`, `meals_per_day_distribution`, `days_closed`,
  `preset_distribution`, `search_daily`, `top_zero_result_queries` (30 days,
  only queries searched at least 3 times), `barcode_outcomes_daily`,
  `weekly_retention_cohorts`, `retention_d1_d7_d30`, and from
  `V13__barcode_misses.sql` `barcode_hit_rate_daily`, `top_missed_barcodes`
  (30 days, grouped by GTIN) and `barcode_misses_by_country`. `DatabaseSchemaTest`
  fails if any of them grows a user id, a nutrient or a feeling column.
  `V11__untracked_days.sql` leaves untracked days out of
  `meals_per_day_distribution`; the activity views still count them, since
  logging on a holiday is still using the app.

| Dashboard | Panels (all aggregate) | Source |
|---|---|---|
| Opson Pulse | daily active loggers, entries per day, days closed, new accounts, sync errors | Postgres + Loki |
| Logging | entries by method, precision mix (weighed / estimate / named), "ate, no details" share, meals per day, time-of-day histogram | Postgres |
| Modes | active users by preset, override usage per metric, friction sheet shown vs completed, mode changes per week | Postgres + Loki events |
| Food catalog | searches per day, zero-result rate, top zero-result queries, barcode hit rate (catalog vs each outside source vs miss) | Postgres |
| Retention | weekly cohorts by first log, D1/D7/D30 | Postgres |
| Server | RED for `/v1/*` excluding `/_health`, p95 latency of `/v1/sync` and search | Loki / Tempo |

Client app events (`docs/practices/app-events.md`) cover what the database
cannot see: `logger.opened`, `logger.method_picked`, `friction.shown`,
`friction.completed`, `mode.changed`. Event attributes never carry amounts.

---

## 10. Navigation and shell

- `TabRoute`, `Router.switchTab`, `Router.enterTab` and `Router.batch` live in
  `:libraries:navigation`, ported from Cards. Tab roots take no arguments, and
  plain `navigate` of a `TabRoute` does not compile. `switchTab` pops to the
  bottom of the back stack, not the graph's start destination, because for a
  user who onboards this session the start destination is the onboarding
  screen and is no longer on the stack.
- The shell is in `apps/compose` (`AppShell.kt`, hosted by `App.kt`), as in
  Cards: the app module already owns the `NavHost`, and only it may see every
  tab. The bottom bar shows while the topmost *screen* is a tab root; a sheet
  or dialog over a tab keeps it.
- Tabs: `TodayRoute`, `TrendsRoute`, `FoodsRoute`, `MoreRoute` (the last in
  `:features:settings`). Onboarded users start on Today. The center + is not a
  tab. It opens `LoggerRoute(slot)` as a bottom sheet at
  `BottomSheetDefaults.FullHeightFraction` (93%) via `BottomSheet(heightFraction = ...)`.
  `slot` is the meal slot's string key.
- `LocalVisibilityPolicy` is provided once at the root from
  `VisibilityPolicySource` (`:libraries:nutrition`). Until the diary's
  `SettingsRepository` binds it, `FullNumbersPolicySource` in `apps/compose`
  serves the Full preset.
- Sheets (`bottomSheet<>`) for everything the handoff calls a sheet. Full
  screens only for Nutrients, Weight, Day close, Create a meal, Custom food and
  each More page.
- Composables never navigate. ViewModels emit events, entry points route.

---

## 11. Design system

Tokens from handoff section 5 go into `:libraries:ui`, replacing the template
theme.

- **Type:** Atkinson Hyperlegible Next (OFL), weights 400 to 800, bundled in
  `:libraries:resources`. Tabular figures on every number. Base 16 sp, large
  text 19 sp as a setting. The wordmark alone is Young Serif (OFL), also in
  `:libraries:resources`, as `BrandFontFamily`.
- **Logo:** the 4f bowl. Masters in `docs/design/brand/`; `OpsonLoader` and
  `OpsonLockup` in `:libraries:ui` draw it in the app, steam animated. On a dark
  ground it sits on a white disc.
- **Colour:** one scheme, Slate, in light and dark (design sync 4, 13.0).
  Appearance (System, Light or Dark) is the only colour setting. Nutrient
  colours tell nutrients apart and never mean over or under; energy out uses
  cool colours (resting and active) so it never looks like food. No red or
  green anywhere for amounts.
- **Shape:** cards 22, sheets 28 top, sticky Today block 24 bottom, chips pill.
- **Icons:** Material Symbols Rounded via the Compose Material icons extended
  Rounded set, reached only through `AppIcons` (named after the Symbols
  ligatures; `nutrition` and `barcode_scanner` have no Rounded twin and use
  the closest glyph).
- **Access:** `AppTheme(darkTheme, largeText)` at the root, fed from
  the user's Look and feel settings by `UserAppTheme` in `apps/compose`; tokens
  through `AppTheme.colors`, `AppTheme.type` (the handoff scale; the older
  `AppTheme.typography` stays for template screens), `AppTheme.shapes`,
  `AppTheme.spacing` and `AppTheme.shadows`. Every component and token renders
  in `DesignSystemGallery` (`:libraries:ui`, `catalog/`).

---

## 12. Testing

- `:libraries:nutrition`: exhaustive unit tests of the policy, one per rule in
  section 4. This is the most important test suite in the repo.
- ViewModels: action in, state and events out, hand-rolled fakes.
- Server: route tests with fakes, repository tests on Testcontainers, a
  full-stack sync test (push, pull, conflict, tombstone, replay).
- **`BlindModeLeakTest`** (Robolectric composition in `androidUnitTest`): set
  Blind, render every surface in `SURFACES`, assert no `metric:calories`,
  `metric:remaining` or `metric:energyOut` tags and no text matching
  `/\d[\d,.]*\s*(kcal|cal\b|calories)|\d[\d,]*\s*left\b/i`. It runs in CI.
  Detekt `HardcodedDisplay` keeps its fixtures honest: a `Display.Shown`,
  `Display.Soft`, `RingValue` or `MacroPart` built outside `:libraries:nutrition`
  (test source sets aside) fails the build.

---

## 13. Releases

**Design v3 (2026-10-26) supersedes the handoff** wherever they disagree. What
changed and what 0.1 builds is in [`docs/design/v3/CHANGES.md`](design/v3/CHANGES.md).

**0.1, the first App Store build.** Enough that a reviewer sees a complete app:

- Onboarding: four questions, recommended mode. No pricing screen (nothing is
  for sale yet, and a price with no purchase flow is a rejection).
- Today in every layout, with day navigation, calendar, day menu, meal sheet,
  water sheet, day close.
- Logger with search, basket, amount sheet, Describe it, Saved meals,
  Yesterday's meal, Ate no details, weigh-in.
- Foods: your foods, saved meals, recent, create a meal, custom food.
- Trends: intake, recap, nutrients, weight. Nutrients detail and Weight screens.
- More: Modes and numbers with friction, Targets, Water, Support (helpline,
  export, delete everything).

**0.2.** Barcode scanning (camera on both platforms), photo estimates,
fasting, reminders, Health Connect and HealthKit, voice notes.

**Later.** Trusted-person sharing, widgets, the Plus tier.

Anything not in a release is hidden, not shown as "Coming soon", except where
the handoff itself says "Coming later".

---

## 14. How the work is run

Each screen is built by an agent in two steps, and reviewed before merge:

1. **Plan.** Write `docs/plans/screens/<screen>.md` from the template in
   [`docs/plans/screens/TEMPLATE.md`](plans/screens/TEMPLATE.md): state,
   actions, events, components, every policy surface, copy, edge cases, tests.
   Check it against the review checklist.
2. **Build** to the plan, with tests and previews.
3. **Review** against the checklist, then merge.

The same applies to each server resource.

---

## 15. Decisions

Dated, newest first. Add to it, never rewrite it.

- **2026-10-26: we own the catalog, layered by trust, with paid sources
  behind flags.** A professional tracker answers from its own database; an
  outside API on every scan is slow, rate-limited and gone the day it changes
  terms. So USDA Branded Foods (about 430,000 products) joins the generic
  USDA foods in `foods`, refreshed monthly, and outside sources only fill
  gaps one product at a time, cached where their terms allow. Every food
  carries a tier (reference, label, licensed, community) and a `verified`
  flag, and search prefers verified, lower tiers within a match, generic
  foods for plain queries, and the brand when the query names one. Paid
  sources are code that waits for a key: Nutritionix is wired and bound only
  when `NUTRITIONIX_APP_ID` and `NUTRITIONIX_APP_KEY` are set, with caching
  off unless the contract allows it; NCCDB is a documented importer slot.
  This amends the 2026-10-25 "no keyed food API" decision only in that
  a keyed API can now be switched on; it still is not.

- **2026-10-25: the client holds back changes to days the server refuses.**
  `DiaryRules.MAX_DAYS_AHEAD` is 2 and Today allows 6. Holding them in the
  outbox keeps planned days on the device and sends them when they come into
  range; raising the server's limit to 7 would let them go at once.
- **2026-10-25: settings changes wait while the user's settings are unknown.**
  Last write wins on the whole settings object, so a default object pushed from
  a fresh device would erase the user's mode. See section 6.

- **2026-10-25: sync JSON is camelCase with epoch-millisecond times.** The
  first draft of §6 used snake_case keys. Every other Opson and Cards route is
  camelCase with `…EpochMs` times, and the client's `@Serializable` models
  then need no `@SerialName`. Enum values stay lowercase snake strings.

- **2026-10-25: local-first with a state-based outbox.** Logging has to work
  offline, and a row-level last-write-wins merge is simple enough to be
  correct. An operation log would let two devices both edit a meal, which
  nobody needs.
- **2026-10-25: one `/v1/sync` endpoint, not CRUD per entity.** Six entities
  times four verbs is 24 routes with the same idempotency logic. One endpoint
  keeps it in one place.
- **2026-10-25: dashboards read `analytics` views through a read-only role.**
  The data is about eating, for people who may be unwell. Grafana should never
  be able to show one person's day. *2026-10-27: it may show one person's
  account and counts, through `support`, and still never their day's food; see
  docs/decisions.md.*
- **2026-10-25: USDA plus Open Food Facts, no keyed food API.** Both are free
  and need no account, and USDA's FNDDS portions ("1 cup") match how people
  log.
- **2026-10-25: no pricing in 0.1.** The handoff's $3.99 Plus is a placeholder,
  and a price without a purchase flow is an App Review rejection.
- **2026-10-25: Pine is the default palette.** It is the handoff default. The
  user's last pick was Espresso, so palette is a setting and that is one tap.
  Superseded 2026-10-27: Slate is the only scheme (`docs/decisions.md`).
