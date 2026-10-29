-- ─────────────────────────────────────────────────────────────────────────────
-- V29: reports about a food's data, and the corrections reviewers make.
--
-- Anyone can say a catalog food is wrong from its detail ("Report a
-- problem"): what is wrong, optionally the numbers they read on the pack,
-- a photo of its label and a note. `food_reports` keeps that, with the food
-- as they saw it (`snapshot`), and a report never changes the food by
-- itself. A reviewer in the admin console accepts it (a correction), rejects
-- it, or marks the food a duplicate.
--
-- `food_id` has no foreign key on purpose: a report can be about an Open
-- Food Facts row in `off_foods` as well as a row in `foods`, and `off_foods`
-- is swapped for a new copy every month (V12). Its ids are derived from the
-- barcode, so they survive the swap and a report still finds its food.
--
-- The server files reports of its own with reason `energy_check` and no
-- account, when the energy guard (FoodEnergy in the server) serves an
-- estimate because a food's 0 kcal contradicts its macros. At most one per
-- food, ever (the partial unique index below): once a reviewer has decided,
-- the flag does not come back.
--
-- `food_corrections` is the overlay. A correction is applied over the stored
-- row every time the food is served, so a re-import of USDA or Open Food
-- Facts, which rewrites `foods` or replaces `off_foods`, never overwrites a
-- human fix. `fields` holds only the fields it sets, per 100 g (or ml, as
-- the food's own numbers are); `base` is the food as it was served just
-- before, for the review diff and the undo. One correction per food is live
-- (the unique index): a newer one supersedes the older (`superseded_by`),
-- and reverting the newer makes the older live again.
--
-- Owned by the reporter's account: deleting the account deletes their
-- reports and photos (cascade). Corrections carry no account, only who
-- applied them (the reviewer's name, as in the config audit log). Photos are
-- deleted 30 days after the decision, like submission photos (V25).
--
-- RLS on with no policies, as on every table. The analytics view carries
-- counts and ages only. Additive and idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS food_reports (
    id              UUID PRIMARY KEY,
    food_id         UUID NOT NULL,
    -- Null for the server's own energy check.
    user_id         UUID REFERENCES auth.users (id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason          TEXT NOT NULL CHECK (reason IN (
        'wrong_calories', 'wrong_nutrients', 'wrong_serving', 'wrong_name',
        'duplicate', 'not_food', 'other', 'energy_check'
    )),
    -- {basisLabel, basisGrams, kcal, protein, carbs, fat, servingLabel, servingGrams}, any of them.
    suggested       JSONB,
    note            TEXT CHECK (char_length(note) <= 1000),
    -- {name, brand, source, per100g: {kcal, protein, carbs, fat, fiber}, serving: {label, grams}}.
    snapshot        JSONB NOT NULL,
    status          TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'accepted', 'rejected', 'duplicate')),
    resolved_at     TIMESTAMPTZ,
    resolved_by     TEXT CHECK (char_length(resolved_by) <= 100),
    resolution      TEXT CHECK (char_length(resolution) <= 500),
    correction_id   UUID,
    duplicate_of    UUID,
    CHECK (user_id IS NOT NULL OR reason = 'energy_check')
);

CREATE INDEX IF NOT EXISTS food_reports_open_idx ON food_reports (created_at) WHERE status = 'open';
CREATE INDEX IF NOT EXISTS food_reports_food_idx ON food_reports (food_id, created_at DESC);
CREATE INDEX IF NOT EXISTS food_reports_user_idx ON food_reports (user_id);
CREATE INDEX IF NOT EXISTS food_reports_resolved_idx ON food_reports (resolved_at) WHERE resolved_at IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS food_reports_energy_check_once ON food_reports (food_id) WHERE reason = 'energy_check';

ALTER TABLE food_reports ENABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS food_report_photos (
    report_id   UUID PRIMARY KEY REFERENCES food_reports (id) ON DELETE CASCADE,
    media_type  TEXT NOT NULL CHECK (media_type IN ('image/jpeg', 'image/png', 'image/webp')),
    bytes       BYTEA NOT NULL
);

ALTER TABLE food_report_photos ENABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS food_corrections (
    id             UUID PRIMARY KEY,
    -- A `foods` or an `off_foods` id; no foreign key, as for reports.
    food_id        UUID NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     TEXT NOT NULL CHECK (char_length(created_by) <= 100),
    -- {name, brand, kcal, protein, carbs, fat, servingLabel, servingGrams}: only what it sets.
    fields         JSONB NOT NULL,
    base           JSONB NOT NULL,
    reverted_at    TIMESTAMPTZ,
    reverted_by    TEXT CHECK (char_length(reverted_by) <= 100),
    superseded_by  UUID REFERENCES food_corrections (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS food_corrections_live ON food_corrections (food_id)
    WHERE reverted_at IS NULL AND superseded_by IS NULL;
CREATE INDEX IF NOT EXISTS food_corrections_food_idx ON food_corrections (food_id, created_at DESC);

ALTER TABLE food_corrections ENABLE ROW LEVEL SECURITY;

-- The open queue for the review board: counts and ages, no food, no account.
CREATE OR REPLACE VIEW analytics.food_report_queue AS
SELECT
    count(*) AS open_reports,
    count(DISTINCT food_id) AS foods,
    count(*) FILTER (WHERE reason = 'energy_check') AS energy_checks,
    min(created_at) AS oldest_at
FROM public.food_reports
WHERE status = 'open';

GRANT SELECT ON analytics.food_report_queue TO grafana_ro;
