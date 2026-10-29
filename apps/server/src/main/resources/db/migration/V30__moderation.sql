-- ─────────────────────────────────────────────────────────────────────────────
-- V30: moderation, provenance, device markers, and the views behind the
-- Builders review board (ops/grafana/builders.json).
--
-- moderation_actions is the app-level record of a ban, a suspension or a
-- contributions restriction: why (a reason code and a note), how wide
-- (`scope`: `full` answers every signed-in call with the 403 ban envelope;
-- `contributions` keeps the app working and refuses food submissions, reports
-- and corrections), until when, who did it, and where an appeal stands. It
-- sits beside Supabase's own `auth.users.banned_until`, which the ban gate
-- still reads, so a ban set from the Supabase dashboard keeps working.
-- moderation_events is the trail: every action, lift, appeal change,
-- rollback and device marker, with the row before and after.
--
-- food_provenance records who contributed what, so a later ban can find and
-- undo it. One row per accepted contribution: an approved submission, or an
-- accepted report (V29) with the correction it led to. Triggers write them
-- as the decision is made, and the decisions already made are backfilled.
--
-- Accounts are deleted on request, and V25 cascades a submission away with
-- its account. So these three tables keep `user_id` only while the account
-- exists (ON DELETE SET NULL) and also carry a key: a SHA-256 of the user id
-- under a random secret generated here (`moderation_secrets`, never in the
-- repo, never readable by grafana_ro). The key is not the id and can't be
-- turned back into it, but the server can recompute it from an id it is
-- given, which is what lets a ban made before a deletion still roll back
-- that person's foods after it. The privacy policy says so.
--
-- device_sightings and device_markers carry devices, never a device id: the
-- app sends a random install key (a Keychain item on iOS, the install id on
-- Android) only with contribution requests, and only its keyed hash is kept.
-- A marker blocks contributions from that device and the free Pro programs,
-- never logging food. DeviceCheck (iOS) and Play Integrity device recall
-- (Android) carry the same mark across a reinstall once they are configured;
-- `remote_state` says whether that bit has been written yet (it can only be
-- written while the device is talking to us, so a new mark waits for the
-- next contribution from it). contribution_checks is a ledger of outcomes
-- with no account, for the board's integrity panel.
--
-- Submissions also get the SHA-256 of each photo, filled by a trigger as the
-- photos are stored, so a reused photo is still findable after the photos are
-- deleted 30 days after review.
--
-- The views: `analytics` stays aggregate-only (no user id, no nutrient, no
-- name), `support` carries one row per contributor with counts and codes,
-- never a food's name, a barcode, a number from a label or a photo.
--
-- RLS on with no policies, as on every table. Additive and idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS moderation_secrets (
    name       TEXT PRIMARY KEY,
    value      TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE moderation_secrets ENABLE ROW LEVEL SECURITY;

-- 244 random bits from two v4 UUIDs: pgcrypto isn't needed, and an existing
-- environment keeps its secret (changing it would orphan every stored key).
INSERT INTO moderation_secrets (name, value)
VALUES ('subject_key', replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', ''))
ON CONFLICT (name) DO NOTHING;

-- The key for an account id. The server calls it too, so SQL and Kotlin can never disagree.
CREATE OR REPLACE FUNCTION public.subject_key(subject UUID) RETURNS TEXT
    LANGUAGE sql STABLE
    SET search_path = ''
AS $$
    SELECT encode(sha256(convert_to(s.value || ':account:' || subject::text, 'UTF8')), 'hex')
    FROM public.moderation_secrets s WHERE s.name = 'subject_key'
$$;

-- The keyed hash of an install key the app sent. The raw key is never stored.
CREATE OR REPLACE FUNCTION public.device_key_hash(device_key TEXT) RETURNS TEXT
    LANGUAGE sql STABLE
    SET search_path = ''
AS $$
    SELECT encode(sha256(convert_to(s.value || ':device:' || device_key, 'UTF8')), 'hex')
    FROM public.moderation_secrets s WHERE s.name = 'subject_key'
$$;

REVOKE ALL ON FUNCTION public.subject_key(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.device_key_hash(TEXT) FROM PUBLIC;

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

CREATE TABLE IF NOT EXISTS moderation_actions (
    id                 UUID        PRIMARY KEY,
    user_id            UUID        REFERENCES auth.users (id) ON DELETE SET NULL,
    subject_key        TEXT        NOT NULL,
    kind               TEXT        NOT NULL CHECK (kind IN ('ban', 'suspension', 'restriction')),
    scope              TEXT        NOT NULL CHECK (scope IN ('full', 'contributions')),
    reason             TEXT        NOT NULL CHECK (reason IN ('fake_data', 'copied_data', 'spam', 'abuse', 'ban_evasion', 'other')),
    note               TEXT        CHECK (char_length(note) <= 1000),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         TEXT        NOT NULL CHECK (char_length(created_by) <= 200),
    -- Null for an indefinite ban or restriction. A suspension always ends.
    until              TIMESTAMPTZ,
    -- Also mark every device this account contributed from.
    mark_devices       BOOLEAN     NOT NULL DEFAULT FALSE,
    lifted_at          TIMESTAMPTZ,
    lifted_by          TEXT        CHECK (char_length(lifted_by) <= 200),
    lift_note          TEXT        CHECK (char_length(lift_note) <= 1000),
    appeal_state       TEXT        NOT NULL DEFAULT 'none' CHECK (appeal_state IN ('none', 'open', 'upheld', 'overturned')),
    appeal_note        TEXT        CHECK (char_length(appeal_note) <= 1000),
    appeal_updated_at  TIMESTAMPTZ,
    appeal_updated_by  TEXT        CHECK (char_length(appeal_updated_by) <= 200),
    CHECK ((kind = 'restriction') = (scope = 'contributions')),
    CHECK (kind <> 'suspension' OR until IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS moderation_actions_user_active_idx ON moderation_actions (user_id) WHERE lifted_at IS NULL;
CREATE INDEX IF NOT EXISTS moderation_actions_subject_idx ON moderation_actions (subject_key);
CREATE INDEX IF NOT EXISTS moderation_actions_created_idx ON moderation_actions (created_at DESC);

ALTER TABLE moderation_actions ENABLE ROW LEVEL SECURITY;

DROP TRIGGER IF EXISTS moderation_actions_subject_key ON moderation_actions;
CREATE TRIGGER moderation_actions_subject_key
    BEFORE INSERT ON moderation_actions
    FOR EACH ROW EXECUTE FUNCTION public.fill_subject_key();

CREATE TABLE IF NOT EXISTS moderation_events (
    id            UUID        PRIMARY KEY,
    -- Orders events written in the same millisecond.
    seq           BIGSERIAL,
    at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor         TEXT        NOT NULL CHECK (char_length(actor) <= 200),
    action        TEXT        NOT NULL CHECK (action ~ '^[a-z][a-z_]{0,39}$'),
    action_id     UUID,
    user_id       UUID        REFERENCES auth.users (id) ON DELETE SET NULL,
    subject_key   TEXT,
    before_jsonb  JSONB,
    after_jsonb   JSONB
);

CREATE INDEX IF NOT EXISTS moderation_events_subject_at_idx ON moderation_events (subject_key, at DESC);
CREATE INDEX IF NOT EXISTS moderation_events_at_idx ON moderation_events (at DESC);

ALTER TABLE moderation_events ENABLE ROW LEVEL SECURITY;

DROP TRIGGER IF EXISTS moderation_events_subject_key ON moderation_events;
CREATE TRIGGER moderation_events_subject_key
    BEFORE INSERT ON moderation_events
    FOR EACH ROW EXECUTE FUNCTION public.fill_subject_key();

-- ── Provenance ──────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS food_provenance (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- No foreign key: the record outlives a food a cascade deletes, and a
    -- correction can be about an `off_foods` row.
    food_id             UUID        NOT NULL,
    kind                TEXT        NOT NULL CHECK (kind IN ('submission', 'correction', 'report')),
    -- The submission or report.
    ref_id              UUID        NOT NULL,
    -- For an accepted report, the correction it led to (V29 `food_corrections`).
    correction_id       UUID,
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

-- An approval is a contribution accepted: record who sent it, in the same transaction.
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

-- A report accepted is a contribution too: the person's numbers became a correction.
-- The server's own energy checks have no account and no provenance.
CREATE OR REPLACE FUNCTION public.food_reports_record_provenance() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    IF NEW.status = 'accepted' AND OLD.status IS DISTINCT FROM 'accepted' AND NEW.user_id IS NOT NULL THEN
        INSERT INTO public.food_provenance (food_id, kind, ref_id, correction_id, user_id, accepted_at, accepted_by)
        VALUES (NEW.food_id, 'report', NEW.id, NEW.correction_id, NEW.user_id, coalesce(NEW.resolved_at, now()), NEW.resolved_by)
        ON CONFLICT (kind, ref_id) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS food_reports_record_provenance ON food_reports;
CREATE TRIGGER food_reports_record_provenance
    AFTER UPDATE OF status ON food_reports
    FOR EACH ROW EXECUTE FUNCTION public.food_reports_record_provenance();

INSERT INTO food_provenance (food_id, kind, ref_id, correction_id, user_id, accepted_at, accepted_by)
SELECT food_id, 'report', id, correction_id, user_id, coalesce(resolved_at, created_at), resolved_by
FROM food_reports
WHERE status = 'accepted' AND user_id IS NOT NULL
ON CONFLICT (kind, ref_id) DO NOTHING;

-- ── Photo hashes ────────────────────────────────────────────────────────────

ALTER TABLE food_submissions ADD COLUMN IF NOT EXISTS front_sha256 TEXT;
ALTER TABLE food_submissions ADD COLUMN IF NOT EXISTS label_sha256 TEXT;

CREATE INDEX IF NOT EXISTS food_submissions_front_sha_idx ON food_submissions (front_sha256) WHERE front_sha256 IS NOT NULL;
CREATE INDEX IF NOT EXISTS food_submissions_label_sha_idx ON food_submissions (label_sha256) WHERE label_sha256 IS NOT NULL;

CREATE OR REPLACE FUNCTION public.food_submission_photos_hash() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    IF NEW.kind = 'front' THEN
        UPDATE public.food_submissions SET front_sha256 = encode(sha256(NEW.bytes), 'hex') WHERE id = NEW.submission_id;
    ELSE
        UPDATE public.food_submissions SET label_sha256 = encode(sha256(NEW.bytes), 'hex') WHERE id = NEW.submission_id;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS food_submission_photos_hash ON food_submission_photos;
CREATE TRIGGER food_submission_photos_hash
    AFTER INSERT ON food_submission_photos
    FOR EACH ROW EXECUTE FUNCTION public.food_submission_photos_hash();

UPDATE food_submissions s SET front_sha256 = encode(sha256(p.bytes), 'hex')
FROM food_submission_photos p
WHERE p.submission_id = s.id AND p.kind = 'front' AND s.front_sha256 IS NULL;

UPDATE food_submissions s SET label_sha256 = encode(sha256(p.bytes), 'hex')
FROM food_submission_photos p
WHERE p.submission_id = s.id AND p.kind = 'label' AND s.label_sha256 IS NULL;

-- ── Devices ─────────────────────────────────────────────────────────────────

-- Which install keys an account contributed from. Belongs to the account (cascade).
CREATE TABLE IF NOT EXISTS device_sightings (
    user_id        UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    device_hash    TEXT        NOT NULL,
    platform       TEXT        NOT NULL CHECK (platform IN ('ios', 'android', 'other')),
    first_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, device_hash)
);

CREATE INDEX IF NOT EXISTS device_sightings_device_idx ON device_sightings (device_hash);

ALTER TABLE device_sightings ENABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS device_markers (
    id             UUID        PRIMARY KEY,
    device_hash    TEXT        NOT NULL,
    platform       TEXT        NOT NULL CHECK (platform IN ('ios', 'android', 'other')),
    action_id      UUID        REFERENCES moderation_actions (id) ON DELETE SET NULL,
    user_id        UUID        REFERENCES auth.users (id) ON DELETE SET NULL,
    subject_key    TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     TEXT        NOT NULL CHECK (char_length(created_by) <= 200),
    -- pending: DeviceCheck or device recall not written yet; set: written;
    -- clear_pending: cleared here, the remote bit still to be reset; cleared.
    remote_state   TEXT        NOT NULL DEFAULT 'pending' CHECK (remote_state IN ('pending', 'set', 'clear_pending', 'cleared')),
    remote_at      TIMESTAMPTZ,
    cleared_at     TIMESTAMPTZ,
    cleared_by     TEXT        CHECK (char_length(cleared_by) <= 200),
    clear_note     TEXT        CHECK (char_length(clear_note) <= 1000)
);

CREATE UNIQUE INDEX IF NOT EXISTS device_markers_active_idx ON device_markers (device_hash) WHERE cleared_at IS NULL;
CREATE INDEX IF NOT EXISTS device_markers_user_idx ON device_markers (user_id);

ALTER TABLE device_markers ENABLE ROW LEVEL SECURITY;

DROP TRIGGER IF EXISTS device_markers_subject_key ON device_markers;
CREATE TRIGGER device_markers_subject_key
    BEFORE INSERT ON device_markers
    FOR EACH ROW EXECUTE FUNCTION public.fill_subject_key();

-- One row per notable contribution check. No account, no device.
CREATE TABLE IF NOT EXISTS contribution_checks (
    id        BIGSERIAL   PRIMARY KEY,
    at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    platform  TEXT        NOT NULL CHECK (platform IN ('ios', 'android', 'other')),
    signal    TEXT        NOT NULL CHECK (signal IN ('account', 'install_key', 'devicecheck', 'play_integrity')),
    outcome   TEXT        NOT NULL CHECK (outcome IN ('ok', 'flagged', 'marked', 'restricted', 'invalid', 'error')),
    blocked   BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS contribution_checks_at_idx ON contribution_checks (at);

ALTER TABLE contribution_checks ENABLE ROW LEVEL SECURITY;

-- ── Signals, one row per submission (not granted to grafana_ro) ─────────────

-- Headline fields compared between what the model read and what the person
-- sent: per 100 when both have it, else per serving. A field differs when it
-- is further apart than label rounding allows (10 kcal or 2 g, or 10%).
CREATE OR REPLACE VIEW public.contribution_signals AS
WITH s AS (
    SELECT fs.*,
           fs.submitted -> 'per100'                AS sub_100,
           fs.submitted -> 'perServing'            AS sub_ps,
           fs.parsed -> 'facts' -> 'per100'        AS ocr_100,
           fs.parsed -> 'facts' -> 'perServing'    AS ocr_ps
    FROM public.food_submissions fs
)
SELECT
    s.id AS submission_id,
    s.user_id,
    s.food_id,
    s.created_at,
    s.status,
    s.reviewed_at,
    s.parsed IS NOT NULL AS has_reading,
    CASE WHEN s.parsed IS NULL THEN NULL ELSE EXISTS (
        SELECT 1
        FROM (VALUES ('energy_kcal', 10.0::float8), ('protein_g', 2.0), ('carbs_g', 2.0), ('fat_g', 2.0)) AS f (k, slack),
             LATERAL (
                 SELECT CASE
                            WHEN (s.sub_100 -> f.k) IS NOT NULL AND (s.ocr_100 -> f.k) IS NOT NULL THEN (s.sub_100 ->> f.k)::float8
                            WHEN (s.sub_ps -> f.k) IS NOT NULL AND (s.ocr_ps -> f.k) IS NOT NULL THEN (s.sub_ps ->> f.k)::float8
                        END AS sent,
                        CASE
                            WHEN (s.sub_100 -> f.k) IS NOT NULL AND (s.ocr_100 -> f.k) IS NOT NULL THEN (s.ocr_100 ->> f.k)::float8
                            WHEN (s.sub_ps -> f.k) IS NOT NULL AND (s.ocr_ps -> f.k) IS NOT NULL THEN (s.ocr_ps ->> f.k)::float8
                        END AS read
             ) v
        WHERE v.sent IS NOT NULL AND abs(v.sent - v.read) > greatest(f.slack, 0.1 * abs(v.read))
    ) END AS ocr_disagrees,
    s.checks @> '[{"code": "energy_macros_mismatch"}]' OR s.checks @> '[{"code": "energy_looks_like_kj"}]' AS atwater_mismatch,
    s.checks @> '[{"code": "impossible_value"}]' AS impossible_value,
    coalesce(
        (coalesce(s.sub_100, s.sub_ps) ->> 'energy_kcal')::float8 = 0
        AND coalesce((coalesce(s.sub_100, s.sub_ps) ->> 'protein_g')::float8, 0)
          + coalesce((coalesce(s.sub_100, s.sub_ps) ->> 'carbs_g')::float8, 0)
          + coalesce((coalesce(s.sub_100, s.sub_ps) ->> 'fat_g')::float8, 0) > 1,
        FALSE
    ) AS kcal_zero_with_macros,
    jsonb_array_length(s.checks) AS failed_checks,
    EXISTS (
        SELECT 1 FROM public.food_submissions o
        WHERE o.id <> s.id AND o.created_at <= s.created_at
          AND ((s.label_sha256 IS NOT NULL AND o.label_sha256 = s.label_sha256)
            OR (s.front_sha256 IS NOT NULL AND o.front_sha256 = s.front_sha256))
    ) AS photo_reused
FROM s;

REVOKE ALL ON public.contribution_signals FROM PUBLIC;

-- ── Views ───────────────────────────────────────────────────────────────────

-- Bans, suspensions and restrictions started and lifted per day.
CREATE OR REPLACE VIEW analytics.moderation_daily AS
SELECT day, kind, scope, sum(started) AS started, sum(lifted) AS lifted
FROM (
    SELECT (created_at AT TIME ZONE 'UTC')::date AS day, kind, scope, 1 AS started, 0 AS lifted
    FROM public.moderation_actions
    UNION ALL
    SELECT (lifted_at AT TIME ZONE 'UTC')::date, kind, scope, 0, 1
    FROM public.moderation_actions WHERE lifted_at IS NOT NULL
) m
GROUP BY day, kind, scope;

-- Flags raised on submissions, by the day they were sent.
CREATE OR REPLACE VIEW analytics.contribution_flags_daily AS
SELECT day, flag, count(*) AS contributions
FROM (
    SELECT (created_at AT TIME ZONE 'UTC')::date AS day,
           unnest(ARRAY[
               CASE WHEN atwater_mismatch THEN 'atwater_mismatch' END,
               CASE WHEN impossible_value THEN 'impossible_value' END,
               CASE WHEN kcal_zero_with_macros THEN 'kcal_zero_with_macros' END,
               CASE WHEN ocr_disagrees THEN 'ocr_disagreement' END,
               CASE WHEN photo_reused THEN 'photo_reused' END
           ]) AS flag
    FROM public.contribution_signals
) f
WHERE flag IS NOT NULL
GROUP BY day, flag;

-- Contribution checks by outcome per day (DeviceCheck, Play Integrity, install keys, account standing).
CREATE OR REPLACE VIEW analytics.contribution_checks_daily AS
SELECT (at AT TIME ZONE 'UTC')::date AS day, platform, signal, outcome,
       count(*) AS checks, count(*) FILTER (WHERE blocked) AS blocked
FROM public.contribution_checks
GROUP BY 1, 2, 3, 4;

-- One row per person who has ever submitted a food or reported one. Counts, rates and codes only.
CREATE OR REPLACE VIEW support.contributors AS
WITH sig AS (
    SELECT * FROM public.contribution_signals WHERE user_id IS NOT NULL
),
people AS (
    SELECT user_id FROM sig
    UNION
    SELECT user_id FROM public.food_reports WHERE user_id IS NOT NULL
),
subs AS (
    SELECT user_id,
           count(*) AS submitted_total,
           count(*) FILTER (WHERE created_at > now() - interval '30 days') AS submitted_30d,
           count(*) FILTER (WHERE status = 'approved' AND reviewed_at > now() - interval '30 days') AS approved_30d,
           count(*) FILTER (WHERE status = 'rejected' AND reviewed_at > now() - interval '30 days') AS rejected_30d,
           count(*) FILTER (WHERE status = 'pending') AS pending,
           count(*) FILTER (WHERE created_at > now() - interval '7 days'
               AND (atwater_mismatch OR impossible_value OR kcal_zero_with_macros OR ocr_disagrees OR photo_reused)) AS flagged_7d,
           count(*) FILTER (WHERE created_at > now() - interval '24 hours'
               AND (atwater_mismatch OR impossible_value OR kcal_zero_with_macros OR ocr_disagrees OR photo_reused)) AS flagged_24h,
           count(*) FILTER (WHERE impossible_value OR kcal_zero_with_macros) AS impossible_total,
           count(*) FILTER (WHERE photo_reused) AS photo_reuse_hits,
           count(*) FILTER (WHERE created_at > now() - interval '1 hour') AS submitted_1h,
           count(*) FILTER (WHERE created_at > now() - interval '24 hours') AS submitted_24h,
           max(created_at) AS last_submitted_at
    FROM sig
    GROUP BY user_id
),
ocr AS (
    SELECT user_id, avg(CASE WHEN ocr_disagrees THEN 1.0 ELSE 0.0 END)::float8 AS ocr_disagreement_rate
    FROM (
        SELECT user_id, ocr_disagrees, row_number() OVER (PARTITION BY user_id ORDER BY created_at DESC) AS n
        FROM sig WHERE has_reading
    ) recent
    WHERE n <= 50
    GROUP BY user_id
),
daily AS (
    SELECT user_id, percentile_cont(0.5) WITHIN GROUP (ORDER BY submitted)::float8 AS median_daily_30d
    FROM (
        SELECT user_id, (created_at AT TIME ZONE 'UTC')::date AS day, count(*) AS submitted
        FROM sig WHERE created_at > now() - interval '30 days'
        GROUP BY 1, 2
    ) d
    GROUP BY user_id
),
reports AS (
    SELECT user_id,
           count(*) FILTER (WHERE created_at > now() - interval '30 days') AS reported_30d,
           count(*) FILTER (WHERE status = 'accepted' AND resolved_at > now() - interval '30 days') AS reports_accepted_30d,
           count(*) FILTER (WHERE status = 'rejected' AND resolved_at > now() - interval '30 days') AS reports_rejected_30d
    FROM public.food_reports WHERE user_id IS NOT NULL
    GROUP BY user_id
),
rolled AS (
    SELECT user_id, count(*) AS rolled_back FROM public.food_provenance
    WHERE user_id IS NOT NULL AND rolled_back_at IS NOT NULL GROUP BY user_id
),
standing AS (
    SELECT DISTINCT ON (user_id) user_id,
           CASE kind WHEN 'ban' THEN 'banned' WHEN 'suspension' THEN 'suspended' ELSE 'restricted' END AS standing
    FROM public.moderation_actions
    WHERE user_id IS NOT NULL AND lifted_at IS NULL AND (until IS NULL OR until > now())
    ORDER BY user_id, CASE kind WHEN 'ban' THEN 0 WHEN 'suspension' THEN 1 ELSE 2 END
)
SELECT
    p.user_id,
    (current_date - (u.created_at AT TIME ZONE 'UTC')::date) AS account_age_days,
    coalesce(s.submitted_total, 0) AS submitted_total,
    coalesce(s.submitted_30d, 0) AS submitted_30d,
    coalesce(s.approved_30d, 0) AS approved_30d,
    coalesce(s.rejected_30d, 0) AS rejected_30d,
    coalesce(s.pending, 0) AS pending,
    s.approved_30d::float8 / nullif(s.approved_30d + s.rejected_30d, 0) AS approval_rate_30d,
    o.ocr_disagreement_rate,
    coalesce(s.flagged_7d, 0) AS flagged_7d,
    coalesce(s.flagged_24h, 0) AS flagged_24h,
    coalesce(s.impossible_total, 0) AS impossible_total,
    coalesce(s.photo_reuse_hits, 0) AS photo_reuse_hits,
    coalesce(s.submitted_1h, 0) AS submitted_1h,
    coalesce(s.submitted_24h, 0) AS submitted_24h,
    d.median_daily_30d,
    coalesce(r.reported_30d, 0) AS reported_30d,
    coalesce(r.reports_accepted_30d, 0) AS reports_accepted_30d,
    coalesce(r.reports_rejected_30d, 0) AS reports_rejected_30d,
    coalesce(rb.rolled_back, 0) AS rolled_back,
    coalesce(st.standing, 'ok') AS standing,
    EXISTS (
        SELECT 1 FROM public.device_markers m
        WHERE m.cleared_at IS NULL
          AND (m.user_id = p.user_id OR m.device_hash IN (SELECT ds.device_hash FROM public.device_sightings ds WHERE ds.user_id = p.user_id))
    ) AS device_marked,
    s.last_submitted_at
FROM people p
JOIN auth.users u ON u.id = p.user_id
LEFT JOIN subs s ON s.user_id = p.user_id
LEFT JOIN ocr o ON o.user_id = p.user_id
LEFT JOIN daily d ON d.user_id = p.user_id
LEFT JOIN reports r ON r.user_id = p.user_id
LEFT JOIN rolled rb ON rb.user_id = p.user_id
LEFT JOIN standing st ON st.user_id = p.user_id;

-- The same photo in two submissions: the later one, the earlier one, and whether two accounts sent them.
CREATE OR REPLACE VIEW support.photo_reuse AS
SELECT s.id AS submission_id, o.id AS other_submission_id, s.user_id, o.user_id AS other_user_id,
       CASE WHEN s.label_sha256 = o.label_sha256 THEN 'label' ELSE 'front' END AS photo,
       s.user_id IS DISTINCT FROM o.user_id AS cross_account,
       s.created_at
FROM public.food_submissions s
JOIN public.food_submissions o
  ON o.id <> s.id AND o.created_at <= s.created_at
 AND ((s.label_sha256 IS NOT NULL AND o.label_sha256 = s.label_sha256)
   OR (s.front_sha256 IS NOT NULL AND o.front_sha256 = s.front_sha256));

-- Every ban, suspension and restriction, without the note (free text stays in the admin console).
CREATE OR REPLACE VIEW support.moderation_actions AS
SELECT id AS action_id, user_id, kind, scope, reason, created_at, created_by, until, mark_devices,
       lifted_at, lifted_by, appeal_state,
       lifted_at IS NULL AND (until IS NULL OR until > now()) AS active
FROM public.moderation_actions;

-- Submissions, reports and corrections per day by state: submitted, then
-- approved / rejected (submissions), accepted / rejected / duplicate
-- (reports), applied / reverted (corrections), and rolled back in moderation.
CREATE OR REPLACE VIEW analytics.contributions_daily AS
SELECT day, kind, state, count(*) AS contributions FROM (
    SELECT (created_at AT TIME ZONE 'UTC')::date AS day, 'submission'::text AS kind, 'submitted'::text AS state
    FROM public.food_submissions
    UNION ALL
    SELECT (reviewed_at AT TIME ZONE 'UTC')::date, 'submission', status
    FROM public.food_submissions WHERE status IN ('approved', 'rejected') AND reviewed_at IS NOT NULL
    UNION ALL
    SELECT (created_at AT TIME ZONE 'UTC')::date, 'report', 'submitted'
    FROM public.food_reports WHERE user_id IS NOT NULL
    UNION ALL
    SELECT (resolved_at AT TIME ZONE 'UTC')::date, 'report', status
    FROM public.food_reports WHERE resolved_at IS NOT NULL AND user_id IS NOT NULL
    UNION ALL
    SELECT (created_at AT TIME ZONE 'UTC')::date, 'correction', 'applied' FROM public.food_corrections
    UNION ALL
    SELECT (reverted_at AT TIME ZONE 'UTC')::date, 'correction', 'reverted'
    FROM public.food_corrections WHERE reverted_at IS NOT NULL
    UNION ALL
    SELECT (rolled_back_at AT TIME ZONE 'UTC')::date, kind, 'rolled_back'
    FROM public.food_provenance WHERE rolled_back_at IS NOT NULL
) c
GROUP BY day, kind, state;

-- What waits for a reviewer, by kind: how many, the oldest and the median age.
CREATE OR REPLACE VIEW analytics.contribution_queue AS
SELECT 'submission'::text AS kind, count(*) AS waiting,
       coalesce(extract(epoch FROM now() - min(created_at)) / 3600, 0)::float8 AS oldest_hours,
       coalesce(percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM now() - created_at) / 3600), 0)::float8 AS median_hours
FROM public.food_submissions WHERE status = 'pending'
UNION ALL
SELECT 'report', count(*),
       coalesce(extract(epoch FROM now() - min(created_at)) / 3600, 0)::float8,
       coalesce(percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM now() - created_at) / 3600), 0)::float8
FROM public.food_reports WHERE status = 'open';

-- The foods people report most in the last 90 days, by id (the admin console shows the food).
CREATE OR REPLACE VIEW analytics.top_reported_foods AS
SELECT food_id, count(*) AS reports, count(*) FILTER (WHERE status = 'open') AS open_reports,
       count(DISTINCT user_id) AS reporters, max(created_at) AS last_reported_at
FROM public.food_reports
WHERE created_at > now() - interval '90 days' AND user_id IS NOT NULL
GROUP BY food_id
ORDER BY count(*) DESC
LIMIT 50;

GRANT SELECT ON ALL TABLES IN SCHEMA analytics TO grafana_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA support TO grafana_ro;
