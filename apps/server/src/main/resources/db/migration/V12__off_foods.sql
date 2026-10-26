-- ─────────────────────────────────────────────────────────────────────────────
-- V12: Open Food Facts, in a table of its own (docs/food-data-sources.md,
-- docs/plans/food-data-import.md section 1).
--
-- Why its own table. Open Food Facts is ODbL, a share-alike licence. A
-- database built from it is a Derivative Database, and if we mixed its rows
-- into `foods` the share-alike terms could reach the USDA rows and the whole
-- merged catalog. So these rows live here and only here. Search may join the
-- two tables at query time; rows are never copied from one to the other.
-- (`foods` still has source 'off' for the one-product-at-a-time barcode cache
-- from V7. That is a separate question and this migration doesn't touch it.)
--
-- What a row is. One product per barcode from the monthly CSV export,
-- filtered as scripts/import_off_foods.py documents (energy and the three
-- macros per 100 g present and physically possible, an 8 to 14 digit barcode,
-- a name). Barcodes with a valid GS1 check digit are stored as 14-digit
-- GTINs, like Branded Foods, so a scan in any form finds them; the rest keep
-- the digits as published. Every row is tier 4 (community) and not verified,
-- and carries the export date in `source_version`. `countries_tags` is kept
-- for ranking ("en:france"), never for filtering.
--
-- The monthly full reload. The daily deltas don't record deletions, so each
-- month replaces the whole table. The importer never writes to `off_foods`:
--   1. It creates `off_foods_next` LIKE off_foods INCLUDING ALL EXCLUDING
--      INDEXES (columns, defaults, the generated tsvector, CHECKs), adds the
--      primary key and the unique barcode, enables RLS and copies this
--      table's grants and owner onto it.
--   2. It loads the export into `off_foods_next`, one committed batch at a
--      time. A dropped connection costs a batch; re-running resumes, because
--      every batch is an idempotent upsert on barcode.
--   3. It builds the search indexes on `off_foods_next`, copying their
--      definitions from this table (pg_indexes), so this file stays their
--      only definition. Building them once after the load is far cheaper than
--      maintaining three GIN indexes through 3 million inserts.
--   4. In one short transaction: rename off_foods (and its indexes) to
--      off_foods_old, rename off_foods_next (and its indexes) to off_foods
--      and the canonical index names below, drop off_foods_old. Renames are
--      catalog-only, so the exclusive lock is held for milliseconds and a
--      search sees either the old table or the new one, never a half-loaded
--      one. Index names come out identical every month, which is what keeps
--      the IF NOT EXISTS below a no-op on an already-loaded database.
-- RLS and grants are per table and LIKE copies neither, which is why step 1
-- sets them explicitly. The importer refuses to swap if this table ever gets
-- a policy, rather than silently dropping it. During a reload the database
-- holds both tables, so it needs room for about twice the table's size.
--
-- Idempotent on purpose. opson-dev got this table by running this exact file
-- through psql before the server's Flyway saw it.
-- Nothing was written to flyway_schema_history, so when a deploy runs V12 it
-- finds the table and indexes already there, does nothing, and records V12
-- as applied with this file's checksum. Hence CREATE ... IF NOT EXISTS
-- throughout; ENABLE ROW LEVEL SECURITY is a no-op when already on.
--
-- RLS is enabled with no policies, as on every table: the server connects as
-- the owner and is unaffected, and Supabase's anon / authenticated roles see
-- nothing if a client is ever pointed at PostgREST.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS off_foods (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    barcode          TEXT NOT NULL,
    name             TEXT NOT NULL,
    brand            TEXT,
    -- As printed on the pack: "400 g", "6 x 33 cl".
    quantity         TEXT,
    -- The label's serving as printed: "30 g", "1 biscuit (12 g)".
    serving          TEXT,
    -- Per 100 g (per 100 ml for drinks labelled that way). The importer only
    -- keeps products with all four; fiber may be unknown (null, not zero).
    kcal_100g       REAL NOT NULL,
    protein_100g     REAL NOT NULL,
    carbs_100g       REAL NOT NULL,
    fat_100g         REAL NOT NULL,
    fiber_100g       REAL,
    -- Same keys as foods.micros (unit in the key, e.g. sodium_mg), per 100 g.
    micros           JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- [{"label": "1 biscuit (12 g)", "grams": 12}]; grams is null for ml servings.
    portions         JSONB NOT NULL DEFAULT '[]'::jsonb,
    -- Open Food Facts country tags, e.g. {en:france,en:belgium}. Ranking only.
    countries_tags   TEXT[] NOT NULL DEFAULT '{}',
    source           TEXT NOT NULL DEFAULT 'off' CHECK (source = 'off'),
    -- The export date, YYYY-MM-DD.
    source_version   TEXT NOT NULL,
    tier             SMALLINT NOT NULL DEFAULT 4 CHECK (tier = 4),
    verified         BOOLEAN NOT NULL DEFAULT FALSE,
    -- When Open Food Facts last changed the product (its last_modified_t).
    last_modified_at TIMESTAMPTZ,
    -- Open Food Facts' unique_scans_n: how many people scanned the product.
    -- The only popularity signal 3 million products come with; 0 if unknown.
    scans            INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    search           TSVECTOR GENERATED ALWAYS AS (
                         to_tsvector('simple'::regconfig, name || ' ' || coalesce(brand, ''))
                     ) STORED,
    CONSTRAINT off_foods_barcode_key UNIQUE (barcode)
);

-- Index names must start with off_foods_: the reload's swap renames them by
-- that prefix.
CREATE INDEX IF NOT EXISTS off_foods_search_idx ON off_foods USING gin (search);
-- Serves both the trigram similarity operator and LIKE 'prefix%' on lower(name).
CREATE INDEX IF NOT EXISTS off_foods_name_trgm_idx ON off_foods USING gin (lower(name) extensions.gin_trgm_ops);
CREATE INDEX IF NOT EXISTS off_foods_countries_idx ON off_foods USING gin (countries_tags);

ALTER TABLE off_foods ENABLE ROW LEVEL SECURITY;
