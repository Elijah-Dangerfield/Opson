-- ─────────────────────────────────────────────────────────────────────────────
-- V30: the Builder program (docs/plans/feedback-2026-10-29/builder-program.md).
--
-- Builders photograph packs they have in hand to add missing foods and check
-- existing ones, and get Pro while they keep at it. Four pieces here:
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
-- `food_provenance` records who contributed what, so a later ban can find and
-- roll back a person's foods even after they delete their account. The same
-- table, key function and trigger are planned by the moderation work
-- (builder-program.md "Two gaps to close first"); every statement below is
-- idempotent and matches that definition, so whichever migration runs second
-- changes nothing. The key is a SHA-256 of the user id under a random secret
-- made here, never in the repo and never readable by grafana_ro.
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

-- ── Provenance (shared with the moderation work; keep in step with it) ──────

CREATE TABLE IF NOT EXISTS moderation_secrets (
    name       TEXT PRIMARY KEY,
    value      TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE moderation_secrets ENABLE ROW LEVEL SECURITY;

INSERT INTO moderation_secrets (name, value)
VALUES ('subject_key', replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', ''))
ON CONFLICT (name) DO NOTHING;

CREATE OR REPLACE FUNCTION public.subject_key(subject UUID) RETURNS TEXT
    LANGUAGE sql STABLE
    SET search_path = ''
AS $$
    SELECT encode(sha256(convert_to(s.value || ':account:' || subject::text, 'UTF8')), 'hex')
    FROM public.moderation_secrets s WHERE s.name = 'subject_key'
$$;

REVOKE ALL ON FUNCTION public.subject_key(UUID) FROM PUBLIC;

CREATE OR REPLACE FUNCTION public.fill_subject_key() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    IF NEW.subject_key IS NULL AND NEW.user_id IS NOT NULL THEN
        NEW.subject_key := public.subject_key(NEW.user_id);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TABLE IF NOT EXISTS food_provenance (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    food_id             UUID        NOT NULL,
    kind                TEXT        NOT NULL CHECK (kind IN ('submission', 'correction', 'report')),
    ref_id              UUID        NOT NULL,
    user_id             UUID        REFERENCES auth.users (id) ON DELETE SET NULL,
    subject_key         TEXT        NOT NULL,
    lane                TEXT        NOT NULL DEFAULT 'review' CHECK (lane IN ('review', 'auto', 'owner')),
    accepted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_by         TEXT        CHECK (char_length(accepted_by) <= 200),
    rolled_back_at      TIMESTAMPTZ,
    rolled_back_by      TEXT        CHECK (char_length(rolled_back_by) <= 200),
    rollback_action_id  UUID,
    UNIQUE (kind, ref_id)
);

CREATE INDEX IF NOT EXISTS food_provenance_subject_idx ON food_provenance (subject_key) WHERE rolled_back_at IS NULL;
CREATE INDEX IF NOT EXISTS food_provenance_food_idx ON food_provenance (food_id);
CREATE INDEX IF NOT EXISTS food_provenance_accepted_idx ON food_provenance (accepted_at);

ALTER TABLE food_provenance ENABLE ROW LEVEL SECURITY;

DROP TRIGGER IF EXISTS food_provenance_subject_key ON food_provenance;
CREATE TRIGGER food_provenance_subject_key
    BEFORE INSERT ON food_provenance
    FOR EACH ROW EXECUTE FUNCTION public.fill_subject_key();

-- An approval is a contribution accepted: record who sent it, in the same
-- transaction. `reviewed_by` says the lane: `owner:<id>` and `auto:<rule>`
-- are written by the server's routing, anything else is a reviewer.
CREATE OR REPLACE FUNCTION public.food_submissions_record_provenance() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    IF NEW.status = 'approved' AND OLD.status IS DISTINCT FROM 'approved' THEN
        INSERT INTO public.food_provenance (food_id, kind, ref_id, user_id, lane, accepted_at, accepted_by)
        VALUES (
            NEW.food_id, 'submission', NEW.id, NEW.user_id,
            CASE WHEN NEW.reviewed_by LIKE 'owner:%' THEN 'owner' WHEN NEW.reviewed_by LIKE 'auto:%' THEN 'auto' ELSE 'review' END,
            coalesce(NEW.reviewed_at, now()), NEW.reviewed_by
        )
        ON CONFLICT (kind, ref_id) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS food_submissions_record_provenance ON food_submissions;
CREATE TRIGGER food_submissions_record_provenance
    AFTER UPDATE OF status ON food_submissions
    FOR EACH ROW EXECUTE FUNCTION public.food_submissions_record_provenance();

INSERT INTO food_provenance (food_id, kind, ref_id, user_id, accepted_at, accepted_by)
SELECT food_id, 'submission', id, user_id, coalesce(reviewed_at, created_at), reviewed_by
FROM food_submissions
WHERE status = 'approved'
ON CONFLICT (kind, ref_id) DO NOTHING;

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
