-- ─────────────────────────────────────────────────────────────────────────────
-- V34: verified foods, Builders' verifications for review, and reports v2
-- (docs/decisions.md, 2026-10-30, "What verified means").
--
-- A food is *checked* (the app's Verified mark) when a person checked it
-- against its pack or a reviewer stood behind it: a Builder's verification
-- confirmed by the label's text or accepted by a reviewer, a live reviewer's
-- correction, or a food someone added that a reviewer approved. Nothing here
-- stores that: the catalog works it out as it serves a food, from
-- `food_verifications` (a confirmed outcome), `food_corrections` and the
-- submission's review status, so a rollback or a reverted correction takes
-- the mark away by itself.
--
-- `food_verifications` (V31) now keeps a check that needs a person. A Builder
-- who says "Something was wrong", or whose label text did not show the food's
-- numbers while they sent their photos, is queued (`status = 'open'`) with
-- what they said (`problem`: which fields were wrong or missing, the values
-- they read, a note), the food as it was served to them (`snapshot`, the same
-- shape as a report's) and the front and label photos
-- (`food_verification_photos`). A reviewer accepts it, laying a correction
-- over the existing food when values are given (`correction_id`, through the
-- V29 overlay, never a new food), or rejects it. Accepting sets the outcome to
-- `confirmed` (unless the same person already has a confirmed check of that
-- food, V31's once-per-person rule), which is what counts toward Builder Pro;
-- an accepted or confirmed verification is what makes the food checked. Rows with no status are the checks of V31, decided on arrival.
-- Photos are deleted 30 days after the decision, like report photos.
--
-- `food_reports` takes several reasons at once (`reasons`, the first also in
-- `reason` for anything that reads one), with two new codes: `wrong_numbers`
-- and `missing_info`. Its photos are now a front and a label (`kind`), at most
-- one of each.
--
-- An accepted verification is a contribution: `food_provenance` gains the
-- kind `verification`, written by a trigger like a report's.
--
-- Views for the Builders board's Database cleanup row carry counts, ages and
-- the reviewer's name only. RLS on with no policies. Additive and idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Verifications for review ────────────────────────────────────────────────

ALTER TABLE food_verifications ADD COLUMN IF NOT EXISTS status TEXT;
ALTER TABLE food_verifications DROP CONSTRAINT IF EXISTS food_verifications_status_check;
ALTER TABLE food_verifications ADD CONSTRAINT food_verifications_status_check
    CHECK (status IS NULL OR status IN ('open', 'accepted', 'rejected')) NOT VALID;
-- {wrong: [..], missing: [..], values: {name, brand, barcode, basisLabel, basisGrams, kcal, protein, carbs, fat, servingLabel, servingGrams}, note}
ALTER TABLE food_verifications ADD COLUMN IF NOT EXISTS problem JSONB;
-- {name, brand, source, per100g, serving}: the food as the Builder saw it.
ALTER TABLE food_verifications ADD COLUMN IF NOT EXISTS snapshot JSONB;
ALTER TABLE food_verifications ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;
ALTER TABLE food_verifications ADD COLUMN IF NOT EXISTS resolved_by TEXT;
ALTER TABLE food_verifications DROP CONSTRAINT IF EXISTS food_verifications_resolved_by_check;
ALTER TABLE food_verifications ADD CONSTRAINT food_verifications_resolved_by_check
    CHECK (char_length(resolved_by) <= 100) NOT VALID;
ALTER TABLE food_verifications ADD COLUMN IF NOT EXISTS resolution TEXT;
ALTER TABLE food_verifications DROP CONSTRAINT IF EXISTS food_verifications_resolution_check;
ALTER TABLE food_verifications ADD CONSTRAINT food_verifications_resolution_check
    CHECK (char_length(resolution) <= 500) NOT VALID;
ALTER TABLE food_verifications ADD COLUMN IF NOT EXISTS correction_id UUID;

CREATE INDEX IF NOT EXISTS food_verifications_open_idx ON food_verifications (created_at) WHERE status = 'open';
CREATE INDEX IF NOT EXISTS food_verifications_resolved_idx ON food_verifications (resolved_at) WHERE resolved_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS food_verification_photos (
    verification_id  UUID NOT NULL REFERENCES food_verifications (id) ON DELETE CASCADE,
    kind             TEXT NOT NULL CHECK (kind IN ('front', 'label')),
    media_type       TEXT NOT NULL CHECK (media_type IN ('image/jpeg', 'image/png', 'image/webp')),
    bytes            BYTEA NOT NULL,
    PRIMARY KEY (verification_id, kind)
);

ALTER TABLE food_verification_photos ENABLE ROW LEVEL SECURITY;

-- ── Reports v2 ──────────────────────────────────────────────────────────────

ALTER TABLE food_reports ADD COLUMN IF NOT EXISTS reasons TEXT[];
ALTER TABLE food_reports DROP CONSTRAINT IF EXISTS food_reports_reason_check;
ALTER TABLE food_reports ADD CONSTRAINT food_reports_reason_check CHECK (reason IN (
    'wrong_calories', 'wrong_nutrients', 'wrong_serving', 'wrong_name',
    'duplicate', 'not_food', 'other', 'energy_check', 'wrong_numbers', 'missing_info'
));
ALTER TABLE food_reports DROP CONSTRAINT IF EXISTS food_reports_reasons_check;
ALTER TABLE food_reports ADD CONSTRAINT food_reports_reasons_check CHECK (reasons IS NULL OR (
    cardinality(reasons) BETWEEN 1 AND 8 AND reasons <@ ARRAY[
        'wrong_calories', 'wrong_nutrients', 'wrong_serving', 'wrong_name',
        'duplicate', 'not_food', 'other', 'wrong_numbers', 'missing_info'
    ]::TEXT[]
));

ALTER TABLE food_report_photos ADD COLUMN IF NOT EXISTS kind TEXT NOT NULL DEFAULT 'label';
ALTER TABLE food_report_photos DROP CONSTRAINT IF EXISTS food_report_photos_kind_check;
ALTER TABLE food_report_photos ADD CONSTRAINT food_report_photos_kind_check CHECK (kind IN ('front', 'label'));
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
        WHERE c.conname = 'food_report_photos_pkey' AND c.contype = 'p'
        GROUP BY c.oid HAVING count(*) = 1
    ) THEN
        ALTER TABLE food_report_photos DROP CONSTRAINT food_report_photos_pkey;
        ALTER TABLE food_report_photos ADD CONSTRAINT food_report_photos_pkey PRIMARY KEY (report_id, kind);
    END IF;
END;
$$;

-- ── Provenance ──────────────────────────────────────────────────────────────

ALTER TABLE food_provenance DROP CONSTRAINT IF EXISTS food_provenance_kind_check;
ALTER TABLE food_provenance ADD CONSTRAINT food_provenance_kind_check
    CHECK (kind IN ('submission', 'correction', 'report', 'verification'));

CREATE OR REPLACE FUNCTION public.food_verifications_record_provenance() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    IF NEW.status = 'accepted' AND OLD.status IS DISTINCT FROM 'accepted' THEN
        INSERT INTO public.food_provenance (food_id, kind, ref_id, correction_id, user_id, accepted_at, accepted_by)
        VALUES (NEW.food_id, 'verification', NEW.id, NEW.correction_id, NEW.user_id, coalesce(NEW.resolved_at, now()), NEW.resolved_by)
        ON CONFLICT (kind, ref_id) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS food_verifications_record_provenance ON food_verifications;
CREATE TRIGGER food_verifications_record_provenance
    AFTER UPDATE OF status ON food_verifications
    FOR EACH ROW EXECUTE FUNCTION public.food_verifications_record_provenance();

-- ── Views ───────────────────────────────────────────────────────────────────

-- The verifications waiting for a reviewer: counts and the oldest, no food, no account.
CREATE OR REPLACE VIEW analytics.food_verification_queue AS
SELECT
    count(*) AS open_verifications,
    count(DISTINCT food_id) AS foods,
    count(*) FILTER (WHERE problem IS NOT NULL) AS with_problem,
    min(created_at) AS oldest_at
FROM public.food_verifications
WHERE status = 'open';

-- Verifications per day: how they came in and how they ended.
CREATE OR REPLACE VIEW analytics.food_verifications_daily AS
SELECT
    (created_at AT TIME ZONE 'UTC')::date AS day,
    count(*) AS verifications,
    count(*) FILTER (WHERE status IS NULL AND outcome = 'confirmed') AS confirmed_on_arrival,
    count(*) FILTER (WHERE status IS NOT NULL) AS sent_for_review,
    count(*) FILTER (WHERE problem IS NOT NULL) AS with_problem,
    count(*) FILTER (WHERE status = 'accepted') AS accepted,
    count(*) FILTER (WHERE status = 'rejected') AS rejected
FROM public.food_verifications
GROUP BY 1;

-- Every reviewer's decisions per day, on reports and verifications alike: the Database cleanup row.
CREATE OR REPLACE VIEW analytics.food_review_decisions_daily AS
SELECT day, kind, reviewer, sum(accepted) AS accepted, sum(rejected) AS rejected, sum(duplicate) AS duplicate
FROM (
    SELECT (resolved_at AT TIME ZONE 'UTC')::date AS day, 'report'::text AS kind, coalesce(resolved_by, 'unknown') AS reviewer,
           (status = 'accepted')::int AS accepted, (status = 'rejected')::int AS rejected, (status = 'duplicate')::int AS duplicate
    FROM public.food_reports WHERE resolved_at IS NOT NULL
    UNION ALL
    SELECT (resolved_at AT TIME ZONE 'UTC')::date, 'verification', coalesce(resolved_by, 'unknown'),
           (status = 'accepted')::int, (status = 'rejected')::int, 0
    FROM public.food_verifications WHERE resolved_at IS NOT NULL
) d
GROUP BY day, kind, reviewer;

GRANT SELECT ON analytics.food_verification_queue, analytics.food_verifications_daily, analytics.food_review_decisions_daily TO grafana_ro;
