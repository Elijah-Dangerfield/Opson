-- ─────────────────────────────────────────────────────────────────────────────
-- V31: the Builder program (docs/plans/feedback-2026-10-29/builder-program.md).
--
-- Builders photograph packs they have in hand to add missing foods and check
-- existing ones, and get Pro while they keep at it. Three pieces here, and a fix:
--
-- `builder_members` is one row per person who joined: when, which terms they
-- accepted, whether they let Opson keep their label photos for up to two
-- years (the only way to build the labelled set in ai-options.md; the policy
-- otherwise deletes photos 30 days after review), when they became Trusted,
-- and whether the owner is holding them back from it. Joining also sets the
-- `builder` role (V28), whose bundles bring Pro; the server only applies
-- them while the person is active (ten accepted contributions in 30 days, or
-- their first 30 days), worked out on every read rather than by a job.
-- Leaving or removal sets `status` and clears the role. Owned by the account
-- (cascade), like every other per-person table.
--
-- `food_submissions` gains the record of how each one was routed: `lane`
-- (review, auto, owner), `routing` (the reasons, the confidence and its
-- signals, the independent cloud read and whether it agreed: codes and
-- numbers, never a photo), `capture` (what the phone read: its OCR lines,
-- its draft, its signals), the random `audit` of an auto-approval, and a
-- `reject_code` from a short list so trust can tell a fake from a blurry
-- photo. Photos may now include `ingredients`.
--
-- `food_verifications` is a Builder checking a catalog food against the pack
-- in their hand: which food, whether the numbers on the label (read on the
-- phone, sent as text) matched it, and how many of them the server found in
-- that text. No photo is kept: a check changes nothing, and a mismatch goes
-- through Report a problem with its photo instead.
--
-- Provenance is V30's `food_provenance`: its trigger reads the lane from
-- `reviewed_by`, which routing writes as `owner:<id>` and `auto:<rule>`, and
-- a failed audit marks the row rolled back. V30's photo hash also learns to
-- leave an ingredients photo out of the label's hash.
--
-- RLS on with no policies, as on every table. Additive and idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS builder_members (
    user_id          UUID        PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
    status           TEXT        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'left', 'removed')),
    joined_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    terms_version    TEXT        NOT NULL CHECK (char_length(terms_version) <= 32),
    photo_retention  BOOLEAN     NOT NULL DEFAULT FALSE,
    trusted_since    TIMESTAMPTZ,
    trust_hold       BOOLEAN     NOT NULL DEFAULT FALSE,
    left_at          TIMESTAMPTZ,
    removed_at       TIMESTAMPTZ,
    removed_by       TEXT        CHECK (char_length(removed_by) <= 200),
    removed_reason   TEXT        CHECK (char_length(removed_reason) <= 500)
);

CREATE INDEX IF NOT EXISTS builder_members_active_idx ON builder_members (joined_at) WHERE status = 'active';

ALTER TABLE builder_members ENABLE ROW LEVEL SECURITY;

-- ── Routing on submissions ──────────────────────────────────────────────────

ALTER TABLE food_submissions ADD COLUMN IF NOT EXISTS lane TEXT;
ALTER TABLE food_submissions DROP CONSTRAINT IF EXISTS food_submissions_lane_check;
ALTER TABLE food_submissions ADD CONSTRAINT food_submissions_lane_check
    CHECK (lane IS NULL OR lane IN ('review', 'auto', 'owner')) NOT VALID;
ALTER TABLE food_submissions ADD COLUMN IF NOT EXISTS routing JSONB;
ALTER TABLE food_submissions ADD COLUMN IF NOT EXISTS capture JSONB;
ALTER TABLE food_submissions ADD COLUMN IF NOT EXISTS audit TEXT;
ALTER TABLE food_submissions DROP CONSTRAINT IF EXISTS food_submissions_audit_check;
ALTER TABLE food_submissions ADD CONSTRAINT food_submissions_audit_check
    CHECK (audit IS NULL OR audit IN ('pending', 'passed', 'failed')) NOT VALID;
ALTER TABLE food_submissions ADD COLUMN IF NOT EXISTS audited_at TIMESTAMPTZ;
ALTER TABLE food_submissions ADD COLUMN IF NOT EXISTS audited_by TEXT;
ALTER TABLE food_submissions ADD COLUMN IF NOT EXISTS audit_note TEXT;
ALTER TABLE food_submissions ADD COLUMN IF NOT EXISTS reject_code TEXT;
ALTER TABLE food_submissions DROP CONSTRAINT IF EXISTS food_submissions_reject_code_check;
ALTER TABLE food_submissions ADD CONSTRAINT food_submissions_reject_code_check
    CHECK (reject_code IS NULL OR reject_code IN ('blurry', 'not_food', 'duplicate', 'wrong_column', 'wrong_values', 'fake_data', 'copied_data', 'other')) NOT VALID;

CREATE INDEX IF NOT EXISTS food_submissions_audit_idx ON food_submissions (reviewed_at) WHERE audit = 'pending';
CREATE INDEX IF NOT EXISTS food_submissions_user_reviewed_idx ON food_submissions (user_id, reviewed_at DESC) WHERE reviewed_at IS NOT NULL;

ALTER TABLE food_submission_photos DROP CONSTRAINT IF EXISTS food_submission_photos_kind_check;
ALTER TABLE food_submission_photos ADD CONSTRAINT food_submission_photos_kind_check
    CHECK (kind IN ('front', 'label', 'ingredients'));

-- ── Checks against the pack ─────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS food_verifications (
    id          UUID        PRIMARY KEY,
    -- A `foods` or an `off_foods` id; no foreign key, as for reports (V29).
    food_id     UUID        NOT NULL,
    user_id     UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    outcome     TEXT        NOT NULL CHECK (outcome IN ('confirmed', 'unconfirmed')),
    grounded    INTEGER     NOT NULL CHECK (grounded >= 0),
    groundable  INTEGER     NOT NULL CHECK (groundable >= 0),
    engine      TEXT        CHECK (char_length(engine) <= 32)
);

CREATE INDEX IF NOT EXISTS food_verifications_user_idx ON food_verifications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS food_verifications_food_idx ON food_verifications (food_id, created_at DESC);
-- One confirmation per person and food counts; a second check of the same pack adds nothing.
CREATE UNIQUE INDEX IF NOT EXISTS food_verifications_once ON food_verifications (user_id, food_id) WHERE outcome = 'confirmed';

ALTER TABLE food_verifications ENABLE ROW LEVEL SECURITY;

-- ── Photo hashes (V30) ───────────────────────────────────────────────────────

-- V30 hashes every photo but the front into label_sha256; an ingredients photo
-- must not overwrite the label's hash, which the reuse check reads.
CREATE OR REPLACE FUNCTION public.food_submission_photos_hash() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    IF NEW.kind = 'front' THEN
        UPDATE public.food_submissions SET front_sha256 = encode(sha256(NEW.bytes), 'hex') WHERE id = NEW.submission_id;
    ELSIF NEW.kind = 'label' THEN
        UPDATE public.food_submissions SET label_sha256 = encode(sha256(NEW.bytes), 'hex') WHERE id = NEW.submission_id;
    END IF;
    RETURN NEW;
END;
$$;

-- ── Views ───────────────────────────────────────────────────────────────────

-- How submissions were routed, per day: no food, no account, no number.
CREATE OR REPLACE VIEW analytics.contribution_lanes_daily AS
SELECT
    (created_at AT TIME ZONE 'UTC')::date AS day,
    coalesce(lane, 'review') AS lane,
    count(*) AS submissions,
    count(*) FILTER (WHERE (routing ->> 'wouldAutoApprove')::boolean) AS would_auto_approve,
    count(*) FILTER (WHERE routing -> 'cloud' IS NOT NULL) AS cloud_reads,
    count(*) FILTER (WHERE (routing -> 'cloud' ->> 'agrees')::boolean) AS cloud_agreed,
    count(*) FILTER (WHERE audit = 'pending') AS audits_waiting,
    count(*) FILTER (WHERE audit = 'passed') AS audits_passed,
    count(*) FILTER (WHERE audit = 'failed') AS audits_failed
FROM public.food_submissions
GROUP BY 1, 2;

-- Builders joined, left and removed per day.
CREATE OR REPLACE VIEW analytics.builder_program_daily AS
SELECT day, sum(joined) AS joined, sum(left_program) AS left_program, sum(removed) AS removed
FROM (
    SELECT (joined_at AT TIME ZONE 'UTC')::date AS day, 1 AS joined, 0 AS left_program, 0 AS removed FROM public.builder_members
    UNION ALL
    SELECT (left_at AT TIME ZONE 'UTC')::date, 0, 1, 0 FROM public.builder_members WHERE left_at IS NOT NULL
    UNION ALL
    SELECT (removed_at AT TIME ZONE 'UTC')::date, 0, 0, 1 FROM public.builder_members WHERE removed_at IS NOT NULL
) b
GROUP BY day;

-- One row per Builder: standing and dates only.
CREATE OR REPLACE VIEW support.builders AS
SELECT user_id, status, joined_at, trusted_since, trust_hold, photo_retention, left_at, removed_at, removed_by
FROM public.builder_members;

GRANT SELECT ON analytics.contribution_lanes_daily, analytics.builder_program_daily TO grafana_ro;
GRANT SELECT ON support.builders TO grafana_ro;
