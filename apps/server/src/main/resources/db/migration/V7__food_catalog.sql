-- ─────────────────────────────────────────────────────────────────────────────
-- V7: the shared food catalog, the sync cursor sequence, and the two
-- anonymous catalog event tables. See docs/system-design.md §5 and §8.
--
-- `foods` is shared by every user. It is loaded from USDA FoodData Central by
-- scripts/import_usda_foods.py (sources usda_foundation / usda_sr / usda_fndds)
-- and grows one Open Food Facts product at a time as barcodes are looked up
-- (source 'off'). (source, source_id) is the idempotency key for both loaders,
-- so re-running the import updates rows in place and keeps their ids.
--
-- Search needs three kinds of match (system-design §7): exact and prefix on the
-- name, full text over name + brand, and trigram similarity for typos. pg_trgm
-- provides the last one. It is created in the `extensions` schema because that
-- is where Supabase keeps extensions; the schema is created here so a plain
-- Postgres (docker compose, Testcontainers) looks the same. Every use of a
-- pg_trgm operator or function is schema-qualified, so nothing depends on the
-- connection's search_path.
--
-- `sync_seq` is the pull cursor for /v1/sync (§6). One sequence shared by every
-- user table, bumped by a trigger on every insert and update (V8), so "rows for
-- this user with server_seq > cursor" is one indexed range scan per table.
--
-- `food_search_events` and `barcode_lookups` have no user_id on purpose: they
-- answer "which foods are we missing", never "what did this person look for".
--
-- RLS is enabled with no policies on every table. The server connects as the
-- table owner and is unaffected; the policy-less RLS is what stops Supabase's
-- anon / authenticated roles reading these tables if a client is ever pointed
-- at PostgREST directly.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA extensions;

CREATE SEQUENCE IF NOT EXISTS public.sync_seq AS BIGINT;

-- Trigger function for every synced table (V8). SET search_path = '' because a
-- trigger function should never resolve names through the caller's path.
CREATE OR REPLACE FUNCTION public.bump_server_seq() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    NEW.server_seq := nextval('public.sync_seq');
    RETURN NEW;
END;
$$;

CREATE TABLE foods (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source       TEXT NOT NULL CHECK (source IN ('usda_foundation', 'usda_sr', 'usda_fndds', 'off')),
    source_id    TEXT NOT NULL,
    name         TEXT NOT NULL,
    brand        TEXT,
    barcode      TEXT UNIQUE,
    -- Per 100 g. Null means unknown, which is not the same as zero.
    kcal_100g    REAL,
    protein_100g REAL,
    carbs_100g   REAL,
    fat_100g     REAL,
    fiber_100g   REAL,
    -- nutrient key (unit in the key, e.g. sodium_mg) -> amount per 100 g
    micros       JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- [{"label": "1 cup", "grams": 240}]
    portions     JSONB NOT NULL DEFAULT '[]'::jsonb,
    search       TSVECTOR GENERATED ALWAYS AS (
                     to_tsvector('simple'::regconfig, name || ' ' || coalesce(brand, ''))
                 ) STORED,
    -- Picks across all users. A ranking tiebreak, never shown to anyone.
    popularity   INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT foods_source_source_id_uq UNIQUE (source, source_id)
);

CREATE INDEX foods_search_idx ON foods USING gin (search);
-- Serves both the trigram similarity operator and LIKE 'prefix%' on lower(name).
CREATE INDEX foods_name_trgm_idx ON foods USING gin (lower(name) extensions.gin_trgm_ops);

ALTER TABLE foods ENABLE ROW LEVEL SECURITY;

CREATE TABLE food_search_events (
    id           BIGSERIAL PRIMARY KEY,
    at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    query_norm   TEXT NOT NULL,
    result_count INTEGER NOT NULL,
    picked       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX food_search_events_at_idx ON food_search_events (at);

ALTER TABLE food_search_events ENABLE ROW LEVEL SECURITY;

-- One row per GET /v1/foods/barcode/{code}: where the answer came from.
-- Feeds the "barcode hit rate (catalog vs OFF vs miss)" panel (§9).
CREATE TABLE barcode_lookups (
    id      BIGSERIAL PRIMARY KEY,
    at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    barcode TEXT NOT NULL,
    outcome TEXT NOT NULL CHECK (outcome IN ('catalog', 'off', 'miss', 'error'))
);

CREATE INDEX barcode_lookups_at_idx ON barcode_lookups (at);

ALTER TABLE barcode_lookups ENABLE ROW LEVEL SECURITY;
