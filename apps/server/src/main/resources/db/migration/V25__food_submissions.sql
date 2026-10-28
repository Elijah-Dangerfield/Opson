-- ─────────────────────────────────────────────────────────────────────────────
-- V25: foods people add from a pack's photos, the review queue behind them,
-- and barcodes suggested by a miss followed by a search.
--
-- A person whose scan (or search) found nothing photographs the front of the
-- pack and its nutrition label; the server reads both with a model, the
-- person corrects what it read, and submits. The food goes into `foods` at
-- once as source `user_submitted` (tier 4, not verified, V10's trigger) with
-- review_status `pending`:
--
--   pending   only its submitter sees it (GET /v1/foods/{id}, and their own
--             barcode lookups); search never returns it. Its `barcode` stays
--             null so a pending row can never claim a code another product
--             (or another person's submission) needs; the code waits on the
--             submission row.
--   approved  a reviewer checked it against the photos. Search and barcode
--             lookup return it to everyone; `barcode` is set and `verified`
--             turns true (V10 left `verified` defaulted for exactly this).
--   rejected  hidden from everyone but its submitter, with a reason.
--
-- Every existing row is `approved`: the default is a constant, so adding the
-- column is a catalog-only change, and both CHECKs are added NOT VALID so
-- neither reads the 450,000 Branded rows under an exclusive lock. Existing
-- rows satisfy both by construction; new and updated rows are checked.
--
-- `food_submissions` is the review record: the barcode as scanned and its
-- GTIN, what the model read (`parsed`), what the person sent (`submitted`),
-- the deterministic checks on the food as it stands, a reviewer's corrections
-- (`reviewed`), and the decision. Owned by
-- the submitter's account: deleting the account deletes the submission and
-- its photos (cascade), and a food that was never approved goes with it
-- (trigger below). An approved food stays, as catalog data with no account
-- attached. Food data is not personal; the photos are, and they go.
--
-- `food_submission_photos` holds the two photos in Postgres rather than
-- Storage: they are only ever read by the token-gated admin API, never by a
-- URL, and a cascade deletes them with the account. The app sends JPEGs of at
-- most 1568 px (about 300 KB each). Photos are kept until 30 days after the
-- review decision, then deleted by the server (FoodSubmissionRepository.purgeDecidedPhotos);
-- the review record itself stays.
--
-- `barcode_link_suggestions` pairs a barcode with a catalog food when someone
-- scanned it, found nothing, then searched and logged that food within ten
-- minutes. One row per (gtin, food), counted across people; no account, no
-- search text. A reviewer attaches the barcode to the food or dismisses it.
-- Nothing is attached automatically.
--
-- RLS on with no policies, as on every table. Analytics views carry ids,
-- counts and ages only: no name, barcode, nutrient or account.
-- Additive and idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE foods DROP CONSTRAINT IF EXISTS foods_source_check;
ALTER TABLE foods ADD CONSTRAINT foods_source_check
    CHECK (source IN ('usda_foundation', 'usda_sr', 'usda_fndds', 'usda_branded', 'nutritionix', 'off', 'nevo', 'user_submitted'))
    NOT VALID;

ALTER TABLE foods ADD COLUMN IF NOT EXISTS review_status TEXT NOT NULL DEFAULT 'approved';
ALTER TABLE foods DROP CONSTRAINT IF EXISTS foods_review_status_check;
ALTER TABLE foods ADD CONSTRAINT foods_review_status_check
    CHECK (review_status IN ('pending', 'approved', 'rejected'))
    NOT VALID;

-- food_source_tier() already maps any unlisted source to 4 (community), which
-- is where people's submissions belong; DatabaseSchemaTest holds the mapping.

CREATE TABLE IF NOT EXISTS food_submissions (
    id             UUID PRIMARY KEY,
    user_id        UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    food_id        UUID NOT NULL REFERENCES foods (id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    status         TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    barcode        TEXT CHECK (barcode ~ '^[0-9]{8,14}$'),
    gtin           TEXT CHECK (gtin ~ '^[0-9]{14}$'),
    -- Where the person started: a barcode miss, or a search with no results.
    origin         TEXT NOT NULL CHECK (origin IN ('barcode', 'search')),
    model          TEXT CHECK (char_length(model) <= 64),
    parsed         JSONB,
    submitted      JSONB NOT NULL,
    -- A reviewer's corrections, kept apart so what the person sent stays as sent.
    reviewed       JSONB,
    checks         JSONB NOT NULL DEFAULT '[]'::jsonb,
    reviewed_at    TIMESTAMPTZ,
    reviewed_by    TEXT CHECK (char_length(reviewed_by) <= 100),
    reject_reason  TEXT CHECK (char_length(reject_reason) <= 500)
);

CREATE INDEX IF NOT EXISTS food_submissions_pending_idx ON food_submissions (created_at) WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS food_submissions_user_idx ON food_submissions (user_id);
CREATE INDEX IF NOT EXISTS food_submissions_food_idx ON food_submissions (food_id);
CREATE INDEX IF NOT EXISTS food_submissions_gtin_idx ON food_submissions (gtin) WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS food_submissions_reviewed_idx ON food_submissions (reviewed_at) WHERE reviewed_at IS NOT NULL;

ALTER TABLE food_submissions ENABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS food_submission_photos (
    submission_id  UUID NOT NULL REFERENCES food_submissions (id) ON DELETE CASCADE,
    kind           TEXT NOT NULL CHECK (kind IN ('front', 'label')),
    media_type     TEXT NOT NULL CHECK (media_type IN ('image/jpeg', 'image/png', 'image/webp')),
    bytes          BYTEA NOT NULL,
    PRIMARY KEY (submission_id, kind)
);

ALTER TABLE food_submission_photos ENABLE ROW LEVEL SECURITY;

-- A food nobody approved leaves with its submission: the account was deleted,
-- or the submission was removed. An approved food is catalog data and stays.
CREATE OR REPLACE FUNCTION public.food_submissions_drop_unapproved_food() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    DELETE FROM public.foods WHERE id = OLD.food_id AND review_status <> 'approved';
    RETURN OLD;
END;
$$;

DROP TRIGGER IF EXISTS food_submissions_drop_unapproved_food ON food_submissions;
CREATE TRIGGER food_submissions_drop_unapproved_food
    AFTER DELETE ON food_submissions
    FOR EACH ROW EXECUTE FUNCTION public.food_submissions_drop_unapproved_food();

CREATE TABLE IF NOT EXISTS barcode_link_suggestions (
    gtin          TEXT NOT NULL CHECK (gtin ~ '^[0-9]{14}$'),
    food_id       UUID NOT NULL REFERENCES foods (id) ON DELETE CASCADE,
    -- The code as first scanned, so a reviewer sees what was printed.
    barcode       TEXT NOT NULL CHECK (barcode ~ '^[0-9]{8,14}$'),
    pairings      INTEGER NOT NULL DEFAULT 1 CHECK (pairings > 0),
    first_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    status        TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'attached', 'dismissed')),
    reviewed_at   TIMESTAMPTZ,
    PRIMARY KEY (gtin, food_id)
);

CREATE INDEX IF NOT EXISTS barcode_link_suggestions_pending_idx
    ON barcode_link_suggestions (pairings DESC, last_at DESC) WHERE status = 'pending';

ALTER TABLE barcode_link_suggestions ENABLE ROW LEVEL SECURITY;

-- Submissions and decisions per day, for the review board.
CREATE OR REPLACE VIEW analytics.food_submissions_daily AS
SELECT day, sum(submitted) AS submitted, sum(approved) AS approved, sum(rejected) AS rejected
FROM (
    SELECT (created_at AT TIME ZONE 'UTC')::date AS day, 1 AS submitted, 0 AS approved, 0 AS rejected
    FROM public.food_submissions
    UNION ALL
    SELECT (reviewed_at AT TIME ZONE 'UTC')::date, 0,
           CASE WHEN status = 'approved' THEN 1 ELSE 0 END,
           CASE WHEN status = 'rejected' THEN 1 ELSE 0 END
    FROM public.food_submissions
    WHERE reviewed_at IS NOT NULL
) events
GROUP BY day;

-- The queue as it stands: one row per pending submission, oldest first. Ids
-- and ages only, so the board can link into the admin console without
-- showing what anyone bought.
CREATE OR REPLACE VIEW analytics.food_submission_queue AS
SELECT
    id AS submission_id,
    created_at AS submitted_at,
    round(extract(epoch FROM (now() - created_at)) / 3600.0, 1) AS age_hours,
    origin,
    barcode IS NOT NULL AS has_barcode,
    jsonb_array_length(checks) AS failed_checks
FROM public.food_submissions
WHERE status = 'pending'
ORDER BY created_at;

-- Suggested barcode links waiting for a reviewer.
CREATE OR REPLACE VIEW analytics.barcode_link_queue AS
SELECT count(*) AS suggestions, coalesce(sum(pairings), 0) AS pairings, min(first_at) AS oldest_at
FROM public.barcode_link_suggestions
WHERE status = 'pending';

GRANT SELECT ON analytics.food_submissions_daily, analytics.food_submission_queue,
    analytics.barcode_link_queue TO grafana_ro;
