-- ─────────────────────────────────────────────────────────────────────────────
-- V8: the user's diary, the tables /v1/sync reads and writes
-- (docs/system-design.md §5 and §6).
--
-- Every table follows the same sync contract:
--  - the primary key is client-generated (a UUID, or the natural key for
--    per-day rows), so a retried push upserts instead of duplicating;
--  - client_updated_at is the device clock when the user made the change. The
--    server only overwrites a row when the incoming value is strictly newer
--    (last write wins), so replaying a mutation is a no-op;
--  - deleted is a tombstone. Rows are never hard-deleted by sync, otherwise a
--    delete could not reach the user's other devices;
--  - server_seq is bumped from sync_seq on every insert and update by the
--    bump_server_seq trigger (V7). It is the pull cursor and nothing else; it
--    never takes part in conflict resolution.
--
-- user_id references auth.users ON DELETE CASCADE, so deleting the account
-- (DELETE /v1/me) deletes the diary with it.
--
-- RLS is enabled with no policies, same reasoning as V7.
--
-- Totals are never stored. They are computed from log_entries, so there is no
-- second copy of the truth to drift.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE log_entries (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    day               DATE NOT NULL,
    slot              TEXT NOT NULL CHECK (slot IN ('breakfast', 'lunch', 'snack', 'dinner', 'uncategorized')),
    logged_at         TIMESTAMPTZ NOT NULL,
    -- A catalog food or one of the user's custom foods, or neither (described,
    -- quick number). The entry keeps its own name and nutrient snapshot either
    -- way, so editing or removing the source never rewrites history.
    food_id           UUID REFERENCES foods (id) ON DELETE SET NULL,
    custom_food_id    UUID,
    name              TEXT NOT NULL,
    amount_text       TEXT,
    quantity          REAL,
    unit              TEXT,
    grams             REAL,
    precision         TEXT NOT NULL CHECK (precision IN ('weighed', 'estimate', 'named')),
    kcal              REAL,
    protein           REAL,
    carbs             REAL,
    fat               REAL,
    fiber             REAL,
    micros            JSONB,
    method            TEXT NOT NULL CHECK (method IN (
                          'search', 'barcode', 'describe', 'saved', 'copy', 'ate_no_details',
                          'quick_number', 'recent', 'favorite', 'custom')),
    client_updated_at TIMESTAMPTZ NOT NULL,
    server_seq        BIGINT NOT NULL DEFAULT nextval('public.sync_seq'),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX log_entries_user_seq_idx ON log_entries (user_id, server_seq);
CREATE INDEX log_entries_user_day_idx ON log_entries (user_id, day);

CREATE TABLE day_records (
    user_id           UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    day               DATE NOT NULL,
    water_ml          INTEGER,
    closed_at         TIMESTAMPTZ,
    note              TEXT,
    -- slot -> true, for "ate, no details"
    ate_no_details    JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- null = follow the user's setting
    meal_groups       BOOLEAN,
    client_updated_at TIMESTAMPTZ NOT NULL,
    server_seq        BIGINT NOT NULL DEFAULT nextval('public.sync_seq'),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, day)
);

CREATE INDEX day_records_user_seq_idx ON day_records (user_id, server_seq);

CREATE TABLE meal_feelings (
    user_id           UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    day               DATE NOT NULL,
    slot              TEXT NOT NULL CHECK (slot IN ('breakfast', 'lunch', 'snack', 'dinner', 'uncategorized')),
    hunger            TEXT,
    fullness          TEXT,
    moods             TEXT[] NOT NULL DEFAULT '{}',
    note              TEXT,
    client_updated_at TIMESTAMPTZ NOT NULL,
    server_seq        BIGINT NOT NULL DEFAULT nextval('public.sync_seq'),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, day, slot)
);

CREATE INDEX meal_feelings_user_seq_idx ON meal_feelings (user_id, server_seq);

CREATE TABLE weigh_ins (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    measured_at       TIMESTAMPTZ NOT NULL,
    kg                REAL NOT NULL,
    client_updated_at TIMESTAMPTZ NOT NULL,
    server_seq        BIGINT NOT NULL DEFAULT nextval('public.sync_seq'),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX weigh_ins_user_seq_idx ON weigh_ins (user_id, server_seq);

CREATE TABLE custom_foods (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    name              TEXT NOT NULL,
    brand             TEXT,
    serving_amount    REAL NOT NULL,
    serving_unit      TEXT NOT NULL,
    kcal              REAL,
    protein           REAL,
    carbs             REAL,
    fat               REAL,
    fiber             REAL,
    client_updated_at TIMESTAMPTZ NOT NULL,
    server_seq        BIGINT NOT NULL DEFAULT nextval('public.sync_seq'),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX custom_foods_user_seq_idx ON custom_foods (user_id, server_seq);

CREATE TABLE saved_meals (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    name              TEXT NOT NULL,
    usual_slot        TEXT CHECK (usual_slot IN ('breakfast', 'lunch', 'snack', 'dinner', 'uncategorized')),
    -- Item snapshots, opaque to the server beyond being a JSON array.
    items             JSONB NOT NULL DEFAULT '[]'::jsonb,
    client_updated_at TIMESTAMPTZ NOT NULL,
    server_seq        BIGINT NOT NULL DEFAULT nextval('public.sync_seq'),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX saved_meals_user_seq_idx ON saved_meals (user_id, server_seq);

-- The whole client Settings object as one document. The server reads exactly
-- one key, settings->>'preset', and only in an aggregate view (V9).
CREATE TABLE user_settings (
    user_id           UUID PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
    settings          JSONB NOT NULL DEFAULT '{}'::jsonb,
    changed_at        TIMESTAMPTZ,
    client_updated_at TIMESTAMPTZ NOT NULL,
    server_seq        BIGINT NOT NULL DEFAULT nextval('public.sync_seq'),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX user_settings_user_seq_idx ON user_settings (user_id, server_seq);

-- Wire the cursor trigger and RLS onto every synced table.
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'log_entries', 'day_records', 'meal_feelings', 'weigh_ins',
        'custom_foods', 'saved_meals', 'user_settings'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE INSERT OR UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.bump_server_seq()',
            t || '_bump_server_seq', t
        );
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
    END LOOP;
END;
$$;
